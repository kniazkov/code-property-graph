package com.kniazkov.cpg.ast;

import java.util.List;

/**
 * A list of expressions.
 */
public final class ExpressionList extends NodeList<Expression> {

    /**
     * Creates an expression list.
     *
     * @param fragment the source fragment covered by the list
     * @param elements the expressions
     * @throws NullPointerException if any argument is {@code null} or an
     *         element is {@code null}
     */
    public ExpressionList(Fragment fragment, List<Expression> elements) {
        super(fragment, elements);
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}
