package com.kniazkov.cpg.tree;

/**
 * A source text that can be queried by absolute character offsets.
 */
public interface Source {

    /**
     * Returns the source text in the half-open range {@code [start, end)}.
     *
     * @param start the inclusive zero-based start offset
     * @param end the exclusive zero-based end offset
     * @return the requested source text
     * @throws IndexOutOfBoundsException if the range is invalid
     */
    String text(int start, int end);
}
