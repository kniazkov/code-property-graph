/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A method or free function with explicit placement. */
public abstract class CallableDeclaration extends Declaration {
    private String name;
    private boolean variadic, isConst, isVolatile;
    private List<String> modifiers = List.of();
    private TypeNode returnType;
    private TypeNode writtenType;
    private List<ParameterDeclaration> parameters = List.of();
    private BlockStatement body;
    private Exit exit;

    CallableDeclaration() {
    }

    /** Returns the name of this syntax node. */
    public String name() {
        return name;
    }

    /** Original typedef-based function declaration spelling, when no parameter clause was written. */
    public Optional<TypeNode> writtenType() { return Optional.ofNullable(writtenType); }

    public boolean variadic() { return variadic; }
    public boolean isConst() { return isConst; }
    public boolean isVolatile() { return isVolatile; }

    /** Returns the modifiers of this syntax node. */
    public List<String> modifiers() {
        return modifiers;
    }

    /** Returns the returnType of this syntax node. */
    public Optional<TypeNode> returnType() {
        return Optional.ofNullable(returnType);
    }

    /** Returns the parameters of this syntax node. */
    public List<ParameterDeclaration> parameters() {
        return parameters;
    }

    /** Returns the body of this syntax node. */
    public Optional<BlockStatement> body() {
        return Optional.ofNullable(body);
    }

    /** Returns the terminal owned by this declaration, available before flow analysis. */
    public Exit exit() { return exit; }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        if (writtenType != null) children.add(writtenType);
        if (returnType != null) {
            children.add(returnType);
        }
        children.addAll(parameters);
        if (body != null) {
            children.add(body);
        }
        children.add(exit);
        return List.copyOf(children);
    }

    /** Creates the builder for the concrete operation during an edit session. */
    abstract Builder<?, ?> emptyBuilder();

    @Override
    final Builder<?, ?> copyBuilder(EditContext context) {
        Builder<?, ?> builder = emptyBuilder();
        context.register(this, builder);
        builder.name = name;
        builder.variadic = variadic; builder.isConst = isConst; builder.isVolatile = isVolatile;
        builder.modifiers = new ArrayList<>(modifiers);
        builder.returnType = returnType == null ? null : context.edge(returnType, TypeNode.class);
        builder.writtenType = writtenType == null ? null : context.edge(writtenType, TypeNode.class);
        builder.parameters = context.copyAll(parameters, ParameterDeclaration.class);
        builder.body = body == null ? null : context.edge(body, BlockStatement.class);
        builder.exit = context.edge(exit, Exit.class);
        return builder;
    }

    /** Holds the fields shared by every concrete variant and copies them in one build session.
     * @param <T> concrete node allocated by the leaf builder
     * @param <B> concrete builder returned by fluent setters */
    public abstract static class Builder<T extends CallableDeclaration, B extends Builder<T, B>> extends NodeBuilder<T> {
        private String name;
        private boolean variadic, isConst, isVolatile;
        private List<String> modifiers = new ArrayList<>();
        private NodeBuilder<? extends TypeNode> returnType;
        private NodeBuilder<? extends TypeNode> writtenType;
        public Optional<NodeBuilder<? extends TypeNode>> writtenType() { return Optional.ofNullable(writtenType); }
        public B setWrittenType(NodeBuilder<? extends TypeNode> value) { writtenType = Objects.requireNonNull(value); return self(); }
        public B clearWrittenType() { writtenType = null; return self(); }
        private List<NodeBuilder<? extends ParameterDeclaration>> parameters = new ArrayList<>();
        private NodeBuilder<? extends BlockStatement> body;
        private NodeBuilder<? extends Exit> exit = Exit.create();

        /** Binds this builder to the snapshot class used for identity lookups. */
        Builder(Class<T> type) {
            super(type);
        }

        public boolean variadic() { return variadic; }
        public boolean isConst() { return isConst; }
        public boolean isVolatile() { return isVolatile; }
        public B setVariadic(boolean value) { variadic = value; return self(); }
        public B setConst(boolean value) { isConst = value; return self(); }
        public B setVolatile(boolean value) { isVolatile = value; return self(); }

        /** Reads the current name from the builder. */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /** Reads the current modifiers from the builder. */
        public List<String> modifiers() {
            return List.copyOf(modifiers);
        }

        /** Reads the current returnType from the builder. */
        public Optional<NodeBuilder<? extends TypeNode>> returnType() {
            return Optional.ofNullable(returnType);
        }

        /** Reads the current parameters from the builder. */
        public List<NodeBuilder<? extends ParameterDeclaration>> parameters() {
            return List.copyOf(parameters);
        }

        /** Reads the current body from the builder. */
        public Optional<NodeBuilder<? extends BlockStatement>> body() {
            return Optional.ofNullable(body);
        }

        /** Replaces the name in the mutable state and returns the concrete builder. */
        public final B setName(String name) {
            this.name = Objects.requireNonNull(name);
            return self();
        }

        /** Replaces the modifiers in the mutable state and returns the concrete builder. */
        public final B setModifiers(List<String> modifiers) {
            this.modifiers = new ArrayList<>(List.copyOf(modifiers));
            return self();
        }

        /** Appends one modifier in source order and returns the concrete builder. */
        public final B addModifier(String value) {
            modifiers.add(Objects.requireNonNull(value));
            return self();
        }

        /** Replaces the returnType in the mutable state and returns the concrete builder. */
        public final B setReturnType(NodeBuilder<? extends TypeNode> returnType) {
            this.returnType = Objects.requireNonNull(returnType);
            return self();
        }

        /** Removes the optional returnType from the next snapshot. */
        public final B clearReturnType() {
            this.returnType = null;
            return self();
        }

        /** Replaces the parameters in the mutable state and returns the concrete builder. */
        public final B setParameters(List<? extends NodeBuilder<? extends ParameterDeclaration>> parameters) {
            this.parameters = new ArrayList<>(List.copyOf(parameters));
            return self();
        }

        /** Appends one parameter in source order and returns the concrete builder. */
        public final B addParameter(NodeBuilder<? extends ParameterDeclaration> value) {
            parameters.add(Objects.requireNonNull(value));
            return self();
        }

        /** Replaces the body in the mutable state and returns the concrete builder. */
        public final B setBody(NodeBuilder<? extends BlockStatement> body) {
            this.body = Objects.requireNonNull(body);
            return self();
        }

        /** Removes the optional body from the next snapshot. */
        public final B clearBody() {
            this.body = null;
            return self();
        }

        /** Returns the concrete builder so inherited setters can be chained. */
        abstract B self();

        @Override
        final void fillNode(T shell, BuildContext context) {
            CallableDeclaration node = shell;
            node.name = requiredText(name, "CallableDeclaration.name");
            node.variadic = variadic; node.isConst = isConst; node.isVolatile = isVolatile;
            node.modifiers = List.copyOf(modifiers);
            node.returnType = returnType == null ? null : context.copy(returnType);
            node.writtenType = writtenType == null ? null : context.copy(writtenType);
            node.parameters = context.copyAll(parameters);
            node.body = body == null ? null : context.copy(body);
            node.exit = context.copy(exit);
        }
    }
}
