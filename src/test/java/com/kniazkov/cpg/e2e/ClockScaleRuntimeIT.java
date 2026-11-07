/* CLOCKS_PER_SEC and clock_t checked against independent strict C++03 native oracles. MIT License. */
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

class ClockScaleRuntimeIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final List<List<String>> HEADERS = List.of(List.of("ctime"), List.of("time.h"),
            List.of("ctime", "time.h"), List.of("time.h", "ctime"));
    // An explicit selected target. Neither the production profile nor renderer probes the host.
    private static final PlatformProfile GLIBC = PlatformProfile.builder().cRuntime("glibc")
            .clockScale("long", "long", "1000000").build();
    private static final String OVERLOADS = """
            int exact_type(int value) { return 1; }
            int exact_type(long value) { return 2; }
            int exact_type(unsigned int value) { return 3; }
            int exact_type(unsigned long value) { return 4; }
            """;

    @Test void observedNativeClockScaleSurvivesApiCliCopyEditsReparsingAndTransforms() throws Exception {
        assertEquals(probe("g++"), probe("clang++"),
                "Independent real-header probes must agree on the selected clock and expression types");
        assertEquals(GLIBC, PlatformProfile.fromSettings(GLIBC.toSettings()));
        assertEquals(GLIBC, GLIBC.toBuilder().build());
        var model = GLIBC.clockScale().orElseThrow();
        assertEquals("long", model.clockType());
        assertEquals("long", model.expressionType());
        assertEquals(new BigInteger("1000000"), model.value());
        assertEquals("1000000L", model.replacement());
        assertEquals(Map.of("clockType", "long", "expressionType", "long", "value", "1000000"),
                GLIBC.toSettings().get("clockScale"));
        for (var headers : HEADERS) {
            Path work = Files.createTempDirectory(temp, "clock-");
            var cli = new CliSandbox(work).source("main.cpp", runtimeProgram(headers));
            var options = options(GLIBC, Cpg.Stage.RESOLVED);
            var project = Cpg.read(cli.input, options);
            verifyRuntime(project, false, headers.size());
            var copy = project.edit().build();
            verifyRuntime(copy, false, headers.size());
            Path settings = work.resolve("profile.json");
            Files.writeString(settings, Json.serialize(GLIBC.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),
                    Json.parse(run.work().resolve("snapshot.json").toFile()));
            Path copied = writeSource(work, "copied", Cpg.sources(copy, Language.CPP).get("main.cpp"));
            for (Path source : List.of(cli.input.resolve("main.cpp"), copied,
                    run.work().resolve("generated/main.cpp"))) {
                verifyRuntime(Cpg.read(source.getParent(), options), false, headers.size());
                nativeRuntime(source, false);
                nativeRuntime(materialize(source, GLIBC, true), false);
            }
            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input, options(GLIBC, stage));
                verifyRuntime(transformed, false, headers.size());
                verifyRuntime(transformed.edit().build(), false, headers.size());
                Path source = writeSource(work, stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verifyRuntime(Cpg.read(source.getParent(), options), false, headers.size());
                nativeRuntime(source, false);
                nativeRuntime(materialize(source, GLIBC, true), false);
            }
            var editor = new ProjectEditor(project);
            editor.replace(variable(project, "editable").initializer().orElseThrow(), IntegerLiteral.create()
                    .setValue(new BigInteger("999999")).setCppType("long").build());
            var rawEdited = editor.build();
            assertEquals(new BigInteger("999999"), assertInstanceOf(IntegerLiteral.class,
                    ExpressionSyntax.written(variable(rawEdited, "editable").initializer().orElseThrow())).bigValue());
            var edited = NameResolver.resolve(rawEdited);
            verifyRuntime(edited, true, headers.size());
            verifyRuntime(edited.edit().build(), true, headers.size());
            Path source = writeSource(work, "edited", Cpg.sources(edited, Language.CPP).get("main.cpp"));
            verifyRuntime(Cpg.read(source.getParent(), options), true, headers.size());
            // The changed initializer is consumed by both a computation and an exact output assertion.
            nativeRuntime(source, true);
            nativeRuntime(materialize(source, GLIBC, true), true);
        }
    }

    @Test void independentTypesAndAllPositiveSuffixBoundariesUseActualCpgConstants() throws Exception {
        record Boundary(String clockType, String expressionType, String decimal, String oracle, int overload) {}
        // Independent hexadecimal expressions are numeric oracles, not the production renderer.
        // Some scales deliberately exceed clockType's range: the two target facts are independent.
        var cases = List.of(
                new Boundary("unsigned long", "int", "1", "0x1", 1),
                new Boundary("unsigned long", "int", "2147483647", "0x7fffffff", 1),
                new Boundary("long", "unsigned int", "1", "0x1U", 3),
                new Boundary("long", "unsigned int", "4294967295", "0xffffffffU", 3),
                new Boundary("int", "long", "1", "0x1L", 2),
                new Boundary("int", "long", "9223372036854775807", "0x7fffffffffffffffL", 2),
                new Boundary("unsigned int", "unsigned long", "1", "0x1UL", 4),
                new Boundary("unsigned int", "unsigned long", "18446744073709551615", "0xffffffffffffffffUL", 4));
        for (var boundary : cases) {
            var profile = PlatformProfile.builder().clockScale(boundary.clockType(),
                    boundary.expressionType(), boundary.decimal()).build();
            assertEquals(profile, PlatformProfile.fromSettings(profile.toSettings()));
            assertEquals(profile, profile.toBuilder().build());
            // These graph-only declarations are never submitted to a host compiler: no synthetic
            // clock_t is misrepresented as the host ABI, and no clock implementation is invented.
            Path graphWork = Files.createTempDirectory(temp, "clock-types-");
            var graphInput = new CliSandbox(graphWork).source("main.cpp", "#include <ctime>\n#include <time.h>\n"
                    + "std::clock_t selectedClock; clock_t companionClock; std::time_t untouched;\n"
                    + "std::clock_t observed(){ return std::clock(); }\n");
            var graph = Cpg.read(graphInput.input, options(profile, Cpg.Stage.RESOLVED));
            assertHealthy(graph);
            for (String name : List.of("selectedClock", "companionClock"))
                assertEquals(boundary.clockType(), variable(graph, name).type().orElseThrow().canonicalIdentity());
            assertInstanceOf(OpaqueLibraryType.class, variable(graph, "untouched").type().orElseThrow().canonicalType());
            var call = callsTo(graph, "clock").getFirst();
            assertTrue(LibraryDeclarations.isLibrary(call.target().orElseThrow()));
            assertTrue(call.target().orElseThrow().body().isEmpty());
            assertEquals(boundary.clockType(), call.target().orElseThrow().returnType().orElseThrow().canonicalIdentity());
            assertEquals(boundary.clockType(), TypeSemantics.expressionType(call, profile).orElseThrow().canonicalIdentity());
            for (String header : List.of("ctime", "time.h")) {
                Path work = Files.createTempDirectory(temp, "clock-constant-");
                String preprocessorChecks = "#if !defined(CLOCKS_PER_SEC) || CLOCKS_PER_SEC != " + boundary.oracle()
                        + "\n#error CPG scale boundary mismatch\n#endif\n";
                String ordinaryChecks = "";
                if (boundary.decimal().equals("4294967295")) {
                    preprocessorChecks += "#if CLOCKS_PER_SEC + 1U != 0x100000000UL\n"
                            + "#error Preprocessor unsigned arithmetic must use its wider target width\n#endif\n";
                    ordinaryChecks = " || CLOCKS_PER_SEC + 1U != 0U";
                } else if (boundary.decimal().equals("18446744073709551615")) {
                    preprocessorChecks += "#if CLOCKS_PER_SEC + 1UL != 0UL\n#error Wrong unsigned-long wrap\n#endif\n";
                    ordinaryChecks = " || CLOCKS_PER_SEC + 1UL != 0UL";
                }
                var cli = new CliSandbox(work).source("main.cpp", "#include <" + header + ">\n"
                        + preprocessorChecks + OVERLOADS + "int main(){ return exact_type(CLOCKS_PER_SEC) != "
                        + boundary.overload() + " || CLOCKS_PER_SEC != " + boundary.oracle() + ordinaryChecks + "; }\n");
                var project = Cpg.read(cli.input, options(profile, Cpg.Stage.RESOLVED));
                assertHealthy(project);
                assertEquals(boundary.expressionType(), callsTo(project, "exact_type").getFirst().target()
                        .orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
                var definitions = project.files().getFirst().provenance().orElseThrow().unit().definitions().stream()
                        .filter(definition -> definition.name().equals("CLOCKS_PER_SEC")).toList();
                assertEquals(1, definitions.size());
                var definition = definitions.getFirst();
                assertNull(definition.unavailableReason());
                assertEquals("library", definition.originKind());
                assertTrue(project.externalInput(definition.sourceKey()).isPresent());
                assertEquals(profile.clockScale().orElseThrow().replacement(), definition.rawReplacement());
                Path expanded = materialize(cli.input.resolve("main.cpp"), profile, false);
                // Native #if evaluates the exact emitted definition, independently of CPG's evaluator.
                String nativeChecks = "#define CLOCKS_PER_SEC " + definition.rawReplacement() + "\n"
                        + preprocessorChecks + "#undef CLOCKS_PER_SEC\n";
                Files.writeString(expanded, nativeChecks + Files.readString(expanded));
                nativeRun(expanded, "");
                Path copied = writeSource(work, "copied", Cpg.sources(project.edit().build(), Language.CPP).get("main.cpp"));
                assertHealthy(Cpg.read(copied.getParent(), options(profile, Cpg.Stage.RESOLVED)));
                nativeRun(materialize(copied, profile, false), "");
            }
        }
    }

    @Test void absentAndEmptyModelsKeepOpaqueTypesAndContextualApiCliErrorJson() throws Exception {
        var empty = PlatformProfile.fromSettings(Map.of("inherits", "default", "cRuntime", "glibc", "clockScale", Map.of()));
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().cRuntime("glibc").build(), empty)) {
            assertTrue(profile.clockScale().isEmpty());
            for (var headers : HEADERS) {
                Path work = Files.createTempDirectory(temp, "clock-unavailable-");
                String namespace = headers.contains("ctime") ? "std::" : "";
                String include = includes(headers);
                var cli = new CliSandbox(work).source("main.cpp", include
                        + "#if !defined(CLOCKS_PER_SEC)\n#error Missing required macro\n#endif\n"
                        + namespace + "clock_t value; " + namespace + "time_t untouched;\n"
                        + namespace + "clock_t observed(){return " + namespace + "clock();}\nint main(){return 0;}\n");
                var options = options(profile, Cpg.Stage.RESOLVED);
                var unused = Cpg.read(cli.input, options);
                assertHealthy(unused);
                for (String name : List.of("value", "untouched"))
                    assertInstanceOf(OpaqueLibraryType.class, variable(unused, name).type().orElseThrow().canonicalType());
                assertEquals(variable(unused, "value").type().orElseThrow().canonicalIdentity(),
                        variable(unused.edit().build(), "value").type().orElseThrow().canonicalIdentity());
                var clock = callsTo(unused, "clock").getFirst().target().orElseThrow();
                assertTrue(LibraryDeclarations.isLibrary(clock));
                assertTrue(clock.body().isEmpty());
                assertInstanceOf(OpaqueLibraryType.class, clock.returnType().orElseThrow().canonicalType());
                var definitions = unused.files().getFirst().provenance().orElseThrow().unit().definitions().stream()
                        .filter(d -> d.name().equals("CLOCKS_PER_SEC")).toList();
                assertEquals(headers.size(), definitions.size());
                for (var definition : definitions) assertNotNull(definition.unavailableReason());
                Path settings = work.resolve("profile.json");
                Files.writeString(settings, Json.serialize(profile.toSettings()));
                cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                        "--platform-profile", settings.toString()).expectStatus(0);
                for (String use : List.of("int main(){return CLOCKS_PER_SEC == 0;}\n",
                        "#if CLOCKS_PER_SEC > 0\n#endif\nint main(){return 0;}\n")) {
                    Files.writeString(cli.input.resolve("main.cpp"), include + use);
                    String error = assertThrows(ParseException.class, () -> Cpg.read(cli.input, options)).getMessage();
                    assertTrue(error.contains("CLOCKS_PER_SEC"), error);
                    assertTrue(error.contains("clockScale"), error);
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

    private static String includes(List<String> headers) {
        return headers.stream().map(header -> "#include <" + header + ">\n").reduce("", String::concat);
    }

    private static String runtimeProgram(List<String> headers) {
        String namespace = headers.contains("ctime") ? "std::" : "";
        String companion = headers.contains("time.h") ? "" : "std::";
        // No host #if CLOCKS_PER_SEC: the observed glibc macro contains a cast. The negative
        // native preprocessing result and positive CPG scalar checks are separate test obligations.
        return includes(headers) + "#include <cstdio>\n" + OVERLOADS + """
                long editable = CLOCKS_PER_SEC;
                %1$stime_t untouched;
                int main() {
                    %1$sclock_t clockValue = %1$sclock();
                    %2$sclock_t companionClock = clockValue;
                    long scaleValue = CLOCKS_PER_SEC;
                    if (exact_type(clockValue) != 2 || exact_type(companionClock) != 2) return 1;
                    if (exact_type(CLOCKS_PER_SEC) != 2 || scaleValue != 1000000L) return 2;
                    double scaleSeconds = static_cast<double>(CLOCKS_PER_SEC) / 1000000.0;
                    if (scaleSeconds != 1.0) return 3;
                    if (clockValue != static_cast<%1$sclock_t>(-1)) {
                        double seconds = static_cast<double>(clockValue) / CLOCKS_PER_SEC;
                        double recovered = seconds * CLOCKS_PER_SEC;
                        if (recovered < static_cast<double>(clockValue) - 1.0) return 4;
                        if (recovered > static_cast<double>(clockValue) + 1.0) return 5;
                    }
                    long difference = CLOCKS_PER_SEC - editable;
                    std::printf("EDITABLE %%ld DIFFERENCE %%ld TYPE %%d CLOCK %%d\\n",
                            editable, difference, exact_type(CLOCKS_PER_SEC), exact_type(clockValue));
                    return 0;
                }
                """.formatted(namespace, companion);
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
        return nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }

    private static void assertHealthy(Project project) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
    }

    private static void verifyRuntime(Project project, boolean edited, int headerCount) {
        assertHealthy(project);
        for (String name : List.of("clockValue", "companionClock", "scaleValue", "editable", "difference"))
            assertEquals("long", variable(project, name).type().orElseThrow().canonicalIdentity(), name);
        assertEquals(new BigInteger("1000000"), assertInstanceOf(IntegerLiteral.class,
                ExpressionSyntax.written(variable(project, "scaleValue").initializer().orElseThrow())).bigValue());
        assertInstanceOf(OpaqueLibraryType.class, variable(project, "untouched").type().orElseThrow().canonicalType());
        var editable = assertInstanceOf(IntegerLiteral.class,
                ExpressionSyntax.written(variable(project, "editable").initializer().orElseThrow()));
        assertEquals(new BigInteger(edited ? "999999" : "1000000"), editable.bigValue());
        assertEquals("long", TypeSemantics.expressionType(editable, GLIBC).orElseThrow().canonicalIdentity());
        var exactCalls = callsTo(project, "exact_type");
        assertEquals(5, exactCalls.size());
        for (var call : exactCalls) assertEquals("long", call.target().orElseThrow()
                .parameters().getFirst().type().orElseThrow().canonicalIdentity());
        var clocks = callsTo(project, "clock");
        assertEquals(1, clocks.size());
        var clock = clocks.getFirst();
        assertTrue(LibraryDeclarations.isLibrary(clock.target().orElseThrow()));
        assertTrue(clock.target().orElseThrow().body().isEmpty(), "No manufactured clock runtime body");
        assertEquals("long", clock.target().orElseThrow().returnType().orElseThrow().canonicalIdentity());
        assertEquals("long", TypeSemantics.expressionType(clock, GLIBC).orElseThrow().canonicalIdentity());
        var pp = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(GLIBC, pp.configuration().platform());
        var definitions = pp.definitions().stream().filter(d -> d.name().equals("CLOCKS_PER_SEC")).toList();
        assertEquals(headerCount, definitions.size(), "Each supplying time header needs its own provenance");
        assertEquals(headerCount, definitions.stream().map(d -> d.sourceKey()).distinct().count());
        for (var definition : definitions) {
            assertFalse(definition.functionLike());
            assertNull(definition.unavailableReason());
            assertEquals("1000000L", definition.rawReplacement());
            assertEquals("library", definition.originKind());
            assertTrue(project.externalInput(definition.sourceKey()).isPresent());
        }
        assertTrue(pp.expansions().stream().anyMatch(event -> event.name().equals("CLOCKS_PER_SEC")));
    }

    private static void assertErrorJson(String json) {
        for (String expected : List.of("unavailable-library-macro", "unavailableReason", "CLOCKS_PER_SEC", "clockScale", "main.cpp"))
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
        assertTrue(pp.expansions().stream().anyMatch(event -> event.name().equals("CLOCKS_PER_SEC")));
        assertFalse(pp.tokens().stream().anyMatch(token -> token.token().spelling().equals("CLOCKS_PER_SEC")),
                "Every native-tested scale use must be expanded by CPG, never the host macro");
        var text = new StringBuilder();
        boolean bodyStarted = false;
        for (var expanded : pp.tokens()) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                assertFalse(bodyStarted, "Fixture headers must be a prefix; never hoist a later include");
                if (retainNativeHeaders) text.append("#include ").append(token.spelling()).append('\n');
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                if (!bodyStarted) {
                    // Finish all real headers before disabling their macro for the complete tested body.
                    text.append("#undef CLOCKS_PER_SEC\n");
                    bodyStarted = true;
                }
                assertNotEquals("CLOCKS_PER_SEC", token.spelling());
                text.append(token.spelling()).append(' ');
            }
        }
        assertTrue(bodyStarted);
        if (!retainNativeHeaders) assertFalse(text.toString().contains("#include"));
        Path file = Files.createTempDirectory(temp, "clock-materialized-").resolve("main.cpp");
        Files.writeString(file, text.append('\n'));
        return file;
    }

    private void nativeRuntime(Path source, boolean edited) throws Exception {
        nativeRun(source, "EDITABLE " + (edited ? "999999 DIFFERENCE 1" : "1000000 DIFFERENCE 0") + " TYPE 2 CLOCK 2\n");
    }

    private void nativeRun(Path source, String expected) throws Exception {
        for (String compiler : COMPILERS) {
            Path work = Files.createTempDirectory(temp, "clock-native-");
            Path binary = work.resolve("clock-check");
            success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
            var result = success(List.of(binary.toString()), work);
            assertEquals(expected, result.out(), compiler + " / " + source);
            assertEquals("", result.err(), compiler + " / " + source);
        }
    }

    private String probe(String compiler) throws Exception {
        Path work = Files.createTempDirectory(temp, "clock-oracle-");
        Path source = work.resolve("probe.cpp"), binary = work.resolve("probe");
        Files.writeString(source, """
                #include <ctime>
                #include <time.h>
                #include <cstdio>
                #include <climits>
                #ifndef __GLIBC__
                #error This independent oracle requires the explicitly selected glibc runtime
                #endif
                #ifndef __x86_64__
                #error This independent oracle requires the explicitly selected x86_64 ABI
                #endif
                #include <gnu/libc-version.h>
                """ + OVERLOADS + """
                int main() {
                    std::printf("WIDTHS %d %d %d %d\\n", CHAR_BIT, int(sizeof(int)*CHAR_BIT), int(sizeof(long)*CHAR_BIT), int(sizeof(void*)*CHAR_BIT));
                    std::printf("CLOCK_TYPE %d\\nGLOBAL_CLOCK_TYPE %d\\n", exact_type(std::clock_t(0)), exact_type(clock_t(0)));
                    std::printf("SCALE_TYPE %d\\nSCALE_VALUE %lu\\n", exact_type(CLOCKS_PER_SEC), static_cast<unsigned long>(CLOCKS_PER_SEC));
                    std::printf("GLIBC_HEADERS %d.%d\\nGLIBC_RUNTIME %s\\n", __GLIBC__, __GLIBC_MINOR__, gnu_get_libc_version());
                    std::clock_t ticks = std::clock();
                    int valid = 1;
                    if (ticks != static_cast<std::clock_t>(-1)) {
                        double seconds = static_cast<double>(ticks) / CLOCKS_PER_SEC;
                        double recovered = seconds * CLOCKS_PER_SEC;
                        if (recovered < static_cast<double>(ticks) - 1.0 || recovered > static_cast<double>(ticks) + 1.0) valid = 0;
                    }
                    std::printf("CLOCK_RETURN_TYPE %d\\nRUNTIME_CONVERSION %d\\n", exact_type(std::clock()), valid);
                    return !valid;
                }
                """);
        success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
        var run = success(List.of(binary.toString()), work);
        String observed = run.out();
        assertEquals("", run.err());
        assertEquals("8 32 64 64", observation(observed, "WIDTHS"));
        assertEquals("2", observation(observed, "CLOCK_TYPE"), "clock_t must be exactly long");
        assertEquals("2", observation(observed, "GLOBAL_CLOCK_TYPE"));
        assertEquals("2", observation(observed, "SCALE_TYPE"), "Probe the macro type separately from clock_t");
        assertEquals("1000000", observation(observed, "SCALE_VALUE"));
        assertEquals("2", observation(observed, "CLOCK_RETURN_TYPE"));
        assertEquals("1", observation(observed, "RUNTIME_CONVERSION"));
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("compiler", compiler);
        metadata.put("version", success(List.of(compiler, "--version"), work).out());
        metadata.put("target", success(List.of(compiler, "-dumpmachine"), work).out().trim());
        metadata.put("headerAndRuntimeObservations", observed);
        metadata.put("platform", GLIBC.toSettings());
        metadata.put("probeSourceSha256", hash(source));
        metadata.put("probeBinarySha256", hash(binary));
        metadata.put("includeSearch", success(List.of(compiler, "-std=c++03", "-E", "-v", source.toString(), "-o", "/dev/null"), work).err());
        metadata.put("compilerSearchDirectories", success(List.of(compiler, "-print-search-dirs"), work).out());
        metadata.put("compilerAndSysrootInvocation", success(List.of(compiler, "-std=c++03", "-###", "-c",
                source.toString(), "-o", work.resolve("not-created.o").toString()), work).err());
        Path macros = work.resolve("macros.cpp");
        Files.writeString(macros, "#include <ctime>\n#include <time.h>\n");
        var definitions = success(List.of(compiler, "-std=c++03", "-dM", "-E", macros.toString()), work).out()
                .lines().filter(line -> line.startsWith("#define CLOCKS_PER_SEC ")).toList();
        assertEquals(1, definitions.size());
        metadata.put("nativeClockScaleMacroDefinitions", definitions);
        Path negative = work.resolve("host-if-negative.cpp");
        Files.writeString(negative, "#include <ctime>\n#if CLOCKS_PER_SEC > 0\nint selected;\n#endif\n");
        var rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-E", negative.toString(), "-o", "/dev/null"), work);
        assertNotEquals(0, rejected.code(), "The selected host cast macro is not a positive #if oracle");
        assertFalse(rejected.err().isBlank());
        metadata.put("hostIfNegativeExitCode", rejected.code());
        metadata.put("hostIfNegativeDiagnostics", rejected.err());
        metadata.put("hostIfNegativeSourceSha256", hash(negative));
        String headers = success(List.of(compiler, "-std=c++03", "-H", "-E", source.toString(), "-o", "/dev/null"), work).err();
        var hashes = new TreeMap<String, String>();
        for (String line : headers.split("\n")) {
            if (line.matches("\\.+ .*")) {
                Path header = Path.of(line.replaceFirst("^\\.+ ", ""));
                if (Files.isRegularFile(header)) hashes.put(header.toString(), hash(header));
            }
        }
        for (String header : List.of("/time.h", "/bits/time.h", "/bits/types/clock_t.h", "/bits/typesizes.h"))
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
        Path artifacts = Files.createDirectories(Path.of("target/clock-scale-oracle"));
        String prefix = compiler.equals("g++") ? "gcc" : "clang";
        Files.writeString(artifacts.resolve(prefix + "-evidence.json"), Json.serialize(metadata));
        Files.writeString(artifacts.resolve(prefix + "-probe.cpp"), Files.readString(source));
        Files.writeString(artifacts.resolve(prefix + "-host-if-negative.cpp"), Files.readString(negative));
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
