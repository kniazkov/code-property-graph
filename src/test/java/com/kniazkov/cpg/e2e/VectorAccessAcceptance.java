/* Real C++03 vector access and handle lifetime acceptance. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class VectorAccessAcceptance {
    private VectorAccessAcceptance() { }
    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var green = Cpg.read(cli.input, options(Cpg.Stage.GREEN));
        verifyGraph(green);
        verifyGraph(UnreadWrites.analyze(green.edit().build()));
        var run = cli.run("cpp", "--stage", "green", "--cpp-standard", "c++03").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(green), Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json = Files.readString(run.work().resolve("snapshot.json"));
        for (String key : List.of("handleEffects", "boundsChecked", "mutatedObjectIds", "resultKind", "producerId", "sequenceAfterId", "HANDLE_VALID", "HANDLE_SEQUENCE")) assertTrue(json.contains(key), key);
        String dot = com.kniazkov.cpg.visualization.DotVisualizer.toDot(green);
        assertTrue(dot.contains("HANDLE_SEQUENCE")); assertTrue(dot.contains("MUTATES"));
        assertEquals(Cpg.sources(green, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
        verifyGraph(Cpg.read(run.work().resolve("generated"), options(Cpg.Stage.GREEN)));
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        verifyGraph(Cpg.read(cli.input, options(Cpg.Stage.FOLDED)));
        // Render a compact representative graph; full graph is still checked above.
        // Dense full-fixture Graphviz layout is not a bounded CLI smoke test.
        var svgCli = new CliSandbox(Files.createDirectories(work.resolve("svg"))).directory(fixture.resolve("svg"));
        var svgRun = svgCli.run("cpp", "--stage", "green", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        String svg = Files.readString(svgRun.work().resolve("snapshot.svg"));
        assertTrue(svg.contains("HANDLE_SEQUENCE")); assertTrue(svg.contains("MUTATES"));
        return List.of(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), svgCli.input, svgRun.work().resolve("generated"));
    }
    private static Cpg.Options options(Cpg.Stage stage) { return new Cpg.Options(Language.CPP, stage, "c++03"); }
    private static void verifyGraph(Project p) {
        assertFalse(p.hasErrors(), () -> p.parseProblems() + " / " + p.semanticDiagnostics());
        var operations = p.dynamicArraySemantics();
        var kinds = EnumSet.noneOf(DynamicArrayOperation.Kind.class);
        operations.forEach(op -> kinds.add(op.kind()));
        for (var kind : List.of(DynamicArrayOperation.Kind.SUBSCRIPT, DynamicArrayOperation.Kind.FRONT,
                DynamicArrayOperation.Kind.BACK, DynamicArrayOperation.Kind.AT, DynamicArrayOperation.Kind.SIZE,
                DynamicArrayOperation.Kind.IS_EMPTY, DynamicArrayOperation.Kind.CAPACITY, DynamicArrayOperation.Kind.MAX_SIZE,
                DynamicArrayOperation.Kind.RESERVE, DynamicArrayOperation.Kind.RESIZE, DynamicArrayOperation.Kind.POP_BACK,
                DynamicArrayOperation.Kind.INSERT, DynamicArrayOperation.Kind.INSERT_FILL, DynamicArrayOperation.Kind.INSERT_RANGE,
                DynamicArrayOperation.Kind.ERASE, DynamicArrayOperation.Kind.ERASE_RANGE, DynamicArrayOperation.Kind.CLEAR,
                DynamicArrayOperation.Kind.BEGIN, DynamicArrayOperation.Kind.END, DynamicArrayOperation.Kind.RBEGIN,
                DynamicArrayOperation.Kind.REND, DynamicArrayOperation.Kind.SWAP)) assertTrue(kinds.contains(kind), kind.name());
        var queries = Set.of(DynamicArrayOperation.Kind.SIZE, DynamicArrayOperation.Kind.IS_EMPTY,
                DynamicArrayOperation.Kind.CAPACITY, DynamicArrayOperation.Kind.MAX_SIZE);
        for (var op : operations) {
            if (queries.contains(op.kind())) {
                assertEquals(DynamicArrayOperation.ResultKind.VALUE, op.resultKind());
                assertTrue(op.mutatedObjects().isEmpty(), op.kind().name());
                assertTrue(op.elementCopyConstructor().isEmpty());
            }
            if (op.kind() == DynamicArrayOperation.Kind.SUBSCRIPT) assertTrue(op.index().isPresent());
            if (op.kind() == DynamicArrayOperation.Kind.AT) assertTrue(op.boundsChecked());
        }
        assertTrue(operations.stream().anyMatch(op -> op.kind() == DynamicArrayOperation.Kind.FRONT && !op.writable()), "Const access stays read-only");
        var prefix = producer(p, "prefix");
        assertEquals(DynamicArrayOperation.ResultKind.REFERENCE, prefix.resultKind());
        assertTrue(prefix.writable());
        var insertion = operations.stream().filter(op -> op.kind() == DynamicArrayOperation.Kind.INSERT).findFirst().orElseThrow();
        assertTrue(insertion.handleEffects().stream().anyMatch(e -> e.producer() == prefix && e.validityAfter() == DynamicArrayOperation.Handle.Validity.VALID), "Reserved insertion preserves an earlier element reference");
        var swap = operations.stream().filter(op -> op.kind() == DynamicArrayOperation.Kind.SWAP).findFirst().orElseThrow();
        assertEquals(2, swap.mutatedObjects().size());
        var old = producerIn(p, "modifiers_and_iteration", "old_element");
        assertTrue(swap.handleEffects().stream().anyMatch(e -> e.producer() == old && e.validityAfter() == old.handle().orElseThrow().validity() && e.sequenceAfter().isPresent()), "Swap transfers old element storage association");
        var uncertain = producerIn(p, "potentially_invalid_reference", "old_element");
        assertTrue(operations.stream().filter(op -> op.kind() == DynamicArrayOperation.Kind.RESERVE)
                .flatMap(op -> op.handleEffects().stream()).anyMatch(e -> e.producer() == uncertain && e.validityAfter() != DynamicArrayOperation.Handle.Validity.VALID), "Unknown reallocation cannot promise reference validity");
        var flow = p.flows().stream().filter(f -> f.entry().name().equals("access_and_capacity")).findFirst().orElseThrow();
        assertTrue(flow.control().stream().anyMatch(e -> e.kind() == FunctionFlow.Branch.EXCEPTION), "Checked access retains exceptional control flow");
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>()); owned.addAll(nodes(p, Node.class));
        for (Node node : owned) for (Node ref : node.references()) assertTrue(owned.contains(ref), node.getClass().getSimpleName());
    }
    private static DynamicArrayOperation producer(Project p, String variable) { return producer(p, nodes(p, VariableDeclaration.class).stream().filter(v -> v.name().equals(variable)).findFirst().orElseThrow()); }
    private static DynamicArrayOperation producerIn(Project p, String function, String variable) {
        var callable = nodes(p, CallableDeclaration.class).stream().filter(c -> c.name().equals(function)).findFirst().orElseThrow();
        return producer(p, nodes(callable, VariableDeclaration.class).stream().filter(v -> v.name().equals(variable)).findFirst().orElseThrow());
    }
    private static DynamicArrayOperation producer(Project p, VariableDeclaration variable) {
        var syntax = nodes(variable.initializer().orElseThrow(), Node.class);
        return p.dynamicArraySemantics().stream().filter(op -> syntax.contains(op.syntax()) && op.handle().isPresent()).findFirst().orElseThrow();
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); } }); return result;
    }
}
