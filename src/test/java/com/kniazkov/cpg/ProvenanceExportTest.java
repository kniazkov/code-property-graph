/* Code Property Graph: non-syntax provenance exports. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProvenanceExportTest {
    private static SourceFile.Builder file(String path) {
        var range = new SourceRange(path, new SourcePosition(1, 1, 0), new SourcePosition(1, 2, 1));
        var fragment = new Preprocessing.Fragment(range, 0, "<");
        var operation = new Preprocessing.Operation("paste", range, List.of(fragment), "<<");
        var parent = new Preprocessing.Expansion(0, "OUTER", range, List.of(fragment), range, 0, null,
                List.of(), List.of(), 0, 1);
        var child = new Preprocessing.Expansion(1, "INNER", range, List.of(fragment), range, 0, 0L,
                List.of(List.of(fragment)), List.of(operation), 0, 1);
        var token = new Preprocessing.Token(0, "1", range, range, 0, List.of(0L, 1L));
        var unit = new Preprocessing(path, List.of(new Preprocessing.Include(0, path, null)),
                List.of(token), List.of(parent, child));
        var declaration = VariableDeclaration.create().setName("x");
        declaration.setRange(range);
        declaration.setProvenance(new TokenProvenance(unit, 0, 1));
        var file = SourceFile.create().setPath(path).setLanguage(Language.CPP).addItem(declaration);
        file.setProvenance(new TokenProvenance(unit, 0, 1));
        return file;
    }

    @Test void jsonScopesEventsAndStoresMetadataOnlyOncePerTranslationUnit() {
        var project = Project.create().addFile(file("a.cpp")).addFile(file("b.cpp")).build();
        String json = ProjectJson.encode(project).toText("");
        assertEquals(json, ProjectJson.encode(project.edit().build()).toText(""));
        assertEquals(2, json.split("\"preprocessing\"", -1).length - 1);
        assertEquals(2, json.split("\"translationUnit\"", -1).length - 1);
        assertTrue(json.replaceAll("\\s+", "").contains("\"sourceFileId\":1"));
        assertTrue(json.replaceAll("\\s+", "").contains("\"sourceFileId\":3"));
        assertTrue(json.contains(com.kniazkov.json.Json.serialize(Path.of("a.cpp").toAbsolutePath().toString())));
        assertTrue(json.contains("\"spellingRange\""));
        assertTrue(json.contains("\"replacement\""));
        assertTrue(json.contains("\"fragments\""));
    }

    @Test void provenanceOverlayIsOptionalScopedAndOutsideSyntax() {
        var project = Project.create().addFile(file("a.cpp")).addFile(file("b.cpp")).build();
        String visible = DotVisualizer.toDot(project);
        assertEquals(visible, DotVisualizer.toDot(project, true));
        assertEquals(visible, DotVisualizer.toDot(project.edit().build()));
        assertTrue(visible.contains("p0e1"));
        assertTrue(visible.contains("p1e1"));
        assertTrue(visible.contains("parent expansion"));
        assertTrue(visible.contains("argument 0"));
        assertTrue(visible.contains("operand"));
        assertTrue(visible.contains("&lt;"));
        String hidden = DotVisualizer.toDot(project, false);
        assertFalse(hidden.contains("macro "));
        assertFalse(hidden.contains("origin"));
        assertTrue(hidden.contains("VariableDeclaration"));
        assertEquals(2, project.children().size());
        assertEquals(1, project.files().getFirst().children().size());
        assertTrue(project.files().getFirst().references().isEmpty());
    }

    @Test void cliRejectsInvalidProvenanceBeforeReadingInput() {
        var error = assertThrows(IllegalArgumentException.class, () -> Main.run("--from", "CPP",
                "--input", "absent", "--svg", "graph.svg", "--provenance", "invalid"));
        assertEquals("--provenance must be on or off", error.getMessage());
    }
}
