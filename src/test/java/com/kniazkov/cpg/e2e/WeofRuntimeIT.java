/* WEOF and wint_t checked against independent strict C++03 native oracles. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.LibraryDeclarations;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.analysis.TypeSemantics;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.CppPreprocessor;
import com.kniazkov.cpg.parser.ParseException;
import com.kniazkov.cpg.parser.PreprocessingToken;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WeofRuntimeIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final List<String> HEADERS = List.of("cwchar", "cwctype", "wchar.h", "wctype.h");
    private static final List<List<String>> HEADER_PAIRS = List.of(
            List.of("cwchar", "cwctype"), List.of("cwctype", "cwchar"),
            List.of("wchar.h", "wctype.h"), List.of("wctype.h", "wchar.h"),
            List.of("cwchar", "wctype.h"), List.of("wctype.h", "cwchar"),
            List.of("wchar.h", "cwctype"), List.of("cwctype", "wchar.h"));
    // Explicit test target, never an inference made by production from cRuntime or host headers.
    private static final PlatformProfile GLIBC = PlatformProfile.builder().cRuntime("glibc")
            .wideInteger("unsigned int", "4294967295", "nonnegative-wchar").build();
    private static final PlatformProfile SIGNED = PlatformProfile.builder()
            .wideInteger("int", "-1", "nonnegative-wchar").build();
    private static final String OVERLOADS = """
            int exact_type(int value) { return 1; }
            int exact_type(long value) { return 2; }
            int exact_type(unsigned int value) { return 3; }
            int exact_type(unsigned long value) { return 4; }
            int exact_type(wchar_t value) { return 5; }
            """;

    @Test void observedNativeWeofSurvivesApiCliCopyEditsReparsingAndTransforms() throws Exception {
        assertEquals(probe("g++"), probe("clang++"),
                "Independent real-header probes must describe the same selected libc and scalar ABI");
        assertEquals(GLIBC, PlatformProfile.fromSettings(GLIBC.toSettings()));
        assertEquals(GLIBC, GLIBC.toBuilder().build());
        assertTrue(GLIBC.wideInteger().isPresent());
        assertEquals(Map.of("wintType", "unsigned int", "weof", "4294967295",
                "characterDomain", "nonnegative-wchar"), GLIBC.toSettings().get("wideInteger"));
        for (var headers : HEADER_PAIRS) {
            Path work = Files.createTempDirectory(temp, "wide-");
            var cli = new CliSandbox(work).source("main.cpp", runtimeProgram(headers));
            var options = options(GLIBC, Cpg.Stage.RESOLVED);
            var project = Cpg.read(cli.input, options);
            verifyRuntime(project, false);
            var copy = project.edit().build();
            verifyRuntime(copy, false);
            Path settings = work.resolve("profile.json");
            Files.writeString(settings, Json.serialize(GLIBC.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),
                    Json.parse(run.work().resolve("snapshot.json").toFile()));
            Path copied = writeSource(work, "copied", Cpg.sources(copy, Language.CPP).get("main.cpp"));
            for (Path source : List.of(cli.input.resolve("main.cpp"), copied,
                    run.work().resolve("generated/main.cpp"))) {
                verifyRuntime(Cpg.read(source.getParent(), options), false);
                nativeRuntime(source, false);
                nativeRuntime(materialize(source, GLIBC, true), false);
            }
            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input, options(GLIBC, stage));
                verifyRuntime(transformed, false);
                verifyRuntime(transformed.edit().build(), false);
                Path source = writeSource(work, stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verifyRuntime(Cpg.read(source.getParent(), options), false);
                nativeRuntime(source, false);
                nativeRuntime(materialize(source, GLIBC, true), false);
            }
            var editor = new ProjectEditor(project);
            editor.replace(variable(project, "editable").initializer().orElseThrow(), IntegerLiteral.create()
                    .setValue(new BigInteger("4294967294")).setCppType("unsigned int").build());
            var rawEdited = editor.build();
            var changedInitializer = assertInstanceOf(IntegerLiteral.class,
                    ExpressionSyntax.written(variable(rawEdited, "editable").initializer().orElseThrow()));
            assertEquals(new BigInteger("4294967294"), changedInitializer.bigValue());
            // Editing invalidates template semantic types, so explicitly recompute before checking
            // the exact char_traits<wchar_t>::int_type and ordinary wide declaration identities.
            var edited = NameResolver.resolve(rawEdited);
            verifyRuntime(edited, true);
            verifyRuntime(edited.edit().build(), true);
            Path source = writeSource(work, "edited", Cpg.sources(edited, Language.CPP).get("main.cpp"));
            verifyRuntime(Cpg.read(source.getParent(), options), true);
            // The output witnesses an actual changed WEOF-origin initializer, not merely a no-op copy.
            nativeRuntime(source, true);
            nativeRuntime(materialize(source, GLIBC, true), true);
        }
    }

    @Test void independentSignedAndUnsignedConstantsUseActualCpgReplacementsWithoutNativeAbiClaims() throws Exception {
        for (boolean signed : List.of(false, true)) {
            var profile = signed ? SIGNED : GLIBC;
            String expectedType = signed ? "int" : "unsigned int";
            String expectedValue = signed ? "(-1)" : "4294967295U";
            int expectedKind = signed ? 1 : 3;
            for (String header : HEADERS) {
                Path work = Files.createTempDirectory(temp, "constant-");
                var cli = new CliSandbox(work).source("main.cpp", "#include <" + header + ">\n"
                        + constantChecks(signed) + OVERLOADS + "int main(){ return exact_type(WEOF) != "
                        + expectedKind + " || WEOF != " + expectedValue + "; }\n");
                var project = Cpg.read(cli.input, options(profile, Cpg.Stage.RESOLVED));
                assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
                assertEquals(expectedType, callsTo(project, "exact_type").getFirst().target().orElseThrow()
                        .parameters().getFirst().type().orElseThrow().canonicalIdentity());
                var pp = project.files().getFirst().provenance().orElseThrow().unit();
                var definitions = pp.definitions().stream().filter(d -> d.name().equals("WEOF")).toList();
                assertEquals(1, definitions.size(), header);
                String replacement = definitions.getFirst().rawReplacement();
                assertNull(definitions.getFirst().unavailableReason());
                assertEquals("library", definitions.getFirst().originKind());
                assertTrue(project.externalInput(definitions.getFirst().sourceKey()).isPresent());
                // No native wide headers or wint_t declarations are retained for a synthetic target.
                // Both compilers must execute precisely CPG's replacement, against independent constants.
                Path expanded = materialize(cli.input.resolve("main.cpp"), profile, false);
                String nativeChecks = "#define OBSERVED_WEOF " + replacement + "\n"
                        + "#if OBSERVED_WEOF != " + expectedValue + "\n#error wrong native preprocessor value\n#endif\n"
                        + (signed ? "#if OBSERVED_WEOF + 2 != 1\n#error wrong signed arithmetic\n#endif\n"
                                : "#if OBSERVED_WEOF <= 2147483647 || OBSERVED_WEOF - 4294967294U != 1\n#error wrong unsigned arithmetic\n#endif\n")
                        + "#undef OBSERVED_WEOF\n";
                Files.writeString(expanded, nativeChecks + Files.readString(expanded));
                nativeRun(expanded, "");
                if (!signed) nativeRun(cli.input.resolve("main.cpp"), "");
                var copied = project.edit().build();
                Path copiedSource = writeSource(work, "copied", Cpg.sources(copied, Language.CPP).get("main.cpp"));
                assertFalse(Cpg.read(copiedSource.getParent(), options(profile, Cpg.Stage.RESOLVED)).hasErrors());
                nativeRun(materialize(copiedSource, profile, false), "");
            }
        }
    }

    @Test void nativeBoundaryOraclesCheckAllSupportedSuffixesAndSignedMinima() throws Exception {
        record Boundary(String type, String decimal, String oracle, int overload) {}
        // These independent hexadecimal/bounded expressions are test oracles, not production rendering.
        var cases = List.of(
                new Boundary("int", "-1", "-1", 1),
                new Boundary("int", "-2147483648", "(-0x7fffffff - 1)", 1),
                new Boundary("unsigned int", "4294967295", "0xffffffffU", 3),
                new Boundary("long", "-1", "-1L", 2),
                new Boundary("long", "-9223372036854775808", "(-0x7fffffffffffffffL - 1L)", 2),
                new Boundary("long", "2147483648", "0x80000000L", 2),
                new Boundary("unsigned long", "18446744073709551615", "0xffffffffffffffffUL", 4));
        for (var boundary : cases) {
            var profile = PlatformProfile.builder()
                    .wideInteger(boundary.type(), boundary.decimal(), "nonnegative-wchar").build();
            Path work = Files.createTempDirectory(temp, "boundary-");
            var cli = new CliSandbox(work).source("main.cpp", "#include <cwchar>\n"
                    + "#if WEOF != " + boundary.oracle() + "\n#error CPG boundary value mismatch\n#endif\n"
                    + OVERLOADS + "int main(){ return exact_type(WEOF) != " + boundary.overload()
                    + " || WEOF != " + boundary.oracle() + "; }\n");
            var project = Cpg.read(cli.input, options(profile, Cpg.Stage.RESOLVED));
            assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
            assertEquals(boundary.type(), callsTo(project, "exact_type").getFirst().target().orElseThrow()
                    .parameters().getFirst().type().orElseThrow().canonicalIdentity());
            var definitions = project.files().getFirst().provenance().orElseThrow().unit().definitions().stream()
                    .filter(definition -> definition.name().equals("WEOF")).toList();
            assertEquals(1, definitions.size());
            assertNull(definitions.getFirst().unavailableReason());
            String replacement = definitions.getFirst().rawReplacement();
            Path expanded = materialize(cli.input.resolve("main.cpp"), profile, false);
            // Exercise both actual CPG body tokens and the native preprocessor's handling of the
            // exact CPG definition. There are no native headers, wint_t declarations or libc calls.
            String nativeChecks = "#define OBSERVED_WEOF " + replacement + "\n"
                    + "#if OBSERVED_WEOF != " + boundary.oracle()
                    + "\n#error Native boundary value mismatch\n#endif\n#undef OBSERVED_WEOF\n";
            Files.writeString(expanded, nativeChecks + Files.readString(expanded));
            nativeRun(expanded, "");
        }
    }

    @Test void absentAndEmptyModelsKeepOpaqueTypesAndContextualApiCliErrorJson() throws Exception {
        var empty = PlatformProfile.fromSettings(Map.of("inherits", "default", "cRuntime", "glibc", "wideInteger", Map.of()));
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().cRuntime("glibc").build(), empty)) {
            assertTrue(profile.wideInteger().isEmpty());
            for (String header : HEADERS) {
                Path work = Files.createTempDirectory(temp, "unavailable-");
                String namespace = header.endsWith(".h") ? "" : "std::";
                String include = "#include <" + header + ">\n";
                var cli = new CliSandbox(work).source("main.cpp", include
                        + "#if !defined(WEOF)\n#error Missing required macro\n#endif\n"
                        + namespace + "wint_t value;\nint main(){return 0;}\n");
                var options = options(profile, Cpg.Stage.RESOLVED);
                var unused = Cpg.read(cli.input, options);
                assertFalse(unused.hasErrors(), () -> unused.parseProblems() + " / " + unused.semanticDiagnostics());
                assertInstanceOf(OpaqueLibraryType.class, variable(unused, "value").type().orElseThrow().canonicalType());
                assertEquals(variable(unused, "value").type().orElseThrow().canonicalIdentity(),
                        variable(unused.edit().build(), "value").type().orElseThrow().canonicalIdentity());
                var definition = unused.files().getFirst().provenance().orElseThrow().unit().definitions().stream()
                        .filter(d -> d.name().equals("WEOF")).findFirst().orElseThrow();
                assertNotNull(definition.unavailableReason());
                Path settings = work.resolve("profile.json");
                Files.writeString(settings, Json.serialize(profile.toSettings()));
                cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                        "--platform-profile", settings.toString()).expectStatus(0);
                for (String use : List.of("int main(){return WEOF == 0;}\n",
                        "#if WEOF == 0\n#endif\nint main(){return 0;}\n")) {
                    Files.writeString(cli.input.resolve("main.cpp"), include + use);
                    String error = assertThrows(ParseException.class, () -> Cpg.read(cli.input, options)).getMessage();
                    assertTrue(error.contains("WEOF"), error);
                    assertTrue(error.contains("wideInteger"), error);
                    var failed = Cpg.readRecovering(cli.input, options);
                    assertTrue(failed.hasErrors());
                    assertErrorJson(ProjectJson.encode(failed).toText("  "));
                    assertErrorJson(ProjectJson.encode(failed.edit().build()).toText("  "));
                    assertThrows(IllegalArgumentException.class, () -> Cpg.sources(failed, Language.CPP));
                    var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                            "--platform-profile", settings.toString()).expectStatus(1);
                    String json = Files.readString(run.work().resolve("snapshot.json"));
                    Json.parse(json);
                    assertErrorJson(json);
                    assertFalse(Files.exists(run.work().resolve("generated")));
                }
            }
        }
    }

    private static Cpg.Options options(PlatformProfile profile, Cpg.Stage stage) {
        return new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(profile);
    }

    private static String constantChecks(boolean signed) {
        return "#if !defined(WEOF)\n#error Missing WEOF\n#endif\n" + (signed ? """
                #if WEOF != -1 || WEOF + 2 != 1 || WEOF >= 0
                #error Wrong signed WEOF
                #endif
                """ : """
                #if WEOF != 4294967295U || WEOF <= 2147483647 || WEOF - 4294967294U != 1
                #error Wrong unsigned WEOF
                #endif
                """);
    }

    private static String runtimeProgram(List<String> headers) {
        String includes = headers.stream().map(header -> "#include <" + header + ">\n").reduce("", String::concat);
        String io = headers.contains("cwchar") ? "std::" : "";
        String ctype = headers.contains("cwctype") ? "std::" : "";
        return includes + "#include <cstdio>\n#include <string>\n" + constantChecks(false) + OVERLOADS + """
                unsigned int editable = WEOF;
                int main() {
                    %1$swint_t sentinel = WEOF;
                    std::char_traits<wchar_t>::int_type trait = WEOF;
                    if (sentinel != 4294967295U || trait != 4294967295U) return 1;
                    if (exact_type(WEOF) != 3 || exact_type(trait) != 3) return 2;
                    if (%2$siswalpha(L'A') == 0 || %2$siswalpha(WEOF) != 0) return 3;
                    if (%2$stowlower(L'A') != L'a' || %2$stowlower(WEOF) != WEOF) return 4;
                    if (%1$sbtowc('A') != L'A' || %1$swctob(WEOF) != -1) return 5;
                    std::FILE *stream = std::tmpfile();
                    if (!stream) return 6;
                    int failed = 0;
                    if (%1$sfwide(stream, 1) <= 0) failed = 1;
                    %1$swint_t observed = %1$sfgetwc(stream);
                    if (observed != WEOF || !std::feof(stream) || std::ferror(stream)) failed = 1;
                    if (std::fclose(stream) != 0) failed = 1;
                    if (failed) return 7;
                    std::printf("EDITABLE %%u TYPE %%d TRAITS %%d\\n", editable, exact_type(WEOF), exact_type(trait));
                    return 0;
                }
                """.formatted(io, ctype);
    }

    private static <T extends Node> List<T> nodes(Project project, Class<T> type) {
        var result = new ArrayList<T>();
        project.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }
        });
        return result;
    }

    private static List<CallExpression> callsTo(Project project, String name) {
        return nodes(project, CallExpression.class).stream()
                .filter(call -> call.target().map(target -> target.name().equals(name)).orElse(false)).toList();
    }

    private static VariableDeclaration variable(Project project, String name) {
        return nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static void verifyRuntime(Project project, boolean edited) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
        for (String name : List.of("sentinel", "trait", "observed", "editable")) {
            assertEquals("unsigned int", variable(project, name).type().orElseThrow().canonicalIdentity(), name);
        }
        var editable = assertInstanceOf(IntegerLiteral.class,
                ExpressionSyntax.written(variable(project, "editable").initializer().orElseThrow()));
        assertEquals(new BigInteger(edited ? "4294967294" : "4294967295"), editable.bigValue());
        var exactCalls = callsTo(project, "exact_type");
        assertEquals(4, exactCalls.size());
        for (var call : exactCalls) assertEquals("unsigned int", call.target().orElseThrow()
                .parameters().getFirst().type().orElseThrow().canonicalIdentity());
        for (String name : List.of("iswalpha", "towlower", "btowc", "wctob", "fwide", "fgetwc")) {
            var calls = callsTo(project, name);
            assertFalse(calls.isEmpty(), name + " must retain its ordinary library declaration");
            for (var call : calls) assertTrue(LibraryDeclarations.isLibrary(call.target().orElseThrow()));
        }
        for (String name : List.of("iswalpha", "towlower", "wctob")) {
            assertEquals("unsigned int", callsTo(project, name).getFirst().target().orElseThrow()
                    .parameters().getFirst().type().orElseThrow().canonicalIdentity(), name);
        }
        for (String name : List.of("towlower", "btowc", "fgetwc")) {
            var call = callsTo(project, name).getFirst();
            assertEquals("unsigned int", call.target().orElseThrow().returnType().orElseThrow().canonicalIdentity(), name);
            assertEquals("unsigned int", TypeSemantics.expressionType(call, GLIBC).orElseThrow().canonicalIdentity(), name);
        }
        var pp = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(GLIBC, pp.configuration().platform());
        var definitions = pp.definitions().stream().filter(d -> d.name().equals("WEOF")).toList();
        assertTrue(definitions.size() >= 2, "Each supplying wide header needs its own provenance");
        assertTrue(definitions.stream().map(d -> d.sourceKey()).distinct().count() >= 2);
        for (var definition : definitions) {
            assertFalse(definition.functionLike());
            assertNull(definition.unavailableReason());
            assertEquals("library", definition.originKind());
            assertTrue(project.externalInput(definition.sourceKey()).isPresent());
        }
        assertTrue(pp.expansions().stream().anyMatch(event -> event.name().equals("WEOF")));
    }

    private static void assertErrorJson(String json) {
        for (String expected : List.of("unavailable-library-macro", "unavailableReason", "WEOF", "wideInteger", "main.cpp"))
            assertTrue(json.contains(expected), json);
    }

    private Path writeSource(Path work, String directory, String source) throws Exception {
        Path file = Files.createDirectory(work.resolve(directory)).resolve("main.cpp");
        Files.writeString(file, source);
        return file;
    }

    private Path materialize(Path source, PlatformProfile profile, boolean retainNativeHeaders) throws Exception {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(source), source.getParent(), List.of(),
                List.of(), "c++03").withPlatform(profile);
        var pp = CppPreprocessor.preprocess(unit, Files.readString(source));
        assertTrue(pp.expansions().stream().anyMatch(event -> event.name().equals("WEOF")));
        assertFalse(pp.tokens().stream().anyMatch(token -> token.token().spelling().equals("WEOF")),
                "Every native-tested WEOF use must come from CPG, never from the host macro");
        var text = new StringBuilder();
        boolean bodyStarted = false;
        for (var expanded : pp.tokens()) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                assertFalse(bodyStarted, "Fixture headers must be a prefix; never hoist a later include");
                if (retainNativeHeaders) text.append("#include ").append(token.spelling()).append('\n');
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                if (!bodyStarted) {
                    // Complete genuine host headers first: <string> itself needs the native WEOF.
                    // After this boundary every tested body token is CPG's materialized expansion.
                    text.append("#undef WEOF\n");
                    bodyStarted = true;
                }
                assertNotEquals("WEOF", token.spelling(), "The host must not expand a tested body use");
                text.append(token.spelling()).append(' ');
            }
        }
        assertTrue(bodyStarted, "A materialized test must contain a non-header body");
        if (!retainNativeHeaders) assertFalse(text.toString().contains("#include"));
        Path file = Files.createTempDirectory(temp, "materialized-").resolve("main.cpp");
        Files.writeString(file, text.append('\n'));
        return file;
    }

    private void nativeRuntime(Path source, boolean edited) throws Exception {
        nativeRun(source, "EDITABLE " + (edited ? "4294967294" : "4294967295") + " TYPE 3 TRAITS 3\n");
    }

    private void nativeRun(Path source, String expected) throws Exception {
        for (String compiler : COMPILERS) {
            Path work = Files.createTempDirectory(temp, "native-");
            Path binary = work.resolve("weof-check");
            success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
            var result = success(List.of(binary.toString()), work);
            assertEquals(expected, result.out(), compiler + " / " + source);
            assertEquals("", result.err(), compiler + " / " + source);
        }
    }

    private String probe(String compiler) throws Exception {
        Path work = Files.createTempDirectory(temp, "oracle-");
        Path source = work.resolve("probe.cpp"), binary = work.resolve("probe");
        Files.writeString(source, """
                #include <cwchar>
                #include <cwctype>
                #include <cstdio>
                #include <climits>
                #include <string>
                #ifndef __GLIBC__
                #error This independent oracle requires the explicitly selected glibc runtime
                #endif
                #ifndef __x86_64__
                #error This independent oracle requires the explicitly selected x86_64 ABI
                #endif
                #include <gnu/libc-version.h>
                #if !defined(WEOF) || WEOF != 4294967295U
                #error Unexpected native WEOF
                #endif
                """ + OVERLOADS + """
                int main() {
                    std::printf("WIDTHS %d %d %d %d\\n", CHAR_BIT, int(sizeof(int)*CHAR_BIT), int(sizeof(long)*CHAR_BIT), int(sizeof(void*)*CHAR_BIT));
                    std::printf("WINT_TYPE %d\\nWEOF_TYPE %d\\nWEOF_VALUE %lu\\n", exact_type(std::wint_t(0)), exact_type(WEOF), static_cast<unsigned long>(WEOF));
                    std::printf("WCHAR_MODEL %d %d %d\\n", exact_type(wchar_t(0)), int(sizeof(wchar_t)*CHAR_BIT), int(wchar_t(-1) < 0));
                    std::printf("TRAITS_TYPE %d\\n", exact_type(std::char_traits<wchar_t>::int_type(0)));
                    std::printf("GLIBC_HEADERS %d.%d\\nGLIBC_RUNTIME %s\\n", __GLIBC__, __GLIBC_MINOR__, gnu_get_libc_version());
                    std::FILE *stream = std::tmpfile();
                    if (!stream) return 1;
                    int failed = 0;
                    if (std::fwide(stream, 1) <= 0) failed = 1;
                    std::wint_t observed = std::fgetwc(stream);
                    int at_eof = observed == WEOF && std::feof(stream) != 0 && std::ferror(stream) == 0;
                    if (std::fclose(stream) != 0) failed = 1;
                    int valid = std::iswalpha(L'A') != 0 && std::towlower(L'A') == L'a';
                    int sentinel = std::iswalpha(WEOF) == 0 && std::towlower(WEOF) == WEOF;
                    std::printf("RUNTIME_CHECKS %d %d %d\\n", at_eof, valid, sentinel);
                    return failed || !at_eof || !valid || !sentinel;
                }
                """);
        success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
        var run = success(List.of(binary.toString()), work);
        String observed = run.out();
        assertEquals("", run.err());
        assertEquals("8 32 64 64", observation(observed, "WIDTHS"));
        assertEquals("3", observation(observed, "WINT_TYPE"), "wint_t must be exactly unsigned int");
        assertEquals("3", observation(observed, "WEOF_TYPE"), "An equal-width different type is insufficient");
        assertEquals("4294967295", observation(observed, "WEOF_VALUE"));
        assertEquals("5 32 1", observation(observed, "WCHAR_MODEL"), "wchar_t must be distinct and signed 32-bit");
        assertEquals("3", observation(observed, "TRAITS_TYPE"));
        assertEquals("1 1 1", observation(observed, "RUNTIME_CHECKS"));
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("compiler", compiler);
        metadata.put("version", success(List.of(compiler, "--version"), work).out());
        metadata.put("target", success(List.of(compiler, "-dumpmachine"), work).out().trim());
        metadata.put("headerAndRuntimeObservations", observed);
        metadata.put("platform", GLIBC.toSettings());
        metadata.put("probeSourceSha256", hash(source));
        metadata.put("probeBinarySha256", hash(binary));
        metadata.put("includeSearch", success(List.of(compiler, "-std=c++03", "-E", "-v", source.toString(), "-o", "/dev/null"), work).err());
        Path macros = work.resolve("macros.cpp");
        Files.writeString(macros, "#include <cwchar>\n#include <cwctype>\n");
        var definitions = success(List.of(compiler, "-std=c++03", "-dM", "-E", macros.toString()), work).out()
                .lines().filter(line -> line.startsWith("#define WEOF ")).toList();
        assertEquals(1, definitions.size());
        metadata.put("nativeWeofMacroDefinitions", definitions);
        String headers = success(List.of(compiler, "-std=c++03", "-H", "-E", source.toString(), "-o", "/dev/null"), work).err();
        var hashes = new TreeMap<String, String>();
        for (String line : headers.split("\n")) {
            if (line.matches("\\.+ .*")) {
                Path header = Path.of(line.replaceFirst("^\\.+ ", ""));
                if (Files.isRegularFile(header)) hashes.put(header.toString(), hash(header));
            }
        }
        for (String header : List.of("/wchar.h", "/wctype.h", "/bits/types/wint_t.h"))
            assertTrue(hashes.keySet().stream().anyMatch(path -> path.endsWith(header)), headers);
        metadata.put("selectedHeaderSha256", hashes);
        String dependencies = success(List.of("ldd", binary.toString()), work).out();
        metadata.put("runtimeDependencies", dependencies);
        var runtimeHashes = new TreeMap<String, String>();
        for (String line : dependencies.split("\n")) {
            if (line.trim().startsWith("libc.so.6 => ")) {
                Path runtime = Path.of(line.trim().substring("libc.so.6 => ".length()).split("\\s+")[0]).toRealPath();
                runtimeHashes.put(runtime.toString(), hash(runtime));
            }
        }
        assertFalse(runtimeHashes.isEmpty(), "Record the actual dynamically linked libc: " + dependencies);
        metadata.put("runtimeLibrarySha256", runtimeHashes);
        Path artifacts = Files.createDirectories(Path.of("target/weof-runtime-oracle"));
        String prefix = compiler.equals("g++") ? "gcc" : "clang";
        Files.writeString(artifacts.resolve(prefix + "-evidence.json"), Json.serialize(metadata));
        Files.writeString(artifacts.resolve(prefix + "-probe.cpp"), Files.readString(source));
        return observed;
    }

    private static String observation(String output, String key) {
        return output.lines().filter(line -> line.startsWith(key + " "))
                .map(line -> line.substring(key.length() + 1)).findFirst().orElseThrow();
    }

    private static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private record Run(int code, String out, String err) {}

    private Run success(List<String> args, Path work) throws Exception {
        var result = command(args, work);
        assertEquals(0, result.code(), args + "\n" + result.out() + "\n" + result.err());
        return result;
    }

    private Run command(List<String> args, Path work) throws Exception {
        Path stdout = Files.createTempFile(work, "stdout-", ".txt"), stderr = Files.createTempFile(work, "stderr-", ".txt");
        var process = new ProcessBuilder(args).directory(work.toFile()).redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), args.toString());
            return new Run(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }
}
