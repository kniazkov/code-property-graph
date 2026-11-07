/* Code Property Graph: C++03 target library macro definitions. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.analysis.StandardLibraryCatalogue;
import com.kniazkov.cpg.build.PlatformProfile;
import java.math.BigInteger;
import java.util.*;

/**
 * CPG-owned preprocessing definitions, not declarations or host headers.
 * Only explicit target facts are supplied here. ABI/runtime-dependent macros
 * (including errno, stdarg, setjmp, offsetof and assert) are not fabricated.
 */
final class StandardLibraryMacros {
    private StandardLibraryMacros() {}
    static Map<String, String> definitions(String header, PlatformProfile profile) {
        String canonical = StandardLibraryCatalogue.canonicalHeader(header);
        var result = new LinkedHashMap<String, String>();
        if (Set.of("cstddef", "cstdio", "cstdlib", "cstring", "ctime", "clocale", "cwchar").contains(canonical))
            result.put("NULL", profile.nullPointerConstant());
        if (canonical.equals("climits")) {
            result.put("CHAR_BIT", Integer.toString(profile.bitsPerByte()));
            result.put("MB_LEN_MAX", Integer.toString(profile.maxMultibyteLength()));
            for (var entry : List.of(Map.entry("SCHAR", "signed char"), Map.entry("CHAR", "char"), Map.entry("SHRT", "short"), Map.entry("INT", "int"), Map.entry("LONG", "long")))
                limits(result, entry.getKey(), entry.getValue(), profile, true);
            for (var entry : List.of(Map.entry("UCHAR", "unsigned char"), Map.entry("USHRT", "unsigned short"), Map.entry("UINT", "unsigned int"), Map.entry("ULONG", "unsigned long")))
                limits(result, entry.getKey(), entry.getValue(), profile, false);
        }
        if (canonical.equals("cwchar")) {
            var range = profile.integerRange("wchar_t").orElseThrow();
            // wchar_t is a distinct C++ type: its promotion is selected by range,
            // not by the rank of the implementation's underlying integer type.
            String promoted = List.of("int", "unsigned int", "long", "unsigned long").stream()
                    .filter(type -> { var candidate = profile.integerRange(type).orElseThrow();
                        return candidate.contains(range.min()) && candidate.contains(range.max()); })
                    .findFirst().orElseThrow();
            limits(result, "WCHAR", profile.wcharType(), profile, true, promoted);
        }
        if (canonical.equals("cfloat")) {
            result.put("FLT_RADIX", "2");
            result.put("FLT_ROUNDS", Integer.toString(profile.floatingRounds()));
            floating(result, "FLT", "float", "F", profile);
            floating(result, "DBL", "double", "", profile);
            floating(result, "LDBL", "long double", "L", profile);
        }
        return Collections.unmodifiableMap(result);
    }
    private static void limits(Map<String, String> result, String prefix, String type, PlatformProfile profile, boolean minimum) {
        limits(result, prefix, type, profile, minimum, type);
    }
    private static void limits(Map<String, String> result, String prefix, String type, PlatformProfile profile, boolean minimum, String promoted) {
        var range = profile.integerRange(type).orElseThrow();
        String suffix;
        if (promoted.equals("long")) suffix = "L";
        else if (promoted.equals("unsigned long")) suffix = "UL";
        else suffix = profile.integerRange("int").orElseThrow().contains(range.max()) ? "" : "U";
        result.put(prefix + "_MAX", range.max() + suffix);
        if (minimum) result.put(prefix + "_MIN", range.min().equals(BigInteger.ZERO) ? "0" + suffix
                : "(-" + range.max() + suffix + " - 1" + suffix + ")");
    }
    private record Format(int precision, int digits, int minExponent, int minDecimalExponent,
                          int maxExponent, int maxDecimalExponent, String max, String epsilon, String min) {}
    private static void floating(Map<String, String> result, String prefix, String type, String suffix, PlatformProfile profile) {
        // Decimal spellings have enough significant digits to round to the named
        // binary format; no Java floating-point arithmetic enters these values.
        Format format = switch (profile.floatingFormats().get(type)) {
            case "binary32" -> new Format(24, 6, -125, -37, 128, 38,
                    "3.40282346638528859811704183484516925e38", "1.1920928955078125e-7", "1.17549435082228750796873653722224568e-38");
            case "binary64" -> new Format(53, 15, -1021, -307, 1024, 308,
                    "1.79769313486231570814527423731704357e308", "2.220446049250313080847263336181640625e-16", "2.22507385850720138309023271733240406e-308");
            case "binary80" -> new Format(64, 18, -16381, -4931, 16384, 4932,
                    "1.18973149535723176502126385303097021e4932", "1.08420217248550443400745280086994171e-19", "3.36210314311209350626267781732175260e-4932");
            case "binary128" -> new Format(113, 33, -16381, -4931, 16384, 4932,
                    "1.18973149535723176508575932662800702e4932", "1.92592994438723585305597794258492732e-34", "3.36210314311209350626267781732175260e-4932");
            default -> throw new IllegalArgumentException("CPG <cfloat>/<float.h> requires an explicit floatingFormats." + type + " target format");
        };
        result.put(prefix + "_MANT_DIG", Integer.toString(format.precision));
        result.put(prefix + "_DIG", Integer.toString(format.digits));
        result.put(prefix + "_MIN_EXP", "(" + format.minExponent + ")");
        result.put(prefix + "_MIN_10_EXP", "(" + format.minDecimalExponent + ")");
        result.put(prefix + "_MAX_EXP", Integer.toString(format.maxExponent));
        result.put(prefix + "_MAX_10_EXP", Integer.toString(format.maxDecimalExponent));
        result.put(prefix + "_MAX", format.max + suffix);
        result.put(prefix + "_EPSILON", format.epsilon + suffix);
        result.put(prefix + "_MIN", format.min + suffix);
    }
}
