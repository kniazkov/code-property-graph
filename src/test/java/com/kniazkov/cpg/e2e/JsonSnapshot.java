/* Code Property Graph: structural assertions for serialized projects. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.json.Json;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares the exported document as JSON. Object property order and formatting are immaterial.
 * A partial object requires its stated fields on the same object. Partial arrays match an
 * ordered subsequence using separate elements, so extra children or edges can be tolerated.
 * Exact comparisons require all fields and preserve array order and length.
 */
final class JsonSnapshot {
    private final Object document;
    private final List<Map<?, ?>> nodes = new ArrayList<>();

    private JsonSnapshot(Object document) {
        this.document = document;
        index(document);
        assertFalse(nodes.isEmpty(), "Snapshot contains no nodes");
        Set<Object> ids = new HashSet<>();
        for (var node : nodes) assertTrue(ids.add(node.get("id")), () -> "Duplicate ID: " + node.get("id"));
        for (var node : nodes) {
            if (node.get("edges") instanceof List<?> edges)
                for (Object edge : edges) assertTrue(edge instanceof Map<?,?> e && ids.contains(e.get("to")),
                        () -> "Dangling edge on node " + node.get("id") + ": " + edge);
        }
    }

    static JsonSnapshot read(Path file) throws Exception {
        return new JsonSnapshot(Json.parse(file.toFile()).toJavaObject());
    }

    /** Substitutes quoted fixture markers as JSON strings, including Windows paths. */
    void exactly(String resource, Map<String,String> substitutions) throws Exception {
        String expected = CliRun.resource(resource);
        for (var entry : substitutions.entrySet()) {
            String marker = Json.serialize(entry.getKey());
            assertTrue(expected.contains(marker), "Unknown golden-file marker: " + marker);
            expected = expected.replace(marker, Json.serialize(entry.getValue()));
        }
        compare("$", Json.parse(expected).toJavaObject(), document);
    }

    void containsNode(String pattern) throws Exception {
        assertFalse(matching(pattern).isEmpty(), () -> "Expected a node matching " + pattern + "\n" + document);
    }

    void excludesNode(String pattern) throws Exception {
        var matches = matching(pattern);
        assertTrue(matches.isEmpty(), () -> "Forbidden node matching " + pattern + ": " + matches.getFirst());
    }

    /** Follows an edge by ID, verifying its actual target instead of relying on a fixed node number. */
    void expectEdge(String from, String kind, String to) throws Exception {
        var sources = matching(from);
        assertEquals(1, sources.size(), "Source must select one node: " + from);
        var targets = matching(to);
        assertEquals(1, targets.size(), "Target must select one node: " + to);
        Object expected = List.of(Map.of("kind", kind, "to", targets.getFirst().get("id")));
        assertTrue(matches(expected, sources.getFirst().get("edges")),
                () -> "Missing edge " + from + " --" + kind + "--> " + to);
    }

    private void index(Object value) {
        if (value instanceof Map<?,?> map) {
            if (map.containsKey("id") && map.containsKey("kind")) nodes.add(map);
            map.values().forEach(this::index);
        } else if (value instanceof List<?> list) list.forEach(this::index);
    }

    private List<Map<?, ?>> matching(String json) throws Exception {
        Object pattern = Json.parse(json).toJavaObject();
        assertInstanceOf(Map.class, pattern, "Node pattern must be an object");
        return nodes.stream().filter(node -> matches(pattern, node)).toList();
    }

    private static boolean matches(Object pattern, Object actual) {
        if (pattern instanceof Map<?,?> fields) {
            if (!(actual instanceof Map<?,?> object)) return false;
            for (var e : fields.entrySet())
                if (!object.containsKey(e.getKey()) || !matches(e.getValue(), object.get(e.getKey()))) return false;
            return true;
        }
        if (pattern instanceof List<?> requested) {
            if (!(actual instanceof List<?> items)) return false;
            int next = 0;
            for (Object item : items)
                if (next < requested.size() && matches(requested.get(next), item)) next++;
            return next == requested.size();
        }
        return Objects.equals(pattern, actual);
    }

    private static void compare(String path, Object expected, Object actual) {
        if (expected instanceof Map<?,?> fields && actual instanceof Map<?,?> object) {
            assertEquals(fields.keySet(), object.keySet(), path + " fields");
            fields.forEach((key, value) -> compare(path + "." + key, value, object.get(key)));
        } else if (expected instanceof List<?> list && actual instanceof List<?> values) {
            assertEquals(list.size(), values.size(), path + " length");
            for (int i = 0; i < list.size(); i++) compare(path + "[" + i + "]", list.get(i), values.get(i));
        } else assertEquals(expected, actual, path);
    }
}
