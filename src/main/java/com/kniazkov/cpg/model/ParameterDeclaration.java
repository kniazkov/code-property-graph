/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A named formal parameter with optional written type. */
public final class ParameterDeclaration extends Declaration {
    private String name;
    private List<String> modifiers = List.of();
    private TypeNode type;
    private Expression defaultValue;

    private ParameterDeclaration() {
    }

    public List<String> modifiers() { return modifiers; }

    public String name() {
        return name;
    }

    public Optional<TypeNode> type() {
        return Optional.ofNullable(type);
    }

    /** The written default belongs to this declaration, never to a call using it. */
    public Optional<Expression> defaultValue() { return Optional.ofNullable(defaultValue); }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        if (type != null) {
            children.add(type);
        }
        if (defaultValue != null) children.add(defaultValue);
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
        builder.name = name;
        builder.modifiers = new ArrayList<>(modifiers);
        builder.type = type == null ? null : context.edge(type, TypeNode.class);
        builder.defaultValue = defaultValue == null ? null : context.edge(defaultValue, Expression.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<ParameterDeclaration> {
        private String name;
        private List<String> modifiers = new ArrayList<>();
        private NodeBuilder<? extends TypeNode> type;
        private NodeBuilder<? extends Expression> defaultValue;

        public Optional<NodeBuilder<? extends Expression>> defaultValue() { return Optional.ofNullable(defaultValue); }
        public Builder setDefaultValue(NodeBuilder<? extends Expression> value) { defaultValue = Objects.requireNonNull(value); return this; }
        public Builder clearDefaultValue() { defaultValue = null; return this; }

        private Builder() {
            super(ParameterDeclaration.class);
        }

        public List<String> modifiers() { return List.copyOf(modifiers); }
        public Builder setModifiers(List<String> values) { modifiers = new ArrayList<>(List.copyOf(values)); return this; }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public Optional<NodeBuilder<? extends TypeNode>> type() {
            return Optional.ofNullable(type);
        }

        public Builder setName(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Builder setType(NodeBuilder<? extends TypeNode> type) {
            this.type = Objects.requireNonNull(type);
            return this;
        }

        public Builder clearType() {
            this.type = null;
            return this;
        }

        @Override
        ParameterDeclaration newNode() {
            return new ParameterDeclaration();
        }

        @Override
        void fillNode(ParameterDeclaration node, BuildContext context) {
            node.modifiers = List.copyOf(modifiers);
            node.name = Objects.requireNonNull(name, "ParameterDeclaration.name is required (empty for an abstract parameter)");
            node.type = type == null ? null : context.copy(type);
            node.defaultValue = defaultValue == null ? null : context.copy(defaultValue);
        }
    }
}
