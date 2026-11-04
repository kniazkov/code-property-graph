/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable MethodDeclaration node. */
public final class MethodDeclaration extends CallableDeclaration {
    private MethodDeclaration() {
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
    public static final class Builder extends CallableDeclaration.Builder<MethodDeclaration, Builder> {
        private Builder() {
            super(MethodDeclaration.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        MethodDeclaration newNode() {
            return new MethodDeclaration();
        }
    }
}
