/* Scoped pass history, immutable edits and native C++03 round trips. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class PassHistoryAcceptance {
    private PassHistoryAcceptance() { }
    private static final Set<String> DEPENDENCIES = Set.of("NameResolver", "ControlFlow", "DataFlow", "UnreadWrites");

    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var outputs = new ArrayList<Path>();
        outputs.add(cli.input);
        // The packaged process and API must expose the same complete history at every stage.
        for (Cpg.Stage stage : Cpg.Stage.values()) {
            var options = new Cpg.Options(Language.CPP, stage, "c++03");
            Project project = Cpg.read(cli.input, options);
            assertFalse(project.hasErrors());
            assertEquals(0, unread(project));
            var encoded = ProjectJson.encode(project);
            assertEquals(encoded, ProjectJson.encode(Cpg.read(cli.input, options)), "Repeat API imports must be deterministic");
            var run = cli.run("cpp", "--stage", stage.name().toLowerCase(Locale.ROOT), "--cpp-standard", "c++03").expectStatus(0);
            var cliJson = Json.parse(run.work().resolve("snapshot.json").toFile());
            SnapshotAssertions.assertEquivalent(encoded, cliJson);
            var repeat = cli.run("cpp", "--stage", stage.name().toLowerCase(Locale.ROOT), "--cpp-standard", "c++03").expectStatus(0);
            assertEquals(Files.readString(run.work().resolve("snapshot.json")),
                    Files.readString(repeat.work().resolve("snapshot.json")), "Repeat CLI imports must be byte-for-byte deterministic");
            var standalone = (Map<?, ?>) cliJson.toJavaObject();
            verifyJsonReferences(standalone);
            if (stage == Cpg.Stage.PARSED) assertTrue(runs(standalone, "UnreadWrites").isEmpty());
            else {
                assertJsonStatus(standalone, "UnreadWrites", "main", "current");
                assertJsonStatus(standalone, "UnreadWrites", "helper", "current");
            }
            for (var source : Cpg.sources(project, Language.CPP).entrySet())
                assertEquals(source.getValue(), Files.readString(run.work().resolve("generated").resolve(source.getKey())));
            outputs.add(run.work().resolve("generated"));
        }

        Project before = NameResolver.resolve(Cpg.read(cli.input, new Cpg.Options(Language.CPP, Cpg.Stage.PARSED, "c++03")));
        var beforeJson = ProjectJson.encode(before);
        var main = function(before, "main");
        var helper = function(before, "helper");
        assertEquals(0, unread(before));
        assertTrue(before.passStatus("UnreadWrites", main).isEmpty(), "No warnings does not prove that a pass ran");
        assertTrue(before.passStatus("UnreadWrites", helper).isEmpty());
        assertTrue(runs(json(before), "UnreadWrites").isEmpty());

        Project scoped = UnreadWrites.analyze(before, main);
        var scopedJson = ProjectJson.encode(scoped);
        assertEquals(0, unread(scoped));
        assertEquals(PassRun.Status.CURRENT, scoped.passStatus("UnreadWrites", function(scoped, "main")).orElseThrow());
        assertTrue(scoped.passStatus("UnreadWrites", function(scoped, "helper")).isEmpty(), "A main-only run must not claim to have checked helper");
        PassRun scopedRun = scoped.passRuns().getLast();
        assertEquals("UnreadWrites", scopedRun.passId());
        assertEquals(PassRun.Scope.FUNCTION, scopedRun.scope());
        assertEquals(List.of("main"), scopedRun.results().stream().map(PassRun.Result::name).toList());
        assertJsonStatus(json(scoped), "UnreadWrites", "main", "current");
        assertFalse(hasJsonResult(json(scoped), "UnreadWrites", "helper"));
        assertEquals(beforeJson, ProjectJson.encode(before));

        // A main-only run on a fully checked snapshot must retain helper's current result.
        Project helperChecked = UnreadWrites.analyze(scoped, function(scoped, "helper"));
        var helperCheckedJson = ProjectJson.encode(helperChecked);
        Project both = UnreadWrites.analyze(helperChecked, function(helperChecked, "main"));
        var bothJson = ProjectJson.encode(both);
        assertEquals(PassRun.Status.CURRENT, both.passStatus("UnreadWrites", function(both, "helper")).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, both.passStatus("UnreadWrites", function(both, "main")).orElseThrow());
        Project edited = insertUnusedAssignment(both);
        var editedJson = ProjectJson.encode(edited);
        assertFalse(edited.hasErrors());
        assertEquals(0, unread(edited), "The edited graph has not yet been checked for unread writes");
        assertEquals(PassRun.Status.STALE, edited.passStatus("UnreadWrites", function(edited, "main")).orElseThrow());
        assertEquals(PassRun.Status.STALE, edited.passStatus("DataFlow", function(edited, "main")).orElseThrow());
        // An edit may conservatively discard other functions' derived payload as well.
        // The scoped rerun must preserve their resulting status rather than refresh it.
        var helperStatus = edited.passStatus("UnreadWrites", function(edited, "helper")).orElseThrow();
        assertJsonStatus(json(edited), "UnreadWrites", "main", "stale");
        assertJsonStatus(json(edited), "UnreadWrites", "helper", helperStatus.name().toLowerCase(Locale.ROOT));
        assertJsonStatus(json(edited), "DataFlow", "main", "stale");
        assertEquals(bothJson, ProjectJson.encode(both), "Editing must not mark an ancestor's pass results stale");

        int priorRuns = edited.passRuns().size();
        // One public analysis call must notice and rebuild its stale dependencies.
        Project rebuilt = UnreadWrites.analyze(edited, function(edited, "main"));
        assertFalse(rebuilt.hasErrors());
        assertEquals(1, unread(rebuilt));
        var assignment = nodes(rebuilt, AssignmentExpression.class).getFirst();
        assertTrue(assignment.diagnostics().stream().anyMatch(d -> d.code().equals(UnreadWrites.CODE)));
        var flow = rebuilt.flows().stream().filter(f -> f.entry() == function(rebuilt, "main")).findFirst().orElseThrow();
        assertTrue(flow.data().stream().anyMatch(e -> e.to() == assignment && e.kind() == FunctionFlow.Dependency.VALUE),
                "Reanalysis must actually rebuild the edited assignment's DFG");
        assertEquals(PassRun.Status.CURRENT, rebuilt.passStatus("UnreadWrites", function(rebuilt, "main")).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, rebuilt.passStatus("DataFlow", function(rebuilt, "main")).orElseThrow());
        assertEquals(helperStatus, rebuilt.passStatus("UnreadWrites", function(rebuilt, "helper")).orElseThrow(),
                "Scoped analysis must not change the unselected helper's status");
        assertEquals(List.of("NameResolver", "ControlFlow", "DataFlow", "UnreadWrites"), rebuilt.passRuns().subList(priorRuns, rebuilt.passRuns().size())
                .stream().map(PassRun::passId).filter(DEPENDENCIES::contains).toList());
        verifyRebuiltJson(json(rebuilt), priorRuns, helperStatus);
        assertEquals(editedJson, ProjectJson.encode(edited));
        assertEquals(bothJson, ProjectJson.encode(both));
        assertEquals(helperCheckedJson, ProjectJson.encode(helperChecked));
        assertEquals(scopedJson, ProjectJson.encode(scoped));
        assertEquals(beforeJson, ProjectJson.encode(before));
        assertEquals(ProjectJson.encode(rebuilt), ProjectJson.encode(UnreadWrites.analyze(edited, function(edited, "main"))),
                "The same immutable edit and analysis must have deterministic history and graph IDs");

        Path editedSources = emit(rebuilt, work.resolve("edited-sources"));
        outputs.add(editedSources);
        assertTrue(Files.readString(editedSources.resolve("main.cpp")).contains("x = 2;"));
        var editedCli = new CliSandbox(Files.createDirectory(work.resolve("edited-cli"))).directory(editedSources);
        var editedApi = Cpg.read(editedCli.input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        var run = editedCli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(0);
        assertEquals(1, unread(editedApi));
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(editedApi), Json.parse(run.work().resolve("snapshot.json").toFile()));
        for (var source : Cpg.sources(editedApi, Language.CPP).entrySet())
            assertEquals(source.getValue(), Files.readString(run.work().resolve("generated").resolve(source.getKey())));
        outputs.add(run.work().resolve("generated"));
        assertEquals(Files.readString(fixture.resolve("sources/main.cpp")), Files.readString(cli.input.resolve("main.cpp")));
        return List.copyOf(outputs);
    }

    private static Project insertUnusedAssignment(Project project) {
        var main = function(project, "main");
        var body = main.body().orElseThrow();
        var x = nodes(main, VariableDeclaration.class).stream().filter(v -> v.name().equals("x")).findFirst().orElseThrow();
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        for (Node item : body.items()) {
            if (item instanceof ReturnStatement) items.add(ExpressionStatement.create().setExpression(AssignmentExpression.create()
                    .setTarget(NameExpression.create().setName("x").setTarget(x.edit())).setValue(IntegerLiteral.create().setValue(2))));
            items.add(item.edit());
        }
        var editor = new ProjectEditor(project);
        editor.replace(body, body.edit().setItems(items).build());
        return editor.build();
    }

    private static Path emit(Project project, Path output) throws Exception {
        Files.createDirectories(output);
        for (var entry : Cpg.sources(project, Language.CPP).entrySet()) {
            Path file = output.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
        }
        return output;
    }

    private static FunctionDeclaration function(Project project, String name) {
        return nodes(project, FunctionDeclaration.class).stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }
    private static long unread(Project project) {
        return project.semanticDiagnostics().stream().filter(d -> d.code().equals(UnreadWrites.CODE)).count();
    }
    private static Map<?, ?> json(Project project) {
        return (Map<?, ?>) ProjectJson.encode(project).toJavaObject();
    }
    private static List<Map<?, ?>> runs(Map<?, ?> root, String passId) {
        assertInstanceOf(List.class, root.get("passRuns"));
        return ((List<?>) root.get("passRuns")).stream().<Map<?, ?>>map(v -> (Map<?, ?>) v)
                .filter(r -> passId.equals(r.get("passId"))).toList();
    }
    private static boolean hasJsonResult(Map<?, ?> root, String passId, String name) {
        return runs(root, passId).stream().flatMap(r -> ((List<?>) r.get("results")).stream())
                .map(r -> (Map<?, ?>) r).anyMatch(r -> name.equals(r.get("name")));
    }
    private static void assertJsonStatus(Map<?, ?> root, String passId, String name, String status) {
        var results = runs(root, passId).stream().flatMap(r -> ((List<?>) r.get("results")).stream())
                .map(r -> (Map<?, ?>) r).filter(r -> name.equals(r.get("name"))).toList();
        assertFalse(results.isEmpty(), passId + " must expose its result for " + name);
        assertEquals(status, results.getLast().get("status"), passId + " status for " + name);
    }
    private static void verifyRebuiltJson(Map<?, ?> root, int priorRuns, PassRun.Status helperStatus) {
        var history = (List<?>) root.get("passRuns");
        assertEquals(List.of("NameResolver", "ControlFlow", "DataFlow", "UnreadWrites"), history.subList(priorRuns, history.size()).stream()
                .map(r -> ((Map<?, ?>) r).get("passId")).filter(DEPENDENCIES::contains).toList());
        long sequence = -1;
        for (Object item : history) {
            var run = (Map<?, ?>) item;
            long next = ((Number) run.get("sequence")).longValue();
            assertTrue(next > sequence, "JSON must preserve ordered run identities");
            sequence = next;
        }
        var last = (Map<?, ?>) history.getLast();
        assertEquals("UnreadWrites", last.get("passId"));
        assertEquals("function", last.get("scope"));
        assertEquals("current", last.get("status"));
        assertJsonStatus(root, "UnreadWrites", "main", "current");
        assertJsonStatus(root, "UnreadWrites", "helper", helperStatus.name().toLowerCase(Locale.ROOT));
        assertTrue(runs(root, "UnreadWrites").stream().anyMatch(r -> "stale".equals(r.get("status"))),
                "Historical stale executions must remain visible after rebuilding");
        verifyJsonReferences(root);
    }
    private static void verifyJsonReferences(Map<?, ?> root) {
        var ids = new LinkedHashMap<Integer, Map<?, ?>>();
        collect(root, ids);
        for (Object item : (List<?>) root.get("passRuns")) {
            var run = (Map<?, ?>) item;
            for (Object value : (List<?>) run.get("results")) {
                var result = (Map<?, ?>) value;
                int id = ((Number) result.get("functionId")).intValue();
                assertTrue(ids.containsKey(id), "Pass result must refer to a defined current function");
                assertEquals(result.get("name"), ids.get(id).get("name"));
            }
        }
    }
    private static void collect(Object value, Map<Integer, Map<?, ?>> ids) {
        if (value instanceof List<?> list) { list.forEach(v -> collect(v, ids)); return; }
        if (!(value instanceof Map<?, ?> map)) return;
        if (map.get("id") instanceof Number id && map.get("kind") instanceof String)
            assertNull(ids.put(id.intValue(), map), "Each graph ID must have exactly one definition");
        map.values().forEach(v -> collect(v, ids));
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
}
