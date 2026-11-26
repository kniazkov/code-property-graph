package com.kniazkov.cpg.parser.cpp;

import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Visitor;
import com.kniazkov.cpg.tree.cpp.CppCharacterLiteral;
import com.kniazkov.cpg.tree.cpp.CppType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the character literal statements CPP03-2.13.2-008 through
 * CPP03-2.13.2-027.
 */
class CharacterLiteralParserTest {

    private static CppCharacterLiteral parse(String source) {
        return (CppCharacterLiteral) new LiteralParser().parse(source);
    }

    @Test
    void statement_010_ordinarySingleCCharHasTypeChar() {
        CppCharacterLiteral literal = parse("'a'");

        assertFalse(literal.wide());
        assertEquals(List.of(97), literal.cchars());
        assertEquals(CppType.CHAR, literal.type());
        assertEquals("char", literal.typeName());
        assertEquals(97L, literal.value());
        assertEquals("'a'", literal.data());
        assertEquals("CharacterLiteral", literal.name());
        assertTrue(literal.children().isEmpty());
    }

    @Test
    void statement_016_wideSingleCCharHasTypeWcharT() {
        CppCharacterLiteral literal = parse("L'a'");

        assertTrue(literal.wide());
        assertEquals(List.of(97), literal.cchars());
        assertEquals(CppType.WCHAR_T, literal.type());
        assertEquals(97L, literal.value());
    }

    @Test
    void statement_013_multicharacterLiteralHasTypeInt() {
        CppCharacterLiteral literal = parse("'ab'");

        assertFalse(literal.wide());
        assertEquals(List.of(97, 98), literal.cchars());
        assertEquals(CppType.INT, literal.type());
        assertEquals(24930L, literal.value());
    }

    @Test
    void statement_018_wideMulticharacterLiteralHasTypeWcharT() {
        CppCharacterLiteral literal = parse("L'ab'");

        assertTrue(literal.wide());
        assertEquals(List.of(97, 98), literal.cchars());
        assertEquals(CppType.WCHAR_T, literal.type());
        assertEquals(24930L, literal.value());
    }

    @Test
    void statement_019_simpleEscapeSequencesAreSupported() {
        Map<String, Integer> escapes = Map.ofEntries(
                Map.entry("'\\n'", 10),
                Map.entry("'\\t'", 9),
                Map.entry("'\\v'", 11),
                Map.entry("'\\b'", 8),
                Map.entry("'\\r'", 13),
                Map.entry("'\\f'", 12),
                Map.entry("'\\a'", 7),
                Map.entry("'\\\\'", 92),
                Map.entry("'\\''", 39),
                Map.entry("'\\\"'", 34),
                Map.entry("'\\?'", 63));

        for (Map.Entry<String, Integer> entry : escapes.entrySet()) {
            CppCharacterLiteral literal = parse(entry.getKey());

            assertEquals(entry.getValue().longValue(), literal.value(), entry.getKey());
            assertEquals(CppType.CHAR, literal.type(), entry.getKey());
        }
    }

    @Test
    void statement_024_octalEscapesUseOneToThreeDigits() {
        assertEquals(0L, parse("'\\0'").value());
        assertEquals(10L, parse("'\\12'").value());
        assertEquals(83L, parse("'\\123'").value());
        assertEquals(List.of(83, 52), parse("'\\1234'").cchars());
        assertEquals(List.of(1, 56), parse("'\\18'").cchars());
        assertEquals(List.of(1, 33), parse("'\\1!'").cchars());
    }

    @Test
    void statement_025_hexadecimalEscapesUseOneOrMoreDigits() {
        assertEquals(97L, parse("'\\x61'").value());
        assertEquals(106L, parse("'\\x6a'").value());
        assertEquals(106L, parse("'\\x6A'").value());
        assertEquals(74L, parse("'\\x4A'").value());
        assertEquals(97L, parse("'\\x00000061'").value());
        assertEquals(List.of(97, 103), parse("'\\x61g'").cchars());
    }

    @Test
    void statement_029_universalCharacterNamesAreTranslated() {
        assertEquals(65L, parse("'\\u0041'").value());
        assertEquals(65L, parse("'\\U00000041'").value());
    }

    @Test
    void rejectsEmptyLiterals() {
        assertThrows(ParseException.class, () -> parse("''"));
        assertThrows(ParseException.class, () -> parse("L''"));
    }

    @Test
    void rejectsMalformedLiterals() {
        assertThrows(ParseException.class, () -> parse("'"));
        assertThrows(ParseException.class, () -> parse("'a"));
        assertThrows(ParseException.class, () -> parse("L'"));
    }

    @Test
    void rejectsRawQuoteNewlineAndCarriageReturn() {
        assertThrows(ParseException.class, () -> parse("'a'b'"));
        assertThrows(ParseException.class, () -> parse("'a\nb'"));
        assertThrows(ParseException.class, () -> parse("'a\rb'"));
    }

    @Test
    void statement_022_unknownEscapeIsRejected() {
        assertThrows(ParseException.class, () -> parse("'\\q'"));
        assertThrows(ParseException.class, () -> parse("'\\!'"));
        assertThrows(ParseException.class, () -> parse("'\\8'"));
        assertThrows(ParseException.class, () -> parse("'\\'"));
    }

    @Test
    void rejectsMalformedHexEscapes() {
        assertThrows(ParseException.class, () -> parse("'\\x'"));
        assertThrows(ParseException.class, () -> parse("'\\x!'"));
        assertThrows(ParseException.class, () -> parse("'\\xg'"));
        assertThrows(ParseException.class, () -> parse("'\\xG'"));
    }

    @Test
    void rejectsMalformedUniversalCharacterNames() {
        assertThrows(ParseException.class, () -> parse("'\\u00'"));
        assertThrows(ParseException.class, () -> parse("'\\u00G0'"));
    }

    @Test
    void exposesFragmentAndVisitor() {
        CppCharacterLiteral literal = parse("'a'");

        assertEquals("'a'", literal.fragment().code());
        assertEquals("CharacterLiteral", literal.accept(new Visitor<String>() {
            @Override
            public String visit(Node node) {
                return node.name();
            }
        }));
    }
}
