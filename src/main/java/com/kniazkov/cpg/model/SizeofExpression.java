/* C++03 sizeof has exactly one unevaluated type or expression operand. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The sizeof operator. Its expression child is syntax, not an evaluated value dependency. */
public final class SizeofExpression extends Expression {
    private TypeNode type;
    private Expression operand;
    private SizeofExpression() {}
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
    public static final class Builder extends NodeBuilder<SizeofExpression> {
        private NodeBuilder<? extends TypeNode> type;
        private NodeBuilder<? extends Expression> operand;
        private Builder() { super(SizeofExpression.class); }
        public Optional<NodeBuilder<? extends TypeNode>> type() { return Optional.ofNullable(type); }
        public Optional<NodeBuilder<? extends Expression>> operand() { return Optional.ofNullable(operand); }
        public Builder setType(NodeBuilder<? extends TypeNode> value) { clearExpressionInfo();
            type = Objects.requireNonNull(value); operand = null; return this;
        }
        public Builder setOperand(NodeBuilder<? extends Expression> value) { clearExpressionInfo();
            operand = Objects.requireNonNull(value); type = null; return this;
        }
        @Override SizeofExpression newNode() { return new SizeofExpression(); }
        @Override void fillNode(SizeofExpression node, BuildContext context) {
            if ((type == null) == (operand == null)) throw new IllegalStateException("sizeof requires exactly one operand");
            node.type = type == null ? null : context.copy(type);
            node.operand = operand == null ? null : context.copy(operand);
        }
    }
}
