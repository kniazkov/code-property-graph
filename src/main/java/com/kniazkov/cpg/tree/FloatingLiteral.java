package com.kniazkov.cpg.tree;

/**
 * A floating-point literal.
 */
public interface FloatingLiteral extends Literal {

    /**
     * Returns the literal value.
     *
     * @return the value
     */
    double value();

    /**
     * Returns the normalized type suffix.
     *
     * @return the suffix, possibly empty
     */
    String suffix();
}
