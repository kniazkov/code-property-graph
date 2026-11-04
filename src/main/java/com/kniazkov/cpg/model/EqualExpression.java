/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable EqualExpression node. */
public final class EqualExpression extends BinaryExpression {
    private EqualExpression() {
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
    public static final class Builder extends BinaryExpression.Builder<EqualExpression, Builder> {
        private Builder() {
            super(EqualExpression.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        EqualExpression newNode() {
            return new EqualExpression();
        }
    }
}
