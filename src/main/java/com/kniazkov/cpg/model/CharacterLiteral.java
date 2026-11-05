/* Typed CPG model: C++03 literal values. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** A C++03 character literal preserving its source spelling. */
public final class CharacterLiteral extends Expression {
    private java.math.BigInteger value;
    private boolean wide;
    private String cppType;
    private String spelling;
    private int codeUnitBits, valueBits;
    public java.math.BigInteger value() { return value; }
    public boolean wide() { return wide; }
    public String cppType() { return cppType; }
    public String spelling() { return spelling; }
    public int codeUnitBits() { return codeUnitBits; }
    public int valueBits() { return valueBits; }
    private CharacterLiteral() { }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        Builder builder = new Builder(); context.register(this, builder);
        builder.value = value;
        builder.codeUnitBits = codeUnitBits; builder.valueBits = valueBits;
        builder.wide = wide;
        builder.cppType = cppType;
        builder.spelling = spelling;
        return builder;
    }
    public static final class Builder extends NodeBuilder<CharacterLiteral> {
        private java.math.BigInteger value = java.math.BigInteger.ZERO;
        private boolean wide = false;
        private String cppType = "char";
        private int codeUnitBits = 8, valueBits = 8;
        private String spelling = "'\\0'";
        private Builder() { super(CharacterLiteral.class); }
        public java.math.BigInteger value() { return value; }
        public boolean wide() { return wide; }
        public String cppType() { return cppType; }
        public String spelling() { return spelling; }
        public Builder setValue(java.math.BigInteger value) { clearExpressionInfo(); this.value = java.util.Objects.requireNonNull(value); canonicalize(); return this; }
        public Builder setWide(boolean value) { clearExpressionInfo(); wide = value; cppType = wide ? "wchar_t" : "char"; canonicalize(); return this; }
        public Builder setCppType(String value) { clearExpressionInfo(); if (!java.util.Set.of("char", "int", "wchar_t").contains(value)) throw new IllegalArgumentException("Unsupported character type"); cppType = value; canonicalize(); return this; }
        public Builder setSpelling(String value) { spelling = java.util.Objects.requireNonNull(value); return this; }
        /** Widths of execution units and the resulting target integer representation. */
        public Builder setWidths(int unitBits, int resultBits) { clearExpressionInfo();
            if (unitBits < 8 || resultBits < unitBits || resultBits > 4096 || resultBits % unitBits != 0)
                throw new IllegalArgumentException("Invalid character widths");
            codeUnitBits = unitBits; valueBits = resultBits; canonicalize(); return this;
        }
        private void canonicalize() {
            var encoded = value.mod(java.math.BigInteger.ONE.shiftLeft(valueBits));
            var units = new java.util.ArrayList<java.math.BigInteger>();
            var mask = java.math.BigInteger.ONE.shiftLeft(codeUnitBits).subtract(java.math.BigInteger.ONE);
            do { units.add(encoded.and(mask)); encoded = encoded.shiftRight(codeUnitBits); } while (encoded.signum() != 0);
            if (cppType.equals("int") && units.size() == 1) units.add(java.math.BigInteger.ZERO);
            var text = new StringBuilder(wide ? "L'" : "'");
            for (int i = units.size() - 1; i >= 0; i--) text.append("\\x").append(units.get(i).toString(16));
            spelling = text.append('\'').toString();
        }
        @Override CharacterLiteral newNode() { return new CharacterLiteral(); }
        @Override void fillNode(CharacterLiteral node, BuildContext context) {
            node.value = value;
            node.codeUnitBits = codeUnitBits; node.valueBits = valueBits;
            node.wide = wide;
            node.cppType = cppType;
            node.spelling = spelling;
        }
    }
}
