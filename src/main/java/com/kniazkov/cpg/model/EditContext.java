/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** One copy session preserving cycles and shared reference targets. */
final class EditContext {
    private final Map<Node, NodeBuilder<?>> builders = new IdentityHashMap<>();

    <T extends Node, B extends NodeBuilder<T>> B edit(T node, Class<B> builderType) {
        return builderType.cast(copy(node));
    }

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

    <T extends Node> void register(T node, NodeBuilder<T> builder) {
        if (builder.nodeType() != node.getClass()) {
            throw new IllegalStateException("Builder does not match node type");
        }
        if (builders.putIfAbsent(node, builder) != null) {
            throw new IllegalStateException("Node already registered");
        }
    }

    <T extends Node> List<NodeBuilder<? extends T>> copyAll(List<T> nodes) {
        List<NodeBuilder<? extends T>> result = new ArrayList<>();
        for (T node : nodes) {
            result.add(copy(node));
        }
        return result;
    }
}
