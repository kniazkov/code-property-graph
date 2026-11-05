/* Typed CPG model: C++03 literal values. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** A C++03 boolean literal preserving its source spelling. */
public final class BooleanLiteral extends Expression {
    private boolean value;
    private String spelling;
    public boolean value() { return value; }
    public String spelling() { return spelling; }
    private BooleanLiteral() { }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        Builder builder = new Builder(); context.register(this, builder);
        builder.value = value;
        builder.spelling = spelling;
        return builder;
    }
    public static final class Builder extends NodeBuilder<BooleanLiteral> {
        private boolean value = false;
        private String spelling = "false";
        private Builder() { super(BooleanLiteral.class); }
        public boolean value() { return value; }
        public String spelling() { return spelling; }
        public Builder setValue(boolean value) { clearExpressionInfo(); this.value = value; spelling = Boolean.toString(value); return this; }
        public Builder setSpelling(String value) { spelling = java.util.Objects.requireNonNull(value); return this; }
        @Override BooleanLiteral newNode() { return new BooleanLiteral(); }
        @Override void fillNode(BooleanLiteral node, BuildContext context) {
            node.value = value;
            node.spelling = spelling;
        }
    }
}
