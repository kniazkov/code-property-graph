/* Code Property Graph: shared CLI/API acceptance for alternative translation units. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class BuildVariantsAcceptance {
    private BuildVariantsAcceptance() {}
    record Output(String macro, Path original, Path generated) {}
    static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);

    static List<Output> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        Path db = cli.input.resolve("compile_commands.json");
        var all = Cpg.readCompilationDatabase(db, OPTIONS);
        assertEquals(4, all.files().size());
        assertEquals(2, all.variants(cli.input.resolve("sources/engine.cpp")).size());
        assertEquals(2, all.variants(cli.input.resolve("sources/main.cpp")).size());
        assertEquals(4, all.files().stream().map(f -> f.variantId().orElseThrow()).distinct().count());
        assertEquals(ProjectJson.encode(all), ProjectJson.encode(Cpg.readCompilationDatabase(db, OPTIONS)));
        var unknown = cli.runDatabaseJson(db, "--stage", "resolved").expectStatus(0);
        assertEquals(ProjectJson.encode(all), Json.parse(unknown.work().resolve("snapshot.json").toFile()));
        var sharedSources = Cpg.sources(all, Language.CPP);
        var sharedRun = cli.runDatabase(db, "--stage", "resolved").expectStatus(0);
        for (String name : List.of("main.cpp", "engine.cpp")) {
            assertEquals(Files.readString(cli.input.resolve("sources").resolve(name)), sharedSources.get(name));
            assertEquals(sharedSources.get(name), Files.readString(sharedRun.work().resolve("generated").resolve(name)));
        }
        var owners = owners(all);
        for (SourceFile file : all.files()) {
            for (CallExpression call : calls(file)) {
                if (call.callee() instanceof NameExpression name && name.name().equals("run")) {
                    assertEquals("AMBIGUOUS", call.resolution().name());
                    assertTrue(call.target().isEmpty());
                    assertEquals(2, call.candidates().size());
                    assertEquals(2, call.candidates().stream().map(owners::get).distinct().count());
                } else if (call.callee() instanceof MemberExpression) {
                    assertEquals("RESOLVED", call.resolution().name());
                    assertSame(file, owners.get(call.target().orElseThrow()));
                }
            }
        }
        assertIsolatedFlows(all);
        var outputs = new ArrayList<Output>();
        for (String macro : List.of("SERVER", "CLIENT")) {
            var selectedFiles = all.files().stream().filter(f -> f.provenance().orElseThrow().unit()
                    .configuration().macros().stream().anyMatch(m -> m.define() && m.operand().equals(macro))).toList();
            var composition = new Build.Composition(macro.toLowerCase(Locale.ROOT),
                    selectedFiles.stream().map(f -> f.variantId().orElseThrow()).toList());
            var selected = all.selectBuild(composition);
            assertEquals(2, selected.files().size());
            assertEquals(composition, selected.composition().orElseThrow());
            assertEquals(composition, selected.edit().build().composition().orElseThrow());
            var selectedOwners = owners(selected);
            var engine = selected.variants(cli.input.resolve("sources/engine.cpp")).getFirst();
            for (var call : calls(selected)) {
                assertEquals("RESOLVED", call.resolution().name());
                assertSame(engine, selectedOwners.get(call.target().orElseThrow()));
            }
            assertIsolatedFlows(selected);
            Path emitted = Files.createDirectory(work.resolve(macro.toLowerCase(Locale.ROOT)));
            Path sources = Files.createDirectory(emitted.resolve("sources"));
            for (var entry : Cpg.sources(selected, Language.CPP).entrySet()) {
                Path target = sources.resolve(entry.getKey());
                Files.createDirectories(target.getParent()); Files.writeString(target, entry.getValue());
            }
            var entries = (List<?>) Json.parse(db.toFile()).toJavaObject();
            var filtered = entries.stream().filter(e -> ((List<?>) ((Map<?, ?>) e).get("arguments")).contains("-D" + macro)).toList();
            Files.writeString(emitted.resolve("compile_commands.json"), Json.serialize(filtered));
            var check = new CliSandbox(Files.createDirectory(work.resolve(macro + "-cli"))).directory(emitted);
            var reparsed = check.runDatabaseJson(check.input.resolve("compile_commands.json"), "--stage", "resolved").expectStatus(0);
            reparsed.snapshot().containsNode("{\"kind\":\"CallExpression\",\"callee\":{\"name\":\"run\"}}");
            outputs.add(new Output(macro, cli.input.resolve("sources"), sources));
        }
        assertEquals(4, all.files().size());
        assertTrue(all.composition().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> all.selectBuild(new Build.Composition("missing", List.of("unknown-id"))));
        assertThrows(IllegalArgumentException.class, () -> all.selectBuild(new Build.Composition("conflicting", all
                .variants(cli.input.resolve("sources/engine.cpp")).stream().map(f -> f.variantId().orElseThrow()).toList())));
        return outputs;
    }

    private static Map<Node, SourceFile> owners(Project project) {
        var owners = new IdentityHashMap<Node, SourceFile>();
        for (var file : project.files()) file.walk(new NodeVisitor() { @Override public void visit(Node n) { owners.put(n, file); } });
        return owners;
    }
    static List<CallExpression> calls(Node node) {
        var calls = new ArrayList<CallExpression>();
        node.walk(new NodeVisitor() { @Override public void visit(CallExpression c) { calls.add(c); } });
        return calls;
    }
    private static void assertIsolatedFlows(Project project) {
        var owners = owners(project);
        for (var flow : project.flows()) {
            var owner = owners.get(flow.entry());
            for (var edge : flow.control()) {
                assertSame(owner, owners.get((Node) edge.from())); assertSame(owner, owners.get((Node) edge.to()));
            }
            for (var edge : flow.data()) {
                assertSame(owner, owners.get(edge.from())); assertSame(owner, owners.get(edge.to()));
            }
        }
    }
}
