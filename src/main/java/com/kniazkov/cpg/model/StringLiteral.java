/* Typed CPG model: C++03 literal values. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** A C++03 string literal preserving its source spelling. */
public final class StringLiteral extends Expression {
    private List<java.math.BigInteger> codeUnits;
    private boolean wide;
    private String spelling;
    public List<java.math.BigInteger> codeUnits() { return codeUnits; }
    public boolean wide() { return wide; }
    public String spelling() { return spelling; }
    /** Execution code units exclude the implicit terminating zero. */
    public String cppType() { return "const " + (wide ? "wchar_t" : "char") + "[" + (codeUnits.size() + 1) + "]"; }
    private StringLiteral() { }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        Builder builder = new Builder(); context.register(this, builder);
        builder.codeUnits = codeUnits;
        builder.wide = wide;
        builder.spelling = spelling;
        return builder;
    }
    public static final class Builder extends NodeBuilder<StringLiteral> {
        private List<java.math.BigInteger> codeUnits = List.of();
        private boolean wide = false;
        private String spelling = "\"\"";
        private Builder() { super(StringLiteral.class); }
        public List<java.math.BigInteger> codeUnits() { return codeUnits; }
        public boolean wide() { return wide; }
        public String spelling() { return spelling; }
        public Builder setCodeUnits(List<java.math.BigInteger> value) { clearExpressionInfo(); codeUnits = List.copyOf(value); canonicalize(); return this; }
        public Builder setWide(boolean value) { clearExpressionInfo(); wide = value; canonicalize(); return this; }
        public Builder setSpelling(String value) { spelling = java.util.Objects.requireNonNull(value); return this; }
        private void canonicalize() { var text = new StringBuilder(wide ? "L\"" : "\""); for (var unit : codeUnits) text.append("\\x").append(unit.toString(16)); spelling = text.append('"').toString(); }
        @Override StringLiteral newNode() { return new StringLiteral(); }
        @Override void fillNode(StringLiteral node, BuildContext context) {
            node.codeUnits = codeUnits;
            node.wide = wide;
            node.spelling = spelling;
        }
    }
}
