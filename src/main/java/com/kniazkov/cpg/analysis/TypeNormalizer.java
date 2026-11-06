/* Immutable profile-dependent green type facts. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;

/** Adds target facts without replacing the written type syntax or typedef links. */
public final class TypeNormalizer {
    private TypeNormalizer() {}
    public static Project green(Project project) {
        var editor = new ProjectEditor(project).setPassId("TypeNormalizer");
        for (var root : SourceContexts.roots(project)) {
            SourceFile file = root.source();
            if (file.language() != Language.CPP) continue;
            var profile = TypeSemantics.profile(file);
            root.node().walk(new NodeVisitor() {
                @Override public void visit(TypeNode type) { editor.describe(type, TypeSemantics.describe(type, profile)); }
            });
        }
        return editor.build().recordPass("TypeNormalizer");
    }
}
