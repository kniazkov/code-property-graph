/* Code Property Graph: persistent parser recovery diagnostics. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Objects;

/** A parse failure and the exact physical fragment omitted from the syntax tree. */
public record ParseProblem(String code, String message, SourceRange range, String skippedText, boolean recovered) {
    public ParseProblem {
        if (Objects.requireNonNull(code).isBlank() || Objects.requireNonNull(message).isBlank())
            throw new IllegalArgumentException("Parse problem code and message must be present");
        Objects.requireNonNull(range);
        Objects.requireNonNull(skippedText);
    }
}
