/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a CaseStatement. */
public final class CaseStatement extends Statement {
    private Expression value;
    private Statement statement;
    private CaseStatement() {}
    public Optional<Expression> value() { return Optional.ofNullable(value); }
    public Statement statement() { return statement; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); if (value != null) result.add(value); result.add(statement); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.value = value == null ? null : context.edge(value, Expression.class);
        builder.statement = context.edge(statement, Statement.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<CaseStatement> {
        private NodeBuilder<? extends Expression> value;
        private NodeBuilder<? extends Statement> statement;
        private Builder() { super(CaseStatement.class); }
        public Optional<NodeBuilder<? extends Expression>> value() { return Optional.ofNullable(value); }
        public Builder setValue(NodeBuilder<? extends Expression> value) { syntaxEdited = true; this.value = Objects.requireNonNull(value); return this; }
        public Builder clearValue() { syntaxEdited = true; value = null; return this; }
        public Optional<NodeBuilder<? extends Statement>> statement() { return Optional.ofNullable(statement); }
        public Builder setStatement(NodeBuilder<? extends Statement> value) { syntaxEdited = true; statement = Objects.requireNonNull(value); return this; }
        @Override CaseStatement newNode() { return new CaseStatement(); }
        @Override void fillNode(CaseStatement node, BuildContext context) {
            node.value = value == null ? null : context.copy(value);
            node.statement = context.copy(statement);
        }
    }
}
