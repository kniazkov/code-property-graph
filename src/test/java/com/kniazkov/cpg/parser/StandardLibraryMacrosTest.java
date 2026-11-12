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
    // Independent C90/C++03 lists, deliberately not derived from the provider.
    private static final Set<String> INTEGER_LIMITS = Set.of("CHAR_BIT", "SCHAR_MIN", "SCHAR_MAX", "UCHAR_MAX",
            "CHAR_MIN", "CHAR_MAX", "MB_LEN_MAX", "SHRT_MIN", "SHRT_MAX", "USHRT_MAX", "INT_MIN", "INT_MAX",
            "UINT_MAX", "LONG_MIN", "LONG_MAX", "ULONG_MAX");
    private static final Set<String> FLOATING_LIMITS = Set.of("FLT_RADIX", "FLT_ROUNDS",
            "FLT_MANT_DIG", "FLT_DIG", "FLT_MIN_EXP", "FLT_MIN_10_EXP", "FLT_MAX_EXP", "FLT_MAX_10_EXP", "FLT_MAX", "FLT_EPSILON", "FLT_MIN",
            "DBL_MANT_DIG", "DBL_DIG", "DBL_MIN_EXP", "DBL_MIN_10_EXP", "DBL_MAX_EXP", "DBL_MAX_10_EXP", "DBL_MAX", "DBL_EPSILON", "DBL_MIN",
            "LDBL_MANT_DIG", "LDBL_DIG", "LDBL_MIN_EXP", "LDBL_MIN_10_EXP", "LDBL_MAX_EXP", "LDBL_MAX_10_EXP", "LDBL_MAX", "LDBL_EPSILON", "LDBL_MIN");
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
            assertEquals(INTEGER_LIMITS.size(), definitions.size());
            assertEquals(INTEGER_LIMITS, definitions.stream().map(CppPreprocessor.Definition::name).collect(java.util.stream.Collectors.toSet()));
            assertTrue(definitions.stream().allMatch(d -> d.sourceKey().contains(profile.identityHash())));
            assertTrue(result.expansions().stream().allMatch(e -> e.definition().file().contains(".cpg-library-" + header)));
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
            var definitions = result.definitions().stream().filter(d -> d.originKind().equals("library")).toList();
            assertEquals(FLOATING_LIMITS.size(), definitions.size());
            assertEquals(FLOATING_LIMITS, definitions.stream().map(CppPreprocessor.Definition::name).collect(java.util.stream.Collectors.toSet()));
        }
        var unknown = binary128.toBuilder().floatingFormat("long double", "unknown").build();
        var error = assertThrows(ParseException.class, () -> preprocess("#include <cfloat>\n", unknown));
        assertTrue(error.reason().contains("explicit floatingFormats.long double"));
        assertEquals(1, error.line());
    }
    @Test void wcharLimitsFollowTheirPromotedUnderlyingTypeAndLeaveWeofUnavailable() {
        var profile = PlatformProfile.builder().wcharType("unsigned int").build();
        assertEquals("0U 4294967295U", text(preprocess("#include <wchar.h>\nWCHAR_MIN WCHAR_MAX", profile)));
        assertNotNull(preprocess("#include <wchar.h>\n", profile).definitions().stream()
                .filter(d -> d.name().equals("WEOF")).findFirst().orElseThrow().unavailableReason());
        assertTrue(assertThrows(ParseException.class, () -> preprocess("#include <wchar.h>\nWEOF", profile))
                .reason().contains("wideInteger"));
    }
    @Test void wcharLimitsUseWcharIntegralPromotionRatherThanUnderlyingIntegerRank() {
        var ilp32 = PlatformProfile.builder().sizeBits("long", 32).alignmentBits("long", 32);
        var signed = ilp32.wcharType("long").build();
        var unsigned = signed.toBuilder().wcharType("unsigned long").build();
        assertEquals("( - 2147483647 - 1 ) 2147483647", text(preprocess("#include <cwchar>\nWCHAR_MIN WCHAR_MAX", signed)));
        assertEquals("0U 4294967295U", text(preprocess("#include <wchar.h>\nWCHAR_MIN WCHAR_MAX", unsigned)));
    }
    private static boolean targetFactAvailable(PlatformProfile profile, Object name) {
        if ("offsetof".equals(name)) return profile.offsetof().isPresent();
        if ("HUGE_VAL".equals(name)) return profile.hugeVal().isPresent();
        if ("CLOCKS_PER_SEC".equals(name)) return profile.clockScale().isPresent();
        return "WEOF".equals(name) ? profile.wideInteger().isPresent() : profile.runtimeIntegerMacros().containsKey(name);
    }
    @Test void implementedInventoryObligationsMatchActualHeaderDefinitions() throws Exception {
        var inventory = (Map<?, ?>) com.kniazkov.json.Json.parse(Path.of("docs/cpp03-library-macros.json").toFile()).toJavaObject();
        var entries = (List<?>) inventory.get("entries");
        for (var profile : List.of(PlatformProfile.builder().offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "2147483647").build(), PlatformProfile.defaults(), PlatformProfile.builder().cRuntime("glibc").build(),
                PlatformProfile.builder().wideInteger("unsigned int","4294967295","nonnegative-wchar").build(),
                PlatformProfile.builder().wideInteger("int","-1","nonnegative-wchar").build(),
                PlatformProfile.builder().clockScale("long","long","1000000").build(),
                PlatformProfile.builder().hugeVal("positive_infinity","gcc-clang-builtin-huge-val").build(),
                PlatformProfile.builder().clockScale("int","unsigned long","18446744073709551615").build(),
                PlatformProfile.builder().cRuntime("glibc").signalDispositionBinding("glibc-x86_64-lp64").build(),
                PlatformProfile.builder().cRuntime("glibc").standardStreamsBinding("glibc-x86_64-lp64").build(),
                PlatformProfile.builder().cRuntime("glibc").mbCurMaxBinding("glibc-x86_64-lp64-mb16").build(),
                PlatformProfile.read(Path.of("src/test/resources/runtime-integer-macros/synthetic-profile.json"))))
        for (Object item : (List<?>) inventory.get("headers")) {
            var header = (Map<?, ?>) item;
            String cpp = (String) header.get("header"), c = (String) header.get("c_header");
            var implemented = new HashSet<String>();
            var targetFacts = new HashSet<String>();
            for (Object record : entries) {
                var entry = (Map<?, ?>) record;
                // Availability for a bounded subset does not close the standards obligation.
                if (cpp.equals(entry.get("header")) && "offsetof".equals(entry.get("name"))) {
                    assertEquals("open", entry.get("support_status"));
                    assertTrue(entry.get("partial_implementation") instanceof Map<?, ?>);
                    targetFacts.add("offsetof");
                    if (profile.offsetof().isPresent()) assertTrue(implemented.add("offsetof"));
                }
                if (cpp.equals(entry.get("header")) && Set.of("target_fact", "compiler_intrinsic").contains(entry.get("support_status"))) targetFacts.add((String)entry.get("name"));
                if (cpp.equals(entry.get("header")) && ("target_fact".equals(entry.get("support_status")) && targetFactAvailable(profile, entry.get("name"))
                        || "compiler_intrinsic".equals(entry.get("support_status")) && targetFactAvailable(profile, entry.get("name"))
                        || "profile_definition".equals(entry.get("support_status"))
                        || "runtime_binding".equals(entry.get("support_status")) && (Set.of("SIG_DFL", "SIG_IGN", "SIG_ERR").contains(entry.get("name")) ? !profile.signalDispositionBinding().equals("unavailable") : Set.of("stdin", "stdout", "stderr").contains(entry.get("name")) ? !profile.standardStreamsBinding().equals("unavailable") : entry.get("name").equals("MB_CUR_MAX") ? !profile.mbCurMaxBinding().equals("unavailable") : profile.cRuntime().equals("glibc"))))
                    assertTrue(implemented.add((String) entry.get("name")), "Duplicate inventory obligation: " + entry);
            }
            if (cpp.equals("climits")) assertEquals(INTEGER_LIMITS, implemented);
            if (cpp.equals("cfloat")) assertEquals(FLOATING_LIMITS, implemented);
            for (String spelling : List.of(cpp, c)) {
                var definitions = preprocess("#include <" + spelling + ">\n", profile).definitions().stream()
                        .filter(d -> d.originKind().equals("library") && d.unavailableReason() == null).toList();
                var targetDefinitions = preprocess("#include <" + spelling + ">\n", profile).definitions().stream()
                        .filter(d->targetFacts.contains(d.name())).toList();
                assertEquals(targetFacts, targetDefinitions.stream().map(CppPreprocessor.Definition::name).collect(java.util.stream.Collectors.toSet()), spelling);
                for (var definition : targetDefinitions)
                    assertEquals(targetFactAvailable(profile, definition.name()), definition.unavailableReason() == null, definition.name());
                assertEquals(implemented.size(), definitions.size(), spelling);
                assertEquals(implemented, definitions.stream().map(CppPreprocessor.Definition::name).collect(java.util.stream.Collectors.toSet()), spelling);
            }
        }
    }
    @Test void noLaterStandardMacrosOrFabricatedAbiMacros() {
        var result = preprocess("#include <climits>\n#include <cfloat>\n#include <cerrno>\n#include <cstdarg>\n"
                + "#include <csetjmp>\n#include <cstddef>\n#include <ciso646>\n", PlatformProfile.defaults());
        var names = result.definitions().stream().map(CppPreprocessor.Definition::name).toList();
        for (String name : List.of("LLONG_MAX", "ULLONG_MAX", "FLT_TRUE_MIN", "FLT_EVAL_METHOD", "DECIMAL_DIG", "FLT_DECIMAL_DIG",
                "va_copy", "and", "or", "not")) assertFalse(names.contains(name), name);
        assertTrue(result.definitions().stream().filter(d -> d.name().equals("offsetof")).allMatch(d -> d.unavailableReason() != null));
        for (String name : List.of("setjmp", "va_start", "va_arg", "va_end")) {
            var definition = result.definitions().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
            assertTrue(definition.functionLike());
            assertNotNull(definition.unavailableReason(), name);
            assertTrue(definition.rawReplacement().isBlank(), name);
        }
    }
}
