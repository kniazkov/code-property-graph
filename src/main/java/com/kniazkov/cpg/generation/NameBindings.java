/* Code Property Graph: preserve selected C++ name bindings during emission. MIT License. */
package com.kniazkov.cpg.generation;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import java.util.*;

/** Checks written lookup, not the potentially multiple runtime targets of a virtual call. */
final class NameBindings {
    private NameBindings() {}

    static Project prepare(Project input) {
        if (input.files().stream().noneMatch(file -> file.language() == Language.CPP)) return input;
        Index original = new Index(input);
        Project resolved = NameResolver.resolve(input.edit().build());
        Index checked = new Index(resolved);
        ProjectEditor edits = new ProjectEditor(input).withoutRevalidation();
        boolean changed = false;
        for (var entry : original.nodes.entrySet()) {
            Node use = entry.getValue();
            Declaration selected = target(use);
            if (selected == null) continue;
            Node fresh = checked.nodes.get(entry.getKey());
            if (same(selected, target(fresh), original, checked)) continue;
            String spelling = original.qualified.get(selected);
            if (spelling == null || !simple(use))
                throw failure(use, "selected declaration cannot be named safely", original);
            if (use instanceof NamedType type) edits.replace(type, type.edit().setName(spelling).build());
            else if (use instanceof NameExpression name) edits.replace(name, name.edit().setName(spelling).build());
            else continue;
            changed = true;
        }
        Project result = changed ? edits.build() : input;
        if (changed) {
            resolved = NameResolver.resolve(result.edit().build());
            checked = new Index(resolved);
            for (var entry : original.nodes.entrySet()) {
                Declaration selected = target(entry.getValue());
                if (selected != null && !same(selected, target(checked.nodes.get(entry.getKey())), original, checked))
                    throw failure(entry.getValue(), "qualification does not preserve the selected declaration", original);
            }
        }
        // Static overload selection is distinct from the union of virtual implementations.
        for (var entry : original.nodes.entrySet()) if (entry.getValue() instanceof CallExpression call && call.target().isPresent()) {
            Node fresh = checked.nodes.get(entry.getKey());
            Declaration selected = fresh instanceof CallExpression current ? current.target().orElse(null) : null;
            if (!same(call.target().orElseThrow(), selected, original, checked))
                throw failure(call.callee(), "the selected static overload cannot be preserved", original);
        }
        for (var entry : original.nodes.entrySet()) if (entry.getValue() instanceof Expression expression && expression.operatorTarget().isPresent()) {
            Node fresh = checked.nodes.get(entry.getKey());
            Declaration selected = fresh instanceof Expression current ? current.operatorTarget().orElse(null) : null;
            if (!same(expression.operatorTarget().orElseThrow(), selected, original, checked))
                throw failure(expression, "the selected operator overload cannot be preserved", original);
        }
        // Implicit wrappers do not affect syntax paths, but their selected user-defined
        // conversions still constrain the emitted operand's meaning.
        for (var entry : original.conversions.entrySet()) {
            var selected = entry.getValue().stream().filter(conversion -> conversion.operatorTarget().isPresent()).toList();
            if (selected.isEmpty()) continue;
            var fresh = checked.conversions.getOrDefault(entry.getKey(), List.of()).stream()
                    .filter(conversion -> conversion.operatorTarget().isPresent()).toList();
            String reason = "the selected implicit conversion cannot be preserved; re-resolved targets: "
                    + fresh.stream().map(conversion -> conversion.operatorTarget().orElseThrow().name()).toList();
            if (selected.size() != fresh.size())
                throw failure(selected.getFirst(), reason, original);
            for (int i = 0; i < selected.size(); i++)
                if (!same(selected.get(i).operatorTarget().orElseThrow(), fresh.get(i).operatorTarget().orElseThrow(), original, checked))
                    throw failure(selected.get(i), reason, original);
        }
        // Never publish output with newly ambiguous lookup, even if no old target was selected.
        for (Node node : checked.nodes.values()) for (Diagnostic diagnostic : node.diagnostics()) {
            if (diagnostic.severity() == Diagnostic.Severity.ERROR)
                throw failure(node, diagnostic.code() + ": " + diagnostic.message(), checked);
        }
        return changed ? resolved : input;
    }

