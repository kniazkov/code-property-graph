package com.kniazkov.cpg.parser.cpp;

import com.kniazkov.cpg.tree.Expression;
import com.kniazkov.cpg.tree.cpp.CppStringLiteral;
import com.kniazkov.cpg.tree.cpp.CppType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the string literal rules of 2.13.4.
 */
class StringLiteralParserTest {

    private final LiteralParser parser = new LiteralParser();

    private CppStringLiteral parse(String text) {
        return assertInstanceOf(CppStringLiteral.class, parser.parse(text));
    }

    @Test
    void parsesNarrowString() {
        CppStringLiteral literal = parse("\"abc\"");

        assertFalse(literal.wide());
        assertEquals(CppType.CHAR_ARRAY, literal.type());
        assertEquals(List.of(97, 98, 99, 0), literal.chars());
        assertEquals("\"abc\"", literal.data());
        assertEquals("StringLiteral", literal.name());
        assertTrue(literal.children().isEmpty());
    }

    @Test
    void parsesWideString() {
        CppStringLiteral literal = parse("L\"abc\"");

        assertTrue(literal.wide());
        assertEquals(CppType.WCHAR_T_ARRAY, literal.type());
        assertEquals(List.of(97, 98, 99, 0), literal.chars());
    }

    @Test
    void parsesEmptyString() {
        assertEquals(List.of(0), parse("\"\"").chars());
    }

    @Test
    void parsesSimpleEscapes() {
        CppStringLiteral literal = parse("\"\\n\\t\\\\\\\"\"");

        assertEquals(List.of(10, 9, 92, 34, 0), literal.chars());
    }

    @Test
    void parsesOctalHexAndUniversalEscapes() {
        assertEquals(List.of(65, 0), parse("\"\\101\"").chars());
        assertEquals(List.of(65, 0), parse("\"\\x41\"").chars());
        assertEquals(List.of(65, 0), parse("\"\\u0041\"").chars());
        assertEquals(List.of(65, 0), parse("\"\\U00000041\"").chars());
    }

    @Test
    void singleQuoteMayAppearLiterally() {
        assertEquals(List.of(39, 0), parse("\"'\"").chars());
    }

    @Test
    void rejectsUnescapedDoubleQuote() {
        assertThrows(ParseException.class, () -> parser.parse("\"a\"b\""));
    }

    @Test
    void rejectsNewlines() {
        assertThrows(ParseException.class, () -> parser.parse("\"a\nb\""));
        assertThrows(ParseException.class, () -> parser.parse("\"a\rb\""));
    }

    @Test
    void rejectsMalformedLiterals() {
        assertThrows(ParseException.class, () -> parser.parse("\"abc"));
        assertThrows(ParseException.class, () -> parser.parse("L\"abc"));
        assertThrows(ParseException.class, () -> parser.parse("\""));
        assertThrows(ParseException.class, () -> parser.parse("L\""));
    }

    @Test
    void rejectsUnknownEscapes() {
        assertThrows(ParseException.class, () -> parser.parse("\"\\z\""));
    }

    @Test
    void visitorReceivesTheNode() {
        Expression expression = parser.parse("\"a\"");

        assertEquals("visited", expression.accept(node -> "visited"));
    }

    @Test
    void fragmentCoversTheLiteral() {
        Expression expression = parser.parse("\"a\"");

        assertEquals(0, expression.fragment().start().offset());
        assertEquals(3, expression.fragment().end().offset());
        assertEquals("\"a\"", expression.fragment().code());
    }
}
