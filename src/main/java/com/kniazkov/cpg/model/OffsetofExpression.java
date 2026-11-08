/* Operand-bearing, unevaluated C++ offsetof compiler intrinsic. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Written type and designator syntax. No numeric result is cached on the graph. */
public final class OffsetofExpression extends Expression {
    private TypeNode type;
    private List<Step> steps = List.of();
    private OffsetofExpression() {}
    public TypeNode type() { return type; }
    public List<Step> steps() { return steps; }
    @Override public List<Node> children() {
        var result = new ArrayList<Node>(); result.add(type); result.addAll(steps); return List.copyOf(result);
    }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.type = context.edge(type, TypeNode.class); builder.steps = context.copyAll(steps, Step.class); return builder;
    }
    public static final class Builder extends NodeBuilder<OffsetofExpression> {
        private NodeBuilder<? extends TypeNode> type;
        private List<NodeBuilder<? extends Step>> steps = new ArrayList<>();
        private Builder() { super(OffsetofExpression.class); }
        public Optional<NodeBuilder<? extends TypeNode>> type() { return Optional.ofNullable(type); }
        public List<NodeBuilder<? extends Step>> steps() { return List.copyOf(steps); }
        public Builder setType(NodeBuilder<? extends TypeNode> value) { clearExpressionInfo(); type = Objects.requireNonNull(value); return this; }
        public Builder setSteps(List<? extends NodeBuilder<? extends Step>> values) { clearExpressionInfo(); steps = new ArrayList<>(List.copyOf(values)); return this; }
        public Builder addStep(NodeBuilder<? extends Step> value) { clearExpressionInfo(); steps.add(Objects.requireNonNull(value)); return this; }
        public Builder addMember(String name) { return addStep(Member.create().setName(name)); }
        public Builder addIndex(NodeBuilder<? extends Expression> expression) { return addStep(Index.create().setExpression(expression)); }
        @Override OffsetofExpression newNode() { return new OffsetofExpression(); }
        @Override void fillNode(OffsetofExpression node, BuildContext context) {
            node.type = context.copy(Objects.requireNonNull(type, "offsetof type")); node.steps = context.copyAll(steps);
            if (node.steps.isEmpty() || !(node.steps.getFirst() instanceof Member))
                throw new IllegalStateException("offsetof designator must begin with a named member");
        }
    }
    /** Designator components are owned syntax, never runtime member access expressions. */
    public abstract static sealed class Step extends Node permits Member, Index {
        private Step() {}
    }
    public static final class Member extends Step {
        private String name;
        private VariableDeclaration target;
        private Member() {}
        public String name() { return name; }
        public Optional<VariableDeclaration> target() { return Optional.ofNullable(target); }
        void invalidateTemplateTargets(Set<Node> discarded) { if (discarded.contains(target)) target = null; }
        @Override public List<Node> children() { return List.of(); }
        @Override public List<Node> references() { return target == null ? List.of() : List.of(target); }
        @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
        public static Builder create() { return new Builder(); }
        @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
        @Override Builder copyBuilder(EditContext context) {
            var builder = new Builder(); context.register(this, builder); builder.name = name;
            builder.target = target == null ? null : context.edge(target, VariableDeclaration.class); return builder;
        }
        public static final class Builder extends NodeBuilder<Member> {
            private String name;
            private NodeBuilder<? extends VariableDeclaration> target;
            private Builder() { super(Member.class); }
            public Optional<String> name() { return Optional.ofNullable(name); }
            public Builder setName(String value) { clearExpressionInfo(); name = Objects.requireNonNull(value); target = null; return this; }
            public Builder setTarget(NodeBuilder<? extends VariableDeclaration> value) { target = Objects.requireNonNull(value); return this; }
            public Builder clearTarget() { target = null; return this; }
            @Override Member newNode() { return new Member(); }
            @Override void fillNode(Member node, BuildContext context) {
                node.name = requiredText(name, "offsetof member name");
                node.target = target == null ? null : context.copy(target);
            }
        }
    }
    public static final class Index extends Step {
        private Expression expression;
        private Index() {}
        public Expression expression() { return expression; }
        @Override public List<Node> children() { return List.of(expression); }
        @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
        public static Builder create() { return new Builder(); }
        @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
        @Override Builder copyBuilder(EditContext context) {
            var builder = new Builder(); context.register(this, builder); builder.expression = context.edge(expression, Expression.class); return builder;
        }
        public static final class Builder extends NodeBuilder<Index> {
            private NodeBuilder<? extends Expression> expression;
            private Builder() { super(Index.class); }
            public Optional<NodeBuilder<? extends Expression>> expression() { return Optional.ofNullable(expression); }
            public Builder setExpression(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); expression = Objects.requireNonNull(value); return this; }
            @Override Index newNode() { return new Index(); }
            @Override void fillNode(Index node, BuildContext context) {
                node.expression = context.copy(Objects.requireNonNull(expression, "offsetof index expression"));
            }
        }
    }
}
