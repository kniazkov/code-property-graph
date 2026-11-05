/* Portable implementation-defined library type. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** A specified category whose underlying implementation type, width and layout are unknown. */
public final class OpaqueLibraryType extends TypeNode {
    public enum Category { ARITHMETIC, INTEGER, OBJECT, UNSPECIFIED }
    private String name, identity;
    private Category category;
    public String name() { return name; }
    public String identity() { return identity; }
    public Category category() { return category; }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this,Builder.class); }
    @Override Builder copyBuilder(EditContext c) { var b=new Builder(); c.register(this,b); b.name=name;b.identity=identity;b.category=category;return b; }
    public static final class Builder extends NodeBuilder<OpaqueLibraryType> {
        private String name,identity;
        private Category category=Category.UNSPECIFIED;
        private Builder() { super(OpaqueLibraryType.class); }
        public Builder setName(String value) { name=Objects.requireNonNull(value);return this; }
        public Builder setIdentity(String value) { identity=Objects.requireNonNull(value);return this; }
        public Builder setCategory(Category value) { category=Objects.requireNonNull(value);return this; }
        @Override OpaqueLibraryType newNode() { return new OpaqueLibraryType(); }
        @Override void fillNode(OpaqueLibraryType n,BuildContext c) { n.name=requiredText(name,"OpaqueLibraryType.name");n.identity=requiredText(identity,"OpaqueLibraryType.identity");n.category=category; }
    }
}
