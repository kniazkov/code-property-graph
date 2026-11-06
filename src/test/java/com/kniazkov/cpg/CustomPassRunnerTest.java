/* Public custom scheduling, scopes and transaction boundaries. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.passes.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomPassRunnerTest {
    private static Project parse(String source) {
        return Project.create().addFile(Parser.parse("custom.cpp", source, Language.CPP)).build();
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }});
        return result;
    }
    private static CallableDeclaration function(Project project, String name) {
        return nodes(project, CallableDeclaration.class).stream().filter(fn -> fn.name().equals(name)).findFirst().orElseThrow();
    }
    private static AnalysisPass analysis(PassId id, Set<PassId> requires, BiConsumer<PassContext, AnalysisResults> callback) {
        return new AnalysisPass() {
            public PassId id() { return id; }
            public Set<PassId> requires() { return requires; }
            public void analyze(PassContext context, AnalysisResults results) { callback.accept(context, results); }
        };
    }
    private static TransformationPass transformation(PassId id, Set<PassId> requires, BiConsumer<PassContext, ProjectEditor> callback) {
        return new TransformationPass() {
            public PassId id() { return id; }
            public Set<PassId> requires() { return requires; }
            public void transform(PassContext context, ProjectEditor editor) { callback.accept(context, editor); }
        };
    }
    private static List<String> passes(Project project) { return project.passRuns().stream().map(PassRun::passId).toList(); }

    @Test void dependencyDiamondIsDeterministicAndOnlyRequestedTargetAlwaysReruns() {
        var root = new PassId("root"); var alpha = new PassId("alpha");
        var zeta = new PassId("zeta"); var shared = new PassId("shared");
        var observed = new ArrayList<PassId>();
        var runner = new PassRunner();
        runner.register(analysis(root, Set.of(zeta, alpha), (c, r) -> observed.add(root)));
        runner.register(analysis(zeta, Set.of(shared), (c, r) -> observed.add(zeta)));
        runner.register(analysis(alpha, Set.of(shared), (c, r) -> observed.add(alpha)));
        runner.register(analysis(shared, Set.of(), (c, r) -> observed.add(shared)));
        Project input = parse("int f() { return 0; }");
        Project first = runner.run(input, root);
        assertEquals(List.of(shared, alpha, zeta, root), observed);
        assertEquals(List.of("shared", "alpha", "zeta", "root"), passes(first));
        assertTrue(input.passRuns().isEmpty());
        Project second = runner.run(first, root);
        assertEquals(List.of(shared, alpha, zeta, root, root), observed);
        assertEquals(first.passRuns().size() + 1, second.passRuns().size());
        assertEquals(PassRun.Status.CURRENT, second.passStatus(root.value()).orElseThrow());
    }

    @Test void missingAndCyclicRegistrationsFailBeforeAnyCallback() {
        var a = new PassId("a"); var b = new PassId("b"); var calls = new AtomicInteger();
        var runner = new PassRunner().register(analysis(a, Set.of(b), (c, r) -> calls.incrementAndGet()));
        Project input = parse("int f() { return 0; }");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> runner.run(input, a)).getMessage().contains("Missing"));
        runner.register(analysis(b, Set.of(a), (c, r) -> calls.incrementAndGet()));
        assertTrue(assertThrows(IllegalArgumentException.class, runner::validate).getMessage().contains("Cyclic"));
        assertThrows(IllegalArgumentException.class, () -> runner.run(input, a));
        assertEquals(0, calls.get());
        assertTrue(input.passRuns().isEmpty());
    }

    @Test void registrationCapturesRequirementsAndRejectsDuplicateIdentity() {
        var a = new PassId("a"); var mutable = new HashSet<PassId>();
        var runner = new PassRunner().register(analysis(a, mutable, (c, r) -> {}));
        mutable.add(new PassId("not-registered"));
        assertDoesNotThrow(runner::validate);
        assertThrows(IllegalArgumentException.class, () -> runner.register(analysis(a, Set.of(), (c, r) -> {})));
        assertThrows(IllegalArgumentException.class, () -> runner.register(analysis(BuiltinPasses.DATA_FLOW, Set.of(), (c, r) -> {})));
        assertThrows(UnsupportedOperationException.class, () -> runner.registeredPasses().clear());
    }

    @Test void scopedContextUsesPreparedSnapshotAndGlobalBuiltInGraphDependencies() {
        Project input = parse("int first() { int a = 1; return a; } int second() { return 2; }");
        CallableDeclaration original = function(input, "first");
        var id = new PassId("scoped"); var key = new ResultKey<>(id, "count", Integer.class);
        var runner = new PassRunner().register(analysis(id, Set.of(BuiltinPasses.DATA_FLOW), (context, results) -> {
            assertNotSame(input, context.project());
            assertNotSame(original, context.function().orElseThrow());
            assertEquals("first", context.function().orElseThrow().name());
            assertEquals(Set.of(BuiltinPasses.DATA_FLOW), context.dependencies());
            assertEquals(PassRun.Status.CURRENT, context.project().passStatus("DataFlow").orElseThrow());
            assertEquals(nodes(context.function().orElseThrow(), Node.class), context.nodes());
            assertThrows(UnsupportedOperationException.class, () -> context.nodes().clear());
            assertThrows(UnsupportedOperationException.class, () -> context.dependencies().clear());
            results.put(context.function().orElseThrow(), key, context.nodes().size());
        }));
        Project result = runner.run(input, id, original);
        assertTrue(result.resultOf(original, key).orElseThrow() > 0);
        assertEquals(PassRun.Scope.FUNCTION, result.passRuns().getLast().scope());
        assertEquals(PassRun.Status.STALE, result.passStatus(id.value()).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, result.passStatus("DataFlow", function(result, "second")).orElseThrow());
        assertTrue(input.passRuns().isEmpty());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test void sinkRejectsForeignProducerTypeScopeAndOldSnapshotNodes() {
        Project input = parse("int first() { return 1; } int second() { return 2; }");
        var id = new PassId("owned"); var key = new ResultKey<>(id, "count", Integer.class);
        var runner = new PassRunner().register(analysis(id, Set.of(BuiltinPasses.NAME_RESOLVER), (c, r) -> {
            Node selected = c.function().orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> r.put(selected, new ResultKey<>(new PassId("other"), "x", Integer.class), 1));
            assertThrows(IllegalArgumentException.class, () -> r.put(selected, (ResultKey) key, "wrong"));
            assertThrows(IllegalArgumentException.class, () -> r.put(function(c.project(), "second"), key, 1));
            assertThrows(IllegalArgumentException.class, () -> r.put(function(input, "first"), key, 1));
            assertThrows(IllegalArgumentException.class, () -> r.diagnose(c.project(), new Diagnostic("x", Diagnostic.Severity.INFO, "Outside")));
            r.put(selected, key, 1);
            assertThrows(IllegalArgumentException.class, () -> r.put(selected, new ResultKey<>(id, "count", String.class), "conflict"));
        }));
        assertEquals(1, runner.run(input, id, function(input, "first")).resultOf(function(input, "first"), key).orElseThrow());
    }

    @Test void escapedSinkIsClosedAfterSuccessAndFailure() {
        Project input = parse("int f() { return 1; }");
        var id = new PassId("capture"); var key = new ResultKey<>(id, "count", Integer.class);
        var captured = new AtomicReference<AnalysisResults>();
        var selected = new AtomicReference<Node>();
        var fail = new AtomicBoolean();
        var runner = new PassRunner().register(analysis(id, Set.of(), (c, r) -> {
            captured.set(r); selected.set(c.nodes().getFirst());
            if (fail.get()) throw new IllegalStateException("failed");
        }));
        runner.run(input, id);
        assertThrows(IllegalStateException.class, () -> captured.get().put(selected.get(), key, 1));
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> runner.run(input, id));
        assertThrows(IllegalStateException.class, () -> captured.get().diagnose(selected.get(), new Diagnostic("x", Diagnostic.Severity.INFO, "Late")));
        assertTrue(input.passRuns().isEmpty());
    }

    @Test void failedRerunLeavesOriginalProductsAndHistoryUntouched() {
        Project input = parse("int f() { return 1; }");
        var id = new PassId("atomic"); var key = new ResultKey<>(id, "value", Integer.class);
        var fail = new AtomicBoolean();
        var runner = new PassRunner().register(analysis(id, Set.of(), (c, r) -> {
            r.put(function(c.project(), "f"), key, fail.get() ? 2 : 1);
            r.diagnose(function(c.project(), "f"), new Diagnostic("atomic-warning", Diagnostic.Severity.WARNING, "Finding"));
            if (fail.get()) throw new IllegalStateException("After staging");
        }));
        Project first = runner.run(input, id);
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> runner.run(first, id));
        assertEquals(1, first.resultOf(function(first, "f"), key).orElseThrow());
        assertEquals(1, first.passRuns().size());
        assertEquals(1, first.analysisDiagnostics().size());
        assertTrue(input.analysisResults().isEmpty());
    }

    @Test void scopedEmptyRerunReplacesOnlyThisProducersScope() {
        Project input = parse("int first() { return 1; } int second() { return 2; }");
        var a = new PassId("a"); var b = new PassId("b");
        var ka = new ResultKey<>(a, "same", Integer.class); var kb = new ResultKey<>(b, "same", Integer.class);
        var omit = new AtomicBoolean();
        var runner = new PassRunner().register(analysis(a, Set.of(), (c, r) -> {
            if (!omit.get()) c.nodes().stream().filter(CallableDeclaration.class::isInstance).forEach(node -> {
                r.put(node, ka, 1); r.diagnose(node, new Diagnostic("same-code", Diagnostic.Severity.WARNING, "a"));
            });
        })).register(analysis(b, Set.of(), (c, r) -> {
            c.nodes().stream().filter(CallableDeclaration.class::isInstance).forEach(node -> {
                r.put(node, kb, 2); r.diagnose(node, new Diagnostic("same-code", Diagnostic.Severity.WARNING, "b"));
            });
        }));
        Project before = runner.run(runner.run(input, a), b);
        omit.set(true);
        Project after = runner.run(before, a, function(before, "first"));
        assertTrue(after.resultOf(function(after, "first"), ka).isEmpty());
        assertEquals(1, after.resultOf(function(after, "second"), ka).orElseThrow());
        assertEquals(2, after.resultOf(function(after, "first"), kb).orElseThrow());
        assertEquals(2, after.resultOf(function(after, "second"), kb).orElseThrow());
        assertEquals(3, after.analysisDiagnostics().size());
        assertEquals(4, before.analysisDiagnostics().size());
        assertEquals(PassRun.Status.CURRENT, after.passStatus(a.value()).orElseThrow());
    }

    @Test void transformationBatchIsImmutableAndRecordsItsRealProvenance() {
        Project input = parse("int f() { return 1 + 2; }");
        var id = new PassId("replace-literals");
        var captured = new AtomicReference<ProjectEditor>();
        var runner = new PassRunner().register(transformation(id, Set.of(), (c, editor) -> {
            captured.set(editor);
            for (IntegerLiteral literal : nodes(c.project(), IntegerLiteral.class))
                editor.replace(literal, IntegerLiteral.create().setValue(literal.value() + 10).build());
        }));
        Project result = runner.run(input, id);
        assertEquals(List.of(1, 2), nodes(input, IntegerLiteral.class).stream().map(IntegerLiteral::value).toList());
        assertEquals(List.of(11, 12), nodes(result, IntegerLiteral.class).stream().map(IntegerLiteral::value).toList());
        assertTrue(nodes(result, IntegerLiteral.class).stream().allMatch(node -> node.transformationProvenance().stream().anyMatch(p -> p.passId().equals(id.value()))));
        assertEquals(id.value(), result.passRuns().getLast().passId());
        assertTrue(input.passRuns().isEmpty());
        assertThrows(IllegalStateException.class, () -> captured.get().build());
        assertThrows(IllegalStateException.class, () -> captured.get().setPassId(id.value()));
    }

    @Test void transformationFailureEarlyBuildAndProvenanceSpoofPublishNothing() {
        Project input = parse("int f() { return 1; }");
        var failure = new PassId("failure"); var early = new PassId("early"); var spoof = new PassId("spoof");
        var captured = new AtomicReference<ProjectEditor>();
        var runner = new PassRunner().register(transformation(failure, Set.of(), (c, editor) -> {
            captured.set(editor);
            editor.replace(nodes(c.project(), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(9).build());
            throw new IllegalStateException("After edit");
        })).register(transformation(early, Set.of(), (c, editor) -> editor.build()))
          .register(transformation(spoof, Set.of(), (c, editor) -> editor.setPassId("invented")));
        assertThrows(IllegalStateException.class, () -> runner.run(input, failure));
        assertThrows(IllegalStateException.class, () -> captured.get().build());
        assertThrows(IllegalStateException.class, () -> runner.run(input, early));
        assertThrows(IllegalArgumentException.class, () -> runner.run(input, spoof));
        assertEquals(1, nodes(input, IntegerLiteral.class).getFirst().value());
        assertTrue(input.passRuns().isEmpty());
    }

    @Test void scopedTransformRestrictsWritesButAllowsExternalReferenceTargets() {
        Project input = parse("int global; int first() { return global; } int second() { return global; }");
        var id = new PassId("scope-bind");
        var runner = new PassRunner().register(transformation(id, Set.of(), (c, editor) -> {
            var global = nodes(c.project(), VariableDeclaration.class).getFirst();
            var use = nodes(c.function().orElseThrow(), NameExpression.class).getFirst();
            editor.bind(use, global);
            assertThrows(IllegalArgumentException.class, () -> editor.remove(c.function().orElseThrow()));
            var outside = function(c.project(), "second");
            assertThrows(IllegalArgumentException.class, () -> editor.remove(outside));
            assertThrows(IllegalArgumentException.class, () -> editor.restrictTo(outside));
            assertThrows(IllegalArgumentException.class, () -> editor.diagnose(outside, new Diagnostic("x", Diagnostic.Severity.INFO, "Outside")));
        }));
        Project result = runner.run(input, id, function(input, "first"));
        assertEquals("global", ((VariableDeclaration) nodes(function(result, "first"), NameExpression.class).getFirst().target().orElseThrow()).name());
        assertTrue(nodes(function(input, "first"), NameExpression.class).getFirst().target().isEmpty());
    }

    @Test void editsForceStaleDependenciesToRerunBeforeConsumer() {
        Project input = parse("int f() { return 1; }");
        var producer = new PassId("producer"); var consumer = new PassId("consumer");
        var key = new ResultKey<>(producer, "sum", Integer.class);
        var seen = new ArrayList<Integer>();
        var runner = new PassRunner().register(analysis(producer, Set.of(), (c, r) ->
                r.put(function(c.project(), "f"), key, nodes(c.project(), IntegerLiteral.class).getFirst().value())))
                .register(analysis(consumer, Set.of(producer), (c, r) ->
                        seen.add(c.resultOf(function(c.project(), "f"), key).orElseThrow())));
        Project first = runner.run(input, consumer);
        var editor = new ProjectEditor(first);
        editor.replace(nodes(first, IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(8).build());
        Project edited = editor.build();
        assertTrue(edited.resultOf(function(edited, "f"), key).isEmpty());
        Project last = runner.run(edited, consumer);
        assertEquals(List.of(1, 8), seen);
        assertEquals(List.of("producer", "consumer", "producer", "consumer"), passes(last));
    }

    @Test void successfulEmptyProjectPassHasCurrentCompletion() {
        var id = new PassId("empty");
        Project input = Project.create().build();
        Project result = new PassRunner().register(analysis(id, Set.of(), (c, r) -> {})).run(input, id);
        assertEquals(List.of("empty"), passes(result));
        assertEquals(PassRun.Status.CURRENT, result.passStatus(id.value()).orElseThrow());
        assertTrue(input.passRuns().isEmpty());
    }
    @Test void laterTransformDependencyForcesEarlierAnalysisToBePreparedAgain() {
        Project input = parse("int f() { return 1; }");
        var analysis = new PassId("a-analysis"); var transform = new PassId("z-transform"); var consumer = new PassId("consumer");
        var key = new ResultKey<>(analysis, "value", Integer.class);
        var seen = new ArrayList<Integer>();
        var runner = new PassRunner().register(analysis(analysis, Set.of(), (c, r) -> {
            int value = nodes(c.project(), IntegerLiteral.class).getFirst().value();
            seen.add(value); r.put(function(c.project(), "f"), key, value);
        })).register(transformation(transform, Set.of(), (c, editor) -> {
            var literal = nodes(c.project(), IntegerLiteral.class).getFirst();
            editor.replace(literal, IntegerLiteral.create().setValue(2).build());
        })).register(analysis(consumer, Set.of(transform, analysis), (c, r) ->
                assertEquals(2, c.resultOf(function(c.project(), "f"), key).orElseThrow())));
        Project result = runner.run(input, consumer);
        assertEquals(List.of(1, 2), seen);
        assertEquals(List.of("a-analysis", "z-transform", "a-analysis", "consumer"), passes(result));
        assertEquals(1, nodes(input, IntegerLiteral.class).getFirst().value());
    }

    @Test void irreconcilableDependenciesNeverInvokeConsumerOrPublishPartialState() {
        Project input = parse("int f() { return 1; }");
        var analysis = new PassId("a-analysis"); var transform = new PassId("z-transform"); var consumer = new PassId("consumer");
        var callbacks = new AtomicInteger();
        var runner = new PassRunner().register(analysis(analysis, Set.of(), (c, r) -> {}))
                .register(transformation(transform, Set.of(analysis), (c, editor) -> {
                    var literal = nodes(c.project(), IntegerLiteral.class).getFirst();
                    editor.replace(literal, IntegerLiteral.create().setValue(literal.value() + 1).build());
                })).register(analysis(consumer, Set.of(analysis, transform), (c, r) -> callbacks.incrementAndGet()));
        assertTrue(assertThrows(IllegalStateException.class, () -> runner.run(input, consumer))
                .getMessage().contains("cannot remain current"));
        assertEquals(0, callbacks.get());
        assertEquals(1, nodes(input, IntegerLiteral.class).getFirst().value());
        assertTrue(input.passRuns().isEmpty());
    }

    @Test void transformCanInvalidateItsPreparedInputsAndStillRecordsRealExecution() {
        Project input = parse("int f() { return 1; }");
        var id = new PassId("dependent-transform");
        var runner = new PassRunner().register(transformation(id, Set.of(BuiltinPasses.DATA_FLOW), (c, editor) -> {
            assertEquals(PassRun.Status.CURRENT, c.project().passStatus("DataFlow").orElseThrow());
            editor.replace(nodes(c.project(), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        }));
        Project result = runner.run(input, id);
        assertEquals(PassRun.Status.STALE, result.passStatus("DataFlow").orElseThrow());
        assertEquals(PassRun.Status.CURRENT, result.passStatus(id.value()).orElseThrow());
        assertEquals(id.value(), result.passRuns().getLast().passId());
    }

    @Test void retainedDiagnosticsNeverAdoptProducerOwnedFindings() {
        Project input = parse("int f() { return 1; }");
        var id = new PassId("owned-diagnostics");
        Diagnostic diagnostic = new Diagnostic("owned-warning", Diagnostic.Severity.WARNING, "Owned only by analysis");
        var omit = new AtomicBoolean();
        var runner = new PassRunner().register(analysis(id, Set.of(), (c, r) -> {
            if (!omit.get()) r.diagnose(function(c.project(), "f"), diagnostic);
        }));
        Project analyzed = runner.run(input, id);
        Project copied = new ProjectEditor(analyzed).retainDiagnostics().withoutRevalidation().build();
        assertEquals(List.of(diagnostic), function(copied, "f").diagnostics());
        omit.set(true);
        Project cleared = runner.run(copied, id);
        assertTrue(function(cleared, "f").diagnostics().isEmpty());
        assertTrue(cleared.analysisDiagnostics().isEmpty());
        assertEquals(List.of(diagnostic), function(analyzed, "f").diagnostics());
    }

    @Test void sourceEditRevalidationNeverAdoptsStaleCustomFindings() {
        Project input = parse("int f() { return 1; }");
        var id = new PassId("owned-diagnostics");
        Diagnostic diagnostic = new Diagnostic("owned-warning", Diagnostic.Severity.WARNING, "Owned only by analysis");
        Project analyzed = new PassRunner().register(analysis(id, Set.of(), (c, r) ->
                r.diagnose(function(c.project(), "f"), diagnostic))).run(input, id);
        var editor = new ProjectEditor(analyzed);
        editor.replace(nodes(analyzed, IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        Project edited = editor.build();
        assertTrue(function(edited, "f").diagnostics().isEmpty());
        assertEquals(PassRun.Status.STALE, edited.analysisDiagnostics().getFirst().status());
        assertEquals(List.of(diagnostic), function(analyzed, "f").diagnostics());
    }

    @Test void functionCoverageDoesNotReplaceARequiredWholeProjectInvocation() {
        Project input = parse("int first() { return 1; } int second() { return 2; }");
        var prerequisite = new PassId("coverage"); var consumer = new PassId("consumer");
        var key = new ResultKey<>(prerequisite, "count", Integer.class);
        var scopes = new ArrayList<String>();
        var runner = new PassRunner().register(analysis(prerequisite, Set.of(), (c, r) -> {
            scopes.add(c.function().map(CallableDeclaration::name).orElse("project"));
            r.put(c.function().<Node>map(fn -> fn).orElse(c.project()), key, c.nodes().size());
        })).register(analysis(consumer, Set.of(prerequisite), (c, r) ->
                assertTrue(c.resultOf(c.project(), key).isPresent())));
        Project first = runner.run(input, prerequisite, function(input, "first"));
        Project second = runner.run(first, prerequisite, function(first, "second"));
        Project result = runner.run(second, consumer);
        assertEquals(List.of("first", "second", "project"), scopes);
        assertEquals(4, result.passRuns().size());
    }

    @Test void layeredDependencyDagReusesCurrentSharedAncestorsWithoutExponentialTraversal() {
        var runner = new PassRunner();
        var calls = new AtomicInteger();
        Set<PassId> previous = Set.of();
        PassId target = null;
        for (int layer = 0; layer < 24; layer++) {
            var left = new PassId("layer-" + layer + "-left");
            var right = new PassId("layer-" + layer + "-right");
            runner.register(analysis(left, previous, (c, r) -> calls.incrementAndGet()));
            runner.register(analysis(right, previous, (c, r) -> calls.incrementAndGet()));
            previous = Set.of(left, right); target = right;
        }
        PassId requested = target;
        Project result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
                runner.run(Project.create().build(), requested));
        assertEquals(47, calls.get());
        assertEquals(47, result.passRuns().size());
    }

}
