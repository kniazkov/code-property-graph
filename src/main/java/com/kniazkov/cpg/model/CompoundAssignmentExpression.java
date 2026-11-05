/* C++03 expression syntax model. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable CompoundAssignmentExpression syntax with owned expression children. */
public final class CompoundAssignmentExpression extends Expression {
    private Expression target;
    private Expression value;
    private String operator;
    private CompoundAssignmentExpression() {}
    public Expression target() { return target; }
    public Expression value() { return value; }
    public String operator() { return operator; }
    @Override public List<Node> children() { return List.of(target, value); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder();
        context.register(this, builder);
        builder.target = context.edge(target, Expression.class);
        builder.value = context.edge(value, Expression.class);
        builder.operator = operator;
        return builder;
    }
    public static final class Builder extends NodeBuilder<CompoundAssignmentExpression> {
        private NodeBuilder<? extends Expression> target;
        private NodeBuilder<? extends Expression> value;
        private String operator;
        private Builder() { super(CompoundAssignmentExpression.class); }
        public Optional<NodeBuilder<? extends Expression>> target() { return Optional.ofNullable(target); }
        public Builder setTarget(NodeBuilder<? extends Expression> value) { target = Objects.requireNonNull(value); clearExpressionInfo(); return this; }
        public Optional<NodeBuilder<? extends Expression>> value() { return Optional.ofNullable(value); }
        public Builder setValue(NodeBuilder<? extends Expression> value) { this.value = Objects.requireNonNull(value); clearExpressionInfo(); return this; }
        public String operator() { return operator; }
        public Builder setOperator(String value) {
            if (!java.util.Set.of("+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "^=", "|=").contains(value)) throw new IllegalArgumentException("Invalid compound assignment operator");
            operator = value; clearExpressionInfo(); return this;
        }
        @Override CompoundAssignmentExpression newNode() { return new CompoundAssignmentExpression(); }
        @Override void fillNode(CompoundAssignmentExpression node, BuildContext context) {
            node.target = context.copy(target);
            node.value = context.copy(value);
            node.operator = Objects.requireNonNull(operator, "operator");
        }
    }
}
