/* Code Property Graph: a finding attached to one syntax node. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Objects;
import java.util.List;

/** Stable rule code and severity for tools, plus a message for source-oriented reports. */
public record Diagnostic(String code, Severity severity, String message,
        SourceRange primaryRange, List<DiagnosticRelatedRange> relatedRanges) {
    public enum Severity { INFO, WARNING, ERROR }

    public Diagnostic {
        Objects.requireNonNull(severity);
        relatedRanges = List.copyOf(relatedRanges);
        if (Objects.requireNonNull(code).isBlank() || Objects.requireNonNull(message).isBlank())
            throw new IllegalArgumentException("Diagnostic code and message must be present");
    }

    /** A finding whose primary position is supplied by its owning syntax node. */
    public Diagnostic(String code, Severity severity, String message) {
        this(code, severity, message, null, List.of());
    }
}
