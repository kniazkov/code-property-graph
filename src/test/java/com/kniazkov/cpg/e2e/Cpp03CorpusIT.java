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
        boolean databaseMode = "compilation-database".equals(settings.getProperty("mode"));
        var cli = new CliSandbox(work).directory(databaseMode ? fixture : fixture.resolve("sources"));
        Path sourceInput = databaseMode ? cli.input.resolve("sources") : cli.input;
        if ("source-fixture".equals(settings.getProperty("mode"))) {
            nativePrograms(work, cli.input, settings.getProperty("stdout"),
                    Integer.parseInt(settings.getProperty("exit")));
            Path editedWork = Files.createDirectory(work.resolve("edited"));
            var edited = new CliSandbox(editedWork).directory(fixture.resolve("sources"));
            Files.copy(fixture.resolve("expected/edited-main.cpp"), edited.input.resolve("main.cpp"),
                    StandardCopyOption.REPLACE_EXISTING);
            nativePrograms(editedWork, edited.input, settings.getProperty("edited.stdout"),
                    Integer.parseInt(settings.getProperty("edited.exit")));
            Object database = Json.parse(fixture.resolve("compile_commands.json").toFile()).toJavaObject();
            assertInstanceOf(List.class, database);
            var entries = (List<?>) database;
            assertEquals(2, entries.size());
            Set<String> units = new TreeSet<>();
            for (Object entry : entries) {
                assertInstanceOf(Map.class, entry);
                var command = (Map<?, ?>) entry;
                assertEquals("sources", command.get("directory"));
                String file = (String) command.get("file");
                assertTrue(units.add(file), "Duplicate compilation entry");
                assertTrue(Files.isRegularFile(fixture.resolve("sources").resolve(file)));
                assertEquals(List.of("g++", "-std=c++03", "-pedantic-errors", "-Iinclude", "-c", file),
                        command.get("arguments"));
            }
            assertEquals(Set.of("main.cpp", "second.cpp"), units);
            return;
        }
        String stage = Objects.requireNonNull(settings.getProperty("stage"));
        var options = new Cpg.Options(Language.CPP, Cpg.Stage.valueOf(stage.toUpperCase(Locale.ROOT)),
                databaseMode ? "c++03" : null);
        int status = Integer.parseInt(settings.getProperty("status"));
        var run = (databaseMode
                ? cli.runDatabase(cli.input.resolve("compile_commands.json"), "--stage", stage, "--cpp-standard", "c++03")
                : cli.run("cpp", "--stage", stage)).expectStatus(status);
        if (status != 0) {
            String diagnostic = settings.getProperty("diagnostic");
            assertTrue(run.stderr().contains(diagnostic), run.stderr());
            run.expectNoExports();
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
        assertEquals(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()),
                "API and CLI graphs including links and source positions");
        for (var entry : apiSources.entrySet())
            assertEquals(entry.getValue(), Files.readString(run.work().resolve("generated").resolve(entry.getKey())));
        var graph = run.snapshot();
        graph.containsNode(settings.getProperty("node"));
        if (settings.containsKey("edge.from"))
            graph.expectEdge(settings.getProperty("edge.from"), settings.getProperty("edge.kind"),
                    settings.getProperty("edge.to"));
        String expected = settings.getProperty("stdout");
        int exit = Integer.parseInt(settings.getProperty("exit"));
        for (Path source : List.of(sourceInput, run.work().resolve("generated")))
            nativePrograms(work, source, expected, exit);
    }

    private static void nativePrograms(Path work, Path source, String expected, int exit) throws Exception {
        for (String compiler : List.of("g++", "clang++")) {
            Result versionResult = command(List.of(compiler, "--version"), work);
            assertEquals(0, versionResult.status(), compiler + " --version\n" + versionResult.output());
            String version = versionResult.output();
            Path binary = work.resolve(compiler + "-" + source.getFileName()).toAbsolutePath();
            List<String> compile = new ArrayList<>(List.of(compiler, "-std=c++03", "-pedantic-errors",
                    "-I" + source.resolve("include").toAbsolutePath()));
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
