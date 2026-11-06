/* Actual public pass executions, scoped diagnostics and dependency freshness. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PassRunAnalysisTest {
    private Project parse(String source) {
        return Project.create().addFile(Parser.parse("runs.cpp", source, Language.CPP)).build();
    }

    private CallableDeclaration function(Project project, String name) {
        var found = new ArrayList<CallableDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(CallableDeclaration callable) {
            if (callable.name().equals(name)) found.add(callable);
        }});
        return found.getFirst();
    }

    private List<Diagnostic> warnings(CallableDeclaration callable) {
        var result = new ArrayList<Diagnostic>();
        callable.walk(new NodeVisitor() { @Override public void visit(Node node) {
            node.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals(UnreadWrites.CODE))
                    .forEach(result::add);
        }});
        return result;
    }

    private List<String> passes(Project project) {
        return project.passRuns().stream().map(PassRun::passId).toList();
    }

    @Test void noFindingsDistinguishesNotRunFromCompletedAndRepeatedCalls() {
        Project parsed = parse("int f() { int value = 1; return value; }");
        assertTrue(parsed.passRuns().isEmpty());
        assertTrue(parsed.passStatus("UnreadWrites", function(parsed, "f")).isEmpty());
        Project resolved = NameResolver.resolve(parsed);
        assertTrue(resolved.passStatus("UnreadWrites", function(resolved, "f")).isEmpty());
        Project result = UnreadWrites.analyze(resolved);
        assertTrue(warnings(function(result, "f")).isEmpty());
        assertEquals(PassRun.Status.CURRENT, result.passStatus("UnreadWrites", function(result, "f")).orElseThrow());
        assertEquals(List.of("ControlFlow", "DataFlow", "UnreadWrites"), passes(result).subList(resolved.passRuns().size(), result.passRuns().size()));
        Project repeated = UnreadWrites.analyze(result);
        assertEquals(result.passRuns().size() + 1, repeated.passRuns().size());
        assertEquals("UnreadWrites", passes(repeated).getLast());
        assertEquals(PassRun.Status.CURRENT, result.passStatus("UnreadWrites").orElseThrow());
        assertTrue(parsed.passRuns().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> repeated.passRuns().clear());
    }

    @Test void publicNoOpTransformationsRecordExactlyOneCompletion() {
        var operations = new LinkedHashMap<String, UnaryOperator<Project>>();
        operations.put("ConstantFolder", ConstantFolder::fold);
        operations.put("ConsoleNormalizer", ConsoleNormalizer::green);
        operations.put("TypeNormalizer", TypeNormalizer::green);
        operations.put("StandardConversions.written", StandardConversions::written);
        operations.put("StandardConversions", StandardConversions::analyze);
        operations.put("DynamicArraySemantics", DynamicArraySemantics::green);
        operations.put("ContainerSemantics", ContainerSemantics::green);
        operations.put("SequenceSemantics", SequenceSemantics::green);
        operations.put("Documentation", Documentation::analyze);
        operations.put("ClassTemplateSemantics", ClassTemplateSemantics::analyze);
        operations.put("ClassTemplateSemantics.diagnose", ClassTemplateSemantics::diagnose);
        operations.put("LibraryDeclarations.install", LibraryDeclarations::install);
        operations.forEach((name, operation) -> {
            Project before = Project.create().build();
            Project after = operation.apply(before);
            assertEquals(List.of(name), passes(after), name);
            assertEquals(PassRun.Status.CURRENT, after.passStatus(name).orElseThrow(), name);
            assertTrue(before.passRuns().isEmpty(), name);
            assertTrue(after.transformationResults(before).isEmpty(), name);
        });
    }

    @Test void resolutionRecordsNestedPublicExecutionsInCompletionOrder() {
        Project project = NameResolver.resolve(parse("int f() { return 0; }"));
        assertEquals(List.of("StandardConversions.written", "ClassTemplateSemantics", "ClassTemplateSemantics",
                "StandardConversions", "Documentation", "ClassTemplateSemantics.diagnose", "NameResolver"), passes(project));
        assertEquals(PassRun.Status.CURRENT, project.passStatus("NameResolver").orElseThrow());
        for (int index = 1; index < project.passRuns().size(); index++)
            assertTrue(project.passRuns().get(index - 1).sequence() < project.passRuns().get(index).sequence());
    }

    @Test void scopedRunPreservesOtherFunctionsFindingsAndRunState() {
        Project parsed = parse("void first() { int unused = 1; } void second() { int unused = 2; }");
        Project first = UnreadWrites.analyze(parsed, function(parsed, "first"));
        assertEquals(1, warnings(function(first, "first")).size());
        assertTrue(warnings(function(first, "second")).isEmpty());
        assertEquals(PassRun.Status.CURRENT, first.passStatus("UnreadWrites", function(first, "first")).orElseThrow());
        assertTrue(first.passStatus("UnreadWrites", function(first, "second")).isEmpty());
        assertEquals(PassRun.Scope.FUNCTION, first.passRuns().getLast().scope());
        assertEquals("first", first.passRuns().getLast().results().getFirst().name());
        Project second = UnreadWrites.analyze(first, function(first, "second"));
        assertEquals(first.passRuns().size() + 1, second.passRuns().size());
        assertEquals(1, warnings(function(second, "first")).size());
        assertEquals(1, warnings(function(second, "second")).size());
        assertEquals(PassRun.Status.CURRENT, second.passStatus("UnreadWrites", function(second, "first")).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, second.passStatus("UnreadWrites", function(second, "second")).orElseThrow());
        assertTrue(warnings(function(first, "second")).isEmpty());
        assertTrue(first.passStatus("UnreadWrites", function(first, "second")).isEmpty());
        Project repeated = UnreadWrites.analyze(second, function(second, "first"));
        assertEquals(1, warnings(function(repeated, "second")).size());
        assertEquals(1, warnings(function(repeated, "first")).size());
        assertThrows(IllegalArgumentException.class,
                () -> UnreadWrites.analyze(parsed, function(parse("void foreign() {}"), "foreign")));
    }

    @Test void scopedCalleeStillUsesWholeProjectObservations() {
        Project parsed = parse("void update(int& value) { value = 7; } int caller() { int x = 1; update(x); return x; }");
        Project result = UnreadWrites.analyze(parsed, function(parsed, "update"));
        assertTrue(warnings(function(result, "update")).isEmpty(), "The caller observes the callee's write");
        assertEquals(PassRun.Status.CURRENT, result.passStatus("DataFlow", function(result, "caller")).orElseThrow());
        assertTrue(result.passStatus("UnreadWrites", function(result, "caller")).isEmpty());
        assertEquals(PassRun.Scope.PROJECT, result.passRuns().get(result.passRuns().size() - 2).scope());
        assertEquals(PassRun.Scope.FUNCTION, result.passRuns().getLast().scope());
    }

    @Test void editRebuildsStaleDependenciesBeforeScopedAnalysisWithoutChangingAncestor() {
        Project before = UnreadWrites.analyze(parse("int first() { int x = 1; return x; } int second() { int unused = 2; return 0; }"));
        List<PassRun> originalHistory = before.passRuns();
        var values = new ArrayList<IntegerLiteral>();
        function(before, "first").walk(new NodeVisitor() { @Override public void visit(IntegerLiteral value) { values.add(value); } });
        var editor = new ProjectEditor(before).withoutRevalidation();
        editor.replace(values.getFirst(), IntegerLiteral.create().setValue(3).build());
        Project edited = editor.build();
        assertEquals(PassRun.Status.STALE, edited.passStatus("NameResolver").orElseThrow());
        assertEquals(PassRun.Status.STALE, edited.passStatus("DataFlow").orElseThrow());
        var otherStatus = edited.passStatus("UnreadWrites", function(edited, "second"));
        var otherFindings = warnings(function(edited, "second"));
        Project after = UnreadWrites.analyze(edited, function(edited, "first"));
        var dependencies = passes(after).subList(edited.passRuns().size(), after.passRuns().size()).stream()
                .filter(Set.of("NameResolver", "ControlFlow", "DataFlow", "UnreadWrites")::contains).toList();
        assertEquals(List.of("NameResolver", "ControlFlow", "DataFlow", "UnreadWrites"), dependencies);
        assertEquals(PassRun.Status.CURRENT, after.passStatus("DataFlow").orElseThrow());
        assertEquals(PassRun.Status.CURRENT, after.passStatus("UnreadWrites", function(after, "first")).orElseThrow());
        assertEquals(otherStatus, after.passStatus("UnreadWrites", function(after, "second")));
        assertEquals(otherFindings, warnings(function(after, "second")));
        assertEquals(PassRun.Scope.PROJECT, after.passRuns().get(after.passRuns().size() - 2).scope());
        assertEquals(PassRun.Scope.FUNCTION, after.passRuns().getLast().scope());
        assertSame(originalHistory, before.passRuns());
        assertEquals(PassRun.Status.CURRENT, before.passStatus("DataFlow").orElseThrow());
        assertEquals(PassRun.Status.CURRENT, before.passStatus("UnreadWrites", function(before, "second")).orElseThrow());
        assertEquals(1, values.getFirst().value());
    }

    @Test void noOpBuilderCannotAdvertiseDiscardedDataFlowAsCurrent() {
        Project analyzed = UnreadWrites.analyze(parse("void f() { int unused = 1; }"));
        Project copied = analyzed.edit().build();
        assertTrue(copied.flows().isEmpty());
        assertTrue(warnings(function(copied, "f")).isEmpty());
        assertEquals(PassRun.Status.STALE, copied.passStatus("DataFlow").orElseThrow());
        Project result = UnreadWrites.analyze(copied);
        assertEquals(List.of("ControlFlow", "DataFlow", "UnreadWrites"), passes(result).subList(copied.passRuns().size(), result.passRuns().size()));
        assertEquals(1, warnings(function(result, "f")).size());
        assertEquals(PassRun.Status.CURRENT, result.passStatus("DataFlow").orElseThrow());
        assertEquals(PassRun.Status.CURRENT, analyzed.passStatus("DataFlow").orElseThrow());
    }

    @Test void explicitCfgRecomputationInvalidatesDataFlowEvenWhenFlowsAreNonempty() {
        Project analyzed = UnreadWrites.analyze(parse("int f() { int x = 1; return x; }"));
        Project cfg = ControlFlow.analyze(analyzed);
        assertFalse(cfg.flows().isEmpty());
        assertTrue(cfg.flows().stream().allMatch(flow -> flow.data().isEmpty()));
        assertEquals(PassRun.Status.STALE, cfg.passStatus("DataFlow").orElseThrow());
        Project result = UnreadWrites.analyze(cfg);
        assertEquals(List.of("DataFlow", "UnreadWrites"), passes(result).subList(cfg.passRuns().size(), result.passRuns().size()));
        assertTrue(result.flows().stream().anyMatch(flow -> !flow.data().isEmpty()));
        assertTrue(warnings(function(result, "f")).isEmpty());
        assertEquals(PassRun.Status.CURRENT, analyzed.passStatus("DataFlow").orElseThrow());
    }
}
