/* Operand-bearing nonlocal environment save. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Saves opaque runtime state and may return again after longjmp. No value or layout is inferred. */
public final class SetjmpExpression extends Expression {
    private Expression buffer;
    private SetjmpExpression() {}
    public Expression buffer() { return buffer; }
    public String emission() { return "gcc-clang-glibc"; }
    @Override public List<Node> children() { return List.of(buffer); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.buffer = context.edge(buffer, Expression.class); return builder;
    }
    public static final class Builder extends NodeBuilder<SetjmpExpression> {
        private NodeBuilder<? extends Expression> buffer;
        private Builder() { super(SetjmpExpression.class); }
        public Optional<NodeBuilder<? extends Expression>> buffer() { return Optional.ofNullable(buffer); }
        public Builder setBuffer(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); buffer = Objects.requireNonNull(value); return this; }
        @Override SetjmpExpression newNode() { return new SetjmpExpression(); }
        @Override void fillNode(SetjmpExpression node, BuildContext context) {
            node.buffer = context.copy(Objects.requireNonNull(buffer, "jmp_buf operand"));
        }
    }
}
