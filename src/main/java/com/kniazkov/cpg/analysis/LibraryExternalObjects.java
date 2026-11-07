/* Compatible global C-linkage catalogue object identities. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** A narrow external-object identity, not a spelling-based declaration merger.
 * Each header keeps its own declaration occurrence and provenance. Only trusted,
 * global, declaration-only C objects can share storage across catalogue files. */
public final class LibraryExternalObjects {
    private LibraryExternalObjects() {}

    /** Full target contract, external symbol, canonical object type and linkage. */
    public record Identity(String profile, String externalName, String type,
            String languageLinkage, Declaration.Linkage linkage) {}

    /** The caller supplies lexical global scope, never an imported lookup scope. */
    public static Optional<Identity> identity(Declaration declaration, boolean global) {
        if (!global || !(declaration instanceof VariableDeclaration variable)
                || !LibraryDeclarations.isLibrary(variable) || !variable.languageLinkage().equals("C")
                || !variable.modifiers().contains("extern") || variable.modifiers().contains("static")
                || variable.initializer().isPresent() || variable.name().isEmpty() || variable.name().contains("::")
                || variable.type().isEmpty() || !resolved(variable.type().orElseThrow())) return Optional.empty();
        String profile=variable.provenance().orElseThrow().unit().configuration().platform().stableIdentity();
        return Optional.of(new Identity(profile, variable.name(), variable.type().orElseThrow().canonicalIdentity(),
                variable.languageLinkage(), Declaration.Linkage.EXTERNAL));
    }

    private static boolean resolved(TypeNode written) {
        TypeNode type=written.canonicalType();
        if (type instanceof NamedType named) return named.fundamentalKind().isPresent()
                || named.declaration().flatMap(Declaration::typeIdentity).isPresent() && !named.dependent();
        if (type instanceof OpaqueLibraryType) return true;
        if (type instanceof QualifiedType qualified) return resolved(qualified.baseType());
        if (type instanceof PointerType pointer) return resolved(pointer.pointee());
        // Reference variables, arrays and callable types need additional identity
        // rules; this bounded facility currently models ordinary scalar objects.
        return false;
    }

    /** Lexical ownership is authoritative, including after graph copy/publication. */
    public static Map<Identity,List<VariableDeclaration>> groups(Project project) {
        var groups=new LinkedHashMap<Identity,List<VariableDeclaration>>();
        for (SourceFile source:project.librarySources()) for (Node item:source.items())
            if (item instanceof VariableDeclaration variable) identity(variable,true).ifPresent(key ->
                    groups.computeIfAbsent(key,ignored -> new ArrayList<>()).add(variable));
        groups.replaceAll((key,values) -> List.copyOf(values));
        return Collections.unmodifiableMap(groups);
    }

    /** A snapshot-local storage representative; never a fabricated definition. */
    static Map<Declaration,Declaration> representatives(Project project) {
        var result=new IdentityHashMap<Declaration,Declaration>();
        for (var group:groups(project).values()) for (var declaration:group)
            result.put(declaration,group.getFirst());
        return result;
    }
}
