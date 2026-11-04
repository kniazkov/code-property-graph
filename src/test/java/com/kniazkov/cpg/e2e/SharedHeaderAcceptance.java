/* Code Property Graph: physical header edits shared by alternative TU variants. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Shared graph-edit checks for the ordinary E2E suite and strict native corpus. */
final class SharedHeaderAcceptance {
    private SharedHeaderAcceptance() {}
    static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);
    private static final String HEADER = "include/common.h";
    record Output(Path original, Path generated, String serverOutput, String clientOutput) {}

    static List<Output> verify(Path fixture, Path work) throws Exception {
        conflict(fixture.resolve("common"), Files.createDirectory(work.resolve("conflict")));
        return List.of(identical(fixture.resolve("common"), Files.createDirectory(work.resolve("identical"))),
                conditional(fixture.resolve("conditional"), Files.createDirectory(work.resolve("conditional"))));
    }

    static void conflict(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        var project = read(cli.input);
        var server = variant(project, "SERVER");
        var original = literal(server);
        var range = original.range().orElseThrow();
        var edited = edit(project, false);
        Path output = work.resolve("must-not-exist");
        var error = assertThrows(IllegalArgumentException.class, () -> export(edited, cli.input, output));
        String message = error.getMessage();
        assertTrue(message.toLowerCase(Locale.ROOT).contains("conflict"), message);
        assertTrue(message.contains(cli.input.resolve("sources").resolve(HEADER).toString()), message);
        assertTrue(message.contains(range.start().line() + ":" + range.start().column()), message);
        assertTrue(message.contains(range.end().line() + ":" + range.end().column()), message);
        for (String macro : List.of("SERVER", "CLIENT")) {
            assertTrue(message.contains(variant(project, macro).variantId().orElseThrow()), message);
            assertTrue(message.contains("-D" + macro), message);
        }
        assertTrue(message.contains("requires \"2\""), message);
        assertTrue(message.contains("requires \"1\""), message);
        assertFalse(Files.exists(output), "Validate the complete source map before any filesystem exports");
        assertEquals(1, literal(variant(project, "SERVER")).value(), "Original immutable snapshot is unchanged");
        assertEquals(1, literal(variant(project, "CLIENT")).value());
        assertEquals(Files.readString(cli.input.resolve("sources").resolve(HEADER)),
                Cpg.sources(project, Language.CPP).get(HEADER));
    }

    static Output identical(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        var project = read(cli.input);
        String original = Files.readString(cli.input.resolve("sources").resolve(HEADER));
        var range = literal(variant(project, "SERVER")).range().orElseThrow();
        assertEquals(range, literal(variant(project, "CLIENT")).range().orElseThrow());
        var edited = edit(project, true);
        Path output = work.resolve("exported");
        var sources = export(edited, cli.input, output);
        assertEquals(Set.of("main.cpp", HEADER), sources.keySet(), "One physical file per output path");
        assertEquals(replace(original, range, "2"), sources.get(HEADER));
        assertEquals(1, occurrences(sources.get(HEADER), "return 2;"));
        assertFalse(sources.get(HEADER).contains("return 1;"));
        verifyReparse(output, Files.createDirectory(work.resolve("cli")), 2, 2);
        return new Output(cli.input.resolve("sources"), output.resolve("sources"), "2\n", "2\n");
    }

    static Output conditional(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        var project = read(cli.input);
        Path header = cli.input.resolve("sources").resolve(HEADER);
        String original = Files.readString(header);
        var range = literal(variant(project, "SERVER")).range().orElseThrow();
        assertNotEquals(range, literal(variant(project, "CLIENT")).range().orElseThrow());
        Path output = work.resolve("exported");
        var sources = export(edit(project, false), cli.input, output);
        String generated = sources.get(HEADER);
        assertEquals(replace(original, range, "2"), generated,
                "Only the server literal changes, preserving directives, comments, whitespace and CRLF");
        assertArrayEquals(original.substring(original.indexOf("#else")).getBytes(StandardCharsets.UTF_8),
                generated.substring(generated.indexOf("#else")).getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(generated.getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(output.resolve("sources").resolve(HEADER)));
        verifyReparse(output, Files.createDirectory(work.resolve("cli")), 2, 1);
        return new Output(cli.input.resolve("sources"), output.resolve("sources"), "2\n", "1\n");
    }

    private static Project read(Path input) throws Exception {
        var project = Cpg.readCompilationDatabase(input.resolve("compile_commands.json"), OPTIONS);
        assertEquals(2, project.files().size());
        assertEquals(2, project.variants(input.resolve("sources/main.cpp")).size(),
                "Both variants must refer to the same physical translation unit");
        assertNotEquals(variant(project, "SERVER").variantId(), variant(project, "CLIENT").variantId());
        var files = Cpg.sources(project, Language.CPP);
        for (String path : List.of("main.cpp", HEADER))
            assertEquals(Files.readString(input.resolve("sources").resolve(path)), files.get(path));
        return project;
    }

    private static Project edit(Project project, boolean both) {
        var editor = new ProjectEditor(project);
        for (String macro : both ? List.of("SERVER", "CLIENT") : List.of("SERVER"))
            editor.replace(literal(variant(project, macro)), IntegerLiteral.create().setValue(2).build());
        return editor.build();
    }

    private static SourceFile variant(Project project, String macro) {
        var matches = project.files().stream().filter(file -> file.provenance().orElseThrow().unit()
                .configuration().macros().stream().anyMatch(m -> m.define() && m.operand().equals(macro))).toList();
        assertEquals(1, matches.size(), macro);
        return matches.getFirst();
    }

    private static IntegerLiteral literal(SourceFile file) {
        var literals = new ArrayList<IntegerLiteral>();
        file.walk(new NodeVisitor() { @Override public void visit(FunctionDeclaration function) {
            if (function.name().equals("retry_limit")) function.walk(new NodeVisitor() {
                @Override public void visit(IntegerLiteral literal) { literals.add(literal); }
            });
        }});
        assertEquals(1, literals.size(), "One retry_limit literal per variant");
        return literals.getFirst();
    }

    /** No source, database, or graph file is written until generation validates every variant. */
    private static Map<String, String> export(Project project, Path input, Path output) throws Exception {
        var sources = Cpg.sources(project, Language.CPP);
        Files.createDirectory(output);
        for (var entry : sources.entrySet()) {
            Path path = output.resolve("sources").resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue(), StandardOpenOption.CREATE_NEW);
        }
        Files.copy(input.resolve("compile_commands.json"), output.resolve("compile_commands.json"));
        ProjectJson.write(project, output.resolve("edited.json"));
        return sources;
    }

    private static void verifyReparse(Path output, Path work, int server, int client) throws Exception {
        var reparsed = Cpg.readCompilationDatabase(output.resolve("compile_commands.json"), OPTIONS);
        assertEquals(server, literal(variant(reparsed, "SERVER")).value());
        assertEquals(client, literal(variant(reparsed, "CLIENT")).value());
        var cli = new CliSandbox(work).directory(output);
        var run = cli.runDatabase(cli.input.resolve("compile_commands.json"), "--stage", "resolved").expectStatus(0);
        for (String name : List.of("main.cpp", HEADER))
            assertArrayEquals(Files.readAllBytes(output.resolve("sources").resolve(name)),
                    Files.readAllBytes(run.work().resolve("generated").resolve(name)));
        run.snapshot().containsNode("{\"kind\":\"FunctionDeclaration\",\"name\":\"retry_limit\"}");
    }

    private static String replace(String original, SourceRange range, String replacement) {
        return original.substring(0, range.start().offset()) + replacement + original.substring(range.end().offset());
    }

    private static long occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
