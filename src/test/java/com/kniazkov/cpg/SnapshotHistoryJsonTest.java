/* Code Property Graph: standalone, deterministic transformation history. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotHistoryJsonTest {
    @TempDir Path directory;

    @SuppressWarnings("unchecked")
    private static Map<String,Object> document(Project project) throws Exception {
        // Round-trip through the ordinary JSON parser: no graph object or source is
        // needed by the assertions that follow.
        return (Map<String,Object>) Json.parse(ProjectJson.encode(project).toText("")).toJavaObject();
    }
    private static Map<Integer,Map<?,?>> definitions(Object value) {
        var result = new LinkedHashMap<Integer,Map<?,?>>();
        collect(value, result);
        return result;
    }
    private static void collect(Object value, Map<Integer,Map<?,?>> result) {
        if (value instanceof List<?> list) { list.forEach(item -> collect(item, result)); return; }
        if (!(value instanceof Map<?,?> object)) return;
        if (object.get("id") instanceof Number id && object.get("kind") instanceof String) {
            assertNull(result.put(id.intValue(), object), "Duplicate graph ID " + id);
        }
        // Preprocessing records have their own local IDs, not graph node IDs.
        object.forEach((key,item) -> { if (!Set.of("preprocessing", "diagnosticGroups").contains(key)) collect(item, result); });
    }
    private static void assertReferences(Object value, Set<Integer> ids) {
        if (value instanceof List<?> list) { list.forEach(item -> assertReferences(item, ids)); return; }
        if (!(value instanceof Map<?,?> object)) return;
        object.forEach((key,item) -> {
            if (Set.of("preprocessing", "diagnosticGroups").contains(key)) return;
            String name = key.toString();
            if ((name.endsWith("Id") && !Set.of("passId", "variantId").contains(name) || name.equals("to")) && item instanceof Number number)
                assertTrue(ids.contains(number.intValue()), "Dangling " + name + ": " + number);
            if (name.endsWith("Ids") && !name.equals("expansionIds") && item instanceof List<?> list)
                list.forEach(id -> { if (id instanceof Number number) assertTrue(ids.contains(number.intValue()), "Dangling " + name); });
            assertReferences(item, ids);
        });
    }
    @Test void foldedLiteralCanBeTracedUsingOnlyTheJson() throws Exception {
        Path source = directory.resolve("Demo.java");
        Files.writeString(source, "class Demo { public static void main(String[] args) { System.out.println(2 + 3); } }");
        var options = new Cpg.Options(Language.JAVA, Cpg.Stage.FOLDED);
        Project project = Cpg.read(directory, options);
        String first = ProjectJson.encode(project).toText("");
        assertEquals(first, ProjectJson.encode(project).toText(""));
        assertEquals(first, ProjectJson.encode(Cpg.read(directory, options)).toText(""));
        var json = document(project);
        Files.delete(source);
        assertEquals(1, ((Number)json.get("schemaVersion")).intValue());
        assertEquals("folded", json.get("stage"));
        var all = definitions(json);
        assertReferences(json, all.keySet());
        Map<?,?> literal = all.values().stream().filter(n -> "IntegerLiteral".equals(n.get("kind"))
                && n.get("value") instanceof Number v && v.intValue() == 5).findFirst().orElseThrow();
        var pending = new ArrayDeque<Map<?,?>>(); pending.add(literal);
        var seen = new HashSet<Map<?,?>>();
        Map<?,?> addition = null;
        boolean foldPass = false;
        while (!pending.isEmpty()) {
            var n = pending.removeFirst();
            if (!seen.add(n)) continue;
            if ("AddExpression".equals(n.get("kind"))) addition = n;
            if (n.get("transformationProvenance") instanceof List<?> links) for (Object item : links) {
                var link = (Map<?,?>)item;
                foldPass |= link.get("passId").equals("ConstantFolder");
                pending.add(all.get(((Number)link.get("sourceId")).intValue()));
            }
        }
        assertTrue(foldPass);
        assertNotNull(addition);
        assertNotNull(addition.get("range"));
        for (String side : List.of("left", "right")) {
            var reference = (Map<?,?>)addition.get(side);
            assertNotNull(all.get(((Number)reference.get("nodeId")).intValue()));
        }
    }
    @Test void removedUnrelatedFunctionDoesNotLeakThroughOldProjectOrSourceFile() throws Exception {
        Files.writeString(directory.resolve("main.cpp"), "int kept() { return 2 + 3; } int unrelated() { return 999; }");
        Project before = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.PARSED));
        // Only the expression is a provenance input, never its whole owning file.
        var nodes = new ArrayList<Node>(); before.walk(new NodeVisitor() { @Override public void visit(Node n) { nodes.add(n); } });
        Node addition = nodes.stream().filter(AddExpression.class::isInstance).findFirst().orElseThrow();
        var value = IntegerLiteral.create().setValue(5);
        value.addTransformationProvenance("constant-fold", "source-construct", addition);
        var file = SourceFile.create().setPath(directory.resolve("result.java").toString()).addItem(VariableDeclaration.create().setName("answer").setInitializer(value));
        // A detached expression retains its token unit even when neither the old
        // Project nor the old SourceFile is otherwise reachable.
        var detachedJson = document(Project.create().addFile(file).build());
        var detachedNodes = definitions(detachedJson);
        assertReferences(detachedJson, detachedNodes.keySet());
        assertTrue(detachedJson.containsKey("sourceContexts"));
        assertEquals(1, detachedNodes.values().stream().filter(n -> "SourceFile".equals(n.get("kind"))).count());
        assertFalse(detachedNodes.values().stream().anyMatch(n -> "unrelated".equals(n.get("name"))));
        file.addTransformationProvenance("test-selection", "source-construct", before.files().getFirst());
        var selectedBuilder = Project.create().addFile(file);
        selectedBuilder.addTransformationProvenance("test-selection", "source-construct", before);
        Project selected = selectedBuilder.build().withSnapshotStage(SnapshotStage.FOLDED);
        var json = document(selected);
        var all = definitions(json);
        assertReferences(json, all.keySet());
        assertFalse(all.values().stream().anyMatch(n -> "unrelated".equals(n.get("name"))));
        assertFalse(all.values().stream().anyMatch(n -> n.get("value") instanceof Number number && number.intValue() == 999));
        assertTrue(json.containsKey("sourceContexts"));
    }
    @Test void referenceDependencyClosureSupportsRecursiveHistoricalCalls() throws Exception {
        Files.writeString(directory.resolve("main.cpp"), "int recursive(int n) { return recursive(n); } int unrelated() { return 999; }");
        var before = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        var calls = new ArrayList<CallExpression>();
        before.walk(new NodeVisitor() { @Override public void visit(CallExpression n) { calls.add(n); } });
        var answer = IntegerLiteral.create().setValue(0);
        answer.addTransformationProvenance("test-recursion", "source-construct", calls.getFirst());
        var selected = Project.create().addFile(SourceFile.create().setPath("result.cpp")
                .addItem(VariableDeclaration.create().setName("answer").setInitializer(answer))).build();
        var json = document(selected);
        var all = definitions(json);
        assertReferences(json, all.keySet());
        assertTrue(all.values().stream().anyMatch(n -> "recursive".equals(n.get("name"))));
        assertFalse(all.values().stream().anyMatch(n -> "unrelated".equals(n.get("name"))));
        assertEquals(ProjectJson.encode(selected), ProjectJson.encode(selected));
    }

    @Test void explicitStageSurvivesEmptyAndErrorSnapshots() throws Exception {
        Files.writeString(directory.resolve("broken.cpp"), "int broken() { int missing = ; return 4; }");
        for (SnapshotStage stage : SnapshotStage.values()) {
            var json = document(Project.create().build().withSnapshotStage(stage));
            assertEquals(stage.name().toLowerCase(Locale.ROOT), json.get("stage"));
            assertEquals(1, ((Number)json.get("schemaVersion")).intValue());
            Project recovered = Cpg.readRecovering(directory, new Cpg.Options(Language.CPP, Cpg.Stage.valueOf(stage.name())));
            var errorJson = document(recovered);
            assertEquals(true, errorJson.get("hasErrors"));
            assertEquals(stage.name().toLowerCase(Locale.ROOT), errorJson.get("stage"));
            assertReferences(errorJson, definitions(errorJson).keySet());
        }
    }
}
