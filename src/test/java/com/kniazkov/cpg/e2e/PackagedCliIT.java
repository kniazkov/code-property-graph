/* Code Property Graph: end-to-end cases for the distributable JAR. MIT License. */
package com.kniazkov.cpg.e2e;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Runs after packaging, using only source inputs, CLI arguments and observable outputs. */
class PackagedCliIT {
    @TempDir Path temp;

    @Test void completeCppSnapshotAndSource() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", "int main() { return 0; }");
        var run = cli.run("cpp", "--stage", "resolved").expectStatus(0);
        run.expectSources(Map.of("main.cpp", "/e2e/return-zero.cpp"));
        run.snapshot().exactly("/json/return-zero.json", Map.of("FILE_PATH", cli.input.resolve("main.cpp").toString()));
        assertFalse(run.stderr().contains("warning ["), run.stderr());
    }

    @Test void foldedTranslationKeepsLiveLinksAndMarksOnlyTheOverwrittenWrite() throws Exception {
        var cli = new CliSandbox(temp).source("Demo.java", """
                public class Demo {
                    public static void main(String[] args) {
                        int score = 1;
                        score = 2 + 3;
                        System.out.println(score);
                    }
                }
                """);
        var run = cli.run("java", "--stage", "folded", "--to", "cpp").expectStatus(0);
        run.expectSources(Map.of("Demo.cpp", "/e2e/folded-score.cpp"));
        var graph = run.snapshot();
        String decl = """
                {"kind":"VariableDeclaration","name":"score","diagnostics":[{"code":"unread-write","severity":"warning"}]}
                """;
        String assignment = """
                {"kind":"AssignmentExpression","value":{"kind":"IntegerLiteral","value":5}}
                """;
        // The LHS also spells score. Its use as a read is distinguished by the outgoing value edge.
        String read = """
                {"kind":"NameExpression","name":"score","edges":[{"kind":"dfg.value"}]}
                """;
        graph.containsNode(decl);
        graph.expectEdge(read, "ref", decl);
        graph.expectEdge(assignment, "dfg.definition", read);
        graph.containsNode("""
                {"kind":"PrintStatement","value":{"name":"score"}}
                """);
        graph.excludesNode("{\"kind\":\"AddExpression\"}");
        graph.excludesNode("{\"kind\":\"CallExpression\"}");
        graph.excludesNode("{\"kind\":\"AssignmentExpression\",\"diagnostics\":[{\"code\":\"unread-write\"}]}");
        assertTrue(run.stderr().contains("Demo.java:3:9: warning [unread-write]"), run.stderr());

        var parsed = cli.run("java", "--stage", "parsed").snapshot();
        parsed.containsNode("{\"kind\":\"AddExpression\"}");
        parsed.containsNode("{\"kind\":\"CallExpression\"}");
        parsed.excludesNode("{\"kind\":\"PrintStatement\"}");
        parsed.excludesNode("{\"diagnostics\":[]}");
        parsed.excludesNode("{\"edges\":[]}");
    }

    @Test void malformedSecondFileFailsWithoutPartialExports() throws Exception {
        var cli = new CliSandbox(temp)
                .source("a.cpp", "int main() { return 0; }")
                .source("z.cpp", "int main() {\n    @\n}");
        var run = cli.run("cpp").expectStatus(1);
        assertTrue(run.stderr().contains("z.cpp:2:5: Unsupported character: @"), run.stderr());
        run.expectNoExports();
    }

    @Test void unsupportedTranslationFailsBeforeWritingAnyOutput() throws Exception {
        var cli = new CliSandbox(temp).source("Demo.java",
                "class Demo { public static void main(String[] args) { System.out.println(1); } }");
        var run = cli.run("java", "--stage", "parsed", "--to", "cpp").expectStatus(1);
        assertTrue(run.stderr().contains("Translate console output at GREEN or FOLDED stage"), run.stderr());
        run.expectNoExports();
    }
}
