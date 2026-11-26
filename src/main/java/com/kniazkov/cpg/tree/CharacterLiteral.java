package com.kniazkov.cpg.tree;

/**
 * A language-independent character literal.
 */
public interface CharacterLiteral extends Literal {

    /**
     * Returns the literal value.
     *
     * @return the value
     */
    int value();
}
