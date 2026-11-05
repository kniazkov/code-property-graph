/* Code Property Graph: immutable control and data dependencies of one callable. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Per-function analysis over existing snapshot nodes. Its terminal is the exit owned by the entry declaration. */
public record FunctionFlow(CallableDeclaration entry, Exit exit, List<ControlEdge> control, List<DataEdge> data, boolean complete) {

    /** Compatibility constructor for a complete analysis. */
    public FunctionFlow(CallableDeclaration entry, Exit exit, List<ControlEdge> control, List<DataEdge> data) { this(entry, exit, control, data, true); }

    public enum Branch { ENTRY, NEXT, TRUE, FALSE, RETURN, BACK, EXCEPTION, THROW, CLEANUP, BREAK, CONTINUE, GOTO, CASE, DEFAULT }
    public enum Dependency { VALUE, DEFINITION }
    /** Directed transfer between instruction, condition, entry or exit vertices. */
    public record ControlEdge(FlowVertex from, FlowVertex to, Branch kind) {
        public ControlEdge { Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(kind); }
    }
    /** Direction is from the producer of a value or reaching definition to its consumer. */
    public record DataEdge(Node from, Node to, Dependency kind) {
        public DataEdge { Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(kind); }
    }

    /** Flow-owned operations, ordered deterministically within their callable. */
    public List<AuxiliaryFlowVertex> auxiliaryVertices() {
        return control.stream().flatMap(e -> java.util.stream.Stream.of(e.from(), e.to()))
                .filter(AuxiliaryFlowVertex.class::isInstance).map(AuxiliaryFlowVertex.class::cast)
                .distinct().sorted(Comparator.comparingInt(AuxiliaryFlowVertex::index)).toList();
    }
    public Optional<AuxiliaryFlowVertex> exceptionalExit() {
        return auxiliaryVertices().stream().filter(v -> v.kind() == AuxiliaryFlowVertex.Kind.EXCEPTIONAL_EXIT).findFirst();
    }

    public FunctionFlow {
        Objects.requireNonNull(entry);
        if (exit != entry.exit()) throw new IllegalArgumentException("Exit belongs to another callable");
        control = List.copyOf(control);
        data = List.copyOf(data);
        if (!complete && (!control.isEmpty() || !data.isEmpty())) throw new IllegalArgumentException("Incomplete flow must not publish partial edges");
        Set<FlowVertex> members = Collections.newSetFromMap(new IdentityHashMap<>());
        entry.walk(new NodeVisitor() {
            @Override public void visit(Node node) { members.add(node); }
        });
        var indices = new HashMap<Integer, AuxiliaryFlowVertex>();
        for (ControlEdge edge : control) for (FlowVertex vertex : List.of(edge.from(), edge.to())) {
            if (vertex instanceof AuxiliaryFlowVertex auxiliary) {
                if (auxiliary.owner() != entry)
                    throw new IllegalArgumentException("Foreign auxiliary flow operation");
                var previous = indices.putIfAbsent(auxiliary.index(), auxiliary);
                if (previous != null && previous != auxiliary) throw new IllegalArgumentException("Duplicate auxiliary flow index");
            }
        }
        members.addAll(indices.values());
        for (ControlEdge edge : control) {
            if (!members.contains(edge.from()) || !members.contains(edge.to()) || edge.from() == exit || edge.from() instanceof AuxiliaryFlowVertex a && (a.kind() == AuxiliaryFlowVertex.Kind.EXCEPTIONAL_EXIT || a.kind() == AuxiliaryFlowVertex.Kind.TERMINATE_EXIT))
                throw new IllegalArgumentException("Invalid control-flow endpoint");
        }
        // Defaults remain owned by their declaration. Only expressions actually
        // referenced by calls in this function may be external data-flow endpoints.
        Set<Node> evaluated = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Node>();
        pending.add(entry);
        while (!pending.isEmpty()) {
            Node node = pending.remove();
            if (!evaluated.add(node)) continue;
            pending.addAll(node.children());
            if (node instanceof CallExpression call)
                call.defaultArguments().forEach(argument -> pending.add(argument.expression()));
        }
        // Implicit subobject lifetime operations evaluate their referenced object,
        // which remains owned by its class rather than by this callable.
        // Project.withFlows separately verifies every auxiliary object is project-owned.
        for (var auxiliary : indices.values()) auxiliary.object().ifPresent(evaluated::add);
        for (DataEdge edge : data) {
            if (!evaluated.contains(edge.from()) || !evaluated.contains(edge.to()))
                throw new IllegalArgumentException("Foreign data-flow endpoint");
        }
    }
}
