/* C++03 expression syntax model. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable ConditionalExpression syntax with owned expression children. */
public final class ConditionalExpression extends Expression {
    private Expression condition;
    private Expression thenExpression;
    private Expression elseExpression;
    private ConditionalExpression() {}
    public Expression condition() { return condition; }
    public Expression thenExpression() { return thenExpression; }
    public Expression elseExpression() { return elseExpression; }
    @Override public List<Node> children() { return List.of(condition, thenExpression, elseExpression); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder();
        context.register(this, builder);
        builder.condition = context.edge(condition, Expression.class);
        builder.thenExpression = context.edge(thenExpression, Expression.class);
        builder.elseExpression = context.edge(elseExpression, Expression.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<ConditionalExpression> {
        private NodeBuilder<? extends Expression> condition;
        private NodeBuilder<? extends Expression> thenExpression;
        private NodeBuilder<? extends Expression> elseExpression;
        private Builder() { super(ConditionalExpression.class); }
        public Optional<NodeBuilder<? extends Expression>> condition() { return Optional.ofNullable(condition); }
        public Builder setCondition(NodeBuilder<? extends Expression> value) { condition = Objects.requireNonNull(value); clearExpressionInfo(); return this; }
        public Optional<NodeBuilder<? extends Expression>> thenExpression() { return Optional.ofNullable(thenExpression); }
        public Builder setThenExpression(NodeBuilder<? extends Expression> value) { thenExpression = Objects.requireNonNull(value); clearExpressionInfo(); return this; }
        public Optional<NodeBuilder<? extends Expression>> elseExpression() { return Optional.ofNullable(elseExpression); }
        public Builder setElseExpression(NodeBuilder<? extends Expression> value) { elseExpression = Objects.requireNonNull(value); clearExpressionInfo(); return this; }
        @Override ConditionalExpression newNode() { return new ConditionalExpression(); }
        @Override void fillNode(ConditionalExpression node, BuildContext context) {
            node.condition = context.copy(condition);
            node.thenExpression = context.copy(thenExpression);
            node.elseExpression = context.copy(elseExpression);
        }
    }
}
