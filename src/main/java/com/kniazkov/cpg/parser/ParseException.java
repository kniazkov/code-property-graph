/* Source diagnostics for the CPG prototype. MIT License. */
package com.kniazkov.cpg.parser;

/** A source error; strict entry points throw it instead of returning a partial graph. */
public final class ParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String file;
    private final String reason;
    private final int line, column;
    private final com.kniazkov.cpg.model.SourceRange range;
    public ParseException(String file, int line, int column, String reason) {
        this(file, line, column, reason, null);
    }
    public ParseException(String file, int line, int column, String reason, com.kniazkov.cpg.model.SourceRange range) {
        super(file + ":" + line + ":" + column + ": " + reason);
        this.range = range;
        this.reason = reason; this.file = file; this.line = line; this.column = column;
    }
    public java.util.Optional<com.kniazkov.cpg.model.SourceRange> range() { return java.util.Optional.ofNullable(range); }
    public String reason() { return reason; }
    public String file() { return file; }
    public int line() { return line; }
    public int column() { return column; }
}
