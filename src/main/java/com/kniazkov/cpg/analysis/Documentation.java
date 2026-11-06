/* Read-only semantic documentation queries. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Documentation is physical text; bindings retain each distinct declaration occurrence. */
public final class Documentation {
    private Documentation() {}
    public static Project analyze(Project project) { return DocumentationBinding.analyze(project).recordPass("Documentation"); }

    /** Related declarations are identified by resolved edges, never by name or source location. */
    public static List<CallableDeclaration> relatedDeclarations(Project project, CallableDeclaration declaration) {
        List<CallableDeclaration> all = new ArrayList<>();
        project.walk(new NodeVisitor() { @Override public void visit(CallableDeclaration node) { all.add(node); } });
        if (!all.contains(declaration)) throw new IllegalArgumentException("Declaration is outside project");
        Set<Declaration> group = Collections.newSetFromMap(new IdentityHashMap<>()); group.add(declaration);
        boolean changed;
        do {
            changed = false;
            for (CallableDeclaration node : all) {
                Declaration previous = node.previousDeclaration().orElse(null), definition = node.definition().orElse(null);
                if (group.contains(node) || group.contains(previous) || group.contains(definition)) {
                    changed |= group.add(node);
                    if (previous != null) changed |= group.add(previous);
                    if (definition != null) changed |= group.add(definition);
                }
            }
        } while (changed);
        return all.stream().filter(group::contains).toList();
    }
    /** Separate comments and briefs remain separate. Repeated physical header text appears once. */
    public static List<Comment> commentsForDeclaration(Project project, CallableDeclaration declaration) {
        var result = new LinkedHashMap<Object, Comment>();
        for (var related : relatedDeclarations(project, declaration)) for (var comment : related.commentReferences())
            if (comment.text().startsWith("/**") || comment.text().startsWith("/*!")
                    || comment.text().startsWith("///") || comment.text().startsWith("//!")) result.putIfAbsent(comment.range().<Object>map(r -> List.of(r, comment.text())).orElse(comment), comment);
        return List.copyOf(result.values());
    }
    public static List<DocumentationEntry> forDeclaration(Project project, CallableDeclaration declaration) {
        return commentsForDeclaration(project, declaration).stream().flatMap(c -> c.documentationEntries().stream()).toList();
    }
    /** Parameter identity follows its position in compatible declarations, not its local spelling. */
    public static List<DocumentationEntry> forParameter(Project project, ParameterDeclaration parameter) {
        var owners = new ArrayList<CallableDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(CallableDeclaration node) {
            if (node.parameters().contains(parameter)) owners.add(node);
        } });
        if (owners.size() != 1) throw new IllegalArgumentException("Parameter is not owned by a callable in project");
        CallableDeclaration owner = owners.getFirst();
        int index = owner.parameters().indexOf(parameter);
        var targets = Collections.newSetFromMap(new IdentityHashMap<Declaration, Boolean>());
        relatedDeclarations(project, owner).forEach(d -> { if (index < d.parameters().size()) targets.add(d.parameters().get(index)); });
        var result = new LinkedHashMap<Object, DocumentationEntry>();
        project.walk(new NodeVisitor() { @Override public void visit(DocumentationEntry entry) {
            if (entry.kind() == DocumentationEntry.Kind.PARAM && entry.bindings().stream().anyMatch(b -> targets.contains(b.target())))
                result.putIfAbsent(entry.tagRange().<Object>map(r -> List.of(r, entry.kind(), entry.name(), entry.description())).orElse(entry), entry);
        } });
        return List.copyOf(result.values());
    }
}
