/* Code Property Graph: typed graph analysis. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Indexes methods across the project, then binds names in lexical order. Each result points into the new snapshot. */
public final class NameResolver {
    private NameResolver() {}

    /** Indexes unambiguous class methods across files before resolving lexical uses. */
    public static Project resolve(Project project) {
        var editor = new ProjectEditor(project);
        Map<String, List<CallableDeclaration>> methods = new HashMap<>();
        project.walk(new NodeVisitor() {
            @Override public void visit(ClassDeclaration owner) {
                for (Declaration member : owner.members()) {
                    if (member instanceof MethodDeclaration method)
                        methods.computeIfAbsent(owner.name() + "." + method.name(), k -> new ArrayList<>()).add(method);
                }
            }
        });
        project.walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration callable) {
                Map<String, Declaration> scope = new HashMap<>();
                callable.parameters().forEach(p -> scope.put(p.name(), p));
                callable.body().ifPresent(block -> resolveBlock(block, scope, methods, editor));
            }
        });
        return editor.build();
    }

    /** Copies the enclosing bindings, including parameters, and introduces local variables in declaration order. */
    private static void resolveBlock(BlockStatement block, Map<String, Declaration> outer,
                                     Map<String, List<CallableDeclaration>> methods, ProjectEditor editor) {
        var locals = new HashMap<>(outer);
        for (Node item : block.items()) {
            if (item instanceof VariableDeclaration variable) locals.put(variable.name(), variable);
            resolveNode(item, locals, methods, editor);
        }
    }

    /** Follows syntax children and binds a qualified call only when exactly one candidate remains. */
    private static void resolveNode(Node node, Map<String, Declaration> scope,
                                    Map<String, List<CallableDeclaration>> methods, ProjectEditor editor) {
        if (node instanceof BlockStatement block) {
            resolveBlock(block, scope, methods, editor);
            return;
        }
        if (node instanceof NameExpression use && scope.containsKey(use.name())) editor.bind(use, scope.get(use.name()));
        if (node instanceof CallExpression call && call.callee() instanceof MemberExpression member
                && member.receiver() instanceof NameExpression owner && !scope.containsKey(owner.name())) {
            var matches = methods.getOrDefault(owner.name() + "." + member.member(), List.of());
            if (matches.size() == 1) editor.bind(call, matches.getFirst());
        }
        for (Node child : node.children()) resolveNode(child, scope, methods, editor);
    }
}
