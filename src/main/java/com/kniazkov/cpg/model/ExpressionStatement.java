/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** An expression used as a statement. */
public final class ExpressionStatement extends Statement {
    private Expression expression;

    private ExpressionStatement() {
    }

    public Expression expression() {
        return expression;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(expression);
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
        builder.expression = context.copy(expression);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<ExpressionStatement> {
        private NodeBuilder<? extends Expression> expression;

        private Builder() {
            super(ExpressionStatement.class);
        }

        public Optional<NodeBuilder<? extends Expression>> expression() {
            return Optional.ofNullable(expression);
        }

        public Builder setExpression(NodeBuilder<? extends Expression> expression) {
            this.expression = Objects.requireNonNull(expression);
            return this;
        }

        @Override
        ExpressionStatement newNode() {
            return new ExpressionStatement();
        }

        @Override
        void fillNode(ExpressionStatement node, BuildContext context) {
            node.expression = context.copy(expression);
        }
    }
}
