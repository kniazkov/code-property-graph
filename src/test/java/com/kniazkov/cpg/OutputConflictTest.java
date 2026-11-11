/* Code Property Graph: atomic output-path validation. MIT License. */
package com.kniazkov.cpg;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OutputConflictTest {
    @TempDir Path temp;

    private Path input() throws Exception {
        Path input = Files.createDirectory(temp.resolve("input"));
        Files.createDirectory(input.resolve("include"));
        Files.writeString(input.resolve("include/value.h"), "#define VALUE 3\n");
        Files.writeString(input.resolve("main.cpp"), "#include \"include/value.h\"\nint main() { return VALUE; }\n");
        return input;
    }

    private void rejected(Path input, Path output, Path json, Path svg) throws Exception {
        var args = new ArrayList<>(List.of("--input", input.toString(), "--from", "CPP",
                "--stage", "resolved", "--out", output.toString(), "--json", json.toString(), "--svg", svg.toString()));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Main.run(args.toArray(String[]::new)));
        assertTrue(error.getMessage().contains("conflict"), error::getMessage);
        assertFalse(Files.exists(output), "No generated directory may be created before validation");
        assertFalse(Files.exists(json), "No JSON export may be written before validation");
        assertFalse(Files.exists(svg), "No SVG export may be written before validation");
    }

    @Test void exportsNestedHeaderWithPortableOutputName() throws Exception {
        Path input = input();
        var project = Cpg.read(input, new Cpg.Options(com.kniazkov.cpg.model.Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        var sources = Cpg.sources(project, com.kniazkov.cpg.model.Language.CPP);
        assertEquals(Set.of("main.cpp", "include/value.h"), sources.keySet());
        Path output = temp.resolve("out");
        Main.run("--input", input.toString(), "--from", "CPP", "--stage", "resolved", "--out", output.toString());
        assertEquals(Files.readString(input.resolve("main.cpp")), Files.readString(output.resolve("main.cpp")));
        assertEquals(Files.readString(input.resolve("include/value.h")), Files.readString(output.resolve("include/value.h")));
    }

    @Test void rejectsJsonAsParentOfGeneratedHeaderBeforeAnyExports() throws Exception {
        Path output = temp.resolve("out");
        rejected(input(), output, output.resolve("include"), temp.resolve("graph.svg"));
    }

    @Test void rejectsSvgInsideGeneratedFileBeforeAnyExports() throws Exception {
        Path output = temp.resolve("out");
        rejected(input(), output, temp.resolve("snapshot.json"), output.resolve("main.cpp/graph.svg"));
    }

    @Test void rejectsJsonAsParentOfSvgBeforeAnyExports() throws Exception {
        Path json = temp.resolve("snapshot.json");
        rejected(input(), temp.resolve("out"), json, json.resolve("graph.svg"));
    }

    @Test void rejectsSvgAsParentOfJsonBeforeAnyExports() throws Exception {
        Path svg = temp.resolve("graph.svg");
        rejected(input(), temp.resolve("out"), svg.resolve("snapshot.json"), svg);
    }

    @Test void rejectsExportAsAncestorOfOutputDirectoryBeforeAnyExports() throws Exception {
        Path json = temp.resolve("exports");
        rejected(input(), json.resolve("out"), json, temp.resolve("graph.svg"));
    }
}
