/* Independent graph passes shared by the supported languages. MIT License. */
package com.kniazkov.cpg;

import java.util.*;
import com.kniazkov.cpg.Graph.*;

/** Transformations return new snapshots and never mutate their input. */
public final class Passes {
    private Passes() {}
    public static Graph resolve(Graph graph) {
        Builder b = graph.mutable();
        Map<String, Draft> methods = new HashMap<>(); Set<String> ambiguous = new HashSet<>();
        for (Draft file : b.root().children()) for (Draft top : file.children()) {
            if (top.kind() == Kind.CLASS) for (Draft method : top.children()) {
                String key = top.text().replaceFirst("^public ", "") + "::main";
                if (methods.putIfAbsent(key, method) != null) ambiguous.add(key);
            }
        }
        for (String key : ambiguous) methods.remove(key);
        for (Draft node : b.nodes()) if (node.kind() == Kind.METHOD) {
            Map<String, Draft> variables = new HashMap<>();
            for (Draft stmt : node.children().get(0).children()) {
                resolveNames(stmt, variables, methods);
                if (stmt.kind() == Kind.DECLARE) variables.put(stmt.text(), stmt);
            }
        }
        return b.build();
    }
    private static void resolveNames(Draft n, Map<String, Draft> vars, Map<String, Draft> methods) {
        if (n.kind() == Kind.NAME) n.reference(vars.get(n.text()));
        if (n.kind() == Kind.CALL) n.reference(methods.get(path(n.children().get(0))));
        for (Draft c : n.children()) resolveNames(c, vars, methods);
    }
    private static String path(Draft n) {
        if (n.kind() == Kind.NAME) return n.text();
        if (n.kind() == Kind.MEMBER) return path(n.children().get(0)) + n.text();
        return "";
    }
    /** Recognizes only the documented console idioms; external library symbols may be unresolved. */
    public static Graph green(Graph graph) {
        Builder b = graph.mutable();
        for (Draft stmt : List.copyOf(b.nodes())) {
            if (stmt.kind() != Kind.EXPRESSION) continue;
            Draft expression = stmt.children().get(0), argument = null;
            if (expression.kind() == Kind.CALL && expression.children().size() == 2
                && path(expression.children().get(0)).equals("System.out.println")
                && baseUnresolved(expression.children().get(0))) argument = expression.children().get(1);
            if (expression.kind() == Kind.BINARY && expression.text().equals("<<")) {
                Draft left = expression.children().get(0), right = expression.children().get(1);
                if (left.kind() == Kind.BINARY && left.text().equals("<<")
                    && path(left.children().get(0)).equals("std::cout") && path(right).equals("std::endl")
                    && baseUnresolved(left.children().get(0)) && baseUnresolved(right)) argument = left.children().get(1);
            }
            if (argument != null) b.replace(stmt, new Draft(Kind.PRINT, "", argument));
        }
        return b.build();
    }
    private static boolean baseUnresolved(Draft n) {
        while (n.kind() == Kind.MEMBER) n = n.children().get(0);
        return n.reference() == null;
    }
    /** Folds signed 32-bit arithmetic only when every intermediate result fits. */
    public static Graph fold(Graph graph) {
        Builder b = graph.mutable(); foldNode(b, b.root()); return b.build();
    }
    private static Draft foldNode(Builder b, Draft n) {
        for (Draft child : List.copyOf(n.children())) foldNode(b, child);
        Long result = null;
        if (n.kind() == Kind.UNARY && n.children().get(0).kind() == Kind.INTEGER) {
            long value = Long.parseLong(n.children().get(0).text()); result = n.text().equals("-") ? -value : value;
        }
        if (n.kind() == Kind.BINARY && !n.text().equals("<<") && n.children().stream().allMatch(c -> c.kind() == Kind.INTEGER)) {
            long a = Long.parseLong(n.children().get(0).text()), c = Long.parseLong(n.children().get(1).text());
            result = switch(n.text()) { case "+" -> a + c; case "-" -> a - c; case "*" -> a * c; case "/" -> c == 0 ? null : a / c; default -> null; };
        }
        if (result != null && result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
            Draft replacement = new Draft(Kind.INTEGER, result.toString()); b.replace(n, replacement); return replacement;
        }
        return n;
    }
}
