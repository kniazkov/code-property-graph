/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A unary operation. */
public final class UnaryExpression extends Expression {
    private UnaryOperator operator;
    private Expression operand;

    private UnaryExpression() {
    }

    public UnaryOperator operator() {
        return operator;
    }

    public Expression operand() {
        return operand;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(operand);
        return List.copyOf(children);
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
        builder.operator = operator;
        builder.operand = context.copy(operand);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<UnaryExpression> {
        private UnaryOperator operator = UnaryOperator.PLUS;
        private NodeBuilder<? extends Expression> operand;

        private Builder() {
            super(UnaryExpression.class);
        }

        public UnaryOperator operator() {
            return operator;
        }

        public Optional<NodeBuilder<? extends Expression>> operand() {
            return Optional.ofNullable(operand);
        }

        public Builder setOperator(UnaryOperator operator) {
            this.operator = Objects.requireNonNull(operator);
            return this;
        }

        public Builder setOperand(NodeBuilder<? extends Expression> operand) {
            this.operand = Objects.requireNonNull(operand);
            return this;
        }

        @Override
        UnaryExpression newNode() {
            return new UnaryExpression();
        }

        @Override
        void fillNode(UnaryExpression node, BuildContext context) {
            node.operator = operator;
            node.operand = context.copy(operand);
        }
    }
}
