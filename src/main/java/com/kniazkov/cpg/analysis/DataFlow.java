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

    private DataFlow(Language language, PlatformProfile profile) {
        this.language = language;
        this.profile = profile;
    }

    /** Uses the CFG for this snapshot (building it if necessary) and attaches deduplicated data edges. */
    public static Project analyze(Project project) {
        var cfg = project.flows().isEmpty() ? ControlFlow.analyze(project) : project;
        Map<CallableDeclaration, Language> languages = new IdentityHashMap<>();
        Map<CallableDeclaration, PlatformProfile> profiles = new IdentityHashMap<>();
        for (SourceFile file : cfg.files()) file.walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration fn) { languages.put(fn, file.language()); profiles.put(fn, TypeSemantics.profile(file)); }
        });
        return cfg.withFlows(cfg.flows().stream()
                .map(flow -> new DataFlow(languages.get(flow.entry()), profiles.get(flow.entry())).analyzeFunction(flow)).toList());
    }

    private FunctionFlow analyzeFunction(FunctionFlow flow) {
        if (!flow.complete()) return flow;
        Map<FlowVertex,List<FlowVertex>> successors = new HashMap<>();
        for (var edge : flow.control())
            successors.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());

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
            for (var successor : successors.getOrDefault(vertex, List.of())) {
                var input = inputs.get(successor);
                if (input == null) {
                    inputs.put(successor, output.copy());
                    work.add(successor);
                } else if (input.merge(output)) work.add(successor);
            }
        }
        Set<DataEdge> edges = new LinkedHashSet<>();
        inputs.forEach((vertex, input) -> transfer(vertex, input.copy(),
                (from, to, kind) -> edges.add(new DataEdge(from, to, kind))));
        return new FunctionFlow(flow.entry(), flow.exit(), flow.control(), List.copyOf(edges));
    }

    /** A program point's possible most recent writes, indexed by declaration identity. */
    private static final class State {
        private final Map<Declaration,Set<Node>> definitions = new LinkedHashMap<>();

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
            return changed;
        }

        void write(Declaration target, Node definition) {
            definitions.put(target, new LinkedHashSet<>(List.of(definition)));
        }
    }

    @FunctionalInterface
    private interface Sink {
        void edge(Node from, Node to, Dependency kind);
    }

    /** Executes only the current CFG instruction. Its successor instructions run through the worklist. */
    private void transfer(FlowVertex vertex, State state, Sink sink) {
        switch (vertex) {
            case VariableDeclaration v -> {
                state.definitions.remove(v);
                v.initializer().ifPresent(value -> {
                    consume(value, v, state, sink);
                    state.write(v, v);
                });
            }
            case MemberInitializer initializer -> initializer.arguments().forEach(argument -> consume(argument, initializer, state, sink));
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

    /** Evaluates value operands and models assignment as a write after the right-hand side has been read. */
    private void evaluate(Expression value, State state, Sink sink) {
        if (value instanceof SizeofExpression) {
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
        } else if (language == Language.CPP && OperatorSemantics.mayInvokeUserCode(value)) {
            // Operator functions evaluate all arguments with unspecified relative order.
            // In particular overloaded && and || do not short circuit.
            arguments(OperatorSemantics.operands(value), value, state, sink);
            // Unknown operator bodies may mutate objects passed by reference. Retain both
            // incoming and possible call definitions, rather than claiming a definite write.
            for (var operand : OperatorSemantics.operands(value)) possibleMutation(operand, value, state);
        } else if (value instanceof NameExpression use) {
            use.target().ifPresent(target -> {
                for (Node write : state.definitions.getOrDefault(target, Set.of()))
                    sink.edge(write, use, Dependency.DEFINITION);
            });
        } else if (value instanceof AssignmentExpression a) {
            if (language == Language.JAVA) address(a, state, sink);
            consume(a.value(), a, state, sink);
            if (language == Language.CPP) address(a, state, sink);
            if (a.target() instanceof NameExpression name) {
                name.target().ifPresent(target -> state.write(target, a));
            }
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
                state.definitions.clear(); state.merge(positive); state.merge(negative);
            }
        } else if (value instanceof CallExpression call && language == Language.CPP) {
            consume(call.callee(), call, state, sink);
            arguments(call, state, sink);
        } else {
            for (Node child : value.children())
                if (child instanceof Expression operand) consume(operand, value, state, sink);
        }
    }

    private static void possibleMutation(Expression operand, Node call, State state) {
        if (operand instanceof ConversionExpression conversion) possibleMutation(conversion.operand(), call, state);
        else if (operand instanceof NameExpression name) name.target().ifPresent(target -> {
            var definitions = new LinkedHashSet<>(state.definitions.getOrDefault(target, Set.of()));
            definitions.add(call); state.definitions.put(target, definitions);
        });
        else if (operand instanceof MemberExpression member) possibleMutation(member.receiver(), call, state);
        else if (operand instanceof AssignmentExpression assignment) possibleMutation(assignment.target(), call, state);
    }

    private void address(AssignmentExpression assignment, State state, Sink sink) {
        if (assignment.target() instanceof MemberExpression member) {
            consume(member.receiver(), assignment, state, sink);
        } else if (assignment.target() instanceof DereferenceExpression pointer) {
            consume(pointer.operand(), assignment, state, sink);
        } else if (assignment.target() instanceof IndexExpression index) {
            consume(index.left(), assignment, state, sink);
            consume(index.right(), assignment, state, sink);
        }
    }

    private static void writeTarget(Expression target, Node update, State state) {
        if (target instanceof NameExpression name) name.target().ifPresent(declaration -> state.write(declaration, update));
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
        for (Expression arg : arguments) {
            State effect = new State();
            evaluate(arg, effect, (from, to, kind) -> {});
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
    }
}
