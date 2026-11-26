package com.kniazkov.cpg.parser.cpp;

import com.kniazkov.cpg.tree.Expression;
import com.kniazkov.cpg.tree.cpp.CppBooleanLiteral;
import com.kniazkov.cpg.tree.cpp.CppCharacterLiteral;
import com.kniazkov.cpg.tree.cpp.CppIntegerLiteral;
import com.kniazkov.cpg.tree.cpp.CppType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves dispatch, whitespace handling, and fragment construction of the
 * literal parser.
 */
class LiteralParserTest {

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> new LiteralParser().parse(null));
    }

    @Test
    void rejectsEmptyAndWhitespaceOnlyInput() {
        assertThrows(ParseException.class, () -> new LiteralParser().parse(""));
        assertThrows(ParseException.class, () -> new LiteralParser().parse("   "));
    }

    @Test
    void trimsSurroundingWhitespace() {
        Expression expression = new LiteralParser().parse("  'a'  ");

        CppCharacterLiteral literal = assertInstanceOf(CppCharacterLiteral.class, expression);
        assertEquals("'a'", literal.data());
        assertEquals("'a'", literal.fragment().code());
    }

    @Test
    void dispatchesCharacterLiterals() {
        assertInstanceOf(CppCharacterLiteral.class, new LiteralParser().parse("'a'"));
        assertInstanceOf(CppCharacterLiteral.class, new LiteralParser().parse("L'a'"));
    }

    @Test
    void dispatchesBooleanLiterals() {
        assertInstanceOf(CppBooleanLiteral.class, new LiteralParser().parse("true"));
        assertInstanceOf(CppBooleanLiteral.class, new LiteralParser().parse("false"));
    }

    @Test
    void dispatchesIntegerLiterals() {
        assertInstanceOf(CppIntegerLiteral.class, new LiteralParser().parse("42"));
        assertInstanceOf(CppIntegerLiteral.class, new LiteralParser().parse("1"));
    }

    @Test
    void rejectsWordsThatAreNotLiterals() {
        assertThrows(ParseException.class, () -> new LiteralParser().parse("TRUE"));
        assertThrows(ParseException.class, () -> new LiteralParser().parse("truex"));
        assertThrows(ParseException.class, () -> new LiteralParser().parse("L1"));
    }

    @Test
    void fragmentCoversTheLiteral() {
        Expression expression = new LiteralParser().parse("42");

        assertEquals(0, expression.fragment().start().offset());
        assertEquals(1, expression.fragment().start().line());
        assertEquals(1, expression.fragment().start().column());
        assertEquals(2, expression.fragment().end().offset());
        assertEquals(1, expression.fragment().end().line());
        assertEquals(3, expression.fragment().end().column());
    }

    @Test
    void defaultModelIsIlp32() {
        assertThrows(ParseException.class, () -> new LiteralParser().parse("2147483648"));
    }

    @Test
    void customModelIsUsed() {
        Expression expression = new LiteralParser(CppDataModel.LP64).parse("2147483648");

        CppIntegerLiteral literal = assertInstanceOf(CppIntegerLiteral.class, expression);
        assertEquals(CppType.LONG_INT, literal.type());
    }
}
