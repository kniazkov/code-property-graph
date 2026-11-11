/* Explicit bounded varargs capability and macro inventory. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.parser.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VarargsCapabilityTest {
    @TempDir Path temp;
    private static PlatformProfile selected() {
        return PlatformProfile.builder().floatingFormat("long double", "binary80")
                .varargs("gcc-clang-builtin", "x86_64-sysv-lp64-v1").build();
    }
    private Build.TranslationUnit unit(PlatformProfile p) {
        return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("varargs.cpp")), temp,
                List.of(), List.of(), "c++03").withPlatform(p);
    }
    @Test void explicitSelectionRoundTripsAndDefaultsStayUnavailable() throws Exception {
        var p = selected();
        assertEquals(p, p.toBuilder().build());
        assertEquals(p, PlatformProfile.fromSettings(p.toSettings()));
        assertTrue(PlatformProfile.defaults().varargs().isEmpty());
        assertTrue(PlatformProfile.builder().cRuntime("glibc").build().varargs().isEmpty());
        assertNotEquals(p.identityHash(), PlatformProfile.defaults().identityHash());
        var settings = new LinkedHashMap<String,Object>(p.toSettings());
        settings.put("varargs", Map.of());
        assertTrue(PlatformProfile.fromSettings(settings).varargs().isEmpty());
        Path file = temp.resolve("profile.json");
        Files.writeString(file, com.kniazkov.json.Json.serialize(p.toSettings()));
        assertEquals(p, PlatformProfile.read(file));
        for (Object bad : List.of("unavailable", Map.of("emission", "gcc-clang-builtin"),
                Map.of("emission", "gcc-clang-builtin", "abi", "other"),
                Map.of("emission", "gcc-clang-builtin", "abi", "x86_64-sysv-lp64-v1", "size", "24"))) {
            settings.put("varargs", bad);
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(settings));
        }
        assertThrows(IllegalArgumentException.class, () -> new VarargsModel("libc", "x86_64-sysv-lp64-v1"));
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder()
                .varargs("gcc-clang-builtin", "x86_64-sysv-lp64-v1").build());
        for (String type : List.of("int", "long", "pointer", "double", "long double"))
            assertThrows(IllegalArgumentException.class, () -> selected().toBuilder().sizeBits(type, 256).build());
        assertThrows(IllegalArgumentException.class, () -> selected().toBuilder().sizeType("unsigned int").build());
        for (String type : List.of("bool", "char", "short", "int", "long", "wchar_t", "pointer", "float", "double", "long double")) {
            assertThrows(IllegalArgumentException.class, () -> selected().toBuilder().alignmentBits(type, null).build());
            int alignment = selected().alignmentBits(type).orElseThrow();
            if (alignment > 8) assertThrows(IllegalArgumentException.class, () -> selected().toBuilder().alignmentBits(type, alignment / 2).build());
        }
        for (String type : List.of("float", "double", "long double"))
            assertThrows(IllegalArgumentException.class, () -> selected().toBuilder().floatingFormat(type, "unknown").build());
    }
    @Test void headerOrderInventoryProvenanceAndIsolation() {
        var p = selected();
        for (String headers : List.of("#include <cstdarg>\n#include <stdarg.h>\n", "#include <stdarg.h>\n#include <cstdarg>\n")) {
            var result = CppPreprocessor.preprocess(unit(p), headers + "va_start(ap,last); va_arg(ap,long); va_end(ap);");
            for (String name : List.of("va_start", "va_arg", "va_end")) {
                var definitions = result.definitions().stream().filter(d -> d.name().equals(name)).toList();
                assertEquals(2, definitions.size());
                assertNotEquals(definitions.getFirst().id(), definitions.getLast().id());
                assertTrue(definitions.stream().allMatch(d -> d.functionLike() && d.originKind().equals("library")
                        && d.sourceKey().contains(p.identityHash()) && d.unavailableReason() == null));
                var event = result.expansions().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
                assertEquals(definitions.getLast().id(), event.definitionId());
                assertEquals(VarargsModel.parameters(name).size(), event.arguments().size());
                assertTrue(result.tokens().stream().filter(t -> t.token().spelling().equals("__builtin_" + name))
                        .anyMatch(t -> t.expansionIds().contains(event.id()) && !t.origins().isEmpty()));
            }
        }
        for (String headers : List.of("", "#include <cstdio>\n")) {
            var pp = CppPreprocessor.preprocess(unit(p), headers + "va_start(ap,last);");
            assertTrue(pp.definitions().stream().noneMatch(d -> d.name().startsWith("va_")));
            assertTrue(pp.tokens().stream().anyMatch(t -> t.token().spelling().equals("va_start")));
        }
    }
    @Test void unavailableMacrosRemainDefinedAndDiagnoseUseAndWrongArity() {
        for (String header : List.of("cstdarg", "stdarg.h")) {
            String include = "#include <" + header + ">\n";
            var pp = CppPreprocessor.preprocess(unit(PlatformProfile.defaults()), include
                    + "#if !defined(va_start) || !defined(va_arg) || !defined(va_end)\n#error absent\n#endif\n");
            assertEquals(3, pp.definitions().stream().filter(d -> d.name().startsWith("va_")
                    && d.unavailableReason() != null).count());
            for (String expression : List.of("va_start(ap,last)", "va_arg(ap,int)", "va_end(ap)")) {
                var ex = assertThrows(ParseException.class, () -> CppPreprocessor.preprocess(unit(PlatformProfile.defaults()), include + expression));
                assertTrue(ex.getMessage().contains("varargs"));
            }
            for (String expression : List.of("va_start(ap)", "va_arg(ap)", "va_end(ap,last)"))
                assertThrows(ParseException.class, () -> CppPreprocessor.preprocess(unit(selected()), include + expression));
        }
    }
    @Test void undefAndPhysicalHeaderOverrideWork() throws Exception {
        var p = selected();
        CppPreprocessor.preprocess(unit(p), "#include <cstdarg>\n#undef va_end\n#if defined(va_end)\n#error defined\n#endif\n");
        Files.writeString(temp.resolve("stdarg.h"), "#define va_end(ap) 7\n");
        var u = new Build.TranslationUnit(unit(p).source(), temp, List.of(new Build.IncludePath("-I", temp)),
                List.of(), "c++03").withPlatform(p);
        assertEquals("7", CppPreprocessor.preprocess(u, "#include <stdarg.h>\nva_end(ap)").tokens().getFirst().token().spelling());
    }
}
