/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A runtime condition with an optional alternative. */
public final class IfStatement extends Statement {
    private Expression condition;
    private Statement thenBranch;
    private Statement elseBranch;

    private IfStatement() {
    }

    public Expression condition() {
        return condition;
    }

    public Statement thenBranch() {
        return thenBranch;
    }

    public Optional<Statement> elseBranch() {
        return Optional.ofNullable(elseBranch);
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(condition);
        children.add(thenBranch);
        if (elseBranch != null) {
            children.add(elseBranch);
        }
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
        builder.condition = context.copy(condition);
        builder.thenBranch = context.copy(thenBranch);
        builder.elseBranch = elseBranch == null ? null : context.copy(elseBranch);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<IfStatement> {
        private NodeBuilder<? extends Expression> condition;
        private NodeBuilder<? extends Statement> thenBranch;
        private NodeBuilder<? extends Statement> elseBranch;

        private Builder() {
            super(IfStatement.class);
        }

        public Optional<NodeBuilder<? extends Expression>> condition() {
            return Optional.ofNullable(condition);
        }

        public Optional<NodeBuilder<? extends Statement>> thenBranch() {
            return Optional.ofNullable(thenBranch);
        }

        public Optional<NodeBuilder<? extends Statement>> elseBranch() {
            return Optional.ofNullable(elseBranch);
        }

        public Builder setCondition(NodeBuilder<? extends Expression> condition) {
            this.condition = Objects.requireNonNull(condition);
            return this;
        }

        public Builder setThenBranch(NodeBuilder<? extends Statement> thenBranch) {
            this.thenBranch = Objects.requireNonNull(thenBranch);
            return this;
        }

        public Builder setElseBranch(NodeBuilder<? extends Statement> elseBranch) {
            this.elseBranch = Objects.requireNonNull(elseBranch);
            return this;
        }

        public Builder clearElseBranch() {
            this.elseBranch = null;
            return this;
        }

        @Override
        IfStatement newNode() {
            return new IfStatement();
        }

        @Override
        void fillNode(IfStatement node, BuildContext context) {
            node.condition = context.copy(condition);
            node.thenBranch = context.copy(thenBranch);
            node.elseBranch = elseBranch == null ? null : context.copy(elseBranch);
        }
    }
}
