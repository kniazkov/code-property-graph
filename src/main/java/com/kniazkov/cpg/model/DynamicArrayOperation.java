/* Owned language-independent dynamic-array semantics. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Derived semantics referencing preserved written syntax; references never own syntax. */
public final class DynamicArrayOperation extends Node {
    public enum Kind { EMPTY, COUNT, FILL, RANGE, COPY, ASSIGN_COPY, ASSIGN_FILL, ASSIGN_RANGE, APPEND }
    private Kind kind;
    private List<Node> sources = List.of();
    public Kind kind() { return kind; }
    /** Ordered inputs: count/value, first/last, source sequence, or appended value. */
    public List<Node> sources() { return sources; }
    private Node syntax;
    public Node syntax() { return syntax; }
    private Node destination;
    public Node destination() { return destination; }
    private DynamicArrayType type;
    public DynamicArrayType type() { return type; }
    private CallableDeclaration selectedTarget;
    public CallableDeclaration selectedTarget() { return selectedTarget; }
    private CallableDeclaration elementCopyConstructor;
    public Optional<CallableDeclaration> elementCopyConstructor() { return Optional.ofNullable(elementCopyConstructor); }
    @Override public List<Node> children() { return List.of(); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(); result.add(type);
        if (syntax != null) result.add(syntax);
        if (destination != null) result.add(destination);
        if (selectedTarget != null) result.add(selectedTarget);
        if (elementCopyConstructor != null) result.add(elementCopyConstructor);
        result.addAll(sources);
        return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    public static Builder create() { return new Builder(); }
    @Override Builder copyBuilder(EditContext c) { var b = new Builder(); c.register(this, b);
        b.syntax = syntax == null ? null : c.edge(syntax, Node.class);
        b.destination = destination == null ? null : c.edge(destination, Node.class);
        b.type = type == null ? null : c.edge(type, DynamicArrayType.class);
        b.selectedTarget = selectedTarget == null ? null : c.edge(selectedTarget, CallableDeclaration.class);
        b.elementCopyConstructor = elementCopyConstructor == null ? null : c.edge(elementCopyConstructor, CallableDeclaration.class);
        b.kind = kind; b.sources = c.copyAll(sources, Node.class);
        return b; }
    public static final class Builder extends NodeBuilder<DynamicArrayOperation> {
        private Builder() { super(DynamicArrayOperation.class); }
        private Kind kind;
        private List<NodeBuilder<? extends Node>> sources = new ArrayList<>();
        public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
        public Builder setSources(List<NodeBuilder<? extends Node>> value) { sources = new ArrayList<>(value); return this; }
        private NodeBuilder<? extends Node> syntax;
        public Builder setSyntax(NodeBuilder<? extends Node> value) { syntax = value; return this; }
        private NodeBuilder<? extends Node> destination;
        public Builder setDestination(NodeBuilder<? extends Node> value) { destination = value; return this; }
        private NodeBuilder<? extends DynamicArrayType> type;
        public Builder setType(NodeBuilder<? extends DynamicArrayType> value) { type = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> selectedTarget;
        public Builder setSelectedTarget(NodeBuilder<? extends CallableDeclaration> value) { selectedTarget = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> elementCopyConstructor;
        public Builder setElementCopyConstructor(NodeBuilder<? extends CallableDeclaration> value) { elementCopyConstructor = value; return this; }
        @Override DynamicArrayOperation newNode() { return new DynamicArrayOperation(); }
        @Override void fillNode(DynamicArrayOperation n, BuildContext c) {
            n.syntax = c.copy(Objects.requireNonNull(syntax, "syntax"));
            n.destination = c.copy(Objects.requireNonNull(destination, "destination"));
            n.type = c.copy(Objects.requireNonNull(type, "type"));
            n.selectedTarget = c.copy(Objects.requireNonNull(selectedTarget, "selectedTarget"));
            n.elementCopyConstructor = elementCopyConstructor == null ? null : c.copy(elementCopyConstructor);
            n.kind = Objects.requireNonNull(kind); n.sources = c.copyAll(sources);
        }
    }
}
