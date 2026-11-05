/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/** Root of the immutable syntax hierarchy. Each snapshot has two kinds of edges.
 * Children form an ordered ownership tree. Resolved references may share targets
 * or point back to ancestors. A visitor selects behavior by the concrete node type.
 * The public API observes only completed snapshots. */
public abstract non-sealed class Node implements FlowVertex {
    /** Restricts node construction to the model package during snapshot assembly. */
    /** Internal edit lineage, never a syntax edge or exported identity. */
    Object editIdentity = new Object();
    List<Comment> commentReferences = List.of();
    /** Ordered explanatory links; comments are owned only by their source file. */
    public final List<Comment> commentReferences() { return commentReferences; }
    List<TransformationProvenance> transformationProvenance = List.of();
    /** Historical inputs retained by identity, outside ownership and semantic references. */
    public final List<TransformationProvenance> transformationProvenance() { return transformationProvenance; }
    SourceRange origin;
    TokenProvenance provenance;

    /** Source-token links, separate from syntax children and resolved references. */
    public final java.util.Optional<TokenProvenance> provenance() { return java.util.Optional.ofNullable(provenance); }
    List<Diagnostic> findings = List.of();

    /** Returns all findings for this snapshot in reporting order. Editing discards derived findings. */
    public final List<Diagnostic> diagnostics() { return findings; }

    /** Returns the original text span when this node has source provenance. */
    public final java.util.Optional<SourceRange> range() {
        return java.util.Optional.ofNullable(origin);
    }

    Node() {
    }

    /** Returns an immutable list of owned children in syntax order. Leaves return an empty list. */
    public abstract List<Node> children();

    /** Returns an immutable list of resolved non-owning edges. The default applies to nodes with no such edges. */
    public List<Node> references() {
        return List.of();
    }

    /** Starts an independent editing session. Shared targets and cycles among all reachable nodes are preserved. */
    public abstract NodeBuilder<? extends Node> edit();

    /** Renders this fragment in a supported language. Use Cpg.sources for whole-project entry point adaptation. */
    public final String source(Language target) {
        return com.kniazkov.cpg.generation.Source.render(this, target);
    }

    /** Returns the supported direct emission targets for this subtree. */
    public final java.util.Set<Language> languages() {
        return com.kniazkov.cpg.generation.Source.languages(this);
    }

    /** Dispatches this single node to its concrete overload on the visitor. */
    public abstract void accept(NodeVisitor visitor);

    /**
     * Visits this subtree in preorder, with siblings in the order of children().
     * The explicit stack holds pending syntax nodes. Reference targets are
     * available to the visitor through references() but never enter the stack.
     */
    public final void walk(NodeVisitor visitor) {
        Objects.requireNonNull(visitor);
        var pending = new ArrayDeque<Node>();
        pending.push(this);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            node.accept(visitor);
            List<Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                pending.push(children.get(i));
            }
        }
    }

    /** Registers a fresh builder before copying children and reference targets into it. */
    abstract NodeBuilder<? extends Node> copyBuilder(EditContext context);
}
