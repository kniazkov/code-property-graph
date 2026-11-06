/* Code Property Graph: ordered execution coverage in standalone JSON. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PassRunJsonTest {
    private static Project parse(String source) {
        return Project.create().addFile(Parser.parse("pass-runs.cpp", source, Language.CPP)).build();
    }

    private static <T extends Node> List<T> nodes(Project project, Class<T> type) {
        var result = new ArrayList<T>();
        project.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }});
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> document(Project project) throws Exception {
        return (Map<String,Object>) Json.parse(ProjectJson.encode(project).toText("")).toJavaObject();
    }

    private static List<?> runs(Map<String,Object> document) {
        return (List<?>) document.get("passRuns");
    }

    private static List<?> results(Map<?,?> run) {
        return (List<?>) run.get("results");
    }

    private static Map<?,?> result(Map<?,?> run, String name) {
        return results(run).stream().map(Map.class::cast)
                .filter(value -> name.equals(value.get("name"))).findFirst().orElseThrow();
    }

    private static Map<?,?> run(Map<String,Object> document, String passId) {
        return runs(document).reversed().stream().map(Map.class::cast)
                .filter(value -> passId.equals(value.get("passId"))).findFirst().orElseThrow();
    }

    private static Map<Integer,Map<?,?>> definitions(Object value) {
        var result = new LinkedHashMap<Integer,Map<?,?>>();
        collect(value, result);
        return result;
    }

    private static void collect(Object value, Map<Integer,Map<?,?>> definitions) {
        if (value instanceof List<?> list) { list.forEach(item -> collect(item, definitions)); return; }
        if (!(value instanceof Map<?,?> object)) return;
        if (object.get("id") instanceof Number id && object.get("kind") instanceof String)
            assertNull(definitions.put(id.intValue(), object), "Duplicate graph ID " + id);
        object.forEach((key,item) -> {
            // Token and diagnostic catalogues have local IDs, not graph IDs.
            if (!Set.of("preprocessing", "diagnosticGroups").contains(key)) collect(item, definitions);
        });
    }

    private static void assertReferences(Object value, Set<Integer> definitions) {
        if (value instanceof List<?> list) { list.forEach(item -> assertReferences(item, definitions)); return; }
        if (!(value instanceof Map<?,?> object)) return;
        object.forEach((key,item) -> {
            if (Set.of("preprocessing", "diagnosticGroups").contains(key)) return;
            String name = key.toString();
            if ((name.endsWith("Id") && !Set.of("passId", "variantId").contains(name) || name.equals("to"))
                    && item instanceof Number id)
                assertTrue(definitions.contains(id.intValue()), "Dangling " + name + ": " + id);
            if (name.endsWith("Ids") && !name.equals("expansionIds") && item instanceof List<?> list)
                list.forEach(id -> {
                    if (id instanceof Number number)
                        assertTrue(definitions.contains(number.intValue()), "Dangling " + name + ": " + number);
                });
            assertReferences(item, definitions);
        });
    }

    @Test void parsedSnapshotAndEmptyNoOpRunAreDifferent() throws Exception {
        Project parsed = Project.create().build();
        Map<String,Object> original = document(parsed);
        assertEquals(List.of(), runs(original));
        assertEquals(List.of(), original.get("history"));

        Map<String,Object> analyzed = document(ConsoleNormalizer.green(parsed));
        assertEquals(1, ((Number) analyzed.get("schemaVersion")).intValue());
        assertEquals("parsed", analyzed.get("stage"), "A direct pass is not a pipeline stage");
        assertEquals(1, runs(analyzed).size());
        Map<?,?> run = run(analyzed, "ConsoleNormalizer");
        assertEquals("project", run.get("scope"));
        assertEquals("current", run.get("status"));
        assertEquals(List.of(), results(run));
        assertEquals(List.of(), runs(document(parsed)), "The original snapshot is unchanged");
        assertReferences(analyzed, definitions(analyzed).keySet());
    }

    @Test void analysisWithoutFindingsStillRecordsCoverage() throws Exception {
        Project analyzed = UnreadWrites.analyze(NameResolver.resolve(parse("int f() { return 0; }")));
        assertTrue(analyzed.semanticDiagnostics().isEmpty());
        Map<String,Object> json = document(analyzed);
        Map<?,?> run = run(json, "UnreadWrites");
        assertEquals("current", run.get("status"));
        assertEquals(1, results(run).size());
        Map<?,?> result = result(run, "f");
        assertEquals("current", result.get("status"));
        assertNotNull(result.get("range"));
        Map<Integer,Map<?,?>> definitions = definitions(json);
        Map<?,?> function = definitions.get(((Number) result.get("functionId")).intValue());
        assertEquals("FunctionDeclaration", function.get("kind"));
        assertEquals("f", function.get("name"));
        assertReferences(json, definitions.keySet());
    }

    @Test void repeatedDirectInvocationsHaveOrderedDistinctExecutions() throws Exception {
        Project once = ControlFlow.analyze(parse("int f() { return 0; }"));
        Project twice = ControlFlow.analyze(once);
        var runs = runs(document(twice));
        assertEquals(List.of("ControlFlow", "ControlFlow"), runs.stream()
                .map(Map.class::cast).map(run -> run.get("passId"))
                .filter("ControlFlow"::equals).toList());
        assertEquals(runs(document(once)).size() + 1, runs.size(), "Current prerequisites are reused");
        long previous = -1;
        for (Object item : runs) {
            var run = (Map<?,?>) item;
            long sequence = ((Number) run.get("sequence")).longValue();
            assertTrue(sequence > previous, "Execution sequence must increase");
            previous = sequence;
        }
        assertEquals(1, runs(document(once)).stream().map(Map.class::cast)
                .filter(run -> "ControlFlow".equals(run.get("passId"))).count());
    }

    @Test void functionScopeUsesExactOverloadAndProjectScopeListsBoth() throws Exception {
        Project parsed = parse("int f(int value) { return value; } int f(char value) { return value; }");
        var functions = nodes(parsed, CallableDeclaration.class);
        Project recorded = parsed.recordPass("WholeProject").recordPass("SecondOverload", functions.getLast());
        Map<String,Object> json = document(recorded);
        Map<?,?> projectRun = run(json, "WholeProject");
        Map<?,?> functionRun = run(json, "SecondOverload");
        assertEquals("project", projectRun.get("scope"));
        assertEquals("function", functionRun.get("scope"));
        assertEquals(2, results(projectRun).size());
        assertEquals(1, results(functionRun).size());
        Map<?,?> selected = (Map<?,?>) results(functionRun).getFirst();
        var projectResults = results(projectRun).stream().map(Map.class::cast).toList();
        assertEquals(projectResults.getLast().get("functionId"), selected.get("functionId"));
        assertNotEquals(projectResults.getFirst().get("functionId"), selected.get("functionId"));
        Map<Integer,Map<?,?>> definitions = definitions(json);
        assertEquals(selected.get("range"), definitions.get(((Number) selected.get("functionId")).intValue()).get("range"));
        assertReferences(json, definitions.keySet());
    }

    @Test void editedCoverageBecomesStaleWithoutChangingTheOriginalSnapshot() throws Exception {
        Project original = parse("int changed() { return 7; } int unchanged() { return 8; }").recordPass("Audit");
        String before = ProjectJson.encode(original).toText("");
        var editor = new ProjectEditor(original).withoutRevalidation();
        editor.replace(nodes(original, IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(9).build());
        Project edited = editor.build();
        Map<String,Object> json = document(edited);
        Map<?,?> editedRun = run(json, "Audit");
        assertEquals("stale", editedRun.get("status"));
        assertEquals("stale", result(editedRun, "changed").get("status"));
        assertEquals("current", result(editedRun, "unchanged").get("status"));
        assertEquals("current", run(document(original), "Audit").get("status"));
        assertEquals(before, ProjectJson.encode(original).toText(""));
        assertNotNull(result(editedRun, "changed").get("functionId"));
        assertReferences(json, definitions(json).keySet());
    }

    @Test void removedCallableRetainsOnlyItsHistoricalCoverageSummary() throws Exception {
        Project original = parse("int kept() { return 0; } int removed() { return 999; }").recordPass("Audit");
        var editor = new ProjectEditor(original).withoutRevalidation();
        editor.remove(nodes(original, CallableDeclaration.class).getLast());
        Project edited = editor.build();
        Map<String,Object> json = document(edited);
        Map<?,?> removed = result(run(json, "Audit"), "removed");
        assertEquals("stale", removed.get("status"));
        assertNotNull(removed.get("range"));
        assertFalse(removed.containsKey("functionId"));
        Map<Integer,Map<?,?>> definitions = definitions(json);
        assertFalse(definitions.values().stream().anyMatch(value -> "removed".equals(value.get("name"))));
        assertFalse(definitions.values().stream().anyMatch(value -> value.get("value") instanceof Number number
                && number.intValue() == 999), "Pass history must not reimport a removed body");
        assertReferences(json, definitions.keySet());
        assertEquals("current", result(run(document(original), "Audit"), "removed").get("status"));
    }

    @Test void passHistoryAndTransformationHistoryAreIndependent() throws Exception {
        Project folded = ConstantFolder.fold(parse("int f() { return 2 + 3; }"));
        Map<String,Object> json = document(folded);
        assertEquals("ConstantFolder", run(json, "ConstantFolder").get("passId"));
        var history = (List<?>) json.get("history");
        assertTrue(history.stream().map(Map.class::cast).anyMatch(value -> "AddExpression".equals(value.get("kind"))));
        assertTrue(history.stream().map(Map.class::cast).allMatch(value -> value.containsKey("id")));
        assertTrue(runs(json).stream().map(Map.class::cast).noneMatch(value -> value.containsKey("id")));
        assertReferences(json, definitions(json).keySet());
    }

    @Test void reencodingAndRepeatingAnIdenticalPipelineAreDeterministic() throws Exception {
        Project first = ControlFlow.analyze(ControlFlow.analyze(parse("int f() { return 0; }")));
        String expected = ProjectJson.encode(first).toText("");
        assertEquals(expected, ProjectJson.encode(first).toText(""));
        for (int i = 0; i < 3; i++) {
            Project repeated = ControlFlow.analyze(ControlFlow.analyze(parse("int f() { return 0; }")));
            assertEquals(expected, ProjectJson.encode(repeated).toText(""));
            Map<String,Object> json = document(repeated);
            assertReferences(json, definitions(json).keySet());
        }
    }
}
