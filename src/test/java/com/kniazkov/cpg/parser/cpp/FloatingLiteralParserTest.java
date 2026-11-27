package com.kniazkov.cpg.parser.cpp;

import com.kniazkov.cpg.tree.Expression;
import com.kniazkov.cpg.tree.cpp.CppFloatingLiteral;
import com.kniazkov.cpg.tree.cpp.CppIntegerLiteral;
import com.kniazkov.cpg.tree.cpp.CppType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the floating literal rules of 2.13.3.
 */
class FloatingLiteralParserTest {

    private final LiteralParser parser = new LiteralParser();

    private CppFloatingLiteral parse(String text) {
        return assertInstanceOf(CppFloatingLiteral.class, parser.parse(text));
    }

    @Test
    void parsesDecimalPoint() {
        CppFloatingLiteral literal = parse("3.14");

        assertEquals(3.14, literal.value());
        assertEquals(CppType.DOUBLE, literal.type());
        assertEquals("", literal.suffix());
        assertEquals("3.14", literal.data());
        assertEquals("FloatingLiteral", literal.name());
        assertTrue(literal.children().isEmpty());
    }

    @Test
    void parsesLeadingAndTrailingDot() {
        assertEquals(0.5, parse(".5").value());
        assertEquals(1.0, parse("1.").value());
    }

    @Test
    void parsesExponents() {
        assertEquals(1000.0, parse("1e3").value());
        assertEquals(1000.0, parse("1E3").value());
        assertEquals(1000.0, parse("1e+3").value());
        assertEquals(0.001, parse("1e-3").value());
    }

    @Test
    void parsesFloatSuffix() {
        CppFloatingLiteral lower = parse("1.0f");
        CppFloatingLiteral upper = parse("1.0F");

        assertEquals(CppType.FLOAT, lower.type());
        assertEquals("f", lower.suffix());
        assertEquals(CppType.FLOAT, upper.type());
        assertEquals("f", upper.suffix());
    }

    @Test
    void parsesLongDoubleSuffix() {
        CppFloatingLiteral lower = parse("1.0l");
        CppFloatingLiteral upper = parse("1.0L");

        assertEquals(CppType.LONG_DOUBLE, lower.type());
        assertEquals("l", lower.suffix());
        assertEquals(CppType.LONG_DOUBLE, upper.type());
        assertEquals("l", upper.suffix());
    }

    @Test
    void rejectsSigns() {
        assertThrows(ParseException.class, () -> parser.parse("+1.0"));
        assertThrows(ParseException.class, () -> parser.parse("-1.0"));
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThrows(ParseException.class, () -> parser.parse("1e999"));
    }

    @Test
    void rejectsMalformedBodies() {
        assertThrows(ParseException.class, () -> parser.parse("1.2.3"));
        assertThrows(ParseException.class, () -> parser.parse("1e"));
    }

    @Test
    void hexadecimalIntegersAreNotFloating() {
        CppIntegerLiteral literal = assertInstanceOf(
                CppIntegerLiteral.class, parser.parse("0x1e"));

        assertEquals(30, literal.value().intValue());
        assertEquals(16, literal.radix());
    }

    @Test
    void integerSuffixDoesNotMakeAFloatingLiteral() {
        CppIntegerLiteral literal = assertInstanceOf(
                CppIntegerLiteral.class, parser.parse("42L"));

        assertEquals(CppType.LONG_INT, literal.type());
    }

    @Test
    void visitorReceivesTheNode() {
        Expression expression = parser.parse("1.0");

        assertEquals("visited", expression.accept(node -> "visited"));
    }

    @Test
    void fragmentCoversTheLiteral() {
        Expression expression = parser.parse("1.0");

        assertEquals(0, expression.fragment().start().offset());
        assertEquals(3, expression.fragment().end().offset());
        assertFalse(expression.fragment().code().isEmpty());
    }
}
