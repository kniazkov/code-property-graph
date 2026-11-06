/* Code Property Graph: owned semantic template instances. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** An instantiated callable, separate from the written template and all requests for it. */
public final class TemplateInstance extends Node {
    private CallableDeclaration origin, selected, declaration;
    private List<Node> requests = List.of();
    private SourceFile source;
    private List<TemplateArgument> structuredArguments = List.of();
    public List<TemplateArgument> structuredArguments() { return structuredArguments; }
    /** Generic callable used for deduction, possibly a forward declaration. */
    public CallableDeclaration origin() { return origin; }
    /** Actual definition copied, which may differ from a forward declaration or primary origin. */
    public CallableDeclaration selected() { return selected; }
    /** Separately owned substituted callable; never the written selected definition itself. */
    public CallableDeclaration declaration() { return declaration; }
    /** First call request, retained for compatibility; explicit-only instances have no call. */
    public CallExpression cause() { return requests.stream().filter(CallExpression.class::isInstance).map(CallExpression.class::cast).findFirst().orElse(null); }
    public List<Node> requests() { return requests; }
    public List<CallExpression> causes() { return requests.stream().filter(CallExpression.class::isInstance).map(CallExpression.class::cast).toList(); }
    public SourceFile source() { return source; }
    public List<TypeNode> arguments() { return structuredArguments.stream().flatMap(argument -> argument.type().stream()).toList(); }
    public boolean complete() {
        boolean[] result={declaration.bodyComplete() && declaration.body().isPresent()};
        declaration.walk(new NodeVisitor(){
            @Override public void visit(Node node){
                if(node.diagnostics().stream().anyMatch(d->d.severity()==Diagnostic.Severity.ERROR))result[0]=false;
                if(node instanceof Expression expression && expression.dependent())result[0]=false;
                if(node instanceof CallExpression call && call.target().isEmpty())result[0]=false;
            }
        });
        return result[0];
    }
    public String status() { return complete() ? "instantiated" : "incomplete-instantiation"; }
    @Override public List<Node> children() { var nodes = new ArrayList<Node>(structuredArguments); nodes.add(declaration); return List.copyOf(nodes); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(requests); result.add(origin); result.add(selected); result.add(source); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var b = new Builder(); context.register(this, b);
        b.origin = context.edge(origin, CallableDeclaration.class); b.selected = context.edge(selected, CallableDeclaration.class);
        b.declaration = context.edge(declaration, CallableDeclaration.class);
        b.requests = new ArrayList<>(); requests.forEach(request -> b.requests.add(context.copy(request))); b.source = context.edge(source, SourceFile.class);
        b.structuredArguments=context.copyAll(structuredArguments,TemplateArgument.class); return b;
    }
    public static final class Builder extends NodeBuilder<TemplateInstance> {
        NodeBuilder<? extends CallableDeclaration> origin, selected, declaration;
        List<NodeBuilder<? extends Node>> requests = new ArrayList<>();
        NodeBuilder<? extends SourceFile> source;
        List<NodeBuilder<? extends TemplateArgument>> structuredArguments=List.of();
        Builder() { super(TemplateInstance.class); }
        @Override TemplateInstance newNode() { return new TemplateInstance(); }
        @Override void fillNode(TemplateInstance n, BuildContext c) {
            n.origin = c.copy(origin); n.selected = c.copy(selected); n.declaration = c.copy(declaration);
            n.requests = c.copyAll(requests); n.source = c.copy(source); n.structuredArguments=c.copyAll(structuredArguments);
        }
    }
}
