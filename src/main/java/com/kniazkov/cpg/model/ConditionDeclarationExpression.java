/* C++03 statement syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Explicit syntax for a ConditionDeclarationExpression. */
public final class ConditionDeclarationExpression extends Expression {
    private VariableDeclaration declaration;
    private ConditionDeclarationExpression() {}
    public VariableDeclaration declaration() { return declaration; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); result.add(declaration); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.declaration = context.edge(declaration, VariableDeclaration.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<ConditionDeclarationExpression> {
        private NodeBuilder<? extends VariableDeclaration> declaration;
        private Builder() { super(ConditionDeclarationExpression.class); }
        public Optional<NodeBuilder<? extends VariableDeclaration>> declaration() { return Optional.ofNullable(declaration); }
        public Builder setDeclaration(NodeBuilder<? extends VariableDeclaration> value) { syntaxEdited = true; declaration = Objects.requireNonNull(value); return this; }
        @Override ConditionDeclarationExpression newNode() { return new ConditionDeclarationExpression(); }
        @Override void fillNode(ConditionDeclarationExpression node, BuildContext context) {
            node.declaration = context.copy(declaration);
        }
    }
}
