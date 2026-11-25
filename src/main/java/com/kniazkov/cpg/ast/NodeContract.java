package com.kniazkov.cpg.ast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates the structural tag contract of a syntax tree.
 *
 * <p>The contract requires unique tags, one tag per child for regular nodes,
 * and no tags on {@link NodeList} nodes. It also checks that
 * {@link Node#child(String)} agrees with {@link Node#children()}.</p>
 */
public final class NodeContract {

    private NodeContract() {}

    /**
     * Returns all contract violations in the tree rooted at {@code root}.
     *
     * @param root the root of the tree
     * @return an immutable list of violations; empty if the tree is valid
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static List<String> violations(Node root) {
        Objects.requireNonNull(root, "root");
        List<String> violations = new ArrayList<>();
        Map<Node, Boolean> visited = new IdentityHashMap<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (visited.put(node, Boolean.TRUE) != null) {
                continue;
            }
            checkNode(node, violations);
            for (Node child : node.children()) {
                if (child == null) {
                    violations.add(label(node) + " has a null child");
                } else {
                    stack.push(child);
                }
            }
        }

        return List.copyOf(violations);
    }

    /**
     * Validates the tree and throws if any violation is found.
     *
     * @param root the root of the tree
     * @throws NullPointerException if {@code root} is {@code null}
     * @throws IllegalStateException if the tree violates the contract
     */
    public static void requireValid(Node root) {
        List<String> violations = violations(root);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("node contract violations: " + violations);
        }
    }

    private static void checkNode(Node node, List<String> violations) {
        List<String> tags = node.tags();
        List<Node> children = node.children();
        String label = label(node);

        if (tags == null) {
            violations.add(label + " returns null tags");
            return;
        }
        if (children == null) {
            violations.add(label + " returns null children");
            return;
        }

        Set<String> seen = new HashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                violations.add(label + " has a null tag");
            } else if (!seen.add(tag)) {
                violations.add(label + " has a duplicate tag: " + tag);
            }
        }

        if (node instanceof NodeList) {
            if (!tags.isEmpty()) {
                violations.add(label + " is a NodeList but has tags");
            }
        } else if (tags.size() != children.size()) {
            violations.add(label + " has " + tags.size() + " tags but "
                    + children.size() + " children");
        }

        int count = Math.min(tags.size(), children.size());
        for (int index = 0; index < count; index++) {
            String tag = tags.get(index);
            if (tag == null) {
                continue;
            }
            Node byTag = node.child(tag).orElse(null);
            if (byTag != children.get(index)) {
                violations.add(label + " child('" + tag + "') does not match "
                        + "children().get(" + index + ")");
            }
        }
    }

    private static String label(Node node) {
        String simpleName = node.getClass().getSimpleName();
        return simpleName.isEmpty() ? node.getClass().getName() : simpleName;
    }
}
