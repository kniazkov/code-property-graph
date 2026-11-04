/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The prototype common output operation. */
public final class PrintStatement extends Statement {
    private Expression value;

    private PrintStatement() {
    }

    public Expression value() {
        return value;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(value);
        return List.copyOf(children);
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }

    /** Wraps an existing immutable expression while preserving references into its project. */
    public static PrintStatement of(Expression value) {
        var result = new PrintStatement();
        result.value = Objects.requireNonNull(value);
        return result;
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
        builder.value = context.edge(value, Expression.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<PrintStatement> {
        private NodeBuilder<? extends Expression> value;

        private Builder() {
            super(PrintStatement.class);
        }

        public Optional<NodeBuilder<? extends Expression>> value() {
            return Optional.ofNullable(value);
        }

        public Builder setValue(NodeBuilder<? extends Expression> value) {
            this.value = Objects.requireNonNull(value);
            return this;
        }

        @Override
        PrintStatement newNode() {
            return new PrintStatement();
        }

        @Override
        void fillNode(PrintStatement node, BuildContext context) {
            node.value = context.copy(value);
        }
    }
}
