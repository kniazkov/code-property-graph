/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable BitwiseNotExpression node. */
public final class BitwiseNotExpression extends UnaryExpression {
    private BitwiseNotExpression() {
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
    public static final class Builder extends UnaryExpression.Builder<BitwiseNotExpression, Builder> {
        private Builder() {
            super(BitwiseNotExpression.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        BitwiseNotExpression newNode() {
            return new BitwiseNotExpression();
        }
    }
}
