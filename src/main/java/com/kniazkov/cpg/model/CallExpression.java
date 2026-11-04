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
        return List.copyOf(candidates);
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
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<CallExpression> {
        private NodeBuilder<? extends Expression> callee;
        private List<NodeBuilder<? extends Expression>> arguments = new ArrayList<>();
        private NodeBuilder<? extends CallableDeclaration> target;
        private List<NodeBuilder<? extends CallableDeclaration>> candidates = new ArrayList<>();
        private Resolution resolution = Resolution.UNRESOLVED;
        public Builder setCandidates(List<? extends NodeBuilder<? extends CallableDeclaration>> values, Resolution status) {
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

        public Builder setCallee(NodeBuilder<? extends Expression> callee) {
            this.callee = Objects.requireNonNull(callee);
            return this;
        }

        public Builder setArguments(List<? extends NodeBuilder<? extends Expression>> arguments) {
            this.arguments = new ArrayList<>(List.copyOf(arguments));
            return this;
        }

        public Builder addArgument(NodeBuilder<? extends Expression> value) {
            arguments.add(Objects.requireNonNull(value));
            return this;
        }

        public Builder setTarget(NodeBuilder<? extends CallableDeclaration> target) {
            this.target = Objects.requireNonNull(target);
            candidates = new ArrayList<>(List.of(target));
            resolution = Resolution.RESOLVED;
            return this;
        }

        public Builder clearTarget() {
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
        }
    }
}
