/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a CatchClause. */
public final class CatchClause extends Node {
    private ParameterDeclaration parameter;
    private BlockStatement body;
    private CatchClause() {}
    public Optional<ParameterDeclaration> parameter() { return Optional.ofNullable(parameter); }
    public BlockStatement body() { return body; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); if (parameter != null) result.add(parameter); result.add(body); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.parameter = parameter == null ? null : context.edge(parameter, ParameterDeclaration.class);
        builder.body = context.edge(body, BlockStatement.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<CatchClause> {
        private NodeBuilder<? extends ParameterDeclaration> parameter;
        private NodeBuilder<? extends BlockStatement> body;
        private Builder() { super(CatchClause.class); }
        public Optional<NodeBuilder<? extends ParameterDeclaration>> parameter() { return Optional.ofNullable(parameter); }
        public Builder setParameter(NodeBuilder<? extends ParameterDeclaration> value) { syntaxEdited = true; parameter = Objects.requireNonNull(value); return this; }
        public Builder clearParameter() { syntaxEdited = true; parameter = null; return this; }
        public Optional<NodeBuilder<? extends BlockStatement>> body() { return Optional.ofNullable(body); }
        public Builder setBody(NodeBuilder<? extends BlockStatement> value) { syntaxEdited = true; body = Objects.requireNonNull(value); return this; }
        @Override CatchClause newNode() { return new CatchClause(); }
        @Override void fillNode(CatchClause node, BuildContext context) {
            node.parameter = parameter == null ? null : context.copy(parameter);
            node.body = context.copy(body);
        }
    }
}
