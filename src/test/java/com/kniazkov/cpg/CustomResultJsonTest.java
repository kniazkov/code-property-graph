/* Typed result JSON payloads stay frozen, namespaced and snapshot-local. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.passes.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomResultJsonTest {
    private record IntRange(int min, int max) {}

    private static Project parse(String source) {
        return Project.create().addFile(Parser.parse("custom-results.cpp", source, Language.CPP)).build();
    }

    private static <T extends Node> List<T> nodes(Project project, Class<T> type) {
        var found = new ArrayList<T>();
        project.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) found.add(type.cast(node));
        }});
        return found;
    }

    private static AnalysisPass pass(PassId id, BiConsumer<PassContext, AnalysisResults> action) {
        return new AnalysisPass() {
            @Override public PassId id() { return id; }
            @Override public void analyze(PassContext context, AnalysisResults results) { action.accept(context, results); }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> document(Project project) {
        return (Map<String,Object>) ProjectJson.encode(project).toJavaObject();
    }

    private static List<Map<?,?>> results(Project project) {
        return ((List<?>) document(project).getOrDefault("analysisResults", List.of())).stream().<Map<?,?>>map(value -> (Map<?,?>) value).toList();
    }

    @Test void rangeAndBuiltinValuesExportWithTypeProducerNodeAndFreshness() {
        var producer = new PassId("example.range");
        var rangeKey = new ResultKey<>(producer, "range", IntRange.class);
        var countKey = new ResultKey<>(producer, "count", Integer.class);
        var textKey = new ResultKey<>(producer, "label", String.class);
        var boolKey = new ResultKey<>(producer, "safe", Boolean.class);
        var calls = new AtomicInteger();
        var types = ResultTypes.defaults().with(IntRange.class, value -> {
            calls.incrementAndGet();
            return Map.of("min", value.min(), "max", value.max());
        });
        Project project = new PassRunner(types).register(pass(producer, (context, sink) -> {
            Node node = nodes(context.project(), CallableDeclaration.class).getFirst();
            sink.put(node, rangeKey, new IntRange(1, 10));
            sink.put(node, countKey, 2);
            sink.put(node, textKey, "closed");
            sink.put(node, boolKey, true);
        })).run(parse("int f() { return 1; }"), producer);
        var exported = results(project);
        assertEquals(List.of("count", "label", "range", "safe"), exported.stream().map(value -> value.get("name")).toList());
        Map<?,?> range = exported.get(2);
        assertEquals(producer.value(), range.get("producer"));
        assertEquals(IntRange.class.getName(), range.get("type"));
        assertEquals("current", range.get("status"));
        var rangeValue = (Map<?,?>) range.get("value");
        assertEquals(1, ((Number) rangeValue.get("min")).intValue());
        assertEquals(10, ((Number) rangeValue.get("max")).intValue());
        assertEquals(2, ((Number) exported.get(0).get("value")).intValue());
        assertEquals("closed", exported.get(1).get("value"));
        assertEquals(true, exported.get(3).get("value"));
        assertTrue(range.get("nodeId") instanceof Number);
        CallableDeclaration function = nodes(project, CallableDeclaration.class).getFirst();
        assertEquals(new IntRange(1, 10), project.resultOf(function, rangeKey).orElseThrow());
        assertTrue(project.resultOf(function, new ResultKey<>(producer, "absent", IntRange.class)).isEmpty());
        String first = ProjectJson.encode(project).toText("");
        assertEquals(first, ProjectJson.encode(project).toText(""));
        assertEquals(1, calls.get(), "Export must never rerun a converter");
        assertEquals(List.of(), document(project).get("history"));
    }

    @Test void sameNameAndDiagnosticCodeRemainNamespacedByProducer() {
        var first = new PassId("first");
        var second = new PassId("second");
        var firstKey = new ResultKey<>(first, "count", Integer.class);
        var secondKey = new ResultKey<>(second, "count", Integer.class);
        var runner = new PassRunner().register(pass(first, (context, sink) -> {
            Node node = nodes(context.project(), CallableDeclaration.class).getFirst();
            sink.put(node, firstKey, 1);
            sink.diagnose(node, new Diagnostic("SAME", Diagnostic.Severity.INFO, "First producer"));
        })).register(pass(second, (context, sink) -> {
            Node node = nodes(context.project(), CallableDeclaration.class).getFirst();
            sink.put(node, secondKey, 2);
            sink.diagnose(node, new Diagnostic("SAME", Diagnostic.Severity.WARNING, "Second producer"));
        }));
        Project project = runner.run(runner.run(parse("int f() { return 0; }"), second), first);
        assertEquals(List.of("first", "second"), results(project).stream().map(value -> value.get("producer")).toList());
        CallableDeclaration function = nodes(project, CallableDeclaration.class).getFirst();
        assertEquals(1, project.resultOf(function, firstKey).orElseThrow());
        assertEquals(2, project.resultOf(function, secondKey).orElseThrow());
        var diagnostics = ((List<?>) document(project).get("analysisDiagnostics")).stream().map(Map.class::cast).toList();
        assertEquals(List.of("first", "second"), diagnostics.stream().map(value -> value.get("producer")).toList());
        assertTrue(diagnostics.stream().allMatch(value -> value.get("nodeId") != null && "current".equals(value.get("status"))));
    }

    @Test void mutableEncoderOutputCannotChangePublishedJson() {
        var producer = new PassId("frozen");
        var key = new ResultKey<>(producer, "range", IntRange.class);
        var values = new ArrayList<Object>(List.of(1, 10));
        var mutable = new HashMap<String,Object>();
        mutable.put("values", values);
        var types = ResultTypes.defaults().with(IntRange.class, value -> mutable);
        Project project = new PassRunner(types).register(pass(producer, (context, sink) ->
                sink.put(context.project(), key, new IntRange(1, 10)))).run(parse("int f() { return 1; }"), producer);
        String before = ProjectJson.encode(project).toText("");
        values.set(0, 999);
        mutable.put("extra", "later");
        assertEquals(before, ProjectJson.encode(project).toText(""));
        assertEquals(new IntRange(1, 10), project.resultOf(project, key).orElseThrow());
    }

    @Test void encoderFailureRejectsWholePublicationAndDoesNotReplacePriorValues() {
        var producer = new PassId("atomic");
        var first = new ResultKey<>(producer, "first", Integer.class);
        var second = new ResultKey<>(producer, "second", IntRange.class);
        Project original = new PassRunner().register(pass(producer, (context, sink) -> sink.put(context.project(), first, 4)))
                .run(parse("int f() { return 1; }"), producer);
        String before = ProjectJson.encode(original).toText("");
        var failure = new IllegalStateException("conversion failed");
        var types = ResultTypes.defaults().with(IntRange.class, value -> { throw failure; });
        var runner = new PassRunner(types).register(pass(producer, (context, sink) -> {
            sink.put(context.project(), first, 99);
            sink.put(context.project(), second, new IntRange(1, 10));
            sink.diagnose(context.project(), new Diagnostic("NEVER", Diagnostic.Severity.ERROR, "Unpublished"));
        }));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> runner.run(original, producer)));
        assertEquals(before, ProjectJson.encode(original).toText(""));
        assertEquals(4, original.resultOf(original, first).orElseThrow());
        assertTrue(original.resultOf(original, second).isEmpty());
        assertFalse(document(original).containsKey("analysisDiagnostics"));
        assertEquals(1, original.passRuns().size());
    }

    @Test void editsKeepSurvivingValuesStaleButRemovedNodesNeverBecomeHistory() {
        var producer = new PassId("audit");
        var key = new ResultKey<>(producer, "count", Integer.class);
        var runner = new PassRunner().register(pass(producer, (context, sink) -> {
            for (CallableDeclaration function : nodes(context.project(), CallableDeclaration.class)) {
                sink.put(function, key, 1);
                sink.diagnose(function, new Diagnostic("AUDIT", Diagnostic.Severity.INFO, function.name()));
            }
        }));
        Project original = runner.run(parse("int kept() { return 1; } int removed() { return 987654; }"), producer);
        String before = ProjectJson.encode(original).toText("");
        var editor = new ProjectEditor(original).withoutRevalidation();
        editor.replace(nodes(original, IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        editor.remove(nodes(original, CallableDeclaration.class).getLast());
        Project edited = editor.build();
        var exported = results(edited);
        assertEquals(1, exported.size());
        assertEquals("stale", exported.getFirst().get("status"));
        assertTrue(edited.resultOf(nodes(edited, CallableDeclaration.class).getFirst(), key).isEmpty());
        var definitions = new HashSet<Integer>();
        collectDefinitions(document(edited), definitions);
        assertTrue(definitions.contains(((Number) exported.getFirst().get("nodeId")).intValue()));
        var diagnostics = (List<?>) document(edited).get("analysisDiagnostics");
        assertEquals(1, diagnostics.size());
        assertEquals("stale", ((Map<?,?>) diagnostics.getFirst()).get("status"));
        assertNoRemovedSyntax(document(edited));
        assertEquals(before, ProjectJson.encode(original).toText(""));
    }

    @Test void publicationOrderDoesNotAffectResultJsonOrdering() {
        var producer = new PassId("ordered");
        var key = new ResultKey<>(producer, "count", Integer.class);
        Project input = parse("int a() { return 1; } int b() { return 2; }");
        var forward = new PassRunner().register(pass(producer, (context, sink) ->
                nodes(context.project(), CallableDeclaration.class).forEach(node -> sink.put(node, key, 1))));
        var reverse = new PassRunner().register(pass(producer, (context, sink) ->
                nodes(context.project(), CallableDeclaration.class).reversed().forEach(node -> sink.put(node, key, 1))));
        assertEquals(ProjectJson.encode(forward.run(input, producer)).toText(""),
                ProjectJson.encode(reverse.run(input, producer)).toText(""));
    }

    @Test void dependencyMetadataExportsInStableOrderWithoutChangingLegacyRecords() {
        var producer = new PassId("dependent");
        var first = new PassId("a.dependency");
        var last = new PassId("z.dependency");
        var runner = new PassRunner().register(pass(first, (context, sink) -> {}))
                .register(pass(last, (context, sink) -> {})).register(new AnalysisPass() {
                    @Override public PassId id() { return producer; }
                    @Override public Set<PassId> requires() { return Set.of(last, first); }
                    @Override public void analyze(PassContext context, AnalysisResults sink) {}
                });
        Project project = runner.run(parse("int f() { return 1; }").recordPass("legacy"), producer);
        var runs = ((List<?>) document(project).get("passRuns")).stream().map(Map.class::cast).toList();
        assertFalse(runs.getFirst().containsKey("custom"));
        assertFalse(runs.getFirst().containsKey("requires"));
        assertEquals(true, runs.getLast().get("custom"));
        assertEquals(List.of("a.dependency", "z.dependency"), runs.getLast().get("requires"));
        assertFalse(runs.get(1).containsKey("requires"), "Empty prerequisite sets remain optional");
    }

    @Test void actualJsonExportPreservesArbitraryPrecisionNumbers() throws Exception {
        var producer = new PassId("precision");
        var integerKey = new ResultKey<>(producer, "integer", BigInteger.class);
        var decimalKey = new ResultKey<>(producer, "decimal", BigDecimal.class);
        BigInteger integer = new BigInteger("12345678901234567890123456789012345678901234567890");
        BigDecimal decimal = new BigDecimal("123456789012345678901234567890.0000000000000000000000000000012345");
        Project project = new PassRunner().register(pass(producer, (context, sink) -> {
            sink.put(context.project(), integerKey, integer);
            sink.put(context.project(), decimalKey, decimal);
        })).run(Project.create().build(), producer);
        String exported = ProjectJson.encode(project).toText("");
        var parsed = (Map<?,?>) Json.parse(exported).toJavaObject();
        var values = ((List<?>) parsed.get("analysisResults")).stream().map(Map.class::cast).toList();
        assertEquals(decimal, values.get(0).get("value"));
        assertEquals(integer, values.get(1).get("value"));
        assertTrue(exported.contains(integer.toString()));
        assertTrue(exported.contains(decimal.toPlainString()));
    }

    private static void collectDefinitions(Object value, Set<Integer> definitions) {
        if (value instanceof List<?> list) list.forEach(item -> collectDefinitions(item, definitions));
        if (value instanceof Map<?,?> map) {
            if (map.get("id") instanceof Number id && map.get("kind") instanceof String) definitions.add(id.intValue());
            map.values().forEach(item -> collectDefinitions(item, definitions));
        }
    }

    private static void assertNoRemovedSyntax(Object value) {
        if (value instanceof List<?> list) list.forEach(CustomResultJsonTest::assertNoRemovedSyntax);
        if (value instanceof Map<?,?> map) {
            if (map.containsKey("id") && map.get("kind") instanceof String) {
                assertNotEquals("removed", map.get("name"), "Removed callable must not be imported");
                if (map.get("value") instanceof Number number)
                    assertNotEquals(987654, number.intValue(), "Removed body must not be imported");
            }
            map.values().forEach(CustomResultJsonTest::assertNoRemovedSyntax);
        }
    }
}
