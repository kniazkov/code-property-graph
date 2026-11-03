/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A method or free function with explicit placement. */
public final class MethodDeclaration extends Declaration {
    private String name;
    private CallablePlacement placement;
    private List<String> modifiers = List.of();
    private TypeNode returnType;
    private List<ParameterDeclaration> parameters = List.of();
    private BlockStatement body;

    private MethodDeclaration() {
    }

    public String name() {
        return name;
    }

    public CallablePlacement placement() {
        return placement;
    }

    public List<String> modifiers() {
        return modifiers;
    }

    public Optional<TypeNode> returnType() {
        return Optional.ofNullable(returnType);
    }

    public List<ParameterDeclaration> parameters() {
        return parameters;
    }

    public Optional<BlockStatement> body() {
        return Optional.ofNullable(body);
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        if (returnType != null) {
            children.add(returnType);
        }
        children.addAll(parameters);
        if (body != null) {
            children.add(body);
        }
        return List.copyOf(children);
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
        builder.placement = placement;
        builder.modifiers = new ArrayList<>(modifiers);
        builder.returnType = returnType == null ? null : context.copy(returnType);
        builder.parameters = context.copyAll(parameters);
        builder.body = body == null ? null : context.copy(body);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<MethodDeclaration> {
        private String name;
        private CallablePlacement placement = CallablePlacement.METHOD;
        private List<String> modifiers = new ArrayList<>();
        private NodeBuilder<? extends TypeNode> returnType;
        private List<NodeBuilder<? extends ParameterDeclaration>> parameters = new ArrayList<>();
        private NodeBuilder<? extends BlockStatement> body;

        private Builder() {
            super(MethodDeclaration.class);
        }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public CallablePlacement placement() {
            return placement;
        }

        public List<String> modifiers() {
            return List.copyOf(modifiers);
        }

        public Optional<NodeBuilder<? extends TypeNode>> returnType() {
            return Optional.ofNullable(returnType);
        }

        public List<NodeBuilder<? extends ParameterDeclaration>> parameters() {
            return List.copyOf(parameters);
        }

        public Optional<NodeBuilder<? extends BlockStatement>> body() {
            return Optional.ofNullable(body);
        }

        public Builder setName(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Builder setPlacement(CallablePlacement placement) {
            this.placement = Objects.requireNonNull(placement);
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

        public Builder setReturnType(NodeBuilder<? extends TypeNode> returnType) {
            this.returnType = Objects.requireNonNull(returnType);
            return this;
        }

        public Builder clearReturnType() {
            this.returnType = null;
            return this;
        }

        public Builder setParameters(List<? extends NodeBuilder<? extends ParameterDeclaration>> parameters) {
            this.parameters = new ArrayList<>(List.copyOf(parameters));
            return this;
        }

        public Builder addParameter(NodeBuilder<? extends ParameterDeclaration> value) {
            parameters.add(Objects.requireNonNull(value));
            return this;
        }

        public Builder setBody(NodeBuilder<? extends BlockStatement> body) {
            this.body = Objects.requireNonNull(body);
            return this;
        }

        public Builder clearBody() {
            this.body = null;
            return this;
        }

        @Override
        MethodDeclaration newNode() {
            return new MethodDeclaration();
        }

        @Override
        void fillNode(MethodDeclaration node, BuildContext context) {
            node.name = requiredText(name, "MethodDeclaration.name");
            node.placement = placement;
            node.modifiers = List.copyOf(modifiers);
            node.returnType = returnType == null ? null : context.copy(returnType);
            node.parameters = context.copyAll(parameters);
            node.body = body == null ? null : context.copy(body);
        }
    }
}
