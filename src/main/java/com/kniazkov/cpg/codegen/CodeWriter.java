package com.kniazkov.cpg.codegen;

/**
 * A sink for generated source code.
 *
 * <p>Implementations control indentation, line separators, and other formatting
 * details. A generator writes tokens and structure through this interface
 * instead of building a string directly.</p>
 */
public interface CodeWriter {

    /**
     * Writes text at the current position.
     *
     * @param text the text to write
     * @return this writer
     */
    CodeWriter write(String text);

    /**
     * Writes a single space.
     *
     * @return this writer
     */
    CodeWriter space();

    /**
     * Starts a new line.
     *
     * @return this writer
     */
    CodeWriter newline();

    /**
     * Increases the indentation level.
     *
     * @return this writer
     */
    CodeWriter indent();

    /**
     * Decreases the indentation level.
     *
     * @return this writer
     * @throws IllegalStateException if the indentation level is already zero
     */
    CodeWriter unindent();

    /**
     * Returns the generated code.
     *
     * @return the generated code
     */
    String code();
}
