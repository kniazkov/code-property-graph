/* Code Property Graph: intraprocedural control flow. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.*;
import java.util.*;

/**
 * Builds control flow backwards from each callable's unique exit. The continuation
 * of a statement is known before its entry is computed. Blocks compose these
 * continuations in reverse source order. Conditions are single CFG vertices.
 */
public final class ControlFlow {
    private ControlFlow() {}

    /** Computes one CFG for each callable with a body, replacing previous flow results. */
    public static Project analyze(Project input) {
        Project project = resolved(input);
        var unsupported = new ArrayList<CallableDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(CallableDeclaration function) {
            if (function.body().isPresent() && initializers(project, function).isEmpty()) unsupported.add(function);
        }});
        var findings = new IdentityHashMap<Node, Diagnostic>();
        for (var function : unsupported) findings.put(function, new Diagnostic("unsupported-constructor-flow", Diagnostic.Severity.WARNING,
                "Constructor flow requires a complete hierarchy without context-dependent virtual-base initialization; no partial flow is published"));
        final Project snapshot = project.withDiagnostics("unsupported-constructor-flow", findings);
        List<FunctionFlow> flows = new ArrayList<>();
        var contexts = SourceContexts.index(snapshot);
        snapshot.walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration fn) {
                fn.body().ifPresent(body -> flows.add(initializers(snapshot, fn)
                        .map(initializers -> new Builder(snapshot, fn, SourceContexts.require(contexts, fn)).build(body, initializers))
                        .orElseGet(() -> new FunctionFlow(fn, fn.exit(), List.of(), List.of(), false))));
            }
        });
        return snapshot.withFlows(flows).invalidatePasses(Set.of("DataFlow")).recordPass("ControlFlow");
    }

    /** Resolving an implicit prerequisite must not erase previously reported root errors.
     * Resolver internals discard annotations on intermediate snapshots, including resource
     * errors that cannot be reconstructed from a partial graph. Restore only missing root
     * findings while retaining every newly produced resolver diagnostic. */
    private static Project resolved(Project input) {
        if (input.passStatus("NameResolver").orElse(null) == PassRun.Status.CURRENT) return input;
        Project result = NameResolver.resolve(input);
        var missing = input.diagnostics().stream().filter(finding -> !result.diagnostics().contains(finding)).toList();
        if (missing.isEmpty()) return result;
        var editor = new ProjectEditor(result).retainDiagnostics().withoutRevalidation();
        missing.forEach(finding -> editor.diagnose(result, finding));
        return editor.build();
    }

    private static Optional<List<ClassSemantics.Subobject>> initializers(Project project, CallableDeclaration function) {
        if (function.kind() != CallableDeclaration.Kind.CONSTRUCTOR && function.kind() != CallableDeclaration.Kind.DESTRUCTOR) return Optional.of(List.of());
        var owners = new ArrayList<ClassDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(ClassDeclaration cls) {
            if (cls.isDefinition() && (cls.members().contains(function) || function.previousDeclaration().filter(cls.members()::contains).isPresent()
                    || cls.typeIdentity().filter(identity -> (function.name().equals(identity + "::" + cls.name()) || function.name().equals(identity + "::~" + cls.name()))).isPresent())) owners.add(cls);
        }});
        // A single callable CFG cannot distinguish complete-object and base-subobject invocation.
        // Keep virtual-base initializers conditional rather than invent unconditional edges.
        if (owners.size() != 1 || !ClassSemantics.hasCompleteNonvirtualHierarchy(owners.getFirst())) return Optional.empty();
        if (function.kind() == CallableDeclaration.Kind.DESTRUCTOR) {
            var objects = new ArrayList<ClassSemantics.Subobject>();
            for (var base : owners.getFirst().bases()) objects.add(new ClassSemantics.Subobject(base, Optional.empty()));
            for (var field : ClassSemantics.fields(owners.getFirst())) objects.add(new ClassSemantics.Subobject(field, Optional.empty()));
            return Optional.of(objects);
        }
        return Optional.of(ClassSemantics.constructionOrder(owners.getFirst(), function));
    }

    private static final class Builder {
        private final Project project;
        private final CallableDeclaration fn;
        private final Language language;
        private final com.kniazkov.cpg.build.PlatformProfile profile;
        private final Exit exit;
        private final AuxiliaryFlowVertex exceptional;
        private final AuxiliaryFlowVertex terminate;
        private int sequence = 2;
        private final Set<Node> retainedSubobjects = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean unsupportedExceptionBoundary;
        private final List<ControlEdge> edges = new ArrayList<>();
        private final Map<String, LabelStatement> labels = new HashMap<>();
        private final Map<Node, List<Node>> liveAt = new IdentityHashMap<>();
        private record Target(FlowVertex vertex, int depth) {}
        private record Handler(CatchClause clause, FlowVertex entry) {}
        private record Region(List<Handler> handlers, int depth) {}
        private record Context(Target breakTo, Target continueTo, List<Region> regions) {
            Context loop(Target b, Target c) { return new Context(b, c, regions); }
        }
        Builder(Project project, CallableDeclaration fn, SourceFile owner) {
            this.project = project; this.fn = fn; exit = fn.exit();
            language = owner.language(); profile = TypeSemantics.profile(owner);
            exceptional = new AuxiliaryFlowVertex(fn, 0, AuxiliaryFlowVertex.Kind.EXCEPTIONAL_EXIT, null, null);
            terminate = new AuxiliaryFlowVertex(fn, 1, AuxiliaryFlowVertex.Kind.TERMINATE_EXIT, null, null);
        }
        void edge(FlowVertex from, FlowVertex to, Branch kind) { edges.add(new ControlEdge(from, to, kind)); }
        boolean invalid(Node body) { return !fn.bodyComplete() || invalidSyntax(body) || unsupportedEvaluation(body); }
        boolean invalidSyntax(Node node) {
            if (node instanceof ClassDeclaration || node instanceof CallableDeclaration) return false;
            if (node.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR)
                    || node instanceof ErrorExpression || node instanceof BlockStatement b && !b.complete() || unsupportedObject(node)) return true;
            return node.children().stream().anyMatch(this::invalidSyntax);
        }
        FunctionFlow build(BlockStatement body, List<ClassSemantics.Subobject> initializers) {
            if (invalid(body) || initializers.stream().anyMatch(i -> unsupportedObject(i.declaration()))) return new FunctionFlow(fn, exit, List.of(), List.of(), false);
            var subobjects = new ArrayList<Node>();
            for (var initializer : initializers) if (objectClass(initializer.declaration()).isPresent()) subobjects.add(initializer.declaration());
            if (fn.kind() == CallableDeclaration.Kind.CONSTRUCTOR) retainedSubobjects.addAll(subobjects);
            var parameters = new ArrayList<Node>();
            for (var parameter : fn.parameters()) if (objectClass(parameter).isPresent()) parameters.add(parameter);
            var initialLive = new ArrayList<Node>(parameters); initialLive.addAll(subobjects);
            inventory(body, initialLive);
            for (var handler : fn.handlers()) inventory(handler, parameters);
            var context = new Context(null, null, List.of());
            if (fn.functionTryBlock()) context = handlers(fn.handlers(), exit, context, parameters, true);
            var finish = cleanup(fn.kind() == CallableDeclaration.Kind.DESTRUCTOR ? initialLive : parameters, 0, exit, context);
            var start = connect(body, finish, Branch.NEXT, context, initialLive);
            var initialized = new ArrayList<Node>(parameters);
            var states = new ArrayList<List<Node>>();
            for (var initializer : initializers) { states.add(List.copyOf(initialized)); if (subobjects.contains(initializer.declaration())) initialized.add(initializer.declaration()); }
            for (int i = fn.kind() == CallableDeclaration.Kind.CONSTRUCTOR ? initializers.size() - 1 : -1; i >= 0; i--) {
                var subobject = initializers.get(i);
                if (subobject.writtenInitializer().isEmpty() && !subobjects.contains(subobject.declaration())) continue;
                FlowVertex initializer = subobject.writtenInitializer().<FlowVertex>map(v -> v).orElseGet(() ->
                        new AuxiliaryFlowVertex(fn, sequence++, AuxiliaryFlowVertex.Kind.IMPLICIT_CONSTRUCTOR, subobject.declaration(), defaultConstructor(subobject.declaration()).orElse(null)));
                edge(initializer, start, Branch.NEXT);
                // Construction succeeds only on the normal edge. Its own object is not yet live on failure.
                boolean mayThrow = subobject.writtenInitializer().map(this::canThrow).orElse(false)
                        || objectClass(subobject.declaration()).map(cls -> {
                            var constructors = cls.members().stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).filter(call -> call.kind() == CallableDeclaration.Kind.CONSTRUCTOR).toList();
                            return constructors.isEmpty() || constructors.stream().anyMatch(call -> !noThrow(call));
                        }).orElse(false);
                if (mayThrow) throwEdges(initializer, context, states.get(i), null, Branch.EXCEPTION);
                start = initializer;
            }
            edge(fn, start, Branch.ENTRY);
            if (unsupportedExceptionBoundary) return new FunctionFlow(fn, exit, List.of(), List.of(), false);
            Map<FlowVertex,List<FlowVertex>> successors = new IdentityHashMap<>();
            for (var edge : edges) successors.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
            Set<FlowVertex> reached = Collections.newSetFromMap(new IdentityHashMap<>());
            var work = new ArrayDeque<FlowVertex>(); work.push(fn);
            while (!work.isEmpty()) { var vertex = work.pop(); if (reached.add(vertex)) work.addAll(successors.getOrDefault(vertex, List.of())); }
            edges.removeIf(e -> !reached.contains(e.from()));
            return new FunctionFlow(fn, exit, edges, List.of());
        }
        boolean containsThrow(Node node) {
            if (node instanceof SizeofExpression || node instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile)) return false;
            return node instanceof ThrowExpression || node.children().stream().anyMatch(this::containsThrow);
        }
        boolean unsupportedEvaluation(Node node) {
            if (node instanceof ClassDeclaration || node instanceof CallableDeclaration) return false;
            if (node instanceof SizeofExpression || node instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile)) return false;
            if (node instanceof InitializationExpression e && e.form() == InitializationExpression.Form.TEMPORARY && e.type().flatMap(ClassSemantics::objectClass).isPresent()) return true;
            if (node instanceof Expression e && !(e instanceof InitializationExpression initialization && initialization.form() != InitializationExpression.Form.TEMPORARY)
                    && (e instanceof CallExpression || e.semanticInfo().map(info -> info.category() == ValueCategory.RVALUE).orElse(false))
                    && TypeSemantics.expressionType(e, profile).flatMap(ClassSemantics::objectClass).isPresent()
                    // A resolved class-reference return names existing storage; it
                    // does not introduce the unsupported record temporary lifetime.
                    && !(e instanceof CallExpression call && call.target().flatMap(CallableDeclaration::returnType)
                            .map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent())
                    && !trivialAssociativeInsertResult(e) && !modeledSequenceResult(e)) return true;
            if (node instanceof Expression e && containsThrow(e) && !special(e)
                    && !(e instanceof ConversionExpression) && !(e instanceof ConditionDeclarationExpression)) return true;
            return node.children().stream().anyMatch(this::unsupportedEvaluation);
        }
        /** A catalogue {@code pair<iterator,bool>} carries no user record lifetime. Do not
         * generalize this exception to arbitrary record-valued library calls. */
        boolean trivialAssociativeInsertResult(Expression expression) {
            if (!(expression instanceof CallExpression call)) return false;
            var operation = project.containerSemantics().stream().filter(o -> o.syntax() == call
                    && o.kind() == ContainerOperation.Kind.INSERT && o.type().uniqueKeys()
                    && o.sources().size() == 1 && LibraryDeclarations.isLibrary(o.selectedTarget())).findFirst();
            if (operation.isEmpty()) return false;
            var result = TypeSemantics.expressionType(call, profile).map(TypeSemantics::unqualified).orElse(null);
            if (!(result instanceof NamedType named)) return false;
            var pair = ClassSemantics.objectClass(named).orElse(null);
            if (pair == null || !pair.name().equals("pair") || !LibraryDeclarations.isLibrary(pair)) return false;
            var arguments = named.templateArguments().isEmpty() ? pair.templateArguments() : named.templateArguments();
            if (arguments.size() != 2 || arguments.get(1).type().map(TypeSemantics::unqualified)
                    .map(TypeNode::canonicalIdentity).filter("bool"::equals).isEmpty()) return false;
            return arguments.getFirst().type().flatMap(LibraryIteratorSemantics::info)
                    .filter(info -> info.container() == operation.get().type().concreteClass()).isPresent();
        }
        /** Only trusted iterator adapters and direct default-string materialization
         * have no unmodeled user-defined temporary cleanup in this abstraction. */
        boolean modeledSequenceResult(Expression expression) {
            var result = TypeSemantics.expressionType(expression, profile).flatMap(ClassSemantics::objectClass).orElse(null);
            if (result == null || !LibraryDeclarations.isLibrary(result)) return false;
            if (result.name().equals("reverse_iterator")
                    && TypeSemantics.expressionType(expression, profile).flatMap(LibraryIteratorSemantics::info).isPresent()
                    && (project.dynamicArraySemantics().stream().anyMatch(o -> o.syntax() == expression
                                && o.resultKind() == DynamicArrayOperation.ResultKind.ITERATOR)
                        || project.containerSemantics().stream().anyMatch(o -> o.syntax() == expression
                                && o.resultKind() == ContainerOperation.ResultKind.ITERATOR))) return true;
            var operation = project.sequenceSemantics().stream().filter(o -> o.syntax() == expression
                    && (LibraryDeclarations.isLibrary(o.selectedTarget()) || o.selectedTarget().templateOrigin().filter(LibraryDeclarations::isLibrary).isPresent())).findFirst().orElse(null);
            if (operation == null) {
                // str() may materialize the same trusted default string as substr().
                // Keep the call opaque: this only proves the destination lifetime shape.
                if (!(expression instanceof CallExpression call) || !call.arguments().isEmpty()
                        || !call.target().filter(LibraryCallEffects::unknown).map(t -> t.name().equals("str")).orElse(false)
                        || !(call.callee() instanceof MemberExpression member)
                        || TypeSemantics.expressionType(member.receiver(), profile).flatMap(ClassSemantics::objectClass)
                                .filter(LibraryDeclarations::isLibrary)
                                .filter(c -> Set.of("basic_stringstream", "basic_istringstream", "basic_ostringstream", "basic_stringbuf").contains(c.name())).isEmpty()) return false;
                if (!result.name().equals("basic_string") || result.templateArguments().size() != 3) return false;
                var arguments = result.templateArguments();
                return arguments.getFirst().type().map(TypeNode::canonicalIdentity).filter("char"::equals).isPresent()
                        && defaultCharacterPolicy(arguments.get(1).type(), "char_traits")
                        && defaultCharacterPolicy(arguments.get(2).type(), "allocator")
                        && directlyMaterialized(expression, result);
            }
            if ((operation.resultKind() == SequenceOperation.ResultKind.OUTPUT_ADAPTER
                    || operation.resultKind() == SequenceOperation.ResultKind.ITERATOR)
                    && Set.of("back_insert_iterator", "front_insert_iterator", "insert_iterator").contains(result.name()))
                return true;
            if (operation.resultKind() == SequenceOperation.ResultKind.ITERATOR
                    && result.name().equals("reverse_iterator"))
                return TypeSemantics.expressionType(expression, profile).flatMap(LibraryIteratorSemantics::info).isPresent();
            if (operation.kind() != SequenceOperation.Kind.STRING_SUBSTR || operation.characterType().isEmpty()) return false;
            return defaultStringMaterialization(expression, result, operation.characterType().orElseThrow());
        }
        boolean defaultStringMaterialization(Expression expression, ClassDeclaration result, CharacterSequenceType type) {
            if (!type.characterType().canonicalIdentity().equals("char")
                    || !defaultCharacterPolicy(type.traitsType(), "char_traits")
                    || !defaultCharacterPolicy(type.allocatorType(), "allocator")) return false;
            return directlyMaterialized(expression, result);
        }
        boolean directlyMaterialized(Expression expression, ClassDeclaration result) {
            // Do not admit a discarded temporary, nested argument, user conversion,
            // reference binding, or custom allocator/traits lifetime on this path.
            var materialized = new boolean[1];
            fn.body().ifPresent(body -> body.walk(new NodeVisitor() {
                @Override public void visit(VariableDeclaration variable) {
                    if (variable.initializer().map(Builder::writtenValue).orElse(null) == expression
                            && variable.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isEmpty()
                            && variable.type().flatMap(ClassSemantics::objectClass).filter(c -> c == result).isPresent())
                        materialized[0] = true;
                }
            }));
            return materialized[0];
        }
        static Expression writtenValue(Expression value) {
            while (value instanceof ConversionExpression conversion && !conversion.isExplicit()) value = conversion.operand();
            return value;
        }
        boolean defaultCharacterPolicy(Optional<TypeNode> policy, String name) {
            if (policy.isEmpty()) return false;
            var cls = policy.flatMap(ClassSemantics::objectClass).orElse(null);
            if (cls == null || !LibraryDeclarations.isLibrary(cls) || !cls.name().equals(name)) return false;
            var unqualified = TypeSemantics.unqualified(policy.orElseThrow());
            var arguments = unqualified instanceof NamedType named && !named.templateArguments().isEmpty()
                    ? named.templateArguments() : cls.templateArguments();
            return arguments.size() == 1 && arguments.getFirst().type()
                    .map(TypeNode::canonicalIdentity).filter("char"::equals).isPresent();
        }
        boolean unsupportedObject(Node node) {
            return node instanceof VariableDeclaration v && v.type().map(TypeSemantics::unqualified).filter(ArrayType.class::isInstance).isPresent()
                    && v.type().flatMap(ClassSemantics::objectClass).isPresent();
        }
        boolean automatic(Node n) {
            return n instanceof VariableDeclaration v && !v.modifiers().contains("static") && !v.modifiers().contains("extern")
                    && v.type().flatMap(ClassSemantics::objectClass).isPresent();
        }
        List<Node> append(List<Node> live, Node n) { var result = new ArrayList<>(live); if (automatic(n)) result.add(n); return List.copyOf(result); }
        void inventory(Node node, List<Node> live) {
            if (node instanceof ClassDeclaration || node instanceof CallableDeclaration) return;
            liveAt.put(node, live);
            if (node instanceof LabelStatement l) labels.put(l.label(), l);
            if (node instanceof BlockStatement b) {
                for (var item : b.items()) { inventory(item, live); live = append(live, item); }
            } else if (node instanceof CatchClause handler) {
                var inner = new ArrayList<Node>(live); handler.parameter().filter(p -> objectClass(p).isPresent()).ifPresent(inner::add); inventory(handler.body(), inner);
            } else if (node instanceof ForStatement loop) {
                for (var init : loop.initializers()) { inventory(init, live); live = append(live, init); }
                if (loop.condition().isPresent()) { inventory(loop.condition().get(), live); live = conditionLive(loop.condition().get(), live); }
                inventory(loop.body(), live); if (loop.update().isPresent()) inventory(loop.update().get(), live);
            } else if (node instanceof IfStatement branch) {
                inventory(branch.condition(), live); var inner = conditionLive(branch.condition(), live);
                inventory(branch.thenBranch(), inner); branch.elseBranch().ifPresent(b -> inventory(b, inner));
            } else if (node instanceof WhileStatement loop) {
                inventory(loop.condition(), live); inventory(loop.body(), conditionLive(loop.condition(), live));
            } else if (node instanceof SwitchStatement selection) {
                inventory(selection.condition(), live); inventory(selection.body(), conditionLive(selection.condition(), live));
            } else for (var child : node.children()) inventory(child, live);
        }
        Optional<ClassDeclaration> objectClass(Node object) {
            return object instanceof VariableDeclaration v ? v.type().flatMap(ClassSemantics::objectClass) : object instanceof ParameterDeclaration p ? p.type().flatMap(ClassSemantics::objectClass) : object instanceof BaseSpecifier b ? ClassSemantics.objectClass(b.type()) : object instanceof ClassDeclaration cls ? Optional.of(cls) : Optional.empty();
        }
        Optional<CallableDeclaration> defaultConstructor(Node object) {
            var candidates = objectClass(object).stream().flatMap(c -> c.members().stream()).filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast)
                    .filter(c -> c.kind() == CallableDeclaration.Kind.CONSTRUCTOR && c.parameters().stream().allMatch(p -> p.defaultValue().isPresent())).toList();
            return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
        }
        Optional<CallableDeclaration> destructor(Node object) {
            Optional<ClassDeclaration> cls = objectClass(object);
            return cls.stream().flatMap(c -> c.members().stream()).filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast)
                    .filter(c -> c.kind() == CallableDeclaration.Kind.DESTRUCTOR).findFirst();
        }
        FlowVertex cleanup(List<Node> live, int depth, FlowVertex next, Context context) {
            return cleanup(live, depth, next, context, false);
        }
        FlowVertex cleanup(List<Node> live, int depth, FlowVertex next, Context context, boolean unwinding) {
            for (int i = depth; i < live.size(); i++) {
                var object = live.get(i);
                var operation = new AuxiliaryFlowVertex(fn, sequence++, AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR, object, destructor(object).orElse(null));
                edge(operation, next, Branch.CLEANUP);
                if (operation.destructor().map(d -> !noThrow(d)).orElse(true)) {
                    if (unwinding) edge(operation, terminate, Branch.EXCEPTION);
                    else throwEdges(operation, context, live.subList(0, i), null, Branch.EXCEPTION);
                }
                next = operation;
            }
            return next;
        }
        Context handlers(List<CatchClause> clauses, FlowVertex next, Context outer, List<Node> live, boolean constructorHandler) {
            var list = new ArrayList<Handler>();
            for (var clause : clauses) {
                FlowVertex end = constructorHandler && (fn.kind() == CallableDeclaration.Kind.CONSTRUCTOR || fn.kind() == CallableDeclaration.Kind.DESTRUCTOR) ? cleanup(live, 0, escapingBoundary(null), outer, true) : next;
                var handlerLive = new ArrayList<Node>(live);
                clause.parameter().filter(p -> objectClass(p).isPresent()).ifPresent(handlerLive::add);
                var entry = connect(clause.body(), cleanup(handlerLive, live.size(), end, outer), Branch.NEXT, outer, handlerLive);
                edge(clause, entry, Branch.NEXT);
                if (handlerLive.size() != live.size()) edge(clause, terminate, Branch.EXCEPTION);
                list.add(new Handler(clause, clause));
            }
            var regions = new ArrayList<Region>(); regions.add(new Region(list, live.size())); regions.addAll(outer.regions());
            return new Context(outer.breakTo(), outer.continueTo(), List.copyOf(regions));
        }
        String exceptionType(Expression value) { return TypeSemantics.expressionType(value, profile).map(TypeSemantics::decay).map(TypeNode::canonicalIdentity).orElseGet(() -> value.semanticInfo().flatMap(ExpressionInfo::resultType).orElse(null)); }
        String caughtType(CatchClause clause) { return clause.parameter().flatMap(ParameterDeclaration::type).map(TypeNode::canonicalIdentity).orElse(null); }
        String plain(String type) {
            if (type == null) return null;
            while (type.startsWith("reference(") || type.startsWith("const(") || type.startsWith("volatile(")) type = type.substring(type.indexOf('(') + 1, type.length() - 1);
            return type;
        }
        boolean catches(String thrown, String caught) {
            thrown = plain(thrown); caught = plain(caught);
            if (Objects.equals(thrown, caught)) return true;
            if (thrown == null || caught == null) return false;
            if (thrown.startsWith("pointer(") && caught.startsWith("pointer(")) {
                thrown = thrown.substring(8, thrown.length() - 1); caught = caught.substring(8, caught.length() - 1);
                if (thrown.startsWith("const(") && !caught.startsWith("const(") || thrown.contains("volatile(") && !caught.contains("volatile(")) return false;
                thrown = plain(thrown); caught = plain(caught);
                if (caught.equals("void") || thrown.equals(caught)) return true;
            }
            if (!thrown.startsWith("named(") || !caught.startsWith("named(")) return false;
            final String identity = thrown.substring(6, thrown.length() - 1);
            var classes = new ArrayList<ClassDeclaration>();
            project.walk(new NodeVisitor() { @Override public void visit(ClassDeclaration cls) { if (cls.isDefinition() && cls.typeIdentity().filter(identity::equals).isPresent()) classes.add(cls); }});
            if (classes.size() != 1) return false;
            var paths = new HashMap<String, Boolean>();
            basePaths(classes.getFirst(), caught, "root", true, paths, new HashSet<>());
            return paths.size() == 1 && paths.values().iterator().next();
        }
        void basePaths(ClassDeclaration cls, String target, String path, boolean accessible, Map<String, Boolean> paths, Set<ClassDeclaration> active) {
            if (!active.add(cls)) return;
            for (int i = 0; i < cls.bases().size(); i++) {
                var base = cls.bases().get(i); var parent = InheritanceSemantics.baseClass(base).orElse(null); if (parent == null) continue;
                String identity = base.type().canonicalIdentity();
                String key = base.isVirtual() ? "virtual:" + identity : path + "/" + i;
                boolean publicPath = accessible && base.access().equals("public");
                if (identity.equals(target)) paths.merge(key, publicPath, (a, b) -> a || b);
                else basePaths(parent, target, key, publicPath, paths, active);
            }
            active.remove(cls);
        }
        void throwEdges(FlowVertex source, Context context, List<Node> live, String type, Branch kind) {
            for (var region : context.regions()) {
                for (var handler : region.handlers()) {
                    boolean catchall = handler.clause().parameter().isEmpty();
                    String caught = caughtType(handler.clause());
                    if (type == null || catchall || catches(type, caught)) {
                        edge(source, cleanup(live, region.depth(), handler.entry(), context, true), kind);
                        if (catchall || type != null) return;
                    }
                }
            }
            edge(source, cleanup(live, 0, escapingBoundary(type), context, true), kind);
        }
        FlowVertex escapingBoundary(String type) {
            if (!fn.hasExceptionSpecification()) return exceptional;
            if (fn.exceptionTypes().isEmpty()) return terminate;
            if (type == null || fn.exceptionTypes().stream().noneMatch(allowed -> catches(type, allowed.canonicalIdentity()))) {
                // A user-installed unexpected handler may substitute a permitted exception or terminate.
                // Without modelling that handler, no apparently complete ordinary exceptional edge is sound.
                unsupportedExceptionBoundary = true;
            }
            return exceptional;
        }
        boolean canThrow(Node node) {
            if (node instanceof ClassDeclaration || node instanceof CallableDeclaration) return false;
            if (language != Language.CPP || node instanceof SizeofExpression
                    || node instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile)) return false;
            if (node instanceof ThrowExpression || node instanceof NewExpression) return true;
            if (node instanceof CallExpression call) {
                boolean noThrow = call.target().map(this::noThrow).orElseGet(() -> TypeSemantics.callType(call, profile)
                        .filter(type -> type.hasExceptionSpecification() && type.exceptionTypes().isEmpty()).isPresent());
                if (!noThrow || call.defaultArguments().stream().anyMatch(a -> canThrow(a.expression()))) return true;
            }
            if (node instanceof Expression expression && expression.operatorTarget().filter(c -> !noThrow(c)).isPresent()) return true;
            if (node instanceof VariableDeclaration v && automatic(v)) {
                var candidates = ClassSemantics.constructorCandidates(ClassSemantics.initialization(project, v));
                if (candidates.isEmpty() || candidates.stream().anyMatch(c -> !noThrow(c))) return true;
            }
            return node.children().stream().anyMatch(this::canThrow);
        }
        boolean noThrow(CallableDeclaration callable) { return callable.hasExceptionSpecification() && callable.exceptionTypes().isEmpty(); }
        void exceptions(FlowVertex source, Node evaluated, Context c, List<Node> live, String type) {
            if (!canThrow(evaluated)) return;
            if (evaluated instanceof CallExpression call && call.children().stream().noneMatch(this::canThrow)
                    && call.defaultArguments().stream().noneMatch(a -> canThrow(a.expression()))) {
                var declared = call.target().filter(CallableDeclaration::hasExceptionSpecification);
                if (declared.isPresent()) {
                    for (var allowed : declared.get().exceptionTypes()) throwEdges(source, c, live, allowed.canonicalIdentity(), Branch.EXCEPTION);
                    return;
                }
            }
            throwEdges(source, c, live, type, Branch.EXCEPTION);
        }
        Expression written(Expression expression) {
            while (expression instanceof ConversionExpression c && !c.isExplicit()) expression = c.operand();
            return expression;
        }
        FlowVertex condition(Expression value, FlowVertex yes, FlowVertex no, Context c, List<Node> live) {
            var expression = written(value);
            if (expression instanceof AssignmentExpression assignment && assignment.target() instanceof NameExpression && special(assignment.value())) {
                edge(assignment, yes, Branch.TRUE); edge(assignment, no, Branch.FALSE);
                return evaluate(assignment.value(), assignment, c, live);
            }
            if (expression.operatorTarget().isEmpty()) {
                if (expression instanceof CommaExpression comma) return evaluate(comma.left(), condition(comma.right(), yes, no, c, live), c, live);
                if (expression instanceof LogicalAndExpression a) return condition(a.left(), condition(a.right(), yes, no, c, live), no, c, live);
                if (expression instanceof LogicalOrExpression o) return condition(o.left(), yes, condition(o.right(), yes, no, c, live), c, live);
                if (expression instanceof LogicalNotExpression n) return condition(n.operand(), no, yes, c, live);
                if (expression instanceof ConditionalExpression t) return condition(t.condition(), condition(t.thenExpression(), yes, no, c, live), condition(t.elseExpression(), yes, no, c, live), c, live);
            }
            if (expression instanceof ThrowExpression t) { t.value().ifPresent(v -> exceptions(expression, v, c, live, null)); throwEdges(expression, c, live, t.value().map(this::exceptionType).orElse(null), Branch.THROW); return expression; }
            Optional<Boolean> truth = language == Language.CPP ? ConstantEvaluator.evaluate(value, profile).map(v -> v.number().signum() != 0)
                    : expression instanceof BooleanLiteral literal ? Optional.of(literal.value()) : Optional.empty();
            if (truth.isEmpty() || truth.get()) edge(value, yes, Branch.TRUE);
            if (truth.isEmpty() || !truth.get()) edge(value, no, Branch.FALSE);
            exceptions(value, value, c, live, null); return value;
        }
        FlowVertex evaluate(Expression value, FlowVertex next, Context c, List<Node> live) {
            var expression = written(value);
            if (expression instanceof ThrowExpression t) { t.value().ifPresent(v -> exceptions(expression, v, c, live, null)); throwEdges(expression, c, live, t.value().map(this::exceptionType).orElse(null), Branch.THROW); return expression; }
            if (expression instanceof AssignmentExpression assignment && assignment.target() instanceof NameExpression && special(assignment.value())) {
                edge(assignment, next, Branch.NEXT); return evaluate(assignment.value(), assignment, c, live);
            }
            if (expression instanceof CommaExpression comma && expression.operatorTarget().isEmpty()) return evaluate(comma.left(), evaluate(comma.right(), next, c, live), c, live);
            if (expression.operatorTarget().isEmpty() && (expression instanceof LogicalAndExpression || expression instanceof LogicalOrExpression)) return condition(value, next, next, c, live);
            if (expression instanceof ConditionalExpression t) return condition(t.condition(), evaluate(t.thenExpression(), next, c, live), evaluate(t.elseExpression(), next, c, live), c, live);
            edge(value, next, Branch.NEXT); exceptions(value, value, c, live, null); return value;
        }
        FlowVertex connect(Node statement, FlowVertex next, Branch fallThrough, Context c, List<Node> live) {
            if (statement instanceof BlockStatement block) {
                var states = new ArrayList<List<Node>>(); var current = live;
                for (var item : block.items()) { states.add(current); current = append(current, item); }
                FlowVertex first = cleanup(current, live.size(), next, c);
                for (int i = block.items().size() - 1; i >= 0; i--) first = connect(block.items().get(i), first, first == next ? fallThrough : Branch.NEXT, c, states.get(i));
                return first;
            }
            if (statement instanceof EmptyStatement || statement instanceof ClassDeclaration || statement instanceof CallableDeclaration) return next;
            if (statement instanceof IfStatement branch) {
                var inner = conditionLive(branch.condition(), live); var after = cleanup(inner, live.size(), next, c);
                return condition(branch.condition(), connect(branch.thenBranch(), after, fallThrough, c, inner), branch.elseBranch().map(b -> connect(b, after, fallThrough, c, inner)).orElse(after), c, live);
            }
            if (statement instanceof WhileStatement loop) {
                var test = loop.condition();
                var inner = conditionLive(test, live); var repeat = cleanup(inner, live.size(), test, c); var after = cleanup(inner, live.size(), next, c);
                var body = connect(loop.body(), repeat, Branch.BACK, c.loop(new Target(next, live.size()), new Target(test, live.size())), inner);
                var entry = condition(test, body, after, c, live);
                if (entry != test) redirect(test, entry);
                return entry;
            }
            if (statement instanceof DoWhileStatement loop) {
                var test = loop.condition();
                var body = connect(loop.body(), test, Branch.BACK, c.loop(new Target(next, live.size()), new Target(test, live.size())), live);
                var entry = condition(test, body, next, c, live); if (entry != test) redirect(test, entry); return body;
            }
            if (statement instanceof ForStatement loop) {
                var innerLive = live; var states = new ArrayList<List<Node>>();
                for (var init : loop.initializers()) { states.add(innerLive); innerLive = append(innerLive, init); }
                var after = cleanup(innerLive, live.size(), next, c);
                FlowVertex anchor = loop.condition().<FlowVertex>map(v -> v).orElse(loop);
                var iterationLive = loop.condition().isPresent() ? conditionLive(loop.condition().get(), innerLive) : innerLive;
                var repeat = cleanup(iterationLive, innerLive.size(), anchor, c);
                var update = loop.update().isPresent() ? evaluate(loop.update().get(), repeat, c, iterationLive) : repeat;
                var body = connect(loop.body(), update, Branch.BACK, c.loop(new Target(after, innerLive.size()), new Target(update, iterationLive.size())), iterationLive);
                var failedCondition = cleanup(iterationLive, innerLive.size(), after, c);
                FlowVertex entry;
                if (loop.condition().isPresent()) { entry = condition(loop.condition().get(), body, failedCondition, c, innerLive); if (entry != anchor) redirect(anchor, entry); }
                else { edge(anchor, body, Branch.TRUE); entry = anchor; }
                for (int i = loop.initializers().size() - 1; i >= 0; i--) entry = connect(loop.initializers().get(i), entry, Branch.NEXT, c, states.get(i));
                return entry;
            }
            if (statement instanceof BreakStatement) { if (c.breakTo() != null) edge(statement, cleanup(live, c.breakTo().depth(), c.breakTo().vertex(), c), Branch.BREAK); return statement; }
            if (statement instanceof ContinueStatement) { if (c.continueTo() != null) edge(statement, cleanup(live, c.continueTo().depth(), c.continueTo().vertex(), c), Branch.CONTINUE); return statement; }
            if (statement instanceof GotoStatement jump) {
                var label = labels.get(jump.label()); if (label != null) edge(statement, cleanup(live, common(live, liveAt.getOrDefault(label, List.of())), label, c), Branch.GOTO); return statement;
            }
            if (statement instanceof LabelStatement label) { edge(label, connect(label.statement(), next, fallThrough, c, live), Branch.NEXT); return label; }
            if (statement instanceof CaseStatement label) { edge(label, connect(label.statement(), next, fallThrough, c, live), Branch.NEXT); return label; }
            if (statement instanceof SwitchStatement selection) {
                var inner = conditionLive(selection.condition(), live); var after = cleanup(inner, live.size(), next, c);
                connect(selection.body(), after, fallThrough, c.loop(new Target(next, live.size()), c.continueTo()), inner);
                var cases = new ArrayList<CaseStatement>(); collectCases(selection.body(), cases);
                for (var label : cases) edge(selection.condition(), label, label.value().isPresent() ? Branch.CASE : Branch.DEFAULT);
                if (cases.stream().noneMatch(label -> label.value().isEmpty())) edge(selection.condition(), after, Branch.DEFAULT);
                exceptions(selection.condition(), selection.condition(), c, live, null); return selection.condition();
            }
            if (statement instanceof TryStatement attempt) return connect(attempt.body(), next, fallThrough, handlers(attempt.handlers(), next, c, live, false), live);
            if (statement instanceof ReturnStatement result) {
                edge(statement, cleanup(live.stream().filter(object -> !retainedSubobjects.contains(object)).toList(), 0, exit, c), Branch.RETURN);
                if (result.value().isPresent()) { var value = result.value().get(); if (special(value)) return evaluate(value, statement, c, live); exceptions(statement, value, c, live, null); }
                return statement;
            }
            if (statement instanceof VariableDeclaration variable && variable.initializer().filter(this::special).isPresent()) {
                edge(variable, next, fallThrough);
                if (automatic(variable)) throwEdges(variable, c, live, null, Branch.EXCEPTION);
                return evaluate(variable.initializer().orElseThrow(), variable, c, live);
            }
            if (statement instanceof ExpressionStatement e && (special(e.expression()) || written(e.expression()) instanceof CallExpression)) return evaluate(e.expression(), next, c, live);
            edge(statement, next, fallThrough); exceptions(statement, statement, c, live, null); return statement;
        }
        List<Node> conditionLive(Expression value, List<Node> live) {
            var expression = written(value);
            return expression instanceof ConditionDeclarationExpression declaration ? append(live, declaration.declaration()) : live;
        }
        boolean special(Expression value) { var e = written(value); return e instanceof AssignmentExpression a && a.target() instanceof NameExpression && special(a.value()) || e instanceof ThrowExpression || e instanceof ConditionalExpression || e.operatorTarget().isEmpty() && (e instanceof LogicalAndExpression || e instanceof LogicalOrExpression || e instanceof CommaExpression); }
        int common(List<Node> a, List<Node> b) { int i = 0; while (i < a.size() && i < b.size() && a.get(i) == b.get(i)) i++; return i; }
        void redirect(FlowVertex old, FlowVertex replacement) { for (int i = 0; i < edges.size(); i++) { var e = edges.get(i); if (e.to() == old) edges.set(i, new ControlEdge(e.from(), replacement, e.kind())); } }
        void collectCases(Node node, List<CaseStatement> result) { if (node instanceof SwitchStatement) return; if (node instanceof CaseStatement label) result.add(label); for (var child : node.children()) collectCases(child, result); }
    }
}
