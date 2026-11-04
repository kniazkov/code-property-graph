/* Code Property Graph: the exit owned by a callable. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** Synthetic final child of a callable, shared by its normal CFG exits. */
public final class Exit extends Node {
    private Exit() {}

    public static Builder create() { return new Builder(); }

    @Override
    public List<Node> children() { return List.of(); }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }

    @Override
    public Builder edit() { return new EditContext().edit(this, Builder.class); }

    @Override
    Builder copyBuilder(EditContext context) {
        Builder builder = create();
        context.register(this, builder);
        return builder;
    }

    /** Allocates a fresh exit in each snapshot. */
    public static final class Builder extends NodeBuilder<Exit> {
        private Builder() { super(Exit.class); }

        @Override
        Exit newNode() { return new Exit(); }

        @Override
        void fillNode(Exit node, BuildContext context) {
            // A terminal has no payload or outgoing edges to copy.
        }
    }
}
