/* Written and implicit C++03 conversions. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** An implicit conversion emits only its operand; a written cast keeps its own range and type. */
public final class ConversionExpression extends Expression {
    public enum Kind { STATIC_CAST, LVALUE_TO_RVALUE, ARRAY_TO_POINTER, FUNCTION_TO_POINTER,
        INTEGRAL_PROMOTION, FLOATING_PROMOTION, ARITHMETIC_CONVERSION, QUALIFICATION, NULL_POINTER, REFERENCE_BINDING }
    private Expression operand;
    private TypeNode targetType;
    private String sourceType;
    private boolean explicit;
    private Kind kind;
    private ConversionExpression() {}
    public Expression operand() { return operand; }
    public TypeNode targetType() { return targetType; }
    public Optional<String> sourceType() { return Optional.ofNullable(sourceType); }
    public boolean isExplicit() { return explicit; }
    public Kind kind() { return kind; }
    void clearDerivedSource() { sourceType = null; }
    @Override public List<Node> children() { return List.of(targetType, operand); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var b = new Builder(); context.register(this, b);
        b.operand = context.edge(operand, Expression.class); b.targetType = context.edge(targetType, TypeNode.class);
        b.sourceType = sourceType; b.explicit = explicit; b.kind = kind; return b;
    }
    public static final class Builder extends NodeBuilder<ConversionExpression> {
        private NodeBuilder<? extends Expression> operand;
        private NodeBuilder<? extends TypeNode> targetType;
        private String sourceType;
        private boolean explicit;
        private Kind kind = Kind.STATIC_CAST;
        private Builder() { super(ConversionExpression.class); }
        public Builder setOperand(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); sourceType = null; operand = Objects.requireNonNull(value); return this; }
        public Builder setTargetType(NodeBuilder<? extends TypeNode> value) { clearExpressionInfo(); targetType = Objects.requireNonNull(value); return this; }
        public Builder setSourceType(String value) { sourceType = Objects.requireNonNull(value); return this; }
        public Builder setExplicit(boolean value) { clearExpressionInfo(); explicit = value; return this; }
        public Builder setKind(Kind value) { clearExpressionInfo(); kind = Objects.requireNonNull(value); return this; }
        @Override ConversionExpression newNode() { return new ConversionExpression(); }
        @Override void fillNode(ConversionExpression node, BuildContext context) {
            node.operand = context.copy(operand); node.targetType = context.copy(targetType);
            node.sourceType = sourceType; node.explicit = explicit; node.kind = kind;
        }
    }
}
