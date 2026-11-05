/* Immutable profile-dependent green type facts. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;

/** Adds target facts without replacing the written type syntax or typedef links. */
public final class TypeNormalizer {
    private TypeNormalizer() {}
    public static Project green(Project project) {
        var editor = new ProjectEditor(project);
        for (SourceFile file : project.files()) if (file.language() == Language.CPP) {
            var profile = TypeSemantics.profile(file);
            file.walk(new NodeVisitor() {
                @Override public void visit(TypeNode type) { editor.describe(type, TypeSemantics.describe(type, profile)); }
            });
        }
        return editor.build();
    }
}
