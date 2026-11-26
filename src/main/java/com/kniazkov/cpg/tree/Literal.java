package com.kniazkov.cpg.tree;

/**
 * A literal expression, such as an integer, character, or boolean literal.
 *
 * <p>This is a language-independent marker interface. Concrete literal nodes
 * live in a language package and implement the more specific literal
 * interfaces from this package.</p>
 */
public interface Literal extends Expression {
}
