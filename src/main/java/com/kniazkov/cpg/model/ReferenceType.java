/* C++03 type syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** A C++03 lvalue reference. */
public final class ReferenceType extends TypeNode {
    private TypeNode referredType;
    private ReferenceType() {}
    public TypeNode referredType() { return referredType; }
    @Override public List<Node> children() { return List.of(referredType); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.referredType = context.edge(referredType, TypeNode.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<ReferenceType> {
        private NodeBuilder<? extends TypeNode> referredType;
        private Builder() { super(ReferenceType.class); }
        public Optional<NodeBuilder<? extends TypeNode>> referredType() { return Optional.ofNullable(referredType); }
        public Builder setReferredType(NodeBuilder<? extends TypeNode> value) { referredType = Objects.requireNonNull(value); return this; }
        @Override ReferenceType newNode() { return new ReferenceType(); }
        @Override void fillNode(ReferenceType node, BuildContext context) {
            node.referredType = context.copy(referredType);
        }
    }
}
