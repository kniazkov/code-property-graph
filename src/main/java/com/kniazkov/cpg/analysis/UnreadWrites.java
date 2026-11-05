/* Code Property Graph: unread local writes. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Reports reachable local writes whose stored value reaches no read in the function. */
public final class UnreadWrites {
    public static final String CODE = "unread-write";
    private UnreadWrites() {}

    /**
     * Builds the DFG for a name-resolved snapshot. Incoming VALUE edges identify actual
     * reachable writes, including assignments nested in expressions. A DEFINITION edge
     * leaving a write proves that some CFG path carries the stored value to a read.
     * Using the assignment expression's result is independent of reading its target.
     */
    public static Project analyze(Project resolved) {
        Project project = DataFlow.analyze(resolved);
        Map<Node, Diagnostic> findings = new IdentityHashMap<>();
        Set<CallableDeclaration> cpp = Collections.newSetFromMap(new IdentityHashMap<>());
        for (SourceFile file : project.files()) if (file.language() == Language.CPP)
            file.walk(new NodeVisitor() {
                @Override public void visit(CallableDeclaration fn) { cpp.add(fn); }
            });
        for (var flow : project.flows()) inspect(flow, cpp.contains(flow.entry()), findings);
        return project.withDiagnostics(CODE, findings);
    }

    private static void inspect(FunctionFlow flow, boolean cpp, Map<Node, Diagnostic> findings) {
        if (!flow.complete()) return;
        Map<Node, String> candidates = new IdentityHashMap<>();
        Set<Node> read = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Declaration> escaped = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> unevaluated = Collections.newSetFromMap(new IdentityHashMap<>());
        if (cpp) flow.entry().body().orElseThrow().walk(new NodeVisitor() {
            @Override public void visit(SizeofExpression size) {
                size.walk(new NodeVisitor() {
                    @Override public void visit(Node node) { unevaluated.add(node); }
                });
            }
        });
        if (cpp) flow.entry().body().orElseThrow().walk(new NodeVisitor() {
            // External code may retain a pointer or a reference to the local and read it later.
            @Override public void visit(AddressOfExpression address) {
                if (!unevaluated.contains(address)) escape(address.operand());
            }
            @Override public void visit(CallExpression call) {
                if (!unevaluated.contains(call) && call.target().isEmpty()) call.arguments().forEach(this::escape);
            }
            private void escape(Expression expr) {
                if (expr instanceof ConversionExpression conversion) escape(conversion.operand());
                if (expr instanceof AssignmentExpression a) escape(a.target());
                if (expr instanceof CompoundAssignmentExpression a) escape(a.target());
                if (expr instanceof NameExpression n) n.target().ifPresent(escaped::add);
            }
        });

        for (var edge : flow.data()) {
            if (edge.kind() == FunctionFlow.Dependency.DEFINITION) {
                read.add(edge.from());
            } else if (edge.to() instanceof VariableDeclaration v && !escaped.contains(v)) {
                candidates.put(v, v.name());
            } else {
                Node write = edge.to();
                Expression target = write instanceof AssignmentExpression assignment ? assignment.target()
                        : write instanceof CompoundAssignmentExpression assignment ? assignment.target()
                        : write instanceof PrefixIncrementExpression || write instanceof PrefixDecrementExpression
                            || write instanceof PostfixIncrementExpression || write instanceof PostfixDecrementExpression
                                ? ((UnaryExpression) write).operand() : null;
                if (target instanceof NameExpression name)
                    name.target().filter(d -> d instanceof VariableDeclaration || d instanceof ParameterDeclaration)
                            .filter(d -> !escaped.contains(d)).ifPresent(d -> candidates.put(write, name.name()));
            }
        }
        candidates.forEach((write, name) -> {
            if (!read.contains(write))
                findings.put(write, new Diagnostic(CODE, Diagnostic.Severity.WARNING,
                        "Value written to '" + name + "' is never read"));
        });
    }
}
