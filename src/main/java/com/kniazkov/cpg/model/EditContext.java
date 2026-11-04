/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Owns the inverse identity map for one edit. A node registers its mutable
 * counterpart before copying children and references. Back edges therefore
 * resolve to the same builder, including cycles that cross file boundaries.
 * Independent edit calls allocate independent maps. */
final class EditContext {
    private final Map<Node, NodeBuilder<?>> builders = new IdentityHashMap<>();

    /** Starts from a concrete node and returns its corresponding concrete builder through a checked cast. */
    <T extends Node, B extends NodeBuilder<T>> B edit(T node, Class<B> builderType) {
        return builderType.cast(copy(node));
    }

    /** Reuses a registered builder or delegates to the node to register and populate one. The register method checks the correspondence on insertion. */
    @SuppressWarnings("unchecked")
    <T extends Node> NodeBuilder<T> copy(T node) {
        NodeBuilder<?> existing = builders.get(node);
        if (existing != null) {
            // Registration below guarantees the node/builder type correspondence.
            return (NodeBuilder<T>) existing;
        }
        NodeBuilder<?> builder = node.copyBuilder(this);
        return (NodeBuilder<T>) builder;
    }

    /** Registers the one builder whose runtime node type matches this node, before following any outgoing edge. */
    void register(Node node, NodeBuilder<?> builder) {
        if (builder.nodeType() != node.getClass()) {
            throw new IllegalStateException("Builder does not match node type");
        }
        if (builders.putIfAbsent(node, builder) != null) {
            throw new IllegalStateException("Node already registered");
        }
    }

    /** Copies an ordered child list and returns the mutable list owned by the destination builder. */
    <T extends Node> List<NodeBuilder<? extends T>> copyAll(List<T> nodes) {
        List<NodeBuilder<? extends T>> result = new ArrayList<>();
        for (T node : nodes) {
            result.add(copy(node));
        }
        return result;
    }
}
