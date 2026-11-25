package com.kniazkov.cpg.tree;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegerLiteralTest {

    private static Source source(String text) {
        return (start, end) -> text.substring(start, end);
    }

    private static Fragment fragment(String text) {
        return new Fragment(
                source(text),
                new Position(0, 1, 1),
                new Position(text.length(), 1, text.length() + 1));
    }

    private static IntegerLiteral literal(String text) {
        return new IntegerLiteral(fragment(text), text);
    }

    @Test
    void parsesDecimalOctalAndHexLiterals() {
        IntegerLiteral decimal = literal("42");
        IntegerLiteral octal = literal("052");
        IntegerLiteral hex = literal("0x2A");
        IntegerLiteral upperHex = literal("0X2A");
        IntegerLiteral zero = literal("0");
        IntegerLiteral doubleZero = literal("00");

        assertEquals(new BigInteger("42"), decimal.value());
        assertEquals(10, decimal.radix());
        assertEquals(8, octal.radix());
        assertEquals(42, octal.intValue());
        assertEquals(16, hex.radix());
        assertEquals(42, hex.intValue());
        assertEquals(16, upperHex.radix());
        assertEquals(8, zero.radix());
        assertEquals(8, doubleZero.radix());
    }

    @Test
    void parsesAllSupportedSuffixes() {
        assertEquals("", literal("1").suffix());
        assertEquals("u", literal("1u").suffix());
        assertEquals("u", literal("1U").suffix());
        assertEquals("l", literal("1l").suffix());
        assertEquals("l", literal("1L").suffix());
        assertEquals("ul", literal("1ul").suffix());
        assertEquals("lu", literal("1lu").suffix());
        assertEquals("ll", literal("1ll").suffix());
        assertEquals("ull", literal("1ull").suffix());
        assertEquals("llu", literal("1llu").suffix());
        assertTrue(literal("1u").isUnsigned());
        assertTrue(literal("1ul").isUnsigned());
        assertFalse(literal("1l").isUnsigned());
    }

    @Test
    void exposesRawDataAndFragment() {
        Fragment fragment = fragment("0x2A");
        IntegerLiteral literal = new IntegerLiteral(fragment, "0x2A");

        assertSame(fragment, literal.fragment());
        assertEquals("0x2A", literal.data());
        assertTrue(literal.children().isEmpty());
        assertEquals("IntegerLiteral", literal.name());
        assertEquals("IntegerLiteral", literal.accept(value -> value.name()));
    }

    @Test
    void exposesNumericConvenienceMethods() {
        IntegerLiteral literal = literal("42");

        assertEquals(42, literal.intValue());
        assertEquals(42L, literal.longValue());
        assertEquals(42, literal.intValueExact());
        assertEquals(42L, literal.longValueExact());
        assertTrue(literal.fitsInt());
        assertTrue(literal.fitsLong());
        assertTrue(literal.fitsUnsignedInt());
        assertTrue(literal.fitsUnsignedLong());
        assertFalse(literal.isZero());
        assertEquals(1, literal.signum());
    }

    @Test
    void exposesZeroAndLargeValues() {
        IntegerLiteral zero = literal("0");
        IntegerLiteral unsignedInt = literal("0xFFFFFFFF");
        IntegerLiteral unsignedIntOverflow = literal("0x100000000");
        IntegerLiteral signedLongOverflow = literal("0x8000000000000000");
        IntegerLiteral unsignedLongOverflow = literal("0x10000000000000000");

        assertTrue(zero.isZero());
        assertEquals(0, zero.signum());
        assertEquals(-1, unsignedInt.intValue());
        assertEquals(4294967295L, unsignedInt.longValue());
        assertFalse(unsignedInt.fitsInt());
        assertTrue(unsignedInt.fitsUnsignedInt());
        assertFalse(unsignedIntOverflow.fitsUnsignedInt());
        assertFalse(signedLongOverflow.fitsLong());
        assertTrue(signedLongOverflow.fitsUnsignedLong());
        assertFalse(unsignedLongOverflow.fitsUnsignedLong());
    }

    @Test
    void exactConversionsRejectOverflow() {
        IntegerLiteral intOverflow = literal("0x80000000");
        IntegerLiteral longOverflow = literal("0x8000000000000000");

        assertThrows(ArithmeticException.class, intOverflow::intValueExact);
        assertThrows(ArithmeticException.class, longOverflow::longValueExact);
    }

    @Test
    void rejectsNullArguments() {
        Fragment fragment = fragment("1");

        assertThrows(NullPointerException.class, () -> new IntegerLiteral(null, "1"));
        assertThrows(NullPointerException.class, () -> new IntegerLiteral(fragment, null));
    }

    @Test
    void rejectsEmptyAndSignedLiterals() {
        assertThrows(IllegalArgumentException.class, () -> literal(""));
        assertThrows(IllegalArgumentException.class, () -> literal("+1"));
        assertThrows(IllegalArgumentException.class, () -> literal("-1"));
    }

    @Test
    void rejectsInvalidSuffixes() {
        assertThrows(IllegalArgumentException.class, () -> literal("1lul"));
        assertThrows(IllegalArgumentException.class, () -> literal("1uu"));
        assertThrows(IllegalArgumentException.class, () -> literal("1lll"));
    }

    @Test
    void rejectsMissingDigits() {
        assertThrows(IllegalArgumentException.class, () -> literal("u"));
        assertThrows(IllegalArgumentException.class, () -> literal("0x"));
        assertThrows(IllegalArgumentException.class, () -> literal("0X"));
    }

    @Test
    void rejectsInvalidDigits() {
        assertThrows(IllegalArgumentException.class, () -> literal("08"));
        assertThrows(IllegalArgumentException.class, () -> literal("0b1"));
        assertThrows(IllegalArgumentException.class, () -> literal("1x"));
        assertThrows(IllegalArgumentException.class, () -> literal("0xG"));
        assertThrows(IllegalArgumentException.class, () -> literal("1_0"));
    }
}
