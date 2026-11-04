/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** A signed 32-bit integer literal for the prototype subset. */
public final class IntegerLiteral extends Expression {
    private int value;

    private IntegerLiteral() {
    }

    public int value() {
        return value;
    }

    @Override
    public List<Node> children() {
        return List.of();
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
    Builder copyBuilder(EditContext context) {
        Builder builder = new Builder();
        context.register(this, builder);
        builder.value = value;
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<IntegerLiteral> {
        private int value = 0;

        private Builder() {
            super(IntegerLiteral.class);
        }

        public int value() {
            return value;
        }

        public Builder setValue(int value) {
            this.value = value;
            return this;
        }

        @Override
        IntegerLiteral newNode() {
            return new IntegerLiteral();
        }

        @Override
        void fillNode(IntegerLiteral node, BuildContext context) {
            node.value = value;
        }
    }
}
