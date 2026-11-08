/* HUGE_VAL checked against independent strict C++03 compiler and libc oracles. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
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
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HugeValRuntimeIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final List<List<String>> HEADERS = List.of(List.of("cmath"), List.of("math.h"),
            List.of("cmath", "math.h"), List.of("math.h", "cmath"));
    // This capability is selected explicitly. No production code probes libc, headers or the JVM.
    private static final PlatformProfile GLIBC = PlatformProfile.builder().cRuntime("glibc")
            .floatingFormat("double", "binary64").sizeBits("double", 64)
            .hugeVal("positive_infinity", "gcc-clang-builtin-huge-val").build();
    private static final String OVERLOADS = """
            int exact_type(float value) { return 1; }
            int exact_type(double value) { return 2; }
            int exact_type(long double value) { return 3; }
            """;

    @Test void nativeInfinitySurvivesApiCliCopyEditsReparsingAndTransforms() throws Exception {
        assertEquals(probe("g++"), probe("clang++"),
                "Independent compiler/header/runtime observations must agree on the selected target");
        assertEquals(GLIBC, PlatformProfile.fromSettings(GLIBC.toSettings()));
        assertEquals(GLIBC, GLIBC.toBuilder().build());
        var model = GLIBC.hugeVal().orElseThrow();
        assertEquals("positive_infinity", model.kind());
        assertEquals("gcc-clang-builtin-huge-val", model.emission());
        assertEquals("(__builtin_huge_val())", model.replacement());
        assertEquals(Map.of("kind", "positive_infinity", "emission", "gcc-clang-builtin-huge-val"),
                GLIBC.toSettings().get("hugeVal"));
        for (var headers : HEADERS) {
            Path work = Files.createTempDirectory(temp, "huge-val-");
            var cli = new CliSandbox(work).source("main.cpp", runtimeProgram(headers));
            var options = options(GLIBC, Cpg.Stage.RESOLVED);
            var project = Cpg.read(cli.input, options);
            verifyRuntime(project, false, headers.size());
            var copy = project.edit().build();
            verifyRuntime(copy, false, headers.size());
            verifyRuntime(NameResolver.resolve(copy), false, headers.size());
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
                Path expanded = materialize(source, GLIBC);
                // Direct builtin syntax is deliberately enabled by the same selected capability.
                assertHealthy(Cpg.read(expanded.getParent(), options));
                nativeRuntime(expanded, false);
            }
            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input, options(GLIBC, stage));
                verifyRuntime(transformed, false, headers.size());
                verifyRuntime(transformed.edit().build(), false, headers.size());
                Path source = writeSource(work, stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verifyRuntime(Cpg.read(source.getParent(), options), false, headers.size());
                nativeRuntime(source, false);
                Path expanded = materialize(source, GLIBC);
                assertHealthy(Cpg.read(expanded.getParent(), options));
                nativeRuntime(expanded, false);
            }
            var editor = new ProjectEditor(project);
            editor.replace(variable(project, "editable").initializer().orElseThrow(), FloatingLiteral.create()
                    .setValue(new BigDecimal("7.5")).setCppType("double").build());
            var rawEdited = editor.build();
            assertEquals(new BigDecimal("7.5"), assertInstanceOf(FloatingLiteral.class,
                    ExpressionSyntax.written(variable(rawEdited, "editable").initializer().orElseThrow())).value());
            var edited = NameResolver.resolve(rawEdited);
            verifyRuntime(edited, true, headers.size());
            verifyRuntime(edited.edit().build(), true, headers.size());
            Path source = writeSource(work, "edited", Cpg.sources(edited, Language.CPP).get("main.cpp"));
            verifyRuntime(Cpg.read(source.getParent(), options), true, headers.size());
            // Both executed comparison and exact printed finite value witness this non-no-op edit.
            nativeRuntime(source, true);
            Path expanded = materialize(source, GLIBC);
            assertHealthy(Cpg.read(expanded.getParent(), options));
            nativeRuntime(expanded, true);
        }
    }

    @Test void absentAndEmptyCapabilityKeepContextualApiCliErrorJson() throws Exception {
        var empty = PlatformProfile.fromSettings(Map.of("inherits", "default", "cRuntime", "glibc", "hugeVal", Map.of()));
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().cRuntime("glibc").build(), empty)) {
            assertTrue(profile.hugeVal().isEmpty());
            for (String header : List.of("cmath", "math.h")) {
                Path work = Files.createTempDirectory(temp, "huge-val-unavailable-");
                String include = "#include <" + header + ">\n";
                var cli = new CliSandbox(work).source("main.cpp", include
                        + "#if !defined(HUGE_VAL)\n#error Missing required macro\n#endif\nint main(){return 0;}\n");
                var options = options(profile, Cpg.Stage.RESOLVED);
                var unused = Cpg.read(cli.input, options);
                assertHealthy(unused);
                assertHealthy(unused.edit().build());
                var definition = unused.files().getFirst().provenance().orElseThrow().unit().definitions().stream()
                        .filter(d -> d.name().equals("HUGE_VAL")).findFirst().orElseThrow();
                assertNotNull(definition.unavailableReason());
                Path settings = work.resolve("profile.json");
                Files.writeString(settings, Json.serialize(profile.toSettings()));
                cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                        "--platform-profile", settings.toString()).expectStatus(0);
                for (String use : List.of("double value = HUGE_VAL; int main(){return 0;}\n",
                        "#if HUGE_VAL > 0\n#endif\nint main(){return 0;}\n")) {
                    Files.writeString(cli.input.resolve("main.cpp"), include + use);
                    String error = assertThrows(ParseException.class, () -> Cpg.read(cli.input, options)).getMessage();
                    assertTrue(error.contains("HUGE_VAL"), error);
                    assertTrue(error.contains("hugeVal"), error);
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

    @Test void nativeCompilersRejectAssignmentIncrementAndAddressOfMaterializedRvalue() throws Exception {
        for (String header : List.of("cmath", "math.h")) {
            for (String misuse : List.of("HUGE_VAL = 1.0;", "++HUGE_VAL;", "double *p = &HUGE_VAL;")) {
                Path work = Files.createTempDirectory(temp, "huge-val-rvalue-");
                Path source = work.resolve("main.cpp");
                Files.writeString(source, "#include <" + header + ">\nint main(){" + misuse + " return 0;}\n");
                // RVALUE is a graph fact. Generic scalar-rvalue diagnostic completeness is separate.
                for (Path input : List.of(source, materialize(source, GLIBC))) {
                    for (String compiler : COMPILERS) {
                        Path binary = Files.createTempDirectory(temp, "huge-val-rejected-").resolve("invalid");
                        var rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors",
                                input.toString(), "-o", binary.toString()), work);
                        assertNotEquals(0, rejected.code(), compiler + " incorrectly accepted " + misuse + " / " + input);
                        assertFalse(Files.exists(binary));
                        assertFalse(rejected.err().isBlank());
                    }
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
        return includes(headers) + "#include <cstdio>\n#include <limits>\n"
                + "#if !defined(HUGE_VAL)\n#error Missing required macro\n#endif\n" + OVERLOADS + """
                double sentinel = HUGE_VAL;
                double editable = HUGE_VAL;
                int calls = 0;
                double touch() { ++calls; return 0.0; }
                int main() {
                    double value = HUGE_VAL;
                    if (exact_type(HUGE_VAL) != 2 || exact_type(value) != 2) return 1;
                    if (!std::numeric_limits<double>::has_infinity) return 2;
                    if (value <= 0.0 || value <= std::numeric_limits<double>::max()) return 3;
                    if (value != std::numeric_limits<double>::infinity() || value != sentinel) return 4;
                    unsigned long bytes = sizeof(__builtin_huge_val());
                    if (bytes != sizeof(double)) return 5;
                    double difference = value - value;
                    double ratio = value / value;
                    if (difference == difference || ratio == ratio) return 6;
                    double retained = touch() + value;
                    if (retained != value || calls != 1) return 7;
                    std::printf("EDITABLE %d FINITE %.1f TYPE %d CALLS %d\\n",
                            editable == value, editable < value ? editable : 0.0, exact_type(editable), calls);
                    return 0;
                }
                """;
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

    private static void verifyRuntime(Project project, boolean edited, int headerCount) throws Exception {
        assertHealthy(project);
        for (String name : List.of("sentinel", "editable", "value", "difference", "ratio", "retained"))
            assertEquals("double", variable(project, name).type().orElseThrow().canonicalIdentity(), name);
        assertInstanceOf(CompilerIntrinsicExpression.class,
                ExpressionSyntax.written(variable(project, "sentinel").initializer().orElseThrow()));
        assertInstanceOf(CompilerIntrinsicExpression.class,
                ExpressionSyntax.written(variable(project, "value").initializer().orElseThrow()));
        var editable = ExpressionSyntax.written(variable(project, "editable").initializer().orElseThrow());
        if (edited) assertEquals(new BigDecimal("7.5"), assertInstanceOf(FloatingLiteral.class, editable).value());
        else assertInstanceOf(CompilerIntrinsicExpression.class, editable);
        var intrinsics = nodes(project, CompilerIntrinsicExpression.class);
        assertFalse(intrinsics.isEmpty());
        for (var intrinsic : intrinsics) {
            assertEquals(CompilerIntrinsicExpression.Kind.DOUBLE_POSITIVE_INFINITY, intrinsic.kind());
            assertEquals("double", intrinsic.cppType());
            assertEquals("gcc-clang-builtin-huge-val", intrinsic.emission());
            assertEquals("double", TypeSemantics.expressionType(intrinsic, GLIBC).orElseThrow().canonicalIdentity());
            assertEquals(ValueCategory.RVALUE, TypeSemantics.valueCategory(intrinsic, GLIBC));
            assertTrue(intrinsic.children().isEmpty());
            assertTrue(intrinsic.references().isEmpty(), "A compiler value has no manufactured callable target");
            assertEquals("__builtin_huge_val()", intrinsic.source(Language.CPP));
        }
        assertTrue(nodes(project, CallableDeclaration.class).stream()
                .noneMatch(declaration -> declaration.name().contains("huge_val")), "No fabricated runtime helper declaration");
        assertTrue(callsTo(project, "__builtin_huge_val").isEmpty());
        assertEquals(3, callsTo(project, "exact_type").size());
        for (var call : callsTo(project, "exact_type")) assertEquals("double", call.target().orElseThrow()
                .parameters().getFirst().type().orElseThrow().canonicalIdentity());
        assertEquals(1, callsTo(project, "touch").size(), "The real observable call must survive transformations");
        assertInstanceOf(SubtractExpression.class,
                ExpressionSyntax.written(variable(project, "difference").initializer().orElseThrow()),
                "Do not turn infinity minus itself into a fictitious finite zero");
        assertInstanceOf(DivideExpression.class,
                ExpressionSyntax.written(variable(project, "ratio").initializer().orElseThrow()),
                "Do not turn infinity divided by itself into a fictitious finite one");
        String json = ProjectJson.encode(project).toText("  ");
        Json.parse(json);
        assertTrue(json.contains("double_positive_infinity"), json);
        assertTrue(json.contains("gcc-clang-builtin-huge-val"), json);
        assertFalse(json.matches("(?s).*:\\s*(?:Infinity|-Infinity|NaN)(?:\\s|,|}).*"), "JSON must not contain nonfinite numbers");
        var pp = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(GLIBC, pp.configuration().platform());
        var definitions = pp.definitions().stream().filter(d -> d.name().equals("HUGE_VAL")).toList();
        assertEquals(headerCount, definitions.size(), "Each supplying math header needs its own provenance");
        assertEquals(headerCount, definitions.stream().map(d -> d.sourceKey()).distinct().count());
        for (var definition : definitions) {
            assertFalse(definition.functionLike());
            assertNull(definition.unavailableReason());
            assertEquals("(__builtin_huge_val())", definition.rawReplacement());
            assertEquals("library", definition.originKind());
            assertTrue(project.externalInput(definition.sourceKey()).isPresent());
        }
        assertTrue(pp.expansions().stream().anyMatch(event -> event.name().equals("HUGE_VAL")));
    }

    private static void assertErrorJson(String json) {
        for (String expected : List.of("unavailable-library-macro", "unavailableReason", "HUGE_VAL", "hugeVal", "main.cpp"))
            assertTrue(json.contains(expected), json);
    }

    private Path writeSource(Path work, String directory, String source) throws Exception {
        Path file = Files.createDirectory(work.resolve(directory)).resolve("main.cpp");
        Files.writeString(file, source);
        return file;
    }

    private Path materialize(Path source, PlatformProfile profile) throws Exception {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(source), source.getParent(), List.of(),
                List.of(), "c++03").withPlatform(profile);
        var pp = CppPreprocessor.preprocess(unit, Files.readString(source));
        assertTrue(pp.expansions().stream().anyMatch(event -> event.name().equals("HUGE_VAL")));
        assertFalse(pp.tokens().stream().anyMatch(token -> token.token().spelling().equals("HUGE_VAL")),
                "Every native-tested macro occurrence must come from CPG, never the host replacement");
        var text = new StringBuilder();
        boolean bodyStarted = false;
        for (var expanded : pp.tokens()) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                assertFalse(bodyStarted, "Fixture headers must be a prefix; never hoist a later include");
                text.append("#include ").append(token.spelling()).append('\n');
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                if (!bodyStarted) {
                    // All real headers finish before the host macro is disabled for the tested body.
                    text.append("#undef HUGE_VAL\n");
                    bodyStarted = true;
                }
                assertNotEquals("HUGE_VAL", token.spelling());
                text.append(token.spelling()).append(' ');
            }
        }
        assertTrue(bodyStarted);
        assertTrue(text.toString().contains("__builtin_huge_val"));
        Path file = Files.createTempDirectory(temp, "huge-val-materialized-").resolve("main.cpp");
        Files.writeString(file, text.append('\n'));
        return file;
    }

    private void nativeRuntime(Path source, boolean edited) throws Exception {
        for (String compiler : COMPILERS) {
            Path work = Files.createTempDirectory(temp, "huge-val-native-");
            Path wrapper = work.resolve("oracle.cpp"), binary = work.resolve("infinity-check");
            // Capture the independent real-header value before the materialized input undefines it.
            // The input remains the actual original/generated/copied/edited source, not a recreated expression.
            Files.writeString(wrapper, """
                    #include <cmath>
                    #include <math.h>
                    #include <cstdio>
                    #include <limits>
                    const double independently_observed_host = HUGE_VAL;
                    #define main tested_main
                    #include "%s"
                    #undef main
                    int main() {
                        if (sentinel != independently_observed_host) return 91;
                        if (sentinel != std::numeric_limits<double>::infinity()) return 92;
                        if (sentinel <= std::numeric_limits<double>::max()) return 93;
                        return tested_main();
                    }
                    """.formatted(source.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"")));
            success(List.of(compiler, "-std=c++03", "-pedantic-errors", wrapper.toString(), "-o", binary.toString()), work);
            var result = success(List.of(binary.toString()), work);
            assertEquals("EDITABLE " + (edited ? "0 FINITE 7.5" : "1 FINITE 0.0") + " TYPE 2 CALLS 1\n",
                    result.out(), compiler + " / " + source);
            assertEquals("", result.err());
            assertNoIntrinsicSymbol(success(List.of("nm", "-u", binary.toString()), work).out());
        }
    }

    private String probe(String compiler) throws Exception {
        Path work = Files.createTempDirectory(temp, "huge-val-oracle-");
        Path source = work.resolve("probe.cpp"), binary = work.resolve("probe");
        Files.writeString(source, """
                #include <cmath>
                #include <math.h>
                #include <cstdio>
                #include <climits>
                #include <limits>
                #ifndef __GLIBC__
                #error This independent oracle requires the explicitly selected glibc runtime
                #endif
                #ifndef __x86_64__
                #error This independent oracle requires the explicitly selected x86_64 ABI
                #endif
                #include <gnu/libc-version.h>
                """ + OVERLOADS + """
                const double observed_host = HUGE_VAL;
                const int observed_host_type = exact_type(HUGE_VAL);
                #undef HUGE_VAL
                const double selected_value = __builtin_huge_val();
                int main() {
                    std::printf("WIDTHS %d %d %d %d\\n", CHAR_BIT, int(sizeof(int)*CHAR_BIT), int(sizeof(double)*CHAR_BIT), int(sizeof(void*)*CHAR_BIT));
                    std::printf("FORMAT %d %d %d %d\\n", std::numeric_limits<double>::radix, std::numeric_limits<double>::digits,
                            std::numeric_limits<double>::min_exponent, std::numeric_limits<double>::max_exponent);
                    std::printf("INFINITY_SUPPORT %d\\n", std::numeric_limits<double>::has_infinity);
                    std::printf("HOST_TYPE %d\\nSELECTED_TYPE %d\\n", observed_host_type, exact_type(__builtin_huge_val()));
                    std::printf("GLIBC_HEADERS %d.%d\\nGLIBC_RUNTIME %s\\n", __GLIBC__, __GLIBC_MINOR__, gnu_get_libc_version());
                    int valid = selected_value > 0.0 && selected_value > std::numeric_limits<double>::max()
                            && selected_value == observed_host && selected_value == std::numeric_limits<double>::infinity();
                    std::printf("POSITIVE_INFINITY %d\\n", valid);
                    return !valid;
                }
                """);
        success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
        var run = success(List.of(binary.toString()), work);
        String observed = run.out();
        assertEquals("", run.err());
        assertEquals("8 32 64 64", observation(observed, "WIDTHS"));
        assertEquals("2 53 -1021 1024", observation(observed, "FORMAT"), "Probe binary64 representation independently of storage");
        assertEquals("1", observation(observed, "INFINITY_SUPPORT"));
        assertEquals("2", observation(observed, "HOST_TYPE"), "The real macro must have exactly double type");
        assertEquals("2", observation(observed, "SELECTED_TYPE"));
        assertEquals("1", observation(observed, "POSITIVE_INFINITY"));
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
        String symbols = success(List.of("nm", "-u", binary.toString()), work).out();
        assertNoIntrinsicSymbol(symbols);
        metadata.put("undefinedSymbols", symbols);
        var nativeDefinitions = new LinkedHashMap<String, List<String>>();
        for (var headers : HEADERS) {
            Path macros = work.resolve("macros-" + nativeDefinitions.size() + ".cpp");
            Files.writeString(macros, includes(headers));
            var definitions = success(List.of(compiler, "-std=c++03", "-dM", "-E", macros.toString()), work).out()
                    .lines().filter(line -> line.startsWith("#define HUGE_VAL ")).toList();
            assertEquals(1, definitions.size());
            assertTrue(definitions.getFirst().contains("__builtin_huge_val"));
            nativeDefinitions.put(String.join(",", headers), definitions);
        }
        metadata.put("nativeHugeValMacroDefinitions", nativeDefinitions);
        Path negative = work.resolve("host-if-negative.cpp");
        Files.writeString(negative, "#include <cmath>\n#if HUGE_VAL > 0\nint selected;\n#endif\n");
        var rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-E", negative.toString(), "-o", "/dev/null"), work);
        assertNotEquals(0, rejected.code(), "The selected floating expression is not a #if integer constant");
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
        for (String header : List.of("/cmath", "/math.h", "/limits", "/gnu/libc-version.h"))
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
        Path artifacts = Files.createDirectories(Path.of("target/huge-val-oracle"));
        String prefix = compiler.equals("g++") ? "gcc" : "clang";
        Files.writeString(artifacts.resolve(prefix + "-evidence.json"), Json.serialize(metadata));
        Files.writeString(artifacts.resolve(prefix + "-probe.cpp"), Files.readString(source));
        Files.writeString(artifacts.resolve(prefix + "-host-if-negative.cpp"), Files.readString(negative));
        return observed;
    }

    private static void assertNoIntrinsicSymbol(String undefined) {
        for (String line : undefined.lines().toList())
            assertFalse(line.toLowerCase(Locale.ROOT).contains("huge_val"), "No compiler intrinsic runtime helper: " + undefined);
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
