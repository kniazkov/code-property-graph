package com.kniazkov.cpg.tree;

/**
 * A literal whose language-level type is known.
 *
 * <p>The type is exposed as a stable spelling, so the interface stays
 * independent of any language-specific type enum.</p>
 */
public interface TypedLiteral extends Literal {

    /**
     * Returns the language-level type spelling.
     *
     * @return the type name, for example {@code "int"} or {@code "bool"}
     */
    String typeName();
}
