/* Source diagnostics for the CPG prototype. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.List;
import java.util.Optional;

/** A source error; strict entry points throw it instead of returning a partial graph. */
public final class ParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String file, reason, code, causeReason;
    private final int line, column;
    private final SourceRange range;
    private final List<DiagnosticRelatedRange> relatedRanges;
    private final DiagnosticContext context;
    public ParseException(String file, int line, int column, String reason) {
        this(file, line, column, reason, null);
    }
    public ParseException(String file, int line, int column, String reason, SourceRange range) {
        this(file, line, column, reason, range, "syntax-error", reason, List.of(), null);
    }
    public ParseException(String file, int line, int column, String reason, SourceRange range,
            String code, String causeReason, List<DiagnosticRelatedRange> relatedRanges, DiagnosticContext context) {
        super(file + ":" + line + ":" + column + ": " + reason);
        this.range = range;
        this.reason = reason; this.file = file; this.line = line; this.column = column;
        this.code = code; this.causeReason = causeReason;
        this.relatedRanges = List.copyOf(relatedRanges); this.context = context;
    }
    /** Changes presentation only, retaining the original cause and physical provenance. */
    public ParseException withReason(String value) {
        return new ParseException(file, line, column, value, range, code, causeReason, relatedRanges, context);
    }
    public ParseException withContext(DiagnosticContext value) {
        return new ParseException(file, line, column, reason, range, code, causeReason, relatedRanges, value);
    }
    ParseException withDiagnostic(String diagnosticCode, String cause, SourceRange primary,
            List<DiagnosticRelatedRange> related, DiagnosticContext occurrence) {
        return new ParseException(file, line, column, reason, primary, diagnosticCode, cause, related, occurrence);
    }
    public Optional<SourceRange> range() { return Optional.ofNullable(range); }
    public Optional<SourceRange> primaryRange() { return range(); }
    public String reason() { return reason; }
    public String code() { return code; }
    public String causeReason() { return causeReason; }
    public List<DiagnosticRelatedRange> relatedRanges() { return relatedRanges; }
    public DiagnosticContext context() { return context; }
    public String file() { return file; }
    public int line() { return line; }
    public int column() { return column; }
}
