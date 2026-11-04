/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns the identity map for one build. Each builder is mapped to a fresh shell
 * before its fields are populated, so a back edge finds the same shell.
 * After all reachable shells are filled, ownership is checked separately from
 * reference connectivity. Failure discards the session and its unfinished nodes. */
final class BuildContext {
    private final Map<NodeBuilder<?>, Node> nodes = new IdentityHashMap<>();

    /** Copies the reachable graph, validates ownership, then publishes its root. */
    <T extends Node> T build(NodeBuilder<T> root) {
        T result = copy(root);
        validateStructure(result);
        return result;
    }

    /** Returns the registered shell on a repeated visit. On first encounter allocates, registers and fills a shell using this session. */
    <T extends Node> T copy(NodeBuilder<T> builder) {
        if (builder == null) {
            throw new IllegalStateException("Required child builder is missing");
        }
        Node existing = nodes.get(builder);
        if (existing != null) {
            return builder.nodeType().cast(existing);
        }
        T node = builder.newNode();
        node.origin = builder.origin;
        nodes.put(builder, node);
        builder.fillNode(node, this);
        return node;
    }

    /** Copies ordered child builders in the same session and freezes the resulting list. */
    <T extends Node> List<T> copyAll(List<? extends NodeBuilder<? extends T>> builders) {
        List<T> result = new ArrayList<>();
        for (NodeBuilder<? extends T> builder : builders) {
            result.add(copy(builder));
        }
        return List.copyOf(result);
    }

    /** Checks every reachable component. A Project additionally requires each reference target to be owned by its syntax tree. */
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

    /** Depth-first ownership check. The active path detects cycles, the parent map detects duplicate positions, and the completed set avoids rescanning subtrees. */
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

    /** Collects descendants of a verified acyclic ownership tree for the project boundary check. */
    private void collect(Node node, Set<Node> result) {
        result.add(node);
        for (Node child : node.children()) {
            collect(child, result);
        }
    }
}
