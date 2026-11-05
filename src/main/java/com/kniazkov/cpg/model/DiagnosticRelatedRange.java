/* Code Property Graph: labeled physical diagnostic evidence. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Objects;

/** One labeled related fragment; its physical file may differ from the primary position. */
public record DiagnosticRelatedRange(String role, String label, SourceRange range) {
    public DiagnosticRelatedRange {
        if (Objects.requireNonNull(role).isBlank() || Objects.requireNonNull(label).isBlank())
            throw new IllegalArgumentException("Related diagnostic role and label must be present");
        Objects.requireNonNull(range);
    }
}
