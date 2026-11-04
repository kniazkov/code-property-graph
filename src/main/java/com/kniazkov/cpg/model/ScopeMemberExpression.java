/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable ScopeMemberExpression node. */
public final class ScopeMemberExpression extends MemberExpression {
    private ScopeMemberExpression() {
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
    public static final class Builder extends MemberExpression.Builder<ScopeMemberExpression, Builder> {
        private Builder() {
            super(ScopeMemberExpression.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        ScopeMemberExpression newNode() {
            return new ScopeMemberExpression();
        }
    }
}
