/* Code Property Graph: executable paths and data dependencies. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.*;
import com.kniazkov.cpg.visualization.DotVisualizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises both front ends, CFG joins, back edges, exits and data-flow fixed points. */
class FlowTest {
    @TempDir Path dir;

    Project read(Language language, String body) throws Exception {
        String code = language == Language.JAVA ? "class Demo { public static void main(String[] args) {" + body + "}}"
                : "class Demo { public: static void main() {" + body + "} };";
        Files.writeString(dir.resolve(language == Language.JAVA ? "Demo.java" : "Demo.cpp"), code);
        return Cpg.read(dir, new Cpg.Options(language, Cpg.Stage.RESOLVED));
    }

    static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        List<T> result = new ArrayList<>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); }
        });
        return result;
    }

    static Set<Node> writes(FunctionFlow flow, Node read) {
        Set<Node> writes = new HashSet<>();
        for (var edge : flow.data()) if (edge.kind() == Dependency.DEFINITION && edge.to() == read) writes.add(edge.from());
        return writes;
    }

    @Test void branchesKillOrPreserveIncomingDefinition() throws Exception {
        for (Language language : List.of(Language.JAVA, Language.CPP)) for (boolean hasElse : List.of(true, false)) {
            var p = read(language, "int x = 0; if (x <= 4) x = 1;" + (hasElse ? " else x = 2;" : "") + "int y = x;");
            var flow = p.flows().getFirst();
            var vars = nodes(p, VariableDeclaration.class);
            var arms = nodes(p, AssignmentExpression.class);
            assertEquals(Set.of(arms.getFirst(), hasElse ? arms.getLast() : vars.getFirst()),
                    writes(flow, vars.getLast().initializer().orElseThrow()));
            var condition = nodes(p, IfStatement.class).getFirst().condition();
            assertEquals(2, flow.control().stream().filter(e -> e.from() == condition).count());
        }
    }

    @Test void loopDefinitionsReachEarlierReadsAndAfterLoop() throws Exception {
        var p = read(Language.JAVA, "int x = 0; while (x < 4) { x = x + 1; } int y = x;");
        var flow = p.flows().getFirst();
        var vars = nodes(p, VariableDeclaration.class);
        var update = nodes(p, AssignmentExpression.class).getFirst();
        var loop = nodes(p, WhileStatement.class).getFirst();
        var expected = Set.of(vars.getFirst(), update);
        assertEquals(expected, writes(flow, ((BinaryExpression)loop.condition()).left()));
        assertEquals(expected, writes(flow, ((BinaryExpression)update.value()).left()));
        assertEquals(expected, writes(flow, vars.getLast().initializer().orElseThrow()));
        assertTrue(flow.control().stream().anyMatch(e -> e.to() == loop.condition() && e.kind() == Branch.BACK));
        assertTrue(writes(flow, update.target()).isEmpty());
    }

    @Test void isolatedExitAndNoContinuationAfterReturn() throws Exception {
        var p = read(Language.JAVA, "int x = 1; if (x != 0) return; return; x = 9;");
        var f = p.flows().getFirst();
        var returns = nodes(p, ReturnStatement.class);
        assertEquals(2, returns.size());
        for (var r : returns)
            assertEquals(List.of(new ControlEdge(r, f.exit(), Branch.RETURN)), f.control().stream().filter(e -> e.from() == r).toList());
        var dead = nodes(p, ExpressionStatement.class).getFirst();
        assertFalse(f.control().stream().anyMatch(e -> e.from() == dead || e.to() == dead));
        var empty = read(Language.CPP, "").flows().getFirst();
        assertEquals(List.of(new ControlEdge(empty.entry(), empty.exit(), Branch.ENTRY)), empty.control());
        assertNotSame(f.exit(), empty.exit());
    }

    @Test void valuesCallsAndLanguageSpecificArgumentOrder() throws Exception {
        for (Language lang : List.of(Language.JAVA, Language.CPP)) {
            var p = read(lang, "int x = 0; int y = f(x = 1, x);");
            var f = p.flows().getFirst();
            var call = nodes(p, CallExpression.class).getFirst();
            var variables = nodes(p, VariableDeclaration.class);
            var assignment = call.arguments().getFirst();
            var read = call.arguments().getLast();
            assertEquals(lang == Language.CPP ? Set.of(variables.getFirst(), assignment) : Set.of(assignment), writes(f, read));
            assertTrue(f.data().contains(new DataEdge(read, call, Dependency.VALUE)));
            assertTrue(f.data().contains(new DataEdge(call, variables.getLast(), Dependency.VALUE)));
        }
    }

    @Test void danglingElseShadowingAndEmptyLoop() throws Exception {
        var p = read(Language.CPP, "int x = 1; if (x > 0) if (x >= 2) { int x = 3; f(x); } else x = 4; while (x == 5) {} int y = x;");
        var f = p.flows().getFirst();
        var vars = nodes(p, VariableDeclaration.class);
        var branches = nodes(p, IfStatement.class);
        assertTrue(branches.getFirst().elseBranch().isEmpty());
        assertTrue(branches.getLast().elseBranch().isPresent());
        var call = nodes(p, CallExpression.class).getFirst();
        assertEquals(Set.of(vars.get(1)), writes(f, call.arguments().getFirst()));
        assertEquals(Set.of(vars.getFirst(), nodes(p, AssignmentExpression.class).getFirst()), writes(f, vars.getLast().initializer().orElseThrow()));
        var condition = nodes(p, WhileStatement.class).getFirst().condition();
        assertTrue(f.control().contains(new ControlEdge(condition, condition, Branch.TRUE)));
        assertTrue(DotVisualizer.toDot(p).contains("DFG def"));
        assertTrue(p.edit().build().flows().isEmpty());
    }
}
