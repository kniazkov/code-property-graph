/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable DotMemberExpression node. */
public final class DotMemberExpression extends MemberExpression {
    private DotMemberExpression() {
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
    public static final class Builder extends MemberExpression.Builder<DotMemberExpression, Builder> {
        private Builder() {
            super(DotMemberExpression.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        DotMemberExpression newNode() {
            return new DotMemberExpression();
        }
    }
}
