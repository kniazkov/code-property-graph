/* Code Property Graph: explicit target profile regression tests. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.Language;
import com.kniazkov.cpg.parser.CppPreprocessor;
import com.kniazkov.cpg.parser.ParseException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlatformProfileTest {
    @TempDir Path temp;
    private Build.TranslationUnit unit(PlatformProfile profile) {
        return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("a.cpp")), temp,
                List.of(), List.of(), "c++03").withPlatform(profile);
    }
    private static PlatformProfile ilp32() {
        return PlatformProfile.builder().name("ilp32").sizeBits("long", 32).alignmentBits("long", 32)
                .sizeBits("pointer", 32).alignmentBits("pointer", 32).build();
    }
    private String preprocess(PlatformProfile profile, String source) {
        return CppPreprocessor.preprocess(unit(profile), source).tokens().stream()
                .map(t -> t.token().canonicalSpelling()).reduce("", String::concat);
    }
    @Test void documentsDefaultWidthsRangesAndKnownLayout() {
        var profile = PlatformProfile.defaults();
        assertEquals(32, profile.sizeBits("unsigned int").orElseThrow());
        assertEquals(64, profile.sizeBits("long int").orElseThrow());
        assertEquals(8, profile.sizeBytes("long").orElseThrow());
        assertEquals(8, profile.bitsPerByte());
        assertEquals(64, profile.alignmentBits("long").orElseThrow());
        assertEquals("sequential", profile.aggregateLayout());
        assertEquals(BigInteger.valueOf(-128), profile.integerRange("char").orElseThrow().min());
        assertEquals(new BigInteger("18446744073709551615"), profile.integerRange("unsigned long").orElseThrow().max());
        assertTrue(profile.sizeBits("void").isEmpty());
        assertTrue(profile.integerRange("double").isEmpty());
        assertEquals(profile, PlatformProfile.fromSettings(profile.toSettings()));
    }
    @Test void integerPoliciesRoundTripAndDistinguishSemanticIdentity() throws Exception {
        var baseline = PlatformProfile.defaults();
        assertEquals("int-first", baseline.enumUnderlyingType());
        assertEquals("signed", baseline.bitFieldSignedness());
        assertEquals("unknown", baseline.signedIntegerConversion());
        for (String enumeration : List.of("int-first", "unsigned-first", "unknown")) {
            for (String conversion : List.of("unknown", "twos-complement")) {
                var profile = baseline.toBuilder().enumUnderlyingType(enumeration)
                        .signedIntegerConversion(conversion).build();
                assertEquals(profile, profile.toBuilder().build());
                var restored = PlatformProfile.fromSettings(profile.toSettings());
                assertEquals(profile, restored);
                assertEquals(profile.hashCode(), restored.hashCode());
                assertEquals(profile.stableIdentity(), restored.stableIdentity());
                assertEquals(enumeration, restored.enumUnderlyingType());
                assertEquals(conversion, restored.signedIntegerConversion());
                if (!profile.equals(baseline)) {
                    assertNotEquals(baseline.stableIdentity(), profile.stableIdentity());
                    assertNotEquals(baseline.identityHash(), profile.identityHash());
                    assertNotEquals(unit(baseline).variantId(), unit(profile).variantId());
                }
            }
        }
        var legacy = new LinkedHashMap<>(baseline.toSettings());
        legacy.remove("bitFieldSignedness");
        legacy.remove("enumUnderlyingType");
        legacy.remove("signedIntegerConversion");
        assertEquals(baseline, PlatformProfile.fromSettings(legacy));
        Path settings = temp.resolve("integer-policies.json");
        Files.writeString(settings, """
                {"inherits":"default","enumUnderlyingType":"unsigned-first","signedIntegerConversion":"twos-complement"}
                """);
        assertEquals(baseline.toBuilder().enumUnderlyingType("unsigned-first")
                .signedIntegerConversion("twos-complement").build(), PlatformProfile.read(settings));
    }
    @Test void bitFieldSignednessRoundTripsAndChangesIdentity() throws Exception {
        var baseline = PlatformProfile.defaults();
        for (String policy : List.of("signed", "unsigned", "unknown")) {
            var profile = baseline.toBuilder().bitFieldSignedness(policy).build();
            assertEquals(policy, profile.bitFieldSignedness());
            assertEquals(profile, profile.toBuilder().build());
            var restored = PlatformProfile.fromSettings(profile.toSettings());
            assertEquals(profile, restored);
            assertEquals(profile.hashCode(), restored.hashCode());
            assertEquals(profile.stableIdentity(), restored.stableIdentity());
            if (!policy.equals("signed")) {
                assertNotEquals(baseline.identityHash(), profile.identityHash());
                assertNotEquals(unit(baseline).variantId(), unit(profile).variantId());
            }
            Path settings = temp.resolve("bit-field-" + policy + ".json");
            Files.writeString(settings, "{\"inherits\":\"default\",\"bitFieldSignedness\":\"" + policy + "\"}");
            assertEquals(profile, PlatformProfile.read(settings));
        }
    }
    @Test void validatesIntegerPolicies() {
        for (String field : List.of("enumUnderlyingType", "signedIntegerConversion", "bitFieldSignedness")) {
            for (Object bad : List.of("guess", "", "INT-FIRST", 1, true)) {
                assertThrows(IllegalArgumentException.class, () ->
                        PlatformProfile.fromSettings(Map.of("inherits", "default", field, bad)));
            }
            var nullSetting = new LinkedHashMap<String, Object>();
            nullSetting.put("inherits", "default"); nullSetting.put(field, null);
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(nullSetting));
        }
        for (String bad : Arrays.asList(null, "", "guess")) {
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().bitFieldSignedness(bad).build());
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().enumUnderlyingType(bad).build());
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().signedIntegerConversion(bad).build());
        }
    }
    @Test void validatesSettingsAndDistinguishesNullFromInheritedAlignment() {
        var inherited = PlatformProfile.fromSettings(Map.of("inherits", "default"));
        var unknown = new LinkedHashMap<String, Object>();
        unknown.put("inherits", "default");
        var aligns = new LinkedHashMap<String, Object>(); aligns.put("long", null);
        unknown.put("alignments", aligns); unknown.put("aggregateLayout", "unknown");
        var explicitUnknown = PlatformProfile.fromSettings(unknown);
        assertTrue(inherited.alignmentBits("long").isPresent());
        assertTrue(explicitUnknown.alignmentBits("long").isEmpty());
        assertEquals("unknown", explicitUnknown.aggregateLayout());
        assertEquals(explicitUnknown, PlatformProfile.fromSettings(explicitUnknown.toSettings()));
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(Map.of("inherits", "none")));
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(Map.of("inherits", "default", "intBits", 16)));
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().sizeBits("int", 31).build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().alignmentBits("long", 24).build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().wcharType("short").build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().executionEncoding("unknown charset").build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().executionEncoding("UTF-16").build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().aggregateLayout("guess").build());
        assertThrows(IllegalArgumentException.class, () -> new Cpg.Options(Language.JAVA, Cpg.Stage.PARSED).withPlatform(inherited));
    }
    @Test void targetBytesAreNotAssumedToHaveEightBits() {
        var profile = PlatformProfile.builder().bitsPerByte(16)
                .sizeBits("bool", 16).alignmentBits("bool", 16)
                .sizeBits("char", 16).alignmentBits("char", 16).build();
        assertEquals(4, profile.sizeBytes("long").orElseThrow());
        assertEquals(1, profile.sizeBytes("char").orElseThrow());
        assertEquals("16", profile.predefinedMacros().get("__CHAR_BIT__"));
        assertTrue(preprocess(profile, "#if __CHAR_BIT__ == 16 && __SIZEOF_LONG__ == 4\nactive\n#endif\n").contains("active"));
    }
    @Test void profilesChangeVariantsButDisplayNamesDoNot() {
        var baseline = unit(PlatformProfile.defaults());
        assertNotEquals(baseline.variantId(), unit(ilp32()).variantId());
        assertEquals(baseline.variantId(), unit(PlatformProfile.defaults().toBuilder().name("display label").build()).variantId());
        assertNotEquals(baseline.variantId(), unit(PlatformProfile.defaults().toBuilder().charSigned(false).build()).variantId());
        assertNotEquals(baseline.variantId(), unit(PlatformProfile.defaults().toBuilder().alignmentBits("int", null).build()).variantId());
        assertEquals(2, new Build(List.of(baseline, unit(ilp32()))).units().size());
    }
    @Test void macrosAndConditionalArithmeticFollowProfiles() {
        assertTrue(preprocess(ilp32(), "#if __SIZEOF_LONG__ == 4 && __SIZEOF_POINTER__ == 4 && __LONG_MAX__ == 2147483647L\nactive\n#endif\n").contains("active"));
        assertTrue(preprocess(ilp32(), "#if 0xffffffffU + 1 == 0 && (-2 >> 1) == -1 && (1 ? -1 : 0U) > 0\nactive\n#endif\n").contains("active"));
        assertThrows(ParseException.class, () -> preprocess(ilp32(), "#if 2147483647L + 1\n#endif\n"));
        assertThrows(ParseException.class, () -> preprocess(ilp32(), "#if 1 << 32\n#endif\n"));
        assertTrue(preprocess(ilp32(), "#if 1 || (1 << 32)\nactive\n#endif\n").contains("active"));
        assertThrows(ParseException.class, () -> preprocess(ilp32(), "#define __SIZEOF_LONG__ 8\n"));
        var unsigned = ilp32().toBuilder().charSigned(false).wcharType("unsigned int").build();
        assertTrue(preprocess(unsigned, "#if defined(__CHAR_UNSIGNED__) && __WCHAR_MAX__ == 0xffffffffU && L'\\xffffffff' > 0\nactive\n#endif\n").contains("active"));
        assertFalse(preprocess(PlatformProfile.defaults(), "#ifdef __CHAR_UNSIGNED__\nactive\n#endif\n").contains("active"));
        var wide = PlatformProfile.builder().sizeBits("long", 128).alignmentBits("long", 128)
                .sizeBits("long long", 128).alignmentBits("long long", 128).build();
        assertTrue(preprocess(wide, "#if (0xffffffffffffffffU + 1) == (1U << 64)\nactive\n#endif\n").contains("active"));
    }
    @Test void sourceAndExecutionEncodingsAreAppliedToRootsAndHeaders() throws Exception {
        var profile = PlatformProfile.builder().sourceEncoding("ISO-8859-1").executionEncoding("ISO-8859-1").build();
        Files.writeString(temp.resolve("a.cpp"), "#include \"h.hpp\"\n#if 'é' == 233\nint main() { return value; }\n#endif\n", StandardCharsets.ISO_8859_1);
        Files.writeString(temp.resolve("h.hpp"), "#if 'é' == 233\nint value = 7;\n#endif\n", StandardCharsets.ISO_8859_1);
        var project = Cpg.read(new Build(List.of(unit(profile))), new Cpg.Options(Language.CPP, Cpg.Stage.PARSED));
        assertFalse(project.hasErrors()); assertEquals(1, project.files().size());
        Path settings = temp.resolve("latin.json"), out = temp.resolve("latin-output");
        Files.writeString(settings, "{\"inherits\":\"default\",\"sourceEncoding\":\"ISO-8859-1\",\"executionEncoding\":\"ISO-8859-1\"}");
        Main.run("--from", "CPP", "--input", temp.toString(), "--stage", "PARSED", "--platform-profile", settings.toString(), "--out", out.toString());
        assertEquals(Files.readString(temp.resolve("h.hpp"), StandardCharsets.ISO_8859_1), Files.readString(out.resolve("h.hpp"), StandardCharsets.ISO_8859_1));
        assertFalse(Cpg.read(out, new Cpg.Options(Language.CPP, Cpg.Stage.PARSED).withPlatform(profile)).hasErrors());
        assertTrue(preprocess(profile, "#if 'é' == 233\nactive\n#endif\n").contains("active"));
        assertThrows(ParseException.class, () -> preprocess(profile, "#if 'α'\n#endif\n"));
        var utf16 = PlatformProfile.builder().sizeBits("wchar_t", 16).alignmentBits("wchar_t", 16)
                .wcharType("unsigned short").wideExecutionEncoding("UTF-16").build();
        assertTrue(preprocess(utf16, "#if L'\\U0001f600' == 0xde00\nactive\n#endif\n").contains("active"));
    }
    @Test void compilationDatabaseSupportsIndependentProfilesAndCliOverride() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "int main() { return __SIZEOF_LONG__; }");
        Path database = temp.resolve("compile_commands.json");
        Files.writeString(database, """
                [{"directory":".","file":"a.cpp","arguments":["c++","-c","a.cpp"]},
                 {"directory":".","file":"a.cpp","arguments":["c++","-c","a.cpp"],
                  "platformProfile":{"inherits":"default","name":"ilp32","sizes":{"long":32,"pointer":32},"alignments":{"long":32,"pointer":32}}}]
                """);
        var build = CompilationDatabase.read(database);
        assertEquals(2, build.units().size());
        assertEquals(32, build.units().get(1).platform().sizeBits("long").orElseThrow());
        var composition = new Build.Composition("selected", List.of(build.units().get(1).variantId()));
        var project = Cpg.read(new Build(build.units(), composition), new Cpg.Options(Language.CPP, Cpg.Stage.PARSED).withPlatform(PlatformProfile.defaults()));
        assertEquals(1, project.build().orElseThrow().units().size());
        assertEquals(1, project.files().size());
        assertEquals(PlatformProfile.defaults(), project.build().orElseThrow().units().getFirst().platform());
        Path profile = temp.resolve("profile.json");
        Files.writeString(profile, "{\"inherits\":\"default\",\"sizes\":{\"long\":32},\"alignments\":{\"long\":32}}");
        Path output = temp.resolve("out.json");
        Main.run("--from", "CPP", "--input", temp.toString(), "--stage", "PARSED", "--platform-profile", profile.toString(), "--json", output.toString());
        assertTrue(Files.readString(output).contains("platform"));
    }
}
