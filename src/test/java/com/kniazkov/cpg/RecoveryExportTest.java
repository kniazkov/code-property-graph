/* Code Property Graph: diagnostic API, exports and source-generation guards. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.ParseException;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryExportTest {
    @TempDir Path temp;

    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var matches = new ArrayList<T>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) matches.add(type.cast(node)); }
        });
        return matches;
    }

    @Test void strictApiRemainsStrictWhileEveryRecoveringStageRetainsDiagnostics() throws Exception {
        String source = "int broken() { int missing = ; return 4; }\n";
        Files.writeString(temp.resolve("a.cpp"), source);
        Files.writeString(temp.resolve("b.cpp"), "int healthy() { return 2 + 3; }\n");
        for (Cpg.Stage stage : Cpg.Stage.values()) {
            var options = new Cpg.Options(Language.CPP, stage);
            assertThrows(ParseException.class, () -> Cpg.read(temp, options));
            Project project = Cpg.readRecovering(temp, options);
            assertTrue(project.hasErrors());
            assertEquals(2, project.files().size());
            SourceFile broken = project.files().getFirst();
            assertTrue(broken.hasErrors());
            assertFalse(project.files().getLast().hasErrors());
            assertTrue(broken.stoppedAt().isEmpty());
            assertEquals(1, broken.parseProblems().size());
            ParseProblem problem = broken.parseProblems().getFirst();
            assertEquals("", problem.skippedText());
            assertTrue(problem.recovered());
            assertEquals(source.indexOf(';'), problem.range().start().offset());
            assertEquals(problem.range().start(), problem.range().end());
            ErrorExpression error = nodes(project, ErrorExpression.class).getFirst();
            assertEquals(problem, error.problem());
            assertEquals(problem.range(), error.range().orElseThrow());
            assertEquals(1, nodes(project.files().getLast(), AddExpression.class).size(),
                    "Incomplete projects remain PARSED rather than losing diagnostics in later passes");
            assertTrue(project.flows().isEmpty());
            String json = ProjectJson.encode(project).toText("");
            assertTrue(json.contains("\"hasErrors\""));
            assertTrue(json.contains("\"parseProblems\""));
            assertTrue(json.contains("\"ErrorExpression\""));
            assertFalse(json.contains("\"stoppedAt\""));
            String dot = DotVisualizer.toDot(project, false);
            assertTrue(dot.contains("ERROR: project contains parse errors"));
            assertTrue(dot.contains("ErrorExpression"));
            assertTrue(dot.contains("healthy"));
        }
    }

    @Test void allPublicEmittersRejectBrokenSnapshotsBeforePreservedSourceReplay() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "int main() { int missing = ; return 0; }\n");
        Project project = Cpg.readRecovering(temp, new Cpg.Options(Language.CPP, Cpg.Stage.PARSED));
        SourceFile file = project.files().getFirst();
        assertTrue(file.provenance().isPresent(), "This source would otherwise qualify for source replay");
        for (Language target : Source.SUPPORTED_TARGETS) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, target))
                    .getMessage().contains("parse errors"));
            assertThrows(IllegalArgumentException.class, () -> Source.files(project, target));
            assertThrows(IllegalArgumentException.class, () -> Source.render(project, target));
            assertThrows(IllegalArgumentException.class, () -> Source.render(file, target));
            assertThrows(IllegalArgumentException.class, () -> file.source(target));
            assertThrows(IllegalArgumentException.class, () -> nodes(project, ErrorExpression.class).getFirst().source(target));
        }
        assertTrue(project.languages().isEmpty());
        assertTrue(file.languages().isEmpty());
    }

    @Test void fatalErrorsExportRetainedSyntaxAndStoppedRange() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "int retained() { return 1; }\nint broken( {\n");
        Files.writeString(temp.resolve("b.cpp"), "int healthy() { return 7; }\n");
        Project project = Cpg.readRecovering(temp, new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED));
        SourceFile file = project.files().getFirst();
        assertTrue(file.stoppedAt().isPresent());
        var encoded = (Map<?, ?>) ProjectJson.encode(project).toJavaObject();
        var files = (List<?>) encoded.get("files");
        var broken = (Map<?, ?>) files.getFirst();
        assertEquals(true, encoded.get("hasErrors"));
        assertEquals(true, broken.get("hasErrors"));
        var stopped = (Map<?, ?>) broken.get("stoppedAt");
        var start = (Map<?, ?>) stopped.get("start");
        assertEquals(file.stoppedAt().orElseThrow().start().offset(),
                ((Number) start.get("offset")).intValue());
        var problem = (Map<?, ?>) ((List<?>) broken.get("parseProblems")).getFirst();
        assertEquals(file.parseProblems().getFirst().skippedText(), problem.get("skippedText"));
        assertEquals(file.parseProblems().getFirst().message(), problem.get("message"));
        assertEquals(false, problem.get("recovered"));
        assertEquals(List.of("retained", "healthy"), nodes(project, FunctionDeclaration.class).stream()
                .map(FunctionDeclaration::name).toList());
        String dot = DotVisualizer.toDot(project, false);
        assertTrue(dot.contains("stopped at "));
        assertTrue(dot.contains("a.cpp"));
        assertTrue(dot.contains("healthy"));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
        assertThrows(IllegalArgumentException.class, () -> Source.render(file, Language.CPP));
    }

    @Test void buildRecoveryRetainsIndependentTranslationUnitSettings() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "int broken( {\n");
        Files.writeString(temp.resolve("b.cpp"), "int healthy() { return VALUE; }\n");
        var bad = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("a.cpp")), temp,
                List.of(), List.of(new Build.MacroOperation(true, "VALUE=1")), "c++03");
        var good = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("b.cpp")), temp,
                List.of(), List.of(new Build.MacroOperation(true, "VALUE=7")), "c++03");
        var build = new Build(List.of(bad, good));
        var options = new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED);
        assertThrows(ParseException.class, () -> Cpg.read(build, options));
        Project project = Cpg.readRecovering(build, options);
        assertEquals(build, project.build().orElseThrow());
        assertEquals(good.variantId(), project.files().getLast().variantId().orElseThrow());
        assertEquals(7, nodes(project.files().getLast(), IntegerLiteral.class).getFirst().value());
        assertTrue(ProjectJson.encode(project).toText("").contains(good.variantId()));
    }

    @Test void cliWritesDiagnosticJsonButNeverCreatesRequestedSourceDirectory() throws Exception {
        Path input = Files.createDirectory(temp.resolve("input"));
        Files.writeString(input.resolve("a.cpp"), "int main() { int missing = ; return 0; }\n");
        Path output = temp.resolve("generated"), json = temp.resolve("graph.json");
        var error = assertThrows(IllegalArgumentException.class, () -> Main.run("--from", "CPP",
                "--input", input.toString(), "--out", output.toString(), "--json", json.toString()));
        assertTrue(error.getMessage().contains("source generation is blocked"));
        assertFalse(Files.exists(output));
        assertEquals(true, ((Map<?, ?>) Json.parse(json.toFile()).toJavaObject()).get("hasErrors"));
    }

    @Test void cleanRecoveryOmitsErrorFieldsAndJavaRemainsStrict() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "int main() { return 0; }\n");
        Project project = Cpg.readRecovering(temp, new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED));
        assertFalse(project.hasErrors());
        String json = ProjectJson.encode(project).toText("");
        for (String key : List.of("hasErrors", "parseProblems", "stoppedAt", "ErrorExpression"))
            assertFalse(json.contains("\"" + key + "\""));
        assertFalse(Cpg.sources(project, Language.CPP).isEmpty());
        Files.writeString(temp.resolve("Bad.java"), "class Bad { public static void main(String[] args) { int value = ; } }");
        assertThrows(ParseException.class, () -> Cpg.readRecovering(temp,
                new Cpg.Options(Language.JAVA, Cpg.Stage.PARSED)));
        assertThrows(IllegalArgumentException.class, () -> Cpg.readRecovering(temp,
                new Cpg.Options(Language.CPP, Cpg.Stage.PARSED, "c++11")));
    }

    @Test void diagnosticsCannotAccidentallyCreateBlockedSourceDirectory() throws Exception {
        Path input = Files.createDirectory(temp.resolve("input"));
        Files.writeString(input.resolve("a.cpp"), "int broken( {\n");
        Path output = temp.resolve("generated"), json = output.resolve("graph.json");
        var error = assertThrows(IllegalArgumentException.class, () -> Main.run("--from", "CPP",
                "--input", input.toString(), "--out", output.toString(), "--json", json.toString()));
        assertTrue(error.getMessage().contains("outside the blocked source directory"));
        assertFalse(Files.exists(output));
    }

    @Test void existingBlockedOutputDoesNotHideDiagnosticExportOrModifySources() throws Exception {
        Path input = Files.createDirectory(temp.resolve("input"));
        Files.writeString(input.resolve("a.cpp"), "int broken( {\n");
        Path output = Files.createDirectory(temp.resolve("generated"));
        Path sentinel = output.resolve("a.cpp");
        Files.writeString(sentinel, "unchanged");
        Path json = temp.resolve("graph.json");
        var error = assertThrows(IllegalArgumentException.class, () -> Main.run("--from", "CPP",
                "--input", input.toString(), "--out", output.toString(), "--json", json.toString()));
        assertTrue(error.getMessage().contains("source generation is blocked"));
        assertEquals("unchanged", Files.readString(sentinel));
        try (var files = Files.list(output)) { assertEquals(List.of(sentinel), files.toList()); }
        assertEquals(true, ((Map<?, ?>) Json.parse(json.toFile()).toJavaObject()).get("hasErrors"));
    }
}
