/* Code Property Graph: reproducible C++03 corpus and strict native oracles. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.Language;
import com.kniazkov.cpg.parser.ParseException;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Every manifest entry becomes a test; missing compilers are errors, never skipped tests. */
class Cpp03CorpusIT {
    private static final Path CORPUS = Path.of("src/test/resources/cpp03");
    @TempDir Path temp;

    private static List<String> names() throws IOException {
        return Files.readAllLines(CORPUS.resolve("scenarios.txt")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
    }

    @Test void inventoryAndNestedCopy() throws Exception {
        List<String> names = names();
        assertFalse(names.isEmpty());
        assertEquals(names.size(), new HashSet<>(names).size(), "Duplicate scenario");
        try (var paths = Files.list(CORPUS)) {
            assertEquals(new TreeSet<>(names), new TreeSet<>(paths.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString()).toList()), "Corpus and manifest differ");
        }
        Path fixture = Files.createDirectories(temp.resolve("fixture/include/detail"));
        Files.writeString(fixture.resolve("value.h"), "// nested header\n");
        var sandbox = new CliSandbox(Files.createDirectory(temp.resolve("sandbox")))
                .directory(temp.resolve("fixture"));
        assertEquals("// nested header\n", Files.readString(sandbox.input.resolve("include/detail/value.h")));
    }

