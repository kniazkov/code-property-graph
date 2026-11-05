/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable CommaExpression node. */
public final class CommaExpression extends BinaryExpression {
    private CommaExpression() {
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
    public static final class Builder extends BinaryExpression.Builder<CommaExpression, Builder> {
        private Builder() {
            super(CommaExpression.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        CommaExpression newNode() {
            return new CommaExpression();
        }
    }
}
