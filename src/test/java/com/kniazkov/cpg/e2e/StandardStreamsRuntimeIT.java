/* CPG standard streams linked to independently observed glibc pointer objects. MIT License. */
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

class StandardStreamsRuntimeIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final List<String> STREAMS = List.of("stdin", "stdout", "stderr");
    private static final String BINDING = "glibc-x86_64-lp64";
    private static final String FILE_POINTER = "pointer(opaque-library(library::FILE))";
    private static final PlatformProfile GLIBC = PlatformProfile.builder().cRuntime("glibc")
            .standardStreamsBinding(BINDING).build();
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03")
            .withPlatform(GLIBC);
    private static final List<List<String>> HEADERS = List.of(List.of("cstdio"), List.of("stdio.h"),
            List.of("cstdio", "stdio.h"), List.of("stdio.h", "cstdio"));

    @Test void liveStreamsSurviveApiCliCopyMaterializationAndPayloadEditing() throws Exception {
        assertEquals(probe("g++"), probe("clang++"),
                "Independent real-header oracles must describe the same selected runtime");
        assertEquals(GLIBC, PlatformProfile.fromSettings(GLIBC.toSettings()));
        assertEquals(GLIBC, GLIBC.toBuilder().build());
        assertEquals(BINDING, GLIBC.toSettings().get("standardStreamsBinding"));
        for (var headers : HEADERS) {
            Path work = Files.createTempDirectory(temp, "streams-");
            var cli = new CliSandbox(work).source("main.cpp", program(headers));
            var project = Cpg.read(cli.input, OPTIONS);
            verify(project, false);
            var copy = project.edit().build();
            verify(copy, false);

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
                verify(Cpg.read(source.getParent(), OPTIONS), false);
                nativeRun(source, false);
                nativeRun(materialize(source), false);
            }

            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var options = new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(GLIBC);
                var transformed = Cpg.read(cli.input, options);
                verify(transformed, false);
                verify(transformed.edit().build(), false);
                Path source = writeSource(work, stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verify(Cpg.read(source.getParent(), OPTIONS), false);
                nativeRun(source, false);
                nativeRun(materialize(source), false);
            }

            var editor = new ProjectEditor(project);
            var strings = payloadStrings(project);
            var replacements = payloads(true);
            assertEquals(3, strings.size());
            for (int i = 0; i < strings.size(); i++) {
                String replacement = replacements.get(i);
                editor.replace(strings.get(i), StringLiteral.create().setCodeUnits(replacement.codePoints()
                        .mapToObj(value -> BigInteger.valueOf(value)).toList()).build());
            }
            var characters = payloadCharacters(project);
            assertEquals(2, characters.size());
            for (var character : characters) {
                editor.replace(character, CharacterLiteral.create().setValue(BigInteger.valueOf('B'))
                        .setSpelling("'B'").build());
            }
            var edited = editor.build();
            verify(edited, true);
            Path source = writeSource(work, "edited", Cpg.sources(edited, Language.CPP).get("main.cpp"));
            verify(Cpg.read(source.getParent(), OPTIONS), true);
            // Separate captured channels and temporary-file readback must observe all five actual edits.
            nativeRun(source, true);
            nativeRun(materialize(source), true);
        }
    }

    @Test void unavailableBindingPreservesPresenceAndContextualApiCliErrorJson() throws Exception {
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().cRuntime("glibc").build())) {
            var options = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03").withPlatform(profile);
            for (String header : List.of("cstdio", "stdio.h")) {
                Path work = Files.createTempDirectory(temp, "unavailable-");
                String include = "#include <" + header + ">\n";
                String namespace = header.equals("cstdio") ? "std::" : "";
                var cli = new CliSandbox(work).source("main.cpp", include + presenceChecks()
                        + "int main(){return " + namespace + "fflush(0);}\n");
                Path settings = work.resolve("profile.json");
                Files.writeString(settings, Json.serialize(profile.toSettings()));
                var unused = Cpg.read(cli.input, options);
                assertFalse(unused.hasErrors(), () -> unused.parseProblems() + " / " + unused.semanticDiagnostics());
                assertFalse(unused.edit().build().hasErrors());
                var definitions = unused.files().getFirst().provenance().orElseThrow().unit().definitions();
                for (String stream : STREAMS) {
                    assertNotNull(definitions.stream().filter(definition -> definition.name().equals(stream))
                            .findFirst().orElseThrow().unavailableReason());
                }
                cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                        "--platform-profile", settings.toString()).expectStatus(0);

                for (String stream : STREAMS) {
                    Files.writeString(cli.input.resolve("main.cpp"), include
                            + "int main(){return " + stream + " == 0;}\n");
                    String error = assertThrows(ParseException.class, () -> Cpg.read(cli.input, options)).getMessage();
                    assertTrue(error.contains(stream), error);
                    assertTrue(error.contains("standardStreamsBinding"), error);
                    var failed = Cpg.readRecovering(cli.input, options);
                    assertTrue(failed.hasErrors());
                    assertErrorJson(ProjectJson.encode(failed).toText("  "), stream);
                    assertErrorJson(ProjectJson.encode(failed.edit().build()).toText("  "), stream);
                    assertThrows(IllegalArgumentException.class, () -> Cpg.sources(failed, Language.CPP));
                    var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                            "--platform-profile", settings.toString()).expectStatus(1);
                    String json = Files.readString(run.work().resolve("snapshot.json"));
                    Json.parse(json);
                    assertErrorJson(json, stream);
                    assertFalse(Files.exists(run.work().resolve("generated")));
                }
            }
        }
    }

    @Test void nativeCompilersRejectInventedStdStreamAlias() throws Exception {
        // This is independent native evidence; the unit tests exercise CPG lookup diagnostics.
        for (boolean undefine : List.of(false, true)) {
            Path work = Files.createTempDirectory(temp, "invalid-namespace-");
            Path source = work.resolve("main.cpp");
            Files.writeString(source, "#include <cstdio>\n"
                    + (undefine ? "#undef stdout\n" : "")
                    + "int main(){return std::stdout == 0;}\n");
            for (String compiler : COMPILERS) {
                var result = command(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(),
                        "-o", work.resolve("invalid").toString()), work, "");
                assertNotEquals(0, result.code(), compiler + " must reject the fabricated std::stdout alias");
                assertTrue(result.err().contains("stdout"), result.err());
            }
        }
    }

    private static void assertErrorJson(String json, String stream) {
        for (String expected : List.of("unavailable-library-macro", "unavailableReason", stream,
                "standardStreamsBinding", "main.cpp")) assertTrue(json.contains(expected), json);
    }

    private static String presenceChecks() {
        return "#if !defined(stdin) || !defined(stdout) || !defined(stderr)\n"
                + "#error All three standard stream macros must be present\n#endif\n";
    }

    private static String program(List<String> headers) {
        String includes = headers.stream().map(header -> "#include <" + header + ">\n")
                .reduce("", String::concat);
        String namespace = headers.contains("cstdio") ? "std::" : "";
        return includes + presenceChecks() + """
                int main() {
                    %1$sFILE **input_cell = &stdin;
                    %1$sFILE **error_cell = &stderr;
                    %1$sFILE *&input_reference = stdin;
                    %1$sFILE *&error_reference = stderr;
                    if (&input_reference != input_cell || &error_reference != error_cell) return 1;
                    if (*input_cell != ::stdin || *error_cell != ::stderr) return 2;
                    int input = %1$sfgetc(stdin);
                    int newline = %1$sfgetc(::stdin);
                    if (input != 'Q' || newline != '\\n') return 3;
                    if (%1$sfputc(input, stdout) < 0 || %1$sfputc(newline, stdout) < 0) return 4;
                    if (%1$sfputs("OUT original\\n", ::stdout) < 0) return 5;
                    if (%1$sfputc('A', stdout) < 0 || %1$sfputc('\\n', stdout) < 0) return 6;
                    if (%1$sfputs("ERR original\\n", stderr) < 0 || %1$sfflush(stderr) != 0) return 7;
                    if (%1$sfflush(stdout) != 0) return 8;

                    %1$sFILE *saved = stdout;
                    %1$sFILE **output_cell = &stdout;
                    %1$sFILE *&output_reference = stdout;
                    if (&output_reference != output_cell) return 9;
                    %1$sFILE *temporary = %1$stmpfile();
                    if (!temporary) return 10;
                    int failed = 0;
                    output_reference = temporary;
                    if (stdout != temporary || *output_cell != temporary) failed = 1;
                    if (%1$sfputs("TMP original:", stdout) < 0 || %1$sfflush(stdout) != 0) failed = 1;
                    *output_cell = saved;
                    if (stdout != saved || output_reference != saved) failed = 1;
                    *output_cell = temporary;
                    if (stdout != temporary || output_reference != temporary) failed = 1;
                    if (%1$sfputc('A', stdout) < 0 || %1$sfputc('\\n', stdout) < 0) failed = 1;
                    if (%1$sfflush(stdout) != 0) failed = 1;
                    output_reference = saved;
                    if (stdout != saved || *output_cell != saved) failed = 1;
                    // Restore the original pointer before every close or early return.
                    stdout = saved;
                    if (failed) { %1$sfclose(temporary); return 11; }
                    %1$srewind(temporary);
                    int ch = 0;
                    while ((ch = %1$sfgetc(temporary)) >= 0) {
                        if (%1$sfputc(ch, stdout) < 0) failed = 1;
                    }
                    if (%1$sferror(temporary)) failed = 1;
                    if (%1$sfclose(temporary) != 0) return 12;
                    if (failed || %1$sfflush(stdout) != 0 || %1$sfflush(stderr) != 0) return 13;
                    if (*input_cell != stdin || *error_cell != stderr || *output_cell != saved) return 14;
                    return 0;
                }
                """.formatted(namespace);
    }

    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (type.isInstance(node)) result.add(type.cast(node));
            }
        });
        return result;
    }

    private static List<CallExpression> callsTo(Project project, String name) {
        return nodes(project.files().getFirst(), CallExpression.class).stream()
                .filter(call -> call.target().map(target -> target.name().equals(name)).orElse(false)).toList();
    }

    private static List<StringLiteral> payloadStrings(Project project) {
        return callsTo(project, "fputs").stream().map(call -> assertInstanceOf(StringLiteral.class,
                ExpressionSyntax.written(call.arguments().getFirst()))).toList();
    }

    private static List<CharacterLiteral> payloadCharacters(Project project) {
        return callsTo(project, "fputc").stream().map(call -> ExpressionSyntax.written(call.arguments().getFirst()))
                .filter(CharacterLiteral.class::isInstance).map(CharacterLiteral.class::cast)
                .filter(literal -> literal.value().equals(BigInteger.valueOf('A'))
                        || literal.value().equals(BigInteger.valueOf('B'))).toList();
    }

    private static List<BigInteger> payloadCharacterValues(Project project) {
        var result = new ArrayList<BigInteger>();
        for (var call : callsTo(project, "fputc")) {
            var expression = ExpressionSyntax.written(call.arguments().getFirst());
            BigInteger value;
            if (expression instanceof CharacterLiteral literal) value = literal.value();
            else if (expression instanceof IntegerLiteral literal) value = literal.bigValue();
            else continue;
            if (value.equals(BigInteger.valueOf('A')) || value.equals(BigInteger.valueOf('B'))) result.add(value);
        }
        return List.copyOf(result);
    }

    private static List<String> payloads(boolean edited) {
        String word = edited ? "edited" : "original";
        return List.of("OUT " + word + "\n", "ERR " + word + "\n", "TMP " + word + ":");
    }

    private static String literalText(StringLiteral literal) {
        var result = new StringBuilder();
        for (var unit : literal.codeUnits()) result.appendCodePoint(unit.intValueExact());
        return result.toString();
    }

    private static void verify(Project project, boolean edited) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
        var targets = new LinkedHashMap<String, VariableDeclaration>();
        for (var expression : nodes(project.files().getFirst(), NameExpression.class)) {
            String name = expression.name().replaceFirst("^::", "");
            if (!STREAMS.contains(name)) continue;
            var target = assertInstanceOf(VariableDeclaration.class, expression.target().orElseThrow());
            var previous = targets.putIfAbsent(name, target);
            if (previous != null) assertSame(previous, target, "One real pointer cell per stream, across both headers");
            assertTrue(LibraryDeclarations.isLibrary(target));
            assertEquals(name, target.name());
            assertEquals("C", target.languageLinkage());
            assertEquals(Declaration.Linkage.EXTERNAL, target.linkage());
            assertTrue(target.modifiers().contains("extern"));
            assertTrue(target.initializer().isEmpty());
            assertFalse(target.isDefinition());
            var pointer = assertInstanceOf(PointerType.class, target.type().orElseThrow().canonicalType());
            assertEquals(FILE_POINTER, pointer.canonicalIdentity());
            var file = assertInstanceOf(OpaqueLibraryType.class, pointer.pointee().canonicalType());
            assertEquals("library::FILE", file.identity());
            assertTrue(TypeSemantics.sizeof(file, GLIBC).isEmpty(), "The binding must not invent FILE layout");
            assertEquals(BigInteger.valueOf(8), TypeSemantics.sizeof(pointer, GLIBC).orElseThrow());
            assertEquals(FILE_POINTER, TypeSemantics.expressionType(expression, GLIBC).orElseThrow().canonicalIdentity());
            assertEquals(ValueCategory.LVALUE, TypeSemantics.valueCategory(expression, GLIBC));
        }
        assertEquals(new HashSet<>(STREAMS), targets.keySet());
        for (var library : project.librarySources()) {
            for (var namespace : nodes(library, NamespaceDeclaration.class)) {
                assertTrue(nodes(namespace, VariableDeclaration.class).stream()
                        .noneMatch(variable -> STREAMS.contains(variable.name())), "No fabricated std stream aliases");
            }
        }
        for (String function : List.of("fputs", "fgetc", "fputc", "fflush", "tmpfile", "rewind", "ferror", "fclose")) {
            var calls = callsTo(project, function);
            assertFalse(calls.isEmpty(), function + " must remain available");
            for (var call : calls) assertTrue(LibraryDeclarations.isLibrary(call.target().orElseThrow()));
        }
        var fputs = callsTo(project, "fputs").getFirst().target().orElseThrow();
        assertEquals(FILE_POINTER, fputs.parameters().get(1).type().orElseThrow().canonicalIdentity());
        assertEquals(FILE_POINTER, callsTo(project, "tmpfile").getFirst().target().orElseThrow()
                .returnType().orElseThrow().canonicalIdentity());
        assertEquals(payloads(edited), payloadStrings(project).stream().map(StandardStreamsRuntimeIT::literalText).toList());
        assertEquals(List.of(BigInteger.valueOf(edited ? 'B' : 'A'), BigInteger.valueOf(edited ? 'B' : 'A')),
                payloadCharacterValues(project));
        var preprocessing = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(GLIBC, preprocessing.configuration().platform());
        for (String stream : STREAMS) {
            var definitions = preprocessing.definitions().stream().filter(definition -> definition.name().equals(stream)).toList();
            assertFalse(definitions.isEmpty());
            for (var definition : definitions) {
                assertFalse(definition.functionLike());
                assertEquals("library", definition.originKind());
                assertNull(definition.unavailableReason());
                assertEquals(stream, definition.rawReplacement().trim(), "The real glibc macro is self-referential");
                assertTrue(project.externalInput(definition.sourceKey()).isPresent());
            }
            assertTrue(preprocessing.expansions().stream().anyMatch(expansion -> expansion.name().equals(stream)));
        }
    }

    private Path writeSource(Path work, String name, String source) throws Exception {
        Path file = Files.createDirectory(work.resolve(name)).resolve("main.cpp");
        Files.writeString(file, source);
        return file;
    }

    private Path materialize(Path source) throws Exception {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(source), source.getParent(), List.of(),
                List.of(), "c++03").withPlatform(GLIBC);
        var preprocessing = CppPreprocessor.preprocess(unit, Files.readString(source));
        for (String stream : STREAMS) {
            var tokens = preprocessing.tokens().stream().filter(token -> token.token().spelling().equals(stream)).toList();
            assertFalse(tokens.isEmpty());
            var expansions = preprocessing.expansions().stream().filter(event -> event.name().equals(stream)).toList();
            assertEquals(tokens.size(), expansions.size(), "Every stream token must come from one terminating self-expansion");
            for (var token : tokens) {
                assertEquals(1, token.origins().stream().filter(origin -> origin.name().equals(stream)).count());
            }
        }
        var text = new StringBuilder();
        for (var expanded : preprocessing.tokens()) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                text.append("\n#include ").append(token.spelling())
                        .append("\n#undef stdin\n#undef stdout\n#undef stderr\n");
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                text.append(token.spelling()).append(' ');
            }
        }
        // Keep genuine FILE declarations, but never let host stream macros replace CPG's emitted tokens.
        assertTrue(text.toString().contains("#undef stdin\n#undef stdout\n#undef stderr\n"));
        Path file = Files.createTempDirectory(temp, "expanded-").resolve("main.cpp");
        Files.writeString(file, text.append('\n'));
        return file;
    }

    private void nativeRun(Path source, boolean edited) throws Exception {
        String word = edited ? "edited" : "original", character = edited ? "B" : "A";
        String expectedOut = "Q\nOUT " + word + "\n" + character + "\nTMP " + word + ":" + character + "\n";
        String expectedErr = "ERR " + word + "\n";
        for (String compiler : COMPILERS) {
            Path work = Files.createTempDirectory(temp, "native-");
            Path binary = work.resolve("streams-check");
            success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
            var result = success(List.of(binary.toString()), work, "Q\n");
            assertEquals(expectedOut, result.out(), compiler + " / " + source + " / stdout");
            assertEquals(expectedErr, result.err(), compiler + " / " + source + " / stderr");
        }
    }

    private String probe(String compiler) throws Exception {
        Path work = Files.createTempDirectory(temp, "oracle-");
        Path source = work.resolve("probe.cpp"), binary = work.resolve("probe");
        Files.writeString(source, """
                #include <cstdio>
                #include <climits>
                #ifndef __GLIBC__
                #error This native oracle requires the explicitly selected glibc runtime
                #endif
                #ifndef __x86_64__
                #error This native oracle requires the explicitly selected x86_64 ABI
                #endif
                #if !defined(stdin) || !defined(stdout) || !defined(stderr)
                #error The real header must provide all three stream macros
                #endif
                #include <gnu/libc-version.h>
                #undef stdin
                #undef stdout
                #undef stderr
                extern "C" {
                    extern std::FILE *stdin;
                    extern std::FILE *stdout;
                    extern std::FILE *stderr;
                }
                int exact_size(unsigned long) { return 17; }
                int exact_size(unsigned int) { return 23; }
                int exact_stream(std::FILE *&) { return 17; }
                int main() {
                    std::FILE **input = &::stdin;
                    std::FILE **output = &::stdout;
                    std::FILE **error = &::stderr;
                    if (!*input || !*output || !*error) return 1;
                    if (std::fgetc(*input) != 'Q' || std::fgetc(*input) != '\\n') return 2;
                    std::fprintf(*output, "WIDTHS %d %d %d %d\\n", CHAR_BIT, int(sizeof(int)*CHAR_BIT), int(sizeof(long)*CHAR_BIT), int(sizeof(void*)*CHAR_BIT));
                    std::fprintf(*output, "SIZE_TYPE %d\\n", exact_size(std::size_t(0)));
                    std::fprintf(*output, "STREAM_TYPES %d %d %d\\n", exact_stream(stdin), exact_stream(stdout), exact_stream(stderr));
                    std::fprintf(*output, "GLIBC_HEADERS %d.%d\\n", __GLIBC__, __GLIBC_MINOR__);
                    std::fprintf(*output, "GLIBC_RUNTIME %s\\n", gnu_get_libc_version());
                    if (std::fputs("ORACLE STDERR\\n", *error) < 0) return 3;
                    if (std::fflush(*output) != 0 || std::fflush(*error) != 0) return 4;
                    return 0;
                }
                """);
        success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
        var result = success(List.of(binary.toString()), work, "Q\n");
        String observed = result.out();
        assertTrue(observed.startsWith("WIDTHS 8 32 64 64\n"), "The selected profile requires x86_64 LP64: " + observed);
        assertEquals("17", observation(observed, "SIZE_TYPE"), "size_t must be exactly unsigned long");
        assertEquals("17 17 17", observation(observed, "STREAM_TYPES"), "All three real objects must be mutable FILE* lvalues");
        assertEquals("ORACLE STDERR\n", result.err());

        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("compiler", compiler);
        metadata.put("version", success(List.of(compiler, "--version"), work).out());
        metadata.put("target", success(List.of(compiler, "-dumpmachine"), work).out().trim());
        metadata.put("headerAndRuntimeObservations", observed);
        metadata.put("separateStderr", result.err());
        metadata.put("platform", GLIBC.toSettings());
        metadata.put("probeSourceSha256", hash(source));
        metadata.put("probeBinarySha256", hash(binary));
        metadata.put("includeSearch", success(List.of(compiler, "-std=c++03", "-E", "-v",
                source.toString(), "-o", "/dev/null"), work).err());
        Path macroSource = work.resolve("macros.cpp");
        Files.writeString(macroSource, "#include <cstdio>\n");
        String macros = success(List.of(compiler, "-std=c++03", "-dM", "-E", macroSource.toString()), work).out();
        var streamMacros = macros.lines().filter(line -> STREAMS.stream()
                .anyMatch(stream -> line.startsWith("#define " + stream + " "))).sorted().toList();
        assertEquals(List.of("#define stderr stderr", "#define stdin stdin", "#define stdout stdout"), streamMacros);
        metadata.put("nativeStreamMacroDefinitions", streamMacros);
        String headers = success(List.of(compiler, "-std=c++03", "-H", "-E",
                source.toString(), "-o", "/dev/null"), work).err();
        var headerHashes = new TreeMap<String, String>();
        for (String line : headers.split("\n")) {
            if (line.matches("\\.+ .*")) {
                Path header = Path.of(line.replaceFirst("^\\.+ ", ""));
                if (Files.isRegularFile(header)) headerHashes.put(header.toString(), hash(header));
            }
        }
        assertTrue(headerHashes.keySet().stream().anyMatch(name -> name.endsWith("/stdio.h")), headers);
        assertTrue(headerHashes.keySet().stream().anyMatch(name -> name.endsWith("/bits/types/FILE.h")), headers);
        metadata.put("selectedHeaderSha256", headerHashes);
        String dependencies = success(List.of("ldd", binary.toString()), work).out();
        metadata.put("runtimeDependencies", dependencies);
        var runtimeHashes = new TreeMap<String, String>();
        var streamSymbols = new TreeMap<String, List<String>>();
        for (String line : dependencies.split("\n")) {
            if (line.trim().startsWith("libc.so.6 => ")) {
                Path runtime = Path.of(line.trim().substring("libc.so.6 => ".length()).split("\\s+")[0]).toRealPath();
                runtimeHashes.put(runtime.toString(), hash(runtime));
                String symbols = success(List.of("nm", "-D", "--defined-only", runtime.toString()), work).out();
                var selected = symbols.lines().filter(symbol -> STREAMS.stream()
                        .anyMatch(stream -> symbol.matches("\\S+\\s+[BD]\\s+" + stream + "(?:@@?\\S+)?"))).sorted().toList();
                assertEquals(3, selected.size(), "The selected libc must export three real C-linkage data objects: " + selected);
                streamSymbols.put(runtime.toString(), selected);
            }
        }
        assertFalse(runtimeHashes.isEmpty(), "Record the actual dynamically selected libc: " + dependencies);
        metadata.put("runtimeLibrarySha256", runtimeHashes);
        metadata.put("exportedStreamObjectSymbols", streamSymbols);
        Path artifacts = Files.createDirectories(Path.of("target/standard-streams-oracle"));
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
        return success(args, work, "");
    }

    private Run success(List<String> args, Path work, String input) throws Exception {
        var result = command(args, work, input);
        assertEquals(0, result.code(), args + "\n" + result.out() + "\n" + result.err());
        return result;
    }

    private Run command(List<String> args, Path work, String input) throws Exception {
        Path stdin = Files.createTempFile(work, "stdin-", ".txt");
        Path stdout = Files.createTempFile(work, "stdout-", ".txt"), stderr = Files.createTempFile(work, "stderr-", ".txt");
        Files.writeString(stdin, input);
        var process = new ProcessBuilder(args).directory(work.toFile()).redirectInput(stdin.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
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
