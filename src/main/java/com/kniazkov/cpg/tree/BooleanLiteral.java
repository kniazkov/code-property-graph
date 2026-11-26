package com.kniazkov.cpg.tree;

/**
 * A language-independent boolean literal.
 */
public interface BooleanLiteral extends Literal {

    /**
     * Returns the boolean value.
     *
     * @return the value
     */
    boolean value();
}
