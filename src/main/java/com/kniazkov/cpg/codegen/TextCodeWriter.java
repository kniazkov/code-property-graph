package com.kniazkov.cpg.codegen;

import java.util.Objects;

/**
 * A {@link CodeWriter} that builds source code in memory.
 *
 * <p>Indentation is applied at the beginning of each non-empty line. Both
 * {@code \n} and {@code \r\n} in written text are normalized to the configured
 * line separator.</p>
 */
public final class TextCodeWriter implements CodeWriter {

    private static final String DEFAULT_INDENT = "    ";
    private static final String DEFAULT_LINE_SEPARATOR = "\n";

    private final StringBuilder output = new StringBuilder();
    private final String indentUnit;
    private final String lineSeparator;

    private int indentLevel;
    private boolean atLineStart = true;

    /** Creates a writer with four-space indentation and {@code \n} line separators. */
    public TextCodeWriter() {
        this(DEFAULT_INDENT, DEFAULT_LINE_SEPARATOR);
    }

    /**
     * Creates a writer with the given formatting.
     *
     * @param indentUnit the text used for one indentation level
     * @param lineSeparator the line separator
     * @throws NullPointerException if any argument is {@code null}
     */
    public TextCodeWriter(String indentUnit, String lineSeparator) {
        this.indentUnit = Objects.requireNonNull(indentUnit, "indentUnit");
        this.lineSeparator = Objects.requireNonNull(lineSeparator, "lineSeparator");
    }

    @Override
    public CodeWriter write(String text) {
        Objects.requireNonNull(text, "text");
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\r') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                appendNewline();
            } else if (current == '\n') {
                appendNewline();
            } else {
                if (atLineStart) {
                    appendIndent();
                    atLineStart = false;
                }
                output.append(current);
            }
        }
        return this;
    }

    @Override
    public CodeWriter space() {
        return write(" ");
    }

    @Override
    public CodeWriter newline() {
        appendNewline();
        return this;
    }

    @Override
    public CodeWriter indent() {
        indentLevel++;
        return this;
    }

    @Override
    public CodeWriter unindent() {
        if (indentLevel == 0) {
            throw new IllegalStateException("indentation level is already zero");
        }
        indentLevel--;
        return this;
    }

    @Override
    public String code() {
        return output.toString();
    }

    private void appendNewline() {
        output.append(lineSeparator);
        atLineStart = true;
    }

    private void appendIndent() {
        for (int level = 0; level < indentLevel; level++) {
            output.append(indentUnit);
        }
    }
}
