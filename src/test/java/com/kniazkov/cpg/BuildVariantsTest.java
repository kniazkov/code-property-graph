package com.kniazkov.cpg;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BuildVariantsTest {
    @TempDir Path temp;
    private static final Cpg.Options RESOLVED = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);
    private Build.TranslationUnit unit(String file, String macro) {
        return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve(file)), temp,
                List.of(), macro == null ? List.of() : List.of(new Build.MacroOperation(true, macro)), "c++03");
    }
    private static List<CallExpression> calls(Node node) {
        var calls = new ArrayList<CallExpression>();
        node.walk(new NodeVisitor() { @Override public void visit(CallExpression c) { calls.add(c); } });
        return calls;
    }
    @Test void retainsAlternativesAndResolvesExplicitCompositionsWithoutCrossVariantEdges() throws Exception {
        Files.writeString(temp.resolve("main.cpp"), "int run(); int main() { return run(); }");
        Files.writeString(temp.resolve("engine.cpp"), """
            #ifdef SERVER
            class Active { public: static void main() { int value = 11; } };
            int run() { Active::main(); return 11; }
            #else
            class Active { public: static void main() { int value = 22; } };
            int run() { Active::main(); return 22; }
            #endif
            """);
        var serverMain = unit("main.cpp", "SERVER"); var serverEngine = unit("engine.cpp", "SERVER");
        var clientMain = unit("main.cpp", "CLIENT"); var clientEngine = unit("engine.cpp", "CLIENT");
        var build = new Build(List.of(serverMain, serverEngine, clientMain, clientEngine));
        var unknown = Cpg.read(build, RESOLVED);
        assertEquals(2, unknown.variants(temp.resolve("main.cpp")).size());
        for (var file : unknown.files()) {
            var call = calls(file).getFirst();
            if (file.path().equals("main.cpp")) {
                assertEquals(CallExpression.Resolution.AMBIGUOUS, call.resolution());
                assertEquals(2, call.candidates().size()); assertTrue(call.target().isEmpty());
            } else {
                assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
                var owned = new HashSet<Node>(); file.walk(new NodeVisitor() {
                    @Override public void visit(Node n) { owned.add(n); }
                });
                assertTrue(owned.contains(call.target().orElseThrow()));
            }
        }
        String json = ProjectJson.encode(unknown).toText("");
        assertEquals(json, ProjectJson.encode(Cpg.read(build, RESOLVED)).toText(""));
        assertTrue(json.contains("candidateIds")); assertTrue(json.contains(serverEngine.variantId()));
        var combined = Cpg.sources(unknown, Language.CPP);
        assertEquals(Files.readString(temp.resolve("engine.cpp")), combined.get("engine.cpp"));
        assertEquals(2, combined.size());
        assertThrows(IllegalArgumentException.class, () -> unknown.source(Language.CPP));
        for (var pair : List.of(List.of(serverMain, serverEngine), List.of(clientMain, clientEngine))) {
            var composition = new Build.Composition(pair.getFirst().macros().getFirst().operand(),
                    pair.stream().map(Build.TranslationUnit::variantId).toList());
            Project selected = unknown.selectBuild(composition);
            assertEquals(composition, selected.composition().orElseThrow());
            assertEquals(2, selected.files().size());
            assertEquals(2, Cpg.sources(selected, Language.CPP).size());
            for (var call : calls(selected)) assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
            var run = calls(selected.files().getFirst()).getFirst().target().orElseThrow();
            assertEquals(pair.get(1).variantId(), run.provenance().orElseThrow().unit().configuration().variantId());
            assertEquals(selected.build(), selected.edit().build().build());
            assertEquals(selected.build(), selected.withFlows(selected.flows()).build());
            assertEquals(selected.build(), selected.withDiagnostics("test", Map.of(selected,
                    new Diagnostic("test", Diagnostic.Severity.INFO, "test"))).build());
            Project fresh = Cpg.read(new Build(build.units(), composition), RESOLVED);
            SnapshotAssertions.assertGraphEquals(ProjectJson.encode(selected), ProjectJson.encode(fresh));
            // Selecting a prior snapshot preserves its actual executions before reanalysis;
            // a fresh import reaches the same graph without that earlier variant coverage.
            int previousRuns = unknown.passRuns().size();
            assertEquals(previousRuns + fresh.passRuns().size(), selected.passRuns().size());
            assertEquals(unknown.passRuns().stream().map(PassRun::sequence).toList(),
                    selected.passRuns().subList(0, previousRuns).stream().map(PassRun::sequence).toList());
            assertEquals(fresh.passRuns().stream().map(PassRun::passId).toList(),
                    selected.passRuns().subList(previousRuns, selected.passRuns().size()).stream().map(PassRun::passId).toList());
            assertEquals(unknown.passRuns().getFirst().results().size(), selected.passRuns().getFirst().results().size());
            assertEquals(PassRun.Status.CURRENT, selected.passStatus("UnreadWrites").orElseThrow());
        }
        assertEquals(json, ProjectJson.encode(unknown).toText(""));
    }
    @Test void reportsKnownBuildOdrConflictRatherThanUnknownAmbiguity() throws Exception {
        Files.writeString(temp.resolve("main.cpp"), "int run(); int main() { return run(); }");
        Files.writeString(temp.resolve("one.cpp"), "int run() { return 1; }");
        Files.writeString(temp.resolve("two.cpp"), "int run() { return 2; }");
        var units = List.of(unit("main.cpp", null), unit("one.cpp", null), unit("two.cpp", null));
        var project = Cpg.read(new Build(units), RESOLVED);
        assertEquals(CallExpression.Resolution.AMBIGUOUS, calls(project).getFirst().resolution());
        var known = project.selectBuild(new Build.Composition("invalid", units.stream().map(Build.TranslationUnit::variantId).toList()));
        var call = calls(known).getFirst();
        assertEquals(CallExpression.Resolution.ODR_CONFLICT, call.resolution());
        assertEquals(2, call.candidates().size()); assertTrue(call.target().isEmpty());
        assertTrue(ProjectJson.encode(known).toText("").contains("odr_conflict"));
        assertTrue(call.diagnostics().stream().anyMatch(d -> d.code().equals("odr-conflict")));
        var folded = Cpg.read(new Build(units, known.composition().orElseThrow()),
                new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED));
        assertTrue(calls(folded).getFirst().diagnostics().stream().anyMatch(d -> d.code().equals("odr-conflict")));
        for (var candidate : call.candidates())
            assertTrue(candidate.diagnostics().stream().anyMatch(d -> d.code().equals("odr-conflict")));
    }
    @Test void duplicateDefinitionsInOneTuAreConflictsEvenWithoutComposition() throws Exception {
        Files.writeString(temp.resolve("main.cpp"),
                "int run() { return 1; } int run() { return 2; } int main() { return run(); }");
        var project = Cpg.read(new Build(List.of(unit("main.cpp", null))), RESOLVED);
        var call = calls(project).getFirst();
        assertTrue(project.composition().isEmpty());
        assertEquals(CallExpression.Resolution.ODR_CONFLICT, call.resolution());
        assertEquals(2, call.candidates().size());
        assertTrue(call.diagnostics().stream().anyMatch(d -> d.code().equals("odr-conflict")));
        for (var candidate : call.candidates())
            assertTrue(candidate.diagnostics().stream().anyMatch(d -> d.code().equals("odr-conflict")));
    }
    @Test void sharedInlineClassMethodsResolveWithinEachTranslationUnit() throws Exception {
        Files.writeString(temp.resolve("shared.hpp"), "class Active { public: static void main() { int value = 1; } }; ");
        Files.writeString(temp.resolve("one.cpp"), "#include \"shared.hpp\"\nint one() { Active::main(); return 1; }");
        Files.writeString(temp.resolve("two.cpp"), "#include \"shared.hpp\"\nint two() { Active::main(); return 2; }");
        var units = List.of(unit("one.cpp", null), unit("two.cpp", null));
        var composition = new Build.Composition("shared", units.stream().map(Build.TranslationUnit::variantId).toList());
        var project = Cpg.read(new Build(units, composition), RESOLVED);
        for (var file : project.files()) {
            var call = calls(file).getFirst();
            assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
            assertEquals(file.variantId().orElseThrow(),
                    call.target().orElseThrow().provenance().orElseThrow().unit().configuration().variantId());
            assertTrue(call.diagnostics().isEmpty());
        }
    }
    @Test void editionOverrideRemapsCompositionToEffectiveIdentities() throws Exception {
        Files.writeString(temp.resolve("main.cpp"), "int main() { return 0; }");
        var original = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")), temp,
                List.of(), List.of(), "c++11");
        var composition = new Build.Composition("override", List.of(original.variantId()));
        var project = Cpg.read(new Build(List.of(original), composition),
                new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        var effective = project.build().orElseThrow().units().getFirst();
        assertEquals("c++03", effective.standard());
        assertNotEquals(original.variantId(), effective.variantId());
        assertEquals(List.of(effective.variantId()), project.composition().orElseThrow().variantIds());
        assertEquals(effective.variantId(), project.files().getFirst().variantId().orElseThrow());
    }
    @Test void identitiesIncludeOrderedSettingsAndDuplicatesAreCollapsedDeterministically() throws Exception {
        Files.writeString(temp.resolve("same.cpp"), "int main() { return 0; }");
        var a = unit("same.cpp", "SERVER"); var b = unit("same.cpp", "CLIENT");
        assertNotEquals(a.variantId(), b.variantId());
        var reordered = new Build.TranslationUnit(a.source(), temp, List.of(),
                List.of(new Build.MacroOperation(false,"SERVER"), new Build.MacroOperation(true,"SERVER")), "c++03");
        assertNotEquals(a.variantId(), reordered.variantId());
        assertEquals(a.variantId(), unit("./same.cpp", "SERVER").variantId());
        var defaultStandard = new Build.TranslationUnit(a.source(), temp, a.includes(), a.macros(), null);
        assertEquals(a.variantId(), defaultStandard.variantId());
        assertEquals(1, new Build(List.of(a, defaultStandard)).units().size());
        assertEquals(2, new Build(List.of(a,b,a)).units().size());
        assertEquals(2, Cpg.read(new Build(List.of(a,b,a)), RESOLVED).files().size());
        var conflict = new Build.Composition("bad", List.of(a.variantId(), b.variantId()));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new Build(List.of(a,b), conflict))
                .getMessage().contains("Conflicting configurations"));
        assertThrows(IllegalArgumentException.class, () -> new Build(List.of(a), new Build.Composition("bad", List.of("missing"))));
        assertThrows(IllegalArgumentException.class, () -> new Build.Composition("bad", List.of(a.variantId(), a.variantId())));
    }
}
