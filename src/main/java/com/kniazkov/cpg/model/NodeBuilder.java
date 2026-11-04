/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Objects;

/** Mutable counterpart of one immutable node. Subclasses hold builders for children
 * and references and may be incomplete while a graph is being assembled.
 * Reusing a builder as a reference target preserves object identity in the snapshot.
 * @param <T> exact snapshot class allocated by this builder */
public abstract class NodeBuilder<T extends Node> {
    private final Class<T> nodeType;

    /** Records the runtime type used to retrieve a previously allocated shell. */
    NodeBuilder(Class<T> nodeType) {
        this.nodeType = Objects.requireNonNull(nodeType);
    }

    /** Starts one assembly session for the reachable builders and validates the resulting ownership tree before returning the root. */
    public final T build() {
        return new BuildContext().build(this);
    }

    /** Provides the checked conversion for identity-map lookups during cyclic assembly. */
    final Class<T> nodeType() {
        return nodeType;
    }

    /** Allocates an unpopulated shell for registration before any outgoing edge is followed. */
    abstract T newNode();

    /** Copies state into the registered shell. Every outgoing edge must use the same context. */
    abstract void fillNode(T node, BuildContext context);

    /** Validates mandatory text at build time and names the missing field in the diagnostic. */
    static String requiredText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(field + " is required");
        }
        return text;
    }
}
