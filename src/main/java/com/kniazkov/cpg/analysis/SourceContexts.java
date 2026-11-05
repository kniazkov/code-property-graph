/* Code Property Graph: source context of written and derived owned nodes. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Instantiated syntax inherits the invocation's translation-unit language and profile.
 * Catalogue syntax retains its own explicit source context; neither is a physical source file. */
final class SourceContexts {
    private SourceContexts() {}
    record Root(Node node, SourceFile source) {}
    static List<Root> roots(Project project) {
        var roots = new ArrayList<Root>();
        project.files().forEach(file -> roots.add(new Root(file, file)));
        project.librarySources().forEach(file -> roots.add(new Root(file, file)));
        project.templateInstances().forEach(instance -> roots.add(new Root(instance, instance.source())));
        return List.copyOf(roots);
    }
    static Map<Node, SourceFile> index(Project project) {
        Map<Node, SourceFile> result = new IdentityHashMap<>();
        roots(project).forEach(root -> add(result, root.node(), root.source()));
        return result;
    }
    private static void add(Map<Node, SourceFile> result, Node root, SourceFile source) {
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { result.put(node, source); } });
    }
    static SourceFile require(Map<Node, SourceFile> contexts, Node node) {
        SourceFile source = contexts.get(node);
        if (source == null) throw new IllegalStateException("Owned callable has no translation-unit context: " + node.getClass().getSimpleName());
        return source;
    }
}
