/* Code Property Graph: typed graph analysis. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Resolves lexical uses and calls within a TU and its explicit or unknown link composition. */
public final class NameResolver {
    private NameResolver() {}

    public static Project resolve(Project project) {
        var editor = new ProjectEditor(project);
        Map<String, List<CallableDeclaration>> global = new LinkedHashMap<>();
        project.files().forEach(file -> index(file, global));
        for (var file : project.files()) {
            Map<String, List<CallableDeclaration>> local = new LinkedHashMap<>();
            index(file, local);
            file.walk(new NodeVisitor() {
                @Override public void visit(CallableDeclaration callable) {
                    Map<String, Declaration> scope = new HashMap<>();
                    callable.parameters().forEach(p -> scope.put(p.name(), p));
                    callable.body().ifPresent(block -> resolveNode(block, scope, local, global,
                            project.composition().isPresent(), editor));
                }
            });
        }
        Project resolved = editor.build();
        var conflicts = new IdentityHashMap<Node, Diagnostic>();
        List<? extends Node> definitionScopes = resolved.composition().isPresent() ? List.of(resolved) : resolved.files();
        for (Node definitionScope : definitionScopes) {
            var functions = new LinkedHashMap<String, List<FunctionDeclaration>>();
            definitionScope.walk(new NodeVisitor() {
                @Override public void visit(FunctionDeclaration function) {
                    if (function.body().isPresent())
                        functions.computeIfAbsent(function.name(), k -> new ArrayList<>()).add(function);
                }
            });
            functions.values().stream().filter(list -> list.size() > 1).forEach(list -> list.forEach(function ->
                    conflicts.put(function, new Diagnostic("odr-conflict", Diagnostic.Severity.ERROR,
                            "Multiple definitions of " + function.name() + " in one translation unit or selected build composition"))));
        }
        resolved.walk(new NodeVisitor() {
            @Override public void visit(CallExpression call) {
                if (call.resolution() == CallExpression.Resolution.ODR_CONFLICT) {
                    var diagnostic = new Diagnostic("odr-conflict", Diagnostic.Severity.ERROR,
                            "Multiple definitions in one translation unit or selected build composition");
                    conflicts.put(call, diagnostic);
                    call.candidates().forEach(candidate -> conflicts.put(candidate, diagnostic));
                }
            }
        });
        return resolved.withDiagnostics("odr-conflict", conflicts);
    }

    private static void index(SourceFile file, Map<String, List<CallableDeclaration>> index) {
        file.walk(new NodeVisitor() {
            @Override public void visit(ClassDeclaration owner) {
                for (var member : owner.members()) if (member instanceof MethodDeclaration method)
                    index.computeIfAbsent(owner.name() + "." + method.name(), k -> new ArrayList<>()).add(method);
            }
            @Override public void visit(FunctionDeclaration function) {
                index.computeIfAbsent(function.name(), k -> new ArrayList<>()).add(function);
            }
        });
    }

    private static List<CallableDeclaration> definitions(List<CallableDeclaration> candidates) {
        return candidates.stream().filter(c -> c.body().isPresent()).toList();
    }

    private static void resolveNode(Node node, Map<String, Declaration> scope,
            Map<String, List<CallableDeclaration>> local, Map<String, List<CallableDeclaration>> global,
            boolean knownBuild, ProjectEditor editor) {
        if (node instanceof BlockStatement block) {
            var locals = new HashMap<>(scope);
            for (Node item : block.items()) {
                if (item instanceof VariableDeclaration variable) locals.put(variable.name(), variable);
                resolveNode(item, locals, local, global, knownBuild, editor);
            }
            return;
        }
        if (node instanceof NameExpression use && scope.containsKey(use.name())) editor.bind(use, scope.get(use.name()));
        if (node instanceof CallExpression call) {
            String key = null;
            boolean qualified = false;
            if (call.callee() instanceof NameExpression name && !scope.containsKey(name.name())) key = name.name();
            else if (call.callee() instanceof MemberExpression member
                    && member.receiver() instanceof NameExpression owner && !scope.containsKey(owner.name())) {
                key = owner.name() + "." + member.member();
                qualified = true;
            }
            List<CallableDeclaration> matches = List.of();
            boolean localConflict = false;
            if (key != null) {
                var localDeclarations = local.getOrDefault(key, List.of());
                var allDeclarations = global.getOrDefault(key, List.of());
                localConflict = definitions(localDeclarations).size() > 1;
                // In-class methods are inline and may appear as separate header instances in each TU.
                matches = definitions(knownBuild && !qualified ? allDeclarations : localDeclarations);
                if (matches.isEmpty()) matches = definitions(allDeclarations);
                if (matches.isEmpty()) matches = localDeclarations.isEmpty() ? allDeclarations : localDeclarations;
            }
            var status = matches.isEmpty() ? CallExpression.Resolution.UNRESOLVED
                    : matches.size() == 1 ? CallExpression.Resolution.RESOLVED
                    : (knownBuild || localConflict) && definitions(matches).size() > 1 ? CallExpression.Resolution.ODR_CONFLICT
                    : CallExpression.Resolution.AMBIGUOUS;
            editor.resolve(call, matches, status);
        }
        for (Node child : node.children()) resolveNode(child, scope, local, global, knownBuild, editor);
    }
}
