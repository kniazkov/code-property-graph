package com.kniazkov.cpg.ast;

import java.util.Objects;

/**
 * A half-open source fragment {@code [start, end)}.
 *
 * <p>The start position is inclusive and the end position is exclusive. An
 * empty fragment is allowed when {@code start} and {@code end} are equal.</p>
 *
 * @param source the source text containing this fragment
 * @param start the inclusive start position
 * @param end the exclusive end position
 */
public record Fragment(Source source, Position start, Position end) {

    /**
     * Creates a source fragment.
     *
     * @param source the source text containing this fragment
     * @param start the inclusive start position
     * @param end the exclusive end position
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code end} precedes {@code start}
     */
    public Fragment {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.compareTo(start) < 0) {
            throw new IllegalArgumentException("fragment end precedes start");
        }
    }

    /**
     * Returns the source text covered by this fragment.
     *
     * @return the fragment text, never {@code null}
     */
    public String code() {
        return source.text(start.offset(), end.offset());
    }
}
