/* Code Property Graph: CPG-owned C++03 macro regression tests. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.build.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

class StandardLibraryMacrosTest {
    @TempDir Path directory;
    private Build.TranslationUnit unit(PlatformProfile profile) {
        return new Build.TranslationUnit(new Build.PhysicalSource(directory.resolve("main.cpp")), directory,
                List.of(), List.of(), "c++03").withPlatform(profile);
    }
    private CppPreprocessor.Result preprocess(String source, PlatformProfile profile) {
        return CppPreprocessor.preprocess(unit(profile), source);
    }
    private String text(CppPreprocessor.Result result) {
        return String.join(" ", result.tokens().stream().filter(t -> !Set.of(WHITESPACE, NEWLINE, EOF, HEADER_NAME).contains(t.token().kind()))
                .map(t -> t.token().spelling()).toList());
    }
    @Test void integerLimitsWorkInDirectivesAndOrdinaryExpressionsForTwoProfiles() {
        var lp64 = PlatformProfile.defaults();
        var ilp32 = lp64.toBuilder().name("unsigned-char-ilp32").sizeBits("long", 32).alignmentBits("long", 32)
                .charSigned(false).nullPointerConstant("0L").maxMultibyteLength(4).build();
        for (String header : List.of("climits", "limits.h")) for (var profile : List.of(lp64, ilp32)) {
            var result = preprocess("#include <" + header + ">\n#if INT_MAX > 0 && INT_MIN < 0 && UINT_MAX > INT_MAX\n"
                    + "INT_MAX LONG_MAX ULONG_MAX CHAR_MIN CHAR_MAX MB_LEN_MAX\n#else\n#error wrong target limits\n#endif\n", profile);
            assertEquals(profile == lp64 ? "2147483647 9223372036854775807L 18446744073709551615UL ( - 127 - 1 ) 127 16"
                    : "2147483647 2147483647L 4294967295UL 0 255 4", text(result));
            var definitions = result.definitions().stream().filter(d -> d.originKind().equals("library")).toList();
            assertEquals(16, definitions.size());
            assertTrue(definitions.stream().allMatch(d -> d.sourceKey().contains(profile.identityHash())));
            assertTrue(result.expansions().stream().allMatch(e -> e.definition().file().contains("<cpg-library:" + header)));
            assertEquals(Set.of(directory.resolve("main.cpp").toString()), result.sources().keySet(), "No host or fake physical headers");
        }
    }
    @Test void headerVisibilityRepeatedInclusionAndTranslationUnitsAreIndependent() {
        String source = "#ifdef INT_MAX\n#error leaked before include\n#endif\n#include <climits>\n"
                + "#undef INT_MAX\n#include <limits.h>\nINT_MAX\n";
        assertEquals("2147483647", text(preprocess(source, PlatformProfile.defaults())));
        assertEquals("INT_MAX NULL FLT_RADIX", text(preprocess("INT_MAX NULL FLT_RADIX", PlatformProfile.defaults())));
        for (String header : List.of("cstddef", "cstdio", "cstdlib", "cstring", "ctime", "clocale", "cwchar",
                "stddef.h", "stdio.h", "stdlib.h", "string.h", "time.h", "locale.h", "wchar.h")) {
            var profile = PlatformProfile.builder().nullPointerConstant("0L").build();
            assertEquals("0L", text(preprocess("#include <" + header + ">\nNULL\n", profile)));
        }
    }
    @Test void actualSuppliedHeadersTakePrecedenceOverTheBuiltinMacroModel() throws Exception {
        Files.writeString(directory.resolve("climits"), "#define INT_MAX 17\n");
        var configured = new Build.TranslationUnit(unit(PlatformProfile.defaults()).source(), directory,
                List.of(new Build.IncludePath("-I", directory)), List.of(), "c++03");
        var result = CppPreprocessor.preprocess(configured, "#include <climits>\nINT_MAX LONG_MAX\n");
        assertEquals("17 LONG_MAX", text(result));
        assertTrue(result.definitions().stream().noneMatch(d -> d.originKind().equals("library")));
    }
    @Test void floatMacrosUseExplicitFormatsNotStorageWidthsOrJavaFloatingPoint() {
        var binary128 = PlatformProfile.defaults();
        var x87 = binary128.toBuilder().floatingFormat("long double", "binary80").floatingRounds(0).build();
        assertEquals(binary128.sizes(), x87.sizes());
        for (String header : List.of("cfloat", "float.h")) for (var profile : List.of(binary128, x87)) {
            var result = preprocess("#include <" + header + ">\n#if FLT_RADIX != 2\n#error radix\n#endif\n"
                    + "FLT_MANT_DIG DBL_MANT_DIG LDBL_MANT_DIG FLT_ROUNDS FLT_EPSILON LDBL_MAX\n", profile);
            assertTrue(text(result).startsWith(profile == binary128 ? "24 53 113 1 " : "24 53 64 0 "), text(result));
            assertTrue(text(result).contains("1.1920928955078125e-7F"));
            assertEquals(29, result.definitions().stream().filter(d -> d.originKind().equals("library")).count());
        }
        var unknown = binary128.toBuilder().floatingFormat("long double", "unknown").build();
        var error = assertThrows(ParseException.class, () -> preprocess("#include <cfloat>\n", unknown));
        assertTrue(error.reason().contains("explicit floatingFormats.long double"));
        assertEquals(1, error.line());
    }
    @Test void wcharLimitsFollowTheirPromotedUnderlyingTypeAndDoNotInventWeof() {
        var profile = PlatformProfile.builder().wcharType("unsigned int").build();
        assertEquals("0U 4294967295U WEOF", text(preprocess("#include <wchar.h>\nWCHAR_MIN WCHAR_MAX WEOF", profile)));
    }
    @Test void wcharLimitsUseWcharIntegralPromotionRatherThanUnderlyingIntegerRank() {
        var ilp32 = PlatformProfile.builder().sizeBits("long", 32).alignmentBits("long", 32);
        var signed = ilp32.wcharType("long").build();
        var unsigned = signed.toBuilder().wcharType("unsigned long").build();
        assertEquals("( - 2147483647 - 1 ) 2147483647", text(preprocess("#include <cwchar>\nWCHAR_MIN WCHAR_MAX", signed)));
        assertEquals("0U 4294967295U", text(preprocess("#include <wchar.h>\nWCHAR_MIN WCHAR_MAX", unsigned)));
    }
    @Test void noLaterStandardMacrosOrFabricatedAbiMacros() {
        var result = preprocess("#include <climits>\n#include <cfloat>\n#include <cerrno>\n#include <cstdarg>\n"
                + "#include <csetjmp>\n#include <cstddef>\n#include <ciso646>\n", PlatformProfile.defaults());
        var names = result.definitions().stream().map(CppPreprocessor.Definition::name).toList();
        for (String name : List.of("LLONG_MAX", "ULLONG_MAX", "FLT_TRUE_MIN", "FLT_EVAL_METHOD", "DECIMAL_DIG", "FLT_DECIMAL_DIG",
                "errno", "va_start", "va_arg", "va_end", "va_copy", "setjmp", "offsetof", "and", "or", "not")) assertFalse(names.contains(name), name);
    }
}
