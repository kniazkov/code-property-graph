/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a ForStatement. */
public final class ForStatement extends Statement {
    private List<Node> initializers = List.of();
    private Expression condition;
    private Expression update;
    private Statement body;
    private ForStatement() {}
    public List<Node> initializers() { return initializers; }
    public Optional<Expression> condition() { return Optional.ofNullable(condition); }
    public Optional<Expression> update() { return Optional.ofNullable(update); }
    public Statement body() { return body; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); result.addAll(initializers); if (condition != null) result.add(condition); if (update != null) result.add(update); result.add(body); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.initializers = context.copyAll(initializers, Node.class);
        builder.condition = condition == null ? null : context.edge(condition, Expression.class);
        builder.update = update == null ? null : context.edge(update, Expression.class);
        builder.body = context.edge(body, Statement.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<ForStatement> {
        private List<NodeBuilder<? extends Node>> initializers = new ArrayList<>();
        private NodeBuilder<? extends Expression> condition;
        private NodeBuilder<? extends Expression> update;
        private NodeBuilder<? extends Statement> body;
        private Builder() { super(ForStatement.class); }
        public List<NodeBuilder<? extends Node>> initializers() { return List.copyOf(initializers); }
        public Builder setInitializers(List<? extends NodeBuilder<? extends Node>> value) { syntaxEdited = true; initializers = new ArrayList<>(List.copyOf(value)); return this; }
        public Builder addInitializer(NodeBuilder<? extends Node> value) { syntaxEdited = true; initializers.add(Objects.requireNonNull(value)); return this; }
        public Optional<NodeBuilder<? extends Expression>> condition() { return Optional.ofNullable(condition); }
        public Builder setCondition(NodeBuilder<? extends Expression> value) { syntaxEdited = true; condition = Objects.requireNonNull(value); return this; }
        public Builder clearCondition() { syntaxEdited = true; condition = null; return this; }
        public Optional<NodeBuilder<? extends Expression>> update() { return Optional.ofNullable(update); }
        public Builder setUpdate(NodeBuilder<? extends Expression> value) { syntaxEdited = true; update = Objects.requireNonNull(value); return this; }
        public Builder clearUpdate() { syntaxEdited = true; update = null; return this; }
        public Optional<NodeBuilder<? extends Statement>> body() { return Optional.ofNullable(body); }
        public Builder setBody(NodeBuilder<? extends Statement> value) { syntaxEdited = true; body = Objects.requireNonNull(value); return this; }
        @Override ForStatement newNode() { return new ForStatement(); }
        @Override void fillNode(ForStatement node, BuildContext context) {
            node.initializers = context.copyAll(initializers);
            node.condition = condition == null ? null : context.copy(condition);
            node.update = update == null ? null : context.copy(update);
            node.body = context.copy(body);
        }
    }
}
