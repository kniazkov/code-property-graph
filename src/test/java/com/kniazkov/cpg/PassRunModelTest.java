/* Immutable execution metadata and edit lineage. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PassRunModelTest {
    private static Project project() {
        return Project.create().addFile(Parser.parseRecovering("history.cpp",
                "int main() { return 1; } int helper() { return 2; }", Language.CPP)).build();
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }});
        return result;
    }
    private static CallableDeclaration function(Project project, String name) {
        return nodes(project, CallableDeclaration.class).stream().filter(node -> node.name().equals(name))
                .findFirst().orElseThrow();
    }

    @Test void completionIsAnImmutableOrderedEventEvenWithNoProducts() {
        Project source = Project.create().build();
        Project first = source.recordPass("Empty");
        Project second = first.recordPass("Empty");
        assertTrue(source.passRuns().isEmpty());
        assertEquals(1, first.passRuns().size());
        assertEquals(List.of(1L, 2L), second.passRuns().stream().map(PassRun::sequence).toList());
        assertEquals(PassRun.Status.CURRENT, second.passStatus("Empty").orElseThrow());
        assertTrue(second.passRuns().getFirst().results().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> second.passRuns().clear());
        assertThrows(IllegalArgumentException.class, () -> source.recordPass(" "));
        assertEquals(2, second.withSnapshotStage(SnapshotStage.FOLDED).passRuns().size());
    }

    @Test void scopedCoverageDoesNotInventExecutionForAnotherFunction() {
        Project source = project();
        CallableDeclaration main = function(source, "main"), helper = function(source, "helper");
        Project scoped = source.recordPass("Check", main);
        assertEquals(PassRun.Scope.FUNCTION, scoped.passRuns().getFirst().scope());
        assertEquals(PassRun.Status.CURRENT, scoped.passStatus("Check", main).orElseThrow());
        assertTrue(scoped.passStatus("Check", helper).isEmpty());
        assertEquals(PassRun.Status.STALE, scoped.passStatus("Check").orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> scoped.passRuns().getFirst().results().clear());
    }

    @Test void editRemapsCoverageAndInvalidatesOnlyItsAffectedFunction() {
        Project source = project().recordPass("Check");
        CallableDeclaration main = function(source, "main"), helper = function(source, "helper");
        var editor = new ProjectEditor(source).withoutRevalidation();
        editor.replace(nodes(main, IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(3).build());
        Project edited = editor.build();
        assertEquals(PassRun.Status.STALE, edited.passStatus("Check", main).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, edited.passStatus("Check", helper).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, source.passStatus("Check").orElseThrow());
        assertEquals(PassRun.Status.STALE, edited.passRuns().getFirst().status());
        for (PassRun.Result result : edited.passRuns().getFirst().results())
            assertSame(function(edited, result.name()), result.function().orElseThrow());
        assertNotSame(main, edited.correspondingCallable(main).orElseThrow());
    }

    @Test void removedFunctionBecomesAHistorySummaryAndFreshNamesDoNotMatchIt() {
        Project source = project().recordPass("Check");
        CallableDeclaration main = function(source, "main");
        var editor = new ProjectEditor(source).withoutRevalidation();
        editor.remove(main);
        Project removed = editor.build();
        PassRun.Result history = removed.passRuns().getFirst().results().getFirst();
        assertEquals("main", history.name());
        assertEquals(main.range(), history.range());
        assertTrue(history.function().isEmpty());
        assertEquals(PassRun.Status.STALE, history.status());
        assertTrue(removed.correspondingCallable(main).isEmpty());
        Project fresh = project();
        assertTrue(removed.correspondingCallable(function(fresh, "main")).isEmpty());
    }

    @Test void diagnosticCopiesKeepHistoryCurrentAndRemapEveryFunction() {
        Project source = project().recordPass("Check").recordPass("ControlFlow").recordPass("DataFlow");
        CallableDeclaration main = function(source, "main");
        Project annotated = source.withDiagnostics("note", Map.of(main,
                new Diagnostic("note", Diagnostic.Severity.WARNING, "Annotation only")));
        for (String pass : List.of("Check", "ControlFlow", "DataFlow"))
            assertEquals(PassRun.Status.CURRENT, annotated.passStatus(pass).orElseThrow());
        assertTrue(main.diagnostics().isEmpty());
        for (PassRun run : annotated.passRuns()) for (PassRun.Result result : run.results())
            assertSame(function(annotated, result.name()), result.function().orElseThrow());
    }

    @Test void discardedProductsAreStaleEvenForAnUnchangedBuilderCopy() {
        Project source = project().recordPass("NameResolver").recordPass("ControlFlow")
                .recordPass("DataFlow").recordPass("UnreadWrites");
        Project copied = source.edit().build();
        assertEquals(PassRun.Status.CURRENT, copied.passStatus("NameResolver").orElseThrow());
        for (String pass : List.of("ControlFlow", "DataFlow", "UnreadWrites")) {
            assertEquals(PassRun.Status.STALE, copied.passStatus(pass).orElseThrow());
            assertEquals(PassRun.Status.CURRENT, source.passStatus(pass).orElseThrow());
        }
        assertEquals(source.passRuns().size(), copied.passRuns().size());
    }

    @Test void callersThroughForwardDeclarationsAreInvalidatedWithTheirDefinition() {
        Project parsed = Project.create().addFile(Parser.parseRecovering("forward.cpp",
                "int helper(); int main() { return helper(); } int helper() { return 1; }", Language.CPP)).build();
        Project source = com.kniazkov.cpg.analysis.NameResolver.resolve(parsed).recordPass("Check");
        CallableDeclaration definition = nodes(source, CallableDeclaration.class).stream()
                .filter(node -> node.name().equals("helper") && node.body().isPresent()).findFirst().orElseThrow();
        var editor = new ProjectEditor(source).withoutRevalidation();
        editor.replace(nodes(definition, IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        Project edited = editor.build();
        assertEquals(PassRun.Status.STALE, edited.passStatus("Check", function(edited, "main")).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, source.passStatus("Check").orElseThrow());
    }

    @Test void callerObservationChangesRetireCalleeFindingsWithoutTouchingUnrelatedFunctions() {
        Project parsed = Project.create().addFile(Parser.parseRecovering("observed.cpp",
                "void update(int& value) { value = 7; } "
                        + "int caller() { int x = 1; update(x); return x; } int unrelated() { return 3; }", Language.CPP)).build();
        Project source = com.kniazkov.cpg.analysis.UnreadWrites.analyze(parsed);
        var editor = new ProjectEditor(source).withoutRevalidation();
        editor.replace(nodes(function(source, "caller"), ReturnStatement.class).getFirst().value().orElseThrow(),
                IntegerLiteral.create().setValue(0).build());
        Project edited = editor.build();
        assertEquals(PassRun.Status.STALE, edited.passStatus("UnreadWrites", function(edited, "update")).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, edited.passStatus("UnreadWrites", function(edited, "unrelated")).orElseThrow());
        Project scoped = com.kniazkov.cpg.analysis.UnreadWrites.analyze(edited, function(edited, "caller"));
        assertEquals(PassRun.Status.STALE, scoped.passStatus("UnreadWrites", function(scoped, "update")).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, scoped.passStatus("UnreadWrites", function(scoped, "unrelated")).orElseThrow());
        Project recomputed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(edited);
        assertTrue(nodes(function(recomputed, "update"), Node.class).stream().flatMap(node -> node.diagnostics().stream())
                .anyMatch(diagnostic -> diagnostic.code().equals("unread-write")));
        assertEquals(PassRun.Status.CURRENT, source.passStatus("UnreadWrites").orElseThrow());
    }

    @Test void addingOrRemovingCallsInvalidatesBothOldAndNewObservationDependencies() {
        for (boolean addCall : List.of(false, true)) {
            String oldBody = "int x = 1; " + (addCall ? "" : "update(x); ") + "return x;";
            Project parsed = Project.create().addFile(Parser.parseRecovering("calls.cpp",
                    "void update(int& value) { value = 7; } int caller() { " + oldBody
                            + " } int unrelated() { return 3; }", Language.CPP)).build();
            Project source = com.kniazkov.cpg.analysis.UnreadWrites.analyze(parsed);
            String newBody = "int x = 1; " + (addCall ? "update(x); " : "") + "return x;";
            Project replacement = Project.create().addFile(Parser.parseRecovering("replacement.cpp",
                    "int caller() { " + newBody + " }", Language.CPP)).build();
            var editor = new ProjectEditor(source).withoutRevalidation();
            editor.replace(function(source, "caller").body().orElseThrow(),
                    function(replacement, "caller").body().orElseThrow());
            Project edited = editor.build();
            assertEquals(PassRun.Status.STALE, edited.passStatus("UnreadWrites", function(edited, "update")).orElseThrow());
            assertEquals(PassRun.Status.CURRENT, edited.passStatus("UnreadWrites", function(edited, "unrelated")).orElseThrow());
            Project scoped = com.kniazkov.cpg.analysis.UnreadWrites.analyze(edited, function(edited, "caller"));
            assertEquals(PassRun.Status.STALE, scoped.passStatus("UnreadWrites", function(scoped, "update")).orElseThrow());
            Project recomputed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(edited);
            assertEquals(!addCall, nodes(function(recomputed, "update"), Node.class).stream()
                    .flatMap(node -> node.diagnostics().stream()).anyMatch(diagnostic -> diagnostic.code().equals("unread-write")));
            assertEquals(PassRun.Status.CURRENT, source.passStatus("UnreadWrites").orElseThrow());
        }
    }

    @Test void deletingTheOnlyCallerInvalidatesObservedCalleeWrites() {
        Project parsed = Project.create().addFile(Parser.parseRecovering("removed.cpp",
                "void update(int& value) { value = 7; } int caller() { int x = 1; update(x); return x; }", Language.CPP)).build();
        Project source = com.kniazkov.cpg.analysis.UnreadWrites.analyze(parsed);
        var editor = new ProjectEditor(source).withoutRevalidation();
        editor.remove(function(source, "caller"));
        Project edited = editor.build();
        assertEquals(PassRun.Status.STALE, edited.passStatus("UnreadWrites", function(edited, "update")).orElseThrow());
        Project recomputed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(edited);
        assertTrue(nodes(function(recomputed, "update"), Node.class).stream().flatMap(node -> node.diagnostics().stream())
                .anyMatch(diagnostic -> diagnostic.code().equals("unread-write")));
    }

    @Test void callsInsidePrototypeDefaultsParticipateInObservationDependencies() {
        Project parsed = Project.create().addFile(Parser.parseRecovering("default.cpp",
                "int global = 0; int update() { global = 7; return 0; } void f(int value = update()); "
                        + "int caller() { f(); return global; } void f(int value) {}", Language.CPP)).build();
        Project source = com.kniazkov.cpg.analysis.UnreadWrites.analyze(parsed);
        var editor = new ProjectEditor(source).withoutRevalidation();
        editor.replace(nodes(function(source, "caller"), ReturnStatement.class).getFirst().value().orElseThrow(),
                IntegerLiteral.create().setValue(0).build());
        Project edited = editor.build();
        assertEquals(PassRun.Status.STALE, edited.passStatus("UnreadWrites", function(edited, "update")).orElseThrow());
        Project scoped = com.kniazkov.cpg.analysis.UnreadWrites.analyze(edited, function(edited, "caller"));
        assertEquals(PassRun.Status.STALE, scoped.passStatus("UnreadWrites", function(scoped, "update")).orElseThrow());
        Project recomputed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(edited);
        assertTrue(nodes(function(recomputed, "update"), Node.class).stream().flatMap(node -> node.diagnostics().stream())
                .anyMatch(diagnostic -> diagnostic.code().equals("unread-write")));
    }

    @Test void changingResolutionStateWithTheSameCandidatesInvalidatesNameResolution() {
        Project parsed = Project.create().addFile(Parser.parseRecovering("resolution.cpp",
                "int helper() { return 0; } int caller() { return helper(); }", Language.CPP)).build();
        Project source = com.kniazkov.cpg.analysis.NameResolver.resolve(parsed);
        CallExpression call = nodes(source, CallExpression.class).getFirst();
        var editor = new ProjectEditor(source).withoutRevalidation();
        editor.resolve(call, call.candidates(), CallExpression.Resolution.AMBIGUOUS);
        Project edited = editor.build();
        assertEquals(PassRun.Status.STALE, edited.passStatus("NameResolver").orElseThrow());
        Project flowed = com.kniazkov.cpg.analysis.ControlFlow.analyze(edited);
        assertTrue(flowed.passRuns().subList(edited.passRuns().size(), flowed.passRuns().size()).stream()
                .anyMatch(run -> run.passId().equals("NameResolver")));
        assertEquals(PassRun.Status.CURRENT, source.passStatus("NameResolver").orElseThrow());
    }

    @Test void memberAccessChangesInvalidateResolutionEvenWhenTheBodyIsUnchanged() {
        Project parsed = Project.create().addFile(Parser.parseRecovering("access.cpp",
                "struct X { int f() { return 0; } }; int caller() { X x; return x.f(); }", Language.CPP)).build();
        Project source = com.kniazkov.cpg.analysis.NameResolver.resolve(parsed);
        CallableDeclaration member = function(source, "f");
        var replacement = member.edit(); replacement.setAccess("private");
        var editor = new ProjectEditor(source).withoutRevalidation();
        editor.replace(member, replacement.build());
        Project edited = editor.build();
        assertEquals(PassRun.Status.STALE, edited.passStatus("NameResolver").orElseThrow());
        assertEquals(PassRun.Status.CURRENT, source.passStatus("NameResolver").orElseThrow());
        assertEquals(Optional.of("private"), edited.correspondingCallable(member).orElseThrow().access());
    }
}
