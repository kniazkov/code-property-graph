/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** An assignment in statement position. */
public final class AssignmentStatement extends Statement {
    private Expression target;
    private Expression value;

    private AssignmentStatement() {
    }

    public Expression target() {
        return target;
    }

    public Expression value() {
        return value;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.add(target);
        children.add(value);
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
        builder.target = context.copy(target);
        builder.value = context.copy(value);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<AssignmentStatement> {
        private NodeBuilder<? extends Expression> target;
        private NodeBuilder<? extends Expression> value;

        private Builder() {
            super(AssignmentStatement.class);
        }

        public Optional<NodeBuilder<? extends Expression>> target() {
            return Optional.ofNullable(target);
        }

        public Optional<NodeBuilder<? extends Expression>> value() {
            return Optional.ofNullable(value);
        }

        public Builder setTarget(NodeBuilder<? extends Expression> target) {
            this.target = Objects.requireNonNull(target);
            return this;
        }

        public Builder setValue(NodeBuilder<? extends Expression> value) {
            this.value = Objects.requireNonNull(value);
            return this;
        }

        @Override
        AssignmentStatement newNode() {
            return new AssignmentStatement();
        }

        @Override
        void fillNode(AssignmentStatement node, BuildContext context) {
            node.target = context.copy(target);
            node.value = context.copy(value);
            if (!(node.target instanceof NameExpression)
                    && !(node.target instanceof MemberExpression)) {
                throw new IllegalStateException("Unsupported assignment target");
            }
        }
    }
}
