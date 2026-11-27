package com.kniazkov.cpg.parser.cpp;

/**
 * The kind of a C++03 preprocessing token produced by the lexer.
 */
public enum TokenKind {

    IDENTIFIER,
    KEYWORD,
    PP_NUMBER,
    CHARACTER_LITERAL,
    STRING_LITERAL,
    OPERATOR,
    PUNCTUATOR
}
