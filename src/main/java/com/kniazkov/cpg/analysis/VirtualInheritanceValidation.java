/* Conservative C++03 final-overrider validation. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Checks known complete hierarchies without conflating distinct base subobjects. */
public final class VirtualInheritanceValidation {
    private VirtualInheritanceValidation() {}

    /** Virtual declarations whose subobject has more than one final overrider.
     * Unknown, incomplete, or cyclic hierarchies deliberately produce no findings. */
    public static List<CallableDeclaration> conflictingMethods(Project project, ClassDeclaration cls) {
        if (!complete(cls, identitySet(), identitySet())) return List.of();
        Graph graph = new Graph();
        graph.add(cls);
        Map<CallableDeclaration, List<CallableDeclaration>> overrides = new IdentityHashMap<>();
        var conflicts = new LinkedHashSet<CallableDeclaration>();
        for (Subobject slot : graph.objects) {
            for (CallableDeclaration method : methods(slot.owner)) {
                if (!InheritanceSemantics.isVirtual(project, method)) continue;
                var candidates = new ArrayList<Candidate>();
                for (Subobject object : graph.objects) {
                    if (!reaches(object, slot, identitySet())) continue;
                    for (CallableDeclaration candidate : methods(object.owner)) {
                        if (candidate == method || overrides.computeIfAbsent(candidate,
                                key -> InheritanceSemantics.overrides(project, key)).contains(method))
                            candidates.add(new Candidate(object, candidate));
                    }
                }
                long finals = candidates.stream().filter(candidate -> candidates.stream().noneMatch(other ->
                        other.object != candidate.object
                        && reaches(other.object, candidate.object, identitySet())
                        && overrides.computeIfAbsent(other.method,
                                key -> InheritanceSemantics.overrides(project, key)).contains(candidate.method))).count();
                if (finals > 1) conflicts.add(method);
            }
        }
        return List.copyOf(conflicts);
    }

    private static List<CallableDeclaration> methods(ClassDeclaration cls) {
        return cls.members().stream().filter(CallableDeclaration.class::isInstance)
                .map(CallableDeclaration.class::cast).filter(method -> !method.isFriend()).toList();
    }
    private static <T> Set<T> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }
    private static boolean complete(ClassDeclaration cls, Set<ClassDeclaration> active, Set<ClassDeclaration> done) {
        if (done.contains(cls)) return true;
        if (!cls.isDefinition() || cls.keyword().equals("union") || !active.add(cls)) return false;
        for (BaseSpecifier specifier : cls.bases()) {
            ClassDeclaration base = InheritanceSemantics.baseClass(specifier).orElse(null);
            if (base == null || !complete(base, active, done)) return false;
        }
        active.remove(cls);
        done.add(cls);
        return true;
    }
    private static boolean reaches(Subobject derived, Subobject base, Set<Subobject> seen) {
        if (derived == base) return true;
        if (!seen.add(derived)) return false;
        return derived.bases.stream().anyMatch(parent -> reaches(parent, base, seen));
    }
    private record Candidate(Subobject object, CallableDeclaration method) {}
    private static final class Subobject {
        final ClassDeclaration owner;
        final List<Subobject> bases = new ArrayList<>();
        Subobject(ClassDeclaration owner) { this.owner = owner; }
    }
    private static final class Graph {
        final List<Subobject> objects = new ArrayList<>();
        final Map<ClassDeclaration, Subobject> virtualBases = new IdentityHashMap<>();
        Subobject add(ClassDeclaration cls) {
            Subobject object = new Subobject(cls);
            objects.add(object);
            for (BaseSpecifier specifier : cls.bases()) {
                ClassDeclaration base = InheritanceSemantics.baseClass(specifier).orElseThrow();
                Subobject parent;
                if (specifier.isVirtual()) {
                    parent = virtualBases.get(base);
                    if (parent == null) {
                        parent = add(base);
                        virtualBases.put(base, parent);
                    }
                } else parent = add(base);
                object.bases.add(parent);
            }
            return object;
        }
    }
}
