/* C++03 written template type parameters. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** A type template parameter owned by its callable, not a concrete instantiated type. */
public final class TemplateParameterDeclaration extends Declaration {
    private String name;
    private String keyword;
    private TemplateParameterDeclaration() {}
    public String name() { return name; }
    /** Preserves the interchangeable written class/typename keyword. */
    public String keyword() { return keyword; }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.name = name; builder.keyword = keyword; return builder;
    }
    public static final class Builder extends NodeBuilder<TemplateParameterDeclaration> {
        private String name;
        private String keyword = "class";
        private Builder() { super(TemplateParameterDeclaration.class); }
        public Optional<String> name() { return Optional.ofNullable(name); }
        public String keyword() { return keyword; }
        public Builder setName(String value) { syntaxEdited = true; name = Objects.requireNonNull(value); return this; }
        public Builder setKeyword(String value) {
            if (!List.of("class", "typename").contains(value)) throw new IllegalArgumentException("Expected class or typename");
            syntaxEdited = true; keyword = value; return this;
        }
        @Override TemplateParameterDeclaration newNode() { return new TemplateParameterDeclaration(); }
        @Override void fillNode(TemplateParameterDeclaration node, BuildContext context) {
            node.name = requiredText(name, "TemplateParameterDeclaration.name"); node.keyword = keyword;
        }
    }
}
