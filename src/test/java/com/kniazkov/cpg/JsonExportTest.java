/* Code Property Graph: golden graph snapshots and source provenance. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the public export against a reviewed fixture and the original UTF-16 text. */
class JsonExportTest {
    @TempDir Path dir;

    @Test void completeSnapshotMatchesReviewedFixture() throws Exception {
        Path file = dir.resolve("main.cpp");
        Files.writeString(file, "int main() { return 0; }");
        var project = Cpg.read(dir, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        String fixture;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/json/return-zero.json"))) {
            fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String name : java.util.stream.Stream.concat(java.util.stream.Stream.of("__cplusplus", "__FILE__", "__LINE__", "__DATE__", "__TIME__"),
                com.kniazkov.cpg.build.PlatformProfile.defaults().predefinedMacros().keySet().stream()).toList())
            fixture = fixture.replace(Json.serialize("PREDEFINED_" + name),
                    Json.serialize(dir.resolve(".cpg-predefined-" + name).toString()));
        var expected = Json.parse(fixture.replace("\"FILE_PATH\"", Json.serialize(file.toString()))
                .replace("\"CONFIG_KEY\"", Json.serialize("java-api:" + project.files().getFirst().variantId().orElseThrow()))
                .replace("\"PLATFORM_KEY\"", Json.serialize("java-api:" + project.files().getFirst().variantId().orElseThrow() + ":platform")));
        assertEquals(expected, ProjectJson.encode(project));
        Path out = dir.resolve("snapshot.json");
        Main.run("--from", "cpp", "--input", dir.toString(), "--stage", "resolved", "--json", out.toString());
        SnapshotAssertions.assertEquivalent(expected, Json.parse(out.toFile()));
        assertThrows(IllegalArgumentException.class, () ->
                Main.run("--from", "cpp", "--input", dir.toString(), "--json", out.toString()));
    }

    @Test void unicodeCrLfAndRewritesPreserveOriginalRanges() throws Exception {
        String source = "// Я😀\r\n\tclass Demo { public static void main(String[] args) {\r\n"
                + "/*😀*/\tSystem.out.println((2 + 3));\r\n} }\r\n";
        Path file = dir.resolve("Demo.java");
        Files.writeString(file, source);
        var options = new Cpg.Options(Language.JAVA, Cpg.Stage.FOLDED);
        var p = Cpg.read(dir, options);
        var again = Cpg.read(dir, options);
        assertEquals(ProjectJson.encode(p).toText("  "), ProjectJson.encode(again).toText("  "));
        var copy = p.edit().build();
        copy.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (node instanceof Project || node instanceof Exit) { assertTrue(node.range().isEmpty()); return; }
                assertEquals(file.toString(), node.range().orElseThrow().file());
            }
            @Override public void visit(PrintStatement print) {
                var r = print.range().orElseThrow();
                assertEquals(new SourcePosition(3, 8, source.indexOf("System")), r.start());
                assertEquals("System.out.println((2 + 3));", source.substring(r.start().offset(), r.end().offset()));
            }
            @Override public void visit(IntegerLiteral literal) {
                assertEquals(5, literal.value());
                var r = literal.range().orElseThrow();
                assertEquals("(2 + 3)", source.substring(r.start().offset(), r.end().offset()));
            }
        });
        var end = p.files().getFirst().range().orElseThrow().end();
        assertEquals(new SourcePosition(5, 1, source.length()), end);
    }

    @Test void referencesAcrossFilesUseProjectWideIds() throws Exception {
        Files.writeString(dir.resolve("a.cpp"), "int main() { B::main(); return 0; }");
        Files.writeString(dir.resolve("b.cpp"), "class B { public: static void main() {} };");
        var project = Cpg.read(dir, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        Map<Node,Integer> ids = new IdentityHashMap<>();
        project.walk(new NodeVisitor() {
            @Override public void visit(Node n) { ids.put(n, ids.size()); }
        });
        String text = ProjectJson.encode(project).toText("");
        var call = (CallExpression) ids.keySet().stream().filter(CallExpression.class::isInstance).findFirst().orElseThrow();
        assertTrue(ids.get(call.target().orElseThrow()) > ids.get(call));
        assertTrue(text.replaceAll("\\s+", "").contains("{\"kind\":\"ref\",\"to\":" + ids.get(call.target().orElseThrow()) + "}"));
        assertTrue(ProjectJson.encode(Project.create().build()).equals(Json.parse("{\"id\":0,\"kind\":\"Project\",\"schemaVersion\":1,\"stage\":\"parsed\",\"passRuns\":[],\"history\":[],\"files\":[]}")));
    }
}
