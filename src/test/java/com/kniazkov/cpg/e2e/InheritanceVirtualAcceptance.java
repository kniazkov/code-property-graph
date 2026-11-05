/* C++03 virtual declarations, default arguments and diamond inheritance. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.InheritanceSemantics;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class InheritanceVirtualAcceptance {
    private InheritanceVirtualAcceptance() {}
    record Output(Path original, Path generated, Path folded, Path invalid) {}
    static Output verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var options = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03");
        Project project = Cpg.read(cli.input, options);
        verifyGraph(project); verifyGraph(project.edit().build());
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json = Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("virtualTargetIds")); assertTrue(json.contains("overridesIds"));
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
        return new Output(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), invalid.input);
    }
    private static void verifyGraph(Project project) {
        assertFalse(project.hasErrors());
        var all = nodes(project, Node.class);
        assertTrue(all.stream().flatMap(n -> n.diagnostics().stream()).noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR), () -> all.stream().flatMap(n -> n.diagnostics().stream()).toList().toString());
        Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>()); owned.addAll(all);
        for (Node node : all) for (Node reference : node.references()) assertTrue(owned.contains(reference), node + " -> " + reference);
        var base = method(project, "Base", "run", 1);
        var derived = method(project, "Derived", "run", 1);
        var reset = method(project, "Device", "reset", 0);
        var printer = method(project, "Printer", "reset", 0);
        var scanner = method(project, "Scanner", "reset", 0);
        assertEquals(Set.of(printer, scanner), new HashSet<>(InheritanceSemantics.overrides(project, reset)));
        assertTrue(InheritanceSemantics.overriddenBy(project, printer).contains(reset));
        assertTrue(InheritanceSemantics.overriddenBy(project, scanner).contains(reset));
        assertTrue(InheritanceSemantics.overrides(project, method(project,"Device","reset",1)).isEmpty());
        assertTrue(method(project,"Pure","value",0).isPureVirtual());
        var throughBase = callWithTarget(project, base);
        var throughDerived = callWithTarget(project, derived);
        assertTrue(throughBase.isVirtualDispatch()); assertTrue(throughDerived.isVirtualDispatch());
        assertTrue(throughBase.virtualTargets().contains(implementation(derived))); assertTrue(throughDerived.virtualTargets().contains(implementation(derived)));
        assertFalse(throughBase.virtualTargetsComplete(), "Known project classes do not close the C++ world");
        assertEquals(1, throughBase.defaultArguments().size());
        assertSame(base, throughBase.defaultArguments().getFirst().declaration());
        assertEquals(1, assertInstanceOf(IntegerLiteral.class, throughBase.defaultArguments().getFirst().expression()).value());
        assertSame(derived, throughDerived.defaultArguments().getFirst().declaration());
        assertEquals(2, assertInstanceOf(IntegerLiteral.class, throughDerived.defaultArguments().getFirst().expression()).value());
        assertTrue(throughBase.arguments().isEmpty()); assertTrue(throughDerived.arguments().isEmpty());
        assertTrue(callWithTarget(project, printer).virtualTargets().contains(reset));
        assertTrue(callWithTarget(project, scanner).virtualTargets().contains(reset));
        var pure = method(project, "Pure", "value", 0);
        assertFalse(InheritanceSemantics.possibleTargets(project, pure, null).contains(implementation(pure)));
        var constructionMethod = method(project, "ConstructionBase", "run", 0);
        var constructionCalls = nodes(project, CallExpression.class).stream()
                .filter(c -> c.callee() instanceof NameExpression n && n.name().equals("run") && c.target().orElse(null)==constructionMethod).toList();
        assertEquals(2, constructionCalls.size());
        for (var call : constructionCalls) assertEquals(List.of(constructionMethod), call.virtualTargets(), "Construction/destruction cannot dispatch to more-derived implementation");
        var qualified = nodes(project, CallExpression.class).stream().filter(c -> c.callee() instanceof ScopeMemberExpression && c.target().orElse(null)==base).findFirst().orElseThrow();
        assertFalse(qualified.isVirtualDispatch());
        assertSame(base, qualified.defaultArguments().getFirst().declaration());
        for (var member : nodes(project, MemberExpression.class)) if (member.member().equals("x")) assertTrue(member.target().isPresent(), "Shared virtual base field must resolve");
    }
    private static CallableDeclaration method(Project project, String owner, String name, int arity) {
        return nodes(project,ClassDeclaration.class).stream().filter(c -> c.name().equals(owner)).findFirst().orElseThrow().members().stream()
                .filter(m -> m instanceof CallableDeclaration c && c.name().equals(name) && c.parameters().size()==arity)
                .map(CallableDeclaration.class::cast).findFirst().orElseThrow();
    }
    private static CallableDeclaration implementation(CallableDeclaration declaration) {
        return declaration.definition().orElse(null) instanceof CallableDeclaration definition ? definition : declaration;
    }
    private static CallExpression callWithTarget(Project project, CallableDeclaration target) {
        return nodes(project,CallExpression.class).stream().filter(c -> c.target().orElse(null)==target || c.target().orElse(null)==implementation(target)).findFirst().orElseThrow();
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (kind.isInstance(node)) result.add(kind.cast(node)); } });
        return result;
    }
}
