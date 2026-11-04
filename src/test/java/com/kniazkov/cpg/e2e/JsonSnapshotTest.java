/* Code Property Graph: regression checks on the E2E assertion helpers. MIT License. */
package com.kniazkov.cpg.e2e;

import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies that broken snapshots cannot satisfy assertions by matching unrelated nodes or edges. */
class JsonSnapshotTest {
    @TempDir Path temp;

    @Test void partialPatternsRequireFieldsOnTheSameNodeAndOrderedArrayElements() throws Exception {
        Path file = temp.resolve("partial.json");
        Files.writeString(file, """
                {"id":0,"kind":"Project","items":[
                  {"id":1,"kind":"A","flags":["x","y","z"],"edges":[{"kind":"ref","to":2}]},
                  {"id":2,"kind":"B","note":"found"}]}
                """);
        var json = JsonSnapshot.read(file);
        json.containsNode("{\"kind\":\"A\",\"flags\":[\"x\",\"z\"]}");
        json.excludesNode("{\"kind\":\"A\",\"flags\":[\"z\",\"x\"]}");
        json.excludesNode("{\"kind\":\"A\",\"note\":\"found\"}");
        assertThrows(AssertionError.class, () -> json.containsNode("{\"kind\":\"A\",\"flags\":[\"x\",\"x\"]}"));
        assertThrows(AssertionError.class, () -> json.excludesNode("{\"kind\":\"B\"}"));
        assertThrows(AssertionError.class, () -> json.expectEdge("{\"kind\":\"A\"}", "ref", "{\"kind\":\"Project\"}"));
        assertThrows(AssertionError.class, () -> json.exactly("/json/return-zero.json", Map.of()));
    }

    @Test void duplicateIdsAndDanglingEdgesFailDuringLoad() throws Exception {
        Path file = temp.resolve("broken.json");
        Files.writeString(file, "{\"id\":0,\"child\":{\"id\":0}}");
        assertThrows(AssertionError.class, () -> JsonSnapshot.read(file));
        Files.writeString(file, "{\"id\":0,\"edges\":[{\"kind\":\"ref\",\"to\":7}]}");
        assertThrows(AssertionError.class, () -> JsonSnapshot.read(file));
    }
}
