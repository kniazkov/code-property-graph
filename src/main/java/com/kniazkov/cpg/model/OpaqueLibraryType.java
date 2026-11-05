/* Portable implementation-defined library type. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** A specified category whose underlying implementation type, width and layout are unknown. */
public final class OpaqueLibraryType extends TypeNode {
    public enum Category { ARITHMETIC, INTEGER, OBJECT, UNSPECIFIED }
    private String name, identity;
    private Category category;
    private ClassDeclaration iteratorContainer;
    private TypeNode iteratorDifferenceType;
    private TypedefDeclaration iteratorValueType;
    public String name() { return name; }
    public String identity() { return identity; }
    public Category category() { return category; }
    /** Trusted catalogue container for an abstract iterator, never a pointer representation. */
    public Optional<ClassDeclaration> iteratorContainer() { return Optional.ofNullable(iteratorContainer); }
    public Optional<TypeNode> iteratorValueType() { return Optional.ofNullable(iteratorValueType).map(TypedefDeclaration::type); }
    public Optional<TypeNode> iteratorDifferenceType() { return Optional.ofNullable(iteratorDifferenceType); }
    @Override public List<Node> references() { var result=new ArrayList<Node>(); if(iteratorContainer!=null)result.add(iteratorContainer);if(iteratorDifferenceType!=null)result.add(iteratorDifferenceType);if(iteratorValueType!=null)result.add(iteratorValueType);return List.copyOf(result); }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this,Builder.class); }
    @Override Builder copyBuilder(EditContext c) { var b=new Builder(); c.register(this,b); b.name=name;b.identity=identity;b.category=category;b.iteratorContainer=iteratorContainer==null?null:c.edge(iteratorContainer,ClassDeclaration.class);b.iteratorValueType=iteratorValueType==null?null:c.edge(iteratorValueType,TypedefDeclaration.class);b.iteratorDifferenceType=iteratorDifferenceType==null?null:c.edge(iteratorDifferenceType,TypeNode.class);return b; }
    public static final class Builder extends NodeBuilder<OpaqueLibraryType> {
        private String name,identity;
        private NodeBuilder<? extends ClassDeclaration> iteratorContainer;
        private NodeBuilder<? extends TypeNode> iteratorDifferenceType;
        private NodeBuilder<? extends TypedefDeclaration> iteratorValueType;
        private Category category=Category.UNSPECIFIED;
        private Builder() { super(OpaqueLibraryType.class); }
        public Builder setName(String value) { name=Objects.requireNonNull(value);return this; }
        public Builder setIdentity(String value) { identity=Objects.requireNonNull(value);return this; }
        public Builder setCategory(Category value) { category=Objects.requireNonNull(value);return this; }
        public Builder setIteratorContainer(NodeBuilder<? extends ClassDeclaration> value) { iteratorContainer=value;return this; }
        public Builder setIteratorValueType(NodeBuilder<? extends TypedefDeclaration> value) { iteratorValueType=value;return this; }
        public Builder setIteratorDifferenceType(NodeBuilder<? extends TypeNode> value) { iteratorDifferenceType=value;return this; }
        @Override OpaqueLibraryType newNode() { return new OpaqueLibraryType(); }
        @Override void fillNode(OpaqueLibraryType n,BuildContext c) { n.name=requiredText(name,"OpaqueLibraryType.name");n.identity=requiredText(identity,"OpaqueLibraryType.identity");n.category=category;n.iteratorContainer=iteratorContainer==null?null:c.copy(iteratorContainer);n.iteratorValueType=iteratorValueType==null?null:c.copy(iteratorValueType);n.iteratorDifferenceType=iteratorDifferenceType==null?null:c.copy(iteratorDifferenceType); }
    }
}
