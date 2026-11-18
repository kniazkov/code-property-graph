/* Local reaching installations of the C++03 unexpected handler. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.ControlEdge;
import java.util.*;

/** Forward may analysis. Entry state and opaque invocations never guess a handler. */
final class UnexpectedHandlerFlow {
    private UnexpectedHandlerFlow() {}
    private record State(Set<CallableDeclaration> targets, boolean unknown, boolean frozen) {
        State { targets = Collections.unmodifiableSet(new LinkedHashSet<>(targets)); }
        State merge(State other) {
            var union = new LinkedHashSet<>(targets); union.addAll(other.targets);
            return frozen != other.frozen ? UNKNOWN : new State(union, unknown || other.unknown, frozen);
        }
    }
    private static final State UNKNOWN = new State(Set.of(), true, false);

    static FunctionFlow bind(FunctionFlow flow, PlatformProfile profile) {
        if (flow.auxiliaryVertices().stream().noneMatch(a -> a.kind() == AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL)) return flow;
        Map<FlowVertex, State> before = new IdentityHashMap<>();
        Map<FlowVertex, List<ControlEdge>> outgoing = new IdentityHashMap<>();
        for (var edge : flow.control()) outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
        var work = new ArrayDeque<FlowVertex>(); work.add(flow.entry()); before.put(flow.entry(), UNKNOWN);
        while (!work.isEmpty()) {
            var vertex = work.remove();
            var after = transfer(vertex, before.get(vertex), profile);
            for (var edge : outgoing.getOrDefault(vertex, List.of())) {
                var propagated = edge.kind() == FunctionFlow.Branch.THROW || edge.kind() == FunctionFlow.Branch.EXCEPTION
                        ? new State(after.targets(), after.unknown(), true) : after;
                var old = before.get(edge.to());
                var merged = old == null ? propagated : old.merge(propagated);
                if (!merged.equals(old)) { before.put(edge.to(), merged); work.add(edge.to()); }
            }
        }
        Map<FlowVertex, FlowVertex> replacements = new IdentityHashMap<>();
        for (var vertex : flow.auxiliaryVertices()) if (vertex.kind() == AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL) {
            var state = before.getOrDefault(vertex, UNKNOWN);
            replacements.put(vertex, new AuxiliaryFlowVertex(vertex.owner(), vertex.index(), vertex.kind(),
                    vertex.object().orElse(null), null, OptionalInt.empty(), List.copyOf(state.targets()), state.unknown()));
        }
        return new FunctionFlow(flow.entry(), flow.exit(), flow.control().stream().map(e -> new ControlEdge(
                replacements.getOrDefault(e.from(), e.from()), replacements.getOrDefault(e.to(), e.to()), e.kind())).toList(), flow.data(), flow.complete());
    }

    private static State transfer(FlowVertex vertex, State state, PlatformProfile profile) {
        // [lib.unexpected]: implementation-selected handler is captured after throw evaluation,
        // before unwinding. A catch resumes execution with an unknown current installation.
        if (vertex instanceof CatchClause) return UNKNOWN;
        if (state.frozen()) return state;
        if (vertex instanceof AuxiliaryFlowVertex auxiliary) return switch (auxiliary.kind()) {
            case IMPLICIT_CONSTRUCTOR, IMPLICIT_DESTRUCTOR, ARRAY_DEALLOCATION, UNEXPECTED_CALL -> UNKNOWN;
            default -> state;
        };
        if (!(vertex instanceof Node node) || node instanceof CallableDeclaration || node instanceof Exit
                || node instanceof LabelStatement || node instanceof CaseStatement || node instanceof ForStatement) return state;
        // Only a direct setter with an effect-free function designator strongly replaces state.
        if (node instanceof CallExpression call && call.target().filter(LibraryDeclarations::isLibrary)
                .filter(t -> t.name().equals("set_unexpected")).isPresent() && call.arguments().size() == 1) {
            Expression argument = call.arguments().getFirst();
            while (argument instanceof ConversionExpression conversion && !conversion.isExplicit()) argument = conversion.operand();
            if (argument instanceof AddressOfExpression address) argument = address.operand();
            if (argument instanceof NameExpression name && name.target().orElse(null) instanceof CallableDeclaration handler)
                return new State(Set.of(handler), false, false);
            return UNKNOWN;
        }
        return opaque(node, profile) ? UNKNOWN : state;
    }

    private static boolean opaque(Node node, PlatformProfile profile) {
        if (node instanceof SizeofExpression || node instanceof OffsetofExpression
                || node instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile)) return false;
        if (node instanceof CallExpression || node instanceof NewExpression || node instanceof DeleteExpression
                || node instanceof MemberInitializer || node instanceof CatchClause) return true;
        if (node instanceof Expression expression && (expression.operatorTarget().isPresent()
                || TypeSemantics.expressionType(expression, profile).flatMap(ClassSemantics::objectClass).isPresent())) return true;
        if (node instanceof VariableDeclaration variable && variable.type().flatMap(ClassSemantics::objectClass).isPresent()) return true;
        return node.children().stream().anyMatch(child -> opaque(child, profile));
    }
}
