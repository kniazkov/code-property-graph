/* Code Property Graph: an explicitly missing or malformed expression. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;

/** Retains an expression's place without pretending that malformed input has a value. */
public final class ErrorExpression extends Expression {
    private ParseProblem problem;
    private ErrorExpression() {}
    public ParseProblem problem() { return problem; }
    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder();
        context.register(this, builder);
        builder.problem = problem;
        return builder;
    }
    public static final class Builder extends NodeBuilder<ErrorExpression> {
        private ParseProblem problem;
        private Builder() { super(ErrorExpression.class); }
        public Builder setProblem(ParseProblem value) {
            problem = Objects.requireNonNull(value);
            setRange(value.range());
            return this;
        }
        @Override ErrorExpression newNode() { return new ErrorExpression(); }
        @Override void fillNode(ErrorExpression node, BuildContext context) {
            node.problem = Objects.requireNonNull(problem, "ErrorExpression.problem is required");
        }
    }
}
