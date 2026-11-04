/* Code Property Graph: diagnostics for individual writes. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises reachable writes, joins, cycles, shadowing and diagnostic lifetime. */
class UnreadWritesTest {
    @TempDir Path directory;

    private Project read(String body, Language language, Cpg.Stage stage) throws Exception {
        String source = language == Language.CPP ? "class Demo { public: static void main() {" + body + "} };"
                : "class Demo { public static void main(String[] args) {" + body + "} }";
        Files.writeString(directory.resolve(language == Language.CPP ? "Demo.cpp" : "Demo.java"), source);
        return Cpg.read(directory, new Cpg.Options(language, stage));
    }

    private List<String> warnings(Project project) {
        List<String> fragments = new ArrayList<>();
        project.walk(new NodeVisitor() {
            @Override public void visit(Node n) {
                if (n.diagnostics().isEmpty()) return;
                assertEquals(1, n.diagnostics().size());
                var d = n.diagnostics().getFirst();
                assertEquals(UnreadWrites.CODE, d.code());
                assertEquals(Diagnostic.Severity.WARNING, d.severity());
                var span = n.range().orElseThrow();
                try {
                    fragments.add(Files.readString(Path.of(span.file())).substring(span.start().offset(), span.end().offset()));
                } catch (java.io.IOException e) { throw new AssertionError(e); }
            }
        });
        return fragments;
    }

    @Test void overwrittenWriteAndCallWithSideEffects() throws Exception {
        for (Language language : List.of(Language.JAVA, Language.CPP)) {
            String output = language == Language.CPP ? "std::cout << x << std::endl;" : "System.out.println(x);";
            Project p = read("int x = 1; x = f(); x = 3; " + output, language, Cpg.Stage.RESOLVED);
            assertEquals(List.of("int x = 1;", "x = f()"), warnings(p));
            assertTrue(Cpg.sources(p, language).values().iterator().next().contains("x = f();"));
            assertTrue(ProjectJson.encode(p).toText("").contains("unread-write"));
            assertTrue(DotVisualizer.toDot(p).contains("⚠ unread-write"));
        }
    }

    @Test void oneReadingBranchAndLoopCarriedUseKeepWriteLive() throws Exception {
        var p = read("int x = 1; if (x > 0) x = 2; else x = 3; while (x < 5) x = x + 1; System.out.println(x);",
                Language.JAVA, Cpg.Stage.RESOLVED);
        assertTrue(warnings(p).isEmpty());
        p = read("int x = 1; if (f()) x = 2; System.out.println(x);", Language.JAVA, Cpg.Stage.RESOLVED);
        assertTrue(warnings(p).isEmpty());
        p = read("int x = 1; if (f()) x = 2; else x = 3; System.out.println(x);", Language.JAVA, Cpg.Stage.RESOLVED);
        assertEquals(List.of("int x = 1;"), warnings(p));
    }

    @Test void ignoresUnreachableAndEscapingWritesAndDistinguishesShadowedNames() throws Exception {
        var p = read("int x = 1; { int x = 2; } escape(x); x = 3; return; int y = 9;",
                Language.CPP, Cpg.Stage.RESOLVED);
        assertEquals(List.of("int x = 2;"), warnings(p));
    }

    @Test void expressionResultAndStoredValueHaveDifferentConsumers() throws Exception {
        var p = read("int x = 1; int y = (x = 2); System.out.println(y);", Language.JAVA, Cpg.Stage.RESOLVED);
        assertEquals(List.of("int x = 1;", "(x = 2)"), warnings(p));
    }

    @Test void editingClearsFindingsAndReanalysisDoesNotDuplicateThem() throws Exception {
        var parsed = read("int x = 7;", Language.JAVA, Cpg.Stage.PARSED);
        var resolved = NameResolver.resolve(parsed);
        var result = UnreadWrites.analyze(resolved);
        assertTrue(warnings(resolved).isEmpty());
        assertEquals(List.of("int x = 7;"), warnings(result));
        assertEquals(warnings(result), warnings(UnreadWrites.analyze(result)));
        assertTrue(warnings(result.edit().build()).isEmpty());
        assertTrue(warnings(parsed).isEmpty());
        assertFalse(ProjectJson.encode(parsed).toText("").contains("diagnostics"));
    }
}
