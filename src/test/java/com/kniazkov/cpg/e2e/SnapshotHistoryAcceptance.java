/* Standalone transformation history and immutable stage snapshots. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.analysis.ConsoleNormalizer;
import com.kniazkov.cpg.analysis.ConstantFolder;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class SnapshotHistoryAcceptance {
    private SnapshotHistoryAcceptance() { }
    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var outputs = new ArrayList<Path>(); outputs.add(cli.input);
        for (Cpg.Stage stage : Cpg.Stage.values()) {
            var options = new Cpg.Options(Language.CPP, stage, "c++03");
            var project = Cpg.read(cli.input, options);
            var encoded = (Map<?, ?>) ProjectJson.encode(project).toJavaObject();
            assertEquals(1, ((Number) encoded.get("schemaVersion")).intValue());
            assertEquals(stage.name().toLowerCase(Locale.ROOT), encoded.get("stage"));
            assertEquals(ProjectJson.encode(project), ProjectJson.encode(Cpg.read(cli.input, options)));
            var run = cli.run("cpp", "--stage", stage.name().toLowerCase(Locale.ROOT), "--cpp-standard", "c++03").expectStatus(0);
            var standalone = (Map<?, ?>) Json.parse(run.work().resolve("snapshot.json").toFile()).toJavaObject();
            assertEquals(encoded.get("stage"), standalone.get("stage"));
            assertEquals(1, ((Number) standalone.get("schemaVersion")).intValue());
            if (stage == Cpg.Stage.FOLDED) verifyStandalone(standalone);
            outputs.add(run.work().resolve("generated"));
        }
        var resolved = Cpg.read(cli.input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        var oldJson = ProjectJson.encode(resolved);
        var originalAdd = nodes(resolved, AddExpression.class).getFirst();
        var green = ConsoleNormalizer.green(resolved);
        var greenJson = ProjectJson.encode(green);
        var print = nodes(green, PrintStatement.class).getFirst();
        assertTrue(print.transformationProvenance().stream().anyMatch(p -> p.role().equals("stream")));
        assertTrue(print.transformationProvenance().stream().anyMatch(p -> p.role().equals("value")));
        var newline = print.transformationProvenance().stream().filter(p -> p.role().equals("newline")).findFirst().orElseThrow();
        assertTrue(print.transformationProvenance().stream().anyMatch(p -> p.role().equals("flush") && p.source() == newline.source()));
        var reverse = green.transformationResults(newline.source());
        assertTrue(reverse.stream().anyMatch(r -> r.result() == print && r.role().equals("newline")));
        assertTrue(reverse.stream().anyMatch(r -> r.result() == print && r.role().equals("flush")));
        var greenAdd = nodes(green, AddExpression.class).getFirst();
        var folded = ConstantFolder.fold(green);
        var five = nodes(folded, IntegerLiteral.class).stream().filter(n -> n.value() == 5).findFirst().orElseThrow();
        assertTrue(five.transformationProvenance().stream().anyMatch(p -> p.passId().equals("ConstantFolder") && p.source() == greenAdd));
        assertTrue(reaches(five, originalAdd), "Folded value must reach the original expression, not only copied coordinates");
        assertEquals(oldJson, ProjectJson.encode(resolved));
        assertEquals(greenJson, ProjectJson.encode(green));
        assertEquals(2, originalAdd.left() instanceof IntegerLiteral literal ? literal.value() : -1);
        var brokenCli = new CliSandbox(Files.createDirectory(work.resolve("broken"))).source("main.cpp", "int x = ;\n");
        for (Cpg.Stage stage : Cpg.Stage.values()) {
            var broken = Cpg.readRecovering(brokenCli.input, new Cpg.Options(Language.CPP, stage, "c++03"));
            var json = (Map<?, ?>) ProjectJson.encode(broken).toJavaObject();
            assertTrue(broken.hasErrors()); assertEquals(1, ((Number) json.get("schemaVersion")).intValue());
            assertEquals(stage.name().toLowerCase(Locale.ROOT), json.get("stage"));
        }
        return List.copyOf(outputs);
    }
    private static boolean reaches(Node from, Node target) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        var pending = new ArrayDeque<Node>(); pending.add(from);
        while (!pending.isEmpty()) { Node node = pending.removeFirst(); if (node == target) return true;
            if (seen.add(node)) node.transformationProvenance().forEach(p -> pending.add(p.source())); }
        return false;
    }
    private static void verifyStandalone(Map<?, ?> root) {
        var ids = new LinkedHashMap<Integer, Map<?, ?>>(); collect(root, ids);
        var histories = (List<?>) root.get("history"); assertNotNull(histories);
        assertTrue(histories.stream().noneMatch(v -> v instanceof Map<?, ?> m && "FunctionDeclaration".equals(m.get("kind")) && "unrelated".equals(m.get("name"))));
        var five = ids.values().stream().filter(n -> "IntegerLiteral".equals(n.get("kind")) && n.get("value") instanceof Number v && v.intValue() == 5)
                .filter(n -> n.get("transformationProvenance") instanceof List<?>).findFirst().orElseThrow();
        var edge = ((List<?>) five.get("transformationProvenance")).stream().map(v -> (Map<?, ?>) v)
                .filter(e -> "ConstantFolder".equals(e.get("passId"))).findFirst().orElseThrow();
        var add = ids.get(((Number) edge.get("sourceId")).intValue());
        assertNotNull(add); assertEquals("AddExpression", add.get("kind"));
        assertNotNull(add.get("range"));
        var priorLink = ((List<?>) add.get("transformationProvenance")).stream().map(v -> (Map<?, ?>) v)
                .filter(e -> "ConsoleNormalizer".equals(e.get("passId"))).findFirst().orElseThrow();
        var sourceAdd = ids.get(((Number) priorLink.get("sourceId")).intValue());
        assertEquals("AddExpression", sourceAdd.get("kind"));
        var sourceRange = (Map<?, ?>) sourceAdd.get("range");
        assertEquals(4, ((Number) ((Map<?, ?>) sourceRange.get("start")).get("line")).intValue());
        var left = ids.get(((Number) ((Map<?, ?>) sourceAdd.get("left")).get("nodeId")).intValue());
        var right = ids.get(((Number) ((Map<?, ?>) sourceAdd.get("right")).get("nodeId")).intValue());
        assertEquals(2, ((Number) left.get("value")).intValue());
        assertEquals(3, ((Number) right.get("value")).intValue());
        for (var node : ids.values()) {
            Object provenance = node.get("transformationProvenance");
            if (provenance instanceof List<?> links) for (Object value : links) {
                var link = (Map<?, ?>) value;
                assertTrue(ids.containsKey(((Number) link.get("sourceId")).intValue()));
                assertFalse(((String) link.get("passId")).isBlank()); assertFalse(((String) link.get("role")).isBlank());
            }
        }
        checkReferences(root, ids);
    }
    private static void collect(Object value, Map<Integer, Map<?, ?>> ids) {
        if (value instanceof List<?> list) { list.forEach(v -> collect(v, ids)); return; }
        if (!(value instanceof Map<?, ?> map)) return;
        if (map.get("id") instanceof Number id && map.get("kind") instanceof String)
            assertNull(ids.put(id.intValue(), map), "Each node ID must have one definition");
        map.values().forEach(v -> collect(v, ids));
    }
    private static void checkReferences(Object value, Map<Integer, Map<?, ?>> ids) {
        if (value instanceof List<?> list) { list.forEach(v -> checkReferences(v, ids)); return; }
        if (!(value instanceof Map<?, ?> map)) return;
        for (var entry : map.entrySet()) {
            String key = entry.getKey().toString(); Object item = entry.getValue();
            if (item instanceof Number number && (key.equals("nodeId") || key.equals("sourceId") || key.equals("targetId") || key.equals("callSiteId") || key.equals("to")))
                assertTrue(ids.containsKey(number.intValue()), "Unresolved " + key + "=" + number);
            checkReferences(item, ids);
        }
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } }); return result;
    }
}