    @Test void missingCompilerIsAnError() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> command(List.of(temp.resolve("missing-compiler").toString(), "--version"), temp));
        assertTrue(error.getMessage().contains("Cannot execute"));
    }

    @TestFactory Stream<DynamicTest> corpus() throws IOException {
        String selected = System.getProperty("cpg.cpp03.scenario");
        List<String> names = names();
        if (selected != null) assertTrue(names.contains(selected), "Unknown scenario: " + selected);
        return names.stream().filter(name -> selected == null || selected.equals(name))
                .map(name -> DynamicTest.dynamicTest(name, () -> scenario(name)));
    }

    private void scenario(String name) throws Exception {
        Path fixture = CORPUS.resolve(name);
        var settings = new Properties();
        try (var reader = Files.newBufferedReader(fixture.resolve("scenario.properties"))) { settings.load(reader); }
        Path work = Files.createDirectory(temp.resolve(name));
        if ("build-variants".equals(settings.getProperty("mode"))) {
            for (var variant : BuildVariantsAcceptance.verify(fixture, work))
                for (Path source : List.of(variant.original(), variant.generated()))
                    nativePrograms(work, source, settings.getProperty("stdout"),
                            Integer.parseInt(settings.getProperty("exit")), "-D" + variant.macro());
            return;
        }
        if ("overloads-adl".equals(settings.getProperty("mode"))) {
            var output = OverloadsAdlAcceptance.verify(fixture, work);
            for (Path source : List.of(output.original(), output.generated(), output.folded())) nativePrograms(work, source, "", 0);
            for (String compiler : List.of("g++", "clang++")) {
                Result rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-fsyntax-only", output.invalid().resolve("main.cpp").toString()), work);
                assertNotEquals(0, rejected.status(), "An explicit constructor cannot perform an implicit argument conversion: " + compiler);
            }
            return;
        }
        if ("inheritance-virtual".equals(settings.getProperty("mode"))) {
            var output = InheritanceVirtualAcceptance.verify(fixture, work);
            for (Path source : List.of(output.original(), output.generated(), output.folded())) nativePrograms(work, source, "", 0);
            for (String compiler : List.of("g++", "clang++")) {
                Result rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-fsyntax-only", output.invalid().resolve("main.cpp").toString()), work);
                assertNotEquals(0, rejected.status(), "Repeated nonvirtual base member access must be ambiguous: " + compiler);
            }
            return;
        }
        if ("casts-memory".equals(settings.getProperty("mode"))) {
            var output = CastsMemoryAcceptance.verify(fixture, work);
            for (Path source : List.of(output.original(), output.generated(), output.folded())) nativePrograms(work, source, "", 0);
            for (String compiler : List.of("g++", "clang++")) {
                Result rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-fsyntax-only", output.invalid().resolve("main.cpp").toString()), work);
                assertNotEquals(0, rejected.status(), "sizeof incomplete class must be rejected: " + compiler);
            }
            return;
        }
        if ("classes-initialization".equals(settings.getProperty("mode"))) {
            var output = ClassesInitializationAcceptance.verify(fixture, work);
            for (Path source : List.of(output.original(), output.generated(), output.folded())) nativePrograms(work, source, "", 0);
            for (String compiler : List.of("g++", "clang++")) {
                Result rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-fsyntax-only", output.invalid().resolve("main.cpp").toString()), work);
                assertNotEquals(0, rejected.status(), "C++11 direct-list initialization must be rejected: " + compiler);
            }
            return;
        }
        if ("functions-defaults".equals(settings.getProperty("mode"))) {
            var output = FunctionsDefaultsAcceptance.verify(fixture, work);
            for (Path source : List.of(output.original(), output.generated(), output.folded()))
                nativePrograms(work, source, "", 0);
            for (String compiler : List.of("g++", "clang++")) {
                Result rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-fsyntax-only",
                        output.invalid().resolve("main.cpp").toString()), work);
                assertNotEquals(0, rejected.status(), "A later default is not visible at the earlier call: " + compiler);
            }
            return;
        }
        if ("literals-expressions".equals(settings.getProperty("mode"))) {
            var output = LiteralsExpressionsAcceptance.verify(fixture, work);
            for (Path source : List.of(output.original(), output.generated(), output.folded()))
                nativePrograms(work, source, "", 0);
            for (String compiler : List.of("g++", "clang++")) {
                Result rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-fsyntax-only",
                        output.invalid().resolve("main.cpp").toString()), work);
                assertNotEquals(0, rejected.status(), "C++11 binary literal must be rejected by " + compiler);
            }
            return;
        }
        if ("scopes-linkage".equals(settings.getProperty("mode"))) {
            var output = ScopesLinkageAcceptance.verify(fixture, work);
            for (Path source : List.of(output.original(), output.generated()))
                nativeConfiguredPrograms(work, source, "", 33, Map.of("a.cpp", List.of("-DWIDE")));
            for (String compiler : List.of("g++", "clang++")) {
                Result rejected = command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-fsyntax-only",
                        output.incomplete().resolve("main.cpp").toString()), work);
                assertNotEquals(0, rejected.status(), "Incomplete sizeof must be rejected by " + compiler);
                assertFalse(rejected.error().isBlank());
            }
            return;
        }
        if ("platform-declarators".equals(settings.getProperty("mode"))) {
            var output = PlatformDeclaratorsAcceptance.verify(fixture, work);
            for (Path source : List.of(output.original(), output.generated()))
                nativePrograms(work, source, settings.getProperty("stdout"), Integer.parseInt(settings.getProperty("exit")));
            for (Path source : List.of(output.minimumOriginal(), output.minimumGenerated()))
                nativePrograms(work, source, "", 1);
            return;
        }
        if ("diagnostic-provenance".equals(settings.getProperty("mode"))) {
            DiagnosticProvenanceAcceptance.verify(fixture, work);
            return;
        }
        if ("parser-recovery".equals(settings.getProperty("mode"))) {
            ParserRecoveryAcceptance.verify(fixture, work);
            return;
        }
        if ("shared-headers".equals(settings.getProperty("mode"))) {
            for (var output : SharedHeaderAcceptance.verify(fixture, work)) {
                nativePrograms(work, output.original(), "1\n", 0, "-DSERVER");
                nativePrograms(work, output.original(), "1\n", 0, "-DCLIENT");
                nativePrograms(work, output.generated(), output.serverOutput(), 0, "-DSERVER");
                nativePrograms(work, output.generated(), output.clientOutput(), 0, "-DCLIENT");
            }
            return;
        }
        boolean databaseMode = "compilation-database".equals(settings.getProperty("mode"));
        var cli = new CliSandbox(work).directory(databaseMode ? fixture : fixture.resolve("sources"));
        Path sourceInput = databaseMode ? cli.input.resolve("sources") : cli.input;
        String stage = Objects.requireNonNull(settings.getProperty("stage"));
        var options = new Cpg.Options(Language.CPP, Cpg.Stage.valueOf(stage.toUpperCase(Locale.ROOT)),
                databaseMode ? "c++03" : null);
        int status = Integer.parseInt(settings.getProperty("status"));
        var run = (databaseMode
                ? cli.runDatabase(cli.input.resolve("compile_commands.json"), "--stage", stage, "--cpp-standard", "c++03")
                : cli.run("cpp", "--stage", stage)).expectStatus(status);
        if (status != 0) {
            String diagnostic = settings.getProperty("diagnostic");
            assertTrue(run.stderr().replaceAll("error \\[[^]]+\\]: ", "").contains(diagnostic), run.stderr());
            run.diagnosticSnapshot();
            var error = assertThrows(ParseException.class, () -> Cpg.read(cli.input, options));
            assertTrue(error.getMessage().contains(diagnostic), error.getMessage());
            return;
        }
        var project = databaseMode ? Cpg.readCompilationDatabase(cli.input.resolve("compile_commands.json"), options)
                : Cpg.read(cli.input, options);
        Map<String, String> apiSources = Cpg.sources(project, Language.CPP);
        assertFalse(apiSources.isEmpty(), "API must generate sources");
        try (var paths = Files.walk(run.work().resolve("generated"))) {
            assertEquals(new TreeSet<>(apiSources.keySet()), new TreeSet<>(paths.filter(Files::isRegularFile)
                    .map(p -> run.work().resolve("generated").relativize(p).toString()
                            .replace(java.io.File.separatorChar, '/')).toList()));
        }
        com.kniazkov.cpg.SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),
                Json.parse(run.work().resolve("snapshot.json").toFile()));
        for (var entry : apiSources.entrySet())
            assertEquals(entry.getValue(), Files.readString(run.work().resolve("generated").resolve(entry.getKey())));
        var graph = run.snapshot();
        graph.containsNode(settings.getProperty("node"));
        if (settings.containsKey("edge.from"))
            graph.expectEdge(settings.getProperty("edge.from"), settings.getProperty("edge.kind"),
                    settings.getProperty("edge.to"));
        String expected = settings.getProperty("stdout");
        int exit = Integer.parseInt(settings.getProperty("exit"));
        for (Path source : List.of(sourceInput, run.work().resolve("generated"))) {
            nativePrograms(work, source, expected, exit, settings.containsKey("native.flag")
                    ? new String[]{settings.getProperty("native.flag")} : new String[0]);
            if (Boolean.parseBoolean(settings.getProperty("native.also-without-flag", "false")))
                nativePrograms(work, source, expected, exit);
        }
        if (settings.containsKey("edited.stdout")) {
            var changed = Cpg.sources(MacroEdits.squareRightOperand(project), Language.CPP);
            Path changedRoot = Files.createDirectory(work.resolve("graph-edited"));
            for (var entry : changed.entrySet()) {
                Path path = changedRoot.resolve(entry.getKey());
                Files.createDirectories(path.getParent()); Files.writeString(path, entry.getValue());
            }
            assertEquals(Files.readString(sourceInput.resolve("include/square.h")), changed.get("include/square.h"));
            assertEquals(Files.readString(sourceInput.resolve("second.cpp")), changed.get("second.cpp"));
            assertFalse(changed.get("main.cpp").contains("SQUARE(a)"));
            nativePrograms(work, changedRoot, settings.getProperty("edited.stdout"),
                    Integer.parseInt(settings.getProperty("edited.exit")));
            Path editedWork = Files.createDirectory(work.resolve("edited"));
            var edited = new CliSandbox(editedWork).directory(fixture.resolve("sources"));
            Files.copy(fixture.resolve("expected/edited-main.cpp"), edited.input.resolve("main.cpp"),
                    StandardCopyOption.REPLACE_EXISTING);
            nativePrograms(editedWork, edited.input, settings.getProperty("edited.stdout"),
                    Integer.parseInt(settings.getProperty("edited.exit")));
        }
    }

    private static void nativePrograms(Path work, Path source, String expected, int exit, String... flags) throws Exception {
        for (String compiler : List.of("g++", "clang++")) {
            Result versionResult = command(List.of(compiler, "--version"), work);
            assertEquals(0, versionResult.status(), compiler + " --version\n" + versionResult.output());
            String version = versionResult.output();
            Path binary = work.resolve(compiler + "-" + source.getFileName()).toAbsolutePath();
            List<String> compile = new ArrayList<>(List.of(compiler, "-std=c++03", "-pedantic-errors",
                    "-I" + source.resolve("include").toAbsolutePath()));
            compile.addAll(List.of(flags));
            try (var paths = Files.walk(source)) {
                compile.addAll(paths.filter(p -> p.toString().endsWith(".cpp")).sorted()
                        .map(p -> p.toAbsolutePath().toString()).toList());
            }
            compile.addAll(List.of("-o", binary.toString()));
            Result build = command(compile, work);
            assertEquals(0, build.status(), version + "\n" + compile + "\n" + build.output() + build.error());
            Result execution = command(List.of(binary.toString()), work);
            assertEquals(exit, execution.status(), version + "\n" + binary + "\n" + execution.output() + execution.error());
            assertEquals(expected, execution.output(), version + "\n" + binary);
            assertEquals("", execution.error(), version + "\n" + binary + " stderr");
        }
    }

    /** Compile each unit under its own effective flags before linking one composition. */
    private void nativeConfiguredPrograms(Path work, Path source, String expected, int exit,
            Map<String, List<String>> flags) throws Exception {
        for (String compiler : List.of("g++", "clang++")) {
            Result version = command(List.of(compiler, "--version"), work);
            assertEquals(0, version.status(), version.output() + version.error());
            Path objects = Files.createTempDirectory(work, "configured-");
            List<String> link = new ArrayList<>(List.of(compiler));
            try (var paths = Files.walk(source)) {
                int ordinal = 0;
                for (Path file : paths.filter(p -> p.toString().endsWith(".cpp")).sorted().toList()) {
                    Path object = objects.resolve("unit-" + ordinal++ + ".o");
                    List<String> compile = new ArrayList<>(List.of(compiler, "-std=c++03", "-pedantic-errors",
                            "-I" + source.resolve("include").toAbsolutePath()));
                    compile.addAll(flags.getOrDefault(source.relativize(file).toString().replace('\\', '/'), List.of()));
                    compile.addAll(List.of("-c", file.toAbsolutePath().toString(), "-o", object.toString()));
                    Result result = command(compile, work);
                    assertEquals(0, result.status(), version.output() + compile + result.output() + result.error());
                    link.add(object.toString());
                }
            }
            Path binary = objects.resolve("program");
            link.addAll(List.of("-o", binary.toString()));
            Result linked = command(link, work);
            assertEquals(0, linked.status(), link + linked.output() + linked.error());
            Result run = command(List.of(binary.toString()), work);
            assertEquals(exit, run.status(), version.output() + run.output() + run.error());
            assertEquals(expected, run.output());
            assertEquals("", run.error());
        }
    }

    private record Result(int status, String output, String error) {}

    private static Result command(List<String> command, Path directory) throws Exception {
        Path output = Files.createTempFile(directory, "process-", ".log");
        Path error = Files.createTempFile(directory, "process-error-", ".log");
        Process child;
        try {
            child = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectError(error.toFile()).redirectOutput(output.toFile()).start();
        } catch (IOException e) {
            throw new AssertionError("Cannot execute " + command + "; both GCC and Clang are required", e);
        }
        try {
            assertTrue(child.waitFor(45, TimeUnit.SECONDS), "Timed out: " + command);
            return new Result(child.exitValue(), Files.readString(output), Files.readString(error));
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(); }
        }
    }
}
