/* Code Property Graph: public API and packaged CLI compilation database acceptance. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.CompilationDatabase;
import com.kniazkov.cpg.model.Language;
import com.kniazkov.cpg.parser.ParseException;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises edition precedence and equivalent explicit/imported inputs without invoking a shell. */
class CompilationDatabaseIT {
    @TempDir Path temp;
    private static final Path FIXTURE = Path.of("src/test/resources/cpp03/compilation-database");
    private static final Cpg.Options DEFAULT = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);
    private static final Cpg.Options EXPLICIT = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03");

    @Test void defaultAndExplicitFolderEditionsProduceIdenticalGraphs() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", "int main() { return 0; }");
        var defaultRun = cli.run("cpp", "--stage", "resolved").expectStatus(0);
        var explicitRun = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(0);
        var expected = ProjectJson.encode(Cpg.read(cli.input, DEFAULT));
        assertEquals(expected, ProjectJson.encode(Cpg.read(cli.input, EXPLICIT)));
        assertEquals(expected, Json.parse(defaultRun.work().resolve("snapshot.json").toFile()));
        assertEquals(expected, Json.parse(explicitRun.work().resolve("snapshot.json").toFile()));
        var failure = cli.run("cpp", "--cpp-standard", "c++11").expectStatus(1);
        failure.expectNoExports();
        assertTrue(failure.stderr().contains("main.cpp"), failure.stderr());
        assertTrue(failure.stderr().contains("c++11"), failure.stderr());
        var error = assertThrows(IllegalArgumentException.class,
                () -> Cpg.read(cli.input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++11")));
        assertTrue(error.getMessage().contains("main.cpp"));
    }

    @Test void importedAndExplicitConfigurationsMatchAcrossWorkingDirectories() throws Exception {
        var cli = new CliSandbox(temp).directory(FIXTURE);
        Path db = cli.input.resolve("compile_commands.json"), sources = cli.input.resolve("sources");
        var failure = cli.runDatabase(db, "--stage", "resolved").expectStatus(1);
        assertTrue(failure.stderr().contains("b.cpp"), failure.stderr());
        assertTrue(failure.stderr().contains("c++11"), failure.stderr());
        failure.expectNoExports();
        var apiFailure = assertThrows(IllegalArgumentException.class, () -> Cpg.readCompilationDatabase(db, DEFAULT));
        assertTrue(apiFailure.getMessage().contains("b.cpp"));
        assertTrue(apiFailure.getMessage().contains("c++11"));
        var manual = new Build(List.of(
                new Build.TranslationUnit(new Build.PhysicalSource(sources.resolve("a.cpp")), sources,
                        List.of(new Build.IncludePath("-I", Path.of("include one")), new Build.IncludePath("-I", Path.of("include two"))),
                        List.of(new Build.MacroOperation(true, "VALUE=3"), new Build.MacroOperation(false, "VALUE"),
                                new Build.MacroOperation(true, "VALUE=4")), "c++03"),
                new Build.TranslationUnit(new Build.PhysicalSource(sources.resolve("b.cpp")), sources,
                        List.of(new Build.IncludePath("-I", Path.of("include two")), new Build.IncludePath("-I", Path.of("include one"))),
                        List.of(new Build.MacroOperation(true, "VALUE=7"), new Build.MacroOperation(false, "VALUE"),
                                new Build.MacroOperation(true, "VALUE=8")), "c++11")));
        assertEquals(manual, CompilationDatabase.read(db));
        var expected = ProjectJson.encode(Cpg.read(manual, EXPLICIT));
        assertEquals(expected, ProjectJson.encode(Cpg.readCompilationDatabase(db, EXPLICIT)));
        var run = cli.runDatabase(db, "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(0);
        assertEquals(expected, Json.parse(run.work().resolve("snapshot.json").toFile()));
        var graph = run.snapshot();
        graph.containsNode("{\"kind\":\"NamedType\",\"name\":\"int\"}");
        graph.expectEdge("{\"kind\":\"NameExpression\",\"name\":\"value\",\"range\":{\"start\":{\"line\":3}}}",
                "ref", "{\"kind\":\"VariableDeclaration\",\"name\":\"value\",\"initializer\":{\"value\":3}}");
    }

    @Test void explicitEditionCannotMakeCpp11SyntaxValid() throws Exception {
        var cli = new CliSandbox(temp).directory(FIXTURE);
        Path db = cli.input.resolve("compile_commands.json");
        Files.writeString(cli.input.resolve("sources/b.cpp"), "int main() { int value = []() { return 1; }(); return value; }\n");
        var run = cli.runDatabase(db, "--cpp-standard", "c++03").expectStatus(1);
        assertTrue(run.stderr().contains("b.cpp"), run.stderr());
        assertTrue(run.stderr().contains("C++03"), run.stderr());
        run.expectNoExports();
        var error = assertThrows(ParseException.class, () -> Cpg.readCompilationDatabase(db, EXPLICIT));
        assertTrue(error.getMessage().contains("b.cpp"));
        assertTrue(error.getMessage().contains("C++03"));
    }

    @Test void unsupportedParsingOptionNamesItsFile() throws Exception {
        var cli = new CliSandbox(temp).directory(FIXTURE);
        Path db = cli.input.resolve("compile_commands.json");
        Files.writeString(db, Files.readString(db).replace("-std=c++11", "-fms-extensions"));
        var run = cli.runDatabase(db, "--cpp-standard", "c++03").expectStatus(1);
        assertTrue(run.stderr().contains("b.cpp"), run.stderr());
        assertTrue(run.stderr().contains("-fms-extensions"), run.stderr());
        run.expectNoExports();
    }
}
