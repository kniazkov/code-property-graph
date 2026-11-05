/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A written type name; no semantic type inference is implied. */
public final class NamedType extends TypeNode {
    void invalidateTemplateTargets(java.util.Set<Node> discarded) {
        if(discarded.contains(declaration))declaration=null;
        if(discarded.contains(alias))alias=null;
        resolutionSteps=resolutionSteps.stream().filter(step->!discarded.contains(step.via())&&!discarded.contains(step.target())).toList();
    }

    private List<TemplateArgument> templateArguments = List.of();
    private boolean hasTemplateArguments, templateKeyword;
    public List<TemplateArgument> templateArguments() { return templateArguments; }
    public boolean hasTemplateArguments() { return hasTemplateArguments; }
    public boolean templateKeyword() { return templateKeyword; }
    private NamedType qualifier;
    private boolean typenameKeyword, dependent;
    public Optional<NamedType> qualifier() { return Optional.ofNullable(qualifier); }
    public boolean typenameKeyword() { return typenameKeyword; }
    public boolean dependent() { return dependent; }
    private String name;
    private String elaboratedKeyword;
    private TypedefDeclaration alias;
    private Declaration declaration;
    private boolean completeAtUse = true;
    private List<TypeResolutionStep> resolutionSteps = List.of();

    private NamedType() {
    }

    public Optional<String> elaboratedKeyword() { return Optional.ofNullable(elaboratedKeyword); }

    public String name() {
        return name;
    }

    public Optional<FundamentalKind> fundamentalKind() { return alias == null ? FundamentalKind.fromSpelling(name) : Optional.empty(); }

    /** Immediate written alias link; following links preserves the complete typedef chain. */
    public Optional<TypedefDeclaration> alias() { return Optional.ofNullable(alias); }

    public Optional<Declaration> declaration() { return Optional.ofNullable(declaration); }
    public boolean completeAtUse() { return completeAtUse; }
    public List<TypeResolutionStep> resolutionSteps() { return resolutionSteps; }
    @Override public List<Node> references() {
        var result = new java.util.ArrayList<Node>();
        if (alias != null) result.add(alias);
        if (declaration != null && declaration != alias) result.add(declaration);
        for (var step : resolutionSteps) {
            if (!result.contains(step.via())) result.add(step.via());
            if (!result.contains(step.target())) result.add(step.target());
        }
        return List.copyOf(result);
    }

