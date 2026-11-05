/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A binary operation whose semantics depend on the language. */
public abstract class BinaryExpression extends Expression {
    private Expression left;
    private Expression right;

    BinaryExpression() {
    }

    /** Returns the left of this syntax node. */
    public Expression left() {
        return left;
    }

    /** Returns the right of this syntax node. */
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

    /** Creates the builder for the concrete operation during an edit session. */
    abstract Builder<?, ?> emptyBuilder();

    @Override
    final Builder<?, ?> copyBuilder(EditContext context) {
        Builder<?, ?> builder = emptyBuilder();
        context.register(this, builder);
        builder.left = context.edge(left, Expression.class);
        builder.right = context.edge(right, Expression.class);
        return builder;
    }

    /** Holds the fields shared by every concrete variant and copies them in one build session.
     * @param <T> concrete node allocated by the leaf builder
     * @param <B> concrete builder returned by fluent setters */
    public abstract static class Builder<T extends BinaryExpression, B extends Builder<T, B>> extends NodeBuilder<T> {
        private NodeBuilder<? extends Expression> left;
        private NodeBuilder<? extends Expression> right;

        /** Binds this builder to the snapshot class used for identity lookups. */
        Builder(Class<T> type) {
            super(type);
        }

        /** Reads the current left from the builder. */
        public Optional<NodeBuilder<? extends Expression>> left() {
            return Optional.ofNullable(left);
        }

        /** Reads the current right from the builder. */
        public Optional<NodeBuilder<? extends Expression>> right() {
            return Optional.ofNullable(right);
        }

        /** Replaces the left in the mutable state and returns the concrete builder. */
        public final B setLeft(NodeBuilder<? extends Expression> left) { clearExpressionInfo();
            this.left = Objects.requireNonNull(left);
            return self();
        }

        /** Replaces the right in the mutable state and returns the concrete builder. */
        public final B setRight(NodeBuilder<? extends Expression> right) { clearExpressionInfo();
            this.right = Objects.requireNonNull(right);
            return self();
        }

        /** Returns the concrete builder so inherited setters can be chained. */
        abstract B self();

        @Override
        final void fillNode(T shell, BuildContext context) {
            BinaryExpression node = shell;
            node.left = context.copy(left);
            node.right = context.copy(right);
        }
    }
}
