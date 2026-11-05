/* Code Property Graph: written/default arguments through API and packaged CLI. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class FunctionsDefaultsAcceptance {
    private FunctionsDefaultsAcceptance() {}
    record Output(Path original, Path generated, Path folded, Path invalid) {}
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03");
    static Output verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        Project project = Cpg.read(cli.input, OPTIONS);
        verifyGraph(project);
        Project copied = project.edit().build();
        assertTrue(copied.flows().isEmpty(), "Flow products are intentionally rebuilt after an edit");
        verifyGraph(com.kniazkov.cpg.analysis.DataFlow.analyze(copied));
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        String generated = Files.readString(run.work().resolve("generated/main.cpp"));
        assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), generated);
        assertEquals(2, occurrences(generated, "log()"), "Omitted calls stay omitted instead of expanding next()");
        assertEquals(1, occurrences(generated, "log(2)"));
        verifyGraph(Cpg.read(run.work().resolve("generated"), OPTIONS));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        var calls = calls(project, "log");
        Expression oldDefault = calls.getFirst().defaultArguments().getFirst().expression();
        var editor = new ProjectEditor(project);
        editor.replace(oldDefault, IntegerLiteral.create().setValue(8).build());
        Project edited = editor.build();
        var changed = calls(edited, "log");
        assertSame(changed.getFirst().defaultArguments().getFirst().expression(), changed.getLast().defaultArguments().getFirst().expression());
        assertEquals(8, assertInstanceOf(IntegerLiteral.class, changed.getFirst().defaultArguments().getFirst().expression()).value());
        assertInstanceOf(CallExpression.class, oldDefault);
        assertFalse(Cpg.sources(edited, Language.CPP).isEmpty());
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        var bad = new CliSandbox(Files.createDirectory(work.resolve("invalid"))).directory(fixture.resolve("invalid"));
        Project invalid = Cpg.read(bad.input, OPTIONS);
        assertFalse(invalid.hasErrors(), "The missing argument is a semantic error, not a parse failure");
        assertTrue(nodes(invalid, Node.class).stream().flatMap(n -> n.diagnostics().stream()).anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(invalid, Language.CPP));
        var failed = bad.run("cpp", "--stage", "resolved", "--svg", "snapshot.svg").expectStatus(1);
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.json")));
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.svg")));
        assertFalse(Files.exists(failed.work().resolve("generated")));
        return new Output(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), bad.input);
    }
    private static void verifyGraph(Project project) {
        assertFalse(project.hasErrors());
        var all = nodes(project, Node.class);
        assertTrue(all.stream().flatMap(n -> n.diagnostics().stream()).noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR));
        Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>()); owned.addAll(all);
        for (Node node : all) for (Node reference : node.references()) assertTrue(owned.contains(reference));
        var log = calls(project, "log"); assertEquals(3, log.size());
        assertEquals(List.of(0, 1, 0), log.stream().map(c -> c.arguments().size()).toList());
        assertEquals(List.of(1, 0, 1), log.stream().map(c -> c.defaultArguments().size()).toList());
        assertEquals(2, assertInstanceOf(IntegerLiteral.class, written(log.get(1).arguments().getFirst())).value());
        var a = log.getFirst().defaultArguments().getFirst();
        var b = log.getLast().defaultArguments().getFirst();
        assertEquals(0, a.parameterIndex()); assertSame(a.parameter(), b.parameter());
        assertSame(a.expression(), b.expression()); assertSame(a.expression(), a.parameter().defaultValue().orElseThrow());
        assertSame(a.parameter(), a.declaration().parameters().getFirst());
        assertFalse(a.declaration().isDefinition(), "The prototype supplies the default, not the later implementation");
        assertTrue(log.getFirst().references().contains(a.expression()));
        assertFalse(log.getFirst().children().contains(a.expression()));
        assertEquals(1, calls(project, "next").size(), "Visitor sees one written default call, not one clone per omitted use");
        assertSame(a.expression(), calls(project, "next").getFirst());
        var mainFlow = project.flows().stream().filter(flow -> flow.entry().name().equals("main")).findFirst().orElseThrow();
        for (var call : List.of(log.getFirst(), log.getLast()))
            assertTrue(mainFlow.data().stream().anyMatch(edge -> edge.from() == a.expression() && edge.to() == call
                    && edge.kind() == FunctionFlow.Dependency.VALUE), "Default expression is evaluated at each omitted call");

        for (var call : log) {
            assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
            assertTrue(call.target().orElseThrow().isDefinition());
        }
        var sum = calls(project, "sum"); assertEquals(3, sum.size());
        assertEquals(List.of(2, 1, 0), sum.stream().map(c -> c.defaultArguments().size()).toList());
        var supplied = sum.getFirst().defaultArguments();
        assertEquals(List.of(0, 1), supplied.stream().map(CallExpression.DefaultArgument::parameterIndex).toList());
        assertNotSame(supplied.get(0).declaration(), supplied.get(1).declaration(), "Defaults accumulate across visible prototypes");
        assertSame(supplied.get(1).expression(), sum.get(1).defaultArguments().getFirst().expression());
    }
    private static Expression written(Expression expression) {
        while (expression instanceof ConversionExpression c && !c.isExplicit()) expression = c.operand();
        return expression;
    }
    private static List<CallExpression> calls(Node root, String name) {
        return nodes(root, CallExpression.class).stream().filter(c -> written(c.callee()) instanceof NameExpression n && n.name().equals(name)).toList();
    }
    private static int occurrences(String source, String text) { return (source.length() - source.replace(text, "").length()) / text.length(); }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        List<T> result = new ArrayList<>(); root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); } }); return result;
    }
}
