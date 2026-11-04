/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A named class with ordered declarations. */
public final class ClassDeclaration extends Declaration {
    private String name;
    private List<String> modifiers = List.of();
    private List<Declaration> members = List.of();

    private ClassDeclaration() {
    }

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
        builder.name = name;
        builder.modifiers = new ArrayList<>(modifiers);
        builder.members = context.copyAll(members, Declaration.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<ClassDeclaration> {
        private String name;
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
            node.name = requiredText(name, "ClassDeclaration.name");
            node.modifiers = List.copyOf(modifiers);
            node.members = context.copyAll(members);
        }
    }
}
