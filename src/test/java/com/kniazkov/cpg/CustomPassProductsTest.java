/* Typed product ownership, atomic publication and immutable snapshots. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.passes.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomPassProductsTest {
    private static final PassId FIRST = new PassId("First"), SECOND = new PassId("Second");
    private static final ResultKey<Integer> COUNT = new ResultKey<>(FIRST, "count", Integer.class);
    private static final ResultKey<String> LABEL = new ResultKey<>(FIRST, "label", String.class);
    private static Project project() {
        return Project.create().addFile(Parser.parseRecovering("products.cpp",
                "int main() { return 1; } int helper() { return 2; }", Language.CPP)).build();
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var found = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) found.add(type.cast(node));
        }});
        return found;
    }
    private static CallableDeclaration function(Project project, String name) {
        return nodes(project, CallableDeclaration.class).stream().filter(n -> n.name().equals(name)).findFirst().orElseThrow();
    }
    private static Project publish(Project project, PassId id, CallableDeclaration scope,
            Map<Node, Map<ResultKey<?>, Object>> values, Map<Node, List<Diagnostic>> findings) {
        return project.publishAnalysis(id, Set.of(), scope, values, findings);
    }
    private static Diagnostic warning() { return new Diagnostic("shared-code", Diagnostic.Severity.WARNING, "Same finding"); }

    @Test void publicationKeepsTypedValuesAndFrozenExportIndependentOfInputs() {
        Project original = project(); Node main = function(original, "main");
        var mutable = new LinkedHashMap<ResultKey<?>, Object>(); mutable.put(COUNT, 4); mutable.put(LABEL, "main");
        Project result = publish(original, FIRST, null, Map.of(main, mutable), Map.of());
        mutable.clear();
        assertEquals(4, result.resultOf(main, COUNT).orElseThrow());
        assertEquals("main", result.resultOf(main, LABEL).orElseThrow());
        assertTrue(original.resultOf(main, COUNT).isEmpty());
        assertEquals(2, result.analysisResults().size());
        assertThrows(UnsupportedOperationException.class, () -> result.analysisResults().clear());
        assertTrue(result.resultOf(function(project(), "main"), COUNT).isEmpty());
        assertEquals(PassRun.Status.CURRENT, result.resultStatus(main, COUNT).orElseThrow());
    }

    @Test void scopedRerunReplacesAllProducerKeysAndFindingsAndPreservesOtherOwners() {
        Project original = project(); var main = function(original, "main"); var helper = function(original, "helper");
        Diagnostic same = warning();
        Project baseline = original.withDiagnostics(same.code(), Map.of(main, same));
        Node currentMain = baseline.correspondingNode(main).orElseThrow(), currentHelper = baseline.correspondingNode(helper).orElseThrow();
        Project first = publish(baseline, FIRST, null,
                Map.of(currentMain, Map.of(COUNT, 3, LABEL, "old"), currentHelper, Map.of(COUNT, 9)),
                Map.of(currentMain, List.of(same), currentHelper, List.of(same)));
        Project second = publish(first, SECOND, null, Map.of(),
                Map.of(first.correspondingNode(main).orElseThrow(), List.of(same)));
        assertEquals(3, second.correspondingNode(main).orElseThrow().diagnostics().size());
        Project rerun = publish(second, FIRST, main,
                Map.of(second.correspondingNode(main).orElseThrow(), Map.of(COUNT, 5)), Map.of());
        assertEquals(5, rerun.resultOf(main, COUNT).orElseThrow());
        assertTrue(rerun.resultOf(main, LABEL).isEmpty());
        assertEquals(9, rerun.resultOf(helper, COUNT).orElseThrow());
        assertEquals(2, rerun.correspondingNode(main).orElseThrow().diagnostics().size());
        assertEquals(1, rerun.correspondingNode(helper).orElseThrow().diagnostics().size());
        assertEquals(3, second.correspondingNode(main).orElseThrow().diagnostics().size());
        assertEquals(3, second.resultOf(main, COUNT).orElseThrow());
        Project clearSecond = publish(rerun, SECOND, null, Map.of(), Map.of());
        assertEquals(List.of(same), clearSecond.correspondingNode(main).orElseThrow().diagnostics());
    }

    @Test void arbitraryCustomInputsMakeEveryCustomProductStaleAfterAnEdit() {
        Project original = project(); var main = function(original, "main"); var helper = function(original, "helper");
        Project analyzed = publish(original, FIRST, null,
                Map.of(main, Map.of(COUNT, 1), helper, Map.of(COUNT, 2)),
                Map.of(main, List.of(warning()), helper, List.of(warning())));
        var editor = new ProjectEditor(analyzed).withoutRevalidation();
        editor.replace(nodes(function(analyzed, "main"), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(3).build());
        Project edited = editor.build();
        for (var callable : List.of(main, helper)) {
            assertTrue(edited.resultOf(callable, COUNT).isEmpty());
            assertEquals(PassRun.Status.STALE, edited.resultStatus(callable, COUNT).orElseThrow());
            assertTrue(edited.correspondingNode(callable).orElseThrow().diagnostics().isEmpty());
            assertEquals(PassRun.Status.STALE, edited.passStatus(FIRST.value(), callable).orElseThrow());
        }
        assertEquals(2, edited.analysisResults().size());
        assertEquals(2, edited.analysisDiagnostics().size());
        assertEquals(PassRun.Status.CURRENT, analyzed.passStatus(FIRST.value()).orElseThrow());
        Project refreshed = publish(edited, FIRST, main, Map.of(edited.correspondingNode(main).orElseThrow(), Map.of(COUNT, 7)), Map.of());
        assertEquals(7, refreshed.resultOf(main, COUNT).orElseThrow());
        assertTrue(refreshed.resultOf(helper, COUNT).isEmpty());
        assertEquals(PassRun.Status.STALE, refreshed.resultStatus(helper, COUNT).orElseThrow());
    }

    @Test void removedAndReplacedTargetsNeverKeepHistoricalNodeProducts() {
        Project original = project(); var main = function(original, "main");
        Node literal = nodes(main, IntegerLiteral.class).getFirst();
        Project analyzed = publish(original, FIRST, null,
                Map.of(main, Map.of(COUNT, 1), literal, Map.of(COUNT, 2)), Map.of(main, List.of(warning())));
        var editor = new ProjectEditor(analyzed).withoutRevalidation(); editor.remove(function(analyzed, "main"));
        Project removed = editor.build();
        assertTrue(removed.analysisResults().isEmpty()); assertTrue(removed.analysisDiagnostics().isEmpty());
        assertTrue(removed.resultOf(main, COUNT).isEmpty()); assertTrue(removed.resultStatus(main, COUNT).isEmpty());
        assertTrue(removed.correspondingNode(literal).isEmpty());
        assertEquals(2, analyzed.analysisResults().size());
    }

    @Test void encodingFailureAndForeignTargetsCannotPublishPartialState() {
        record Value(String text) {}
        var custom = new ResultKey<>(FIRST, "custom", Value.class);
        Project source = project(); var main = function(source, "main");
        Project analyzed = publish(source, FIRST, null, Map.of(main, Map.of(COUNT, 3)), Map.of(main, List.of(warning())));
        Node target = analyzed.correspondingNode(main).orElseThrow();
        var types = ResultTypes.defaults().with(Value.class, value -> { throw new IllegalStateException("encode failed"); });
        assertThrows(IllegalStateException.class, () -> analyzed.publishAnalysis(FIRST, Set.of(), null,
                Map.of(target, Map.of(COUNT, 9, custom, new Value("bad"))), Map.of(), types));
        assertThrows(IllegalArgumentException.class, () -> publish(analyzed, FIRST, null,
                Map.of(function(project(), "main"), Map.of(COUNT, 5)), Map.of()));
        assertEquals(3, analyzed.resultOf(main, COUNT).orElseThrow());
        assertEquals(1, analyzed.analysisDiagnostics().size()); assertEquals(1, analyzed.passRuns().size());
    }

    @Test void encodedTreeIsFrozenBeforeTheProducerOrEncoderCanChangeIt() {
        record Value(String text) {}
        var custom = new ResultKey<>(FIRST, "custom", Value.class);
        var map = new LinkedHashMap<String, Object>(); var list = new ArrayList<Object>(); list.add("original"); map.put("items", list);
        var types = ResultTypes.defaults().with(Value.class, value -> map);
        Project source = project(); Node main = function(source, "main");
        Project analyzed = source.publishAnalysis(FIRST, Set.of(), null,
                Map.of(main, Map.of(custom, new Value("immutable"))), Map.of(), types);
        list.add("later"); map.put("extra", true);
        assertEquals(Map.of("items", List.of("original")), analyzed.analysisResults().getFirst().encodedValue());
    }

    @Test void dependencyRerunsConservativelyInvalidateCrossScopeReaders() {
        PassId middle = new PassId("Middle"), last = new PassId("Last");
        var middleKey = new ResultKey<>(middle, "value", Integer.class);
        var lastKey = new ResultKey<>(last, "value", Integer.class);
        Project source = project().recordPass("Prerequisite"); var main = function(source, "main"); var helper = function(source, "helper");
        Project mid = source.publishAnalysis(middle, Set.of(new PassId("Prerequisite")), null,
                Map.of(main, Map.of(middleKey, 1), helper, Map.of(middleKey, 2)), Map.of(main, List.of(warning())));
        Project complete = mid.publishAnalysis(last, Set.of(middle), null,
                Map.of(mid.correspondingNode(main).orElseThrow(), Map.of(lastKey, 3), mid.correspondingNode(helper).orElseThrow(), Map.of(lastKey, 4)), Map.of());
        Project rerun = complete.recordPass("Prerequisite", main);
        assertTrue(rerun.resultOf(main, middleKey).isEmpty()); assertTrue(rerun.resultOf(main, lastKey).isEmpty());
        assertTrue(rerun.resultOf(helper, middleKey).isEmpty()); assertTrue(rerun.resultOf(helper, lastKey).isEmpty());
        assertEquals(PassRun.Status.STALE, rerun.passStatus(last.value(), main).orElseThrow());
        assertEquals(PassRun.Status.STALE, rerun.passStatus(last.value(), helper).orElseThrow());
        assertTrue(rerun.correspondingNode(main).orElseThrow().diagnostics().isEmpty());
        assertEquals(3, complete.resultOf(main, lastKey).orElseThrow());
    }

    @Test void customUnreadWriteCodeIsNeverAdoptedByBuiltinFindingRestoration() {
        Project original = project().recordPass("UnreadWrites");
        var main = function(original, "main"); var helper = function(original, "helper");
        Diagnostic custom = new Diagnostic("unread-write", Diagnostic.Severity.WARNING, "Custom ownership");
        Project analyzed = publish(original, FIRST, null, Map.of(), Map.of(helper, List.of(custom)));
        var editor = new ProjectEditor(analyzed).withoutRevalidation();
        editor.replace(nodes(function(analyzed, "main"), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(5).build());
        Project edited = editor.build();
        assertTrue(edited.correspondingNode(helper).orElseThrow().diagnostics().isEmpty());
        assertEquals(PassRun.Status.STALE, edited.analysisDiagnostics().getFirst().status());
        Project builtin = edited.retainUnchangedPassResults(analyzed, "UnreadWrites");
        assertTrue(builtin.correspondingNode(helper).orElseThrow().diagnostics().isEmpty());
        assertEquals(List.of(custom), analyzed.correspondingNode(helper).orElseThrow().diagnostics());
        assertEquals(PassRun.Status.STALE, edited.retainUnchangedPassResults(analyzed, FIRST.value())
                .passStatus(FIRST.value(), helper).orElseThrow());
    }

    @Test void annotationCopiesAndRootMetadataNeverRetainThePreviousRoot() {
        Project source = project(); Node main = function(source, "main");
        Project analyzed = publish(source, FIRST, null, Map.of(source, Map.of(COUNT, 8), main, Map.of(COUNT, 1)), Map.of(source, List.of(warning())));
        assertSame(analyzed, analyzed.analysisResults().getFirst().node());
        assertSame(analyzed, analyzed.analysisDiagnostics().getFirst().node());
        Project annotated = analyzed.withDiagnostics("other", Map.of(analyzed.correspondingNode(main).orElseThrow(),
                new Diagnostic("other", Diagnostic.Severity.INFO, "Built-in annotation")));
        assertEquals(8, annotated.resultOf(source, COUNT).orElseThrow());
        assertSame(annotated, annotated.analysisResults().getFirst().node());
        assertEquals(1, annotated.diagnostics().size()); assertEquals(1, analyzed.diagnostics().size());
        Project recorded = annotated.recordPass("Unrelated");
        assertSame(recorded, recorded.analysisResults().getFirst().node());
        assertSame(recorded, recorded.analysisDiagnostics().getFirst().node());
    }

    @Test void scopedCoverageIncludesNestedOwnedCallablesAcrossNoOpCopies() {
        Project source = Project.create().addFile(Parser.parseRecovering("nested.cpp",
                "int f() { struct C { int g() { return 1; } }; return 0; }", Language.CPP)).build();
        var outer = function(source, "f"); var nested = function(source, "g");
        Project analyzed = publish(source, FIRST, outer,
                Map.of(outer, Map.of(COUNT, 1), nested, Map.of(COUNT, 2)), Map.of(nested, List.of(warning())));
        assertEquals(List.of("f", "g"), analyzed.passRuns().getFirst().results().stream().map(PassRun.Result::name).toList());
        assertEquals(PassRun.Status.CURRENT, analyzed.passStatus(FIRST.value(), nested).orElseThrow());
        Project copied = analyzed.edit().build();
        assertEquals(2, copied.resultOf(nested, COUNT).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, copied.resultStatus(nested, COUNT).orElseThrow());
        assertEquals(1, copied.correspondingNode(nested).orElseThrow().diagnostics().size());
        Project rerun = publish(copied, FIRST, outer, Map.of(), Map.of());
        assertTrue(rerun.resultOf(nested, COUNT).isEmpty());
        assertTrue(rerun.correspondingNode(nested).orElseThrow().diagnostics().isEmpty());
    }

    @Test void scopedProducerRerunsPreserveProductsOutsideEveryFunction() {
        Project source = project(); var main = function(source, "main");
        Project analyzed = publish(source, FIRST, null, Map.of(source, Map.of(COUNT, 8), main, Map.of(COUNT, 1)),
                Map.of(source, List.of(warning())));
        Project scoped = publish(analyzed, FIRST, main, Map.of(), Map.of());
        assertEquals(8, scoped.resultOf(source, COUNT).orElseThrow());
        assertTrue(scoped.resultOf(main, COUNT).isEmpty());
        assertEquals(PassRun.Status.CURRENT, scoped.resultStatus(source, COUNT).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, scoped.analysisDiagnostics().getFirst().status());
        assertEquals(1, scoped.diagnostics().size());
        assertEquals(PassRun.Status.CURRENT, scoped.passStatus(FIRST.value()).orElseThrow());
    }

    @Test void fullFunctionCoverageNeverPretendsTheGlobalCustomCallbackRan() {
        Project source = project(); var main = function(source, "main"); var helper = function(source, "helper");
        Project first = publish(source, FIRST, main, Map.of(main, Map.of(COUNT, 1)), Map.of());
        Project both = publish(first, FIRST, helper, Map.of(helper, Map.of(COUNT, 2)), Map.of());
        assertEquals(PassRun.Status.CURRENT, both.passStatus(FIRST.value(), main).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, both.passStatus(FIRST.value(), helper).orElseThrow());
        assertEquals(PassRun.Status.STALE, both.passStatus(FIRST.value()).orElseThrow());
        Project global = publish(both, FIRST, null, Map.of(both, Map.of(COUNT, 3)), Map.of());
        assertEquals(PassRun.Status.CURRENT, global.passStatus(FIRST.value()).orElseThrow());
        assertEquals(3, global.resultOf(source, COUNT).orElseThrow());
    }

    @Test void builtinPrerequisiteRestorationNeverAdoptsCustomRootDiagnostics() {
        Project source = project(); Diagnostic same = warning();
        Project nativeOnly = source.withDiagnostics(same.code(), Map.of(source, same));
        Project analyzed = publish(nativeOnly, FIRST, null, Map.of(), Map.of(nativeOnly, List.of(same)));
        assertEquals(2, analyzed.diagnostics().size());
        Project flow = com.kniazkov.cpg.analysis.ControlFlow.analyze(analyzed);
        Project cleared = publish(flow, FIRST, null, Map.of(), Map.of());
        assertEquals(List.of(same), cleared.diagnostics());
        assertEquals(List.of(same), cleared.intrinsicDiagnostics());
        assertEquals(2, analyzed.diagnostics().size());
    }

    @Test void unchangedCopiesPreserveCustomProductsUnlessADeclaredPrerequisiteWasDiscarded() {
        Project source = project().recordPass("DataFlow"); Node main = function(source, "main");
        Project plain = publish(source, FIRST, null, Map.of(main, Map.of(COUNT, 1)), Map.of());
        var dependent = new ResultKey<>(SECOND, "value", Integer.class);
        Project complete = plain.publishAnalysis(SECOND, Set.of(new PassId("DataFlow")), null,
                Map.of(main, Map.of(dependent, 2)), Map.of());
        Project copy = complete.edit().build();
        assertEquals(1, copy.resultOf(main, COUNT).orElseThrow());
        assertTrue(copy.resultOf(main, dependent).isEmpty());
        assertEquals(PassRun.Status.STALE, copy.resultStatus(main, dependent).orElseThrow());
        assertEquals(2, complete.resultOf(main, dependent).orElseThrow());
    }
}
