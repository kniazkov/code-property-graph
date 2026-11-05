/* Code Property Graph: exact macro ranges and deterministic diagnostic summaries. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.CompilationDatabase;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class DiagnosticProvenanceAcceptance {
    private DiagnosticProvenanceAcceptance() {}
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);

    static void verify(Path fixture, Path work) throws Exception {
        assignment(fixture.resolve("assignment"), Files.createDirectory(work.resolve("assignment")));
        paste(fixture.resolve("paste"), Files.createDirectory(work.resolve("paste")));
        shared(fixture.resolve("shared"), Files.createDirectory(work.resolve("shared")));
        causes(fixture.resolve("causes"), Files.createDirectory(work.resolve("causes")));
    }
    private static Project read(CliSandbox cli) {
        return Cpg.readCompilationDatabaseRecovering(cli.input.resolve("compile_commands.json"), OPTIONS);
    }
    private static CliRun run(CliSandbox cli, Project project) throws Exception {
        var run = cli.runDatabase(cli.input.resolve("compile_commands.json"), "--stage", "resolved").expectStatus(1);
        run.diagnosticSnapshot();
        assertEquals(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertEquals(project.diagnosticGroups().size(), run.stderr().lines().filter(l -> l.contains(": error [")).count(), run.stderr());
        assertEquals(project.diagnosticGroups(), project.edit().build().diagnosticGroups());
        assertEquals(project.diagnosticGroups(), NameResolver.resolve(project).diagnosticGroups());
        for (var problem : project.parseProblems()) {
            assertNotNull(problem.context());
            assertNotNull(problem.context().variantId());
            assertTrue(project.files().stream().anyMatch(f -> f.variantId().orElseThrow().equals(problem.context().variantId())));
            assertFalse(problem.context().includeChain().isEmpty());
            for (var expansion : problem.context().expansionChain())
                assertTrue(project.files().stream().filter(f -> f.variantId().orElseThrow().equals(problem.context().variantId()))
                        .flatMap(f -> f.provenance().orElseThrow().unit().expansions().stream()).anyMatch(e -> e.equals(expansion)));
        }
        return run;
    }

    private static void assignment(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        var project = read(cli);
        assertEquals(2, project.parseProblems().size(), "Independent error after a recovered macro error remains visible");
        var problem = project.parseProblems().stream().filter(p -> !p.context().expansionChain().isEmpty()).findFirst().orElseThrow();
        Path source = cli.input.resolve("sources/main.cpp"), header = cli.input.resolve("sources/include/assign.h");
        String text = Files.readString(source), definition = Files.readString(header);
        int call = text.indexOf("ASSIGN(total)"), semicolon = text.indexOf(';', call);
        range(problem.primaryRange(), source, semicolon, semicolon);
        assertEquals("", problem.skippedText());
        assertTrue(problem.recovered());
        var invocation = related(problem, "macro-invocation");
        range(invocation.range(), source, call, call + "ASSIGN(total)".length());
        var operator = related(problem, "macro-definition");
        range(operator.range(), header, definition.indexOf('='), definition.indexOf('=') + 1);
        assertTrue(problem.context().expansionChain().stream().anyMatch(e -> e.name().equals("ASSIGN")));
        var run = run(cli, project);
        assertTrue(run.stderr().contains("assign.h:1:"), run.stderr());
        assertTrue(run.stderr().contains("macro-invocation") || run.stderr().toLowerCase(Locale.ROOT).contains("invocation"), run.stderr());
        run.diagnosticSnapshot().containsNode("{\"kind\":\"VariableDeclaration\",\"name\":\"second\"}");
    }

    private static void paste(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        var project = read(cli);
        assertEquals(1, project.parseProblems().size()); assertEquals(1, project.diagnosticGroups().size());
        var problem = project.parseProblems().getFirst();
        Path source = cli.input.resolve("sources/main.cpp"), header = cli.input.resolve("sources/include/join.h");
        String text = Files.readString(source), definition = Files.readString(header);
        int call = text.indexOf("JOIN(+, *)");
        range(problem.primaryRange(), source, call, call + "JOIN(+, *)".length());
        range(related(problem, "macro-argument-left").range(), source, text.indexOf('+', call), text.indexOf('+', call) + 1);
        range(related(problem, "macro-argument-right").range(), source, text.indexOf('*', call), text.indexOf('*', call) + 1);
        range(related(problem, "macro-paste-operator").range(), header, definition.indexOf("##"), definition.indexOf("##") + 2);
        assertTrue(problem.context().expansionChain().stream().anyMatch(e -> e.name().equals("JOIN")));
        var run = run(cli, project);
        assertTrue(run.stderr().contains("join.h:1:"), run.stderr());
        assertTrue(run.stderr().contains("+*"), run.stderr());
        String json = Files.readString(run.work().resolve("snapshot.json"));
        for (String field : List.of("primaryRange", "relatedRanges", "context", "includeChain", "expansionChain"))
            assertTrue(json.contains("\"" + field + "\""));
    }

    private static void shared(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        var project = read(cli);
        assertEquals(2, project.parseProblems().size());
        assertEquals(1, project.diagnosticGroups().size());
        var group = project.diagnosticGroups().getFirst();
        assertEquals(2, group.translationUnitCount()); assertEquals(2, group.variantCount()); assertEquals(2, group.occurrenceCount());
        assertTrue(group.primaryRange().file().endsWith("common.h"));
        assertEquals(Set.of("a.cpp", "b.cpp"), group.occurrences().stream().map(p -> Path.of(p.context().translationUnit()).getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        for (var problem : group.occurrences()) {
            assertTrue(problem.context().includeChain().stream().anyMatch(i -> i.file().endsWith("common.h")));
            assertTrue(problem.context().includeChain().stream().anyMatch(i -> i.file().equals(problem.context().translationUnit())));
        }
        reverseOrder(cli, project);
        var run = run(cli, project);
        assertTrue(run.stderr().contains("2"), run.stderr());
        var unit = CompilationDatabase.read(cli.input.resolve("compile_commands.json")).units().getFirst();
        var variants = new ArrayList<Build.TranslationUnit>();
        for (String value : List.of("A", "B")) variants.add(new Build.TranslationUnit(unit.source(), unit.directory(), unit.includes(),
                List.of(new Build.MacroOperation(true, value)), unit.standard()));
        var alternatives = Cpg.readRecovering(new Build(variants), OPTIONS).diagnosticGroups().getFirst();
        assertEquals(1, alternatives.translationUnitCount()); assertEquals(2, alternatives.variantCount());
    }

    private static void causes(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        var project = read(cli);
        assertEquals(2, project.parseProblems().size()); assertEquals(2, project.diagnosticGroups().size());
        var first = project.diagnosticGroups().getFirst();
        var second = project.diagnosticGroups().getLast();
        assertEquals(first.primaryRange(), second.primaryRange(), "Different reasons at one physical macro invocation");
        assertNotEquals(first.reason(), second.reason());
        assertTrue(project.parseProblems().stream().anyMatch(p -> p.reason().contains("+*")));
        assertTrue(project.parseProblems().stream().anyMatch(p -> p.reason().contains("+/")));
        reverseOrder(cli, project);
        run(cli, project);
    }

    static void directiveCauses(Path work) throws Exception {
        for (String directive : List.of("line", "include")) {
            Path directory = Files.createDirectory(work.resolve(directive));
            var cli = new CliSandbox(directory).source("main.cpp", "#" + directive + " COUNT\nint main() { return 0; }\n");
            Files.writeString(cli.input.resolve("compile_commands.json"), """
                [
                  {"directory":".","file":"main.cpp","arguments":["g++","-std=c++03","-DCOUNT=40000","-c","main.cpp"]},
                  {"directory":".","file":"main.cpp","arguments":["g++","-std=c++03","-DCOUNT=50000","-c","main.cpp"]}
                ]
                """);
            var project = read(cli);
            assertEquals(2, project.diagnosticGroups().size(), directive);
            assertEquals(project.diagnosticGroups().getFirst().primaryRange(), project.diagnosticGroups().getLast().primaryRange());
            for (String value : List.of("40000", "50000"))
                assertTrue(project.parseProblems().stream().anyMatch(p -> p.reason().contains(value)), directive + " " + value);
            for (var problem : project.parseProblems())
                assertTrue(problem.context().expansionChain().stream().anyMatch(e -> e.name().equals("COUNT")), directive);
            reverseOrder(cli, project);
            run(cli, project);
        }
    }

    private static void reverseOrder(CliSandbox cli, Project expected) throws Exception {
        var original = CompilationDatabase.read(cli.input.resolve("compile_commands.json"));
        var units = new ArrayList<>(original.units()); Collections.reverse(units);
        var reversed = Cpg.readRecovering(new Build(units), OPTIONS);
        assertEquals(expected.diagnosticGroups(), reversed.diagnosticGroups());
        Map<?, ?> left = (Map<?, ?>) ProjectJson.encode(expected).toJavaObject();
        Map<?, ?> right = (Map<?, ?>) ProjectJson.encode(reversed).toJavaObject();
        assertEquals(left.get("diagnosticGroups"), right.get("diagnosticGroups"));
    }
    private static DiagnosticRelatedRange related(ParseProblem problem, String role) {
        var ranges = problem.relatedRanges().stream().filter(r -> r.role().equals(role)).toList();
        assertEquals(1, ranges.size(), role + ": " + problem.relatedRanges());
        return ranges.getFirst();
    }
    private static void range(SourceRange range, Path file, int start, int end) throws Exception {
        assertEquals(file.toAbsolutePath().normalize().toString(), range.file());
        assertEquals(start, range.start().offset()); assertEquals(end, range.end().offset());
        String text = Files.readString(file);
        for (var position : List.of(range.start(), range.end())) {
            String before = text.substring(0, position.offset());
            int line = (int) before.chars().filter(c -> c == '\n').count() + 1;
            int column = position.offset() - before.lastIndexOf('\n');
            assertEquals(line, position.line()); assertEquals(column, position.column());
        }
    }
}
