/* Safe C++03 vector normalization with preserved written syntax. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import java.util.*;
import static com.kniazkov.cpg.model.DynamicArrayOperation.Kind.*;
import static com.kniazkov.cpg.model.DynamicArrayOperation.Handle.Validity.*;

/** Publishes sequence semantics only for the trusted ordinary vector specialization.
 * Handle invalidation is conservative; straight-line capacity lower bounds can prove
 * preservation, but allocation strategy and exact internal copy counts are unspecified.
 * Raw calls remain available for source generation and conservative effect analysis. */
public final class DynamicArraySemantics {
    private DynamicArraySemantics() {}

    public static Project green(Project project) {
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        var types = new LinkedHashMap<ClassDeclaration, DynamicArrayType.Builder>();
        var elements = new IdentityHashMap<ClassDeclaration, TypeNode>();
        var sites = new IdentityHashMap<ClassDeclaration, List<NodeBuilder<? extends TypeNode>>>();
        for (var instance : project.classTemplateInstances()) {
            if (!trusted(instance.origin(), "vector") || !trusted(instance.selected(), "vector")
                    || instance.origin() != instance.selected() || instance.arguments().size() != 2) continue;
            TypeNode element = instance.arguments().getFirst().type().orElse(null);
            TypeNode allocator = instance.arguments().get(1).type().orElse(null);
            if (element == null || allocator == null || bool(element) || !standardAllocator(project, allocator, element)) continue;
            var cls = instance.declaration();
            types.put(cls, DynamicArrayType.create().setConcreteClass(context.copy(cls)).setElementType(context.copy(element)));
            types.get(cls).addTransformationProvenance("DynamicArraySemantics", "source-construct", cls);
            elements.put(cls, element); sites.put(cls, new ArrayList<>());
        }
        var operations = new ArrayList<NodeBuilder<? extends DynamicArrayOperation>>();
        for (var source : SourceContexts.roots(project)) {
            if (source.source().language() != Language.CPP || LibraryDeclarations.isLibrary(source.node())) continue;
            var profile = TypeSemantics.profile(source.source());
            var conditionalNodes = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
            collectConditionalNodes(source.node(), false, conditionalNodes);
            walkEvaluated(source.node(), new NodeVisitor() {
                private final Map<Node, SequenceState> states = new IdentityHashMap<>();
                private final List<TrackedHandle> handles = new ArrayList<>();
                private boolean straightLine;
                @Override public void visit(CallableDeclaration callable) {
                    states.clear(); handles.clear(); straightLine = true;
                }
                @Override public void visit(Node node) {
                    if (node instanceof IfStatement || node instanceof WhileStatement || node instanceof ForStatement
                            || node instanceof DoWhileStatement || node instanceof SwitchStatement || node instanceof TryStatement
                            || node instanceof GotoStatement || node instanceof ConditionalExpression || node instanceof LogicalAndExpression
                            || node instanceof LogicalOrExpression || node instanceof TypeidExpression) {
                        straightLine = false; states.clear();
                        if (node instanceof TypeidExpression) handles.forEach(handle -> handle.validity = UNKNOWN);
                    }
                }
                private ClassDeclaration owner(TypeNode type) {
                    var cls = ClassSemantics.objectClass(TypeSemantics.withoutReference(type)).orElse(null);
                    return types.containsKey(cls) ? cls : null;
                }
                private ClassDeclaration owner(Expression expression) {
                    return TypeSemantics.expressionType(expression, profile).map(this::owner).orElse(null);
                }
                @Override public void visit(TypeNode type) {
                    var cls = owner(type); if (cls != null) sites.get(cls).add(context.copy(type));
                }
                private void add(DynamicArrayOperation.Kind kind, Node syntax, Node destination,
                        ClassDeclaration cls, CallableDeclaration target, List<? extends Expression> arguments) {
                    if (cls == null || target == null || !libraryTarget(target)) return;
                    boolean record = ClassSemantics.objectClass(elements.get(cls)).isPresent();
                    if (record && kind == RESIZE && arguments.size() == 1) return;
                    if (record && (kind == DynamicArrayOperation.Kind.COUNT || kind == DynamicArrayOperation.Kind.ASSIGN_COPY || kind == DynamicArrayOperation.Kind.ASSIGN_FILL
                            || kind == DynamicArrayOperation.Kind.ASSIGN_RANGE)) return;
                    if ((kind == DynamicArrayOperation.Kind.RANGE || kind == DynamicArrayOperation.Kind.ASSIGN_RANGE)
                            && (record || !pointerRange(arguments, profile))) return;
                    var operation = DynamicArrayOperation.create().setKind(kind).setSyntax(context.copy(syntax))
                            .setDestination(context.copy(destination)).setType(types.get(cls)).setSelectedTarget(context.copy(target));
                    var inputs = new ArrayList<NodeBuilder<? extends Node>>();
                    operation.addTransformationProvenance("DynamicArraySemantics", "source-construct", syntax);
                    arguments.forEach(argument -> inputs.add(context.copy(argument))); operation.setSources(inputs);
                    // vector and the standard allocator copy through const T& even
                    // when the caller supplies a mutable lvalue to push_back/fill.
                    if (copies(kind)) {
                        var selected = ClassSemantics.selectedElementCopyConstructor(elements.get(cls), null, profile);
                        if (record && selected.isEmpty() && ClassSemantics.objectClass(elements.get(cls)).stream()
                                .flatMap(owner -> ClassSemantics.specialMembers(owner).stream())
                                .anyMatch(member -> member.kind() == ClassSemantics.SpecialKind.COPY_CONSTRUCTOR
                                        && member.origin() != ClassSemantics.Origin.IMPLICIT)) return;
                        if (selected.isPresent() && !selected.get().access().orElse("public").equals("public")) return;
                        selected.ifPresent(copy -> operation.setElementCopyConstructor(context.copy(copy)));
                    }
                    if (record && !elementEffects(operation, kind, elements.get(cls), profile, context)) return;
                    describe(operation, kind, syntax, destination, target, arguments);
                    operations.add(operation);
                }
                @Override public void visit(VariableDeclaration variable) {
                    if (!variable.isDefinition() || variable.type().isEmpty()) return;
                    TypeNode written = TypeSemantics.unqualified(variable.type().orElseThrow());
                    if (written instanceof ReferenceType || written instanceof ArrayType) return;
                    var cls = owner(written); if (cls == null) return;
                    var value = variable.initializer().map(DynamicArraySemantics::written).orElse(null);
                    List<Expression> arguments = value == null ? List.of()
                            : value instanceof InitializationExpression init && init.form() == InitializationExpression.Form.DIRECT
                            ? init.arguments() : List.of(value);
                    var target = variable.constructorTarget().orElse(null);
                    var kind = creation(cls, arguments, profile);
                    if (kind != null) add(kind, variable, variable, cls, target, arguments);
                }
                @Override public void visit(CallExpression call) {
                    if (!(call.callee() instanceof MemberExpression member)) {
                        states.clear(); handles.forEach(handle -> handle.validity = UNKNOWN); return;
                    }
                    var cls = owner(member.receiver()); var target = call.target().orElse(null);
                    if (cls == null || target == null) {
                        states.clear(); handles.forEach(handle -> handle.validity = UNKNOWN); return;
                    }
                    var args = call.arguments();
                    var kind = memberKind(target.name(), args, profile);
                    if (kind == SWAP && (args.size() != 1 || cls != owner(args.getFirst()))) kind = null;
                    if (kind == ASSIGN_COPY && cls != owner(args.getFirst())) kind = null;
                    if (kind == INSERT_RANGE && !pointerRange(args.subList(1, 3), profile)) kind = null;
                    int before = operations.size();
                    if (kind != null) add(kind, call, member.receiver(), cls, target, args);
                    if (operations.size() == before) { states.clear(); handles.forEach(handle -> handle.validity = UNKNOWN); }
                }
                @Override public void visit(IndexExpression index) {
                    add(SUBSCRIPT, index, index.left(), owner(index.left()), index.operatorTarget().orElse(null), List.of(index.right()));
                }
                private Long number(Expression expression) {
                    return ConstantEvaluator.evaluate(expression, profile).filter(v -> v.number().signum() >= 0 && v.number().bitLength() < 62)
                            .map(v -> v.number().longValue()).orElse(null);
                }
                private Long position(Expression expression, Node sequence) {
                    expression = written(expression);
                    if (expression instanceof CallExpression call && call.callee() instanceof MemberExpression member
                            && identity(member.receiver()) == sequence && call.target().filter(DynamicArraySemantics::libraryTarget).isPresent()) {
                        if (call.target().orElseThrow().name().equals("begin")) return 0L;
                        if (call.target().orElseThrow().name().equals("end")) return states.getOrDefault(sequence, new SequenceState()).size;
                    }
                    if (expression instanceof AddExpression add) {
                        Long left = position(add.left(), sequence), right = number(add.right());
                        return left == null || right == null ? null : left + right;
                    }
                    return null;
                }
                private void describe(DynamicArrayOperation.Builder operation, DynamicArrayOperation.Kind kind, Node syntax,
                        Node destination, CallableDeclaration target, List<? extends Expression> args) {
                    Node sequence = identity(destination);
                    var state = states.computeIfAbsent(sequence, ignored -> new SequenceState());
                    boolean access = Set.of(SUBSCRIPT, FRONT, BACK, AT).contains(kind);
                    boolean iterator = Set.of(BEGIN, END, RBEGIN, REND, INSERT, ERASE, ERASE_RANGE).contains(kind);
                    boolean writable = destination instanceof Expression expression && TypeSemantics.expressionType(expression, profile)
                            .map(TypeSemantics::withoutReference).map(t -> !(t instanceof QualifiedType q) || !q.isConst()).orElse(false);
                    if (access) operation.setResultKind(DynamicArrayOperation.ResultKind.REFERENCE).setWritable(writable);
                    else if (iterator) operation.setResultKind(DynamicArrayOperation.ResultKind.ITERATOR).setWritable(writable);
                    else if (Set.of(SIZE, IS_EMPTY, CAPACITY, MAX_SIZE).contains(kind)) operation.setResultKind(DynamicArrayOperation.ResultKind.VALUE);
                    Long index = null;
                    var positionKind = DynamicArrayOperation.Handle.Position.UNKNOWN;
                    if (kind == FRONT || kind == BEGIN) { index = 0L; positionKind = DynamicArrayOperation.Handle.Position.BEGIN; }
                    else if (kind == BACK || kind == RBEGIN) { index = state.size == null ? null : state.size - 1; positionKind = DynamicArrayOperation.Handle.Position.LAST; }
                    else if (kind == END || kind == REND) { index = kind == END ? state.size : Long.valueOf(-1); positionKind = DynamicArrayOperation.Handle.Position.END; }
                    else if (kind == SUBSCRIPT || kind == AT) { index = number(args.getFirst()); positionKind = DynamicArrayOperation.Handle.Position.INDEX; operation.setIndex(context.copy(args.getFirst())); }
                    else if (Set.of(INSERT, INSERT_FILL, INSERT_RANGE, ERASE, ERASE_RANGE).contains(kind)) index = position(args.getFirst(), sequence);
                    var effects = new ArrayList<DynamicArrayOperation.HandleEffect.Builder>();
                    Long amount = kind == INSERT ? Long.valueOf(1) : kind == INSERT_FILL ? number(args.get(1)) : null;
                    Long nextSize = state.size == null || amount == null ? null : state.size + amount;
                    Node other = kind == SWAP ? identity(args.getFirst()) : null;
                    if (kind == SWAP) operation.setMutatedObjects(List.of(context.copy(destination), context.copy(args.getFirst())));
                    for (var handle : handles) {
                        if (kind == SWAP && (handle.sequences.contains(sequence) || handle.sequences.contains(other))) {
                            boolean conditional = conditionalNodes.contains(syntax);
                            if (conditional) {
                                handle.sequences.add(sequence); handle.sequences.add(other);
                            } else {
                                boolean hasFirst = handle.sequences.remove(sequence), hasSecond = handle.sequences.remove(other);
                                if (hasFirst) handle.sequences.add(other);
                                if (hasSecond) handle.sequences.add(sequence);
                            }
                            // End iterators are container boundaries, not element handles guaranteed by swap.
                            if (handle.boundary) handle.validity = UNKNOWN;
                            var effect = DynamicArrayOperation.HandleEffect.create().setProducer(handle.producer).setValidityAfter(handle.validity);
                            if (!conditional && handle.sequences.size() == 1) effect.setSequenceAfter(context.copy(handle.sequences.iterator().next()));
                            effects.add(effect);
                        } else if (handle.sequences.contains(sequence) && mutates(kind)) {
                            var validity = UNKNOWN;
                            if (handle.validity == INVALID) validity = INVALID;
                            else if (kind == CLEAR || Set.of(ASSIGN_COPY, ASSIGN_FILL, ASSIGN_RANGE).contains(kind)) validity = INVALID;
                            else if (straightLine) {
                                if (kind == INSERT_FILL && amount != null && amount == 0) validity = handle.validity;
                                else if (kind == ERASE_RANGE && index != null && index.equals(position(args.get(1), sequence))) validity = handle.validity;
                                else if (kind == RESERVE && state.capacity != null && number(args.getFirst()) != null && number(args.getFirst()) <= state.capacity) validity = handle.validity;
                                else if (Set.of(INSERT, INSERT_FILL).contains(kind) && nextSize != null && state.capacity != null
                                        && nextSize <= state.capacity && index != null && handle.position != null)
                                    validity = handle.position < index && !handle.boundary ? handle.validity : INVALID;
                                else if ((kind == ERASE || kind == ERASE_RANGE || kind == POP_BACK) && handle.position != null) {
                                    Long boundary = kind == POP_BACK ? (state.size == null ? null : state.size - 1) : index;
                                    if (boundary != null) validity = handle.position < boundary && !handle.boundary ? handle.validity : INVALID;
                                } else if (kind == APPEND && state.size != null && state.capacity != null && state.size < state.capacity)
                                    validity = handle.boundary ? INVALID : handle.validity;
                            }
                            if ((conditionalNodes.contains(syntax) || handle.sequences.size() > 1) && handle.validity != INVALID) validity = UNKNOWN;
                            handle.validity = validity;
                            effects.add(DynamicArrayOperation.HandleEffect.create().setProducer(handle.producer).setValidityAfter(validity));
                        }
                    }
                    operation.setHandleEffects(effects);
                    if (straightLine) {
                        if (kind == EMPTY) { state.size = 0L; state.capacity = 0L; }
                        else if (kind == COUNT || kind == FILL) { state.size = number(args.getFirst()); state.capacity = state.size; }
                        else if (kind == RESERVE) { Long n = number(args.getFirst()); state.capacity = n == null ? null : state.capacity == null ? n : Math.max(n, state.capacity); }
                        else if (kind == CLEAR) state.size = 0L;
                        else if (kind == APPEND) state.size = state.size == null ? null : state.size + 1;
                        else if (kind == INSERT || kind == INSERT_FILL) state.size = nextSize;
                        else if (kind == RESIZE) state.size = number(args.getFirst());
                        else if (kind == POP_BACK) state.size = state.size == null ? null : Math.max(0, state.size - 1);
                        else if (kind == SWAP) { var otherState = states.getOrDefault(other, new SequenceState()); states.put(sequence, otherState); states.put(other, state); }
                        else if (mutates(kind)) {
                            state.size = null;
                            if (Set.of(ASSIGN_COPY, ASSIGN_FILL, ASSIGN_RANGE).contains(kind)) state.capacity = null;
                        }
                    } else { state.size = null; state.capacity = null; }
                    if (access || iterator) {
                        var validity = iterator ? VALID : straightLine && index != null && state.size != null && index >= 0 && index < state.size ? VALID : UNKNOWN;
                        var handle = DynamicArrayOperation.Handle.create().setKind(access ? DynamicArrayOperation.Handle.Kind.REFERENCE : DynamicArrayOperation.Handle.Kind.ITERATOR)
                                .setSequence(context.copy(sequence)).setPositionKind(positionKind).setWritable(writable).setValidity(validity)
                                .setDirection(kind == RBEGIN || kind == REND ? DynamicArrayOperation.Handle.Direction.REVERSE : DynamicArrayOperation.Handle.Direction.FORWARD);
                        if (positionKind == DynamicArrayOperation.Handle.Position.INDEX) handle.setPosition(context.copy(args.getFirst()));
                        operation.setHandle(handle);
                        // Reverse iterators are invalidated according to their base iterator, not the dereferenced element.
                        Long basePosition = kind == RBEGIN ? state.size : kind == REND ? Long.valueOf(0) : index;
                        handles.add(new TrackedHandle(operation, sequence, basePosition, kind == END || kind == RBEGIN, validity));
                    }
                }

                @Override public void visit(AssignmentExpression assignment) {
                    var cls = owner(assignment.target());
                    if (cls != null) {
                        int before = operations.size();
                        if (cls == owner(assignment.value())) add(DynamicArrayOperation.Kind.ASSIGN_COPY,
                                assignment, assignment.target(), cls, assignment.operatorTarget().orElse(null), List.of(assignment.value()));
                        if (operations.size() == before) { states.clear(); handles.forEach(handle -> handle.validity = UNKNOWN); }
                    }
                }
            });
        }
        types.forEach((cls, type) -> type.setWrittenTypes(sites.get(cls)));
        root.setDynamicArrayTypes(new ArrayList<>(types.values())); root.setDynamicArraySemantics(operations);
        return root.build().recordPass("DynamicArraySemantics");
    }
    private static void collectConditionalNodes(Node node, boolean conditional, Set<Node> result) {
        if (conditional) result.add(node);
        boolean childrenConditional = conditional || node instanceof IfStatement || node instanceof WhileStatement
                || node instanceof ForStatement || node instanceof DoWhileStatement || node instanceof SwitchStatement
                || node instanceof TryStatement || node instanceof ConditionalExpression
                || node instanceof LogicalAndExpression || node instanceof LogicalOrExpression;
        for (var child : node.children()) collectConditionalNodes(child, childrenConditional, result);
    }
    /** Visit operands before their enclosing operation. C++03 argument order is unspecified,
     * so an effectful argument destroys proofs before the enclosing mutation is described. */
    private static void walkEvaluated(Node node, NodeVisitor visitor) {
        if (node instanceof SizeofExpression || node instanceof OffsetofExpression || node instanceof TypeidExpression) {
            // sizeof is unevaluated; typeid requires RTTI-specific evaluation rules not proven here.
            node.accept(visitor);
        } else if (node instanceof ConditionalExpression || node instanceof LogicalAndExpression || node instanceof LogicalOrExpression) {
            node.accept(visitor);
            for (var child : node.children()) walkEvaluated(child, visitor);
        } else if (node instanceof Expression) {
            for (var child : node.children()) walkEvaluated(child, visitor);
            node.accept(visitor);
        } else {
            node.accept(visitor);
            for (var child : node.children()) walkEvaluated(child, visitor);
        }
    }
    private static final class SequenceState { Long size, capacity; }
    private static final class TrackedHandle {
        final DynamicArrayOperation.Builder producer;
        final Set<Node> sequences = Collections.newSetFromMap(new IdentityHashMap<>());
        final Long position; final boolean boundary;
        DynamicArrayOperation.Handle.Validity validity;
        TrackedHandle(DynamicArrayOperation.Builder producer, Node sequence, Long position, boolean boundary, DynamicArrayOperation.Handle.Validity validity) {
            this.producer = producer; this.sequences.add(sequence); this.position = position; this.boundary = boundary; this.validity = validity;
        }
    }
    private static Node identity(Node node) {
        if (node instanceof Expression expression) node = written(expression);
        if (node instanceof NameExpression name && name.target().isPresent()) {
            Node target = name.target().orElseThrow();
            if (target instanceof VariableDeclaration variable && variable.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent()
                    && variable.initializer().isPresent()) return identity(variable.initializer().orElseThrow());
            return target;
        }
        return node;
    }
    private static boolean copies(DynamicArrayOperation.Kind kind) {
        return Set.of(COUNT, FILL, RANGE, COPY, ASSIGN_COPY, ASSIGN_FILL, ASSIGN_RANGE, APPEND, RESERVE, RESIZE, INSERT, INSERT_FILL, INSERT_RANGE).contains(kind);
    }
    private static boolean mutates(DynamicArrayOperation.Kind kind) {
        return Set.of(ASSIGN_COPY, ASSIGN_FILL, ASSIGN_RANGE, APPEND, RESERVE, RESIZE, POP_BACK, INSERT, INSERT_FILL, INSERT_RANGE, ERASE, ERASE_RANGE, CLEAR, SWAP).contains(kind);
    }
    private static boolean elementEffects(DynamicArrayOperation.Builder operation, DynamicArrayOperation.Kind kind, TypeNode element,
            PlatformProfile profile, EditContext context) {
        var cls = ClassSemantics.objectClass(element).orElseThrow();
        if (Set.of(INSERT, INSERT_FILL, INSERT_RANGE, ERASE, ERASE_RANGE, RESIZE).contains(kind)) {
            // Until assignment overload selection and multiplicity are proven, keep nontrivial shifts raw.
            if (ClassSemantics.specialMembers(cls).stream().anyMatch(m -> m.kind() == ClassSemantics.SpecialKind.COPY_ASSIGNMENT
                    && m.origin() != ClassSemantics.Origin.IMPLICIT)) return false;
        }
        if (mutates(kind) && kind != SWAP) {
            var destructors = ClassSemantics.specialMembers(cls).stream().filter(m -> m.kind() == ClassSemantics.SpecialKind.DESTRUCTOR)
                    .flatMap(m -> m.declarations().stream()).toList();
            if (destructors.size() > 1 || destructors.stream().anyMatch(d -> !d.access().orElse("public").equals("public"))) return false;
            if (!destructors.isEmpty()) operation.setElementDestructor(context.copy(destructors.getFirst()));
        }
        return true;
    }
    private static DynamicArrayOperation.Kind memberKind(String name, List<? extends Expression> args, PlatformProfile profile) {
        int n = args.size();
        if (n == 0) return switch (name) {
            case "front" -> FRONT; case "back" -> BACK; case "size" -> SIZE; case "empty" -> IS_EMPTY;
            case "capacity" -> CAPACITY; case "max_size" -> MAX_SIZE; case "pop_back" -> POP_BACK; case "clear" -> CLEAR;
            case "begin" -> BEGIN; case "end" -> END; case "rbegin" -> RBEGIN; case "rend" -> REND; default -> null;
        };
        if (n == 1) return switch (name) {
            case "operator[]" -> SUBSCRIPT; case "at" -> AT; case "reserve" -> RESERVE; case "resize" -> RESIZE;
            case "push_back" -> APPEND; case "erase" -> ERASE; case "swap" -> SWAP; case "operator=" -> ASSIGN_COPY; default -> null;
        };
        if (n == 2) return switch (name) {
            case "resize" -> RESIZE; case "erase" -> ERASE_RANGE; case "insert" -> INSERT;
            case "assign" -> count(args.getFirst(), profile) ? ASSIGN_FILL : ASSIGN_RANGE; default -> null;
        };
        if (n == 3 && name.equals("insert")) return count(args.get(1), profile) ? INSERT_FILL : INSERT_RANGE;
        return null;
    }
    private static Expression written(Expression expression) {
        while (expression instanceof ConversionExpression conversion && !conversion.isExplicit()) expression = conversion.operand();
        return expression;
    }
    private static DynamicArrayOperation.Kind creation(ClassDeclaration cls, List<Expression> args, PlatformProfile profile) {
        if (args.isEmpty()) return DynamicArrayOperation.Kind.EMPTY;
        if (args.size() == 1) {
            if (TypeSemantics.expressionType(args.getFirst(), profile).flatMap(ClassSemantics::objectClass)
                    .filter(other -> ClassSemantics.sameClass(cls, other)).isPresent()) return DynamicArrayOperation.Kind.COPY;
            return count(args.getFirst(), profile) ? DynamicArrayOperation.Kind.COUNT : null;
        }
        // Explicit allocator arguments remain raw until their evaluation is modeled.
        if (args.size() != 2) return null;
        return count(args.getFirst(), profile) ? DynamicArrayOperation.Kind.FILL : DynamicArrayOperation.Kind.RANGE;
    }
    private static boolean pointerRange(List<? extends Expression> arguments, PlatformProfile profile) {
        return arguments.size() == 2 && arguments.stream().allMatch(argument -> TypeSemantics.expressionType(argument, profile)
                .map(TypeSemantics::decay).map(TypeSemantics::unqualified).filter(PointerType.class::isInstance).isPresent());
    }
    private static boolean count(Expression expression, PlatformProfile profile) {
        return TypeSemantics.expressionType(expression, profile).flatMap(type -> TypeSemantics.integerType(type, profile)).isPresent();
    }
    private static boolean bool(TypeNode type) {
        return TypeSemantics.unqualified(type) instanceof NamedType named && named.fundamentalKind().orElse(null) == FundamentalKind.BOOL;
    }
    private static boolean trusted(ClassDeclaration declaration, String name) {
        return LibraryDeclarations.isLibrary(declaration) && declaration.name().equals(name);
    }
    private static boolean libraryTarget(CallableDeclaration target) {
        return LibraryDeclarations.isLibrary(target) || target.templateOrigin().filter(LibraryDeclarations::isLibrary).isPresent();
    }
    private static boolean standardAllocator(Project project, TypeNode allocator, TypeNode element) {
        var cls = ClassSemantics.objectClass(allocator).orElse(null);
        if (cls == null || !trusted(cls, "allocator")) return false;
        for (var instance : project.classTemplateInstances()) if (instance.declaration() == cls)
            return trusted(instance.origin(), "allocator") && instance.selected() == instance.origin()
                    && instance.arguments().size() == 1 && instance.arguments().getFirst().type()
                    .filter(type -> type.canonicalIdentity().equals(element.canonicalIdentity())).isPresent();
        // Default template arguments may retain the trusted primary declaration rather
        // than a concrete allocator specialization; validate their written type argument.
        TypeNode plain = TypeSemantics.unqualified(allocator);
        return plain instanceof NamedType named && named.templateArguments().size() == 1
                && named.templateArguments().getFirst().type()
                        .filter(type -> type.canonicalIdentity().equals(element.canonicalIdentity())).isPresent()
                && project.classTemplateInstances().stream().anyMatch(instance -> instance.origin() == cls
                        && instance.selected() == cls && trusted(instance.origin(), "allocator")
                        && instance.arguments().size() == 1 && instance.arguments().getFirst().type()
                                .filter(type -> type.canonicalIdentity().equals(element.canonicalIdentity())).isPresent());
    }
}
