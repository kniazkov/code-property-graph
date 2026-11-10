/* CPG-owned assert selection and expansion, independent of host headers. MIT License. */
package com.kniazkov.cpg.parser;
import com.kniazkov.cpg.build.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;
class AssertionMacrosTest {
    @TempDir Path directory;
    private PlatformProfile glibc() { return PlatformProfile.builder().cRuntime("glibc").build(); }
    private Build.TranslationUnit unit(PlatformProfile p) { return new Build.TranslationUnit(new Build.PhysicalSource(directory.resolve("main.cpp")), directory, List.of(), List.of(), "c++03").withPlatform(p); }
    private CppPreprocessor.Result pp(String source, PlatformProfile p) { return CppPreprocessor.preprocess(unit(p), source); }
    private String text(CppPreprocessor.Result r) { return String.join(" ", r.tokens().stream().filter(t -> !Set.of(WHITESPACE, NEWLINE, EOF, HEADER_NAME).contains(t.token().kind())).map(t -> t.token().spelling()).toList()); }
    @Test void unknownRuntimeAllowsUnusedDefinitionAndDisabledReinclusionButRejectsInvocation() {
        var p = PlatformProfile.defaults();
        var unused = pp("#include <cassert>\nassert\n", p);
        var definition = unused.definitions().getLast();
        assertEquals("assert", definition.name()); assertTrue(definition.functionLike());
        assertNotNull(definition.unavailableReason()); assertEquals("", definition.rawReplacement());
        assertEquals("assert", text(unused));
        var error = assertThrows(ParseException.class, () -> pp("#include <cassert>\nassert(1);", p));
        assertEquals("unavailable-library-macro", error.code()); assertEquals(2, error.line());
        assertTrue(error.reason().contains("cRuntime=glibc"));
        assertEquals("( ( void ) 0 ) ;", text(pp("#include <cassert>\n#define NDEBUG 0\n#include <assert.h>\nassert(unknown);", p)));
    }
    @Test void disabledArgumentsAreNeitherPrescannedNorSemanticallyRetained() {
        var result = pp("#define NDEBUG\n#include <cassert>\n#define BAD(a,b) a+b\nassert(BAD(1)); assert(no_such_name++);", PlatformProfile.defaults());
        assertEquals("( ( void ) 0 ) ; ( ( void ) 0 ) ;", text(result));
        assertEquals(List.of("assert", "assert"), result.expansions().stream().map(CppPreprocessor.ExpansionEvent::name).toList());
        assertTrue(result.expansions().getFirst().arguments().getFirst().stream().anyMatch(f -> f.spelling().equals("BAD")));
    }
    @Test void reinclusionSelectsPresenceAndDoesNotReactUntilNextInclude() {
        var result = pp("#include <cassert>\nassert(++n);\n#define NDEBUG 0\nassert(++n);\n#include <assert.h>\nassert(++n);\n#undef NDEBUG\nassert(++n);\n#include <cassert>\nassert(++n);", glibc());
        var definitions = result.definitions().stream().filter(d -> d.name().equals("assert")).toList();
        assertEquals(3, definitions.size());
        assertNotEquals(definitions.get(0).sourceKey(), definitions.get(1).sourceKey());
        assertNotEquals(definitions.get(0).id(), definitions.get(2).id());
        var expansions = result.expansions().stream().filter(e -> e.name().equals("assert")).toList();
        assertEquals(List.of(definitions.get(0).id(), definitions.get(0).id(), definitions.get(1).id(), definitions.get(1).id(), definitions.get(2).id()), expansions.stream().map(CppPreprocessor.ExpansionEvent::definitionId).toList());
        assertEquals(3, text(result).split("__assert_fail", -1).length - 1);
        assertEquals("assert ( 1 )", text(pp("assert(1)", glibc())), "No cross-TU macro leakage");
    }
    @Test void rawArgumentStringizationAndPresumedInvocationLocations() {
        var result = pp("#include <cassert>\n#define VALUE 0\n#line 72 \"logical.cpp\"\nassert(VALUE /* retained as whitespace */ + 1);", glibc());
        String text = text(result);
        assertTrue(text.contains("static_cast < bool > ( 0 + 1 )"), text);
        assertTrue(text.contains("\"VALUE + 1\" , \"logical.cpp\" , 72 , 0"), text);
        var event = result.expansions().stream().filter(e -> e.name().equals("assert")).findFirst().orElseThrow();
        assertEquals("stringize", event.operations().getFirst().kind());
        assertTrue(event.definition().file().contains(".cpg-library-cassert-assert-enabled"));
    }
    @Test void suppliedHeadersRemainAuthoritative() throws Exception {
        Files.writeString(directory.resolve("cassert"), "#define assert(e) 17\n");
        var base = unit(PlatformProfile.defaults());
        var custom = new Build.TranslationUnit(base.source(), directory, List.of(new Build.IncludePath("-I",directory)), List.of(), "c++03");
        var result = CppPreprocessor.preprocess(custom, "#include <cassert>\nassert(no_such_name)");
        assertEquals("17", text(result));
        assertTrue(result.definitions().stream().noneMatch(d -> d.name().equals("assert") && d.originKind().equals("library")));
    }
    @Test void runtimeChoiceRoundTripsAndRejectsIncompatibleBasicWidths() {
        var p = glibc(); assertEquals(p, PlatformProfile.fromSettings(p.toSettings())); assertEquals(p,p.toBuilder().build());
        assertNotEquals(PlatformProfile.defaults().identityHash(), p.identityHash());
        assertNotEquals(unit(PlatformProfile.defaults()).variantId(), unit(p).variantId());
        assertThrows(IllegalArgumentException.class, () -> p.toBuilder().sizeBits("int",16).alignmentBits("int",16).wcharType("long").sizeBits("wchar_t",64).alignmentBits("wchar_t",64).build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().cRuntime("host").build());
        assertThrows(IllegalArgumentException.class, () -> p.toBuilder().sizeBits("pointer",16).alignmentBits("pointer",16).build());
        var legacy = new LinkedHashMap<>(p.toSettings()); legacy.remove("cRuntime");
        assertEquals("unknown", PlatformProfile.fromSettings(legacy).cRuntime());
    }
}
