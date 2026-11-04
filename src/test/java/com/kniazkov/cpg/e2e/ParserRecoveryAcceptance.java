/* Code Property Graph: recovery contract through API and packaged CLI. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ParserRecoveryAcceptance {
    private ParserRecoveryAcceptance() {}
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);

    static void verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture);
        Path database = cli.input.resolve("compile_commands.json");
        var project = Cpg.readCompilationDatabaseRecovering(database, OPTIONS);
        assertTrue(project.hasErrors());
        assertEquals(2, project.files().size());
        var bad = project.files().stream().filter(f -> f.path().equals("a.cpp")).findFirst().orElseThrow();
        var good = project.files().stream().filter(f -> f.path().equals("b.cpp")).findFirst().orElseThrow();
        assertTrue(bad.hasErrors()); assertTrue(bad.stoppedAt().isPresent());
        assertFalse(bad.parseProblems().isEmpty());
        assertFalse(good.hasErrors()); assertTrue(good.stoppedAt().isEmpty());
        assertEquals(1, functions(bad).stream().filter(f -> f.name().equals("retained")).count());
        assertEquals(1, functions(good).stream().filter(f -> f.name().equals("usable")).count());
        assertTrue(nodes(good, IntegerLiteral.class).stream().anyMatch(n -> n.value() == 7));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
        assertThrows(IllegalArgumentException.class, () -> com.kniazkov.cpg.generation.Source.render(project, Language.CPP));
        for (boolean sources : List.of(true, false)) {
            var run = (sources ? cli.runDatabase(database, "--stage", "resolved", "--svg", "broken.svg")
                    : cli.runDatabaseJson(database, "--stage", "resolved", "--svg", "broken.svg")).expectStatus(1);
            var snapshot = run.diagnosticSnapshot();
            snapshot.containsNode("{\"kind\":\"Project\",\"hasErrors\":true}");
            snapshot.containsNode("{\"kind\":\"SourceFile\",\"hasErrors\":true}");
            snapshot.containsNode("{\"kind\":\"FunctionDeclaration\",\"name\":\"retained\"}");
            snapshot.containsNode("{\"kind\":\"FunctionDeclaration\",\"name\":\"usable\"}");
            assertEquals(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
            String json = Files.readString(run.work().resolve("snapshot.json"));
            assertTrue(json.contains("\"stoppedAt\""));
            assertTrue(json.contains("\"skippedText\""));
            assertTrue(run.stderr().contains("a.cpp:"), run.stderr());
            String svg = Files.readString(run.work().resolve("broken.svg"));
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var xml = factory.newDocumentBuilder().parse(run.work().resolve("broken.svg").toFile());
            assertEquals("svg", xml.getDocumentElement().getTagName());
            assertTrue(svg.toLowerCase(Locale.ROOT).contains("error"));
            assertTrue(svg.toLowerCase(Locale.ROOT).contains("stopped"));
            assertTrue(svg.contains("a.cpp") && svg.contains("usable") && svg.contains("retained"));
        }
    }

    static void missingInitializer(Path work) throws Exception {
        String source = "int main() { int x = ; int y = 2; return y; }\n";
        var cli = new CliSandbox(work).source("main.cpp", source);
        var project = Cpg.readRecovering(cli.input, OPTIONS);
        assertTrue(project.hasErrors());
        var file = project.files().getFirst();
        assertTrue(file.stoppedAt().isEmpty(), "Semicolon is a reliable recovery boundary");
        var declarations = nodes(project, VariableDeclaration.class);
        assertEquals(List.of("x", "y"), declarations.stream().map(VariableDeclaration::name).toList());
        var error = assertInstanceOf(ErrorExpression.class, declarations.getFirst().initializer().orElseThrow());
        assertEquals("", error.problem().skippedText());
        var range = error.problem().range();
        assertEquals(range.start(), range.end());
        assertEquals(source.indexOf(';'), range.start().offset());
        assertEquals(2, assertInstanceOf(IntegerLiteral.class, declarations.getLast().initializer().orElseThrow()).value());
        assertEquals(1, nodes(project, ReturnStatement.class).size());
        var run = cli.run("cpp", "--stage", "folded").expectStatus(1);
        run.diagnosticSnapshot().containsNode("{\"kind\":\"ErrorExpression\",\"problem\":{\"skippedText\":\"\",\"recovered\":true}}");
        String json = Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("\"hasErrors\": true") || json.contains("\"hasErrors\":true"));
        String damaged = "int main() { int x = 1 + ; int y = 2; return y; }\n";
        Files.writeString(cli.input.resolve("main.cpp"), damaged);
        var skipped = nodes(Cpg.readRecovering(cli.input, OPTIONS), ErrorExpression.class).getFirst().problem();
        assertFalse(skipped.skippedText().isEmpty());
        assertEquals(damaged.substring(skipped.range().start().offset(), skipped.range().end().offset()), skipped.skippedText());
        assertTrue(skipped.skippedText().contains("1 +"));
        cli.run("cpp", "--stage", "resolved").expectStatus(1).diagnosticSnapshot()
                .containsNode("{\"kind\":\"VariableDeclaration\",\"name\":\"y\"}");
    }

    static void extension(Path work) throws Exception {
        String declaration = "struct __attribute__((packed)) Packed { int value; };\n";
        var cli = new CliSandbox(work).source("main.cpp", declaration + "int main() { return 0; }\n");
        var run = cli.run("cpp", "--stage", "parsed").expectStatus(1);
        run.diagnosticSnapshot().containsNode("{\"kind\":\"Project\",\"hasErrors\":true}");
        assertTrue(run.stderr().contains("main.cpp:1:8"), run.stderr());
        assertTrue(run.stderr().contains("__attribute__") && run.stderr().contains("C++03"), run.stderr());
        var bad = Cpg.readRecovering(cli.input, OPTIONS).files().getFirst();
        assertEquals(8, bad.parseProblems().getFirst().range().start().column());
        Files.writeString(cli.input.resolve("main.cpp"), "#if 0\n" + declaration + "#endif\nint main() { return 0; }\n");
        var good = cli.run("cpp", "--stage", "resolved").expectStatus(0);
        good.snapshot().containsNode("{\"kind\":\"FunctionDeclaration\",\"name\":\"main\"}");
        assertFalse(Cpg.readRecovering(cli.input, OPTIONS).hasErrors());
    }

    static void editionBoundaries(Path work) throws Exception {
        int index = 0;
        for (String source : List.of("#define M(...) 1\nint main() { return M(1); }\n",
                "int main() { return 1LL; }\n", "int main() { return 0b10; }\n",
                "int main() { int x = R\"(raw)\"; return 0; }\n")) {
            Path root = Files.createDirectory(work.resolve("edition-" + index++));
            var cli = new CliSandbox(root).source("main.cpp", source);
            var run = cli.run("cpp", "--stage", "parsed").expectStatus(1);
            run.diagnosticSnapshot().containsNode("{\"kind\":\"Project\",\"hasErrors\":true}");
            assertTrue(run.stderr().contains("main.cpp:"), run.stderr());
            assertTrue(run.stderr().contains("C++03"), run.stderr());
            assertTrue(Cpg.readRecovering(cli.input, OPTIONS).hasErrors());
            assertThrows(com.kniazkov.cpg.parser.ParseException.class, () -> Cpg.read(cli.input, OPTIONS));
        }
    }

    static void progress(Path work) throws Exception {
        int index = 0;
        for (String source : List.of("int main() {", "int main() { int x =", "int main() { @ @ @ @ @", "int main() { int x = ; int y = ;")) {
            Path root = Files.createDirectory(work.resolve("case-" + index++));
            var cli = new CliSandbox(root).source("a.cpp", source).source("b.cpp", "int usable() { return 7; }\n");
            var run = cli.run("cpp", "--stage", "resolved").expectStatus(1);
            run.diagnosticSnapshot().containsNode("{\"kind\":\"FunctionDeclaration\",\"name\":\"usable\"}");
            var project = Cpg.readRecovering(cli.input, OPTIONS);
            assertTrue(project.hasErrors());
            assertFalse(project.files().getFirst().parseProblems().isEmpty());
            assertFalse(project.files().getLast().hasErrors());
            if (source.contains("int y = ;")) assertTrue(project.files().getFirst().parseProblems().size() >= 2);
        }
    }

    private static List<FunctionDeclaration> functions(Node root) { return nodes(root, FunctionDeclaration.class); }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
}
