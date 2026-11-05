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
        // A callee write may be observed only after returning to its caller.
        Set<Node> observed = Collections.newSetFromMap(new IdentityHashMap<>());
        project.flows().forEach(flow -> flow.data().stream()
                .filter(edge -> edge.kind() == FunctionFlow.Dependency.DEFINITION)
                .forEach(edge -> observed.add(edge.from())));
        var contexts = SourceContexts.index(project);
        for (var flow : project.flows()) {
            SourceFile source = SourceContexts.require(contexts, flow.entry());
            inspect(flow, project.dynamicArraySemantics(), project.containerSemantics(), project.sequenceSemantics(), source.language() == Language.CPP ? TypeSemantics.profile(source) : null, observed, findings);
        }
        return project.withDiagnostics(CODE, findings);
    }

    private static void walkEvaluatedSyntax(CallableDeclaration callable, NodeVisitor visitor) {
        callable.initializers().forEach(initializer -> initializer.walk(visitor));
        callable.body().ifPresent(body -> body.walk(visitor));
        callable.handlers().forEach(handler -> handler.walk(visitor));
    }

    private static void inspect(FunctionFlow flow, List<DynamicArrayOperation> operations, List<ContainerOperation> containers, List<SequenceOperation> sequences, PlatformProfile profile, Set<Node> observed, Map<Node, Diagnostic> findings) {
        if (!flow.complete()) return;
        Map<Node, String> candidates = new IdentityHashMap<>();
        Set<Node> normalized = Collections.newSetFromMap(new IdentityHashMap<>());
        operations.forEach(operation -> normalized.add(operation.syntax()));
        containers.forEach(operation -> normalized.add(operation.syntax()));
        sequences.forEach(operation -> normalized.add(operation.syntax()));
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
        if (profile != null && flow.auxiliaryVertices().stream().anyMatch(vertex ->
                vertex.constructor().filter(LibraryCallEffects::unknown).isPresent()
                || vertex.destructor().filter(LibraryCallEffects::unknown).isPresent())) escaped.addAll(libraryAccessible);
        if (profile != null) walkEvaluatedSyntax(flow.entry(), new NodeVisitor() {
            @Override public void visit(MemberInitializer initializer) {
                if (initializer.target().filter(LibraryCallEffects::libraryObject).isPresent()) {
                    escaped.addAll(libraryAccessible);
                    initializer.arguments().forEach(argument -> escaped.addAll(LibraryCallEffects.roots(argument)));
                }
            }
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
                escaped.addAll(LibraryCallEffects.roots(expr));
                if (expr instanceof ConversionExpression conversion) escape(conversion.operand());
                if (expr instanceof AssignmentExpression a) escape(a.target());
                if (expr instanceof CompoundAssignmentExpression a) escape(a.target());
                if (expr instanceof NameExpression n) n.target().ifPresent(escaped::add);
            }
        });

        for (var edge : flow.data()) {
            // Common sequence writes are observable object operations, not dead scalar stores.
            if (edge.kind() == FunctionFlow.Dependency.VALUE && normalized.contains(edge.to())) continue;
            if (edge.kind() == FunctionFlow.Dependency.DEFINITION) continue;
            if (edge.to() instanceof VariableDeclaration v && !escaped.contains(v)) {
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
            if (!observed.contains(write))
                findings.put(write, new Diagnostic(CODE, Diagnostic.Severity.WARNING,
                        "Value written to '" + name + "' is never read"));
        });
    }
}
