/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A binary operation whose semantics depend on the language. */
public final class BinaryExpression extends Expression {
    private BinaryOperator operator;
    private Expression left;
    private Expression right;

    private BinaryExpression() {
    }

    public BinaryOperator operator() {
        return operator;
    }

    public Expression left() {
        return left;
    }

    public Expression right() {
        return right;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(left);
        children.add(right);
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
        builder.left = context.copy(left);
        builder.right = context.copy(right);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<BinaryExpression> {
        private BinaryOperator operator = BinaryOperator.ADD;
        private NodeBuilder<? extends Expression> left;
        private NodeBuilder<? extends Expression> right;

        private Builder() {
            super(BinaryExpression.class);
        }

        public BinaryOperator operator() {
            return operator;
        }

        public Optional<NodeBuilder<? extends Expression>> left() {
            return Optional.ofNullable(left);
        }

        public Optional<NodeBuilder<? extends Expression>> right() {
            return Optional.ofNullable(right);
        }

        public Builder setOperator(BinaryOperator operator) {
            this.operator = Objects.requireNonNull(operator);
            return this;
        }

        public Builder setLeft(NodeBuilder<? extends Expression> left) {
            this.left = Objects.requireNonNull(left);
            return this;
        }

        public Builder setRight(NodeBuilder<? extends Expression> right) {
            this.right = Objects.requireNonNull(right);
            return this;
        }

        @Override
        BinaryExpression newNode() {
            return new BinaryExpression();
        }

        @Override
        void fillNode(BinaryExpression node, BuildContext context) {
            node.operator = operator;
            node.left = context.copy(left);
            node.right = context.copy(right);
        }
    }
}
