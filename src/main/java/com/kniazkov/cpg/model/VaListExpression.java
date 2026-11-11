/* Operand-bearing C++03 variable-argument state operations. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Compiler operations on opaque va_list state. The type operand is never evaluated;
 * the last named parameter is a binding operand, not a runtime value read. */
public final class VaListExpression extends Expression {
    public enum Kind { START, ARG, END }
    private Kind kind;
    private Expression list, lastParameter;
    private TypeNode requestedType;
    private VaListExpression() {}
    public Kind kind() { return kind; }
    public Expression list() { return list; }
    public Optional<Expression> lastParameter() { return Optional.ofNullable(lastParameter); }
    public Optional<TypeNode> requestedType() { return Optional.ofNullable(requestedType); }
    public String emission() { return "gcc-clang-builtin"; }
    @Override public List<Node> children() {
        var result = new ArrayList<Node>(); result.add(list);
        if (lastParameter != null) result.add(lastParameter);
        if (requestedType != null) result.add(requestedType);
        return List.copyOf(result);
    }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder); builder.kind = kind;
        builder.list = context.edge(list, Expression.class);
        builder.lastParameter = lastParameter == null ? null : context.edge(lastParameter, Expression.class);
        builder.requestedType = requestedType == null ? null : context.edge(requestedType, TypeNode.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<VaListExpression> {
        private Kind kind = Kind.ARG;
        private NodeBuilder<? extends Expression> list, lastParameter;
        private NodeBuilder<? extends TypeNode> requestedType;
        private Builder() { super(VaListExpression.class); }
        public Kind kind() { return kind; }
        public Optional<NodeBuilder<? extends Expression>> list() { return Optional.ofNullable(list); }
        public Optional<NodeBuilder<? extends Expression>> lastParameter() { return Optional.ofNullable(lastParameter); }
        public Optional<NodeBuilder<? extends TypeNode>> requestedType() { return Optional.ofNullable(requestedType); }
        public Builder setKind(Kind value) { clearExpressionInfo(); kind = Objects.requireNonNull(value); return this; }
        public Builder setList(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); list = Objects.requireNonNull(value); return this; }
        public Builder setLastParameter(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); lastParameter = Objects.requireNonNull(value); return this; }
        public Builder setRequestedType(NodeBuilder<? extends TypeNode> value) { clearExpressionInfo(); requestedType = Objects.requireNonNull(value); return this; }
        public Builder clearLastParameter() { clearExpressionInfo(); lastParameter = null; return this; }
        public Builder clearRequestedType() { clearExpressionInfo(); requestedType = null; return this; }
        @Override VaListExpression newNode() { return new VaListExpression(); }
        @Override void fillNode(VaListExpression node, BuildContext context) {
            if ((kind == Kind.START) != (lastParameter != null) || (kind == Kind.ARG) != (requestedType != null))
                throw new IllegalStateException("va_start requires a last parameter, va_arg a type, and va_end neither");
            node.kind = kind; node.list = context.copy(Objects.requireNonNull(list, "va_list operand"));
            node.lastParameter = lastParameter == null ? null : context.copy(lastParameter);
            node.requestedType = requestedType == null ? null : context.copy(requestedType);
        }
    }
}
