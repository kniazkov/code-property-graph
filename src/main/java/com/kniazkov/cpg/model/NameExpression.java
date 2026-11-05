/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** A name with an optional resolved declaration reference. */
public final class NameExpression extends Expression {
    private String name;
    private Declaration target;

    private NameExpression() {
    }

    public String name() {
        return name;
    }

    public Optional<Declaration> target() {
        return Optional.ofNullable(target);
    }

    @Override
    public List<Node> children() {
        return List.of();
    }

    @Override
    public List<Node> references() {
        var result = new ArrayList<Node>(super.references());
        if (target != null && !result.contains(target)) result.add(target);
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
        builder.name = name;
        builder.target = target == null ? null : context.edge(target, Declaration.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<NameExpression> {
        private String name;
        private NodeBuilder<? extends Declaration> target;

        private Builder() {
            super(NameExpression.class);
        }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public Optional<NodeBuilder<? extends Declaration>> target() {
            return Optional.ofNullable(target);
        }

        public Builder setName(String name) { clearExpressionInfo();
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Builder setTarget(NodeBuilder<? extends Declaration> target) { clearExpressionInfo();
            this.target = Objects.requireNonNull(target);
            return this;
        }

        public Builder clearTarget() { clearExpressionInfo();
            this.target = null;
            return this;
        }

        @Override
        NameExpression newNode() {
            return new NameExpression();
        }

        @Override
        void fillNode(NameExpression node, BuildContext context) {
            node.name = requiredText(name, "NameExpression.name");
            node.target = target == null ? null : context.copy(target);
        }
    }
}
