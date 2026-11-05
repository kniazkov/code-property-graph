/* Genuine C++03 container families and observable ordering. MIT License. */
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
final class ContainerFamiliesAcceptance {
    private ContainerFamiliesAcceptance() { }
    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var green = Cpg.read(cli.input, options(Cpg.Stage.GREEN));
        verifyGraph(green);
        verifyGraph(UnreadWrites.analyze(green.edit().build()));
        var run = cli.run("cpp", "--stage", "green", "--cpp-standard", "c++03").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(green), Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json = Files.readString(run.work().resolve("snapshot.json"));
        for (String key : List.of("containerTypes", "containerSemantics", "comparatorId", "keyTypeId", "mappedTypeId", "sourceContainerId", "transferKind", "preservesTransferredReferences", "mayInsert", "uniqueKeys", "iteratorCategory")) assertTrue(json.contains(key), key);
        String dot = DotVisualizer.toDot(green);
        for (String edge : List.of("COMPARATOR_TARGET", "SOURCE_CONTAINER", "CONTAINER_TYPE", "KEY_TYPE")) assertTrue(dot.contains(edge), edge);
        assertEquals(Cpg.sources(green, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
        verifyGraph(Cpg.read(run.work().resolve("generated"), options(Cpg.Stage.GREEN)));
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        verifyGraph(Cpg.read(cli.input, options(Cpg.Stage.FOLDED)));
        var svgCli = new CliSandbox(Files.createDirectories(work.resolve("svg"))).directory(fixture.resolve("svg"));
        var svgRun = svgCli.run("cpp", "--stage", "green", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        String svg = Files.readString(svgRun.work().resolve("snapshot.svg"));
        assertTrue(svg.contains("SOURCE_CONTAINER")); assertTrue(svg.contains("MUTATES"));
        return List.of(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), svgCli.input, svgRun.work().resolve("generated"));
    }
    private static Cpg.Options options(Cpg.Stage stage) { return new Cpg.Options(Language.CPP, stage, "c++03"); }
    private static void verifyGraph(Project p) {
        assertFalse(p.hasErrors(), () -> p.parseProblems() + " / " + p.semanticDiagnostics());
        var kinds = EnumSet.noneOf(ContainerType.Kind.class); p.containerTypes().forEach(type -> kinds.add(type.kind()));
        assertEquals(EnumSet.allOf(ContainerType.Kind.class), kinds);
        for (var type : p.containerTypes()) {
            switch (type.kind()) {
                case DEQUE -> assertEquals(ContainerType.IteratorCategory.RANDOM_ACCESS, type.iteratorCategory());
                case LIST, SET, MULTISET, MAP, MULTIMAP -> assertEquals(ContainerType.IteratorCategory.BIDIRECTIONAL, type.iteratorCategory());
                default -> assertEquals(ContainerType.IteratorCategory.NONE, type.iteratorCategory());
            }
            if (type.associative()) {
                assertTrue(type.keyType().isPresent()); assertTrue(type.comparatorType().isPresent());
                assertEquals(type.kind() == ContainerType.Kind.SET || type.kind() == ContainerType.Kind.MAP, type.uniqueKeys());
            }
            if (type.kind() == ContainerType.Kind.MAP || type.kind() == ContainerType.Kind.MULTIMAP) assertTrue(type.mappedType().isPresent());
        }
        for (var member : nodes(p.files().getFirst(), PointerMemberExpression.class))
            assertTrue(member.target().isPresent(), "Resolved iterator arrow member: " + member.member());
        assertTrue(p.semanticDiagnostics().stream().noneMatch(d -> d.code().equals("unread-write")
                && d.message().contains("mapped_reference")), "Mapped reference writes are subsequently read");
        var operations = p.containerSemantics();
        for (String name : List.of("sequences", "associative", "adapters", "main")) {
            var flow = p.flows().stream().filter(f -> f.entry().name().equals(name)).findFirst().orElseThrow();
            assertTrue(flow.complete(), "Complete native function flow: " + name);
        }
        var finds = operations.stream().filter(op -> op.kind() == ContainerOperation.Kind.FIND).toList();
        assertFalse(finds.isEmpty());
        for (var find : finds) { assertFalse(find.mayInsert()); assertTrue(find.mutatedObjects().isEmpty()); assertEquals(ContainerOperation.ResultKind.ITERATOR, find.resultKind()); }
        var mapAccess = operations.stream().filter(op -> op.kind() == ContainerOperation.Kind.SUBSCRIPT && op.type().kind() == ContainerType.Kind.MAP).findFirst().orElseThrow();
        assertTrue(mapAccess.mayInsert()); assertFalse(mapAccess.mutatedObjects().isEmpty()); assertTrue(mapAccess.writable());
        var splices = operations.stream().filter(op -> op.kind() == ContainerOperation.Kind.SPLICE).toList();
        assertEquals(2, splices.size());
        assertEquals(Set.of(ContainerOperation.TransferKind.ALL, ContainerOperation.TransferKind.ONE), new HashSet<>(splices.stream().map(ContainerOperation::transferKind).toList()));
        for (var splice : splices) { assertTrue(splice.preservesTransferredReferences()); assertTrue(splice.sourceContainer().isPresent()); assertEquals(2, splice.mutatedObjects().size()); }
        var moved = nodes(p.files().getFirst(), VariableDeclaration.class).stream().filter(v -> v.name().equals("moved")).findFirst().orElseThrow();
        var producer = operations.stream().filter(op -> nodes(moved.initializer().orElseThrow(), Node.class).contains(op.syntax()) && op.handle().isPresent()).findFirst().orElseThrow();
        var one = splices.stream().filter(op -> op.transferKind() == ContainerOperation.TransferKind.ONE).findFirst().orElseThrow();
        assertTrue(one.handleEffects().stream().anyMatch(e -> e.producer() == producer && e.sequenceAfter().isPresent()), "Transferred element iterator retains its storage identity");
        var write = nodes(p.files().getFirst(), AssignmentExpression.class).stream()
                .filter(a -> a.target() instanceof NameExpression n && n.name().equals("saved")).findFirst().orElseThrow();
        assertTrue(p.flows().stream().flatMap(f -> f.data().stream()).anyMatch(e -> e.from() == write
                && e.to() instanceof ContainerOperation op && op.kind() == ContainerOperation.Kind.BACK
                && op.destination() instanceof NameExpression n && n.name().equals("destination")),
                "Write through transferred reference reaches the destination sequence");
        var comparisons = operations.stream().filter(op -> op.comparator().isPresent()).toList();
        assertTrue(comparisons.stream().anyMatch(op -> op.type().kind() == ContainerType.Kind.MAP));
        assertTrue(comparisons.stream().anyMatch(op -> op.type().kind() == ContainerType.Kind.PRIORITY_QUEUE));
        for (var operation : comparisons) {
            assertEquals("operator()", operation.comparator().orElseThrow().name());
            assertTrue(operation.comparator().orElseThrow().body().isPresent(), "User comparator implementation is linked");
        }
        for (var kind : List.of(ContainerType.Kind.STACK, ContainerType.Kind.QUEUE, ContainerType.Kind.PRIORITY_QUEUE)) {
            assertTrue(operations.stream().anyMatch(op -> op.type().kind() == kind && op.kind() == ContainerOperation.Kind.PUSH));
            assertTrue(operations.stream().anyMatch(op -> op.type().kind() == kind && op.kind() == ContainerOperation.Kind.POP));
        }
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>()); owned.addAll(nodes(p, Node.class));
        for (Node node : owned) for (Node ref : node.references()) assertTrue(owned.contains(ref), node.getClass().getSimpleName());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); } }); return result;
    }
}
