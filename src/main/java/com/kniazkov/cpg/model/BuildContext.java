/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One private assembly session; shells never leave this package during assembly. */
final class BuildContext {
    private final Map<NodeBuilder<?>, Node> nodes = new IdentityHashMap<>();

    <T extends Node> T build(NodeBuilder<T> root) {
        T result = copy(root);
        validateStructure(result);
        return result;
    }

    <T extends Node> T copy(NodeBuilder<T> builder) {
        if (builder == null) {
            throw new IllegalStateException("Required child builder is missing");
        }
        Node existing = nodes.get(builder);
        if (existing != null) {
            return builder.nodeType().cast(existing);
        }
        T node = builder.newNode();
        nodes.put(builder, node);
        builder.fillNode(node, this);
        return node;
    }

    <T extends Node> List<T> copyAll(List<? extends NodeBuilder<? extends T>> builders) {
        List<T> result = new ArrayList<>();
        for (NodeBuilder<? extends T> builder : builders) {
            result.add(copy(builder));
        }
        return List.copyOf(result);
    }

    private void validateStructure(Node root) {
        Map<Node, Node> parents = new IdentityHashMap<>();
        Set<Node> active = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> complete = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node : nodes.values()) {
            visit(node, parents, active, complete);
        }
        if (root instanceof Project) {
            Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>());
            collect(root, owned);
            if (owned.size() != nodes.size()) {
                throw new IllegalStateException("Project reference points outside its syntax tree");
            }
        }
    }

    private void visit(Node node, Map<Node, Node> parents, Set<Node> active, Set<Node> complete) {
        if (active.contains(node)) {
            throw new IllegalStateException("Cycle in syntax children");
        }
        if (complete.contains(node)) {
            return;
        }
        active.add(node);
        for (Node child : node.children()) {
            if (parents.put(child, node) != null) {
                throw new IllegalStateException("Syntax child has multiple owners or positions");
            }
            visit(child, parents, active, complete);
        }
        active.remove(node);
        complete.add(node);
    }

    private void collect(Node node, Set<Node> result) {
        result.add(node);
        for (Node child : node.children()) {
            collect(child, result);
        }
    }
}
