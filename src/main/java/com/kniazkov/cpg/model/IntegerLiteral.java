/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** An integer literal with the prototype's signed 32-bit payload.
 * Optional C++ type spelling preserves the type of synthesized sizeof results. */
public final class IntegerLiteral extends Expression {
    private int value;
    private String cppType;

    /** Explicit C++ type of a folded or suffixed literal; absent for an ordinary decimal literal. */
    public java.util.Optional<String> cppType() { return java.util.Optional.ofNullable(cppType); }

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
        builder.cppType = cppType;
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<IntegerLiteral> {
        private int value = 0;
        private String cppType;
        public java.util.Optional<String> cppType() { return java.util.Optional.ofNullable(cppType); }
        public Builder setCppType(String type) {
            if (!java.util.Set.of("int", "unsigned int", "long", "unsigned long").contains(type))
                throw new IllegalArgumentException("Unsupported integer literal type: " + type);
            cppType = type; return this;
        }
        public Builder clearCppType() { cppType = null; return this; }

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
            node.cppType = cppType;
        }
    }
}
