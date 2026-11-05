/* C++03 template substitution. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Detached signature probes never publish bodies or alter written declarations. */
public final class TemplateSubstitution {
    private TemplateSubstitution() {}
    /** Own a type occurrence separately from the declarations to which it refers. */
    public static TemplateArgument typeArgument(TypeNode type, TemplateArgument.Kind kind) {
        var fork = new EditContext().fork(type, Map.of());
        return TemplateArgument.create().setType(fork.edge(type, TypeNode.class)).setKind(kind).build();
    }
    public static NamedType templateType(NamedType written,String templateName,Map<? extends Node,? extends Node> substitutions) {
        var replacements=new IdentityHashMap<Node,Node>();replacements.putAll(substitutions);
        var fork=new EditContext().fork(written,replacements);
        var builder=fork.edit(written,NamedType.Builder.class).setName(templateName).clearDeclaration().setDependent(false);
        return builder.build();
    }
    public static CallableDeclaration signature(CallableDeclaration origin, Map<? extends Node, ? extends Node> substitutions) {
        var replacements = new IdentityHashMap<Node, Node>(); replacements.putAll(substitutions);
        var fork = new EditContext().fork(origin, replacements);
        var builder = (CallableDeclaration.Builder<?, ?>) fork.copy(origin);
        builder.setTemplateParameters(List.of()); builder.setTemplateKind(TemplateKind.NONE); builder.clearTemplateArguments(); builder.setExportedTemplate(false); builder.clearBody(); builder.clearDefinition(); builder.clearPreviousDeclaration();
        return builder.build();
    }
}
