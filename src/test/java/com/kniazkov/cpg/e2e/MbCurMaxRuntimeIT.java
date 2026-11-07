/* CPG MB_CUR_MAX expansion checked against an independent live glibc oracle. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.LibraryDeclarations;
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

class MbCurMaxRuntimeIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final String BINDING = "glibc-x86_64-lp64-mb16";
    private static final PlatformProfile GLIBC = PlatformProfile.builder().cRuntime("glibc")
            .mbCurMaxBinding(BINDING).build();
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03")
            .withPlatform(GLIBC);

    @Test void liveLocaleQuerySurvivesApiCliCopyMaterializationAndStringEditing() throws Exception {
        Oracle oracle = probe("g++");
        assertEquals(oracle, probe("clang++"), "Independent real-header oracles must describe the same runtime");
        assertEquals(GLIBC, PlatformProfile.fromSettings(GLIBC.toSettings()));
        assertEquals(BINDING, GLIBC.toSettings().get("mbCurMaxBinding"));
        for (boolean legacy : List.of(false, true)) {
            Path work = Files.createTempDirectory(temp, legacy ? "legacy-" : "cpp-");
            var cli = new CliSandbox(work).source("main.cpp", program(legacy, oracle));
            var project = Cpg.read(cli.input, OPTIONS);
            verify(project, oracle.locale());
            var copy = project.edit().build();
            verify(copy, oracle.locale());

            Path settings = work.resolve("glibc.json");
            Files.writeString(settings, Json.serialize(GLIBC.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(0);
            var json = Json.parse(run.work().resolve("snapshot.json").toFile());
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), json);
            assertTrue(json.toText("  ").contains(BINDING));

            Path copied = writeSource(work, "copied", Cpg.sources(copy, Language.CPP).get("main.cpp"));
            Path generated = run.work().resolve("generated/main.cpp");
            for (Path source : List.of(cli.input.resolve("main.cpp"), copied, generated)) {
                verify(Cpg.read(source.getParent(), OPTIONS), oracle.locale());
                nativeRun(source, oracle, false);
                nativeRun(materialize(source), oracle, false);
            }

            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var options = new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(GLIBC);
                var transformed = Cpg.read(cli.input, options);
                verify(transformed, oracle.locale());
                verify(transformed.edit().build(), oracle.locale());
                Path transformedSource = writeSource(work, stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verify(Cpg.read(transformedSource.getParent(), OPTIONS), oracle.locale());
                // A zero-argument query must not become a cached constant across setlocale.
                nativeRun(transformedSource, oracle, false);
                nativeRun(materialize(transformedSource), oracle, false);
            }

            var editor = new ProjectEditor(project);
            var locale = localeArguments(project).get(1);
            editor.replace(locale, StringLiteral.create().setCodeUnits(List.of(BigInteger.valueOf('C')))
                    .setSpelling("\"C\"").build());
            var edited = editor.build();
            verify(edited, "C");
            Path editedSource = writeSource(work, "edited", Cpg.sources(edited, Language.CPP).get("main.cpp"));
            verify(Cpg.read(editedSource.getParent(), OPTIONS), "C");
            // Native output must now observe C for the middle read, not the old UTF-8 limit.
            nativeRun(editedSource, oracle, true);
            nativeRun(materialize(editedSource), oracle, true);
        }
    }

    @Test void unavailableBindingPreservesPresenceChecksAndContextualApiCliErrorJson() throws Exception {
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().cRuntime("glibc").build())) {
            var options = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03").withPlatform(profile);
            for (String header : List.of("cstdlib", "stdlib.h")) {
                Path work = Files.createTempDirectory(temp, "unavailable-");
                String include = "#include <" + header + ">\n";
                String namespace = header.endsWith(".h") ? "" : "std::";
                var cli = new CliSandbox(work).source("main.cpp", include
                        + "#if !defined(MB_CUR_MAX)\n#error MB_CUR_MAX must be a macro\n#endif\n"
                        + "int main(){return " + namespace + "atoi(\"0\");}\n");
                Path settings = work.resolve("profile.json");
                Files.writeString(settings, Json.serialize(profile.toSettings()));
                var unused = Cpg.read(cli.input, options);
                assertFalse(unused.hasErrors(), () -> unused.parseProblems() + " / " + unused.semanticDiagnostics());
                assertFalse(unused.edit().build().hasErrors());
                cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                        "--platform-profile", settings.toString()).expectStatus(0);

                Files.writeString(cli.input.resolve("main.cpp"), include + "int main(){return MB_CUR_MAX;}\n");
                String error = assertThrows(ParseException.class, () -> Cpg.read(cli.input, options)).getMessage();
                assertTrue(error.contains("MB_CUR_MAX"), error);
                assertTrue(error.contains("mbCurMaxBinding"), error);
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

    @Test void nativeCompilersRejectAssignmentIncrementAndAddressOfMaterializedRvalue() throws Exception {
        for (String header : List.of("cstdlib", "stdlib.h")) {
            for (String misuse : List.of("MB_CUR_MAX = 1;", "++MB_CUR_MAX;", "unsigned long *p = &MB_CUR_MAX;")) {
                Path work = Files.createTempDirectory(temp, "invalid-rvalue-");
                Path source = work.resolve("main.cpp");
                Files.writeString(source, "#include <" + header + ">\nint main(){" + misuse + " return 0;}\n");
                // CPG already models RVALUE; generic assignment/address diagnostic completeness
                // is separate from this binding. These compilers independently enforce C++03.
                for (Path input : List.of(source, materialize(source, 1))) {
                    for (String compiler : COMPILERS) {
                        Path binary = Files.createTempDirectory(temp, "rejected-").resolve("invalid");
                        var result = command(List.of(compiler, "-std=c++03", "-pedantic-errors",
                                input.toString(), "-o", binary.toString()), work);
                        assertNotEquals(0, result.code(), compiler + " incorrectly accepted " + misuse + " / " + input);
                        assertFalse(Files.exists(binary));
                        assertFalse(result.err().isBlank(), "A rejected rvalue use must produce a diagnostic");
                    }
                }
            }
        }
    }

    private static void assertErrorJson(String json) {
        for (String expected : List.of("unavailable-library-macro", "unavailableReason", "MB_CUR_MAX",
                "mbCurMaxBinding", "main.cpp")) assertTrue(json.contains(expected), json);
    }

    private String program(boolean legacy, Oracle oracle) {
        String headers = legacy ? "#include <stdlib.h>\n#include <locale.h>\n#include <stdio.h>\n#include <limits.h>\n"
                : "#include <cstdlib>\n#include <clocale>\n#include <cstdio>\n#include <climits>\n";
        String namespace = legacy ? "" : "std::";
        // LC_CTYPE's numeric value and the installed locale come only from the native oracle.
        // The selected profile does not invent or implicitly import locale targetFacts.
        return headers + """
                int exact_type(unsigned long value) { return 17; }
                int exact_type(unsigned int value) { return 23; }
                int main() {
                    if (!%1$ssetlocale(%2$d, "C")) return 1;
                    %1$ssize_t first = MB_CUR_MAX;
                    if (!%1$ssetlocale(%2$d, "%3$s")) return 2;
                    %1$ssize_t middle = MB_CUR_MAX;
                    int exact = exact_type(MB_CUR_MAX);
                    if (!%1$ssetlocale(%2$d, "C")) return 3;
                    %1$ssize_t last = MB_CUR_MAX;
                    if (first < 1 || middle < 1 || last < 1) return 4;
                    if (first > MB_LEN_MAX || middle > MB_LEN_MAX || last > MB_LEN_MAX) return 5;
                    if (first != last || exact != 17) return 6;
                    char *end = 0;
                    long ordinary = %1$sstrtol("17", &end, 10);
                    if (ordinary != 17 || !end || *end != '\\0') return 7;
                    %1$sprintf("VALUES %%lu %%lu %%lu\\nTYPE %%d ORDINARY %%ld\\n", first, middle, last, exact, ordinary);
                    return 0;
                }
                """.formatted(namespace, oracle.category(), oracle.locale());
    }

    private static List<CallExpression> queryCalls(Project project) {
        return nodes(project, CallExpression.class).stream()
                .filter(call -> call.callee() instanceof NameExpression name
                        && name.name().endsWith("__ctype_get_mb_cur_max"))
                .toList();
    }

    private static List<CallExpression> callsTo(Project project, String name) {
        return nodes(project, CallExpression.class).stream()
                .filter(call -> call.target().map(target -> target.name().equals(name)).orElse(false)).toList();
    }

    private static List<StringLiteral> localeArguments(Project project) {
        return callsTo(project, "setlocale").stream()
                .map(call -> assertInstanceOf(StringLiteral.class,
                        ExpressionSyntax.written(call.arguments().get(1)))).toList();
    }

    private static String literalText(StringLiteral literal) {
        var result = new StringBuilder();
        for (var unit : literal.codeUnits()) result.appendCodePoint(unit.intValueExact());
        return result.toString();
    }

    private static <T extends Node> List<T> nodes(Project project, Class<T> type) {
        var result = new ArrayList<T>();
        project.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (type.isInstance(node)) result.add(type.cast(node));
            }
        });
        return result;
    }

    private void verify(Project project, String middleLocale) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
        var calls = queryCalls(project);
        assertEquals(4, calls.size(), "Every locale-sensitive query must survive analysis and rewriting");
        var targets = Collections.newSetFromMap(new IdentityHashMap<CallableDeclaration, Boolean>());
        var first = nodes(project, VariableDeclaration.class).stream()
                .filter(variable -> variable.name().equals("first")).findFirst().orElseThrow();
        assertEquals("unsigned long", first.type().orElseThrow().canonicalIdentity());
        for (var call : calls) {
            var target = call.target().orElseThrow();
            targets.add(target);
            assertTrue(LibraryDeclarations.isLibrary(target));
            assertTrue(target.body().isEmpty());
            assertEquals("__ctype_get_mb_cur_max", target.name());
            assertEquals("C", target.languageLinkage());
            assertEquals(first.type().orElseThrow().canonicalIdentity(), target.returnType().orElseThrow().canonicalIdentity());
            assertTrue(target.parameters().isEmpty());
            assertTrue(target.hasExceptionSpecification());
            assertTrue(target.exceptionTypes().isEmpty());
            assertEquals("unsigned long", TypeSemantics.expressionType(call, GLIBC).orElseThrow().canonicalIdentity());
            assertEquals(ValueCategory.RVALUE, TypeSemantics.valueCategory(call, GLIBC));
        }
        assertEquals(1, targets.size(), "All expansions must use one global library helper declaration");
        var selected = callsTo(project, "exact_type");
        assertEquals(1, selected.size());
        assertEquals("unsigned long", selected.getFirst().target().orElseThrow().parameters().getFirst()
                .type().orElseThrow().canonicalIdentity(), "Equal integer widths cannot substitute for exact type identity");
        var ordinary = callsTo(project, "strtol");
        assertEquals(1, ordinary.size());
        assertTrue(LibraryDeclarations.isLibrary(ordinary.getFirst().target().orElseThrow()),
                "Adding the helper must preserve ordinary stdlib declarations");
        assertEquals(List.of("C", middleLocale, "C"), localeArguments(project).stream()
                .map(MbCurMaxRuntimeIT::literalText).toList());
        var preprocessing = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(GLIBC, preprocessing.configuration().platform());
        var definitions = preprocessing.definitions().stream()
                .filter(definition -> definition.name().equals("MB_CUR_MAX")).toList();
        assertFalse(definitions.isEmpty());
        for (var definition : definitions) {
            assertFalse(definition.functionLike());
            assertEquals("library", definition.originKind());
            assertNull(definition.unavailableReason());
            assertEquals("(::__ctype_get_mb_cur_max())", definition.rawReplacement().replaceAll("\\s+", ""));
            assertTrue(project.externalInput(definition.sourceKey()).isPresent());
        }
    }

    private Path writeSource(Path work, String name, String source) throws Exception {
        Path file = Files.createDirectory(work.resolve(name)).resolve("main.cpp");
        Files.writeString(file, source);
        return file;
    }

    private Path materialize(Path source) throws Exception {
        return materialize(source, 4);
    }

    private Path materialize(Path source, int expectedQueries) throws Exception {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(source), source.getParent(), List.of(),
                List.of(), "c++03").withPlatform(GLIBC);
        var preprocessing = CppPreprocessor.preprocess(unit, Files.readString(source));
        var tokens = preprocessing.tokens();
        assertFalse(tokens.stream().anyMatch(token -> token.token().spelling().equals("MB_CUR_MAX")),
                "The native preprocessor must not replace the tested macro with its own implementation");
        assertEquals(expectedQueries, preprocessing.expansions().stream().filter(event -> event.name().equals("MB_CUR_MAX")).count());
        assertEquals(expectedQueries, tokens.stream().filter(token -> token.token().spelling().equals("__ctype_get_mb_cur_max")).count());
        var text = new StringBuilder();
        for (var expanded : tokens) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                text.append("\n#include ").append(token.spelling()).append("\n#undef MB_CUR_MAX\n#undef MB_LEN_MAX\n");
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                text.append(token.spelling()).append(' ');
            }
        }
        // Headers supply declarations, but every query and bound above came from CPG tokens.
        assertTrue(text.toString().replaceAll("\\s+", "").contains("(::__ctype_get_mb_cur_max())"));
        Path file = Files.createTempDirectory(temp, "expanded-").resolve("main.cpp");
        Files.writeString(file, text.append('\n'));
        return file;
    }

    private void nativeRun(Path source, Oracle oracle, boolean edited) throws Exception {
        String expected = "VALUES " + oracle.first() + " " + (edited ? oracle.first() : oracle.middle())
                + " " + oracle.last() + "\nTYPE 17 ORDINARY 17\n";
        for (String compiler : COMPILERS) {
            Path work = Files.createTempDirectory(temp, "native-");
            Path binary = work.resolve("mb-cur-max-check");
            success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
            assertEquals(expected, success(List.of(binary.toString()), work).out(), compiler + " / " + source);
        }
    }

    private record Oracle(int category, String locale, long first, long middle, long last) {}

    private Oracle probe(String compiler) throws Exception {
        Path work = Files.createTempDirectory(temp, "oracle-");
        Path source = work.resolve("probe.cpp"), binary = work.resolve("probe");
        Files.writeString(source, """
                #include <cstdlib>
                #include <climits>
                #include <clocale>
                #include <cstdio>
                #ifndef __GLIBC__
                #error This native oracle requires the explicitly selected glibc runtime
                #endif
                #ifndef __x86_64__
                #error This native oracle requires the explicitly selected x86_64 ABI
                #endif
                #include <gnu/libc-version.h>
                int exact_type(unsigned long value) { return 17; }
                int exact_type(unsigned int value) { return 23; }
                int main() {
                    std::printf("WIDTHS %d %d %d %d\\n", CHAR_BIT, int(sizeof(int)*CHAR_BIT), int(sizeof(long)*CHAR_BIT), int(sizeof(void*)*CHAR_BIT));
                    std::printf("MB_LEN_MAX %d\\nLC_CTYPE %d\\n", MB_LEN_MAX, LC_CTYPE);
                    std::printf("SIZE_TYPE %d %d\\n", exact_type(std::size_t(0)), exact_type(MB_CUR_MAX));
                    std::printf("GLIBC_HEADERS %d.%d\\n", __GLIBC__, __GLIBC_MINOR__);
                    std::printf("GLIBC_RUNTIME %s\\n", gnu_get_libc_version());
                    if (!std::setlocale(LC_CTYPE, "C")) return 90;
                    std::size_t first = MB_CUR_MAX;
                    const char *locales[] = {"C.utf8", "C.UTF-8", "en_US.UTF-8", "en_US.utf8", "UTF-8"};
                    const char *selected = 0;
                    for (unsigned int i = 0; i < sizeof(locales)/sizeof(locales[0]); ++i) {
                        if (std::setlocale(LC_CTYPE, locales[i]) && MB_CUR_MAX != first) {
                            selected = locales[i];
                            break;
                        }
                    }
                    if (!selected) {
                        std::fprintf(stderr, "Missing native runtime coverage: no tested UTF-8 locale provides a changed MB_CUR_MAX. Install a UTF-8 locale.\\n");
                        return 91;
                    }
                    std::size_t middle = MB_CUR_MAX;
                    if (!std::setlocale(LC_CTYPE, "C")) return 92;
                    std::size_t last = MB_CUR_MAX;
                    std::printf("UTF8_LOCALE %s\\nVALUES %lu %lu %lu\\n", selected, first, middle, last);
                    if (first < 1 || middle < 1 || last < 1 || first > MB_LEN_MAX || middle > MB_LEN_MAX || last > MB_LEN_MAX) return 93;
                    if (first != last || first == middle) return 94;
                    return 0;
                }
                """);
        success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
        String observed = success(List.of(binary.toString()), work).out();
        assertTrue(observed.startsWith("WIDTHS 8 32 64 64\n"), "The explicit test profile requires LP64: " + observed);
        assertEquals("16", observation(observed, "MB_LEN_MAX"), observed);
        assertEquals("17 17", observation(observed, "SIZE_TYPE"), "Both size_t and MB_CUR_MAX must be unsigned long: " + observed);
        var values = observation(observed, "VALUES").split(" ");
        var oracle = new Oracle(Integer.parseInt(observation(observed, "LC_CTYPE")),
                observation(observed, "UTF8_LOCALE"), Long.parseLong(values[0]), Long.parseLong(values[1]), Long.parseLong(values[2]));
        assertEquals(oracle.first(), oracle.last(), "Restoring C must restore the live bound");
        assertNotEquals(oracle.first(), oracle.middle(), "UTF-8 transition coverage must not be silently skipped");
        for (long value : List.of(oracle.first(), oracle.middle(), oracle.last())) assertTrue(value > 0 && value <= 16, observed);

        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("compiler", compiler);
        metadata.put("version", success(List.of(compiler, "--version"), work).out());
        metadata.put("target", success(List.of(compiler, "-dumpmachine"), work).out().trim());
        metadata.put("headerAndRuntimeObservations", observed);
        metadata.put("platform", GLIBC.toSettings());
        metadata.put("probeSourceSha256", hash(source));
        metadata.put("probeBinarySha256", hash(binary));
        metadata.put("includeSearch", success(List.of(compiler, "-std=c++03", "-E", "-v",
                source.toString(), "-o", "/dev/null"), work).err());
        String headers = success(List.of(compiler, "-std=c++03", "-H", "-E",
                source.toString(), "-o", "/dev/null"), work).err();
        var hashes = new TreeMap<String, String>();
        for (String line : headers.split("\n")) {
            if (line.matches("\\.+ .*")) {
                Path header = Path.of(line.replaceFirst("^\\.+ ", ""));
                if (Files.isRegularFile(header)) hashes.put(header.toString(), hash(header));
            }
        }
        for (String header : List.of("/stdlib.h", "/locale.h", "/limits.h"))
            assertTrue(hashes.keySet().stream().anyMatch(name -> name.endsWith(header)), headers);
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
        assertFalse(runtimeHashes.isEmpty(), "Record the actual dynamically selected libc: " + dependencies);
        metadata.put("runtimeLibrarySha256", runtimeHashes);
        Path artifacts = Files.createDirectories(Path.of("target/mb-cur-max-oracle"));
        String prefix = compiler.equals("g++") ? "gcc" : "clang";
        Files.writeString(artifacts.resolve(prefix + "-evidence.json"), Json.serialize(metadata));
        Files.writeString(artifacts.resolve(prefix + "-probe.cpp"), Files.readString(source));
        return oracle;
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
