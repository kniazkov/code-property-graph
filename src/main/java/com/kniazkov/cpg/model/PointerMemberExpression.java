/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable PointerMemberExpression node. */
public final class PointerMemberExpression extends MemberExpression {
    private PointerMemberExpression() {
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
    public static final class Builder extends MemberExpression.Builder<PointerMemberExpression, Builder> {
        private Builder() {
            super(PointerMemberExpression.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        PointerMemberExpression newNode() {
            return new PointerMemberExpression();
        }
    }
}
