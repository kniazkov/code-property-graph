package com.kniazkov.cpg.parser.cpp;

import com.kniazkov.cpg.tree.Visitor;
import com.kniazkov.cpg.tree.cpp.CppIntegerLiteral;
import com.kniazkov.cpg.tree.cpp.CppType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the integer literal statements CPP03-2.13.1-011 through
 * CPP03-2.13.1-027.
 */
class IntegerLiteralParserTest {

    private static CppIntegerLiteral parse(String source) {
        return (CppIntegerLiteral) new LiteralParser(CppDataModel.LP64).parse(source);
    }

    private static CppIntegerLiteral parse(String source, CppDataModel model) {
        return (CppIntegerLiteral) new LiteralParser(model).parse(source);
    }

    @Test
    void statement_011_integerLiteralIsADigitSequence() {
        CppIntegerLiteral literal = parse("42");

        assertEquals(new BigInteger("42"), literal.value());
        assertEquals(10, literal.radix());
        assertEquals("", literal.suffix());
        assertEquals(CppType.INT, literal.type());
        assertEquals("int", literal.typeName());
        assertEquals("42", literal.data());
        assertEquals("IntegerLiteral", literal.name());
        assertTrue(literal.children().isEmpty());
    }

    @Test
    void statement_015_decimalLiteralUsesBaseTen() {
        CppIntegerLiteral literal = parse("123");

        assertEquals(10, literal.radix());
        assertEquals(new BigInteger("123"), literal.value());
    }

    @Test
    void statement_016_octalLiteralUsesBaseEight() {
        CppIntegerLiteral literal = parse("052");

        assertEquals(8, literal.radix());
        assertEquals(new BigInteger("42"), literal.value());
    }

    @Test
    void statement_017_hexadecimalLiteralUsesBaseSixteen() {
        assertEquals(16, parse("0x2A").radix());
        assertEquals(16, parse("0X2A").radix());
        assertEquals(new BigInteger("42"), parse("0x2A").value());
    }

    @Test
    void statement_003_zeroIsAnOctalLiteral() {
        CppIntegerLiteral literal = parse("0");

        assertEquals(8, literal.radix());
        assertEquals(BigInteger.ZERO, literal.value());
        assertEquals(CppType.INT, literal.type());
    }

    @Test
    void statement_019_twelveHasThreeSpellings() {
        assertEquals(parse("12").value(), parse("014").value());
        assertEquals(parse("12").value(), parse("0XC").value());
    }

    @Test
    void statement_024_unsignedSuffixSelectsUnsignedInt() {
        for (String source : List.of("42u", "42U")) {
            CppIntegerLiteral literal = parse(source);

            assertEquals("u", literal.suffix());
            assertEquals(CppType.UNSIGNED_INT, literal.type());
        }
    }

    @Test
    void statement_025_longSuffixSelectsLongInt() {
        for (String source : List.of("42l", "42L")) {
            CppIntegerLiteral literal = parse(source);

            assertEquals("l", literal.suffix());
            assertEquals(CppType.LONG_INT, literal.type());
        }
    }

    @Test
    void statement_026_combinedSuffixSelectsUnsignedLongInt() {
        for (String source : List.of("42ul", "42lu", "42uL", "42Lu", "42Ul", "42lU", "42UL", "42LU")) {
            CppIntegerLiteral literal = parse(source);

            assertEquals(CppType.UNSIGNED_LONG_INT, literal.type());
            assertTrue(literal.suffix().equals("ul") || literal.suffix().equals("lu"));
        }
    }

    @Test
    void statement_021_decimalWithoutSuffixUsesIntThenLong() {
        assertEquals(CppType.INT, parse("2147483647").type());
        assertEquals(CppType.LONG_INT, parse("2147483648").type());
    }

    @Test
    void statement_022_decimalOverflowIsRejectedInIlp32() {
        assertThrows(ParseException.class, () -> parse("2147483648", CppDataModel.ILP32));
    }

    @Test
    void statement_023_octalAndHexUseTheFullCandidateList() {
        assertEquals(CppType.INT, parse("0x7FFFFFFF").type());
        assertEquals(CppType.UNSIGNED_INT, parse("0x80000000").type());
        assertEquals(CppType.LONG_INT, parse("0x100000000").type());
        assertEquals(CppType.UNSIGNED_LONG_INT, parse("0xFFFFFFFFFFFFFFFF").type());
    }

    @Test
    void statement_023_octalAndHexOverflowIsRejected() {
        assertThrows(ParseException.class, () -> parse("0x10000000000000000"));
    }

    @Test
    void statement_024_unsignedSuffixUsesUnsignedIntThenUnsignedLong() {
        assertEquals(CppType.UNSIGNED_INT, parse("4294967295u").type());
        assertEquals(CppType.UNSIGNED_LONG_INT, parse("4294967296u").type());
    }

    @Test
    void statement_024_unsignedSuffixOverflowIsRejected() {
        assertThrows(ParseException.class, () -> parse("4294967296u", CppDataModel.ILP32));
    }

    @Test
    void statement_025_longSuffixUsesLongIntThenUnsignedLong() {
        assertEquals(CppType.LONG_INT, parse("2147483648l").type());
        assertEquals(CppType.UNSIGNED_LONG_INT, parse("9223372036854775808l").type());
    }

    @Test
    void statement_025_longSuffixOverflowIsRejected() {
        assertThrows(ParseException.class, () -> parse("18446744073709551616l"));
    }

    @Test
    void statement_027_unrepresentableLiteralIsRejected() {
        assertThrows(ParseException.class, () -> parse("0x10000000000000000"));
        assertThrows(ParseException.class, () -> parse("18446744073709551616l"));
    }

    @Test
    void rejectsSigns() {
        assertThrows(ParseException.class, () -> parse("+1"));
        assertThrows(ParseException.class, () -> parse("-1"));
    }

    @Test
    void rejectsMissingDigits() {
        assertThrows(ParseException.class, () -> parse("0x"));
        assertThrows(ParseException.class, () -> parse("u"));
    }

    @Test
    void rejectsInvalidOctalDigits() {
        assertThrows(ParseException.class, () -> parse("08"));
    }

    @Test
    void rejectsCxx11Suffixes() {
        assertThrows(ParseException.class, () -> parse("1ll"));
        assertThrows(ParseException.class, () -> parse("1ull"));
        assertThrows(ParseException.class, () -> parse("1llu"));
    }

    @Test
    void rejectsNonNumericBody() {
        assertThrows(ParseException.class, () -> parse("1z"));
    }

    @Test
    void exposesFragmentAndVisitor() {
        CppIntegerLiteral literal = parse("42");

        assertEquals("42", literal.fragment().code());
        assertEquals(0, literal.fragment().start().offset());
        assertEquals(2, literal.fragment().end().offset());
        assertEquals("IntegerLiteral", literal.accept(new Visitor<String>() {
            @Override
            public String visit(com.kniazkov.cpg.tree.Node node) {
                return node.name();
            }
        }));
    }
}
