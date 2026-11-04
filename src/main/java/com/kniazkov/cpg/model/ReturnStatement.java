/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A return with an optional value. */
public final class ReturnStatement extends Statement {
    private Expression value;

    private ReturnStatement() {
    }

    public Optional<Expression> value() {
        return Optional.ofNullable(value);
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        if (value != null) {
            children.add(value);
        }
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
        builder.value = value == null ? null : context.edge(value, Expression.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<ReturnStatement> {
        private NodeBuilder<? extends Expression> value;

        private Builder() {
            super(ReturnStatement.class);
        }

        public Optional<NodeBuilder<? extends Expression>> value() {
            return Optional.ofNullable(value);
        }

        public Builder setValue(NodeBuilder<? extends Expression> value) {
            this.value = Objects.requireNonNull(value);
            return this;
        }

        public Builder clearValue() {
            this.value = null;
            return this;
        }

        @Override
        ReturnStatement newNode() {
            return new ReturnStatement();
        }

        @Override
        void fillNode(ReturnStatement node, BuildContext context) {
            node.value = value == null ? null : context.copy(value);
        }
    }
}
