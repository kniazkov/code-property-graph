package com.kniazkov.cpg.tree;

import java.util.List;

/**
 * A list of parameter declarations.
 */
public final class ParameterList extends NodeList<Declaration> {

    /**
     * Creates a parameter list.
     *
     * @param fragment the source fragment covered by the list
     * @param elements the parameter declarations
     * @throws NullPointerException if any argument is {@code null} or an
     *         element is {@code null}
     */
    public ParameterList(Fragment fragment, List<Declaration> elements) {
        super(fragment, elements);
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}
