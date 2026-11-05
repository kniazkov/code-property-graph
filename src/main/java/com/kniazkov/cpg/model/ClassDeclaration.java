/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A named class with ordered declarations. */
public final class ClassDeclaration extends Declaration {
    private List<TemplateParameterDeclaration> templateParameters = List.of();
    public List<TemplateParameterDeclaration> templateParameters() { return templateParameters; }
    private String name;
    private boolean implicit;
    private String keyword = "class";
    private String memberAccess = "public";
    private List<String> modifiers = List.of();
    private List<Declaration> members = List.of();
    private List<BaseSpecifier> bases = List.of();
    public List<BaseSpecifier> bases() { return bases; }

    private ClassDeclaration() {
    }

    public boolean implicit() { return implicit; }
    public String keyword() { return keyword; }
    public String memberAccess() { return memberAccess; }

    public String name() {
        return name;
    }

    public List<String> modifiers() {
        return modifiers;
    }

    public List<Declaration> members() {
        return members;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.addAll(templateParameters);
        children.addAll(bases);
        children.addAll(members);
        return List.copyOf(children);
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }

    public static Builder create() {
        return new Builder();
    }

    @Override
    public Builder edit() {
        return new EditContext().edit(this, Builder.class);
    }

    @Override
    Builder copyBuilder(EditContext context) {
        Builder builder = new Builder();
        context.register(this, builder);
        builder.templateParameters = context.copyAll(templateParameters, TemplateParameterDeclaration.class);
        builder.name = name;
        builder.bases = context.copyAll(bases, BaseSpecifier.class);
        builder.implicit = implicit;
        builder.keyword = keyword; builder.memberAccess = memberAccess;
        builder.modifiers = new ArrayList<>(modifiers);
        builder.members = context.copyAll(members, Declaration.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<ClassDeclaration> {
        private List<NodeBuilder<? extends BaseSpecifier>> bases = new ArrayList<>();
        public List<NodeBuilder<? extends BaseSpecifier>> bases() { return List.copyOf(bases); }
        public Builder setBases(List<? extends NodeBuilder<? extends BaseSpecifier>> values) { bases = new ArrayList<>(List.copyOf(values)); return this; }
        public Builder addBase(NodeBuilder<? extends BaseSpecifier> value) { bases.add(Objects.requireNonNull(value)); return this; }
        private List<NodeBuilder<? extends TemplateParameterDeclaration>> templateParameters = new ArrayList<>();
        public List<NodeBuilder<? extends TemplateParameterDeclaration>> templateParameters() { return List.copyOf(templateParameters); }
        public Builder setTemplateParameters(List<? extends NodeBuilder<? extends TemplateParameterDeclaration>> values) { syntaxEdited = true; templateParameters = new ArrayList<>(values); return this; }
        private String name;
        private boolean implicit;
        public boolean implicit() { return implicit; }
        public Builder setImplicit(boolean value) { implicit = value; return this; }
        private String keyword = "class";
        private String memberAccess = "public";
        private List<String> modifiers = new ArrayList<>();
        private List<NodeBuilder<? extends Declaration>> members = new ArrayList<>();

        private Builder() {
            super(ClassDeclaration.class);
        }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public List<String> modifiers() {
            return List.copyOf(modifiers);
        }

        public List<NodeBuilder<? extends Declaration>> members() {
            return List.copyOf(members);
        }

        public Builder setKeyword(String value) { if (!List.of("class", "struct", "union").contains(value)) throw new IllegalArgumentException("Invalid class keyword"); keyword = value; return this; }
        public Builder setMemberAccess(String value) { if (!List.of("public", "private", "protected").contains(value)) throw new IllegalArgumentException("Invalid access"); memberAccess = value; return this; }

        public Builder setName(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Builder setModifiers(List<String> modifiers) {
            this.modifiers = new ArrayList<>(List.copyOf(modifiers));
            return this;
        }

        public Builder addModifier(String value) {
            modifiers.add(Objects.requireNonNull(value));
            return this;
        }

        public Builder setMembers(List<? extends NodeBuilder<? extends Declaration>> members) {
            this.members = new ArrayList<>(List.copyOf(members));
            return this;
        }

        public Builder addMember(NodeBuilder<? extends Declaration> value) {
            members.add(Objects.requireNonNull(value));
            return this;
        }

        @Override
        ClassDeclaration newNode() {
            return new ClassDeclaration();
        }

        @Override
        void fillNode(ClassDeclaration node, BuildContext context) {
            node.templateParameters = context.copyAll(templateParameters);
            node.bases = context.copyAll(bases);
            node.name = requiredText(name, "ClassDeclaration.name");
            node.implicit = implicit;
            node.keyword = keyword; node.memberAccess = memberAccess;
            node.modifiers = List.copyOf(modifiers);
            node.members = context.copyAll(members);
        }
    }
}
