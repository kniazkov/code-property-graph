/* CPG errno expansion linked to independently observed glibc storage. MIT License. */
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

class ErrnoRuntimeIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final PlatformProfile GLIBC = PlatformProfile.builder().cRuntime("glibc").build();
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03")
            .withPlatform(GLIBC);

    @Test void sharedRuntimeStorageSurvivesApiCliCopyMaterializationAndRhsEditing() throws Exception {
        int erange = probe("g++");
        assertEquals(erange, probe("clang++"), "Both independent header oracles must describe the same runtime");
        for (boolean legacy : List.of(false, true)) {
            Path work = Files.createTempDirectory(temp, legacy ? "legacy-" : "cpp-");
            var cli = new CliSandbox(work).source("main.cpp", program(legacy, erange));
            var project = Cpg.read(cli.input, OPTIONS);
            verify(project);
            assertEquals(BigInteger.valueOf(11),
                    assertInstanceOf(IntegerLiteral.class, firstErrnoAssignment(project).value()).bigValue());
            var copy = project.edit().build();
            verify(copy);

            Path settings = work.resolve("glibc.json");
            Files.writeString(settings, Json.serialize(GLIBC.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),
                    Json.parse(run.work().resolve("snapshot.json").toFile()));

            Path copied = writeSource(work, "copied", Cpg.sources(copy, Language.CPP).get("main.cpp"));
            Path generated = run.work().resolve("generated/main.cpp");
            for (Path source : List.of(cli.input.resolve("main.cpp"), copied, generated)) {
                verify(Cpg.read(source.getParent(), OPTIONS));
                nativeRun(source, 11, erange);
                nativeRun(materialize(source), 11, erange);
            }

            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var options = new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(GLIBC);
                var transformed = Cpg.read(cli.input, options);
                verify(transformed);
                verify(transformed.edit().build());
                Path transformedSource = writeSource(work, stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verify(Cpg.read(transformedSource.getParent(), OPTIONS));
                // Optimizing stages must preserve the live storage, especially across strtol.
                nativeRun(transformedSource, 11, erange);
                nativeRun(materialize(transformedSource), 11, erange);
            }

            var editor = new ProjectEditor(project);
            editor.replace(firstErrnoAssignment(project).value(), IntegerLiteral.create().setValue(19).build());
            var edited = editor.build();
            verify(edited);
            assertEquals(BigInteger.valueOf(19),
                    assertInstanceOf(IntegerLiteral.class, firstErrnoAssignment(edited).value()).bigValue());
            Path editedSource = writeSource(work, "edited", Cpg.sources(edited, Language.CPP).get("main.cpp"));
            var reparsed = Cpg.read(editedSource.getParent(), OPTIONS);
            verify(reparsed);
            assertEquals(BigInteger.valueOf(19),
                    assertInstanceOf(IntegerLiteral.class, firstErrnoAssignment(reparsed).value()).bigValue());
            // Native stdout observes the edited RHS through all three aliases, not just source text.
            nativeRun(editedSource, 19, erange);
            nativeRun(materialize(editedSource), 19, erange);
        }
    }

    @Test void unknownRuntimeAllowsPresenceChecksButRetainsContextualErrorJsonForUse() throws Exception {
        var unknown = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03");
        for (String header : List.of("cerrno", "errno.h")) {
            Path work = Files.createTempDirectory(temp, "unknown-");
            String include = "#include <" + header + ">\n";
            var cli = new CliSandbox(work).source("main.cpp", include
                    + "#ifndef errno\n#error errno must be declared as a macro\n#endif\nint main(){return 0;}\n");
            var unused = Cpg.read(cli.input, unknown);
            assertFalse(unused.hasErrors(), () -> unused.parseProblems() + " / " + unused.semanticDiagnostics());
            assertFalse(unused.edit().build().hasErrors());
            cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(0);

            Files.writeString(cli.input.resolve("main.cpp"), include + "int main(){errno = 7; return errno;}\n");
            assertTrue(assertThrows(ParseException.class, () -> Cpg.read(cli.input, unknown))
                    .getMessage().contains("cRuntime=glibc"));
            var failed = Cpg.readRecovering(cli.input, unknown);
            assertTrue(failed.hasErrors());
            assertErrorJson(ProjectJson.encode(failed).toText("  "));
            assertErrorJson(ProjectJson.encode(failed.edit().build()).toText("  "));
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(failed, Language.CPP));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(1);
            String json = Files.readString(run.work().resolve("snapshot.json"));
            Json.parse(json);
            assertErrorJson(json);
            assertFalse(Files.exists(run.work().resolve("generated")));
        }
    }

    private static void assertErrorJson(String json) {
        assertTrue(json.contains("unavailable-library-macro"), json);
        assertTrue(json.contains("unavailableReason"), json);
        assertTrue(json.contains("errno"), json);
        assertTrue(json.contains("cRuntime"), json);
        assertTrue(json.contains("main.cpp"), json);
    }

    private String program(boolean legacy, int erange) {
        String headers = legacy ? "#include <errno.h>\n#include <stdlib.h>\n#include <stdio.h>\n"
                : "#include <cerrno>\n#include <cstdlib>\n#include <cstdio>\n";
        String namespace = legacy ? "" : "std::";
        // ERANGE is supplied by the separately compiled real-header oracle, never by CPG's catalogue.
        return headers + """
                int main() {
                    errno = 11;
                    int &reference = errno;
                    int *saved = &errno;
                    if (&reference != saved || &errno != saved) return 1;
                    %sprintf("INITIAL %%d %%d %%d\\n", errno, reference, *saved);
                    reference = 23;
                    if (errno != 23 || *saved != 23) return 2;
                    *saved = 37;
                    if (errno != 37 || reference != 37) return 3;
                    errno = 0;
                    char *end = 0;
                    long value = %sstrtol("999999999999999999999999999999999999999999999999999999999999", &end, 10);
                    if (value <= 0 || !end || *end != '\\0') return 4;
                    if (errno != %d || reference != %d || *saved != %d) return 5;
                    %sprintf("OVERFLOW %%d %%d %%d\\n", errno, reference, *saved);
                    return 0;
                }
                """.formatted(namespace, namespace, erange, erange, erange, namespace);
    }

    private static List<CallExpression> errnoCalls(Project project) {
        return nodes(project, CallExpression.class).stream()
                .filter(call -> call.callee() instanceof NameExpression name
                        && name.name().endsWith("__errno_location"))
                .toList();
    }

    private static AssignmentExpression firstErrnoAssignment(Project project) {
        return nodes(project, AssignmentExpression.class).stream()
                .filter(assignment -> assignment.target() instanceof DereferenceExpression dereference
                        && dereference.operand() instanceof CallExpression call
                        && call.callee() instanceof NameExpression name
                        && name.name().endsWith("__errno_location"))
                .findFirst().orElseThrow();
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

    private void verify(Project project) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
        var calls = errnoCalls(project);
        assertEquals(10, calls.size(), "Every written errno use must bind to runtime storage");
        var targets = Collections.newSetFromMap(new IdentityHashMap<CallableDeclaration, Boolean>());
        for (var call : calls) {
            var target = call.target().orElseThrow();
            targets.add(target);
            assertTrue(LibraryDeclarations.isLibrary(target));
            assertTrue(target.body().isEmpty());
            assertEquals("__errno_location", target.name());
            assertEquals("C", target.languageLinkage());
            assertEquals("pointer(int)", target.returnType().orElseThrow().canonicalIdentity());
            assertTrue(target.parameters().isEmpty());
            assertTrue(target.hasExceptionSpecification());
            assertTrue(target.exceptionTypes().isEmpty());
        }
        assertEquals(1, targets.size(), "Calls must share the same global library declaration");
        var assignment = firstErrnoAssignment(project);
        assertEquals("int", TypeSemantics.expressionType(assignment.target(), GLIBC).orElseThrow().canonicalIdentity());
        assertEquals(ValueCategory.LVALUE, TypeSemantics.valueCategory(assignment.target(), GLIBC));
        var preprocessing = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(GLIBC, preprocessing.configuration().platform());
        var definitions = preprocessing.definitions().stream().filter(definition -> definition.name().equals("errno")).toList();
        assertFalse(definitions.isEmpty());
        for (var definition : definitions) {
            assertFalse(definition.functionLike());
            assertEquals("library", definition.originKind());
            assertNull(definition.unavailableReason());
            assertEquals("(*::__errno_location())", definition.rawReplacement().replaceAll("\\s+", ""));
            assertTrue(project.externalInput(definition.sourceKey()).isPresent());
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
        var tokens = preprocessing.tokens();
        assertFalse(tokens.stream().anyMatch(token -> token.token().spelling().equals("errno")),
                "Materialized code must not leave errno for the host preprocessor to expand");
        assertEquals(10, preprocessing.expansions().stream().filter(event -> event.name().equals("errno")).count());
        assertEquals(10, tokens.stream().filter(token -> token.token().spelling().equals("__errno_location")).count());
        var text = new StringBuilder();
        for (var expanded : tokens) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                text.append("\n#include ").append(token.spelling()).append("\n#undef errno\n#undef ERANGE\n");
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                text.append(token.spelling()).append(' ');
            }
        }
        // Host headers declare the runtime entry point, but cannot supply either tested macro.
        assertTrue(text.toString().replaceAll("\\s+", "").contains("(*::__errno_location())"));
        Path file = Files.createTempDirectory(temp, "expanded-").resolve("main.cpp");
        Files.writeString(file, text.append('\n'));
        return file;
    }

    private void nativeRun(Path source, int assigned, int erange) throws Exception {
        String expected = "INITIAL " + assigned + " " + assigned + " " + assigned + "\nOVERFLOW "
                + erange + " " + erange + " " + erange + "\n";
        for (String compiler : COMPILERS) {
            Path work = Files.createTempDirectory(temp, "native-");
            Path binary = work.resolve("errno-check");
            success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
            assertEquals(expected, success(List.of(binary.toString()), work).out, compiler + " / " + source);
        }
    }

    private int probe(String compiler) throws Exception {
        Path work = Files.createTempDirectory(temp, "oracle-");
        Path source = work.resolve("probe.cpp"), binary = work.resolve("probe");
        Files.writeString(source, """
                #include <cerrno>
                #include <climits>
                #include <cstdio>
                #ifndef __GLIBC__
                #error This native oracle requires the explicitly selected glibc runtime
                #endif
                #include <gnu/libc-version.h>
                int main() {
                    std::printf("WIDTHS %d %d %d %d\\n", CHAR_BIT, int(sizeof(int)*CHAR_BIT), int(sizeof(long)*CHAR_BIT), int(sizeof(void*)*CHAR_BIT));
                    std::printf("ERANGE %d\\n", ERANGE);
                    std::printf("GLIBC_HEADERS %d.%d\\n", __GLIBC__, __GLIBC_MINOR__);
                    std::printf("GLIBC_RUNTIME %s\\n", gnu_get_libc_version());
                    return 0;
                }
                """);
        success(List.of(compiler, "-std=c++03", "-pedantic-errors", source.toString(), "-o", binary.toString()), work);
        String observed = success(List.of(binary.toString()), work).out;
        assertTrue(observed.startsWith("WIDTHS 8 32 64 64\n"), "The explicit test profile requires LP64: " + observed);
        int erange = observed.lines().filter(line -> line.startsWith("ERANGE "))
                .mapToInt(line -> Integer.parseInt(line.substring("ERANGE ".length()))).findFirst().orElseThrow();
        assertTrue(erange > 0);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("compiler", compiler);
        metadata.put("version", success(List.of(compiler, "--version"), work).out);
        metadata.put("target", success(List.of(compiler, "-dumpmachine"), work).out.trim());
        metadata.put("headerAndRuntimeObservations", observed);
        metadata.put("platform", GLIBC.toSettings());
        metadata.put("probeSourceSha256", hash(source));
        metadata.put("includeSearch", success(List.of(compiler, "-std=c++03", "-E", "-v",
                source.toString(), "-o", "/dev/null"), work).err);
        String headers = success(List.of(compiler, "-std=c++03", "-H", "-E",
                source.toString(), "-o", "/dev/null"), work).err;
        var hashes = new TreeMap<String, String>();
        for (String line : headers.split("\n")) {
            if (line.matches("\\.+ .*")) {
                Path header = Path.of(line.replaceFirst("^\\.+ ", ""));
                if (Files.isRegularFile(header)) hashes.put(header.toString(), hash(header));
            }
        }
        assertTrue(hashes.keySet().stream().anyMatch(name -> name.endsWith("/errno.h")), headers);
        metadata.put("selectedHeaderSha256", hashes);
        Path artifacts = Files.createDirectories(Path.of("target/errno-runtime-oracle"));
        Files.writeString(artifacts.resolve((compiler.equals("g++") ? "gcc" : "clang") + "-evidence.json"),
                Json.serialize(metadata));
        return erange;
    }

    private static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private record Run(int code, String out, String err) {}

    private Run success(List<String> args, Path work) throws Exception {
        var result = command(args, work);
        assertEquals(0, result.code, args + "\n" + result.out + "\n" + result.err);
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
