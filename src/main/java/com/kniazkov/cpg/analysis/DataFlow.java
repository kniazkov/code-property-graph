/* Code Property Graph: reaching definitions and value dependencies. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.FunctionFlow.*;
import java.util.*;

/**
 * Computes forward may-reaching definitions on each callable's reachable CFG.
 * States are keyed by resolved declarations, so shadowed names remain distinct.
 * Joins accumulate definitions and a write replaces the set for its own target.
 * A worklist revisits successors until even loop back edges contribute no new facts.
 */
public final class DataFlow {
    private final Language language;
    private final PlatformProfile profile;
    private Set<Declaration> libraryAccessible = Set.of();
    private final Map<Node, DynamicArrayOperation> arrays = new IdentityHashMap<>();
    private final Map<Declaration, Declaration> arrayAliases = new IdentityHashMap<>();
    private final Map<Node, Expression> expandedValues = new IdentityHashMap<>();
    private final Map<Node, List<DataEdge>> expandedDependencies = new IdentityHashMap<>();

    private DataFlow(Language language, PlatformProfile profile) {
        this.language = language;
        this.profile = profile;
    }

    /** Uses the CFG for this snapshot (building it if necessary) and attaches deduplicated data edges. */
    public static Project analyze(Project project) {
        var cfg = project.flows().isEmpty() ? ControlFlow.analyze(project) : project;
        var contexts = SourceContexts.index(cfg);
        return cfg.withFlows(cfg.flows().stream().map(flow -> {
            SourceFile source = SourceContexts.require(contexts, flow.entry());
            return new DataFlow(source.language(), TypeSemantics.profile(source)).analyzeFunction(flow, cfg.dynamicArraySemantics());
        }).toList());
    }

