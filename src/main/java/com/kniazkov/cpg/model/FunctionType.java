/* C++03 type syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** A function type, including abstract/named parameters, ellipsis, and member cv. */
public final class FunctionType extends TypeNode {
    private boolean hasExceptionSpecification;
    private List<TypeNode> exceptionTypes = List.of();
    public boolean hasExceptionSpecification() { return hasExceptionSpecification; }
    public List<TypeNode> exceptionTypes() { return exceptionTypes; }
    private TypeNode returnType;
    private List<ParameterDeclaration> parameters;
    private boolean variadic, isConst, isVolatile;
    private FunctionType() {}
    public TypeNode returnType() { return returnType; }
    public List<ParameterDeclaration> parameters() { return parameters; }
    public boolean variadic() { return variadic; }
    public boolean isConst() { return isConst; }
    public boolean isVolatile() { return isVolatile; }
    @Override public List<Node> children() { var result = new ArrayList<Node>(); result.add(returnType); result.addAll(parameters); result.addAll(exceptionTypes); return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.hasExceptionSpecification = hasExceptionSpecification;
        builder.exceptionTypes = context.copyAll(exceptionTypes, TypeNode.class);
        builder.returnType = context.edge(returnType, TypeNode.class);
        builder.parameters = context.copyAll(parameters, ParameterDeclaration.class);
        builder.variadic = variadic; builder.isConst = isConst; builder.isVolatile = isVolatile;
        return builder;
    }
    public static final class Builder extends NodeBuilder<FunctionType> {
        private boolean hasExceptionSpecification;
        private List<NodeBuilder<? extends TypeNode>> exceptionTypes = new ArrayList<>();
        public boolean hasExceptionSpecification() { return hasExceptionSpecification; }
        public List<NodeBuilder<? extends TypeNode>> exceptionTypes() { return List.copyOf(exceptionTypes); }
        public Builder setHasExceptionSpecification(boolean value) { syntaxEdited = true; hasExceptionSpecification = value; return this; }
        public Builder setExceptionTypes(List<? extends NodeBuilder<? extends TypeNode>> value) { syntaxEdited = true; exceptionTypes = new ArrayList<>(List.copyOf(value)); return this; }
        private NodeBuilder<? extends TypeNode> returnType;
        private List<NodeBuilder<? extends ParameterDeclaration>> parameters = new ArrayList<>();
        private boolean variadic, isConst, isVolatile;
        private Builder() { super(FunctionType.class); }
        public Optional<NodeBuilder<? extends TypeNode>> returnType() { return Optional.ofNullable(returnType); }
        public List<NodeBuilder<? extends ParameterDeclaration>> parameters() { return List.copyOf(parameters); }
        public boolean variadic() { return variadic; }
        public boolean isConst() { return isConst; }
        public boolean isVolatile() { return isVolatile; }
        public Builder setReturnType(NodeBuilder<? extends TypeNode> value) { returnType = Objects.requireNonNull(value); return this; }
        public Builder setParameters(List<? extends NodeBuilder<? extends ParameterDeclaration>> value) { parameters = new ArrayList<>(List.copyOf(value)); return this; }
        public Builder addParameter(NodeBuilder<? extends ParameterDeclaration> value) { parameters.add(Objects.requireNonNull(value)); return this; }
        public Builder setVariadic(boolean value) { variadic = value; return this; }
        public Builder setConst(boolean value) { isConst = value; return this; }
        public Builder setVolatile(boolean value) { isVolatile = value; return this; }
        @Override FunctionType newNode() { return new FunctionType(); }
        @Override void fillNode(FunctionType node, BuildContext context) {
            node.hasExceptionSpecification = hasExceptionSpecification;
            node.exceptionTypes = context.copyAll(exceptionTypes);
            node.returnType = context.copy(returnType); node.parameters = context.copyAll(parameters);
            node.variadic = variadic; node.isConst = isConst; node.isVolatile = isVolatile;
        }
    }
}
