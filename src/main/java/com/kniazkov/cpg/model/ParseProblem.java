/* Code Property Graph: persistent parser recovery diagnostics. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;

/**
 * One parse-failure occurrence. The primary position is independent of the exact physical
 * fragment omitted from syntax: range/skippedText retain the recovery contract.
 * reason is a stable cause with significant parameters, excluding presentation/context notes.
 */
public record ParseProblem(String code, String message, SourceRange range, String skippedText, boolean recovered,
        SourceRange primaryRange, String reason, List<DiagnosticRelatedRange> relatedRanges, DiagnosticContext context) {
    public ParseProblem {
        if (Objects.requireNonNull(code).isBlank() || Objects.requireNonNull(message).isBlank()
                || Objects.requireNonNull(reason).isBlank())
            throw new IllegalArgumentException("Parse problem code, message and reason must be present");
        Objects.requireNonNull(range);
        Objects.requireNonNull(primaryRange);
        Objects.requireNonNull(skippedText);
        relatedRanges = List.copyOf(relatedRanges);
    }

    /** Compatibility for callers without provenance; the owning project supplies a TU context. */
    public ParseProblem(String code, String message, SourceRange range, String skippedText, boolean recovered) {
        this(code, message, range, skippedText, recovered, range, message, List.of(), null);
    }

    public ParseProblem withContext(DiagnosticContext value) {
        return new ParseProblem(code, message, range, skippedText, recovered, primaryRange, reason, relatedRanges,
                Objects.requireNonNull(value));
    }
}
