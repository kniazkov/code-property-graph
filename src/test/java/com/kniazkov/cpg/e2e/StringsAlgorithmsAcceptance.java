/* Genuine C++03 character ranges and algorithm effects. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class StringsAlgorithmsAcceptance {
    private StringsAlgorithmsAcceptance() { }
    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var green = Cpg.read(cli.input, options(Cpg.Stage.GREEN));
        verifyGraph(green);
        verifyGraph(UnreadWrites.analyze(green.edit().build()));
        var run = cli.run("cpp", "--stage", "green", "--cpp-standard", "c++03").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(green), Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json = Files.readString(run.work().resolve("snapshot.json"));
        for (String key : List.of("characterSequenceTypes", "sequenceSemantics", "callableTargetId", "accumulatorTypeId", "outputDestinationId", "lengthKind", "sizeEffect")) assertTrue(json.contains(key), key);
        String dot = DotVisualizer.toDot(green);
        for (String edge : List.of("CALLABLE_TARGET", "OUTPUT_DESTINATION", "ACCUMULATOR_TYPE", "RANGE_LAST")) assertTrue(dot.contains(edge), edge);
        assertEquals(Cpg.sources(green, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
        verifyGraph(Cpg.read(run.work().resolve("generated"), options(Cpg.Stage.GREEN)));
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        verifyGraph(Cpg.read(cli.input, options(Cpg.Stage.FOLDED)));
        var svgCli = new CliSandbox(Files.createDirectories(work.resolve("svg"))).directory(fixture.resolve("svg"));
        var svgRun = svgCli.run("cpp", "--stage", "green", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        String svg = Files.readString(svgRun.work().resolve("snapshot.svg"));
        assertTrue(svg.contains("OUTPUT_DESTINATION")); assertTrue(svg.contains("MUTATES"));
        return List.of(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), svgCli.input, svgRun.work().resolve("generated"));
    }
    private static Cpg.Options options(Cpg.Stage stage) { return new Cpg.Options(Language.CPP, stage, "c++03"); }
    private static void verifyGraph(Project p) {
        assertFalse(p.hasErrors(), () -> p.parseProblems() + " / " + p.semanticDiagnostics());
        assertFalse(p.characterSequenceTypes().isEmpty());
        var operations = p.sequenceSemantics();
        for (var kind : List.of(SequenceOperation.Kind.STRING_BUFFER, SequenceOperation.Kind.STRING_SUBSCRIPT,
                SequenceOperation.Kind.STRING_AT, SequenceOperation.Kind.STRING_FIND, SequenceOperation.Kind.STRING_SUBSTR,
                SequenceOperation.Kind.STRING_APPEND, SequenceOperation.Kind.STRING_SIZE, SequenceOperation.Kind.COPY,
                SequenceOperation.Kind.BACK_INSERTER, SequenceOperation.Kind.ADVANCE, SequenceOperation.Kind.DISTANCE,
                SequenceOperation.Kind.FIND_IF, SequenceOperation.Kind.TRANSFORM, SequenceOperation.Kind.SORT,
                SequenceOperation.Kind.REMOVE, SequenceOperation.Kind.ACCUMULATE))
            assertTrue(operations.stream().anyMatch(op -> op.kind() == kind), "Normalized " + kind);
        var buffer = operations.stream().filter(op -> op.kind() == SequenceOperation.Kind.STRING_BUFFER).findFirst().orElseThrow();
        assertEquals(SequenceOperation.LengthKind.EXPLICIT_COUNT, buffer.lengthKind()); assertTrue(buffer.count().isPresent());
        var copy = operations.stream().filter(op -> op.kind() == SequenceOperation.Kind.COPY).findFirst().orElseThrow();
        assertTrue(copy.first().isPresent()); assertTrue(copy.last().isPresent());
        assertTrue(copy.outputDestination().isPresent()); assertFalse(copy.mutatedObjects().isEmpty());
        var remove = operations.stream().filter(op -> op.kind() == SequenceOperation.Kind.REMOVE).findFirst().orElseThrow();
        assertEquals(SequenceOperation.SizeEffect.UNCHANGED, remove.sizeEffect());
        assertEquals(SequenceOperation.ResultKind.ITERATOR, remove.resultKind()); assertFalse(remove.mutatedObjects().isEmpty());
        var sums = operations.stream().filter(op -> op.kind() == SequenceOperation.Kind.ACCUMULATE).toList();
        assertEquals(2, sums.size());
        for (var sum : sums) {
            assertTrue(sum.initialValue().isPresent()); assertTrue(sum.accumulatorType().isPresent()); assertTrue(sum.resultType().isPresent());
            assertTrue(sum.accumulatorType().orElseThrow().canonicalIdentity().contains("long"));
        }
        for (var kind : List.of(SequenceOperation.Kind.FIND_IF, SequenceOperation.Kind.TRANSFORM, SequenceOperation.Kind.SORT)) {
            var op = operations.stream().filter(o -> o.kind() == kind).findFirst().orElseThrow();
            assertTrue(op.callableTarget().isPresent(), "Callable target for " + kind);
            assertTrue(op.callableTarget().orElseThrow().body().isPresent());
        }
        assertTrue(sums.stream().anyMatch(op -> op.callableTarget().isPresent()));
        for (String name : List.of("strings", "algorithms", "main"))
            assertTrue(p.flows().stream().filter(f -> f.entry().name().equals(name)).findFirst().orElseThrow().complete(), "Complete native flow: " + name);
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>()); owned.addAll(nodes(p, Node.class));
        for (Node node : owned) for (Node ref : node.references()) assertTrue(owned.contains(ref), node.getClass().getSimpleName());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); } }); return result;
    }
}
