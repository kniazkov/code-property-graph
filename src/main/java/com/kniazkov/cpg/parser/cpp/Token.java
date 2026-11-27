package com.kniazkov.cpg.parser.cpp;

import java.util.Objects;

/**
 * A C++03 preprocessing token.
 *
 * @param kind the token kind
 * @param text the token spelling
 * @param offset the zero-based offset in the source
 */
public record Token(TokenKind kind, String text, int offset) {

    /**
     * Creates a token.
     *
     * @param kind the token kind
     * @param text the token spelling
     * @param offset the zero-based offset in the source
     */
    public Token {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
