/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A unary operation. */
public abstract class UnaryExpression extends Expression {
    private Expression operand;

    UnaryExpression() {
    }

    /** Returns the operand of this syntax node. */
    public Expression operand() {
        return operand;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(operand);
        return List.copyOf(children);
    }

    /** Creates the builder for the concrete operation during an edit session. */
    abstract Builder<?, ?> emptyBuilder();

    @Override
    final Builder<?, ?> copyBuilder(EditContext context) {
        Builder<?, ?> builder = emptyBuilder();
        context.register(this, builder);
        builder.operand = context.edge(operand, Expression.class);
        return builder;
    }

    /** Holds the fields shared by every concrete variant and copies them in one build session.
     * @param <T> concrete node allocated by the leaf builder
     * @param <B> concrete builder returned by fluent setters */
    public abstract static class Builder<T extends UnaryExpression, B extends Builder<T, B>> extends NodeBuilder<T> {
        private NodeBuilder<? extends Expression> operand;

        /** Binds this builder to the snapshot class used for identity lookups. */
        Builder(Class<T> type) {
            super(type);
        }

        /** Reads the current operand from the builder. */
        public Optional<NodeBuilder<? extends Expression>> operand() {
            return Optional.ofNullable(operand);
        }

        /** Replaces the operand in the mutable state and returns the concrete builder. */
        public final B setOperand(NodeBuilder<? extends Expression> operand) { clearExpressionInfo();
            this.operand = Objects.requireNonNull(operand);
            return self();
        }

        /** Returns the concrete builder so inherited setters can be chained. */
        abstract B self();

        @Override
        final void fillNode(T shell, BuildContext context) {
            UnaryExpression node = shell;
            node.operand = context.copy(operand);
        }
    }
}