    private FunctionFlow analyzeFunction(FunctionFlow flow, List<DynamicArrayOperation> operations) {
        operations.forEach(operation -> arrays.put(operation.syntax(), operation));
        flow.entry().walk(new NodeVisitor() {
            @Override public void visit(VariableDeclaration variable) {
                if (variable.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent()
                        && variable.type().map(TypeSemantics::withoutReference).flatMap(ClassSemantics::objectClass)
                                .filter(cls -> operations.stream().anyMatch(op -> op.type().concreteClass() == cls)).isPresent())
                    variable.initializer().ifPresent(value -> {
                        var roots = LibraryCallEffects.roots(value);
                        if (roots.size() == 1) arrayAliases.put(variable, roots.iterator().next());
                    });
            }
        });
        if (!flow.complete()) return flow;
        libraryAccessible = LibraryCallEffects.accessible(flow.entry(), profile);
        // Sequenced/branching values may already execute as separate CFG vertices.
        // Detect that ownership from actual edges, rather than duplicating CFG grammar.
        for (var edge : flow.control()) if (edge.from() instanceof Expression completed && edge.to() instanceof Node consumer) {
            Expression value = consumer instanceof VariableDeclaration variable ? variable.initializer().orElse(null)
                    : consumer instanceof ReturnStatement result ? result.value().orElse(null)
                    : consumer instanceof AssignmentExpression assignment && assignment.target() instanceof NameExpression ? assignment.value() : null;
            if (value != null && contains(value, completed)) {
                expandedValues.put(consumer, value);
            }
        }
        Map<FlowVertex,List<FlowVertex>> normalPredecessors = new IdentityHashMap<>();
        for (var edge : flow.control()) if (edge.kind() != Branch.EXCEPTION && edge.kind() != Branch.THROW)
            normalPredecessors.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge.from());
        expandedValues.forEach((consumer, value) -> {
            Set<FlowVertex> completes = Collections.newSetFromMap(new IdentityHashMap<>());
            var pending = new ArrayDeque<FlowVertex>(); pending.add(consumer);
            while (!pending.isEmpty()) {
                var vertex = pending.remove();
                if (completes.add(vertex)) pending.addAll(normalPredecessors.getOrDefault(vertex, List.of()));
            }
            var dependencies = new ArrayList<DataEdge>();
            expandedDependencies(value, completes, dependencies);
            expandedDependencies.put(consumer, dependencies);
        });
        Map<FlowVertex,List<ControlEdge>> successors = new HashMap<>();
        for (var edge : flow.control())
            successors.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);

        Map<FlowVertex, State> inputs = new LinkedHashMap<>();
        var entry = new State();
        flow.entry().parameters().forEach(parameter -> entry.write(parameter, parameter));
        inputs.put(flow.entry(), entry);
        var work = new ArrayDeque<FlowVertex>();
        work.add(flow.entry());
        while (!work.isEmpty()) {
            var vertex = work.remove();
            State output = inputs.get(vertex).copy();
            transfer(vertex, output, (from, to, kind) -> {});
            for (var edge : successors.getOrDefault(vertex, List.of())) {
                var successor = edge.to();
                State propagated = output;
                if (edge.kind() == Branch.EXCEPTION) {
                    // A potentially throwing instruction may fail before its final write.
                    propagated = output.copy();
                    propagated.merge(inputs.get(vertex));
                }
                var input = inputs.get(successor);
                if (input == null) {
                    inputs.put(successor, propagated.copy());
                    work.add(successor);
                } else if (input.merge(propagated)) work.add(successor);
            }
        }
        Set<DataEdge> edges = new LinkedHashSet<>();
        inputs.forEach((vertex, input) -> transfer(vertex, input.copy(),
                (from, to, kind) -> edges.add(new DataEdge(from, to, kind))));
        return new FunctionFlow(flow.entry(), flow.exit(), flow.control(), List.copyOf(edges), flow.complete());
    }

    /** Restore value structure for expanded selectors/arms without executing them again. */
    private static boolean expandedDependencies(Expression value, Set<FlowVertex> completes, List<DataEdge> edges) {
        boolean contributes = completes.contains(value);
        for (Node child : value.children()) if (child instanceof Expression expression
                && expandedDependencies(expression, completes, edges)) {
            edges.add(new DataEdge(expression, value, Dependency.VALUE));
            contributes = true;
        }
        return contributes;
    }

    private static boolean contains(Node root, Node candidate) {
        if (root == candidate) return true;
        for (Node child : root.children()) if (contains(child, candidate)) return true;
        return false;
    }

    /** A program point's possible most recent writes, indexed by declaration identity. */
    private static final class State {
        private final Map<Declaration,Set<Node>> definitions = new LinkedHashMap<>();
        private final Map<Declaration,Set<ElementBinding>> elements = new LinkedHashMap<>();

        State copy() {
            State state = new State();
            state.merge(this);
            return state;
        }

        boolean merge(State other) {
            boolean changed = false;
            for (var entry : other.definitions.entrySet()) {
                var values = definitions.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>());
                changed |= values.addAll(entry.getValue());
            }
            for (var entry : other.elements.entrySet())
                changed |= elements.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).addAll(entry.getValue());
            return changed;
        }

        void write(Declaration target, Node definition) {
            definitions.put(target, new LinkedHashSet<>(List.of(definition)));
        }
    }

    /** Element storage is distinct from a reference to the container object itself. */
    private record ElementBinding(DynamicArrayOperation producer, Declaration sequence,
            boolean iterator, boolean writable, boolean uncertain) { }

    @FunctionalInterface
    private interface Sink {
        void edge(Node from, Node to, Dependency kind);
    }

    /** Executes only the current CFG instruction. Its successor instructions run through the worklist. */
    private void transfer(FlowVertex vertex, State state, Sink sink) {
        if (vertex instanceof Node consumer && expandedValues.containsKey(consumer)) {
            Expression value = expandedValues.get(consumer);
            for (DataEdge edge : expandedDependencies.get(consumer)) sink.edge(edge.from(), edge.to(), edge.kind());
            sink.edge(value, consumer, Dependency.VALUE);
            if (consumer instanceof VariableDeclaration variable) { state.write(variable, variable); bindElement(variable, value, state); constructLibraryObject(variable, state, sink); }
            else if (consumer instanceof AssignmentExpression assignment) {
                if (arrays.containsKey(assignment)) {
                    arrayEffect(arrays.get(assignment), state, sink);
                    sink.edge(arrays.get(assignment), assignment, Dependency.VALUE);
                } else writeTarget(assignment.target(), assignment, state);
            }
            return;
        }
        switch (vertex) {
            case VariableDeclaration v -> {
                state.definitions.remove(v);
                state.elements.remove(v);
                v.initializer().ifPresent(value -> {
                    consume(value, v, state, sink);
                    state.write(v, v);
                    bindElement(v, value, state);
                });
                constructLibraryObject(v, state, sink);
            }
            case AuxiliaryFlowVertex auxiliary -> {
                if (auxiliary.kind() == AuxiliaryFlowVertex.Kind.IMPLICIT_CONSTRUCTOR
                        && auxiliary.constructor().filter(LibraryCallEffects::unknown).isPresent()) {
                    auxiliary.object().ifPresent(object -> {
                        if (object instanceof Declaration declaration) state.write(declaration, object);
                        opaqueMutation(object, libraryAccessible, state, sink);
                    });
                }
                if (auxiliary.kind() == AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR)
                    auxiliary.object().filter(Declaration.class::isInstance).map(Declaration.class::cast).ifPresent(object -> {
                        // Destruction reads the final object state, but is not a written call.
                        for (Node write : state.definitions.getOrDefault(object, Set.of()))
                            sink.edge(write, object, Dependency.DEFINITION);
                        var destructor = auxiliary.destructor();
                        if (destructor.isEmpty() && object instanceof VariableDeclaration variable) destructor = variable.destructorTarget();
                        if (destructor.filter(LibraryCallEffects::unknown).isPresent()) opaqueMutation(object, libraryAccessible, state, sink);
                    });
            }
            case CatchClause handler -> handler.parameter().ifPresent(parameter -> state.write(parameter, parameter));
            case MemberInitializer initializer -> {
                initializer.arguments().forEach(argument -> consume(argument, initializer, state, sink));
                if (initializer.target().filter(LibraryCallEffects::libraryObject).isPresent()) {
                    var exposed = new LinkedHashSet<Declaration>(libraryAccessible);
                    initializer.arguments().forEach(argument -> exposed.addAll(LibraryCallEffects.roots(argument)));
                    opaqueMutation(initializer, exposed, state, sink);
                }
            }
            case ExpressionStatement s -> evaluate(s.expression(), state, sink);
            case Expression condition -> evaluate(condition, state, sink);
            case ReturnStatement ret -> ret.value().ifPresent(value -> consume(value, ret, state, sink));
            case PrintStatement print -> consume(print.value(), print, state, sink);
            default -> { /* Entry parameters were seeded above. Exit has no effects. */ }
        }
    }

    private void consume(Expression value, Node consumer, State state, Sink sink) {
        evaluate(value, state, sink);
        sink.edge(value, consumer, Dependency.VALUE);
    }

    private void constructLibraryObject(VariableDeclaration variable, State state, Sink sink) {
        if (arrays.containsKey(variable)) { arrayEffect(arrays.get(variable), state, sink); return; }
        if (variable.constructorTarget().filter(LibraryCallEffects::unknown).isEmpty()) return;
        state.write(variable, variable);
        var exposed = new LinkedHashSet<Declaration>(libraryAccessible);
        variable.initializer().ifPresent(value -> value.walk(new NodeVisitor() {
            @Override public void visit(NameExpression use) { exposed.addAll(LibraryCallEffects.roots(use)); }
        }));
        opaqueMutation(variable, exposed, state, sink);
    }

    private Declaration alias(Declaration target) {
        Set<Declaration> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (arrayAliases.containsKey(target) && seen.add(target)) target = arrayAliases.get(target);
        return target;
    }

    /** Semantic writes change a container's contents, never its reference identity. */
    private void arrayEffect(DynamicArrayOperation operation, State state, Sink sink) {
        for (Node source : operation.sources()) sink.edge(source, operation, Dependency.VALUE);
        Set<Declaration> destinations = new LinkedHashSet<>();
        for (Node object : operation.mutatedObjects()) destinations.addAll(sequenceRoots(object));
        // Queries and accessors read the actual sequence state; they never replace it.
        // Mutations also depend on the previous size/content/lifetime of the object.
        var reads = new LinkedHashSet<>(sequenceRoots(operation.destination()));
        reads.addAll(destinations);
        for (Declaration destination : reads)
            for (Node definition : state.definitions.getOrDefault(destination, Set.of()))
                sink.edge(definition, operation, Dependency.DEFINITION);
        // Record lifetime/copy/assignment code can read and change accessible state.
        // Pure accessors do not invoke such element code.
        if (!destinations.isEmpty() && operation.kind() != DynamicArrayOperation.Kind.EMPTY
                && operation.kind() != DynamicArrayOperation.Kind.SWAP
                && (operation.elementCopyConstructor().isPresent()
                    || ClassSemantics.objectClass(operation.type().elementType()).isPresent())) {
            var exposed = new LinkedHashSet<Declaration>(libraryAccessible);
            for (Node source : operation.sources()) if (source instanceof Expression expression)
                exposed.addAll(LibraryCallEffects.roots(expression));
            opaqueMutation(operation, exposed, state, sink);
        }
        if (operation.kind() == DynamicArrayOperation.Kind.SWAP && destinations.size() == 2) {
            var objects = new ArrayList<>(destinations);
            state.elements.replaceAll((variable, bindings) -> {
                var moved = new LinkedHashSet<ElementBinding>();
                for (var binding : bindings) {
                    Declaration sequence = binding.sequence();
                    if (sequence == objects.get(0)) sequence = objects.get(1);
                    else if (sequence == objects.get(1)) sequence = objects.get(0);
                    moved.add(new ElementBinding(binding.producer(), sequence, binding.iterator(),
                            binding.writable(), binding.uncertain()));
                }
                return moved;
            });
        } else if (!destinations.isEmpty()) {
            // Reallocation and positional invalidation are may-effects without a
            // proven capacity/index relation. Never promote a stale handle to valid.
            state.elements.replaceAll((variable, bindings) -> {
                var invalidated = new LinkedHashSet<ElementBinding>();
                for (var binding : bindings) {
                    if (!destinations.contains(binding.sequence())) { invalidated.add(binding); continue; }
                    var effect = operation.handleEffects().stream().filter(e -> e.producer() == binding.producer()).findFirst();
                    boolean uncertain = effect.map(e -> e.validityAfter() != DynamicArrayOperation.Handle.Validity.VALID).orElse(true);
                    invalidated.add(new ElementBinding(binding.producer(), binding.sequence(), binding.iterator(), binding.writable(), uncertain));
                }
                return invalidated;
            });
        }
        for (Declaration destination : destinations) state.write(destination, operation);
    }

    private Set<Declaration> sequenceRoots(Node object) {
        var result = new LinkedHashSet<Declaration>();
        if (object instanceof Declaration declaration) result.add(alias(declaration));
        else if (object instanceof Expression expression)
            LibraryCallEffects.roots(expression).forEach(root -> result.add(alias(root)));
        return result;
    }

    private Set<ElementBinding> elementBindings(Expression value, State state) {
        if (value instanceof ConversionExpression conversion) return elementBindings(conversion.operand(), state);
        DynamicArrayOperation operation = arrays.get(value);
        if (operation != null && operation.handle().isPresent()) {
            var handle = operation.handle().get();
            var result = new LinkedHashSet<ElementBinding>();
            for (Declaration sequence : sequenceRoots(handle.sequence()))
                result.add(new ElementBinding(operation, sequence,
                        handle.kind() == DynamicArrayOperation.Handle.Kind.ITERATOR, handle.writable(),
                        handle.validity() != DynamicArrayOperation.Handle.Validity.VALID));
            return result;
        }
        if (value instanceof NameExpression name)
            return name.target().map(target -> state.elements.getOrDefault(target, Set.of())).orElse(Set.of());
        if (value instanceof DereferenceExpression || value instanceof IndexExpression) {
            Expression iterator = value instanceof DereferenceExpression dereference ? dereference.operand() : ((IndexExpression) value).left();
            var result = new LinkedHashSet<ElementBinding>();
            for (var binding : elementBindings(iterator, state)) if (binding.iterator())
                result.add(new ElementBinding(binding.producer(), binding.sequence(), false, binding.writable(), binding.uncertain()));
            return result;
        }
        if (value instanceof MemberExpression member) {
            var result = new LinkedHashSet<ElementBinding>();
            for (var binding : elementBindings(member.receiver(), state)) if (!binding.iterator()) result.add(binding);
            return result;
        }
        if (value instanceof AddExpression || value instanceof SubtractExpression) {
            var binary = (BinaryExpression) value;
            var result = new LinkedHashSet<ElementBinding>();
            for (var binding : elementBindings(binary.left(), state)) if (binding.iterator()) result.add(binding);
            if (value instanceof AddExpression)
                for (var binding : elementBindings(binary.right(), state)) if (binding.iterator()) result.add(binding);
            return result;
        }
        return Set.of();
    }

    private void bindElement(Declaration declaration, Expression value, State state) {
        Set<ElementBinding> bindings = elementBindings(value, state);
        boolean reference = declaration instanceof VariableDeclaration variable && variable.type()
                .map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent();
        var retained = new LinkedHashSet<ElementBinding>();
        for (var binding : bindings) if (reference || binding.iterator()) retained.add(binding);
        if (!retained.isEmpty()) state.elements.put(declaration, retained);
    }

    private void opaqueMutation(Node operation, Set<Declaration> exposed, State state, Sink sink) {
        var storage = new LinkedHashSet<Declaration>();
        for (Declaration exposedTarget : exposed) {
            storage.add(alias(exposedTarget));
            for (var binding : state.elements.getOrDefault(exposedTarget, Set.of())) storage.add(binding.sequence());
        }
        for (Declaration target : storage) {
            var definitions = new LinkedHashSet<>(state.definitions.getOrDefault(target, Set.of()));
            for (Node definition : definitions) sink.edge(definition, operation, Dependency.DEFINITION);
            definitions.add(operation);
            state.definitions.put(target, definitions);
        }
    }

    /** Evaluates value operands and models assignment as a write after the right-hand side has been read. */
    private void evaluate(Expression value, State state, Sink sink) {
        DynamicArrayOperation operation = arrays.get(value);
        if (operation != null) {
            // Evaluate preserved written operands once; common nodes reference their results.
            if (value instanceof CallExpression call) {
                consume(call.callee(), call, state, sink);
                arguments(call, state, sink);
            } else if (OperatorSemantics.mayInvokeUserCode(value))
                arguments(OperatorSemantics.operands(value), value, state, sink);
            else if (value instanceof AssignmentExpression assignment) {
                consume(assignment.value(), assignment, state, sink);
                address(assignment, state, sink);
            }
            arrayEffect(operation, state, sink);
            sink.edge(operation, value, Dependency.VALUE);
            return;
        }
        if (value instanceof ConditionDeclarationExpression condition) {
            VariableDeclaration declaration = condition.declaration();
            state.definitions.remove(declaration);
            declaration.initializer().ifPresent(initializer -> {
                consume(initializer, declaration, state, sink);
                state.write(declaration, declaration);
            });
            constructLibraryObject(declaration, state, sink);
            sink.edge(declaration, condition, Dependency.DEFINITION);
        } else if (value instanceof SizeofExpression) {
            // C++03 sizeof does not read or execute any expression in its operand.
            return;
        } else if (value instanceof TypeidExpression typeid) {
            if (!TypeSemantics.typeidOperandUnevaluated(typeid, profile)) typeid.operand().ifPresent(operand -> {
                // Until polymorphism is proven, either execution is possible.
                State evaluated = state.copy();
                consume(operand, typeid, evaluated, sink);
                state.merge(evaluated);
            });
        } else if (value instanceof NewExpression allocation) {
            var arguments = new ArrayList<Expression>(allocation.placementArguments());
            TypeNode type = allocation.type();
            while (type instanceof ArrayType array) {
                array.bound().ifPresent(arguments::add);
                type = array.elementType();
            }
            arguments.addAll(allocation.initializerArguments());
            arguments(arguments, allocation, state, sink);
        } else if ((value instanceof DereferenceExpression || value instanceof IndexExpression) && !elementBindings(value, state).isEmpty()) {
            for (Node child : value.children()) if (child instanceof Expression operand) consume(operand, value, state, sink);
            for (var binding : elementBindings(value, state))
                for (Node write : state.definitions.getOrDefault(binding.sequence(), Set.of()))
                    sink.edge(write, value, Dependency.DEFINITION);
        } else if (language == Language.CPP && OperatorSemantics.mayInvokeUserCode(value)) {
            // Operator functions evaluate all arguments with unspecified relative order.
            // In particular overloaded && and || do not short circuit.
            arguments(OperatorSemantics.operands(value), value, state, sink);
            // Unknown operator bodies may mutate objects passed by reference. Retain both
            // incoming and possible call definitions, rather than claiming a definite write.
            for (var operand : OperatorSemantics.operands(value)) possibleMutation(operand, value, state);
            if (LibraryCallEffects.unknown(value)) opaqueMutation(value, libraryAccessible, state, sink);
        } else if (value instanceof NameExpression use) {
            use.target().ifPresent(target -> {
                for (Node write : state.definitions.getOrDefault(alias(target), Set.of()))
                    sink.edge(write, use, Dependency.DEFINITION);
                for (var binding : state.elements.getOrDefault(target, Set.of()))
                    if (!binding.iterator()) for (Node write : state.definitions.getOrDefault(binding.sequence(), Set.of()))
                        sink.edge(write, use, Dependency.DEFINITION);
            });
        } else if (value instanceof AssignmentExpression a) {
            if (language == Language.JAVA) address(a, state, sink);
            consume(a.value(), a, state, sink);
            if (language == Language.CPP) address(a, state, sink);
            writeTarget(a.target(), a, state);
            if (a.target() instanceof NameExpression name) name.target().ifPresent(target -> {
                if (state.elements.getOrDefault(target, Set.of()).stream().anyMatch(ElementBinding::iterator)
                        || TypeSemantics.expressionType(a.target(), profile).flatMap(LibraryIteratorSemantics::info).isPresent()) {
                    state.elements.remove(target);
                    bindElement(target, a.value(), state);
                }
            });
        } else if (value instanceof CompoundAssignmentExpression assignment) {
            consume(assignment.target(), assignment, state, sink);
            consume(assignment.value(), assignment, state, sink);
            writeTarget(assignment.target(), assignment, state);
        } else if (value instanceof PrefixIncrementExpression || value instanceof PrefixDecrementExpression
                || value instanceof PostfixIncrementExpression || value instanceof PostfixDecrementExpression) {
            var update = (UnaryExpression) value;
            consume(update.operand(), update, state, sink);
            writeTarget(update.operand(), update, state);
        } else if (value instanceof LogicalAndExpression || value instanceof LogicalOrExpression) {
            var logical = (BinaryExpression) value;
            consume(logical.left(), logical, state, sink);
            var truth = knownTruth(logical.left());
            boolean and = logical instanceof LogicalAndExpression;
            if (truth.isPresent()) {
                if (truth.get() == and) consume(logical.right(), logical, state, sink);
            } else {
                State evaluated = state.copy();
                consume(logical.right(), logical, evaluated, sink);
                state.merge(evaluated);
            }
        } else if (value instanceof ConditionalExpression conditional) {
            consume(conditional.condition(), conditional, state, sink);
            var truth = knownTruth(conditional.condition());
            if (truth.isPresent()) consume(truth.get() ? conditional.thenExpression()
                    : conditional.elseExpression(), conditional, state, sink);
            else {
                State positive = state.copy(), negative = state.copy();
                consume(conditional.thenExpression(), conditional, positive, sink);
                consume(conditional.elseExpression(), conditional, negative, sink);
                state.definitions.clear(); state.elements.clear(); state.merge(positive); state.merge(negative);
            }
        } else if (value instanceof CallExpression call && language == Language.CPP) {
            consume(call.callee(), call, state, sink);
            arguments(call, state, sink);
            if (LibraryCallEffects.unknown(call)) {
                var exposed = new LinkedHashSet<Declaration>(libraryAccessible);
                LibraryCallEffects.exposed(call).forEach(expression -> exposed.addAll(LibraryCallEffects.roots(expression)));
                opaqueMutation(call, exposed, state, sink);
            }
        } else {
            for (Node child : value.children())
                if (child instanceof Expression operand) consume(operand, value, state, sink);
        }
    }

    private void possibleMutation(Expression operand, Node call, State state) {
        if (operand instanceof ConversionExpression conversion) possibleMutation(conversion.operand(), call, state);
        else if (operand instanceof NameExpression name) name.target().ifPresent(target -> {
            var definitions = new LinkedHashSet<>(state.definitions.getOrDefault(target, Set.of()));
            definitions.add(call); state.definitions.put(target, definitions);
            for (var binding : state.elements.getOrDefault(target, Set.of())) if (binding.writable())
                state.definitions.computeIfAbsent(binding.sequence(), ignored -> new LinkedHashSet<>()).add(call);
        });
        else if (operand instanceof MemberExpression member) possibleMutation(member.receiver(), call, state);
        else if (operand instanceof AssignmentExpression assignment) possibleMutation(assignment.target(), call, state);
    }

    private void address(AssignmentExpression assignment, State state, Sink sink) {
        if (arrays.containsKey(assignment.target())) {
            consume(assignment.target(), assignment, state, sink);
        } else if (assignment.target() instanceof MemberExpression member) {
            consume(member.receiver(), assignment, state, sink);
        } else if (assignment.target() instanceof DereferenceExpression pointer) {
            consume(pointer.operand(), assignment, state, sink);
        } else if (assignment.target() instanceof IndexExpression index) {
            consume(index.left(), assignment, state, sink);
            consume(index.right(), assignment, state, sink);
        }
    }

    private void writeTarget(Expression target, Node update, State state) {
        if (target instanceof ConversionExpression conversion) { writeTarget(conversion.operand(), update, state); return; }
        var bindings = elementBindings(target, state);
        boolean indirect = !(target instanceof NameExpression) || bindings.stream().anyMatch(binding -> !binding.iterator());
        if (indirect && !bindings.isEmpty()) {
            for (var binding : bindings) if (binding.writable()) {
                // An element update does not overwrite every other element. Retain
                // prior definitions, also covering ambiguous/invalidated handles.
                state.definitions.computeIfAbsent(binding.sequence(), ignored -> new LinkedHashSet<>()).add(update);
            }
        } else if (target instanceof NameExpression name)
            name.target().ifPresent(declaration -> state.write(alias(declaration), update));
    }

    /** Proven target integer constants select a branch; unknown conditions retain both effects. */
    private Optional<Boolean> knownTruth(Expression expression) {
        if (language == Language.CPP) return ConstantEvaluator.evaluate(expression, profile)
                .map(value -> value.number().signum() != 0);
        if (expression instanceof BooleanLiteral literal) return Optional.of(literal.value());
        if (expression instanceof IntegerLiteral literal) return Optional.of(literal.bigValue().signum() != 0);
        return Optional.empty();
    }

    /**
     * Conservatively joins argument effects without asserting an evaluation order.
     * A read in one argument may see an incoming or sibling definition. This is
     * not a proof that conflicting C++03 argument accesses are well-defined.
     */
    private void arguments(CallExpression call, State state, Sink sink) {
        var arguments = new ArrayList<Expression>(call.arguments());
        call.defaultArguments().forEach(argument -> arguments.add(argument.expression()));
        arguments(arguments, call, state, sink);
    }

    private void arguments(List<Expression> arguments, Node consumer, State state, Sink sink) {
        List<State> writes = new ArrayList<>();
        State possibleLast = new State();
        var rebound = new LinkedHashSet<Declaration>();
        for (Expression arg : arguments) {
            // Binding-sensitive effects need the incoming reference/iterator state.
            State effect = state.copy();
            evaluate(arg, effect, (from, to, kind) -> {});
            effect.definitions.entrySet().removeIf(entry -> entry.getValue().equals(state.definitions.get(entry.getKey())));
            var bindings = new LinkedHashSet<>(state.elements.keySet()); bindings.addAll(effect.elements.keySet());
            for (Declaration binding : bindings)
                if (!Objects.equals(state.elements.get(binding), effect.elements.get(binding))) rebound.add(binding);
            effect.elements.entrySet().removeIf(entry -> entry.getValue().equals(state.elements.get(entry.getKey())));
            writes.add(effect);
            possibleLast.merge(effect);
        }
        for (int i = 0; i < writes.size(); i++) {
            State before = state.copy();
            for (int j = 0; j < writes.size(); j++)
                if (j != i) before.merge(writes.get(j));
            consume(arguments.get(i), consumer, before, sink);
        }
        possibleLast.definitions.forEach(state.definitions::put);
        rebound.forEach(state.elements::remove);
        possibleLast.elements.forEach(state.elements::put);
    }
}
