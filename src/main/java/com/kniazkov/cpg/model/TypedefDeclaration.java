/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A typedef declaration. Its written type remains owned, and uses refer here. */
public final class TypedefDeclaration extends Declaration {
    private String name;
    private TypeNode type;

    private TypedefDeclaration() {
    }

    public String name() {
        return name;
    }

    public TypeNode type() { return type; }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        if (type != null) {
            children.add(type);
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
        builder.type = type == null ? null : context.edge(type, TypeNode.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<TypedefDeclaration> {
        private String name;
        private NodeBuilder<? extends TypeNode> type;

        private Builder() {
            super(TypedefDeclaration.class);
        }

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
        TypedefDeclaration newNode() {
            return new TypedefDeclaration();
        }

        @Override
        void fillNode(TypedefDeclaration node, BuildContext context) {
            node.name = requiredText(name, "TypedefDeclaration.name");
            node.type = context.copy(type);
        }
    }
}
