/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a BreakStatement. */
public final class BreakStatement extends Statement {
    private BreakStatement() {}
    @Override public List<Node> children() { var result = new ArrayList<Node>(); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        return builder;
    }
    public static final class Builder extends NodeBuilder<BreakStatement> {
        private Builder() { super(BreakStatement.class); }
        @Override BreakStatement newNode() { return new BreakStatement(); }
        @Override void fillNode(BreakStatement node, BuildContext context) {
        }
    }
}
