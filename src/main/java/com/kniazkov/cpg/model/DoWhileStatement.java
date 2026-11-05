/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a DoWhileStatement. */
public final class DoWhileStatement extends Statement {
    private Statement body;
    private Expression condition;
    private DoWhileStatement() {}
    public Statement body() { return body; }
    public Expression condition() { return condition; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); result.add(body); result.add(condition); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.body = context.edge(body, Statement.class);
        builder.condition = context.edge(condition, Expression.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<DoWhileStatement> {
        private NodeBuilder<? extends Statement> body;
        private NodeBuilder<? extends Expression> condition;
        private Builder() { super(DoWhileStatement.class); }
        public Optional<NodeBuilder<? extends Statement>> body() { return Optional.ofNullable(body); }
        public Builder setBody(NodeBuilder<? extends Statement> value) { syntaxEdited = true; body = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends Expression>> condition() { return Optional.ofNullable(condition); }
        public Builder setCondition(NodeBuilder<? extends Expression> value) { syntaxEdited = true; condition = Objects.requireNonNull(value); return this; }
        @Override DoWhileStatement newNode() { return new DoWhileStatement(); }
        @Override void fillNode(DoWhileStatement node, BuildContext context) {
            node.body = context.copy(body);
            node.condition = context.copy(condition);
        }
    }
}
