/* Physical source comments, deliberately without documentation interpretation. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;

/** One exact physical comment. Ownership belongs to SourceFile, not its explanatory targets. */
public final class Comment extends Node {
    public enum Kind { LINE, BLOCK }
    private Kind kind;
    private String text;
    private Comment() {}
    public Kind kind() { return kind; }
    public String text() { return text; }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.kind = kind; builder.text = text; return builder;
    }
    public static final class Builder extends NodeBuilder<Comment> {
        private Kind kind;
        private String text;
        private Builder() { super(Comment.class); }
        public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
        public Builder setText(String value) { text = Objects.requireNonNull(value); return this; }
        @Override Comment newNode() { return new Comment(); }
        @Override void fillNode(Comment node, BuildContext context) {
            node.kind = Objects.requireNonNull(kind, "Comment.kind");
            node.text = requiredText(text, "Comment.text");
        }
    }
}
