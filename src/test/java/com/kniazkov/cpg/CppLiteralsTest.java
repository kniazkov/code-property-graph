/* Code Property Graph: C++03 literal model and target regressions. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.math.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CppLiteralsTest {
    private Expression literal(String text) { return literal(text, PlatformProfile.defaults()); }
    private Expression literal(String text, PlatformProfile profile) {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(Path.of("literal.cpp")), Path.of("."), List.of(), List.of(), "c++03").withPlatform(profile);
        var project = Project.create().addFile(Parser.parse(unit, "int main() { return " + text + "; }" )).build();
        var expressions = new ArrayList<Expression>();
        project.walk(new NodeVisitor() { @Override public void visit(Expression node) { expressions.add(node); } });
        return expressions.getFirst();
    }
    @Test void selectsCxx03CandidatesWithoutTruncation() {
        var large = (IntegerLiteral) literal("18446744073709551615UL");
        assertEquals(new BigInteger("18446744073709551615"), large.bigValue());
        assertEquals("unsigned long", large.cppType().orElseThrow());
        assertThrows(ArithmeticException.class, large::value);
        assertEquals("18446744073709551615UL", large.spelling().orElseThrow());
        assertEquals("long", ((IntegerLiteral) literal("2147483648")).cppType().orElseThrow());
        assertEquals("unsigned int", ((IntegerLiteral) literal("0xffffffff")).cppType().orElseThrow());
        assertEquals(63, ((IntegerLiteral) literal("077")).value());
        assertEquals(42, ((IntegerLiteral) literal("0X2AuL")).value());
        var ilp32 = PlatformProfile.builder().sizeBits("long", 32).alignmentBits("long", 32).build();
        assertEquals("unsigned long", ((IntegerLiteral) literal("2147483648", ilp32)).cppType().orElseThrow());
        assertThrows(ParseException.class, () -> literal("4294967296", ilp32));
        var wide = PlatformProfile.builder().sizeBits("long", 128).alignmentBits("long", 128).sizeBits("long long", 128).alignmentBits("long long", 128).build();
        assertEquals(new BigInteger("18446744073709551616"), ((IntegerLiteral) literal("18446744073709551616", wide)).bigValue());
    }
    @Test void modelsFloatingAndBooleanLiterals() {
        var floating = (FloatingLiteral) literal(".125e+2F");
        assertEquals(new BigDecimal("12.5"), floating.value());
        assertEquals("float", floating.cppType());
        assertEquals(".125e+2F", floating.spelling());
        assertEquals("long double", ((FloatingLiteral) literal("1.L")).cppType());
        assertEquals("double", ((FloatingLiteral) literal("1e3")).cppType());
        assertTrue(((BooleanLiteral) literal("true")).value());
        assertFalse(((BooleanLiteral) literal("false")).value());
    }
    @Test void decodesEscapesAndUsesExecutionEncoding() {
        assertEquals(BigInteger.valueOf(10), ((CharacterLiteral) literal("'\\n'")).value());
        assertEquals(BigInteger.valueOf(65), ((CharacterLiteral) literal("'\\101'")).value());
        assertEquals(BigInteger.valueOf(-1), ((CharacterLiteral) literal("'\\xff'")).value());
        assertEquals("int", ((CharacterLiteral) literal("'ab'")).cppType());
        assertEquals(BigInteger.valueOf(0x6162), ((CharacterLiteral) literal("'ab'")).value());
        var latin = PlatformProfile.builder().executionEncoding("ISO-8859-1").charSigned(false).build();
        assertEquals(BigInteger.valueOf(233), ((CharacterLiteral) literal("'é'", latin)).value());
        var utf = (StringLiteral) literal("\"é\"");
        assertEquals(List.of(BigInteger.valueOf(195), BigInteger.valueOf(169)), utf.codeUnits());
        assertThrows(ParseException.class, () -> literal("\"é\"", PlatformProfile.builder().executionEncoding("US-ASCII").build()));
        assertEquals(BigInteger.valueOf(0x3a9), ((CharacterLiteral) literal("L'\\u03a9'")).value());
    }
    @Test void concatenatesStringsAfterDecodingEachToken() {
        var string = (StringLiteral) literal("\"\\x41\" \"B\\0\" L\"\\u03a9\"");
        assertTrue(string.wide());
        assertEquals(List.of(BigInteger.valueOf(65), BigInteger.valueOf(66), BigInteger.ZERO, BigInteger.valueOf(0x3a9)), string.codeUnits());
        assertEquals("const wchar_t[5]", string.cppType());
        assertEquals("\"\\x41\" \"B\\0\" L\"\\u03a9\"", string.spelling());
        assertEquals(List.of(), ((StringLiteral) literal("\"\"")).codeUnits());
        var utf16 = PlatformProfile.builder().sizeBits("wchar_t", 16).alignmentBits("wchar_t", 16).wcharType("unsigned short").wideExecutionEncoding("UTF-16").build();
        assertEquals(List.of(BigInteger.valueOf(0xd83d), BigInteger.valueOf(0xde00)), ((StringLiteral) literal("L\"\\U0001f600\"", utf16)).codeUnits());
    }
    @Test void rejectsMalformedAndLaterEditionLiterals() {
        for (String text : List.of("09", "0x", "1uu", "1LL", "0b10", "1e+", "0x1p2", "1.0ff", "1.0_i", "'\\x'", "'\\q'", "'\\400'", "L'\\u0041'", "\"\\U00110000\""))
            assertThrows(ParseException.class, () -> literal(text), text);
    }
    @Test void editingRetainsPayloadAndDoesNotReuseStaleSpelling() {
        var original = (IntegerLiteral) literal("0xffUL");
        assertEquals(original.bigValue(), original.edit().build().bigValue());
        assertEquals(original.spelling(), original.edit().build().spelling());
        assertTrue(original.edit().setValue(7).build().spelling().isEmpty());
        assertTrue(original.edit().setCppType("int").build().spelling().isEmpty());
        assertEquals("false", ((BooleanLiteral) literal("true")).edit().setValue(false).build().spelling());
        var signed = (CharacterLiteral) literal("'\\xff'");
        assertEquals("'\\xfe'", signed.edit().setValue(BigInteger.valueOf(-2)).build().spelling());
        var multi = (CharacterLiteral) literal("'ab'");
        assertEquals("'\\x0\\x1'", multi.edit().setValue(BigInteger.ONE).build().spelling());
        assertEquals(multi.value(), ((CharacterLiteral) literal(multi.edit().setValue(multi.value()).build().spelling())).value());
        var string = (StringLiteral) literal("L\"abc\"");
        assertEquals(string.codeUnits(), string.edit().build().codeUnits());
        assertThrows(UnsupportedOperationException.class, () -> string.codeUnits().clear());
    }
}
