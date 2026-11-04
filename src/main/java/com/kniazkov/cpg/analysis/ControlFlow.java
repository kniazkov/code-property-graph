/* Code Property Graph: intraprocedural control flow. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.*;
import java.util.*;

/**
 * Builds control flow backwards from each callable's unique exit. The continuation
 * of a statement is known before its entry is computed. Blocks compose these
 * continuations in reverse source order. Conditions are single CFG vertices.
 */
public final class ControlFlow {
    private ControlFlow() {}

    /** Computes one CFG for each callable with a body, replacing previous flow results. */
    public static Project analyze(Project project) {
        List<FunctionFlow> flows = new ArrayList<>();
        project.walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration fn) {
                fn.body().ifPresent(body -> flows.add(new Builder(fn.exit()).build(fn, body)));
            }
        });
        return project.withFlows(flows);
    }

    private static final class Builder {
        private final Exit exit;

        Builder(Exit exit) { this.exit = exit; }
        private final List<ControlEdge> edges = new ArrayList<>();

        FunctionFlow build(CallableDeclaration fn, BlockStatement body) {
            var start = connect(body, exit, Branch.NEXT);
            edges.add(new ControlEdge(fn, start, Branch.ENTRY));
            // Keep unreachable syntax in the AST, without inventing executable paths to it.
            Map<FlowVertex,List<FlowVertex>> successors = new HashMap<>();
            for (var edge : edges) successors.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
            Set<FlowVertex> reached = new HashSet<>();
            var work = new ArrayDeque<FlowVertex>();
            work.push(fn);
            while (!work.isEmpty()) {
                var vertex = work.pop();
                if (reached.add(vertex)) work.addAll(successors.getOrDefault(vertex, List.of()));
            }
            edges.removeIf(edge -> !reached.contains(edge.from()));
            return new FunctionFlow(fn, exit, edges, List.of());
        }

        /** Returns the first executable vertex, or the supplied continuation for an empty block. */
        FlowVertex connect(Node statement, FlowVertex next, Branch fallThrough) {
            if (statement instanceof BlockStatement block) {
                FlowVertex first = next;
                for (Node item : block.items().reversed()) {
                    first = connect(item, first, first == next ? fallThrough : Branch.NEXT);
                }
                return first;
            }
            if (statement instanceof IfStatement branch) {
                var test = branch.condition();
                edges.add(new ControlEdge(test, connect(branch.thenBranch(), next, fallThrough), Branch.TRUE));
                var alternative = branch.elseBranch().map(body -> connect(body, next, fallThrough)).orElse(next);
                edges.add(new ControlEdge(test, alternative, Branch.FALSE));
                return test;
            }
            if (statement instanceof WhileStatement loop) {
                var test = loop.condition();
                edges.add(new ControlEdge(test, connect(loop.body(), test, Branch.BACK), Branch.TRUE));
                edges.add(new ControlEdge(test, next, Branch.FALSE));
                return test;
            }
            if (statement instanceof ReturnStatement) edges.add(new ControlEdge(statement, exit, Branch.RETURN));
            else edges.add(new ControlEdge(statement, next, fallThrough));
            return statement;
        }
    }
}
