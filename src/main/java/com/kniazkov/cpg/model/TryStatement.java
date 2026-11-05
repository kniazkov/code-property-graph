/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a TryStatement. */
public final class TryStatement extends Statement {
    private BlockStatement body;
    private List<CatchClause> handlers = List.of();
    private TryStatement() {}
    public BlockStatement body() { return body; }
    public List<CatchClause> handlers() { return handlers; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); result.add(body); result.addAll(handlers); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.body = context.edge(body, BlockStatement.class);
        builder.handlers = context.copyAll(handlers, CatchClause.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<TryStatement> {
        private NodeBuilder<? extends BlockStatement> body;
        private List<NodeBuilder<? extends CatchClause>> handlers = new ArrayList<>();
        private Builder() { super(TryStatement.class); }
        public Optional<NodeBuilder<? extends BlockStatement>> body() { return Optional.ofNullable(body); }
        public Builder setBody(NodeBuilder<? extends BlockStatement> value) { syntaxEdited = true; body = Objects.requireNonNull(value); return this; }
        public List<NodeBuilder<? extends CatchClause>> handlers() { return List.copyOf(handlers); }
        public Builder setHandlers(List<? extends NodeBuilder<? extends CatchClause>> value) { syntaxEdited = true; handlers = new ArrayList<>(List.copyOf(value)); return this; }
        public Builder addHandler(NodeBuilder<? extends CatchClause> value) { syntaxEdited = true; handlers.add(Objects.requireNonNull(value)); return this; }
        @Override TryStatement newNode() { return new TryStatement(); }
        @Override void fillNode(TryStatement node, BuildContext context) {
            node.body = context.copy(body);
            node.handlers = context.copyAll(handlers);
        }
    }
}
