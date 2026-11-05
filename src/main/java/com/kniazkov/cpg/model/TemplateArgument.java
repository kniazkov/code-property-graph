/* Structured written template argument. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
public final class TemplateArgument extends Node {
    public enum Kind { TYPE, VALUE, TEMPLATE }
    private Kind kind;
    private TypeNode type;
    private Expression expression;
    private TemplateArgument() {}
    public Kind kind() { return kind; }
    public Optional<TypeNode> type() { return Optional.ofNullable(type); }
    public Optional<Expression> expression() { return Optional.ofNullable(expression); }
    @Override public List<Node> children() { return type != null ? List.of(type) : List.of(expression); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder); builder.kind = kind;
        builder.type = type == null ? null : context.edge(type, TypeNode.class);
        builder.expression = expression == null ? null : context.edge(expression, Expression.class); return builder;
    }
    public static final class Builder extends NodeBuilder<TemplateArgument> {
        private Kind kind = Kind.TYPE;
        private NodeBuilder<? extends TypeNode> type;
        private NodeBuilder<? extends Expression> expression;
        private Builder() { super(TemplateArgument.class); }
        public Kind kind() { return kind; }
        public Optional<NodeBuilder<? extends TypeNode>> type() { return Optional.ofNullable(type); }
        public Optional<NodeBuilder<? extends Expression>> expression() { return Optional.ofNullable(expression); }
        public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
        public Builder setType(NodeBuilder<? extends TypeNode> value) { type = Objects.requireNonNull(value); expression = null; return this; }
        public Builder setExpression(NodeBuilder<? extends Expression> value) { expression = Objects.requireNonNull(value); type = null; kind = Kind.VALUE; return this; }
        @Override TemplateArgument newNode() { return new TemplateArgument(); }
        @Override void fillNode(TemplateArgument node, BuildContext context) {
            if ((type == null) == (expression == null) || (kind == Kind.VALUE) != (expression != null)) throw new IllegalStateException("Template argument kind and payload disagree");
            node.kind = kind; node.type = type == null ? null : context.copy(type); node.expression = expression == null ? null : context.copy(expression);
        }
    }
}
