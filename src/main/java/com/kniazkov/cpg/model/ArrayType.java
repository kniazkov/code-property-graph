/* C++03 type syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** An array with an optional owned symbolic bound expression. */
public final class ArrayType extends TypeNode {
    private TypeNode elementType;
    private Expression bound;
    private ArrayType() {}
    public TypeNode elementType() { return elementType; }
    public Optional<Expression> bound() { return Optional.ofNullable(bound); }
    @Override public List<Node> children() { return bound == null ? List.of(elementType) : List.of(elementType, bound); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.elementType = context.edge(elementType, TypeNode.class);
        builder.bound = bound == null ? null : context.edge(bound, Expression.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<ArrayType> {
        private NodeBuilder<? extends TypeNode> elementType;
        private NodeBuilder<? extends Expression> bound;
        private Builder() { super(ArrayType.class); }
        public Optional<NodeBuilder<? extends TypeNode>> elementType() { return Optional.ofNullable(elementType); }
        public Builder setElementType(NodeBuilder<? extends TypeNode> value) { elementType = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends Expression>> bound() { return Optional.ofNullable(bound); }
        public Builder setBound(NodeBuilder<? extends Expression> value) { bound = Objects.requireNonNull(value); return this; }
        public Builder clearBound() { bound = null; return this; }
        @Override ArrayType newNode() { return new ArrayType(); }
        @Override void fillNode(ArrayType node, BuildContext context) {
            node.elementType = context.copy(elementType);
            node.bound = bound == null ? null : context.copy(bound);
        }
    }
}
