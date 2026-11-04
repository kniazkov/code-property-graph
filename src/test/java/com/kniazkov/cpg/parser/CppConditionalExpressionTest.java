/* Code Property Graph: C++03 conditional-expression tests. MIT License. */
package com.kniazkov.cpg.parser;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

class CppConditionalExpressionTest {
    private static boolean evaluate(String expression) {
        var tokens = CppLexer.scan("condition.cpp", expression).tokens().stream()
                .filter(t -> t.kind() != WHITESPACE && t.kind() != NEWLINE && t.kind() != EOF).toList();
        return CppConditionalExpression.evaluate(tokens);
    }

    @Test void implementsArithmeticAndBitwisePrecedenceAndAssociativity() {
        for (String expression : List.of(
                "1 + 2 * 3 == 7", "(1 + 2) * 3 == 9", "20 / 3 % 4 == 2",
                "10 - 3 - 2 == 5", "64 / 4 / 2 == 8", "1 + 2 << 2 == 12",
                "64 >> 2 >> 1 == 8", "8 >> 1 + 1 == 2", "1 | 2 ^ 3 & 6",
                "1 | 2 == 2", "2 == 3 < 4 == 0", "!0 && +3 && -2 < 0",
                "(~0 & 255) == 255", "0 || 1 && 2", "1 < 2 && 2 <= 2 && 3 > 2 && 3 >= 3"))
            assertTrue(evaluate(expression), expression);
        assertFalse(evaluate("1 ^ 1 | 0"));
        assertFalse(evaluate("2 & 2 == 2"));
    }

    @Test void supportsAlternativesAndReplacesRemainingKeywordsAndIdentifiers() {
        assertTrue(evaluate("true and not false and (3 bitand 1) and (1 not_eq 2)"));
        assertTrue(evaluate("(1 bitor 2) == (7 xor 4) and (compl 0) == -1"));
        assertTrue(evaluate("unknown == 0 and int == 0 and sizeof == 0 and new == 0 and delete == 0"));
        assertTrue(evaluate("(true + true) == 2 && -true == -1"));
        assertFalse(evaluate("false or undeclared"));
    }

    @Test void acceptsOnlyCpp03IntegerBasesAndSuffixes() {
        for (String suffix : List.of("", "u", "U", "l", "L", "ul", "uL", "Ul", "UL", "lu", "lU", "Lu", "LU"))
            assertTrue(evaluate("012" + suffix + " == 10 && 0xA" + suffix + " == 10"), suffix);
        for (String expression : List.of("08", "0x", "0b10", "1ll", "1LL", "1lL", "1ull", "1z",
                "1.0", "1e2", ".5", "0x1p2", "1uu", "123name", "18446744073709551616U",
                "9223372036854775808"))
            assertThrows(ParseException.class, () -> evaluate(expression), expression);
    }

    @Test void preservesSignedAndUnsignedConversionsAndUnsignedWrapping() {
        for (String expression : List.of(
                "-1 > 1U", "-1 == 0xffffffffffffffff", "0xffffffffffffffffU + 1 == 0",
                "0U - 1 == 18446744073709551615U", "0x8000000000000000 / 2 == 0x4000000000000000",
                "0xffffffffffffffffU % 3 == 0", "0x8000000000000000 >> 63 == 1",
                "-2 >> 1 == -1", "1 << 63 == (-9223372036854775807 - 1)",
                "(-1 >> 1U) == -1", "9223372036854775808L > 0", "(0U - 1) * 2 == 0xfffffffffffffffeU"))
            assertTrue(evaluate(expression), expression);
    }

    @Test void ternaryIsRightAssociativeAndConvertsBothOperandTypes() {
        assertTrue(evaluate("0 ? 0 : 1 ? 3 : 0"));
        assertTrue(evaluate("1 ? 0 ? 0 : 4 : 0"));
        assertTrue(evaluate("(1 ? -1 : 0U) > 0"));
        assertTrue(evaluate("(0 ? 0U : -1) > 0"));
        assertTrue(evaluate("(1 ? -1 : (1 ? 1U : 0)) > 0"));
        assertFalse(evaluate("1 ? 0 : 1 ? 1 : 0"));
    }

    @Test void skippedOperandsAreParsedButArithmeticFaultsAreNotEvaluated() {
        for (String expression : List.of("1 || 1/0", "!(0 && 1/0)", "1 ? 3 : 1/0",
                "0 ? 1/0 : 1", "1 || (1 << -1)", "0 ? (1 >> 64) : 1",
                "1 || 9223372036854775807 + 1", "1 || -(-9223372036854775807 - 1)",
                "(1 ? -1 : (1U / 0)) > 0", "1 || (0 ? 1 : 1/0)"))
            assertTrue(evaluate(expression), expression);
        for (String expression : List.of("0 && (1 +)", "1 || (1 /)", "1 ? 2 :", "0 ? : 1",
                "1 || 1.0", "0 && 1LL", "1 ? 2 : (3, 4)", "1 || \"string\""))
            assertThrows(ParseException.class, () -> evaluate(expression), expression);
    }

    @Test void diagnosesEvaluatedArithmeticFaults() {
        for (String expression : List.of("1 / 0", "1 % 0", "1 << -1", "1 >> 64", "1 << 0xffffffffffffffffU",
                "9223372036854775807 + 1", "(-9223372036854775807 - 1) - 1", "9223372036854775807 * 2",
                "-(-9223372036854775807 - 1)", "(-9223372036854775807 - 1) / -1",
                "(-9223372036854775807 - 1) % -1", "1 && 1/0", "0 || 1/0"))
            assertThrows(ParseException.class, () -> evaluate(expression), expression);
    }

    @Test void evaluatesCharacterEscapesAndDocumentedExecutionEncoding() {
        for (String expression : List.of("'a' == 97", "'\\n' == 10", "'\\t' == 9", "'\\a' == 7",
                "'\\b' == 8", "'\\v' == 11", "'\\f' == 12", "'\\r' == 13", "'\\\\' == 92",
                "'\\\'' == 39", "'\\\"' == 34", "'\\?' == 63", "'\\101' == 'A'", "'\\x41' == 'A'",
                "'\\377' == 255", "'\\x100' == 0", "'ab' == 0x6162", "'\\0a' == 'a'",
                "L'\\u03b1' == 945", "L'α' == 945", "L'ab' == 'b'", "L'\\xFFFFFFFF' == -1",
                "'é' == 0xc3a9", "'\\u00e9' == 'é'", "L'\\U0001f600' == 0x1f600"))
            assertTrue(evaluate(expression), expression);
        for (String expression : List.of("'\\x'", "'\\q'", "1 || '\\x'"))
            assertThrows(ParseException.class, () -> evaluate(expression), expression);
    }

    @Test void rejectsCastsAssignmentsCommaAndMalformedSyntax() {
        for (String expression : List.of("", "()", "1 2", "(1", "1)", "1 ? 2", "1 : 2", "1 ? 2 : 3 : 4",
                "(int)1", "1, 2", "1 ? 2, 3 : 4", "x = 1", "++x", "x++", "f()", "1 += 2"))
            assertThrows(ParseException.class, () -> evaluate(expression), expression);
    }

    @Test void errorsPreservePhysicalLocationAcrossSplices() {
        var error = assertThrows(ParseException.class, () -> evaluate("1 + \\\n  2 / 0"));
        assertEquals(2, error.line());
        assertEquals(5, error.column());
        assertEquals("Division by zero in #if expression", error.reason());
        assertTrue(error.file().endsWith("condition.cpp"));
    }
}