    @Override
    public List<Node> children() {
        var children = new java.util.ArrayList<Node>(templateArguments); if (qualifier != null) children.add(0, qualifier); return List.copyOf(children);
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
        builder.templateArguments = context.copyAll(templateArguments, TemplateArgument.class); builder.hasTemplateArguments = hasTemplateArguments; builder.templateKeyword = templateKeyword;
        builder.qualifier = qualifier == null ? null : context.edge(qualifier, NamedType.class); builder.typenameKeyword = typenameKeyword; builder.dependent = dependent;
        builder.name = name;
        builder.elaboratedKeyword = elaboratedKeyword;
        builder.alias = alias == null ? null : context.edge(alias, TypedefDeclaration.class);
        builder.declaration = declaration == null ? null : context.edge(declaration, Declaration.class);
        builder.completeAtUse = completeAtUse;
        for (var step : resolutionSteps) builder.addResolutionStep(step.kind(), context.edge(step.via(), Declaration.class), context.edge(step.target(), Declaration.class));
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<NamedType> {
        private java.util.List<NodeBuilder<? extends TemplateArgument>> templateArguments = new java.util.ArrayList<>();
        private boolean hasTemplateArguments, templateKeyword;
        public List<NodeBuilder<? extends TemplateArgument>> templateArguments() { return List.copyOf(templateArguments); }
        public boolean hasTemplateArguments() { return hasTemplateArguments; }
        public boolean templateKeyword() { return templateKeyword; }
        public Builder setTemplateArguments(List<? extends NodeBuilder<? extends TemplateArgument>> values) { syntaxEdited = true; templateArguments = new java.util.ArrayList<>(values); hasTemplateArguments = true; return this; }
        public Builder setTemplateKeyword(boolean value) { templateKeyword = value; return this; }
        private NodeBuilder<? extends NamedType> qualifier;
        private boolean typenameKeyword, dependent;
        public Optional<NodeBuilder<? extends NamedType>> qualifier() { return Optional.ofNullable(qualifier); }
        public boolean typenameKeyword() { return typenameKeyword; }
        public boolean dependent() { return dependent; }
        public Builder setQualifier(NodeBuilder<? extends NamedType> value) { qualifier = Objects.requireNonNull(value); return this; }
        public Builder setTypenameKeyword(boolean value) { typenameKeyword = value; return this; }
        public Builder setDependent(boolean value) { dependent = value; return this; }
        private String name;
        private String elaboratedKeyword;
        public Optional<String> elaboratedKeyword() { return Optional.ofNullable(elaboratedKeyword); }
        public Builder setElaboratedKeyword(String value) {
            if (!List.of("class", "struct", "union", "enum").contains(value)) throw new IllegalArgumentException("Invalid elaborated type keyword");
            elaboratedKeyword = value; return this;
        }
        public Builder clearElaboratedKeyword() { elaboratedKeyword = null; return this; }
        private NodeBuilder<? extends TypedefDeclaration> alias;
        private NodeBuilder<? extends Declaration> declaration;
        private boolean completeAtUse = true;
        private record Step(String kind, NodeBuilder<? extends Declaration> via, NodeBuilder<? extends Declaration> target) {}
        private final java.util.ArrayList<Step> resolutionSteps = new java.util.ArrayList<>();
        public Optional<NodeBuilder<? extends Declaration>> declaration() { return Optional.ofNullable(declaration); }
        public Builder setDeclaration(NodeBuilder<? extends Declaration> value) { declaration = Objects.requireNonNull(value); return this; }
        public Builder clearDeclaration() { declaration = null; return this; }
        public boolean completeAtUse() { return completeAtUse; }
        public Builder setCompleteAtUse(boolean value) { completeAtUse = value; return this; }
        public Builder addResolutionStep(String kind, NodeBuilder<? extends Declaration> via, NodeBuilder<? extends Declaration> target) {
            resolutionSteps.add(new Step(Objects.requireNonNull(kind), Objects.requireNonNull(via), Objects.requireNonNull(target))); return this;
        }
        public Builder clearResolutionSteps() { resolutionSteps.clear(); return this; }

        private Builder() {
            super(NamedType.class);
        }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public Builder setName(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Optional<NodeBuilder<? extends TypedefDeclaration>> alias() { return Optional.ofNullable(alias); }
        public Builder setAlias(NodeBuilder<? extends TypedefDeclaration> value) { alias = Objects.requireNonNull(value); return this; }
        public Builder clearAlias() { alias = null; return this; }

        @Override
        NamedType newNode() {
            return new NamedType();
        }

        @Override
        void fillNode(NamedType node, BuildContext context) {
            node.templateArguments = context.copyAll(templateArguments); node.hasTemplateArguments = hasTemplateArguments; node.templateKeyword = templateKeyword;
            node.qualifier = qualifier == null ? null : context.copy(qualifier); node.typenameKeyword = typenameKeyword; node.dependent = dependent;
            node.name = requiredText(name, "NamedType.name");
            node.elaboratedKeyword = elaboratedKeyword;
            node.alias = alias == null ? null : context.copy(alias);
            node.declaration = declaration == null ? null : context.copy(declaration);
            node.completeAtUse = completeAtUse;
            node.resolutionSteps = resolutionSteps.stream().map(step -> new TypeResolutionStep(step.kind(), context.copy(step.via()), context.copy(step.target()))).toList();
        }
    }
}
