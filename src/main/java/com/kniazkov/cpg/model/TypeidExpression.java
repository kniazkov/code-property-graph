/* C++03 typeid has exactly one type or expression operand. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The typeid operator. Its expression operand may be evaluated for a polymorphic glvalue. */
public final class TypeidExpression extends Expression {
    private TypeNode type;
    private Expression operand;
    private TypeidExpression() {}
    public Optional<TypeNode> type() { return Optional.ofNullable(type); }
    public Optional<Expression> operand() { return Optional.ofNullable(operand); }
    @Override public List<Node> children() { return type != null ? List.of(type) : List.of(operand); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder();
        context.register(this, builder);
        builder.type = type == null ? null : context.edge(type, TypeNode.class);
        builder.operand = operand == null ? null : context.edge(operand, Expression.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<TypeidExpression> {
        private NodeBuilder<? extends TypeNode> type;
        private NodeBuilder<? extends Expression> operand;
        private Builder() { super(TypeidExpression.class); }
        public Optional<NodeBuilder<? extends TypeNode>> type() { return Optional.ofNullable(type); }
        public Optional<NodeBuilder<? extends Expression>> operand() { return Optional.ofNullable(operand); }
        public Builder setType(NodeBuilder<? extends TypeNode> value) { clearExpressionInfo();
            type = Objects.requireNonNull(value); operand = null; return this;
        }
        public Builder setOperand(NodeBuilder<? extends Expression> value) { clearExpressionInfo();
            operand = Objects.requireNonNull(value); type = null; return this;
        }
        @Override TypeidExpression newNode() { return new TypeidExpression(); }
        @Override void fillNode(TypeidExpression node, BuildContext context) {
            if ((type == null) == (operand == null)) throw new IllegalStateException("typeid requires exactly one operand");
            node.type = type == null ? null : context.copy(type);
            node.operand = operand == null ? null : context.copy(operand);
        }
    }
}
