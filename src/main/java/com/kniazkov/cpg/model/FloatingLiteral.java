/* Typed CPG model: C++03 literal values. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** A C++03 floating literal preserving its source spelling.
 * value() is the exact mathematical decimal value, not a target-rounded binary value.
 * The platform profile describes storage sizes, not floating-point representation or rounding. */
public final class FloatingLiteral extends Expression {
    private java.math.BigDecimal value;
    private String cppType;
    private String spelling;
    public java.math.BigDecimal value() { return value; }
    public String cppType() { return cppType; }
    public String spelling() { return spelling; }
    private FloatingLiteral() { }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        Builder builder = new Builder(); context.register(this, builder);
        builder.value = value;
        builder.cppType = cppType;
        builder.spelling = spelling;
        return builder;
    }
    public static final class Builder extends NodeBuilder<FloatingLiteral> {
        private java.math.BigDecimal value = java.math.BigDecimal.ZERO;
        private String cppType = "double";
        private String spelling = "0.0";
        private Builder() { super(FloatingLiteral.class); }
        public java.math.BigDecimal value() { return value; }
        public String cppType() { return cppType; }
        public String spelling() { return spelling; }
        public Builder setValue(java.math.BigDecimal value) { clearExpressionInfo(); this.value = java.util.Objects.requireNonNull(value); canonicalize(); return this; }
        public Builder setCppType(String value) { clearExpressionInfo(); if (!java.util.Set.of("float", "double", "long double").contains(value)) throw new IllegalArgumentException("Unsupported floating type"); cppType = value; canonicalize(); return this; }
        public Builder setSpelling(String value) { spelling = java.util.Objects.requireNonNull(value); return this; }
        private void canonicalize() { spelling = value.toString(); if (!spelling.contains(".") && !spelling.contains("E") && !spelling.contains("e")) spelling += ".0"; spelling += cppType.equals("float") ? "F" : cppType.equals("long double") ? "L" : ""; }
        @Override FloatingLiteral newNode() { return new FloatingLiteral(); }
        @Override void fillNode(FloatingLiteral node, BuildContext context) {
            node.value = value;
            node.cppType = cppType;
            node.spelling = spelling;
        }
    }
}
