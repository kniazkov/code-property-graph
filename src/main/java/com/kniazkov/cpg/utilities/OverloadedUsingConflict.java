/* Code Property Graph: conservative overload-import rename safety. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** A using-declaration imports an overload set, not just its optional single target. */
final class OverloadedUsingConflict {
    private OverloadedUsingConflict() {}

    /** Imports needing a split when only part of their overload set is renamed. */
    static List<UsingDeclaration> find(Project project, Collection<? extends Declaration> family) {
        Set<Declaration> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        selected.addAll(family);
        Index index = new Index();
        for (SourceFile file : project.files()) index.visit(file, "");
        for (SourceFile file : project.librarySources()) index.visit(file, "");
        var result = new ArrayList<UsingDeclaration>();
        for (UsingDeclaration using : index.imports) {
            String key = using.target().map(index.names::get).orElse(null);
            if (key == null) key = index.lookup(using.qualifiedName(), index.scopes.get(using));
            List<CallableDeclaration> group = index.groups.getOrDefault(key, List.of());
            if (group.stream().anyMatch(selected::contains) && group.stream().anyMatch(d -> !selected.contains(d)))
                result.add(using);
        }
        return List.copyOf(result);
    }

    private static final class Index {
        final Map<Declaration, String> names = new IdentityHashMap<>();
        final Map<Node, String> scopes = new IdentityHashMap<>();
        final Map<String, List<CallableDeclaration>> groups = new LinkedHashMap<>();
        final Map<String, NamespaceAliasDeclaration> aliases = new LinkedHashMap<>();
        final List<UsingDeclaration> imports = new ArrayList<>();

        void visit(Node node, String scope) {
            scopes.put(node, scope);
            if (node instanceof UsingDeclaration using) imports.add(using);
            if (node instanceof NamespaceAliasDeclaration alias) aliases.put(join(scope, alias.name()), alias);
            if (node instanceof CallableDeclaration callable) {
                String name = join(scope, callable.name());
                names.put(callable, name);
                groups.computeIfAbsent(name, ignored -> new ArrayList<>()).add(callable);
            }
            if (node instanceof NamespaceDeclaration namespace) {
                scope = join(scope, namespace.name()); names.put(namespace, scope);
            } else if (node instanceof ClassDeclaration declaration) {
                scope = join(scope, declaration.name()); names.put(declaration, scope);
            }
            for (Node child : node.children()) visit(child, scope);
        }

        String lookup(String written, String scope) {
            if (written.startsWith("::")) return expand(written.substring(2));
            while (true) {
                String candidate = expand(join(scope, written));
                if (groups.containsKey(candidate)) return candidate;
                if (scope.isEmpty()) return null;
                int separator = scope.lastIndexOf("::");
                scope = separator < 0 ? "" : scope.substring(0, separator);
            }
        }

        String expand(String name) {
            Set<String> seen = new HashSet<>();
            while (seen.add(name)) {
                boolean changed = false;
                for (int separator = name.lastIndexOf("::"); separator >= 0;
                        separator = name.lastIndexOf("::", separator - 1)) {
                    NamespaceAliasDeclaration alias = aliases.get(name.substring(0, separator));
                    String target = alias == null ? null : alias.target().map(names::get).orElse(null);
                    if (target != null) { name = target + name.substring(separator); changed = true; break; }
                }
                if (!changed) break;
            }
            return name;
        }
        static String join(String scope, String name) {
            if (name.startsWith("::")) return name.substring(2);
            return scope.isEmpty() ? name : scope + "::" + name;
        }
    }
}
