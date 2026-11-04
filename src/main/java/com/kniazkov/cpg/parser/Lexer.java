/* Code Property Graph: tokenization of the supported Java and C++ subsets. MIT License. */
package com.kniazkov.cpg.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans a single source file from left to right. Comments and whitespace affect
 * locations but produce no tokens. A preprocessor line stays intact so the file
 * parser can validate the directive against the chosen language. The final token
 * records the position at which a missing closing delimiter should be reported.
 */
final class Lexer {
    private final String file;
    private final String source;
    private int offset;
    private int line = 1;
    private int column = 1;

    Lexer(String file, String source) {
        this.file = file;
        this.source = source;
    }

    /** Consumes this lexer's input once and returns tokens in source order. */
    List<Token> scan() {
        var tokens = new ArrayList<Token>();
        while (offset < source.length()) {
            int start = offset, startLine = line, startColumn = column;
            char ch = source.charAt(offset);
            if (Character.isWhitespace(ch)) {
                advance();
                continue;
            }
            if (source.startsWith("//", offset)) {
                skipLine();
                continue;
            }
            if (source.startsWith("/*", offset)) {
                advance();
                advance();
                while (offset < source.length() && !source.startsWith("*/", offset)) advance();
                if (offset == source.length()) throw new ParseException(file, startLine, startColumn, "Unterminated comment");
                advance();
                advance();
                continue;
            }
            if (ch == '#') {
                skipLine();
            } else if (Character.isJavaIdentifierStart(ch)) {
                advance();
                while (offset < source.length() && Character.isJavaIdentifierPart(source.charAt(offset))) advance();
            } else if (Character.isDigit(ch)) {
                advance();
                while (offset < source.length() && Character.isDigit(source.charAt(offset))) advance();
            } else if (source.startsWith("::", offset) || source.startsWith("<<", offset)
                    || source.startsWith("<=", offset) || source.startsWith(">=", offset)
                    || source.startsWith("==", offset) || source.startsWith("!=", offset)) {
                advance();
                advance();
            } else if ("{}()[];=+-*/.,:<>".indexOf(ch) >= 0) {
                advance();
            } else {
                throw new ParseException(file, line, column, "Unsupported character: " + ch);
            }
            tokens.add(new Token(source.substring(start, offset), startLine, startColumn));
        }
        tokens.add(new Token("<eof>", line, column));
        return List.copyOf(tokens);
    }

    /** Leaves the newline for the ordinary whitespace path, also handling a final line without one. */
    private void skipLine() {
        while (offset < source.length() && source.charAt(offset) != '\n') advance();
    }

    /** Advances the offset and updates the one-based position of the next character. */
    private void advance() {
        if (source.charAt(offset++) == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
    }
}
