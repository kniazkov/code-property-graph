package com.kniazkov.cpg.ast;

import java.util.List;

/**
 * A list of statements.
 */
public final class StatementList extends NodeList<Statement> {

    /**
     * Creates a statement list.
     *
     * @param fragment the source fragment covered by the list
     * @param elements the statements
     * @throws NullPointerException if any argument is {@code null} or an
     *         element is {@code null}
     */
    public StatementList(Fragment fragment, List<Statement> elements) {
        super(fragment, elements);
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}
