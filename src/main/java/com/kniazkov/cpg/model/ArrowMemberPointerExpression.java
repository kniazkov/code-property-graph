/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable ArrowMemberPointerExpression node. */
public final class ArrowMemberPointerExpression extends BinaryExpression {
    private ArrowMemberPointerExpression() {
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
    public static final class Builder extends BinaryExpression.Builder<ArrowMemberPointerExpression, Builder> {
        private Builder() {
            super(ArrowMemberPointerExpression.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        ArrowMemberPointerExpression newNode() {
            return new ArrowMemberPointerExpression();
        }
    }
}
