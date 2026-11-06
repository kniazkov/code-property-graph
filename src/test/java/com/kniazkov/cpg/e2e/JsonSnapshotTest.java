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

    @Test void currentSelectorsExcludeHistoryWhileIntegrityChecksIncludeIt() throws Exception {
        Path file = temp.resolve("history.json");
        Files.writeString(file, """
                {"id":0,"kind":"Project","items":[
                  {"id":1,"kind":"A","edges":[{"kind":"ref","to":2}]},
                  {"id":2,"kind":"B"}],
                 "history":[
                  {"id":3,"kind":"A","edges":[{"kind":"ref","to":4}]},
                  {"id":4,"kind":"B"},
                  {"id":5,"kind":"AddExpression","edges":[{"kind":"ref","to":2}]}],
                 "sourceContexts":[{"id":6,"kind":"SourceContext"}]}
                """);
        var snapshot = JsonSnapshot.read(file);
        snapshot.expectEdge("{\"kind\":\"A\"}", "ref", "{\"kind\":\"B\"}");
        snapshot.excludesNode("{\"kind\":\"AddExpression\"}");
        snapshot.excludesNode("{\"kind\":\"SourceContext\"}");
        assertThrows(AssertionError.class, () -> snapshot.containsNode("{\"kind\":\"AddExpression\"}"));

        String valid = Files.readString(file);
        Files.writeString(file, valid.replace("\"id\":4", "\"id\":2"));
        assertTrue(assertThrows(AssertionError.class, () -> JsonSnapshot.read(file)).getMessage().contains("Duplicate ID"));
        Files.writeString(file, valid.replace("\"to\":4", "\"to\":99"));
        assertTrue(assertThrows(AssertionError.class, () -> JsonSnapshot.read(file)).getMessage().contains("Dangling edge"));
        Files.writeString(file, valid.replace("\"id\":6", "\"id\":1"));
        assertTrue(assertThrows(AssertionError.class, () -> JsonSnapshot.read(file)).getMessage().contains("Duplicate ID"));
    }

    @Test void duplicateIdsAndDanglingEdgesFailDuringLoad() throws Exception {
        Path file = temp.resolve("broken.json");
        Files.writeString(file, "{\"id\":0,\"kind\":\"Project\",\"child\":{\"id\":0,\"kind\":\"A\"}}");
        assertTrue(assertThrows(AssertionError.class, () -> JsonSnapshot.read(file)).getMessage().contains("Duplicate ID"));
        Files.writeString(file, "{\"id\":0,\"kind\":\"Project\",\"edges\":[{\"kind\":\"ref\",\"to\":7}]}");
        assertTrue(assertThrows(AssertionError.class, () -> JsonSnapshot.read(file)).getMessage().contains("Dangling edge"));
    }
}
