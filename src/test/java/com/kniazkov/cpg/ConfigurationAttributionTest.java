package com.kniazkov.cpg;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.CompilationDatabase;
import com.kniazkov.cpg.model.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationAttributionTest {
    @TempDir Path temp;

    @Test void retainsExactArgumentsAndTheirPositions() throws Exception {
        Path db = temp.resolve("compile_commands.json");
        Files.writeString(db, """
                [{"directory":".","file":"a.cpp","arguments":["c++","-DDEBUG=0","-D","UNUSED=2","-U","OLD","a.cpp"]}]
                """);
        var unit = CompilationDatabase.read(db).units().getFirst();
        var source = unit.configurationSource();
        assertEquals("database", source.kind());
        assertEquals(db, source.database());
        assertEquals(0, source.entryOrdinal());
        assertEquals(List.of("c++", "-DDEBUG=0", "-D", "UNUSED=2", "-U", "OLD", "a.cpp"), source.arguments());
        assertEquals("argument:1", unit.macros().get(0).sourceKey());
        assertEquals("-DDEBUG=0", unit.macros().get(0).originalArgument());
        assertEquals(List.of("-DDEBUG=0"), unit.macros().get(0).originalArguments());
        assertEquals("argument:2", unit.macros().get(1).sourceKey());
        assertEquals("-D", unit.macros().get(1).originalArgument());
        assertEquals(List.of("-D", "UNUSED=2"), unit.macros().get(1).originalArguments());
        assertEquals("argument:4", unit.macros().get(2).sourceKey());
        assertEquals(List.of("-U", "OLD"), unit.macros().get(2).originalArguments());
        var api = new Build.TranslationUnit(unit.source(), unit.directory(), unit.includes(),
                unit.macros().stream().map(m -> new Build.MacroOperation(m.define(), m.operand())).toList(), unit.standard());
        assertEquals("java-api", api.configurationSource().kind());
        assertEquals(api.variantId(), unit.variantId(), "Attribution must not change effective configuration identity");
    }

    @Test void editionOverridePreservesDatabaseLineage() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "int main() { return 0; }");
        Path db = temp.resolve("compile_commands.json");
        Files.writeString(db, """
                [{"directory":".","file":"a.cpp","arguments":["c++","-std=c++11","-DUNUSED=0","a.cpp"]}]
                """);
        var source = CompilationDatabase.read(db).units().getFirst().configurationSource();
        var project = Cpg.readCompilationDatabase(db, new Cpg.Options(Language.CPP, Cpg.Stage.PARSED,
                "c++03", Build.ConfigurationSource.cli(List.of("--cpp-standard", "c++03"))));
        var unit = project.build().orElseThrow().units().getFirst();
        assertEquals("c++03", unit.standard());
        assertEquals("database", unit.configurationSource().kind());
        assertEquals(source.database(), unit.configurationSource().database());
        assertEquals(source.arguments(), unit.configurationSource().arguments());
        assertEquals("cli", unit.configurationSource().standardOverrideKind());
        assertEquals("-DUNUSED=0", unit.macros().getFirst().originalArgument());
    }
}
