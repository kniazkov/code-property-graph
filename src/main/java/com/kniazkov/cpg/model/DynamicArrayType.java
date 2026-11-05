/* Owned language-independent dynamic-array semantics. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Derived semantics referencing preserved written syntax; references never own syntax. */
public final class DynamicArrayType extends Node {
    private ClassDeclaration declaration;
    public ClassDeclaration declaration() { return declaration; }
    private TypeNode elementType;
    public TypeNode elementType() { return elementType; }
    private List<TypeNode> writtenTypes = List.of();
    public List<TypeNode> writtenTypes() { return writtenTypes; }
    public ClassDeclaration concreteClass() { return declaration; }
    @Override public List<Node> children() { return List.of(); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(); if (declaration != null) result.add(declaration);
        if (elementType != null) result.add(elementType);
        result.addAll(writtenTypes);
        return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    public static Builder create() { return new Builder(); }
    @Override Builder copyBuilder(EditContext c) { var b = new Builder(); c.register(this, b);
        b.declaration = declaration == null ? null : c.edge(declaration, ClassDeclaration.class);
        b.elementType = elementType == null ? null : c.edge(elementType, TypeNode.class);
        b.writtenTypes = c.copyAll(writtenTypes, TypeNode.class);
        return b; }
    public static final class Builder extends NodeBuilder<DynamicArrayType> {
        private Builder() { super(DynamicArrayType.class); }
        private NodeBuilder<? extends ClassDeclaration> declaration;
        public Builder setDeclaration(NodeBuilder<? extends ClassDeclaration> value) { declaration = value; return this; }
        private NodeBuilder<? extends TypeNode> elementType;
        public Builder setElementType(NodeBuilder<? extends TypeNode> value) { elementType = value; return this; }
        private List<NodeBuilder<? extends TypeNode>> writtenTypes = new ArrayList<>();
        public Builder setWrittenTypes(List<NodeBuilder<? extends TypeNode>> value) { writtenTypes = new ArrayList<>(value); return this; }
        public Builder setConcreteClass(NodeBuilder<? extends ClassDeclaration> value) { return setDeclaration(value); }
        @Override DynamicArrayType newNode() { return new DynamicArrayType(); }
        @Override void fillNode(DynamicArrayType n, BuildContext c) {
            n.declaration = declaration == null ? null : c.copy(declaration);
            n.elementType = c.copy(Objects.requireNonNull(elementType, "elementType"));
            n.writtenTypes = c.copyAll(writtenTypes);
        }
    }
}
