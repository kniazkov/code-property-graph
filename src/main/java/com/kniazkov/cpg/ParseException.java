/* Source diagnostics for the CPG prototype. MIT License. */
package com.kniazkov.cpg;

/** A source error; no partial graph accompanies this exception. */
public final class ParseException extends RuntimeException {
    private final String file;
    private final int line, column;
    public ParseException(String file, int line, int column, String reason) {
        super(file + ":" + line + ":" + column + ": " + reason);
        this.file = file; this.line = line; this.column = column;
    }
    public String file() { return file; }
    public int line() { return line; }
    public int column() { return column; }
}
