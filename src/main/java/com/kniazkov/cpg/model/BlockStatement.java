/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An ordered sequence of statements and declarations. */
public final class BlockStatement extends Statement {
    private List<Node> items = List.of();

    private BlockStatement() {
    }

    public List<Node> items() {
        return items;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.addAll(items);
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
        builder.items = context.copyAll(items, Node.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<BlockStatement> {
        private List<NodeBuilder<? extends Node>> items = new ArrayList<>();

        private Builder() {
            super(BlockStatement.class);
        }

        public List<NodeBuilder<? extends Node>> items() {
            return List.copyOf(items);
        }

        public Builder setItems(List<? extends NodeBuilder<? extends Node>> items) {
            this.items = new ArrayList<>(List.copyOf(items));
            return this;
        }

        public Builder addItem(NodeBuilder<? extends Node> value) {
            items.add(Objects.requireNonNull(value));
            return this;
        }

        @Override
        BlockStatement newNode() {
            return new BlockStatement();
        }

        @Override
        void fillNode(BlockStatement node, BuildContext context) {
            node.items = context.copyAll(items);
            for (Node item : node.items) {
                if (!(item instanceof Statement) && !(item instanceof Declaration)) {
                    throw new IllegalStateException("Block item must be a statement or declaration");
                }
            }
        }
    }
}
