/* Code Property Graph: owned semantic template instances. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** An instantiated callable, separate from the written template and its arguments.
 * Currently represents basic type substitution only; completeness is explicit. */
public final class TemplateInstance extends Node {
    private CallableDeclaration origin, declaration;
    private CallExpression cause;
    private SourceFile source;
    private List<TypeNode> arguments = List.of();
    public CallableDeclaration origin() { return origin; }
    public CallableDeclaration declaration() { return declaration; }
    public CallExpression cause() { return cause; }
    public SourceFile source() { return source; }
    public List<TypeNode> arguments() { return arguments; }
    public boolean complete() { return false; } // Full instantiation diagnostics await the specialization pass.
    public String status() { return "basic-type-substitution"; }
    @Override public List<Node> children() { var nodes = new ArrayList<Node>(arguments); nodes.add(declaration); return List.copyOf(nodes); }
    @Override public List<Node> references() { return List.of(origin, cause, source); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var b = new Builder(); context.register(this, b);
        b.origin = context.edge(origin, CallableDeclaration.class); b.declaration = context.edge(declaration, CallableDeclaration.class);
        b.cause = context.copy(cause); b.source = context.edge(source, SourceFile.class);
        b.arguments = context.copyAll(arguments, TypeNode.class); return b;
    }
    public static final class Builder extends NodeBuilder<TemplateInstance> {
        NodeBuilder<? extends CallableDeclaration> origin, declaration;
        NodeBuilder<? extends CallExpression> cause;
        NodeBuilder<? extends SourceFile> source;
        List<NodeBuilder<? extends TypeNode>> arguments = List.of();
        Builder() { super(TemplateInstance.class); }
        @Override TemplateInstance newNode() { return new TemplateInstance(); }
        @Override void fillNode(TemplateInstance n, BuildContext c) {
            n.origin = c.copy(origin); n.declaration = c.copy(declaration); n.cause = c.copy(cause); n.source = c.copy(source); n.arguments = c.copyAll(arguments);
        }
    }
}
