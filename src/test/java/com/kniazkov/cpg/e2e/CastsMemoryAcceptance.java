/* C++03 sizes, casts and allocation through all public paths. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class CastsMemoryAcceptance {
    private CastsMemoryAcceptance() {}
    record Output(Path original, Path generated, Path folded, Path invalid) {}
    static Output verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var options = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03");
        Project project = Cpg.read(cli.input, options);
        verifyGraph(project); verifyGraph(project.edit().build());
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
        verifyGraph(Cpg.read(run.work().resolve("generated"), options));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        var invalid = new CliSandbox(Files.createDirectory(work.resolve("invalid"))).directory(fixture.resolve("invalid"));
        var failed = invalid.run("cpp", "--stage", "resolved", "--svg", "snapshot.svg").expectStatus(1);
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.json")));
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.svg")));
        assertFalse(Files.exists(failed.work().resolve("generated")));
        var invalidFold = new CliSandbox(Files.createDirectory(work.resolve("invalid-fold")));
        Files.writeString(invalidFold.input.resolve("main.cpp"), "struct S { unsigned b:3; }; int main(){ S s; return false && sizeof(s.b); }");
        var foldFailure = invalidFold.run("cpp", "--stage", "folded", "--svg", "snapshot.svg").expectStatus(1);
        assertTrue(Files.readString(foldFailure.work().resolve("snapshot.json")).contains("invalid-sizeof"));
        assertFalse(Files.exists(foldFailure.work().resolve("generated")), "Folding cannot erase semantic errors in an unevaluated branch");
        return new Output(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), invalid.input);
    }
    private static void verifyGraph(Project project) {
        assertFalse(project.hasErrors());
        var all = nodes(project, Node.class);
        assertTrue(all.stream().flatMap(n -> n.diagnostics().stream()).noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR), () -> all.stream().flatMap(n -> n.diagnostics().stream()).toList().toString());
        Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>()); owned.addAll(all);
        for (Node node : all) for (Node reference : node.references()) assertTrue(owned.contains(reference));
        assertEquals(3, nodes(project, NewExpression.class).size());
        assertEquals(2, nodes(project, DeleteExpression.class).size());
        assertEquals(2, nodes(project, TypeidExpression.class).size());
        var kinds = nodes(project, ConversionExpression.class).stream().filter(ConversionExpression::isExplicit).map(ConversionExpression::kind).collect(java.util.stream.Collectors.toSet());
        assertTrue(kinds.containsAll(Set.of(ConversionExpression.Kind.STATIC_CAST, ConversionExpression.Kind.DYNAMIC_CAST, ConversionExpression.Kind.CONST_CAST, ConversionExpression.Kind.REINTERPRET_CAST, ConversionExpression.Kind.C_STYLE_CAST)), kinds.toString());
        var buffer = nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals("buffer")).findFirst().orElseThrow();
        var bound = assertInstanceOf(ArrayType.class, buffer.type().orElseThrow()).bound().orElseThrow();
        assertFalse(nodes(bound, SizeofExpression.class).isEmpty(), "symbolic sizeof must survive resolution and copy");
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (kind.isInstance(node)) result.add(kind.cast(node)); } });
        return result;
    }
}
