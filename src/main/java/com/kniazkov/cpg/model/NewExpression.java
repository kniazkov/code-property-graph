/* C++03 allocation syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Allocated type, optional placement arguments, and optional parenthesized initializer. */
public final class NewExpression extends Expression {
    private TypeNode type;
    private List<Expression> placementArguments=List.of(), initializerArguments=List.of();
    private boolean initializer, global, parenthesizedType;
    private NewExpression() {}
    public TypeNode type() { return type; }
    public List<Expression> placementArguments() { return placementArguments; }
    public List<Expression> initializerArguments() { return initializerArguments; }
    public boolean hasInitializer() { return initializer; }
    public boolean isGlobal() { return global; }
    public boolean isParenthesizedType() { return parenthesizedType; }
    @Override public List<Node> children() { var nodes=new ArrayList<Node>(); nodes.addAll(placementArguments); nodes.add(type); nodes.addAll(initializerArguments); return List.copyOf(nodes); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) { var b=new Builder(); context.register(this,b); b.type=context.edge(type,TypeNode.class); b.placementArguments=context.copyAll(placementArguments,Expression.class); b.initializerArguments=context.copyAll(initializerArguments,Expression.class); b.initializer=initializer; b.global=global; b.parenthesizedType=parenthesizedType; return b; }
    public static final class Builder extends NodeBuilder<NewExpression> {
        private NodeBuilder<? extends TypeNode> type;
        private List<NodeBuilder<? extends Expression>> placementArguments=new ArrayList<>(), initializerArguments=new ArrayList<>();
        private boolean initializer, global, parenthesizedType;
        private Builder() { super(NewExpression.class); }
        public Optional<NodeBuilder<? extends TypeNode>> type() { return Optional.ofNullable(type); }
        public List<NodeBuilder<? extends Expression>> placementArguments() { return List.copyOf(placementArguments); }
        public List<NodeBuilder<? extends Expression>> initializerArguments() { return List.copyOf(initializerArguments); }
        public boolean hasInitializer() { return initializer; }
        public boolean isGlobal() { return global; }
        public boolean isParenthesizedType() { return parenthesizedType; }
        public Builder setType(NodeBuilder<? extends TypeNode> value) { clearExpressionInfo(); type=Objects.requireNonNull(value); return this; }
        public Builder setGlobal(boolean value) { clearExpressionInfo(); global=value; return this; }
        public Builder setParenthesizedType(boolean value) { clearExpressionInfo(); parenthesizedType=value; return this; }
        public Builder setHasInitializer(boolean value) { clearExpressionInfo(); initializer=value; if (!value) initializerArguments.clear(); return this; }
        public Builder addPlacementArgument(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); placementArguments.add(Objects.requireNonNull(value)); return this; }
        public Builder addInitializerArgument(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); initializer=true; initializerArguments.add(Objects.requireNonNull(value)); return this; }
        public Builder setPlacementArguments(List<? extends NodeBuilder<? extends Expression>> values) { clearExpressionInfo(); placementArguments=new ArrayList<>(List.copyOf(values)); return this; }
        public Builder setInitializerArguments(List<? extends NodeBuilder<? extends Expression>> values) { clearExpressionInfo(); initializer=true; initializerArguments=new ArrayList<>(List.copyOf(values)); return this; }
        @Override NewExpression newNode() { return new NewExpression(); }
        @Override void fillNode(NewExpression node, BuildContext context) { node.type=context.copy(Objects.requireNonNull(type)); node.placementArguments=context.copyAll(placementArguments); node.initializerArguments=context.copyAll(initializerArguments); node.initializer=initializer; node.global=global; node.parenthesizedType=parenthesizedType; }
    }
}
