/* Reaching C++03 unexpected installations and bounded direct-call summaries. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.ControlEdge;
import java.util.*;

/** Forward may analysis; external entry state, recursion and opaque effects remain unknown. */
final class UnexpectedHandlerFlow {
    private UnexpectedHandlerFlow() {}
    private record Value(Set<CallableDeclaration> targets, boolean unknown) {
        Value { targets = Collections.unmodifiableSet(new LinkedHashSet<>(targets)); }
        Value merge(Value other) {
            var union = new LinkedHashSet<>(targets); union.addAll(other.targets);
            return new Value(union, unknown || other.unknown);
        }
    }
    private static final Value UNKNOWN = new Value(Set.of(), true);
    private record State(Value current, Map<Declaration, Value> locals, boolean frozen) {
        State { locals = Map.copyOf(locals); }
        State merge(State other) {
            var values = new HashMap<Declaration, Value>();
            var keys = new HashSet<>(locals.keySet()); keys.addAll(other.locals.keySet());
            for (var key : keys) values.put(key, locals.getOrDefault(key, UNKNOWN).merge(other.locals.getOrDefault(key, UNKNOWN)));
            return new State(frozen == other.frozen ? current.merge(other.current) : UNKNOWN, values, frozen && other.frozen);
        }
        State write(Declaration local, Value value) {
            var values = new HashMap<>(locals); values.put(local, value);
            return new State(current, values, frozen);
        }
        State freeze() { return new State(current, locals, true); }
    }
    private static State unknownState() { return new State(UNKNOWN, Map.of(), false); }
    private record Evaluation(State state, Value value, Value escaping) {}
    private record Result(Map<FlowVertex, State> before, State normal, Value returned, Value escaping) {}

    /** Each callable retains unknown external entry context; only its direct calls are specialized. */
    static List<FunctionFlow> bind(List<FunctionFlow> flows, Map<Node, SourceFile> contexts) {
        var engine = new Engine(flows, contexts);
        var result = new ArrayList<FunctionFlow>();
        for (var flow : flows) {
            if (!flow.complete() || flow.auxiliaryVertices().stream().noneMatch(a -> a.kind() == AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL)) {
                result.add(flow); continue;
            }
            engine.remaining = 2000;
            var analysis = engine.solve(flow, unknownState(), new HashSet<>());
            Map<FlowVertex, FlowVertex> replacements = new IdentityHashMap<>();
            for (var vertex : flow.auxiliaryVertices()) if (vertex.kind() == AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL) {
                var value = analysis.before().getOrDefault(vertex, unknownState()).current();
                replacements.put(vertex, new AuxiliaryFlowVertex(vertex.owner(), vertex.index(), vertex.kind(),
                        vertex.object().orElse(null), null, OptionalInt.empty(), List.copyOf(value.targets()), value.unknown()));
            }
            result.add(new FunctionFlow(flow.entry(), flow.exit(), flow.control().stream().map(e -> new ControlEdge(
                    replacements.getOrDefault(e.from(), e.from()), replacements.getOrDefault(e.to(), e.to()), e.kind())).toList(), flow.data(), flow.complete()));
        }
        return List.copyOf(result);
    }

