package com.kniazkov.cpg.tree;

/**
 * A position in the source text.
 *
 * <p>The offset is zero-based and counts characters from the beginning of the
 * source. The line and column are one-based. Columns count characters, not
 * bytes.</p>
 *
 * @param offset the zero-based character offset
 * @param line the one-based line number
 * @param column the one-based column number
 */
public record Position(int offset, int line, int column)
        implements Comparable<Position> {

    /**
     * Creates a source position.
     *
     * @param offset the zero-based character offset
     * @param line the one-based line number
     * @param column the one-based column number
     * @throws IllegalArgumentException if {@code offset} is negative or if
     *         {@code line} or {@code column} is less than one
     */
    public Position {
        if (offset < 0 || line < 1 || column < 1) {
            throw new IllegalArgumentException("invalid source position");
        }
    }

    /**
     * Compares this position with another position by character offset.
     *
     * @param other the other position
     * @return a negative integer, zero, or a positive integer as this position
     *         is before, at, or after {@code other}
     */
    @Override
    public int compareTo(Position other) {
        return Integer.compare(offset, other.offset);
    }
}
