/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Objects;

/** Mutable construction state. Incomplete states are allowed until build(). */
public abstract class NodeBuilder<T extends Node> {
    private final Class<T> nodeType;

    NodeBuilder(Class<T> nodeType) {
        this.nodeType = Objects.requireNonNull(nodeType);
    }

    /** Builds a fresh snapshot; never publishes partially initialized nodes. */
    public final T build() {
        return new BuildContext().build(this);
    }

    final Class<T> nodeType() {
        return nodeType;
    }

    abstract T newNode();

    abstract void fillNode(T node, BuildContext context);

    static String requiredText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(field + " is required");
        }
        return text;
    }
}
