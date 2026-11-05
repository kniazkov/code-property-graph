/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** Arbitrary precision integer payload with optional original spelling and C++ target type.
 * The legacy value() accessor requires a signed 32-bit representable value. */
public final class IntegerLiteral extends Expression {
    private java.math.BigInteger value;
    private String spelling;
    private String cppType;

    /** Explicit C++ type of a folded or suffixed literal; absent for an ordinary decimal literal. */
    public java.util.Optional<String> cppType() { return java.util.Optional.ofNullable(cppType); }

    private IntegerLiteral() {
    }

    public java.math.BigInteger bigValue() { return value; }
    public java.util.Optional<String> spelling() { return java.util.Optional.ofNullable(spelling); }

    public int value() {
        return value.intValueExact();
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
        builder.spelling = spelling;
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<IntegerLiteral> {
        private java.math.BigInteger value = java.math.BigInteger.ZERO;
        private String spelling;
        private String cppType;
        public java.util.Optional<String> cppType() { return java.util.Optional.ofNullable(cppType); }
        public Builder setCppType(String type) { clearExpressionInfo();
            if (!java.util.Set.of("int", "unsigned int", "long", "unsigned long").contains(type))
                throw new IllegalArgumentException("Unsupported integer literal type: " + type);
            cppType = type; spelling = null; return this;
        }
        public Builder clearCppType() { clearExpressionInfo(); cppType = null; spelling = null; return this; }

        private Builder() {
            super(IntegerLiteral.class);
        }

        public java.math.BigInteger bigValue() { return value; }
        public java.util.Optional<String> spelling() { return java.util.Optional.ofNullable(spelling); }
        public Builder setSpelling(String value) { spelling = java.util.Objects.requireNonNull(value); return this; }
        public Builder clearSpelling() { spelling = null; return this; }
        public Builder setValue(java.math.BigInteger value) { clearExpressionInfo(); this.value = java.util.Objects.requireNonNull(value); spelling = null; return this; }

        public int value() {
            return value.intValueExact();
        }

        public Builder setValue(int value) { clearExpressionInfo();
            this.value = java.math.BigInteger.valueOf(value);
            spelling = null;
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
            node.spelling = spelling;
        }
    }
}
