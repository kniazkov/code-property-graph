/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a LabelStatement. */
public final class LabelStatement extends Statement {
    private String label;
    private Statement statement;
    private LabelStatement() {}
    public String label() { return label; }
    public Statement statement() { return statement; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); result.add(statement); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.label = label;
        builder.statement = context.edge(statement, Statement.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<LabelStatement> {
        private String label;
        private NodeBuilder<? extends Statement> statement;
        private Builder() { super(LabelStatement.class); }
        public Optional<String> label() { return Optional.ofNullable(label); }
        public Builder setLabel(String value) { syntaxEdited = true; label = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends Statement>> statement() { return Optional.ofNullable(statement); }
        public Builder setStatement(NodeBuilder<? extends Statement> value) { syntaxEdited = true; statement = Objects.requireNonNull(value); return this; }
        @Override LabelStatement newNode() { return new LabelStatement(); }
        @Override void fillNode(LabelStatement node, BuildContext context) {
            node.label = requiredText(label, "LabelStatement.label");
            node.statement = context.copy(statement);
        }
    }
}
