/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A variable with optional written type and initializer. */
public final class VariableDeclaration extends Declaration {
    private String name;
    private List<String> modifiers = List.of();
    private TypeNode type;
    private Expression initializer;
    private Expression bitWidth;
    public Optional<Expression> bitWidth() { return Optional.ofNullable(bitWidth); }

    private VariableDeclaration() {
    }

    public List<String> modifiers() { return modifiers; }

    public String name() {
        return name;
    }

    public Optional<TypeNode> type() {
        return Optional.ofNullable(type);
    }

    public Optional<Expression> initializer() {
        return Optional.ofNullable(initializer);
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        if (type != null) {
            children.add(type);
        }
        if (bitWidth != null) children.add(bitWidth);
        if (initializer != null) {
            children.add(initializer);
        }
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
        builder.bitWidth = bitWidth == null ? null : context.edge(bitWidth, Expression.class);
        builder.modifiers = new ArrayList<>(modifiers);
        builder.type = type == null ? null : context.edge(type, TypeNode.class);
        builder.initializer = initializer == null ? null : context.edge(initializer, Expression.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<VariableDeclaration> {
        private String name;
        private List<String> modifiers = new ArrayList<>();
        private NodeBuilder<? extends TypeNode> type;
        private NodeBuilder<? extends Expression> initializer;
        private NodeBuilder<? extends Expression> bitWidth;
        public Optional<NodeBuilder<? extends Expression>> bitWidth() { return Optional.ofNullable(bitWidth); }
        public Builder setBitWidth(NodeBuilder<? extends Expression> value) { bitWidth = Objects.requireNonNull(value); return this; }
        public Builder clearBitWidth() { bitWidth = null; return this; }

        private Builder() {
            super(VariableDeclaration.class);
        }

        public List<String> modifiers() { return List.copyOf(modifiers); }
        public Builder setModifiers(List<String> values) { modifiers = new ArrayList<>(List.copyOf(values)); return this; }
        public Builder addModifier(String value) { modifiers.add(Objects.requireNonNull(value)); return this; }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public Optional<NodeBuilder<? extends TypeNode>> type() {
            return Optional.ofNullable(type);
        }

        public Optional<NodeBuilder<? extends Expression>> initializer() {
            return Optional.ofNullable(initializer);
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

        public Builder setInitializer(NodeBuilder<? extends Expression> initializer) {
            this.initializer = Objects.requireNonNull(initializer);
            return this;
        }

        public Builder clearInitializer() {
            this.initializer = null;
            return this;
        }

        @Override
        VariableDeclaration newNode() {
            return new VariableDeclaration();
        }

        @Override
        void fillNode(VariableDeclaration node, BuildContext context) {
            node.name = bitWidth == null ? requiredText(name, "VariableDeclaration.name") : Objects.requireNonNull(name);
            node.bitWidth = bitWidth == null ? null : context.copy(bitWidth);
            node.modifiers = List.copyOf(modifiers);
            node.type = type == null ? null : context.copy(type);
            node.initializer = initializer == null ? null : context.copy(initializer);
        }
    }
}
