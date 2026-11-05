/* Context-specific documentation resolution. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Internal snapshot publication used after normal declaration resolution. */
public final class DocumentationBinding {
    private DocumentationBinding() {}
    public static Project analyze(Project project) {
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        var entries = new IdentityHashMap<DocumentationEntry, DocumentationEntry.Builder>();
        project.walk(new NodeVisitor() {
            @Override public void visit(DocumentationEntry entry) {
                entries.put(entry, context.edit(entry, DocumentationEntry.Builder.class).clearBindings());
            }
        });
        project.walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration declaration) {
                for (Comment comment : declaration.commentReferences()) for (DocumentationEntry entry : comment.documentationEntries()) {
                    Declaration target = switch (entry.kind()) {
                        case PARAM -> declaration.parameters().stream().filter(p -> p.name().equals(entry.name())).findFirst().orElse(null);
                        case TPARAM -> declaration.templateParameters().stream().filter(p -> p.name().equals(entry.name())).findFirst().orElse(null);
                        default -> declaration;
                    };
                    entries.get(entry).addBinding(context.edge(declaration, CallableDeclaration.class),
                            target == null ? null : context.edge(target, Declaration.class));
                }
            }
        });
        var build = new BuildContext();
        Project result = build.build(root);
        // Preserve other analyses' diagnostics; entry diagnostics are rebuilt against their exact bindings.
        project.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (!(node instanceof DocumentationEntry)) build.copy(context.copy(node)).findings = node.findings;
            }
        });
        return result;
    }
}
