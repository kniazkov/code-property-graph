/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;

/** Base of the proposed immutable model; independent of the original Graph.Node. */
public abstract class Node {
    Node() {
    }

    /** Returns syntax children in source order, excluding reference edges. */
    public abstract List<Node> children();

    /** Returns resolved reference targets, excluding syntax children. */
    public List<Node> references() {
        return List.of();
    }

    /** Copies the reachable graph into independent builders, preserving identity. */
    public abstract NodeBuilder<? extends Node> edit();

    abstract NodeBuilder<? extends Node> copyBuilder(EditContext context);
}
