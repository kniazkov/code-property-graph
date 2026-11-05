/* Genuine C++03 stream state and preserved opaque library effects. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.LibraryDeclarations;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class RemainingLibraryAcceptance {
    private RemainingLibraryAcceptance() { }
    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var green = Cpg.read(cli.input, options(Cpg.Stage.GREEN));
        verifyGraph(green);
        verifyGraph(UnreadWrites.analyze(green.edit().build()));
        var run = cli.run("cpp", "--stage", "green", "--cpp-standard", "c++03").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(green), Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json = Files.readString(run.work().resolve("snapshot.json"));
        for (String key : List.of("standardLibrary", "LIBRARY_HEADER", "CONSTRUCTOR_TARGET", "dfg.definition", "operatorTargetId")) assertTrue(json.contains(key), key);
        String dot = DotVisualizer.toDot(green);
        assertTrue(dot.contains("operator implementation")); assertTrue(dot.contains("DFG def"));
        assertEquals(Cpg.sources(green, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
        verifyGraph(Cpg.read(run.work().resolve("generated"), options(Cpg.Stage.GREEN)));
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        verifyGraph(Cpg.read(cli.input, options(Cpg.Stage.FOLDED)));
        var svgCli = new CliSandbox(Files.createDirectories(work.resolve("svg"))).directory(fixture.resolve("svg"));
        var svgRun = svgCli.run("cpp", "--stage", "green", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        String svg = Files.readString(svgRun.work().resolve("snapshot.svg"));
        assertTrue(svg.contains("operator implementation")); assertTrue(svg.contains("DFG def"));
        return List.of(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), svgCli.input, svgRun.work().resolve("generated"));
    }
    private static Cpg.Options options(Cpg.Stage stage) { return new Cpg.Options(Language.CPP, stage, "c++03"); }
    private static void verifyGraph(Project p) {
        assertFalse(p.hasErrors(), () -> p.parseProblems() + " / " + p.semanticDiagnostics());
        assertTrue(p.standardLibrary().isPresent());
        assertTrue(nodes(p.standardLibrary().orElseThrow(), CallableDeclaration.class).stream().allMatch(f -> f.body().isEmpty()));
        var file = p.files().getFirst();
        assertTrue(nodes(file, PrintStatement.class).isEmpty(), "String streams are not console output");
        for (var call : nodes(file, CallExpression.class)) assertTrue(call.target().isPresent(), "Selected call: " + call.callee());
        var shifts = nodes(file, Expression.class).stream().filter(e -> e instanceof ShiftLeftExpression || e instanceof ShiftRightExpression).toList();
        assertFalse(shifts.isEmpty());
        for (var shift : shifts) {
            assertTrue(shift.operatorTarget().isPresent(), "Selected stream operator");
            assertTrue(LibraryDeclarations.isLibrary(shift.operatorTarget().orElseThrow()));
        }
        for (String name : List.of("stream", "output", "source", "destination", "formatted", "wide")) {
            var variable = nodes(file, VariableDeclaration.class).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
            assertTrue(variable.constructorTarget().isPresent(), "Selected constructor: " + name);
        }
        for (String name : List.of("stream_state", "stream_iterators", "formatted_streams", "main"))
            assertTrue(p.flows().stream().filter(f -> f.entry().name().equals(name)).findFirst().orElseThrow().complete(), "Complete native flow: " + name);
        var first = nodes(file, VariableDeclaration.class).stream().filter(v -> v.name().equals("first")).findFirst().orElseThrow();
        assertTrue(p.flows().stream().flatMap(f -> f.data().stream()).anyMatch(e -> e.from() instanceof ShiftRightExpression
                && e.to() instanceof NameExpression n && n.target().orElse(null) == first && e.kind() == FunctionFlow.Dependency.DEFINITION),
                "Formatted extraction may define the observed output variable");
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>()); owned.addAll(nodes(p, Node.class));
        for (Node node : owned) for (Node ref : node.references()) assertTrue(owned.contains(ref), node.getClass().getSimpleName());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); } }); return result;
    }
}