    private static final class Engine {
        private final Map<CallableDeclaration, FunctionFlow> functions = new IdentityHashMap<>();
        private final Map<Node, SourceFile> contexts;
        private int remaining;
        Engine(List<FunctionFlow> flows, Map<Node, SourceFile> contexts) {
            flows.forEach(f -> functions.put(f.entry(), f)); this.contexts = contexts;
        }
        Result solve(FunctionFlow flow, State initial, Set<CallableDeclaration> active) {
            if (!flow.complete() || active.contains(flow.entry()) || active.size() >= 8 || --remaining < 0)
                return new Result(Map.of(), unknownState(), UNKNOWN, UNKNOWN);
            var stack = new HashSet<>(active); stack.add(flow.entry());
            var profile = TypeSemantics.profile(SourceContexts.require(contexts, flow.entry()));
            var locals = new HashSet<Declaration>();
            flow.entry().parameters().stream().filter(p -> p.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isEmpty()).forEach(locals::add);
            flow.entry().body().ifPresent(body -> body.walk(new NodeVisitor() {
                @Override public void visit(VariableDeclaration variable) {
                    if (!variable.modifiers().contains("static") && !variable.modifiers().contains("extern")
                            && variable.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isEmpty()) locals.add(variable);
                }
            }));
            // Address-taken or reference-aliased slots are never treated as private scalar bindings.
            var escaped = new HashSet<Declaration>();
            flow.entry().body().ifPresent(body -> body.walk(new NodeVisitor() {
                @Override public void visit(Node node) {
                    if (node instanceof AddressOfExpression address && unwrap(address.operand()) instanceof NameExpression name)
                        name.target().ifPresent(escaped::add);
                    if (node instanceof VariableDeclaration variable && variable.type().map(TypeSemantics::unqualified)
                            .filter(ReferenceType.class::isInstance).isPresent()) escaped.addAll(locals);
                }
            }));
            locals.removeAll(escaped);
            Map<FlowVertex, State> before = new IdentityHashMap<>();
            Map<FlowVertex, List<ControlEdge>> outgoing = new IdentityHashMap<>();
            for (var edge : flow.control()) outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            var work = new ArrayDeque<FlowVertex>(); work.add(flow.entry()); before.put(flow.entry(), initial);
            Value returned = null;
            while (!work.isEmpty()) {
                var vertex = work.remove();
                var evaluated = transfer(vertex, before.get(vertex), locals, profile, stack);
                if (vertex instanceof ReturnStatement) returned = returned == null ? evaluated.value() : returned.merge(evaluated.value());
                for (var edge : outgoing.getOrDefault(vertex, List.of())) {
                    var propagated = evaluated.state();
                    if (edge.kind() == FunctionFlow.Branch.THROW) propagated = propagated.freeze();
                    else if (edge.kind() == FunctionFlow.Branch.EXCEPTION)
                        propagated = new State(evaluated.escaping(), Map.of(), true);
                    var old = before.get(edge.to());
                    var merged = old == null ? propagated : old.merge(propagated);
                    if (!merged.equals(old)) { before.put(edge.to(), merged); work.add(edge.to()); }
                }
            }
            Value escaping = flow.exceptionalExit().map(v -> before.getOrDefault(v, unknownState()).current()).orElse(UNKNOWN);
            return new Result(before, before.getOrDefault(flow.exit(), unknownState()), returned == null ? UNKNOWN : returned, escaping);
        }
        Evaluation transfer(FlowVertex vertex, State state, Set<Declaration> locals, PlatformProfile profile, Set<CallableDeclaration> active) {
            if (vertex instanceof CatchClause) return uncertain();
            if (vertex instanceof AuxiliaryFlowVertex auxiliary && auxiliary.kind() == AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL) return uncertain();
            if (state.frozen()) return new Evaluation(state, UNKNOWN, state.current());
            if (vertex instanceof AuxiliaryFlowVertex auxiliary) return switch (auxiliary.kind()) {
                case IMPLICIT_CONSTRUCTOR, IMPLICIT_DESTRUCTOR, ARRAY_DEALLOCATION -> uncertain();
                default -> new Evaluation(state, UNKNOWN, state.current());
            };
            if (!(vertex instanceof Node node) || node instanceof CallableDeclaration || node instanceof Exit
                    || node instanceof LabelStatement || node instanceof CaseStatement || node instanceof ForStatement)
                return new Evaluation(state, UNKNOWN, state.current());
            if (node instanceof VariableDeclaration variable) {
                if (variable.type().flatMap(ClassSemantics::objectClass).isPresent()) return uncertain();
                var value = variable.initializer().map(e -> evaluate(e, state, locals, profile, active))
                        .orElse(new Evaluation(state, UNKNOWN, UNKNOWN));
                return new Evaluation(locals.contains(variable) ? value.state().write(variable, value.value()) : unknownState(), UNKNOWN, value.escaping());
            }
            if (node instanceof ExpressionStatement statement) return evaluate(statement.expression(), state, locals, profile, active);
            if (node instanceof ReturnStatement statement) return statement.value().map(e -> evaluate(e, state, locals, profile, active))
                    .orElse(new Evaluation(state, UNKNOWN, UNKNOWN));
            if (node instanceof Expression expression) return evaluate(expression, state, locals, profile, active);
            return opaque(node, profile) ? uncertain() : new Evaluation(state, UNKNOWN, UNKNOWN);
        }
        Evaluation evaluate(Expression expression, State state, Set<Declaration> locals, PlatformProfile profile, Set<CallableDeclaration> active) {
            var value = unwrap(expression);
            if (value instanceof CallExpression call) return call(call, state, locals, profile, active);
            if (value instanceof AssignmentExpression assignment && assignment.operatorTarget().isEmpty()) {
                var right = evaluate(assignment.value(), state, locals, profile, active);
                var target = unwrap(assignment.target());
                if (target instanceof NameExpression name && name.target().filter(locals::contains).isPresent())
                    return new Evaluation(right.state().write(name.target().orElseThrow(), right.value()), right.value(), right.escaping());
                // A direct integral object cannot alias a saved function pointer or
                // change the process handler. Keep evaluating the RHS for real calls.
                if(target instanceof NameExpression name && name.target().orElse(null) instanceof VariableDeclaration variable
                        && variable.type().map(TypeSemantics::unqualified)
                            .filter(type -> !(type instanceof ReferenceType) && TypeSemantics.integralOrEnum(type,profile)).isPresent())
                    return new Evaluation(right.state(),right.value(),right.escaping());
                return uncertain(); // alias writes may overwrite a saved handler pointer
            }
            if (value instanceof InitializationExpression init && init.arguments().size() == 1)
                return evaluate(init.arguments().getFirst(), state, locals, profile, active);
            if (value instanceof ThrowExpression thrown) {
                var operand = thrown.value().map(e -> evaluate(e, state, locals, profile, active))
                        .orElse(new Evaluation(state, UNKNOWN, UNKNOWN));
                return new Evaluation(operand.state(), UNKNOWN, operand.escaping());
            }
            if (opaque(value, profile)) return uncertain();
            return new Evaluation(state, designator(value, state, locals), UNKNOWN);
        }
        Evaluation call(CallExpression call, State state, Set<Declaration> locals, PlatformProfile profile, Set<CallableDeclaration> active) {
            if (!(unwrap(call.callee()) instanceof NameExpression || unwrap(call.callee()) instanceof ScopeMemberExpression)
                    || call.isVirtualDispatch() || !call.defaultArguments().isEmpty()
                    || call.arguments().stream().anyMatch(a -> opaque(a, profile))) return uncertain();
            if (call.target().filter(LibraryDeclarations::isLibrary).filter(t -> t.name().equals("set_unexpected")).isPresent()
                    && call.arguments().size() == 1) {
                var target = designator(call.arguments().getFirst(), state, locals);
                return new Evaluation(new State(target, state.locals(), false), state.current(), UNKNOWN);
            }
            var target = call.target().orElse(null);
            if (target == null || LibraryDeclarations.isLibrary(target)) return uncertain();
            if (!functions.containsKey(target)) target = target.definition().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).orElse(target);
            var function = functions.get(target);
            if (function == null || call.arguments().size() != target.parameters().size()
                    || target.parameters().stream().anyMatch(p -> p.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent())) return uncertain();
            var parameters = new HashMap<Declaration, Value>();
            for (int i = 0; i < call.arguments().size(); i++) {
                var argument = unwrap(call.arguments().get(i));
                // Taking a local address allows the callee to overwrite the caller's saved pointer.
                if (containsLocalAddress(argument, locals)) return uncertain();
                parameters.put(target.parameters().get(i), designator(argument, state, locals));
            }
            var result = solve(function, new State(state.current(), parameters, false), active);
            // Callee-local bindings never escape into the caller's environment.
            return new Evaluation(new State(result.normal().current(), state.locals(), false), result.returned(), result.escaping());
        }
    }
    private static Evaluation uncertain() { return new Evaluation(unknownState(), UNKNOWN, UNKNOWN); }
    private static Expression unwrap(Expression value) {
        while (value instanceof ConversionExpression conversion && !conversion.isExplicit()) value = conversion.operand();
        return value;
    }
    private static Value designator(Expression expression, State state, Set<Declaration> locals) {
        var value = unwrap(expression);
        if (value instanceof AddressOfExpression address) {
            value = unwrap(address.operand());
            if (value instanceof ScopeMemberExpression member && member.target().orElse(null) instanceof CallableDeclaration handler)
                return new Value(Set.of(handler), false);
            if (!(value instanceof NameExpression name) || !(name.target().orElse(null) instanceof CallableDeclaration)) return UNKNOWN;
        }
        if (value instanceof ScopeMemberExpression member && member.target().orElse(null) instanceof CallableDeclaration handler)
            return new Value(Set.of(handler), false);
        if (value instanceof NameExpression name) {
            if (name.target().orElse(null) instanceof CallableDeclaration handler) return new Value(Set.of(handler), false);
            if (name.target().filter(locals::contains).isPresent()) return state.locals().getOrDefault(name.target().orElseThrow(), UNKNOWN);
        }
        return UNKNOWN;
    }
    private static boolean containsLocalAddress(Node node, Set<Declaration> locals) {
        if (node instanceof AddressOfExpression address && unwrap(address.operand()) instanceof NameExpression name
                && name.target().filter(locals::contains).isPresent()) return true;
        return node.children().stream().anyMatch(n -> containsLocalAddress(n, locals));
    }
    private static boolean opaque(Node node, PlatformProfile profile) {
        if (node instanceof SizeofExpression || node instanceof OffsetofExpression
                || node instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile)) return false;
        if (node instanceof CallExpression || node instanceof NewExpression || node instanceof DeleteExpression
                || node instanceof CompoundAssignmentExpression || node instanceof PrefixIncrementExpression
                || node instanceof PrefixDecrementExpression || node instanceof PostfixIncrementExpression
                || node instanceof PostfixDecrementExpression || node instanceof AssignmentExpression || node instanceof MemberInitializer || node instanceof CatchClause) return true;
        if (node instanceof Expression expression && (expression.operatorTarget().isPresent()
                || TypeSemantics.expressionType(expression, profile).flatMap(ClassSemantics::objectClass).isPresent())) return true;
        if (node instanceof VariableDeclaration variable && variable.type().flatMap(ClassSemantics::objectClass).isPresent()) return true;
        return node.children().stream().anyMatch(child -> opaque(child, profile));
    }
}
