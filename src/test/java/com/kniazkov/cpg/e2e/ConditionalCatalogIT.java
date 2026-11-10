/* Code Property Graph: branch catalogue, presence checks and owned external origins. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalCatalogIT {
    @TempDir Path temp;
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);
    private static final Path FIXTURE = Path.of("src/test/resources/cpp03/conditional-catalog");

    @Test void catalogueBranchesAndExternalOwnersReachApiAndPackagedCli() throws Exception {
        var cli = new CliSandbox(temp).directory(FIXTURE);
        Path database = cli.input.resolve("compile_commands.json");
        var project = Cpg.readCompilationDatabase(database, OPTIONS);
        var run = cli.runDatabase(database, "--stage", "resolved").expectStatus(0);
        assertEquals(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        var file = project.files().stream().filter(f -> f.path().equals("main.cpp")).findFirst().orElseThrow();
        var p = file.provenance().orElseThrow().unit();
        var retry = definition(p, "RETRY_LIMIT");
        assertEquals("3", retry.rawReplacement());
        assertFalse(retry.functionLike());
        assertTrue(retry.range().file().endsWith("options.h"));
        assertTrue(p.expansions().stream().noneMatch(e -> e.definitionId() == retry.id()));
        var function = definition(p, "UNUSED_FUNCTION");
        assertTrue(function.functionLike()); assertEquals(List.of("x"), function.parameters());
        var switched = p.expansions().stream().filter(e -> e.name().equals("SWITCH_VALUE")).toList();
        assertEquals(2, switched.size()); assertNotEquals(switched.get(0).definitionId(), switched.get(1).definitionId());
        assertEquals(List.of("1", "2"), switched.stream().map(e -> p.definitions().stream()
                .filter(d -> d.id() == e.definitionId()).findFirst().orElseThrow().rawReplacement()).toList());
        var debug = p.presenceChecks().stream().filter(c -> c.name().equals("DEBUG")).toList();
        assertEquals(1, debug.size()); assertTrue(debug.getFirst().present()); assertTrue(debug.getFirst().conditionResult());
        assertEquals(definition(p, "DEBUG").id(), debug.getFirst().definitionId());
        assertTrue(p.expansions().stream().noneMatch(e -> e.name().equals("DEBUG")));
        assertTrue(p.presenceChecks().stream().noneMatch(c -> c.name().equals("SHOULD_NOT_CHECK")));
        assertTrue(p.includes().stream().noneMatch(i -> i.file().contains("deliberately-missing")));
        var inactive = p.branches().stream().filter(b -> b.rawSource().contains("int broken = 1 + ;")).findFirst().orElseThrow();
        assertFalse(inactive.active());
        assertTrue(p.directives().stream().anyMatch(d -> d.kind().equals("pragma")));
        var names = new ArrayList<String>();
        project.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration v) { names.add(v.name()); } });
        assertFalse(names.contains("broken"));
        assertTrue(p.logicalLocations().stream().anyMatch(l -> l.file().equals("logical.cpp") && l.line() == 120));
        var lineExpansion = p.expansions().stream().filter(e -> e.name().equals("__LINE__")).findFirst().orElseThrow();
        assertNotEquals(120, lineExpansion.invocation().start().line());
        var header = p.includes().stream().filter(i -> i.file().endsWith("options.h")).findFirst().orElseThrow();
        assertEquals(retry.id(), p.includeStates().stream().filter(s -> s.includeId() == header.id())
                .findFirst().orElseThrow().activeDefinitions().get("RETRY_LIMIT"));
        var root = p.includes().stream().filter(i -> i.parentId() == null).findFirst().orElseThrow();
        assertEquals(switched.get(1).definitionId(), p.includeStates().stream().filter(s -> s.includeId() == root.id())
                .findFirst().orElseThrow().activeDefinitions().get("SWITCH_VALUE"));
        assertOwner(project, definition(p, "DEBUG"), "-DDEBUG=0",
                List.of("compilation-database", "compilation-command", "macro-definition-argument"));
        assertOwner(project, definition(p, "UNUSED_EXTERNAL"), "-DUNUSED_EXTERNAL=9",
                List.of("compilation-database", "compilation-command", "macro-definition-argument"));
        var builtin = definition(p, "__cplusplus");
        assertOwner(project, builtin, null, List.of("language-profile", "predefined-macro"));
        assertTrue(p.expansions().stream().anyMatch(e -> e.definitionId() == builtin.id()));
        var aux = project.files().stream().filter(f -> f.path().equals("auxiliary.cpp")).findFirst().orElseThrow().provenance().orElseThrow().unit();
        assertTrue(aux.definitions().stream().noneMatch(d -> d.name().equals("DEBUG")));
        assertOwner(project, definition(aux, "OTHER_UNUSED"), "-DOTHER_UNUSED=5",
                List.of("compilation-database", "compilation-command", "macro-definition-argument"));
        var generated = Cpg.sources(project, Language.CPP);
        assertEquals(Files.readString(cli.input.resolve("sources/main.cpp")), generated.get("main.cpp"));
        assertEquals(Files.readString(cli.input.resolve("sources/include/options.h")), generated.get("include/options.h"));
        var json = Files.readString(run.work().resolve("snapshot.json"));
        for (String field : List.of("definitions", "presenceChecks", "branches", "sourceNodeId", "externalInputs"))
            assertTrue(json.contains("\"" + field + "\""));
    }

    @Test void undefinedAndDefinedZeroArePresenceChecksNotValueExpansions() throws Exception {
        Path file = temp.resolve("main.cpp");
        Files.writeString(file, "#ifdef DEBUG\nint main() { return 1; }\n#else\nint main() { return 0; }\n#endif\n");
        for (boolean defined : List.of(false, true)) {
            var unit = new Build.TranslationUnit(new Build.PhysicalSource(file), temp, List.of(),
                    defined ? List.of(new Build.MacroOperation(true, "DEBUG=0")) : List.of(), "c++03");
            var project = Cpg.read(new Build(List.of(unit)), OPTIONS);
            var p = project.files().getFirst().provenance().orElseThrow().unit();
            assertEquals(1, p.presenceChecks().size()); assertEquals(0, p.expansions().size());
            var check = p.presenceChecks().getFirst();
            assertEquals(defined, check.present()); assertEquals(defined, check.conditionResult());
            assertEquals(defined, check.definitionId() != null);
            if (defined) assertTrue(project.externalInput(definition(p, "DEBUG").sourceKey()).isPresent());
        }
    }

    @Test void partialExpressionBranchesJoinTheSharedSuffixInBothConfigurations() throws Exception {
        var cli = new CliSandbox(temp).directory(FIXTURE);
        Path main = cli.input.resolve("sources/main.cpp");
        for (boolean defined : List.of(false, true)) {
            var unit = new Build.TranslationUnit(new Build.PhysicalSource(main), main.getParent(), List.of(),
                    defined ? List.of(new Build.MacroOperation(true, "DEBUG=0")) : List.of(), "c++03");
            var project = Cpg.read(new Build(List.of(unit)), OPTIONS);
            var chosen = new ArrayList<VariableDeclaration>();
            project.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration v) {
                if (v.name().equals("chosen")) chosen.add(v);
            } });
            assertEquals(1, chosen.size());
            var sum = assertInstanceOf(AddExpression.class, chosen.getFirst().initializer().orElseThrow());
            assertEquals(defined ? 10 : 20, assertInstanceOf(IntegerLiteral.class, sum.left()).value());
            assertEquals(1, assertInstanceOf(IntegerLiteral.class, sum.right()).value());
            assertEquals(Files.readString(main), Cpg.sources(project, Language.CPP).get("main.cpp"));
        }
    }

    @Test void inactiveBrokenSyntaxFailsOnlyWhenItsBranchBecomesActive() throws Exception {
        var cli = new CliSandbox(temp).directory(FIXTURE);
        Path main = cli.input.resolve("sources/main.cpp");
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(main), main.getParent(), List.of(),
                List.of(new Build.MacroOperation(true, "BROKEN=1")), "c++03");
        assertThrows(com.kniazkov.cpg.parser.ParseException.class, () -> Cpg.read(new Build(List.of(unit)), OPTIONS));
        Files.writeString(main, "#if 1\nint main() { return 0; }\n");
        var run = cli.runDatabase(cli.input.resolve("compile_commands.json"), "--stage", "resolved").expectStatus(1);
        run.diagnosticSnapshot();
        assertTrue(run.stderr().contains("conditional") || run.stderr().contains("endif"));
    }

    private static Preprocessing.Definition definition(Preprocessing p, String name) {
        return p.definitions().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }
    private static void assertOwner(Project project, Preprocessing.Definition definition, String text, List<String> roles) {
        var target = project.externalInput(definition.sourceKey()).orElseThrow();
        if (text != null) assertEquals(text, target.text());
        var path = new ArrayList<Node>();
        assertTrue(path(project, target, path));
        assertEquals(roles, path.stream().filter(ExternalInput.class::isInstance).map(ExternalInput.class::cast).map(ExternalInput::role).toList());
        var seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        project.walk(new NodeVisitor() { @Override public void visit(Node n) { seen.add(n); } });
        assertTrue(seen.contains(target));
    }
    private static boolean path(Node node, Node target, List<Node> result) {
        result.add(node);
        if (node == target) return true;
        for (var child : node.children()) if (path(child, target, result)) return true;
        result.removeLast(); return false;
    }
}
