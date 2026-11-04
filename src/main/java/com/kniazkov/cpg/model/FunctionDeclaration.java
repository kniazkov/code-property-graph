/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Immutable FunctionDeclaration node. */
public final class FunctionDeclaration extends CallableDeclaration {
    private FunctionDeclaration() {
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
    public static final class Builder extends CallableDeclaration.Builder<FunctionDeclaration, Builder> {
        private Builder() {
            super(FunctionDeclaration.class);
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        FunctionDeclaration newNode() {
            return new FunctionDeclaration();
        }
    }
}
