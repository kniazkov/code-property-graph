/* Code Property Graph: stable diagnostic groups and immutable occurrence contexts. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticGroupingTest {
    @TempDir Path temp;

    private SourceRange range(String file, int start, int end) {
        return new SourceRange(temp.resolve(file).toString(), new SourcePosition(1, start + 1, start),
                new SourcePosition(1, end + 1, end));
    }

    private DiagnosticContext context(String file, String variant, long include) {
        return new DiagnosticContext(temp.resolve(file).toString(), variant, List.of(
                new Preprocessing.Include(0, temp.resolve(file).toString(), null),
                new Preprocessing.Include(include, temp.resolve("shared.h").toString(), 0L)), List.of());
    }

    private ParseProblem problem(String file, String variant, long include, String reason, String message) {
        return new ParseProblem("invalid-token-paste", message, range(file, 2, 4), "xx", false,
                range("shared.h", 8, 12), reason,
                List.of(new DiagnosticRelatedRange("paste-operator", "token-paste operator", range("macro.h", 7, 9))),
                context(file, variant, include));
    }

    private SourceFile.Builder file(String name, ParseProblem... problems) {
        return SourceFile.create().setLanguage(Language.CPP).setPath(temp.resolve(name).toString())
                .setParseProblems(Arrays.asList(problems));
    }

    private Project project(boolean reverse) {
        var a = file("a.cpp", problem("a.cpp", "a1", 1, "+*", "Invalid paste +* (logical location: first:1:1)"),
                problem("a.cpp", "a1", 2, "+*", "Invalid paste +* (logical location: second:1:1)"));
        var aVariant = file("a.cpp", problem("a.cpp", "a2", 1, "+*", "Invalid paste +*"));
        var b = file("b.cpp", problem("b.cpp", "b1", 1, "+*", "Invalid paste +*"));
        return Project.create().setFiles(reverse ? List.of(b, aVariant, a) : List.of(a, aVariant, b)).build();
    }

    @Test void commonHeaderGroupsByPhysicalCauseAndCountsDistinctTranslationUnits() {
        Project project = project(false);
        assertEquals(1, project.diagnosticGroups().size());
        DiagnosticGroup group = project.diagnosticGroups().getFirst();
        assertEquals(2, group.translationUnitCount());
        assertEquals(3, group.variantCount());
        assertEquals(4, group.occurrenceCount());
        assertEquals(List.of("a1", "a1", "a2", "b1"), group.occurrences().stream()
                .map(p -> p.context().variantId()).toList());
        assertEquals(List.of(1L, 2L, 1L, 1L), group.occurrences().stream()
                .map(p -> p.context().includeChain().getLast().id()).toList());
        assertEquals(group.occurrences(), project.parseProblems());
        assertEquals(range("shared.h", 8, 12), group.primaryRange());
        assertNotEquals(group.primaryRange(), group.occurrences().getFirst().range());
    }

    @Test void differentSignificantCausesAndIndependentPositionsNeverCoalesce() {
        var first = problem("a.cpp", "a1", 1, "+*", "Invalid paste +*");
        var second = problem("a.cpp", "a2", 1, "+/", "Invalid paste +/");
        var later = new ParseProblem(first.code(), first.message(), first.range(), first.skippedText(), true,
                range("shared.h", 15, 19), first.reason(), first.relatedRanges(), first.context());
        var project = Project.create().addFile(file("a.cpp", second, later, first)).build();
        assertEquals(3, project.diagnosticGroups().size());
        assertEquals(List.of("+*", "+/", "+*"), project.diagnosticGroups().stream().map(DiagnosticGroup::reason).toList());
        assertEquals(3, project.parseProblems().size());
    }

    @Test void reversingInputOrderPreservesApiJsonSummariesAndCliOutput() {
        Project forward = project(false), reverse = project(true);
        assertEquals(forward.parseProblems(), reverse.parseProblems());
        assertEquals(forward.diagnosticGroups(), reverse.diagnosticGroups());
        assertEquals(encoded(forward).get("diagnosticGroups"), encoded(reverse).get("diagnosticGroups"));
        assertEquals(cli(forward), cli(reverse));
        String output = cli(forward);
        assertEquals(1, output.lines().filter(line -> line.contains(": error [")).count());
        assertEquals(1, output.lines().filter(line -> line.contains(": note [paste-operator]:")).count());
        assertTrue(output.startsWith(temp.resolve("shared.h") + ":1:9: error [invalid-token-paste]:"));
        assertTrue(output.contains("2 translation units, 3 variants, 4 occurrences"));
    }

    @Test void contextAndRelatedEvidenceAreImmutableAndSurviveCopiesAndReanalysis() {
        var related = new ArrayList<>(List.of(new DiagnosticRelatedRange("argument", "first argument", range("a.cpp", 1, 2))));
        var includes = new ArrayList<>(context("a.cpp", "v", 1).includeChain());
        var fragments = new ArrayList<>(List.of(new Preprocessing.Fragment(range("macro.h", 3, 4), 1, "=")));
        var arguments = new ArrayList<List<Preprocessing.Fragment>>(List.of(fragments));
        var expansion = new Preprocessing.Expansion(2, "ASSIGN", range("macro.h", 0, 4), fragments,
                range("a.cpp", 0, 8), 0, null, arguments, List.of(), 0, 2, 3);
        var expansions = new ArrayList<>(List.of(expansion));
        var context = new DiagnosticContext(temp.resolve("a.cpp").toString(), "v", includes, expansions);
        var problem = new ParseProblem("expected-expression", "Expected expression", range("a.cpp", 8, 8), "", true,
                range("a.cpp", 8, 8), "Expected expression", related, context);
        related.clear(); includes.clear(); fragments.clear(); arguments.clear(); expansions.clear();
        assertEquals(1, problem.relatedRanges().size());
        assertEquals(2, context.includeChain().size());
        assertEquals(1, context.expansionChain().size());
        assertEquals(1, context.expansionChain().getFirst().arguments().getFirst().size());
        assertThrows(UnsupportedOperationException.class, () -> problem.relatedRanges().clear());
        assertThrows(UnsupportedOperationException.class, () -> context.includeChain().clear());
        assertThrows(UnsupportedOperationException.class, () -> expansion.arguments().getFirst().clear());
        var project = Project.create().addFile(file("a.cpp", problem)).build();
        var copy = new ProjectEditor(project).build();
        assertNotSame(project.files().getFirst(), copy.files().getFirst());
        assertEquals(project.parseProblems(), copy.parseProblems());
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(copy));
        var analyzed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(com.kniazkov.cpg.analysis.NameResolver.resolve(copy));
        assertEquals(project.diagnosticGroups(), analyzed.diagnosticGroups());
        assertThrows(UnsupportedOperationException.class, () -> project.parseProblems().clear());
        assertThrows(UnsupportedOperationException.class, () -> project.diagnosticGroups().getFirst().occurrences().clear());
    }

    @Test void jsonRetainsEachOccurrenceContextAndCrossFileEvidence() {
        var json = encoded(project(false));
        var group = (Map<?, ?>) ((List<?>) json.get("diagnosticGroups")).getFirst();
        assertEquals(2, ((Number) group.get("translationUnitCount")).intValue());
        assertEquals(3, ((Number) group.get("variantCount")).intValue());
        var occurrences = (List<?>) group.get("occurrences");
        assertEquals(4, occurrences.size());
        var first = (Map<?, ?>) occurrences.getFirst();
        assertEquals("+*", first.get("reason"));
        assertEquals(temp.resolve("shared.h").toString(), ((Map<?, ?>) first.get("primaryRange")).get("file"));
        assertEquals(temp.resolve("a.cpp").toString(), ((Map<?, ?>) first.get("range")).get("file"));
        var context = (Map<?, ?>) first.get("context");
        assertEquals(temp.resolve("a.cpp").toString(), context.get("translationUnit"));
        assertEquals("a1", context.get("variantId"));
        assertEquals(2, ((List<?>) context.get("includeChain")).size());
        assertEquals(List.of(), context.get("expansionChain"));
        var related = (Map<?, ?>) ((List<?>) first.get("relatedRanges")).getFirst();
        assertEquals("paste-operator", related.get("role"));
        assertEquals(temp.resolve("macro.h").toString(), ((Map<?, ?>) related.get("range")).get("file"));
    }

    @Test void oldConstructorGetsOwningTranslationUnitContextAtProjectBoundary() {
        var problem = new ParseProblem("parse-error", "Legacy error", range("shared.h", 0, 1), "x", false);
        var project = Project.create().addFile(file("a.cpp", problem)).addFile(file("b.cpp", problem)).build();
        assertEquals(problem.range(), problem.primaryRange());
        assertEquals(problem.message(), problem.reason());
        assertEquals(2, project.diagnosticGroups().getFirst().translationUnitCount());
        assertEquals(List.of(temp.resolve("a.cpp").toString(), temp.resolve("b.cpp").toString()),
                project.parseProblems().stream().map(p -> p.context().translationUnit()).toList());
    }

    private static Map<?, ?> encoded(Project project) {
        return (Map<?, ?>) ProjectJson.encode(project).toJavaObject();
    }

    private static String cli(Project project) {
        PrintStream previous = System.err;
        var bytes = new ByteArrayOutputStream();
        try (var stream = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setErr(stream);
            Main.printParseProblems(project);
        } finally {
            System.setErr(previous);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
