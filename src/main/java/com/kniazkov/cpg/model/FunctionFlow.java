/* Code Property Graph: immutable control and data dependencies of one callable. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Per-function analysis over existing snapshot nodes. Its terminal is the exit owned by the entry declaration. */
public record FunctionFlow(CallableDeclaration entry, Exit exit, List<ControlEdge> control, List<DataEdge> data, boolean complete) {

    /** Compatibility constructor for a complete analysis. */
    public FunctionFlow(CallableDeclaration entry, Exit exit, List<ControlEdge> control, List<DataEdge> data) { this(entry, exit, control, data, true); }

    public enum Branch { ENTRY, NEXT, TRUE, FALSE, RETURN, BACK }
    public enum Dependency { VALUE, DEFINITION }
    /** Directed transfer between instruction, condition, entry or exit vertices. */
    public record ControlEdge(FlowVertex from, FlowVertex to, Branch kind) {
        public ControlEdge { Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(kind); }
    }
    /** Direction is from the producer of a value or reaching definition to its consumer. */
    public record DataEdge(Node from, Node to, Dependency kind) {
        public DataEdge { Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(kind); }
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
        for (ControlEdge edge : control) {
            if (!members.contains(edge.from()) || !members.contains(edge.to()) || edge.from() == exit)
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
        for (DataEdge edge : data) {
            if (!evaluated.contains(edge.from()) || !evaluated.contains(edge.to()))
                throw new IllegalArgumentException("Foreign data-flow endpoint");
        }
    }
}
