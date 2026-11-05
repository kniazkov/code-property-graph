/* Code Property Graph: declaration scopes and linkage across configured units. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ScopesLinkageAcceptance {
    private ScopesLinkageAcceptance() {}
    record Output(Path original, Path generated, Path incomplete) {}
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);

    static Output verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        Path database = cli.input.resolve("compile_commands.json");
        Project project = Cpg.readCompilationDatabase(database, OPTIONS);
        assertFalse(project.hasErrors());
        assertEquals(3, project.files().size());
        var run = cli.runDatabase(database, "--stage", "resolved").expectStatus(0);
        assertEquals(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertFalse(run.stderr().contains("error ["), run.stderr());
        Map<String, String> sources = Cpg.sources(project, Language.CPP);
        for (var entry : sources.entrySet())
            assertEquals(entry.getValue(), Files.readString(run.work().resolve("generated").resolve(entry.getKey())));
        assertEquals(Files.readString(cli.input.resolve("sources/include/config.h")), sources.get("include/config.h"));
        var selected = project.selectBuild(new Build.Composition("acceptance",
                project.files().stream().map(f -> f.variantId().orElseThrow()).toList()));
        assertFalse(selected.hasErrors());
        assertEquals(3, selected.files().size());
        verifyLinks(project);
        verifyLinks(selected);
        Path generated = run.work().resolve("generated");
        var generatedUnits = project.files().stream().map(file -> {
            var original = file.provenance().orElseThrow().unit().configuration();
            Path source = generated.resolve(original.source().path().getFileName().toString());
            return new Build.TranslationUnit(new Build.PhysicalSource(source), generated, List.of(), original.macros(),
                    original.standard(), Build.ConfigurationSource.javaApi(), original.platform());
        }).toList();
        Project reparsed = Cpg.read(new Build(generatedUnits), OPTIONS);
        assertFalse(reparsed.hasErrors());
        verifyLinks(reparsed);
        verifyLinks(project.edit().build());
        ProjectEditor edits = new ProjectEditor(project);
        for (var variable : nodes(project, VariableDeclaration.class))
            if (variable.name().equals("sample"))
                edits.replace(variable, variable.edit().setName("renamed").build());
        verifyLinks(edits.build());
        Path incomplete = verifyIncomplete(fixture.resolve("incomplete"), Files.createDirectory(work.resolve("incomplete")));
        return new Output(cli.input.resolve("sources"), generated, incomplete);
    }

    private static Path verifyIncomplete(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        Project project = Cpg.read(cli.input, OPTIONS);
        assertFalse(project.hasErrors(), "The source parses; completeness is a semantic failure");
        var errors = nodes(project, Node.class).stream().flatMap(n -> n.diagnostics().stream())
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR && d.code().equals("incomplete-type")).toList();
        assertEquals(1, errors.size());
        var sizes = nodes(project, SizeofExpression.class);
        assertEquals(2, sizes.size());
        var incomplete = assertInstanceOf(NamedType.class, sizes.getFirst().type().orElseThrow());
        assertFalse(incomplete.completeAtUse());
        var visible = assertInstanceOf(ClassDeclaration.class, incomplete.declaration().orElseThrow());
        assertFalse(visible.isDefinition());
        assertTrue(visible.definition().orElseThrow().isDefinition(), "Later definition cannot retroactively complete the earlier use");
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
        var run = cli.run("cpp", "--stage", "resolved", "--svg", "snapshot.svg").expectStatus(1);
        assertTrue(run.stderr().contains("incomplete-type"), run.stderr());
        assertFalse(Files.exists(run.work().resolve("generated")));
        assertTrue(Files.isRegularFile(run.work().resolve("snapshot.json")));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        com.kniazkov.cpg.SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),
                Json.parse(run.work().resolve("snapshot.json").toFile()));
        return cli.input;
    }

    static void verifyLinks(Project project) {
        var errors = nodes(project, Node.class).stream().flatMap(n -> n.diagnostics().stream())
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
        assertTrue(errors.isEmpty(), () -> "Positive composition has semantic errors: " + errors);
        Map<Node, SourceFile> owners = new IdentityHashMap<>();
        for (var file : project.files()) file.walk(new NodeVisitor() {
            @Override public void visit(Node n) { owners.put(n, file); }
        });
        project.walk(new NodeVisitor() { @Override public void visit(Node n) {
            for (Node reference : n.references()) assertTrue(owners.containsKey(reference), "Reference belongs to this snapshot");
        } });
        for (var file : project.files()) {
            for (var call : nodes(file, CallExpression.class)) {
                if (!(call.callee() instanceof NameExpression name)) continue;
                if (Set.of("helper", "hidden").contains(name.name())) {
                    assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
                    assertSame(file, owners.get(call.target().orElseThrow()), "Internal linkage cannot cross TUs");
                }
            }
        }
        for (var declaration : nodes(project, Declaration.class))
            if (declaration instanceof CallableDeclaration callable && callable.name().equals("helper")
                    || declaration instanceof VariableDeclaration variable && variable.name().equals("offset"))
                assertEquals(Declaration.Linkage.INTERNAL, declaration.linkage(),
                        "Block redeclarations inherit the visible file-static linkage");
        var widths = nodes(project, TypedefDeclaration.class).stream().filter(d -> d.name().equals("Width")).toList();
        assertEquals(2, widths.size());
        assertEquals(widths.get(0).range(), widths.get(1).range());
        assertNotSame(owners.get(widths.get(0)), owners.get(widths.get(1)));
        assertEquals(Set.of("int", "long"), widths.stream().map(d -> d.type().canonicalIdentity())
                .collect(java.util.stream.Collectors.toSet()));
        for (var file : project.files()) for (var type : nodes(file, NamedType.class))
            if (type.name().equals("Width")) assertSame(file, owners.get(type.alias().orElseThrow()));
        SourceFile main = project.files().stream().filter(f -> f.path().endsWith("main.cpp")).findFirst().orElseThrow();
        var directives = nodes(main, UsingDirective.class).stream().filter(d -> d.namespaceName().equals("shop")).toList();
        assertEquals(1, directives.size(), "One shared using directive, not one synthetic node per use");
        UsingDirective directive = directives.getFirst();
        assertEquals("shop", directive.target().orElseThrow().name());
        NamedType id = named(main, "id"), count = named(main, "count");
        for (NamedType type : List.of(id, count)) {
            var steps = type.resolutionSteps().stream().filter(step -> step.kind().equals("using-directive")).toList();
            assertEquals(1, steps.size());
            assertSame(directive, steps.getFirst().via());
            assertSame(type.alias().orElseThrow(), steps.getFirst().target());
        }
        assertEquals("OrderId", id.name());
        assertEquals("RawId", assertInstanceOf(NamedType.class, id.alias().orElseThrow().type()).name());
        assertEquals("long", id.canonicalIdentity());
        assertEquals("int", count.canonicalIdentity());
        assertTrue(id.resolutionSteps().stream().anyMatch(step -> step.kind().equals("typedef")));
        NamedType qualified = named(main, "qualified");
        assertEquals("shop::OrderId", qualified.name());
        assertEquals("long", qualified.canonicalIdentity());
        assertFalse(qualified.resolutionSteps().stream().anyMatch(step -> step.kind().startsWith("using")));
        assertTrue(named(main, "via_alias").resolutionSteps().stream().anyMatch(step -> step.kind().equals("namespace-alias")));
        assertTrue(named(main, "imported").resolutionSteps().stream().anyMatch(step -> step.kind().equals("using-declaration")));
        var orders = nodes(main, ClassDeclaration.class).stream().filter(c -> c.name().equals("Order")).toList();
        assertEquals(2, orders.size());
        var forward = orders.stream().filter(c -> !c.isDefinition()).findFirst().orElseThrow();
        var definition = orders.stream().filter(ClassDeclaration::isDefinition).findFirst().orElseThrow();
        assertSame(definition, forward.definition().orElseThrow());
        assertSame(forward, definition.previousDeclaration().orElseThrow());
        NamedType before = assertInstanceOf(NamedType.class,
                assertInstanceOf(PointerType.class, variable(main, "current").type().orElseThrow()).pointee());
        NamedType after = assertInstanceOf(NamedType.class,
                assertInstanceOf(PointerType.class, variable(main, "later").type().orElseThrow()).pointee());
        assertSame(forward, before.declaration().orElseThrow());
        assertFalse(before.completeAtUse());
        assertTrue(after.completeAtUse());
        Declaration afterDeclaration = after.declaration().orElseThrow();
        assertSame(definition, afterDeclaration.isDefinition() ? afterDeclaration : afterDeclaration.definition().orElseThrow());
        for (var call : nodes(main, CallExpression.class))
            assertEquals(CallExpression.Resolution.RESOLVED, call.resolution(), "External declarations find their definitions");
        var cApi = nodes(main, FunctionDeclaration.class).stream().filter(f -> f.name().equals("c_api")).toList();
        assertEquals(2, cApi.size());
        for (var function : cApi) {
            assertEquals("C", function.languageLinkage());
            assertEquals(Declaration.Linkage.EXTERNAL, function.linkage());
        }
    }

    private static VariableDeclaration variable(SourceFile file, String name) {
        return file.items().stream().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast)
                .filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }
    private static NamedType named(SourceFile file, String name) {
        return assertInstanceOf(NamedType.class, variable(file, name).type().orElseThrow());
    }

    static <T> List<T> nodes(Node root, Class<T> kind) {
        List<T> result = new ArrayList<>();
        root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (kind.isInstance(n)) result.add(kind.cast(n)); } });
        return result;
    }
}
