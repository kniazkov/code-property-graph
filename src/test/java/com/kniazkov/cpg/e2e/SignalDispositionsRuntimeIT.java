/* Signal sentinel casts checked against independent strict C++03 libc oracles. MIT License. */
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
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SignalDispositionsRuntimeIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final List<String> MACROS = List.of("SIG_DFL", "SIG_IGN", "SIG_ERR");
    private static final List<List<String>> HEADERS = List.of(List.of("csignal"), List.of("signal.h"),
            List.of("csignal", "signal.h"), List.of("signal.h", "csignal"));
    private static final String BINDING = "glibc-x86_64-lp64";
    private static final String HANDLER = "pointer(function(int)->void)";
    // This explicit capability never discovers the host. Only the independent test oracle does.
    private static final PlatformProfile GLIBC = PlatformProfile.builder().cRuntime("glibc")
            .signalDispositionBinding(BINDING).build();
    private static final String OVERLOADS = """
            typedef void (*handler)(int);
            int exact_type(handler value) { return 1; }
            int exact_type(void *value) { return 2; }
            int exact_type(int value) { return 3; }
            """;

    @Test void nativeDispositionsSurviveApiCliCopyEditsReparsingAndTransforms() throws Exception {
        String observed = probe("g++");
        assertEquals(observed, probe("clang++"),
                "Independent compiler/header/runtime observations must agree on the selected target");
        // SIGTERM remains a separate, independently observed int fact, not part of the binding.
        var profile = GLIBC.toBuilder().runtimeIntegerMacro("SIGTERM", "int", observation(observed, "SIGTERM")).build();
        assertEquals(profile, PlatformProfile.fromSettings(profile.toSettings()));
        assertEquals(profile, profile.toBuilder().build());
        assertEquals(BINDING, profile.toSettings().get("signalDispositionBinding"));
        assertTrue(GLIBC.runtimeIntegerMacros().isEmpty());
        for (var headers : HEADERS) {
            Path work = Files.createTempDirectory(temp, "signal-dispositions-");
            var cli = new CliSandbox(work).source("main.cpp", runtimeProgram(headers));
            var options = options(profile, Cpg.Stage.RESOLVED);
            var project = Cpg.read(cli.input, options);
            verify(project, profile, false, headers.size());
            var copy = project.edit().build();
            verify(copy, profile, false, headers.size());
            verify(NameResolver.resolve(copy), profile, false, headers.size());
            Path settings = work.resolve("profile.json");
            Files.writeString(settings, Json.serialize(profile.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),
                    Json.parse(run.work().resolve("snapshot.json").toFile()));
            Path copied = writeSource(work, "copied", Cpg.sources(copy, Language.CPP).get("main.cpp"));
            for (Path source : List.of(cli.input.resolve("main.cpp"), copied, run.work().resolve("generated/main.cpp"))) {
                verify(Cpg.read(source.getParent(), options), profile, false, headers.size());
                executeBothForms(source, profile, false);
            }
            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input, options(profile, stage));
                verify(transformed, profile, false, headers.size());
                verify(transformed.edit().build(), profile, false, headers.size());
                Path source = writeSource(work, stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verify(Cpg.read(source.getParent(), options), profile, false, headers.size());
                executeBothForms(source, profile, false);
            }
            // Parse the replacement independently, then edit the initializer actually installed below.
            Path replacement = writeSource(work, "replacement", "#include <csignal>\n"
                    + "void (*replacement)(int) = SIG_DFL;\n");
            var replacementProject = Cpg.read(replacement.getParent(), options);
            assertHealthy(replacementProject);
            var replacementCast = ExpressionSyntax.written(variable(replacementProject, "replacement").initializer().orElseThrow());
            var editor = new ProjectEditor(project);
            editor.replace(variable(project, "editable").initializer().orElseThrow(), replacementCast.edit().build());
            var edited = NameResolver.resolve(editor.build());
            verify(edited, profile, true, headers.size());
            verify(edited.edit().build(), profile, true, headers.size());
            Path source = writeSource(work, "edited", Cpg.sources(edited, Language.CPP).get("main.cpp"));
            verify(Cpg.read(source.getParent(), options), profile, true, headers.size());
            executeBothForms(source, profile, true);
            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(source.getParent(), options(profile, stage));
                verify(transformed, profile, true, headers.size());
                Path transformedSource = writeSource(work, "edited-" + stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verify(Cpg.read(transformedSource.getParent(), options), profile, true, headers.size());
                executeBothForms(transformedSource, profile, true);
            }
        }
    }

    @Test void unavailableBindingKeepsPresenceAndContextualApiCliErrorJson() throws Exception {
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().cRuntime("glibc").build())) {
            for (String header : List.of("csignal", "signal.h")) {
                Path work = Files.createTempDirectory(temp, "signal-unavailable-");
                String include = "#include <" + header + ">\n";
                var cli = new CliSandbox(work).source("main.cpp", include + presenceChecks() + "int main(){return 0;}\n");
                var options = options(profile, Cpg.Stage.RESOLVED);
                var unused = Cpg.read(cli.input, options);
                assertHealthy(unused);
                assertHealthy(unused.edit().build());
                for (String name : MACROS) assertNotNull(unused.files().getFirst().provenance().orElseThrow()
                        .unit().definitions().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow().unavailableReason());
                Path settings = work.resolve("profile.json");
                Files.writeString(settings, Json.serialize(profile.toSettings()));
                cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                        "--platform-profile", settings.toString()).expectStatus(0);
                for (String name : MACROS) {
                    Files.writeString(cli.input.resolve("main.cpp"), include + "void (*value)(int) = " + name + ";\nint main(){return 0;}\n");
                    String error = assertThrows(ParseException.class, () -> Cpg.read(cli.input, options)).getMessage();
                    assertTrue(error.contains(name), error);
                    assertTrue(error.contains("signalDispositionBinding"), error);
                    var failed = Cpg.readRecovering(cli.input, options);
                    assertTrue(failed.hasErrors());
                    assertErrorJson(ProjectJson.encode(failed).toText("  "), name);
                    assertErrorJson(ProjectJson.encode(failed.edit().build()).toText("  "), name);
                    assertThrows(IllegalArgumentException.class, () -> Cpg.sources(failed, Language.CPP));
                    var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                            "--platform-profile", settings.toString()).expectStatus(1);
                    String json = Files.readString(run.work().resolve("snapshot.json"));
                    Json.parse(json);
                    assertErrorJson(json, name);
                    assertFalse(Files.exists(run.work().resolve("generated")));
                }
            }
        }
    }

    @Test void nativeCompilersRejectAssignmentIncrementAndAddressOfMaterializedRvalues() throws Exception {
        for (String header : List.of("csignal", "signal.h")) {
            for (String name : MACROS) {
                for (String misuse : List.of(name + " = 0;", "++" + name + ";", "void (*p)(int) = &" + name + ";")) {
                    Path work = Files.createTempDirectory(temp, "signal-rvalue-");
                    Path source = work.resolve("main.cpp");
                    Files.writeString(source, "#include <" + header + ">\nint main(){" + misuse + " return 0;}\n");
                    for (Path input : List.of(source, materialize(source, GLIBC))) {
                        for (String compiler : COMPILERS) {
                            Path binary = Files.createTempDirectory(temp, "signal-rejected-").resolve("invalid");
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
    }

    private static Cpg.Options options(PlatformProfile profile, Cpg.Stage stage) {
        return new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(profile);
    }

    private static String includes(List<String> headers) {
        return headers.stream().map(header -> "#include <" + header + ">\n").reduce("", String::concat);
    }

    private static String presenceChecks() {
        return "#if !defined(SIG_DFL) || !defined(SIG_IGN) || !defined(SIG_ERR)\n"
                + "#error All three signal disposition macros must be present\n#endif\n";
    }

    private static String runtimeProgram(List<String> headers) {
        String namespace = headers.contains("csignal") ? "std::" : "::";
        return includes(headers) + "#include <cstdio>\n" + presenceChecks() + OVERLOADS + """
                handler selected_default = SIG_DFL;
                handler selected_ignore = SIG_IGN;
                handler selected_error = SIG_ERR;
                handler editable = SIG_IGN;
                int tested_signum = SIGTERM;
                int calls = 0;
                handler touch() { ++calls; return SIG_IGN; }
                void declared_handler(int number) {}
                int main() {
                    if (exact_type(SIG_DFL) != 1 || exact_type(SIG_IGN) != 1 || exact_type(SIG_ERR) != 1) return 1;
                    if (selected_default == selected_ignore || selected_default == selected_error || selected_ignore == selected_error) return 2;
                    if (selected_default == &declared_handler || selected_ignore == &declared_handler || selected_error == &declared_handler) return 3;
                    unsigned long unevaluated = sizeof(touch());
                    if (unevaluated != sizeof(handler) || calls != 0) return 4;
                    handler original = %1$ssignal(SIGTERM, selected_ignore);
                    if (original == selected_error) return 5;
                    handler previous = %1$ssignal(SIGTERM, selected_default);
                    int failed = previous != selected_ignore;
                    handler restored = %1$ssignal(SIGTERM, original);
                    if (restored == selected_error) { %1$ssignal(SIGTERM, original); return 6; }
                    if (restored != selected_default) failed = 1;
                    if (failed) return 7;
                    handler &editable_reference = editable;
                    handler saved_alias = editable_reference;
                    handler original_edit = %1$ssignal(SIGTERM, saved_alias);
                    if (original_edit == selected_error) return 8;
                    handler previous_edit = %1$ssignal(SIGTERM, original_edit);
                    if (previous_edit == selected_error) { %1$ssignal(SIGTERM, original_edit); return 9; }
                    if (previous_edit != saved_alias) return 10;
                    std::printf("EDITABLE_IGNORE %%d PREVIOUS_IGNORE %%d TYPE %%d CALLS %%d\\n",
                            editable == selected_ignore, previous_edit == selected_ignore, exact_type(editable), calls);
                    return 0;
                }
                """.formatted(namespace);
    }

    private static <T extends Node> List<T> nodes(Project project, Class<T> type) {
        var result = new ArrayList<T>();
        project.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }
        });
        return result;
    }

    private static VariableDeclaration variable(Project project, String name) {
        return nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }

    private static void assertHealthy(Project project) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
    }

    private static void verify(Project project, PlatformProfile profile, boolean edited, int headerCount) throws Exception {
        verifyCasts(project, profile, edited);
        var pp = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(profile, pp.configuration().platform());
        for (String name : MACROS) {
            var definitions = pp.definitions().stream().filter(d -> d.name().equals(name)).toList();
            assertEquals(headerCount, definitions.size(), name + " needs provenance for each supplying header");
            assertEquals(headerCount, definitions.stream().map(d -> d.sourceKey()).distinct().count());
            String number = name.equals("SIG_DFL") ? "0" : name.equals("SIG_IGN") ? "1" : "-1";
            for (var definition : definitions) {
                assertFalse(definition.functionLike());
                assertNull(definition.unavailableReason());
                assertEquals("((void (*)(int)) " + number + ")", definition.rawReplacement());
                assertEquals("library", definition.originKind());
                assertTrue(project.externalInput(definition.sourceKey()).isPresent());
            }
            assertTrue(pp.expansions().stream().anyMatch(event -> event.name().equals(name)), name);
        }
    }

    private static void verifyCasts(Project project, PlatformProfile profile, boolean edited) throws Exception {
        assertHealthy(project);
        var expected = Map.of("selected_default", 0, "selected_ignore", 1, "selected_error", -1, "editable", edited ? 0 : 1);
        for (var entry : expected.entrySet()) {
            var variable = variable(project, entry.getKey());
            assertEquals(HANDLER, variable.type().orElseThrow().canonicalIdentity());
            var cast = assertInstanceOf(ConversionExpression.class, ExpressionSyntax.written(variable.initializer().orElseThrow()));
            assertTrue(cast.isExplicit());
            assertEquals(ConversionExpression.Kind.C_STYLE_CAST, cast.kind());
            var function = assertInstanceOf(FunctionType.class, assertInstanceOf(PointerType.class, cast.targetType()).pointee());
            assertEquals("void", function.returnType().canonicalIdentity());
            assertEquals(1, function.parameters().size());
            assertEquals("int", function.parameters().getFirst().type().orElseThrow().canonicalIdentity());
            assertFalse(function.variadic());
            assertEquals(HANDLER, TypeSemantics.expressionType(cast, profile).orElseThrow().canonicalIdentity());
            assertEquals(ValueCategory.RVALUE, TypeSemantics.valueCategory(cast, profile));
            // Inspect only written integer operands. Never evaluate sentinel pointers as numbers.
            assertEquals(BigInteger.valueOf(entry.getValue()), integerOperand(cast.operand()));
        }
        assertTrue(nodes(project, CallableDeclaration.class).stream().noneMatch(d -> MACROS.contains(d.name())
                || d.name().contains("signal_disposition") || d.name().contains("__cpg")), "No fabricated callable sentinel/helper");
        var calls = nodes(project, CallExpression.class);
        assertEquals(4, calls.stream().filter(c -> c.target().map(t -> t.name().equals("exact_type")).orElse(false)).count());
        for (var call : calls) if (call.target().map(t -> t.name().equals("exact_type")).orElse(false))
            assertEquals(HANDLER, call.target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
        assertEquals(7, calls.stream().filter(c -> c.target().map(t -> t.name().equals("signal")).orElse(false)).count());
        assertTrue(calls.stream().noneMatch(c -> c.target().map(t -> Set.of("raise", "kill").contains(t.name())).orElse(false)));
        String json = ProjectJson.encode(project).toText("  ");
        Json.parse(json);
        assertTrue(json.contains("c_style_cast"), json);
        assertTrue(json.contains(HANDLER), json);
        assertTrue(json.contains(BINDING), json);
    }

    private static BigInteger integerOperand(Expression expression) {
        expression = ExpressionSyntax.written(expression);
        if (expression instanceof IntegerLiteral literal) return literal.bigValue();
        if (expression instanceof NegateExpression negate) return integerOperand(negate.operand()).negate();
        throw new AssertionError("Expected an unchanged symbolic cast operand, got " + expression.getClass());
    }

    private static void assertErrorJson(String json, String name) {
        for (String expected : List.of("unavailable-library-macro", "unavailableReason", name, "signalDispositionBinding", "main.cpp"))
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
        assertTrue(pp.expansions().stream().anyMatch(event -> MACROS.contains(event.name())));
        var forbidden = new HashSet<>(MACROS);
        forbidden.add("SIGTERM");
        assertFalse(pp.tokens().stream().anyMatch(token -> forbidden.contains(token.token().spelling())),
                "Native-tested sentinel and signal-number occurrences must come from CPG, never the host macro");
        var text = new StringBuilder();
        boolean bodyStarted = false;
        for (var expanded : pp.tokens()) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                assertFalse(bodyStarted, "Fixture headers must be a prefix; never hoist a later include");
                text.append("#include ").append(token.spelling()).append('\n');
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                if (!bodyStarted) {
                    text.append("#undef SIG_DFL\n#undef SIG_IGN\n#undef SIG_ERR\n#undef SIGTERM\n");
                    bodyStarted = true;
                }
                assertFalse(forbidden.contains(token.spelling()));
                text.append(token.spelling()).append(' ');
            }
        }
        assertTrue(bodyStarted);
        Path file = Files.createTempDirectory(temp, "signal-materialized-").resolve("main.cpp");
        Files.writeString(file, text.append('\n'));
        return file;
    }

    private void executeBothForms(Path source, PlatformProfile profile, boolean edited) throws Exception {
        nativeRuntime(source, edited);
        Path materialized = materialize(source, profile);
        verifyCasts(Cpg.read(materialized.getParent(), options(profile, Cpg.Stage.RESOLVED)), profile, edited);
        nativeRuntime(materialized, edited);
    }

    private void nativeRuntime(Path source, boolean edited) throws Exception {
        for (String compiler : COMPILERS) {
            Path work = Files.createTempDirectory(temp, "signal-native-");
            Path wrapper = work.resolve("oracle.cpp"), binary = work.resolve("signal-check");
            // Capture real-header values before the actual tested source disables the host macros.
            // GCC/Clang establish this structural ABI contract, not distinct C/C++ function linkage types.
            Files.writeString(wrapper, """
                    #include <csignal>
                    #include <signal.h>
                    #include <cstdio>
                    extern "C" { typedef void host_function(int); }
                    typedef host_function *host_handler;
                    int host_type(host_handler value) { return 1; }
                    template<class T> int host_type(T value) { return 2; }
                    host_handler host_default = SIG_DFL;
                    host_handler host_ignore = SIG_IGN;
                    host_handler host_error = SIG_ERR;
                    int host_default_type = host_type(SIG_DFL);
                    int host_ignore_type = host_type(SIG_IGN);
                    int host_error_type = host_type(SIG_ERR);
                    int host_sigterm = SIGTERM;
                    #define main tested_main
                    #include "%s"
                    #undef main
                    int main() {
                        if (host_default_type != 1 || host_ignore_type != 1 || host_error_type != 1) return 91;
                        if (host_type(selected_default) != 1 || host_type(selected_ignore) != 1 || host_type(selected_error) != 1) return 92;
                        if (selected_default != host_default || selected_ignore != host_ignore || selected_error != host_error) return 93;
                        if (tested_signum != host_sigterm) return 94;
                        return tested_main();
                    }
                    """.formatted(source.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"")));
            success(List.of(compiler, "-std=c++03", "-pedantic-errors", wrapper.toString(), "-o", binary.toString()), work);
            var result = success(List.of(binary.toString()), work);
            int ignore = edited ? 0 : 1;
            assertEquals("EDITABLE_IGNORE " + ignore + " PREVIOUS_IGNORE " + ignore + " TYPE 1 CALLS 0\n",
                    result.out(), compiler + " / " + source);
            assertEquals("", result.err());
            String undefined = success(List.of("nm", "-u", binary.toString()), work).out();
            assertNoSentinelHelper(undefined);
            assertTrue(undefined.matches("(?s).*\\b(?:__sysv_)?signal(?:@|\\s).*"), undefined);
        }
    }

    private String probe(String compiler) throws Exception {
        Path work = Files.createTempDirectory(temp, "signal-oracle-");
        Path source = work.resolve("probe.cpp"), binary = work.resolve("probe");
        Files.writeString(source, """
                #include <csignal>
                #include <signal.h>
                #include <cstdio>
                #include <climits>
                #ifndef __GLIBC__
                #error This oracle requires the explicitly selected glibc runtime
                #endif
                #ifndef __x86_64__
                #error This oracle requires the explicitly selected x86_64 ABI
                #endif
                #include <gnu/libc-version.h>
                """ + OVERLOADS + """
                handler host_default = SIG_DFL;
                handler host_ignore = SIG_IGN;
                handler host_error = SIG_ERR;
                int host_default_type = exact_type(SIG_DFL);
                int host_ignore_type = exact_type(SIG_IGN);
                int host_error_type = exact_type(SIG_ERR);
                int int_type(int value) { return 1; }
                int int_type(long value) { return 2; }
                int int_type(unsigned int value) { return 3; }
                int int_type(unsigned long value) { return 4; }
                int host_sigterm = SIGTERM;
                int host_sigterm_type = int_type(SIGTERM);
                #undef SIG_DFL
                #undef SIG_IGN
                #undef SIG_ERR
                #undef SIGTERM
                handler selected_default = ((void (*)(int)) 0);
                handler selected_ignore = ((void (*)(int)) 1);
                handler selected_error = ((void (*)(int)) -1);
                void declared_handler(int number) {}
                int main() {
                    std::printf("WIDTHS %d %d %d %d\\n", CHAR_BIT, int(sizeof(int)*CHAR_BIT), int(sizeof(long)*CHAR_BIT), int(sizeof(void*)*CHAR_BIT));
                    std::printf("GLIBC_HEADERS %d.%d\\nGLIBC_RUNTIME %s\\n", __GLIBC__, __GLIBC_MINOR__, gnu_get_libc_version());
                    std::printf("SIGTERM %d\\nSIGTERM_TYPE %d\\n", host_sigterm, host_sigterm_type);
                    std::printf("HOST_TYPES %d %d %d\\n", host_default_type, host_ignore_type, host_error_type);
                    std::printf("SELECTED_TYPES %d %d %d\\n", exact_type(selected_default), exact_type(selected_ignore), exact_type(selected_error));
                    int valid = selected_default == host_default && selected_ignore == host_ignore && selected_error == host_error;
                    int distinct = host_default != host_ignore && host_default != host_error && host_ignore != host_error;
                    int non_function = host_default != &declared_handler && host_ignore != &declared_handler && host_error != &declared_handler;
                    std::printf("VALUES_MATCH %d\\nDISTINCT %d\\nNON_FUNCTION %d\\n", valid, distinct, non_function);
                    return !(valid && distinct && non_function);
                }
                """);
        success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
        var run = success(List.of(binary.toString()), work);
        String observed = run.out();
        assertEquals("", run.err());
        assertEquals("8 32 64 64", observation(observed, "WIDTHS"));
        assertTrue(Integer.parseInt(observation(observed, "SIGTERM")) > 0);
        assertEquals("1", observation(observed, "SIGTERM_TYPE"));
        assertEquals("1 1 1", observation(observed, "HOST_TYPES"));
        assertEquals("1 1 1", observation(observed, "SELECTED_TYPES"));
        for (String key : List.of("VALUES_MATCH", "DISTINCT", "NON_FUNCTION")) assertEquals("1", observation(observed, key));
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
        assertNoSentinelHelper(symbols);
        metadata.put("undefinedSymbols", symbols);
        var nativeDefinitions = new LinkedHashMap<String, List<String>>();
        for (var headers : HEADERS) {
            Path macros = work.resolve("macros-" + nativeDefinitions.size() + ".cpp");
            Files.writeString(macros, includes(headers));
            var definitions = success(List.of(compiler, "-std=c++03", "-dM", "-E", macros.toString()), work).out()
                    .lines().filter(line -> MACROS.stream().anyMatch(name -> line.startsWith("#define " + name + " "))).toList();
            assertEquals(3, definitions.size());
            nativeDefinitions.put(String.join(",", headers), definitions);
        }
        metadata.put("nativeSignalDispositionMacroDefinitions", nativeDefinitions);
        var hostIfObservations = new LinkedHashMap<String, Object>();
        var selectedIfRejections = new LinkedHashMap<String, Object>();
        var ifSources = new ArrayList<Path>();
        for (String name : MACROS) {
            Path host = work.resolve("host-if-" + name + ".cpp");
            Files.writeString(host, "#include <csignal>\n#if " + name + " == 0\nint selected;\n#endif\n");
            var hostResult = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-E", host.toString(), "-o", "/dev/null"), work);
            // A host typedef spelling can accidentally parse after #if replaces identifiers by 0:
            // ((__sighandler_t) -1) becomes ((0) -1). This does not establish a portable #if contract.
            hostIfObservations.put(name, Map.of("exitCode", hostResult.code(), "diagnostics", hostResult.err(), "sourceSha256", hash(host)));
            String value = name.equals("SIG_DFL") ? "0" : name.equals("SIG_IGN") ? "1" : "-1";
            Path selected = work.resolve("selected-if-" + name + ".cpp");
            Files.writeString(selected, "#define " + name + " ((void (*)(int)) " + value + ")\n"
                    + "#if " + name + " == 0\nint selected;\n#endif\n");
            var rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-E", selected.toString(), "-o", "/dev/null"), work);
            assertNotEquals(0, rejected.code(), "The selected function-pointer cast is not a #if integer expression");
            assertFalse(rejected.err().isBlank());
            selectedIfRejections.put(name, Map.of("exitCode", rejected.code(), "diagnostics", rejected.err(), "sourceSha256", hash(selected)));
            ifSources.add(host);
            ifSources.add(selected);
        }
        metadata.put("hostIfObservations", hostIfObservations);
        metadata.put("selectedIfRejections", selectedIfRejections);
        String included = success(List.of(compiler, "-std=c++03", "-H", "-E", source.toString(), "-o", "/dev/null"), work).err();
        var hashes = new TreeMap<String, String>();
        for (String line : included.split("\n")) {
            if (line.matches("\\.+ .*")) {
                Path header = Path.of(line.replaceFirst("^\\.+ ", ""));
                if (Files.isRegularFile(header)) hashes.put(header.toString(), hash(header));
            }
        }
        for (String header : List.of("/csignal", "/signal.h", "/bits/signum-generic.h", "/gnu/libc-version.h"))
            assertTrue(hashes.keySet().stream().anyMatch(path -> path.endsWith(header)), included);
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
        Path artifacts = Files.createDirectories(Path.of("target/signal-dispositions-oracle"));
        String prefix = compiler.equals("g++") ? "gcc" : "clang";
        Files.writeString(artifacts.resolve(prefix + "-evidence.json"), Json.serialize(metadata));
        Files.writeString(artifacts.resolve(prefix + "-probe.cpp"), Files.readString(source));
        for (Path ifSource : ifSources)
            Files.writeString(artifacts.resolve(prefix + "-" + ifSource.getFileName()), Files.readString(ifSource));
        return observed;
    }

    private static void assertNoSentinelHelper(String undefined) {
        for (String name : MACROS) assertFalse(undefined.contains(name), "No sentinel runtime helper: " + undefined);
        assertFalse(undefined.contains("__cpg"), undefined);
        assertFalse(undefined.contains("signal_disposition"), undefined);
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
