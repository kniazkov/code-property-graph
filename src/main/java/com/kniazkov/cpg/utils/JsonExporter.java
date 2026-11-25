package com.kniazkov.cpg.utils;

import com.kniazkov.json.Json;
import com.kniazkov.json.JsonObject;
import com.kniazkov.json.JsonObjectBuilder;
import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Position;
import com.kniazkov.cpg.tree.Reference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exports a syntax tree or a subtree as a JSON code property graph.
 *
 * <p>The exporter assigns one integer identifier to every node reachable from
 * the root through {@link Node#children()} and {@link Node#references()}. The
 * root always receives identifier {@code 0}; identifiers increase monotonically
 * in pre-order traversal order. References always point to an identifier that
 * is present in the exported {@code nodes} array.</p>
 *
 * <p>Each node object also contains its {@link Node#tags()} array. Tags are
 * unique within a node; list nodes have an empty tag array.</p>
 */
public final class JsonExporter {

    private static final String FORMAT = "cpp03-cpg";
    private static final int VERSION = 1;

    private JsonExporter() {}

    /**
     * Exports a syntax tree or subtree as a JSON object.
     *
     * @param root the root of the tree or subtree
     * @return the JSON code property graph
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static JsonObject toJson(Node root) {
        Objects.requireNonNull(root, "root");

        List<Node> nodes = collect(root);
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
        return Json.serialize(toJson(root), "  ");
    }

    private static List<Node> collect(Node root) {
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

            List<Reference> references = node.references();
            for (int index = references.size() - 1; index >= 0; index--) {
                stack.push(references.get(index).target());
            }

            List<Node> children = node.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
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

        object.array("references", array -> {
            for (Reference reference : node.references()) {
                array.object(value -> {
                    value.put("kind", reference.kind().name());
                    value.put("target", ids.get(reference.target()));
                });
            }
        });
    }

    private static void writePosition(JsonObjectBuilder object, Position position) {
        object.put("offset", position.offset());
        object.put("line", position.line());
        object.put("column", position.column());
    }
}
