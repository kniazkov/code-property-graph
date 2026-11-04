/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A precondition loop. */
public final class WhileStatement extends Statement {
    private Expression condition;
    private Statement body;

    private WhileStatement() {
    }

    public Expression condition() {
        return condition;
    }

    public Statement body() {
        return body;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(condition);
        children.add(body);
        return List.copyOf(children);
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
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
        builder.body = context.copy(body);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<WhileStatement> {
        private NodeBuilder<? extends Expression> condition;
        private NodeBuilder<? extends Statement> body;

        private Builder() {
            super(WhileStatement.class);
        }

        public Optional<NodeBuilder<? extends Expression>> condition() {
            return Optional.ofNullable(condition);
        }

        public Optional<NodeBuilder<? extends Statement>> body() {
            return Optional.ofNullable(body);
        }

        public Builder setCondition(NodeBuilder<? extends Expression> condition) {
            this.condition = Objects.requireNonNull(condition);
            return this;
        }

        public Builder setBody(NodeBuilder<? extends Statement> body) {
            this.body = Objects.requireNonNull(body);
            return this;
        }

        @Override
        WhileStatement newNode() {
            return new WhileStatement();
        }

        @Override
        void fillNode(WhileStatement node, BuildContext context) {
            node.condition = context.copy(condition);
            node.body = context.copy(body);
        }
    }
}
