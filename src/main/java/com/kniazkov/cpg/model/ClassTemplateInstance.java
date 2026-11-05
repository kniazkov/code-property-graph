/* Owned C++03 class-template specialization. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Concrete class syntax and all demands for the same semantic specialization. */
public final class ClassTemplateInstance extends Node {
    ClassDeclaration origin, selected, declaration;
    SourceFile source;
    String identity;
    List<TemplateArgument> arguments = List.of();
    List<Node> requests = List.of();
    public ClassDeclaration origin() { return origin; }
    public ClassDeclaration selected() { return selected; }
    public ClassDeclaration declaration() { return declaration; }
    public SourceFile source() { return source; }
    public String identity() { return identity; }
    public List<TemplateArgument> arguments() { return arguments; }
    public List<Node> requests() { return requests; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(arguments); result.add(declaration); return List.copyOf(result); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(requests); result.add(origin); result.add(selected); result.add(source); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext c) {
        var b = new Builder(); c.register(this, b);
        b.origin = c.edge(origin, ClassDeclaration.class); b.selected = c.edge(selected, ClassDeclaration.class);
        b.declaration = c.edge(declaration, ClassDeclaration.class); b.source = c.edge(source, SourceFile.class);
        b.identity = identity; b.arguments = c.copyAll(arguments, TemplateArgument.class); b.requests = new ArrayList<>(); for (Node request : requests) b.requests.add(c.copy(request)); return b;
    }
    public static final class Builder extends NodeBuilder<ClassTemplateInstance> {
        NodeBuilder<? extends ClassDeclaration> origin, selected, declaration;
        NodeBuilder<? extends SourceFile> source;
        String identity;
        List<NodeBuilder<? extends TemplateArgument>> arguments = List.of();
        List<NodeBuilder<? extends Node>> requests = new ArrayList<>();
        Builder() { super(ClassTemplateInstance.class); }
        @Override ClassTemplateInstance newNode() { return new ClassTemplateInstance(); }
        @Override void fillNode(ClassTemplateInstance n, BuildContext c) {
            n.origin = c.copy(origin); n.selected = c.copy(selected); n.declaration = c.copy(declaration);
            n.source = c.copy(source); n.identity = identity; n.arguments = c.copyAll(arguments); n.requests = c.copyAll(requests);
        }
    }
}
