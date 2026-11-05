/* C++03 deallocation syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Scalar or array delete, optionally qualified with the global scope. */
public final class DeleteExpression extends Expression {
    private Expression operand;
    private boolean array, global;
    private DeleteExpression() {}
    public Expression operand() { return operand; }
    public boolean isArray() { return array; }
    public boolean isGlobal() { return global; }
    @Override public List<Node> children() { return List.of(operand); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) { var b = new Builder(); context.register(this,b); b.operand=context.edge(operand,Expression.class); b.array=array; b.global=global; return b; }
    public static final class Builder extends NodeBuilder<DeleteExpression> {
        private NodeBuilder<? extends Expression> operand;
        private boolean array, global;
        private Builder() { super(DeleteExpression.class); }
        public Optional<NodeBuilder<? extends Expression>> operand() { return Optional.ofNullable(operand); }
        public boolean isArray() { return array; }
        public boolean isGlobal() { return global; }
        public Builder setOperand(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); operand=Objects.requireNonNull(value); return this; }
        public Builder setArray(boolean value) { clearExpressionInfo(); array=value; return this; }
        public Builder setGlobal(boolean value) { clearExpressionInfo(); global=value; return this; }
        @Override DeleteExpression newNode() { return new DeleteExpression(); }
        @Override void fillNode(DeleteExpression node, BuildContext context) { node.operand=context.copy(Objects.requireNonNull(operand)); node.array=array; node.global=global; }
    }
}
