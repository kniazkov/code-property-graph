/* Read-only context of one prepared invocation. MIT License. */
package com.kniazkov.cpg.passes;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** The immutable input after all declared dependencies have been prepared. Function handles
 * and nodes belong to this snapshot, rather than the caller's possibly older snapshot. */
public final class PassContext {
    private final Project project;
    private final CallableDeclaration function;
    private final Set<PassId> dependencies;
    private final List<Node> nodes;

    PassContext(Project project, CallableDeclaration function, Set<PassId> dependencies) {
        this.project = Objects.requireNonNull(project);
        this.function = function;
        this.dependencies = Collections.unmodifiableSet(new TreeSet<>(dependencies));
        var selected = new ArrayList<Node>();
        (function == null ? project : function).walk(new NodeVisitor() {
            @Override public void visit(Node node) { selected.add(node); }
        });
        nodes = List.copyOf(selected);
    }
    public Project project() { return project; }
    /** Empty denotes whole-project scope. */
    public Optional<CallableDeclaration> function() { return Optional.ofNullable(function); }
    /** Direct declared requirements, all current before this invocation starts. */
    public Set<PassId> dependencies() { return dependencies; }
    /** Owned nodes in the selected scope, in deterministic traversal order. */
    public List<Node> nodes() { return nodes; }
    public <T> Optional<T> resultOf(Node node, ResultKey<T> key) { return project.resultOf(node, key); }
}
