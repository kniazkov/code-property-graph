package com.kniazkov.cpg.utils;

import com.kniazkov.json.Json;
import com.kniazkov.json.JsonObject;
import com.kniazkov.json.JsonObjectBuilder;
import com.kniazkov.cpg.graph.Graph;
import com.kniazkov.cpg.graph.Relations;
import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Position;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exports a syntax tree or a code property graph as JSON.
 *
 * <p>The exporter assigns one integer identifier to every node reachable from
 * the root through {@link Node#children()} and through the graph relations. The
 * root always receives identifier {@code 0}; identifiers increase monotonically
 * in pre-order traversal order.</p>
 *
 * <p>Each node object also contains its {@link Node#tags()} array. Tags are
 * unique within a node; list nodes have an empty tag array. Graph relations are
 * written to a separate {@code relations} object, one array per relation type.</p>
 */
public final class JsonExporter {

    private static final String FORMAT = "cpp03-cpg";
    private static final int VERSION = 2;

    private JsonExporter() { }

    /**
     * Exports a syntax tree or subtree as a JSON object without relations.
     *
     * @param root the root of the tree or subtree
     * @return the JSON code property graph
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static JsonObject toJson(Node root) {
        Objects.requireNonNull(root, "root");
        return toJson(Graph.builder(root).build());
    }

    /**
     * Exports a code property graph as a JSON object.
     *
     * @param graph the graph to export
     * @return the JSON code property graph
     * @throws NullPointerException if {@code graph} is {@code null}
     */
    public static JsonObject toJson(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        Map<String, Relations> relations = graph.relations();
        List<Node> nodes = collect(graph.root(), relations);
        Map<Node, Integer> ids = new IdentityHashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            ids.put(nodes.get(index), index);
        }

        JsonObjectBuilder document = Json.object()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("root", 0);

        document.array("nodes", array -> {
            for (Node node : nodes) {
                array.object(object -> writeNode(object, node, ids));
            }
        });

        document.object("relations", object -> {
            for (Map.Entry<String, Relations> entry : relations.entrySet()) {
                object.array(entry.getKey(), array -> {
                    for (Node source : nodes) {
                        for (Node target : entry.getValue().targets(source)) {
                            array.object(edge -> {
                                edge.put("from", ids.get(source));
                                edge.put("to", ids.get(target));
                            });
                        }
                    }
                });
            }
        });

        return document.build();
    }

    /**
     * Exports a syntax tree or subtree as an indented JSON string.
     *
     * @param root the root of the tree or subtree
     * @return the JSON code property graph as text
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static String toJsonString(Node root) {
        Objects.requireNonNull(root, "root");
        return Json.serialize(toJson(root), "  ");
    }

    /**
     * Exports a code property graph as an indented JSON string.
     *
     * @param graph the graph to export
     * @return the JSON code property graph as text
     * @throws NullPointerException if {@code graph} is {@code null}
     */
    public static String toJsonString(Graph graph) {
        Objects.requireNonNull(graph, "graph");
        return Json.serialize(toJson(graph), "  ");
    }

    private static List<Node> collect(Node root, Map<String, Relations> relations) {
        List<Node> nodes = new ArrayList<>();
        Map<Node, Boolean> visited = new IdentityHashMap<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (visited.put(node, Boolean.TRUE) != null) {
                continue;
            }
            nodes.add(node);

            List<Node> next = new ArrayList<>();
            for (Relations relation : relations.values()) {
                next.addAll(relation.targets(node));
            }
            next.addAll(node.children());

            for (int index = next.size() - 1; index >= 0; index--) {
                stack.push(next.get(index));
            }
        }

        return nodes;
    }

    private static void writeNode(
            JsonObjectBuilder object, Node node, Map<Node, Integer> ids) {
        object.put("id", ids.get(node));
        object.put("type", node.name());

        object.array("tags", array -> {
            for (String tag : node.tags()) {
                array.add(tag);
            }
        });

        Fragment fragment = node.fragment();
        if (fragment == null) {
            object.putNull("fragment");
        } else {
            object.object("fragment", value -> {
                value.object("start", position -> writePosition(position, fragment.start()));
                value.object("end", position -> writePosition(position, fragment.end()));
                value.put("code", fragment.code());
            });
        }

        object.array("children", array -> {
            for (Node child : node.children()) {
                array.add(ids.get(child));
            }
        });
    }

    private static void writePosition(JsonObjectBuilder object, Position position) {
        object.put("offset", position.offset());
        object.put("line", position.line());
        object.put("column", position.column());
    }
}
