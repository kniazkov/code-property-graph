/* Code Property Graph: unread local writes. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
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
        var contexts = SourceContexts.index(project);
        for (var flow : project.flows()) {
            SourceFile source = SourceContexts.require(contexts, flow.entry());
            inspect(flow, source.language() == Language.CPP ? TypeSemantics.profile(source) : null, findings);
        }
        return project.withDiagnostics(CODE, findings);
    }

    private static void walkEvaluatedSyntax(CallableDeclaration callable, NodeVisitor visitor) {
        callable.initializers().forEach(initializer -> initializer.walk(visitor));
        callable.body().ifPresent(body -> body.walk(visitor));
        callable.handlers().forEach(handler -> handler.walk(visitor));
    }

    private static void inspect(FunctionFlow flow, PlatformProfile profile, Map<Node, Diagnostic> findings) {
        if (!flow.complete()) return;
        Map<Node, String> candidates = new IdentityHashMap<>();
        Set<Node> read = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Declaration> escaped = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> unevaluated = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Declaration> libraryAccessible = profile == null ? Set.of() : LibraryCallEffects.accessible(flow.entry(), profile);
        if (profile != null) walkEvaluatedSyntax(flow.entry(), new NodeVisitor() {
            @Override public void visit(TypeidExpression typeid) {
                if (TypeSemantics.typeidOperandUnevaluated(typeid, profile)) mark(typeid);
            }
            private void mark(Node size) {
                size.walk(new NodeVisitor() {
                    @Override public void visit(Node node) { unevaluated.add(node); }
                });
            }
            @Override public void visit(SizeofExpression size) {
                size.walk(new NodeVisitor() {
                    @Override public void visit(Node node) { unevaluated.add(node); }
                });
            }
        });
        if (profile != null) walkEvaluatedSyntax(flow.entry(), new NodeVisitor() {
            // External code may retain a pointer or a reference to the local and read it later.
            @Override public void visit(Expression expression) {
                if (!unevaluated.contains(expression) && OperatorSemantics.mayInvokeUserCode(expression))
                    OperatorSemantics.operands(expression).forEach(this::escape);
                if (!unevaluated.contains(expression) && LibraryCallEffects.unknown(expression))
                    escaped.addAll(libraryAccessible);
            }
            @Override public void visit(AddressOfExpression address) {
                if (!unevaluated.contains(address)) escape(address.operand());
            }
            @Override public void visit(VariableDeclaration variable) {
                if (variable.constructorTarget().filter(LibraryCallEffects::unknown).isPresent()
                        || variable.destructorTarget().filter(LibraryCallEffects::unknown).isPresent()) {
                    escaped.addAll(libraryAccessible);
                    variable.initializer().ifPresent(value -> value.walk(new NodeVisitor() {
                        @Override public void visit(NameExpression use) { escaped.addAll(LibraryCallEffects.roots(use)); }
                    }));
                }
            }
            @Override public void visit(CallExpression call) {
                if (unevaluated.contains(call)) return;
                if (call.target().isEmpty()) call.arguments().forEach(this::escape);
                if (LibraryCallEffects.unknown(call)) {
                    LibraryCallEffects.exposed(call).forEach(expression -> escaped.addAll(LibraryCallEffects.roots(expression)));
                    escaped.addAll(libraryAccessible);
                }
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
