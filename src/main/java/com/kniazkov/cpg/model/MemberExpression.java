/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Member access with its original access form. */
public final class MemberExpression extends Expression {
    private Expression receiver;
    private String member;
    private MemberAccessKind access;

    private MemberExpression() {
    }

    public Expression receiver() {
        return receiver;
    }

    public String member() {
        return member;
    }

    public MemberAccessKind access() {
        return access;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(receiver);
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
        builder.receiver = context.copy(receiver);
        builder.member = member;
        builder.access = access;
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<MemberExpression> {
        private NodeBuilder<? extends Expression> receiver;
        private String member;
        private MemberAccessKind access = MemberAccessKind.DOT;

        private Builder() {
            super(MemberExpression.class);
        }

        public Optional<NodeBuilder<? extends Expression>> receiver() {
            return Optional.ofNullable(receiver);
        }

        public Optional<String> member() {
            return Optional.ofNullable(member);
        }

        public MemberAccessKind access() {
            return access;
        }

        public Builder setReceiver(NodeBuilder<? extends Expression> receiver) {
            this.receiver = Objects.requireNonNull(receiver);
            return this;
        }

        public Builder setMember(String member) {
            this.member = Objects.requireNonNull(member);
            return this;
        }

        public Builder setAccess(MemberAccessKind access) {
            this.access = Objects.requireNonNull(access);
            return this;
        }

        @Override
        MemberExpression newNode() {
            return new MemberExpression();
        }

        @Override
        void fillNode(MemberExpression node, BuildContext context) {
            node.receiver = context.copy(receiver);
            node.member = requiredText(member, "MemberExpression.member");
            node.access = access;
        }
    }
}
