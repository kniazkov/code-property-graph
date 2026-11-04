/* Code Property Graph: UTF-16 source coordinates. MIT License. */
package com.kniazkov.cpg.model;

/** One-based line and column, and zero-based absolute UTF-16 offset. */
public record SourcePosition(int line, int column, int offset) {
    public SourcePosition {
        if (line < 1 || column < 1 || offset < 0) throw new IllegalArgumentException("Invalid source position");
    }
}
