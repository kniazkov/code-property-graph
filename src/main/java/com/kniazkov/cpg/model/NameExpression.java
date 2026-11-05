/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** A name with an optional resolved declaration reference. */
public final class NameExpression extends Expression {
    void invalidateTemplateTargets(java.util.Set<Node> discarded) { if(discarded.contains(target)) target=null; }

    private List<TemplateArgument> templateArguments = List.of();
    private boolean hasTemplateArguments, templateKeyword;
    public List<TemplateArgument> templateArguments() { return templateArguments; }
    public boolean hasTemplateArguments() { return hasTemplateArguments; }
    public boolean templateKeyword() { return templateKeyword; }
    private String name;
    private Declaration target;

    private NameExpression() {
    }

    public String name() {
        return name;
    }

    public Optional<Declaration> target() {
        return Optional.ofNullable(target);
    }

    @Override
    public List<Node> children() {
        return List.copyOf(templateArguments);
    }

    @Override
    public List<Node> references() {
        var result = new ArrayList<Node>(super.references());
        if (target != null && !result.contains(target)) result.add(target);
        return List.copyOf(result);
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
        builder.name = name;
        builder.target = target == null ? null : context.edge(target, Declaration.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<NameExpression> {
        private java.util.List<NodeBuilder<? extends TemplateArgument>> templateArguments = new java.util.ArrayList<>();
        private boolean hasTemplateArguments, templateKeyword;
        public List<NodeBuilder<? extends TemplateArgument>> templateArguments() { return List.copyOf(templateArguments); }
        public boolean hasTemplateArguments() { return hasTemplateArguments; }
        public boolean templateKeyword() { return templateKeyword; }
        public Builder setTemplateArguments(List<? extends NodeBuilder<? extends TemplateArgument>> values) { syntaxEdited = true; templateArguments = new java.util.ArrayList<>(values); hasTemplateArguments = true; return this; }
        public Builder setTemplateKeyword(boolean value) { templateKeyword = value; return this; }
        private String name;
        private NodeBuilder<? extends Declaration> target;

        private Builder() {
            super(NameExpression.class);
        }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public Optional<NodeBuilder<? extends Declaration>> target() {
            return Optional.ofNullable(target);
        }

        public Builder setName(String name) { clearExpressionInfo();
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Builder setTarget(NodeBuilder<? extends Declaration> target) { clearExpressionInfo();
            this.target = Objects.requireNonNull(target);
            return this;
        }

        public Builder clearTarget() { clearExpressionInfo();
            this.target = null;
            return this;
        }

        @Override
        NameExpression newNode() {
            return new NameExpression();
        }

        @Override
        void fillNode(NameExpression node, BuildContext context) {
            node.templateArguments = context.copyAll(templateArguments); node.hasTemplateArguments = hasTemplateArguments; node.templateKeyword = templateKeyword;
            node.name = requiredText(name, "NameExpression.name");
            node.target = target == null ? null : context.copy(target);
        }
    }
}
