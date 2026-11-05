/* C++03 written template parameters. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** A type, value or template parameter owned by its template declaration. */
public final class TemplateParameterDeclaration extends Declaration {
    public enum Kind { TYPE, VALUE, TEMPLATE }
    private Kind kind = Kind.TYPE;
    private TypeNode type;
    private TemplateArgument defaultArgument;
    private List<TemplateParameterDeclaration> templateParameters = List.of();
    public Kind kind() { return kind; }
    public Optional<TypeNode> type() { return Optional.ofNullable(type); }
    public Optional<TemplateArgument> defaultArgument() { return Optional.ofNullable(defaultArgument); }
    public List<TemplateParameterDeclaration> templateParameters() { return templateParameters; }
    private String name;
    private String keyword;
    private TemplateParameterDeclaration() {}
    public String name() { return name; }
    /** Preserves the interchangeable written class/typename keyword. */
    public String keyword() { return keyword; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(templateParameters); if (type != null) result.add(type); if (defaultArgument != null) result.add(defaultArgument); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.kind = kind; builder.type = type == null ? null : context.edge(type, TypeNode.class); builder.defaultArgument = defaultArgument == null ? null : context.edge(defaultArgument, TemplateArgument.class); builder.templateParameters = context.copyAll(templateParameters, TemplateParameterDeclaration.class); builder.name = name; builder.keyword = keyword; return builder;
    }
    public static final class Builder extends NodeBuilder<TemplateParameterDeclaration> {
        private Kind kind = Kind.TYPE;
        private NodeBuilder<? extends TypeNode> type;
        private NodeBuilder<? extends TemplateArgument> defaultArgument;
        private List<NodeBuilder<? extends TemplateParameterDeclaration>> templateParameters = new ArrayList<>();
        public Kind kind() { return kind; }
        public Optional<NodeBuilder<? extends TypeNode>> type() { return Optional.ofNullable(type); }
        public Optional<NodeBuilder<? extends TemplateArgument>> defaultArgument() { return Optional.ofNullable(defaultArgument); }
        public List<NodeBuilder<? extends TemplateParameterDeclaration>> templateParameters() { return List.copyOf(templateParameters); }
        public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
        public Builder setType(NodeBuilder<? extends TypeNode> value) { type = Objects.requireNonNull(value); return this; }
        public Builder setDefaultArgument(NodeBuilder<? extends TemplateArgument> value) { defaultArgument = Objects.requireNonNull(value); return this; }
        public Builder setTemplateParameters(List<? extends NodeBuilder<? extends TemplateParameterDeclaration>> values) { templateParameters = new ArrayList<>(values); return this; }
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
            if ((kind == Kind.VALUE) != (type != null)) throw new IllegalStateException("Only a value parameter requires a declared type");
            if (kind != Kind.TEMPLATE && !templateParameters.isEmpty()) throw new IllegalStateException("Nested parameters require template parameter kind");
            node.kind = kind; node.type = type == null ? null : context.copy(type); node.defaultArgument = defaultArgument == null ? null : context.copy(defaultArgument); node.templateParameters = context.copyAll(templateParameters); node.name = Objects.requireNonNull(name, "TemplateParameterDeclaration.name"); node.keyword = keyword;
        }
    }
}
