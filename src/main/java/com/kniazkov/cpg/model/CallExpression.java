/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A call with arguments and an optional resolved target. */
public final class CallExpression extends Expression {
    private Expression callee;
    private List<Expression> arguments = List.of();
    private CallableDeclaration target;
    private List<DefaultArgument> defaultArguments = List.of();

    /** A non-owning link to the visible declaration supplying an omitted argument. */
    public record DefaultArgument(int parameterIndex, CallableDeclaration declaration, ParameterDeclaration parameter) {
        public DefaultArgument {
            if (parameterIndex < 0) throw new IllegalArgumentException("Negative parameter index");
            Objects.requireNonNull(declaration); Objects.requireNonNull(parameter);
        }
        public Expression expression() { return parameter.defaultValue().orElseThrow(); }
    }

    /** Mutable graph counterparts are resolved together in the enclosing build session. */
    public record DefaultArgumentBuilder(int parameterIndex, NodeBuilder<? extends CallableDeclaration> declaration,
            NodeBuilder<? extends ParameterDeclaration> parameter) {
        public DefaultArgumentBuilder {
            if (parameterIndex < 0) throw new IllegalArgumentException("Negative parameter index");
            Objects.requireNonNull(declaration); Objects.requireNonNull(parameter);
        }
    }

    public List<DefaultArgument> defaultArguments() { return defaultArguments; }

    /** Drop stale derived links after a declaration's default or parameter list was edited. */
    void validateDefaultArguments() {
        int expected = arguments.size();
        for (var value : defaultArguments) {
            if (value.parameterIndex() != expected++ || value.parameter().defaultValue().isEmpty()
                    || value.parameterIndex() >= value.declaration().parameters().size()
                    || value.declaration().parameters().get(value.parameterIndex()) != value.parameter()) {
                defaultArguments = List.of();
                expressionInfo = null;
                return;
            }
        }
    }
    public enum Resolution { UNRESOLVED, RESOLVED, AMBIGUOUS, ODR_CONFLICT }
    private List<CallableDeclaration> candidates = List.of();
    private Resolution resolution = Resolution.UNRESOLVED;
    public List<CallableDeclaration> candidates() { return candidates; }
    public Resolution resolution() { return resolution; }

    private CallExpression() {
    }

    public Expression callee() {
        return callee;
    }

    public List<Expression> arguments() {
        return arguments;
    }

    public Optional<CallableDeclaration> target() {
        return Optional.ofNullable(target);
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(callee);
        children.addAll(arguments);
        return List.copyOf(children);
    }

    @Override
    public List<Node> references() {
        List<Node> result = new ArrayList<>(candidates);
        for (var argument : defaultArguments) {
            for (Node reference : List.of(argument.declaration(), argument.parameter(), argument.expression()))
                if (!result.contains(reference)) result.add(reference);
        }
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
        builder.callee = context.edge(callee, Expression.class);
        builder.arguments = context.copyAll(arguments, Expression.class);
        builder.target = target == null ? null : context.edge(target, CallableDeclaration.class);
        builder.candidates = context.copyAll(candidates, CallableDeclaration.class);
        builder.resolution = resolution;
        for (var argument : defaultArguments) builder.defaultArguments.add(new DefaultArgumentBuilder(argument.parameterIndex(),
                context.edge(argument.declaration(), CallableDeclaration.class), context.edge(argument.parameter(), ParameterDeclaration.class)));
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<CallExpression> {
        private NodeBuilder<? extends Expression> callee;
        private List<NodeBuilder<? extends Expression>> arguments = new ArrayList<>();
        private NodeBuilder<? extends CallableDeclaration> target;
        private List<NodeBuilder<? extends CallableDeclaration>> candidates = new ArrayList<>();
        private Resolution resolution = Resolution.UNRESOLVED;
        private List<DefaultArgumentBuilder> defaultArguments = new ArrayList<>();
        public List<DefaultArgumentBuilder> defaultArguments() { return List.copyOf(defaultArguments); }
        public Builder setDefaultArguments(List<DefaultArgumentBuilder> values) {
            clearExpressionInfo(); defaultArguments = new ArrayList<>(List.copyOf(values)); return this;
        }
        public Builder clearDefaultArguments() { clearExpressionInfo(); defaultArguments.clear(); return this; }
        public Builder setCandidates(List<? extends NodeBuilder<? extends CallableDeclaration>> values, Resolution status) { clearExpressionInfo(); clearDefaultArguments();
            candidates = new ArrayList<>(values);
            resolution = Objects.requireNonNull(status);
            if (status == Resolution.RESOLVED && candidates.size() != 1
                    || status == Resolution.UNRESOLVED && !candidates.isEmpty()
                    || (status == Resolution.AMBIGUOUS || status == Resolution.ODR_CONFLICT) && candidates.size() < 2)
                throw new IllegalArgumentException("Candidate count does not match resolution status");
            target = status == Resolution.RESOLVED ? candidates.getFirst() : null;
            return this;
        }

        private Builder() {
            super(CallExpression.class);
        }

        public Optional<NodeBuilder<? extends Expression>> callee() {
            return Optional.ofNullable(callee);
        }

        public List<NodeBuilder<? extends Expression>> arguments() {
            return List.copyOf(arguments);
        }

        public Optional<NodeBuilder<? extends CallableDeclaration>> target() {
            return Optional.ofNullable(target);
        }

        public Builder setCallee(NodeBuilder<? extends Expression> callee) { clearExpressionInfo(); clearDefaultArguments();
            this.callee = Objects.requireNonNull(callee);
            return this;
        }

        public Builder setArguments(List<? extends NodeBuilder<? extends Expression>> arguments) { clearExpressionInfo(); clearDefaultArguments();
            this.arguments = new ArrayList<>(List.copyOf(arguments));
            return this;
        }

        public Builder addArgument(NodeBuilder<? extends Expression> value) { clearExpressionInfo(); clearDefaultArguments();
            arguments.add(Objects.requireNonNull(value));
            return this;
        }

        public Builder setTarget(NodeBuilder<? extends CallableDeclaration> target) { clearExpressionInfo(); clearDefaultArguments();
            this.target = Objects.requireNonNull(target);
            candidates = new ArrayList<>(List.of(target));
            resolution = Resolution.RESOLVED;
            return this;
        }

        public Builder clearTarget() { clearExpressionInfo(); clearDefaultArguments();
            this.target = null;
            candidates.clear();
            resolution = Resolution.UNRESOLVED;
            return this;
        }

        @Override
        CallExpression newNode() {
            return new CallExpression();
        }

        @Override
        void fillNode(CallExpression node, BuildContext context) {
            node.callee = context.copy(callee);
            node.arguments = context.copyAll(arguments);
            node.target = target == null ? null : context.copy(target);
            node.candidates = context.copyAll(candidates);
            node.resolution = resolution;
            node.defaultArguments = defaultArguments.stream().map(value -> new DefaultArgument(value.parameterIndex(),
                    context.copy(value.declaration()), context.copy(value.parameter()))).toList();
        }
    }
}
