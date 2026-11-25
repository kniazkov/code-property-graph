package com.kniazkov.cpg.ast;

/**
 * An addition expression {@code left + right}.
 */
public final class Addition extends BinaryExpression {

    /**
     * Creates an addition expression.
     *
     * @param fragment the source fragment covered by the expression
     * @param left the left operand
     * @param right the right operand
     * @throws NullPointerException if any argument is {@code null}
     */
    public Addition(Fragment fragment, Expression left, Expression right) {
        super(fragment, left, right);
    }

    @Override
    public String operator() {
        return "+";
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}
