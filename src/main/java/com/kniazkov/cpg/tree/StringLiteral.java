package com.kniazkov.cpg.tree;

import java.util.List;

/**
 * A string literal.
 */
public interface StringLiteral extends Literal {

    /**
     * Returns whether the literal has the {@code L} prefix.
     *
     * @return {@code true} for a wide string literal
     */
    boolean wide();

    /**
     * Returns the character codes, including the terminating null character.
     *
     * @return an immutable list of codes
     */
    List<Integer> chars();
}
