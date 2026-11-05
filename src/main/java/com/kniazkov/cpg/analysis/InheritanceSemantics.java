/* Derived, non-owning C++03 inheritance relationships. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Snapshot queries, not ownership edges. Dispatch candidates describe known project
 * implementations only: external subclasses and dynamic receiver types remain unknown. */
public final class InheritanceSemantics {
    private InheritanceSemantics() {}

    public static Optional<ClassDeclaration> baseClass(BaseSpecifier base) {
        TypeNode type = TypeSemantics.unqualified(base.type());
        if (type instanceof NamedType named && named.declaration().orElse(null) instanceof ClassDeclaration cls)
            return Optional.of(definition(cls));
        return Optional.empty();
    }
    private static ClassDeclaration definition(ClassDeclaration cls) {
        return cls.definition().orElse(null) instanceof ClassDeclaration def ? def : cls;
    }
    public static boolean derivesFrom(ClassDeclaration derived, ClassDeclaration base) {
        return derivesFrom(definition(derived), definition(base), Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static boolean derivesFrom(ClassDeclaration derived, ClassDeclaration base, Set<ClassDeclaration> seen) {
        if (!seen.add(derived)) return false;
        for (BaseSpecifier specifier : derived.bases()) {
            ClassDeclaration parent = baseClass(specifier).orElse(null);
            if (parent != null && (parent == base || derivesFrom(parent, base, seen))) return true;
        }
        return false;
    }
    public static List<CallableDeclaration> overrides(Project project, CallableDeclaration method) {
        Index index = new Index(project);
        return index.overrides(method, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    public static List<CallableDeclaration> overriddenBy(Project project, CallableDeclaration method) {
        Index index = new Index(project);
        return index.methods.stream().filter(candidate -> index.overrides(candidate,
                Collections.newSetFromMap(new IdentityHashMap<>())).contains(method)).toList();
    }
    public static Optional<ClassDeclaration> owningClass(Project project, CallableDeclaration method) {
        return Optional.ofNullable(new Index(project).owners.get(method));
    }
    public static boolean isVirtual(Project project, CallableDeclaration method) {
        Index index = new Index(project);
        return index.virtual(method, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    /** Known implementations for the static receiver and its known subclasses.
     * Never a closed-world assertion about the runtime target. */
    public static List<CallableDeclaration> possibleTargets(Project project, CallableDeclaration method,
            ClassDeclaration staticReceiver) {
        Index index = new Index(project);
        var result = new LinkedHashSet<CallableDeclaration>();
        // The statically selected implementation is possible unless pure.
        if (!index.pure(method)) result.add(implementation(method));
        for (CallableDeclaration candidate : index.methods) {
            ClassDeclaration owner = index.owners.get(candidate);
            if (owner == null || staticReceiver != null && owner != staticReceiver && !derivesFrom(owner, staticReceiver)) continue;
            if (!index.pure(candidate) && index.overrides(candidate,
                    Collections.newSetFromMap(new IdentityHashMap<>())).contains(method)) result.add(implementation(candidate));
        }
        return List.copyOf(result);
    }
    private static CallableDeclaration implementation(CallableDeclaration method) {
        return method.definition().orElse(null) instanceof CallableDeclaration definition ? definition : method;
    }
    /** Override compatibility ignores return type (including covariant returns),
     * but retains nominal parameter identity, cv qualifiers, and variadicness. */
    public static boolean sameOverrideSignature(CallableDeclaration left, CallableDeclaration right) {
        if (left.kind() == CallableDeclaration.Kind.CONSTRUCTOR || right.kind() == CallableDeclaration.Kind.CONSTRUCTOR
                || left.modifiers().contains("static") || right.modifiers().contains("static")) return false;
        boolean destructors = left.kind() == CallableDeclaration.Kind.DESTRUCTOR && right.kind() == CallableDeclaration.Kind.DESTRUCTOR;
        return (destructors || simpleName(left.name()).equals(simpleName(right.name())))
                && left.kind() == right.kind() && left.isConst() == right.isConst() && left.isVolatile() == right.isVolatile()
                && left.variadic() == right.variadic() && parameterTypes(left).equals(parameterTypes(right));
    }
    private static String simpleName(String name) { int at = name.lastIndexOf("::"); return at < 0 ? name : name.substring(at + 2); }
    private static List<String> parameterTypes(CallableDeclaration method) {
        return method.parameters().stream().map(p -> p.type().map(TypeIdentity::parameterIdentity).orElse("<unknown>" )).toList();
    }
    static final class Index {
        final Map<CallableDeclaration, ClassDeclaration> owners = new IdentityHashMap<>();
        final List<CallableDeclaration> methods = new ArrayList<>();
        Index(Project project) {
            project.walk(new NodeVisitor() {
                @Override public void visit(ClassDeclaration cls) {
                    for (Declaration member : cls.members()) if (member instanceof CallableDeclaration method && !method.isFriend()) {
                        owners.put(method, cls); methods.add(method);
                    }
                }
            });
            // Resolve qualified out-of-line definitions even before declaration links
            // have been published by NameResolver's immutable linking pass.
            for (SourceFile file : project.files()) {
                Map<String, ClassDeclaration> classes = new LinkedHashMap<>();
                collectClasses(file, "", classes);
                file.walk(new NodeVisitor() {
                    @Override public void visit(CallableDeclaration method) {
                        int separator = method.name().lastIndexOf("::");
                        if (separator < 0 || owners.containsKey(method)) return;
                        ClassDeclaration owner = classes.get(method.name().substring(0, separator));
                        if (owner != null) { owners.put(method, owner); methods.add(method); }
                    }
                });
            }
            // Out-of-line definitions retain the owning in-class declaration's relationships.
            for (CallableDeclaration method : List.copyOf(methods))
                if (method.definition().orElse(null) instanceof CallableDeclaration def && !owners.containsKey(def)) {
                    owners.put(def, owners.get(method)); methods.add(def);
                }
        }
        void collectClasses(Node node, String prefix, Map<String, ClassDeclaration> classes) {
            if (node instanceof NamespaceDeclaration namespace) prefix += namespace.name() + "::";
            if (node instanceof ClassDeclaration cls) {
                classes.put(prefix + cls.name(), cls); prefix += cls.name() + "::";
            }
            for (Node child : node.children()) collectClasses(child, prefix, classes);
        }
        boolean pure(CallableDeclaration method) {
            ClassDeclaration owner = owners.get(method);
            return method.isPureVirtual() || owner != null && methods.stream().anyMatch(candidate ->
                    owners.get(candidate) == owner && sameOverrideSignature(method, candidate) && candidate.isPureVirtual());
        }
        boolean virtual(CallableDeclaration method, Set<CallableDeclaration> active) {
            ClassDeclaration owner = owners.get(method);
            return owner != null && (methods.stream().anyMatch(candidate -> owners.get(candidate) == owner
                    && sameOverrideSignature(method, candidate) && candidate.modifiers().contains("virtual"))
                    || !overrides(method, active).isEmpty());
        }
        List<CallableDeclaration> overrides(CallableDeclaration method, Set<CallableDeclaration> active) {
            ClassDeclaration owner = owners.get(method);
            if (owner == null || !active.add(method) || method.modifiers().contains("static")) return List.of();
            var result = new ArrayList<CallableDeclaration>();
            for (CallableDeclaration candidate : methods) {
                ClassDeclaration base = owners.get(candidate);
                if (owner != base && derivesFrom(owner, base) && sameOverrideSignature(method, candidate) && virtual(candidate, active))
                    result.add(candidate);
            }
            active.remove(method);
            return List.copyOf(result);
        }
    }
}
