/* C++03 classes through API, CLI, copying, native execution and regeneration. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class ClassesInitializationAcceptance {
    private ClassesInitializationAcceptance() {}
    record Output(Path original, Path generated, Path folded, Path invalid) {}
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03");
    static Output verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        Project project = Cpg.read(cli.input, OPTIONS);
        verifyGraph(project);
        verifyGraph(DataFlow.analyze(project.edit().build()));
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        verifyGraph(Cpg.read(run.work().resolve("generated"), OPTIONS));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        var bad = new CliSandbox(Files.createDirectory(work.resolve("invalid"))).directory(fixture.resolve("invalid"));
        var rejected = bad.run("cpp", "--stage", "resolved", "--svg", "snapshot.svg").expectStatus(1);
        assertTrue(Files.isRegularFile(rejected.work().resolve("snapshot.json")));
        assertFalse(Files.exists(rejected.work().resolve("generated")));
        return new Output(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), bad.input);
    }
    private static void verifyGraph(Project project) {
        assertFalse(project.hasErrors());
        var all = nodes(project, Node.class);
        assertTrue(all.stream().flatMap(n -> n.diagnostics().stream()).noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR));
        var ordered = nodes(project, ClassDeclaration.class).stream().filter(c -> c.name().equals("Ordered")).findFirst().orElseThrow();
        var constructor = ordered.members().stream().filter(MethodDeclaration.class::isInstance).map(MethodDeclaration.class::cast)
                .filter(c -> c.kind() == CallableDeclaration.Kind.CONSTRUCTOR).findFirst().orElseThrow();
        assertEquals(List.of("second", "first", "Base"), constructor.initializers().stream().map(MemberInitializer::name).toList());
        var sequence = ClassSemantics.constructionOrder(ordered, constructor);
        assertEquals(List.of("Base", "first", "second"), sequence.stream().map(s -> s.writtenInitializer().orElseThrow().name()).toList());
        var flow = project.flows().stream().filter(f -> f.entry() == constructor).findFirst().orElseThrow();
        assertTrue(flow.control().stream().anyMatch(edge -> edge.from() == constructor && edge.to() == constructor.initializers().get(2)));
        assertTrue(flow.control().stream().anyMatch(edge -> edge.from() == constructor.initializers().get(2) && edge.to() == constructor.initializers().get(1)));
        var aggregate = nodes(project, ClassDeclaration.class).stream().filter(c -> c.name().equals("Aggregate")).findFirst().orElseThrow();
        assertTrue(ClassSemantics.aggregate(aggregate));
        assertTrue(ClassSemantics.specialMembers(aggregate).stream().allMatch(member -> member.origin() == ClassSemantics.Origin.IMPLICIT));
        var lifetime = ClassSemantics.lifetimes(project);
        assertTrue(lifetime.stream().anyMatch(l -> l.end() == ClassSemantics.LifetimeEnd.REFERENCE_LIFETIME));
        assertTrue(lifetime.stream().anyMatch(l -> l.object() instanceof VariableDeclaration v && v.name().equals("once") && l.end() == ClassSemantics.LifetimeEnd.PROGRAM_EXIT));
        Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>()); owned.addAll(all);
        for (Node node : all) for (Node reference : node.references()) assertTrue(owned.contains(reference));
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }}); return result;
    }
}
