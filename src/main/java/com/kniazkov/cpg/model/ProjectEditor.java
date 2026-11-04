/* Code Property Graph: apply edits to a complete typed snapshot. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Stages replacements and bindings against one source snapshot. Each build creates independent objects. */
public final class ProjectEditor {
    private final Project source;
    private final Set<Node> members = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Node, Node> replacements = new IdentityHashMap<>();
    private final Set<Node> removed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<NameExpression, Declaration> names = new IdentityHashMap<>();
    private final Map<CallExpression, CallableDeclaration> calls = new IdentityHashMap<>();

    public ProjectEditor(Project source) {
        this.source = Objects.requireNonNull(source);
        source.walk(new NodeVisitor() {
            @Override public void visit(Node node) { members.add(node); }
        });
    }

    private void requireMember(Node node) {
        if (!members.contains(node)) throw new IllegalArgumentException("Node is outside the source project");
    }

    /** Replaces the node at its owning edge and redirects reference edges to its replacement. */
    public void replace(Node node, Node replacement) {
        requireMember(node);
        if (node == source || removed.contains(node)) throw new IllegalArgumentException("Cannot replace this node");
        replacements.put(node, Objects.requireNonNull(replacement));
    }

    /** Removes a list element only when no edge from outside its subtree references it. */
    public void remove(Node node) {
        requireMember(node);
        Set<Node> subtree = Collections.newSetFromMap(new IdentityHashMap<>());
        node.walk(new NodeVisitor() {
            @Override public void visit(Node child) { subtree.add(child); }
        });
        for (Node member : members) {
            if (!subtree.contains(member) && member.references().stream().anyMatch(subtree::contains))
                throw new IllegalStateException("Node has incoming references");
        }
        boolean listItem = members.stream().anyMatch(parent ->
                (parent instanceof BlockStatement || parent instanceof SourceFile || parent instanceof Project
                || parent instanceof ClassDeclaration || parent instanceof CallExpression call && call.arguments().contains(node)
                || parent instanceof CallableDeclaration callable && callable.parameters().contains(node))
                && parent.children().contains(node));
        if (!listItem) throw new IllegalArgumentException("Only list elements can be removed");
        removed.add(node);
    }

    public void bind(NameExpression use, Declaration target) {
        requireMember(use); requireMember(target); names.put(use, target);
    }

    public void bind(CallExpression call, CallableDeclaration target) {
        requireMember(call); requireMember(target); calls.put(call, target);
    }

    /** Copies all owned nodes and redirects links in the same identity context before validation. */
    public Project build() {
        var context = new EditContext(replacements, removed);
        var root = context.edit(source, Project.Builder.class);
        names.forEach((use, target) -> context.edit(use, NameExpression.Builder.class).setTarget(context.edge(target, Declaration.class)));
        calls.forEach((use, target) -> context.edit(use, CallExpression.Builder.class).setTarget(context.edge(target, CallableDeclaration.class)));
        return root.build();
    }
}
