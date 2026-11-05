/* C++03 written initializer syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** An initializer list, direct initializer, or typed temporary construction. */
public final class InitializationExpression extends Expression {
    public enum Form { DIRECT, AGGREGATE, TEMPORARY }
    private Form form = Form.DIRECT;
    private TypeNode type;
    private List<Expression> arguments = List.of();
    private InitializationExpression() {}
    public Form form() { return form; }
    public Optional<TypeNode> type() { return Optional.ofNullable(type); }
    public List<Expression> arguments() { return arguments; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); if (type != null) result.add(type); result.addAll(arguments); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) { var b = new Builder(); context.register(this, b); b.form = form; b.type = type == null ? null : context.edge(type, TypeNode.class); b.arguments = context.copyAll(arguments, Expression.class); return b; }
    public static final class Builder extends NodeBuilder<InitializationExpression> {
        private Form form = Form.DIRECT;
        private NodeBuilder<? extends TypeNode> type;
        private List<NodeBuilder<? extends Expression>> arguments = new ArrayList<>();
        private Builder() { super(InitializationExpression.class); }
        public Form form() { return form; }
        public Optional<NodeBuilder<? extends TypeNode>> type() { return Optional.ofNullable(type); }
        public Builder clearType() { clearExpressionInfo(); type = null; return this; }
        public Builder setForm(Form value) { clearExpressionInfo(); form = Objects.requireNonNull(value); return this; }
        public Builder setType(NodeBuilder<? extends TypeNode> value) { clearExpressionInfo(); type = Objects.requireNonNull(value); return this; }
        public Builder addArgument(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); arguments.add(Objects.requireNonNull(value)); return this; }
        public Builder setArguments(List<? extends NodeBuilder<? extends Expression>> values) { clearExpressionInfo(); arguments = new ArrayList<>(List.copyOf(values)); return this; }
        public List<NodeBuilder<? extends Expression>> arguments() { return List.copyOf(arguments); }
        @Override InitializationExpression newNode() { return new InitializationExpression(); }
        @Override void fillNode(InitializationExpression node, BuildContext context) { node.form = form; node.type = type == null ? null : context.copy(type); node.arguments = context.copyAll(arguments); if (form == Form.TEMPORARY && type == null) throw new IllegalStateException("Temporary requires type"); }
    }
}
