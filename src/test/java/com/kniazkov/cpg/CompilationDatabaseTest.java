package com.kniazkov.cpg;

import com.kniazkov.cpg.build.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompilationDatabaseTest {
    @TempDir Path temp;
    @Test void tokenizesWithoutExecutingOrExpanding() {
        assertEquals(List.of("c++", "-Iinclude space", "-DX=a b", "", "a.cpp"),
            CompilationDatabase.tokenize("c++ -I'include space' \"-DX=a b\" '' a.cpp"));
        for (String command : List.of("c++ $(touch marker)", "c++ a.cpp; touch marker",
                "c++ \"$HOME\"", "c++ 'broken", "c++ a.cpp | cat"))
            assertThrows(IllegalArgumentException.class, () -> CompilationDatabase.tokenize(command));
        assertFalse(Files.exists(temp.resolve("marker")));
    }
    @Test void preservesOrderingAndResolvesAgainstDirectory() throws Exception {
        Path db = temp.resolve("commands.json");
        Files.writeString(db, """
            [{"directory":"working dir","file":"a.cpp","arguments":["c++",
            "-Ione","-I","two","-isystem","sys","-DNAME=1","-U","NAME","-D","NAME=2",
            "-std=c++11","-std","c++03","-c","a.cpp","-o","out.o"],
            "command":"deliberately ignored"}]
            """);
        var unit = CompilationDatabase.read(db).units().getFirst();
        assertEquals(temp.resolve("working dir/a.cpp"), unit.source().path());
        assertEquals(List.of("-I","-I","-isystem"), unit.includes().stream().map(Build.IncludePath::kind).toList());
        assertEquals(temp.resolve("working dir/two"), unit.includes().get(1).path());
        assertEquals(List.of(new Build.MacroOperation(true,"NAME=1"),
            new Build.MacroOperation(false,"NAME"), new Build.MacroOperation(true,"NAME=2")),
            unit.macros().stream().map(m -> new Build.MacroOperation(m.define(), m.operand())).toList());
        assertEquals("c++03",unit.standard());
        assertThrows(UnsupportedOperationException.class, () -> unit.macros().clear());
    }
    @Test void diagnosesSemanticFlagsWithFilename() throws Exception {
        for (String flag : List.of("-include", "-fms-extensions", "@response", "-I", "-I-")) {
            Path db = temp.resolve("commands.json");
            Files.writeString(db, "[{\"directory\":\".\",\"file\":\"a.cpp\",\"arguments\":[\"c++\",\""+flag+"\"]}]");
            var error = assertThrows(IllegalArgumentException.class, () -> CompilationDatabase.read(db));
            assertTrue(error.getMessage().contains("a.cpp"), error.getMessage());
            assertTrue(error.getMessage().contains(flag), error.getMessage());
        }
    }
    @Test void handlesContinuationsButRejectsCommandSeparators() {
        String slash = Character.toString(92);
        assertEquals(List.of("c++", "a.cpp"), CompilationDatabase.tokenize("c++ " + slash + "\n a.cpp"));
        assertEquals(List.of("c++", "a.cpp"), CompilationDatabase.tokenize("c++ \"a." + slash + "\ncpp\""));
        for (String command : List.of("c++ a.cpp\nnext", "c++ a.cpp\r\nnext",
                "c++ " + slash + "\r\n a.cpp"))
            assertThrows(IllegalArgumentException.class, () -> CompilationDatabase.tokenize(command));
    }
    @Test void malformedSchemasHaveDatabaseAndEntryContext() throws Exception {
        Path db = temp.resolve("bad.json");
        for (String document : List.of("null", "{}", "[null]", "[1]", "[{}]",
                "[{\"file\":1}]", "[{\"file\":\"a.cpp\",\"directory\":null}]",
                "[{\"file\":\"a.cpp\",\"directory\":\".\",\"arguments\":null}]",
                "[{\"file\":\"a.cpp\",\"directory\":\".\",\"arguments\":[1]}]")) {
            Files.writeString(db, document);
            var error = assertThrows(IllegalArgumentException.class, () -> CompilationDatabase.read(db));
            assertTrue(error.getMessage().contains("bad.json"), error.getMessage());
            if (document.startsWith("[")) assertTrue(error.getMessage().contains("entry 0"));
        }
    }
    @Test void defaultEditionAndExplicitUnsupportedEdition() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "int main() { return 0; }");
        Path db = temp.resolve("commands.json");
        Files.writeString(db, "[{\"directory\":\".\",\"file\":\"a.cpp\",\"command\":\"c++ -c a.cpp\"}]");
        assertNotNull(Cpg.readCompilationDatabase(db, new Cpg.Options(com.kniazkov.cpg.model.Language.CPP, Cpg.Stage.PARSED)));
        var error = assertThrows(IllegalArgumentException.class, () -> Cpg.readCompilationDatabase(db,
            new Cpg.Options(com.kniazkov.cpg.model.Language.CPP, Cpg.Stage.PARSED, "c++11")));
        assertTrue(error.getMessage().contains("a.cpp"));
        assertTrue(error.getMessage().contains("c++11"));
    }
    @Test void contextInstancesAreSeparateFromPhysicalSource() {
        var source = new Build.PhysicalSource(temp.resolve("header.h"));
        var unit = new Build.TranslationUnit(source,temp,List.of(),List.of(),null);
        assertNotEquals(new Build.IncludeInstance(unit,source,null,1),
                        new Build.IncludeInstance(unit,source,null,2));
    }
}
