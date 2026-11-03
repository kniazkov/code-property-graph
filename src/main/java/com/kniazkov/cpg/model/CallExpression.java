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
    private MethodDeclaration target;

    private CallExpression() {
    }

    public Expression callee() {
        return callee;
    }

    public List<Expression> arguments() {
        return arguments;
    }

    public Optional<MethodDeclaration> target() {
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
        return target == null ? List.of() : List.of(target);
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
        builder.callee = context.copy(callee);
        builder.arguments = context.copyAll(arguments);
        builder.target = target == null ? null : context.copy(target);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<CallExpression> {
        private NodeBuilder<? extends Expression> callee;
        private List<NodeBuilder<? extends Expression>> arguments = new ArrayList<>();
        private NodeBuilder<? extends MethodDeclaration> target;

        private Builder() {
            super(CallExpression.class);
        }

        public Optional<NodeBuilder<? extends Expression>> callee() {
            return Optional.ofNullable(callee);
        }

        public List<NodeBuilder<? extends Expression>> arguments() {
            return List.copyOf(arguments);
        }

        public Optional<NodeBuilder<? extends MethodDeclaration>> target() {
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

        public Builder setTarget(NodeBuilder<? extends MethodDeclaration> target) {
            this.target = Objects.requireNonNull(target);
            return this;
        }

        public Builder clearTarget() {
            this.target = null;
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
        }
    }
}
