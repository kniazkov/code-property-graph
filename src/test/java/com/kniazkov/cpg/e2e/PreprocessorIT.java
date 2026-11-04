/* Code Property Graph: phase-4 API/CLI acceptance over ordinary header projects. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.CompilationDatabase;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.CppPreprocessor;
import com.kniazkov.cpg.parser.ParseException;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Graph acceptance complements token-level macro rules and native corpus execution. */
class PreprocessorIT {
    @TempDir Path temp;
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);

    @Test void squareExpandsBothUnitsAndGeneratesCompleteSources() throws Exception {
        var cli = new CliSandbox(temp).directory(Path.of("src/test/resources/cpp03/square"));
        Path db = cli.input.resolve("compile_commands.json");
        var project = Cpg.readCompilationDatabase(db, OPTIONS);
        var run = cli.runDatabase(db, "--stage", "resolved").expectStatus(0);
        assertEquals(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        int[] multiplies = {0};
        project.walk(new NodeVisitor() {
            @Override public void visit(MultiplyExpression node) { multiplies[0]++; }
        });
        assertEquals(2, multiplies[0]);
        assertEquals(3, Cpg.sources(project, Language.CPP).size());
        var graph = run.snapshot();
        graph.containsNode("{\"kind\":\"MultiplyExpression\",\"left\":{\"name\":\"a\"},\"right\":{\"name\":\"a\"}}");
        graph.containsNode("{\"kind\":\"MultiplyExpression\",\"left\":{\"name\":\"c\"},\"right\":{\"name\":\"c\"}}");
        assertTrue(Cpg.sources(project, Language.CPP).get("include/square.h").contains("int second();"));
    }

    @Test void macroOperationsAndRepeatedIncludesReachTheCli() throws Exception {
        var cli = new CliSandbox(temp).directory(Path.of("src/test/resources/cpp03/macros"));
        Path db = cli.input.resolve("compile_commands.json");
        var project = Cpg.readCompilationDatabase(db, OPTIONS);
        var run = cli.runDatabase(db, "--stage", "resolved").expectStatus(0);
        assertEquals(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        int[] guarded = {0};
        project.walk(new NodeVisitor() {
            @Override public void visit(ClassDeclaration node) { if (node.name().equals("Guarded")) guarded[0]++; }
        });
        assertEquals(1, guarded[0], "Include guard prevents duplicate declarations");
        var graph = run.snapshot();
        graph.containsNode("{\"kind\":\"VariableDeclaration\",\"name\":\"left\",\"initializer\":{\"value\":2}}");
        graph.containsNode("{\"kind\":\"VariableDeclaration\",\"name\":\"right\",\"initializer\":{\"value\":7}}");
        var unit = CompilationDatabase.read(db).units().getFirst();
        var expanded = CppPreprocessor.preprocess(unit, Files.readString(unit.source().path()));
        assertEquals(2, expanded.includes().stream().filter(i -> i.source().path().getFileName().toString().equals("slot.h")).count());
        assertTrue(expanded.tokens().stream().flatMap(t -> t.origins().stream()).anyMatch(o -> o.name().equals("SQUARE")));
        assertTrue(Cpg.sources(project, Language.CPP).get("main.cpp").contains("#define"));
    }

    @Test void compilationFlagsReachParserAndStayLocalToEachUnit() throws Exception {
        Path fixture = Files.createDirectory(temp.resolve("flags"));
        Files.writeString(fixture.resolve("first.cpp"), "int first() { return VALUE; }\n");
        Files.writeString(fixture.resolve("second.cpp"), "int second() { return VALUE; }\n");
        Files.writeString(fixture.resolve("compile_commands.json"), """
            [
              {"directory":".","file":"first.cpp","arguments":["g++","-DVALUE=1","-UVALUE","-DVALUE=7","-c","first.cpp"]},
              {"directory":".","file":"second.cpp","arguments":["g++","-DVALUE=3","-c","second.cpp"]}
            ]
            """);
        var cli = new CliSandbox(Files.createDirectory(temp.resolve("flags-run"))).directory(fixture);
        Path db = cli.input.resolve("compile_commands.json");
        var project = Cpg.readCompilationDatabase(db, OPTIONS);
        var run = cli.runDatabase(db, "--stage", "resolved").expectStatus(0);
        assertEquals(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        var sources = Cpg.sources(project, Language.CPP);
        assertTrue(sources.get("first.cpp").contains("return VALUE;"));
        run.snapshot().containsNode("{\"kind\":\"FunctionDeclaration\",\"name\":\"first\",\"body\":{\"items\":[{\"kind\":\"ReturnStatement\",\"value\":{\"value\":7}}]}}");
        assertTrue(sources.get("second.cpp").contains("return VALUE;"));
        run.snapshot().containsNode("{\"kind\":\"FunctionDeclaration\",\"name\":\"second\",\"body\":{\"items\":[{\"kind\":\"ReturnStatement\",\"value\":{\"value\":3}}]}}");
    }

    @Test void missingHeaderReportsTheWholeIncludeChainWithoutExports() throws Exception {
        var cli = new CliSandbox(temp).directory(Path.of("src/test/resources/cpp03/missing-header/sources"));
        var run = cli.run("cpp", "--stage", "resolved").expectStatus(1);
        run.diagnosticSnapshot();
        var error = assertThrows(ParseException.class, () -> Cpg.read(cli.input, OPTIONS));
        for (String expected : new String[]{"Header not found: missing.h", "include chain:", "main.cpp", "outer.h", "inner.h"}) {
            assertTrue(run.stderr().contains(expected), run.stderr());
            assertTrue(error.getMessage().contains(expected), error.getMessage());
        }
        assertEquals(1, error.line());
        assertEquals(2, error.column());
    }
}
