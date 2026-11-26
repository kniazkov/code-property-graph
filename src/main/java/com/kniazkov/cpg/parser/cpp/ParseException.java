package com.kniazkov.cpg.parser.cpp;

/**
 * Signals that a literal does not satisfy the C++03 grammar or type rules.
 */
public final class ParseException extends RuntimeException {

    private final int offset;

    /**
     * Creates a parse error.
     *
     * @param message the diagnostic message
     * @param offset the zero-based offset in the parsed text
     */
    public ParseException(String message, int offset) {
        super(message);
        this.offset = offset;
    }

    /**
     * Returns the zero-based offset at which parsing failed.
     *
     * @return the offset
     */
    public int offset() {
        return offset;
    }
}
