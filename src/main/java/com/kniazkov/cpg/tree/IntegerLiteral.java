package com.kniazkov.cpg.tree;

import java.math.BigInteger;

/**
 * A language-independent integer literal.
 *
 * <p>Implementations provide the numeric value, the base, and the normalized
 * suffix. Language packages may add type resolution on top of this
 * interface.</p>
 */
public interface IntegerLiteral extends Literal {

    /**
     * Returns the numeric value.
     *
     * @return the value
     */
    BigInteger value();

    /**
     * Returns the base of the literal.
     *
     * @return {@code 8}, {@code 10}, or {@code 16}
     */
    int radix();

    /**
     * Returns the normalized suffix.
     *
     * @return the suffix, possibly empty
     */
    String suffix();
}
