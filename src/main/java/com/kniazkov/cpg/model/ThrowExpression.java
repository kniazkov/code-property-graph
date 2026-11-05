/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a ThrowExpression. */
public final class ThrowExpression extends Expression {
    private Expression value;
    private ThrowExpression() {}
    public Optional<Expression> value() { return Optional.ofNullable(value); }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); if (value != null) result.add(value); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.value = value == null ? null : context.edge(value, Expression.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<ThrowExpression> {
        private NodeBuilder<? extends Expression> value;
        private Builder() { super(ThrowExpression.class); }
        public Optional<NodeBuilder<? extends Expression>> value() { return Optional.ofNullable(value); }
        public Builder setValue(NodeBuilder<? extends Expression> value) { syntaxEdited = true; this.value = Objects.requireNonNull(value); return this; }
        public Builder clearValue() { syntaxEdited = true; value = null; return this; }
        @Override ThrowExpression newNode() { return new ThrowExpression(); }
        @Override void fillNode(ThrowExpression node, BuildContext context) {
            node.value = value == null ? null : context.copy(value);
        }
    }
}
