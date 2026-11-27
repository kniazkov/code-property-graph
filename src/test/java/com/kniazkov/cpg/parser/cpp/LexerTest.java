package com.kniazkov.cpg.parser.cpp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the preprocessing-token lexer rules of clause 2.4 and related
 * sections.
 */
class LexerTest {

    private final Lexer lexer = new Lexer();

    private List<Token> tokens(String source) {
        return lexer.tokenize(source);
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> lexer.tokenize(null));
    }

    @Test
    void tokenizesEmptySource() {
        assertEquals(List.of(), tokens(""));
        assertEquals(List.of(), tokens("   \n\t"));
    }

    @Test
    void skipsLineComments() {
        List<Token> result = tokens("int x // comment\n y");

        assertEquals(List.of("int", "x", "y"),
                result.stream().map(Token::text).toList());
    }

    @Test
    void skipsLineCommentAtEndOfSource() {
        assertEquals(List.of("a"),
                tokens("a // comment").stream().map(Token::text).toList());
    }

    @Test
    void skipsBlockComments() {
        assertEquals(List.of("a", "c"),
                tokens("a /* b */ c").stream().map(Token::text).toList());
        assertEquals(List.of("a", "c"),
                tokens("a /* * b */ c").stream().map(Token::text).toList());
    }

    @Test
    void rejectsUnterminatedBlockComment() {
        assertThrows(ParseException.class, () -> tokens("a /* b"));
    }

    @Test
    void distinguishesKeywordsAndIdentifiers() {
        List<Token> result = tokens("class Foo _bar x1 x_");

        assertEquals(List.of(TokenKind.KEYWORD, TokenKind.IDENTIFIER,
                TokenKind.IDENTIFIER, TokenKind.IDENTIFIER, TokenKind.IDENTIFIER),
                result.stream().map(Token::kind).toList());
        assertEquals("x_", result.get(4).text());
    }

    @Test
    void tokenizesPreprocessingNumbers() {
        List<Token> result = tokens("42 0x2A 1.0f 1e+3 1e-3 1E+3 1_2");

        assertEquals(7, result.size());
        result.forEach(token -> assertEquals(TokenKind.PP_NUMBER, token.kind()));
        assertEquals("1e+3", result.get(3).text());
        assertEquals("1E+3", result.get(5).text());
        assertEquals("1_2", result.get(6).text());
    }

    @Test
    void stopsNumberBeforeANonExponentSign() {
        assertEquals(List.of("1", "+", "2"),
                tokens("1+2").stream().map(Token::text).toList());
    }

    @Test
    void tokenizesCharacterAndStringLiterals() {
        List<Token> result = tokens("'a' L'a' \"ab\" L\"ab\"");

        assertEquals(List.of(TokenKind.CHARACTER_LITERAL, TokenKind.CHARACTER_LITERAL,
                TokenKind.STRING_LITERAL, TokenKind.STRING_LITERAL),
                result.stream().map(Token::kind).toList());
        assertEquals("L\"ab\"", result.get(3).text());
    }

    @Test
    void handlesEscapesInsideLiterals() {
        assertEquals(List.of("'\\''", "\"\\\"\""),
                tokens("'\\'' \"\\\"\"").stream().map(Token::text).toList());
    }

    @Test
    void rejectsUnterminatedLiteralAtNewline() {
        assertThrows(ParseException.class, () -> tokens("'a\n"));
        assertThrows(ParseException.class, () -> tokens("'a\r"));
    }

    @Test
    void rejectsUnterminatedLiteralAtEndOfSource() {
        assertThrows(ParseException.class, () -> tokens("'a"));
        assertThrows(ParseException.class, () -> tokens("'\\"));
    }

    @Test
    void aLiteralAfterAnIdentifierIsNotMerged() {
        assertEquals(List.of("xL", "'a'"),
                tokens("xL'a'").stream().map(Token::text).toList());
    }

    @Test
    void tokenizesMultiCharacterOperators() {
        List<Token> result = tokens(">>= <<= ->* ... :: -> ++ -- && ## .*");

        assertEquals(List.of(">>=", "<<=", "->*", "...", "::", "->", "++",
                "--", "&&", "##", ".*"),
                result.stream().map(Token::text).toList());
        assertEquals(TokenKind.PUNCTUATOR, result.get(4).kind());
        assertEquals(TokenKind.OPERATOR, result.get(0).kind());
    }

    @Test
    void tokenizesPunctuators() {
        List<Token> result = tokens("{}[]();:?.,#");

        result.forEach(token -> assertEquals(TokenKind.PUNCTUATOR, token.kind()));
    }

    @Test
    void aSlashThatStartsNoCommentIsAnOperator() {
        assertEquals(List.of("/", "x"),
                tokens("/x").stream().map(Token::text).toList());
        assertEquals(List.of("/"),
                tokens("/").stream().map(Token::text).toList());
    }

    @Test
    void aLoneLIsAnIdentifier() {
        assertEquals(TokenKind.IDENTIFIER, tokens("L").get(0).kind());
        assertEquals(TokenKind.IDENTIFIER, tokens("Lx").get(0).kind());
    }

    @Test
    void aDotNotFollowedByADigitIsAPunctuator() {
        assertEquals(List.of(".", "x"),
                tokens(".x").stream().map(Token::text).toList());
        assertEquals(List.of("."),
                tokens(".").stream().map(Token::text).toList());
    }

    @Test
    void aDotFollowedByADigitStartsANumber() {
        assertEquals(List.of(TokenKind.PP_NUMBER),
                tokens(".5").stream().map(Token::kind).toList());
    }

    @Test
    void rejectsUnrecognizedCharacters() {
        assertThrows(ParseException.class, () -> tokens("@"));
    }

    @Test
    void reportsOffsets() {
        List<Token> result = tokens("int x");

        assertEquals(0, result.get(0).offset());
        assertEquals(4, result.get(1).offset());
    }

    @Test
    void tokenRecordValidatesItsArguments() {
        assertThrows(NullPointerException.class, () -> new Token(null, "x", 0));
        assertThrows(NullPointerException.class, () -> new Token(TokenKind.IDENTIFIER, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Token(TokenKind.IDENTIFIER, "x", -1));
    }
}
