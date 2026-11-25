package com.kniazkov.cpg.ast;

/**
 * A visitor over syntax tree nodes.
 *
 * @param <R> the result type of the visitor
 */
public interface Visitor<R> {

    /**
     * Visits a syntax tree node.
     *
     * @param node the node to visit
     * @return the result produced by the visitor
     */
    R visit(Node node);
}
