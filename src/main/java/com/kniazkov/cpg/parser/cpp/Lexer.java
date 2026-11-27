package com.kniazkov.cpg.parser.cpp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A small C++03 preprocessing-token lexer.
 *
 * <p>The lexer implements the preprocessing-token categories of clause 2.4 and
 * related sections for a single source string. It skips white space and comments and
 * returns immutable tokens with their source offsets.</p>
 */
public final class Lexer {

    private static final Set<String> KEYWORDS = Set.of(
            "asm", "auto", "bool", "break", "case", "catch", "char", "class",
            "const", "const_cast", "continue", "default", "delete", "do",
            "double", "dynamic_cast", "else", "enum", "explicit", "export",
            "extern", "false", "float", "for", "friend", "goto", "if",
            "inline", "int", "long", "mutable", "namespace", "new", "operator",
            "private", "protected", "public", "register", "reinterpret_cast",
            "return", "short", "signed", "sizeof", "static", "static_cast",
            "struct", "switch", "template", "this", "throw", "true", "try",
            "typedef", "typeid", "typename", "union", "unsigned", "using",
            "virtual", "void", "volatile", "wchar_t", "while");

    private static final Set<String> PUNCTUATORS = Set.of(
            "{", "}", "[", "]", "(", ")", ";", ":", "?", ".", "...", "#",
            "##", "%:%:", "::", ",");

    private static final List<String> OPERATORS = List.of(
            "%:%:", ">>=", "<<=", "->*", "...",
            "::", "->", "++", "--", "<<", ">>", "<=", ">=", "==", "!=",
            "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "##", ".*",
            "{", "}", "[", "]", "(", ")", ";", ":", "?", ".", "+", "-",
            "*", "/", "%", "^", "&", "|", "~", "!", "=", "<", ">", ",", "#");

    /**
     * Tokenizes a source string.
     *
     * @param source the source text
     * @return an immutable list of preprocessing tokens
     * @throws NullPointerException if {@code source} is {@code null}
     * @throws ParseException if the source contains an unterminated comment or
     *     literal, or an unrecognized character
     */
    public List<Token> tokenize(String source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char ch = source.charAt(index);
            if (Character.isWhitespace(ch)) {
                index++;
            } else if (ch == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '/') {
                index = skipLineComment(source, index + 2);
            } else if (ch == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '*') {
                index = skipBlockComment(source, index + 2);
            } else if (ch == '\'' || ch == '"') {
                index = readLiteral(source, index, index, ch, tokens);
            } else if (ch == 'L' && index + 1 < source.length()
                    && (source.charAt(index + 1) == '\''
                    || source.charAt(index + 1) == '"')) {
                index = readLiteral(
                        source, index, index + 1, source.charAt(index + 1), tokens);
            } else if (isIdentifierStart(ch)) {
                index = readIdentifier(source, index, tokens);
            } else if (Character.isDigit(ch)
                    || (ch == '.' && index + 1 < source.length()
                    && Character.isDigit(source.charAt(index + 1)))) {
                index = readNumber(source, index, tokens);
            } else {
                index = readOperator(source, index, tokens);
            }
        }
        return List.copyOf(tokens);
    }

    private static int skipLineComment(String source, int index) {
        int cursor = index;
        while (cursor < source.length() && source.charAt(cursor) != '\n') {
            cursor++;
        }
        return cursor;
    }

    private static int skipBlockComment(String source, int index) {
        int cursor = index;
        while (cursor + 1 < source.length()) {
            if (source.charAt(cursor) == '*' && source.charAt(cursor + 1) == '/') {
                return cursor + 2;
            }
            cursor++;
        }
        throw new ParseException("unterminated block comment", index - 2);
    }

    private static int readLiteral(String source, int start, int quoteIndex, char quote,
                                   List<Token> tokens) {
        int cursor = quoteIndex + 1;
        while (cursor < source.length()) {
            char ch = source.charAt(cursor);
            if (ch == '\\') {
                cursor += 2;
                continue;
            }
            if (ch == quote) {
                TokenKind kind = quote == '\''
                        ? TokenKind.CHARACTER_LITERAL
                        : TokenKind.STRING_LITERAL;
                tokens.add(new Token(kind, source.substring(start, cursor + 1), start));
                return cursor + 1;
            }
            if (ch == '\n' || ch == '\r') {
                break;
            }
            cursor++;
        }
        throw new ParseException("unterminated literal", start);
    }

    private static boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static int readIdentifier(String source, int start, List<Token> tokens) {
        int cursor = start + 1;
        while (cursor < source.length() && isIdentifierPart(source.charAt(cursor))) {
            cursor++;
        }
        String text = source.substring(start, cursor);
        TokenKind kind = KEYWORDS.contains(text) ? TokenKind.KEYWORD : TokenKind.IDENTIFIER;
        tokens.add(new Token(kind, text, start));
        return cursor;
    }

    private static int readNumber(String source, int start, List<Token> tokens) {
        int cursor = start + 1;
        while (cursor < source.length()) {
            char ch = source.charAt(cursor);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.') {
                cursor++;
            } else if (ch == '+' || ch == '-') {
                char previous = source.charAt(cursor - 1);
                if (previous == 'e' || previous == 'E') {
                    cursor++;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        tokens.add(new Token(TokenKind.PP_NUMBER, source.substring(start, cursor), start));
        return cursor;
    }

    private static int readOperator(String source, int start, List<Token> tokens) {
        for (String operator : OPERATORS) {
            if (source.startsWith(operator, start)) {
                TokenKind kind = PUNCTUATORS.contains(operator)
                        ? TokenKind.PUNCTUATOR
                        : TokenKind.OPERATOR;
                tokens.add(new Token(kind, operator, start));
                return start + operator.length();
            }
        }
        throw new ParseException("unrecognized character", start);
    }
}
