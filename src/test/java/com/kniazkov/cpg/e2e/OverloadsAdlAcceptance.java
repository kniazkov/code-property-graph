/* C++03 overloads and user-defined operator effects through public paths. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class OverloadsAdlAcceptance {
    private OverloadsAdlAcceptance() {}
    record Output(Path original, Path generated, Path folded, Path invalid) {}
    static Output verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        Path profilePath = fixture.resolve("long32.json").toAbsolutePath();
        var profile = PlatformProfile.read(profilePath);
        var options = new Cpg.Options(Language.CPP, Cpg.Stage.GREEN, "c++03").withPlatform(profile);
        Project project = Cpg.read(cli.input, options);
        verifyGraph(project); verifyGraph(project.edit().build());
        var run = cli.run("cpp", "--stage", "green", "--cpp-standard", "c++03", "--platform-profile", profilePath.toString(), "--svg", "snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json = Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("operatorTargetId"));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        String generated = Files.readString(run.work().resolve("generated/main.cpp"));
        assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), generated);
        assertTrue(generated.contains("1L"), generated);
        assertTrue(generated.contains("price + tax"), generated);
        verifyGraph(Cpg.read(run.work().resolve("generated"), options));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03", "--platform-profile", profilePath.toString()).expectStatus(0);
        String foldedSource = Files.readString(folded.work().resolve("generated/main.cpp"));
        assertTrue(foldedSource.contains("price + tax"), foldedSource);
        assertTrue(foldedSource.contains("makeMoney(0) && finance::makeMoney(1)"), foldedSource);
        var invalid = new CliSandbox(Files.createDirectory(work.resolve("invalid"))).directory(fixture.resolve("invalid"));
        var failed = invalid.run("cpp", "--stage", "green", "--svg", "snapshot.svg").expectStatus(1);
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.json")));
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.svg")));
        assertFalse(Files.exists(failed.work().resolve("generated")));
        return new Output(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), invalid.input);
    }
    private static void verifyGraph(Project project) {
        assertFalse(project.hasErrors());
        var all = nodes(project, Node.class);
        assertTrue(all.stream().flatMap(n -> n.diagnostics().stream()).noneMatch(d -> d.severity()==Diagnostic.Severity.ERROR), () -> all.stream().flatMap(n -> n.diagnostics().stream()).toList().toString());
        Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>()); owned.addAll(all);
        for (Node node : all) for (Node reference : node.references()) assertTrue(owned.contains(reference), node + " -> " + reference);
        var calls = nodes(project,CallExpression.class).stream().filter(c -> c.callee() instanceof NameExpression n && n.name().equals("f")).toList();
        assertEquals(3,calls.size());
        var targets = calls.stream().map(c -> c.target().orElseThrow()).toList();
        assertNotSame(targets.get(0),targets.get(1));
        assertSame(targets.get(1),targets.get(2), "Long-valued arithmetic must retain nominal overload identity");
        var first = targets.get(0).parameters().getFirst().type().orElseThrow();
        var second = targets.get(1).parameters().getFirst().type().orElseThrow();
        assertEquals("int",first.canonicalIdentity()); assertEquals("long",second.canonicalIdentity());
        assertEquals(Optional.of(BigInteger.valueOf(32)),first.semanticType().orElseThrow().sizeBits());
        assertEquals(Optional.of(BigInteger.valueOf(32)),second.semanticType().orElseThrow().sizeBits());
        var total = nodes(project,VariableDeclaration.class).stream().filter(v -> v.name().equals("total")).findFirst().orElseThrow();
        Expression initializer=total.initializer().orElseThrow();
        while(initializer instanceof ConversionExpression conversion && !conversion.isExplicit()) initializer=conversion.operand();
        var plus=assertInstanceOf(AddExpression.class,initializer);
        assertEquals(CallExpression.Resolution.RESOLVED,plus.operatorResolution());
        assertEquals("operator+",plus.operatorTarget().orElseThrow().name());
        assertTrue(plus.operatorTarget().orElseThrow().body().isPresent());
        var shade = nodes(project,VariableDeclaration.class).stream().filter(v -> v.name().equals("shade")).findFirst().orElseThrow();
        var enumPlus = assertInstanceOf(AddExpression.class,shade.initializer().orElseThrow());
        assertEquals("operator+",enumPlus.operatorTarget().orElseThrow().name());
        assertNotSame(plus.operatorTarget().orElseThrow(),enumPlus.operatorTarget().orElseThrow());
        var logical=nodes(project,LogicalAndExpression.class).stream().filter(e -> e.operatorTarget().isPresent()).toList();
        assertEquals(1,logical.size()); assertEquals("operator&&",logical.getFirst().operatorTarget().orElseThrow().name());
        var tagged=nodes(project,CallExpression.class).stream().filter(c -> c.callee() instanceof NameExpression n && n.name().equals("tagged")).findFirst().orElseThrow();
        assertEquals("tagged",tagged.target().orElseThrow().name());
    }
    private static <T extends Node> List<T> nodes(Node root,Class<T> kind) {
        var result=new ArrayList<T>();
        root.walk(new NodeVisitor(){@Override public void visit(Node node){if(kind.isInstance(node))result.add(kind.cast(node));}});
        return result;
    }
}
