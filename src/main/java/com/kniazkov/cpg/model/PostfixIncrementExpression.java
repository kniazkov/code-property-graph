/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable PostfixIncrementExpression node. */
public final class PostfixIncrementExpression extends UnaryExpression {
    private PostfixIncrementExpression() {
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }

    public static Builder create() {
        return new Builder();
    }

    @Override
    public Builder edit() {
        return new EditContext().edit(this, Builder.class);
    }

    @Override
    Builder emptyBuilder() {
        return new Builder();
    }

    /** Constructs this concrete syntax form using the inherited fields. */
    public static final class Builder extends UnaryExpression.Builder<PostfixIncrementExpression, Builder> {
        private Builder() {
            super(PostfixIncrementExpression.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        PostfixIncrementExpression newNode() {
            return new PostfixIncrementExpression();
        }
    }
}
