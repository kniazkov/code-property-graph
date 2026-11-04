/* Code Property Graph: original source span. MIT License. */
package com.kniazkov.cpg.model;

import java.nio.file.Path;
import java.util.Objects;

/** Half-open interval in the original file. Offsets can be used directly with String.substring. */
public record SourceRange(String file, SourcePosition start, SourcePosition end) {
    public SourceRange {
        file = Path.of(file).toAbsolutePath().normalize().toString();
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (end.offset() < start.offset() || end.line() < start.line()
                || (start.line() == end.line() && end.column() < start.column()))
            throw new IllegalArgumentException("Reversed source range");
    }
}