    private static boolean simple(Node node) {
        return node instanceof NamedType type && type.qualifier().isEmpty() && !type.hasTemplateArguments() && !type.dependent()
                || node instanceof NameExpression name && !name.hasTemplateArguments() && !name.dependent();
    }
    private static Declaration target(Node node) {
        if (node instanceof NamedType type) return type.alias().map(Declaration.class::cast).orElseGet(() -> type.declaration().orElse(null));
        if (node instanceof NameExpression name) return name.target().orElse(null);
        if (node instanceof MemberExpression member) return member.target().orElse(null);
        if (node instanceof MemberInitializer initializer) return initializer.target().orElse(null);
        if (node instanceof UsingDirective directive) return directive.target().orElse(null);
        if (node instanceof UsingDeclaration declaration) return declaration.target().orElse(null);
        if (node instanceof NamespaceAliasDeclaration alias) return alias.target().orElse(null);
        return null;
    }
    private static boolean same(Declaration before, Declaration after, Index original, Index checked) {
        if (after == null) return false;
        String path = original.paths.get(before);
        if (path != null) return path.equals(checked.paths.get(after));
        // A detached external type can retain a stable semantic identity without an owned occurrence.
        return before.getClass() == after.getClass() && before.range().equals(after.range())
                && before.typeIdentity().isPresent() && before.typeIdentity().equals(after.typeIdentity());
    }
    private static IllegalArgumentException failure(Node use, String reason, Index index) {
        String written = use instanceof NamedType type ? type.name() : use instanceof NameExpression name ? name.name() : use instanceof MemberExpression member ? member.member()
                : use instanceof MemberInitializer initializer ? initializer.name()
                : use instanceof UsingDirective directive ? directive.namespaceName()
                : use instanceof UsingDeclaration declaration ? declaration.qualifiedName()
                : use instanceof NamespaceAliasDeclaration alias ? alias.targetName() : use.getClass().getSimpleName();
        if (use instanceof Expression expression && expression.operatorTarget().isPresent())
            written = expression.operatorTarget().orElseThrow().name();
        String leaf = written.contains("::") ? written.substring(written.lastIndexOf("::") + 2) : written;
        String suffix = "::" + leaf;
        var candidates = new TreeSet<String>(index.candidates.getOrDefault(leaf, Set.of()));
        index.qualified.forEach((declaration, name) -> { if (name.endsWith(suffix)) candidates.add(name); });
        String location = use.range().map(range -> range.file() + ":" + range.start().line() + ":" + range.start().column()).orElse("synthetic source");
        return new IllegalArgumentException("Cannot preserve C++ name binding at " + location + " for '" + written + "': " + reason
                + (candidates.isEmpty() ? "" : "; candidates: " + String.join(", ", candidates)));
    }

    /** Paths ignore implicit conversions because resolution may insert or refresh them. */
    private static final class Index {
        final Map<String, Node> nodes = new LinkedHashMap<>();
        final Map<String, List<ConversionExpression>> conversions = new LinkedHashMap<>();
        final Map<Node, String> paths = new IdentityHashMap<>();
        final Map<Declaration, String> qualified = new IdentityHashMap<>();
        final Map<String, Set<String>> candidates = new LinkedHashMap<>();
        Index(Project project) {
            for (int i = 0; i < project.files().size(); i++)
                index(project.files().get(i), "file" + i, "", true);
            var written = new LinkedHashMap<>(nodes);
            for (int i = 0; i < project.librarySources().size(); i++)
                index(project.librarySources().get(i), "library" + i, "", true);
            for (int i = 0; i < project.templateInstances().size(); i++)
                index(project.templateInstances().get(i), "template" + i, "", false);
            for (int i = 0; i < project.classTemplateInstances().size(); i++)
                index(project.classTemplateInstances().get(i), "class-template" + i, "", false);
            nodes.clear(); nodes.putAll(written);
        }
        void index(Node node, String path, String scope, boolean nameable) {
            if (node instanceof ConversionExpression conversion && !conversion.isExplicit()) {
                if (path.startsWith("file")) conversions.computeIfAbsent(path, ignored -> new ArrayList<>()).add(conversion);
                index(conversion.operand(), path, scope, nameable); return;
            }
            nodes.put(path, node); paths.put(node, path);
            if (node instanceof Declaration declaration) {
                String name = declarationName(declaration);
                if (name != null) candidates.computeIfAbsent(name, ignored -> new TreeSet<>()).add(
                        (nameable ? "::" + scope + name : name) + declaration.range().map(range -> " at "
                                + range.file() + ":" + range.start().line() + ":" + range.start().column()).orElse(" (synthetic declaration)"));
                if (!nameable) name = null;
                if (name != null && name.matches("[A-Za-z_][A-Za-z_0-9]*(::[A-Za-z_][A-Za-z_0-9]*)*"))
                    qualified.put(declaration, "::" + scope + name);
            }
            if (node instanceof NamespaceDeclaration namespace) {
                nameable &= !namespace.anonymous();
                scope += namespace.name() + "::";
            } else if (!(node instanceof SourceFile)) nameable = false;
            List<Node> children = node.children().stream().filter(child -> !(child instanceof Comment)).toList();
            for (int i = 0; i < children.size(); i++) index(children.get(i), path + "/" + i, scope, nameable);
        }
        private static String declarationName(Declaration declaration) {
            return switch (declaration) {
                case TypedefDeclaration d -> d.name();
                case ClassDeclaration d -> d.name();
                case EnumDeclaration d -> d.name();
                case VariableDeclaration d -> d.name();
                case CallableDeclaration d -> d.name();
                case NamespaceDeclaration d -> d.name();
                default -> null;
            };
        }
    }
}
