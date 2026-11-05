/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a GotoStatement. */
public final class GotoStatement extends Statement {
    private String label;
    private GotoStatement() {}
    public String label() { return label; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.label = label;
        return builder;
    }
    public static final class Builder extends NodeBuilder<GotoStatement> {
        private String label;
        private Builder() { super(GotoStatement.class); }
        public Optional<String> label() { return Optional.ofNullable(label); }
        public Builder setLabel(String value) { syntaxEdited = true; label = Objects.requireNonNull(value); return this; }
        @Override GotoStatement newNode() { return new GotoStatement(); }
        @Override void fillNode(GotoStatement node, BuildContext context) {
            node.label = requiredText(label, "GotoStatement.label");
        }
    }
}
