/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a SwitchStatement. */
public final class SwitchStatement extends Statement {
    private Expression condition;
    private Statement body;
    private SwitchStatement() {}
    public Expression condition() { return condition; }
    public Statement body() { return body; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); result.add(condition); result.add(body); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.condition = context.edge(condition, Expression.class);
        builder.body = context.edge(body, Statement.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<SwitchStatement> {
        private NodeBuilder<? extends Expression> condition;
        private NodeBuilder<? extends Statement> body;
        private Builder() { super(SwitchStatement.class); }
        public Optional<NodeBuilder<? extends Expression>> condition() { return Optional.ofNullable(condition); }
        public Builder setCondition(NodeBuilder<? extends Expression> value) { syntaxEdited = true; condition = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends Statement>> body() { return Optional.ofNullable(body); }
        public Builder setBody(NodeBuilder<? extends Statement> value) { syntaxEdited = true; body = Objects.requireNonNull(value); return this; }
        @Override SwitchStatement newNode() { return new SwitchStatement(); }
        @Override void fillNode(SwitchStatement node, BuildContext context) {
            node.condition = context.copy(condition);
            node.body = context.copy(body);
        }
    }
}
