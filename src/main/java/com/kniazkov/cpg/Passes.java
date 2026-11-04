/* Code Property Graph: passes over immutable typed snapshots. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Each pass traverses the input tree and publishes one independent project. */
public final class Passes {
    private Passes() {}

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

    private static void resolveBlock(BlockStatement block, Map<String, Declaration> outer,
                                     Map<String, List<CallableDeclaration>> methods, ProjectEditor editor) {
        var locals = new HashMap<>(outer);
        for (Node item : block.items()) {
            if (item instanceof VariableDeclaration variable) locals.put(variable.name(), variable);
            resolveNode(item, locals, methods, editor);
        }
    }

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

    private static boolean external(Expression expression, String name) {
        return expression instanceof NameExpression n && n.name().equals(name) && n.target().isEmpty();
    }

    private static boolean stream(Expression expression, String member) {
        return expression instanceof ScopeMemberExpression access && access.member().equals(member)
                && external(access.receiver(), "std");
    }

    /** Recognizes the supported console forms after name resolution, respecting local shadowing. */
    public static Project green(Project project) {
        var editor = new ProjectEditor(project);
        for (SourceFile file : project.files()) file.walk(new NodeVisitor() {
            @Override public void visit(ExpressionStatement statement) {
                Expression expr = statement.expression();
                if (file.language() == Language.JAVA && expr instanceof CallExpression call && call.arguments().size() == 1
                        && call.callee() instanceof DotMemberExpression println && println.member().equals("println")
                        && println.receiver() instanceof DotMemberExpression out && out.member().equals("out")
                        && external(out.receiver(), "System")) {
                    editor.replace(statement, PrintStatement.of(call.arguments().getFirst()));
                }
                if (file.language() == Language.CPP && expr instanceof ShiftLeftExpression tail && stream(tail.right(), "endl")
                        && tail.left() instanceof ShiftLeftExpression head && stream(head.left(), "cout")) {
                    editor.replace(statement, PrintStatement.of(head.right()));
                }
            }
        });
        return editor.build();
    }

    /** Evaluates literal arithmetic bottom-up, retaining unsafe divisions and signed 32-bit overflow. */
    public static Project fold(Project project) {
        var editor = new ProjectEditor(project);
        var order = new ArrayList<Node>();
        project.walk(new NodeVisitor() {
            @Override public void visit(Node node) { order.add(node); }
        });
        Map<Node, Integer> constants = new IdentityHashMap<>();
        for (Node node : order.reversed()) {
            if (node instanceof IntegerLiteral literal) {
                constants.put(node, literal.value());
                continue;
            }
            long result;
            if (node instanceof UnaryExpression unary && constants.containsKey(unary.operand())) {
                long operand = constants.get(unary.operand());
                if (unary instanceof NegateExpression) result = -operand;
                else if (unary instanceof UnaryPlusExpression) result = operand;
                else continue;
            } else if (node instanceof BinaryExpression binary
                    && constants.containsKey(binary.left()) && constants.containsKey(binary.right())) {
                long a = constants.get(binary.left()), b = constants.get(binary.right());
                if (binary instanceof AddExpression) result = a + b;
                else if (binary instanceof SubtractExpression) result = a - b;
                else if (binary instanceof MultiplyExpression) result = a * b;
                else if (binary instanceof DivideExpression && b != 0) result = a / b;
                else continue;
            } else continue;
            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                constants.put(node, (int) result);
                editor.replace(node, IntegerLiteral.create().setValue((int) result).build());
            }
        }
        return editor.build();
    }
}
