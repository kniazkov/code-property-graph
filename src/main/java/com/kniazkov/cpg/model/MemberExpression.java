/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Member access with its original access form. */
public abstract class MemberExpression extends Expression {
    private Expression receiver;
    private String member;
    private Declaration target;
    public Optional<Declaration> target() { return Optional.ofNullable(target); }
    @Override public List<Node> references() { return target == null ? List.of() : List.of(target); }

    MemberExpression() {
    }

    /** Returns the receiver of this syntax node. */
    public Expression receiver() {
        return receiver;
    }

    /** Returns the member of this syntax node. */
    public String member() {
        return member;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(receiver);
        return List.copyOf(children);
    }

    /** Creates the builder for the concrete operation during an edit session. */
    abstract Builder<?, ?> emptyBuilder();

    @Override
    final Builder<?, ?> copyBuilder(EditContext context) {
        Builder<?, ?> builder = emptyBuilder();
        context.register(this, builder);
        builder.receiver = context.edge(receiver, Expression.class);
        builder.member = member;
        builder.target = target == null ? null : context.edge(target, Declaration.class);
        return builder;
    }

    /** Holds the fields shared by every concrete variant and copies them in one build session.
     * @param <T> concrete node allocated by the leaf builder
     * @param <B> concrete builder returned by fluent setters */
    public abstract static class Builder<T extends MemberExpression, B extends Builder<T, B>> extends NodeBuilder<T> {
        private NodeBuilder<? extends Expression> receiver;
        private String member;
        private NodeBuilder<? extends Declaration> target;
        public Optional<NodeBuilder<? extends Declaration>> target() { return Optional.ofNullable(target); }
        public B setTarget(NodeBuilder<? extends Declaration> value) { target = Objects.requireNonNull(value); return self(); }
        public B clearTarget() { target = null; return self(); }

        /** Binds this builder to the snapshot class used for identity lookups. */
        Builder(Class<T> type) {
            super(type);
        }

        /** Reads the current receiver from the builder. */
        public Optional<NodeBuilder<? extends Expression>> receiver() {
            return Optional.ofNullable(receiver);
        }

        /** Reads the current member from the builder. */
        public Optional<String> member() {
            return Optional.ofNullable(member);
        }

        /** Replaces the receiver in the mutable state and returns the concrete builder. */
        public final B setReceiver(NodeBuilder<? extends Expression> receiver) {
            this.receiver = Objects.requireNonNull(receiver);
            return self();
        }

        /** Replaces the member in the mutable state and returns the concrete builder. */
        public final B setMember(String member) {
            this.member = Objects.requireNonNull(member);
            return self();
        }

        /** Returns the concrete builder so inherited setters can be chained. */
        abstract B self();

        @Override
        final void fillNode(T shell, BuildContext context) {
            MemberExpression node = shell;
            node.receiver = context.copy(receiver);
            node.member = requiredText(member, "MemberExpression.member");
            node.target = target == null ? null : context.copy(target);
        }
    }
}
