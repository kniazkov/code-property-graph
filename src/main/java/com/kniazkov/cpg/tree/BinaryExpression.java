package com.kniazkov.cpg.tree;

import java.util.List;
import java.util.Objects;

/**
 * An expression with a left operand and a right operand.
 *
 * <p>Concrete subclasses define the operator and any additional semantics. The
 * two operands are exposed as the only AST children of the node.</p>
 */
public abstract class BinaryExpression implements Expression {

    /** Tag of the left operand. */
    public static final String LEFT_TAG = "left";

    /** Tag of the right operand. */
    public static final String RIGHT_TAG = "right";

    private final Fragment fragment;
    private final Expression left;
    private final Expression right;

    /**
     * Creates a binary expression.
     *
     * @param fragment the source fragment covered by the expression
     * @param left the left operand
     * @param right the right operand
     * @throws NullPointerException if any argument is {@code null}
     */
    protected BinaryExpression(Fragment fragment, Expression left, Expression right) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    @Override
    public Fragment fragment() {
        return fragment;
    }

    @Override
    public List<Node> children() {
        return List.<Node>of(left, right);
    }

    /**
     * Returns the left operand.
     *
     * @return the left operand, never {@code null}
     */
    public Expression left() {
        return left;
    }

    /**
     * Returns the right operand.
     *
     * @return the right operand, never {@code null}
     */
    public Expression right() {
        return right;
    }

    @Override
    public List<String> tags() {
        return List.of(LEFT_TAG, RIGHT_TAG);
    }

    /**
     * Returns the source-level operator symbol.
     *
     * @return the operator symbol, never {@code null}
     */
    public abstract String operator();

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}
