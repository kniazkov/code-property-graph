/* C++03 type syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** A pointer, independently of any cv qualifiers on the pointer itself. */
public final class PointerType extends TypeNode {
    private TypeNode pointee;
    private PointerType() {}
    public TypeNode pointee() { return pointee; }
    @Override public List<Node> children() { return List.of(pointee); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.pointee = context.edge(pointee, TypeNode.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<PointerType> {
        private NodeBuilder<? extends TypeNode> pointee;
        private Builder() { super(PointerType.class); }
        public Optional<NodeBuilder<? extends TypeNode>> pointee() { return Optional.ofNullable(pointee); }
        public Builder setPointee(NodeBuilder<? extends TypeNode> value) { pointee = Objects.requireNonNull(value); return this; }
        @Override PointerType newNode() { return new PointerType(); }
        @Override void fillNode(PointerType node, BuildContext context) {
            node.pointee = context.copy(pointee);
        }
    }
}
