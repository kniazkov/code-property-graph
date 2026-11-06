/* CLI/API/JSON and strict native nested-access regressions for issue #89. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.Language;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NestedClassAccessIT {
    @TempDir Path temp;
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);
    private record Invalid(String name, String source, String code, String spelling) {}

    private List<Map<?, ?>> accessDiagnostics(Object value) {
        var result = new ArrayList<Map<?, ?>>();
        if (value instanceof Map<?, ?> map) {
            if (map.get("diagnostics") instanceof List<?> diagnostics)
                for (Object diagnostic : diagnostics)
                    if (diagnostic instanceof Map<?, ?> item && item.get("code") instanceof String code
                            && code.startsWith("inaccessible-")) result.add(item);
            // Resolution history can retain replaced expressions; inspect the live graph only.
            for (var entry : map.entrySet())
                if (!"history".equals(entry.getKey())) result.addAll(accessDiagnostics(entry.getValue()));
        } else if (value instanceof List<?> list)
            for (Object child : list) result.addAll(accessDiagnostics(child));
        return result;
    }
    private void position(Map<?, ?> position, String source, int offset) {
        String preceding = source.substring(0, offset);
        assertEquals(offset, ((Number) position.get("offset")).intValue());
        assertEquals(1 + preceding.chars().filter(c -> c == '\n').count(), ((Number) position.get("line")).longValue());
        assertEquals(offset - preceding.lastIndexOf('\n'), ((Number) position.get("column")).intValue());
    }
    private void invalid(Invalid example) throws Exception {
        Path work = Files.createDirectory(temp.resolve(example.name()));
        var cli = new CliSandbox(work).source("main.cpp", example.source());
        var project = Cpg.readRecovering(cli.input, OPTIONS);
        assertTrue(project.hasErrors(), example.name());
        assertEquals(1, project.semanticDiagnostics().stream()
                .filter(d -> d.code().startsWith("inaccessible-")).count(), example.name());
        var run = cli.run("cpp", "--stage", "resolved").expectStatus(1);
        run.diagnosticSnapshot();
        var diagnostics = accessDiagnostics(Json.parse(run.work().resolve("snapshot.json").toFile()).toJavaObject());
        assertEquals(accessDiagnostics(ProjectJson.encode(project).toJavaObject()), diagnostics);
        assertEquals(1, diagnostics.size(), example.name());
        var diagnostic = diagnostics.getFirst();
        assertEquals(example.code(), diagnostic.get("code"));
        var range = (Map<?, ?>) diagnostic.get("primaryRange");
        assertNotNull(range);
        assertEquals(cli.input.resolve("main.cpp").toString(), range.get("file"));
        int start = example.source().lastIndexOf(example.spelling());
        assertTrue(start >= 0);
        position((Map<?, ?>) range.get("start"), example.source(), start);
        position((Map<?, ?>) range.get("end"), example.source(), start + example.spelling().length());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
    }
    @Test void allFiveAuditProbesReachCliJsonAndBlockExport() throws Exception {
        // These assert the printed 2003 rule. Modern GCC/Clang apply later
        // nested-class access changes, so they are not rejection oracles here.
        for (Invalid example : List.of(
                new Invalid("nested-method", "class Outer { void secret(); public: class Inner { public: void use(Outer& o) { o.secret(); } }; };", "inaccessible-overload", "o.secret()"),
                new Invalid("nested-field", "class Outer { int secret; public: class Inner { public: int use(Outer& o) { return o.secret; } }; };", "inaccessible-member", "o.secret"),
                new Invalid("enclosing-method", "class Outer { class Inner { void secret(); }; void use(Inner& i) { i.secret(); } };", "inaccessible-overload", "i.secret()"),
                new Invalid("enclosing-field", "class Outer { class Inner { int secret; }; int use(Inner& i) { return i.secret; } };", "inaccessible-member", "i.secret"),
                new Invalid("nested-type", "class Outer { typedef int Secret; public: class Inner { Secret value; }; };", "inaccessible-type", "Secret"))) invalid(example);
    }
    @Test void qualifiedAndUnqualifiedTypesAndFieldsRetainExactMultilineRanges() throws Exception {
        int index = 0;
        for (String declaration : List.of("Secret value;", "Secret* value;", "Secret value[2];", "Outer::Secret value;",
                "Outer::Secret* value;", "typedef Secret Alias;", "typedef Outer::Secret Alias;")) {
            String spelling = (declaration.startsWith("typedef") ? "typedef " : "")
                    + (declaration.contains("Outer::Secret") ? "Outer::Secret" : "Secret");
            invalid(new Invalid("declarator-" + index++, "class Outer { class Secret {};\npublic: class Inner {\n  "
                    + declaration + "\n}; };\n", "inaccessible-type", spelling));
        }
        for (String expression : List.of("secret", "Outer::secret"))
            invalid(new Invalid("static-" + index++, "class Outer { static int secret;\npublic: class Inner { int use(){\n  return "
                    + expression + ";\n} }; };\n", "inaccessible-member", expression));
        for (String name : List.of("A", "B"))
            invalid(new Invalid("qualified-injected-" + name,
                    "class C { class A {}; A *p; class B : A { A *q; B *s;\n C::" + name + " *r; }; };",
                    "inaccessible-type", "C::" + name));
    }
    @Test void validPublicSelfFriendAndInjectedNamesGenerateAndRunOnBothCompilers() throws Exception {
        List<String> sources = List.of(
                """
                class Outer { public: typedef int Number; int field;
                  int method(){return field;}
                  class Inner { public: Number use(Outer& o){return o.method()+o.field;} };
                };
                int main(){Outer o; o.field=3; Outer::Inner i; return i.use(o)==6 ? 0 : 1;}
                """,
                """
                class Outer { typedef int Number; int field; int method(){return field;}
                public: Number use(){field=4; return method()+field;}
                  class Inner { typedef int Number; int field; int method(){return field;}
                  public: Number use(){field=5;return method()+field;} };
                };
                int main(){Outer o; Outer::Inner i; return o.use()+i.use()==18 ? 0 : 1;}
                """,
                """
                class Friend;
                class Outer { typedef int Number; int field; int method(){return field;}
                  friend class Friend; friend int use(Outer&);
                };
                class Friend { public: int use(Outer& o){Outer::Number n=3;o.field=n;return o.method();} };
                int use(Outer& o){Outer::Number n=4;o.field=n;return o.method();}
                int main(){Outer o; Friend f; return f.use(o)+use(o)==7 ? 0 : 1;}
                """,
                // Printed 11.8/2: the enclosing class can name A; B's base-clause
                // can name A; the A and B names in B's body are injected names.
                """
                class C { class A {}; A *p; class B : A { A *q; B *s; }; };
                int main(){C c;return 0;}
                """);
        for (int i = 0; i < sources.size(); i++) {
            Path work = Files.createDirectory(temp.resolve("valid-" + i));
            var cli = new CliSandbox(work).source("main.cpp", sources.get(i));
            var project = Cpg.read(cli.input, OPTIONS);
            assertFalse(project.hasErrors(), project.semanticDiagnostics().toString());
            assertTrue(accessDiagnostics(ProjectJson.encode(project).toJavaObject()).isEmpty());
            var run = cli.run("cpp", "--stage", "resolved").expectStatus(0);
            assertTrue(accessDiagnostics(Json.parse(run.work().resolve("snapshot.json").toFile()).toJavaObject()).isEmpty());
            assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
            for (String compiler : List.of("g++", "clang++"))
                for (Path input : List.of(cli.input, run.work().resolve("generated"))) {
                    Path binary = Files.createTempFile(work, "nested-native-", ".out");
                    var compile = command(List.of(compiler, "-std=c++03", "-pedantic-errors", input.resolve("main.cpp").toString(), "-o", binary.toString()), work);
                    assertEquals(0, compile.status(), compile.output());
                    var execution = command(List.of(binary.toString()), work);
                    assertEquals(0, execution.status(), execution.output());
                    assertEquals("", execution.output());
                }
        }
    }
    private record Result(int status, String output) {}
    private Result command(List<String> command, Path work) throws Exception {
        Path log = Files.createTempFile(work, "native-", ".log");
        Process process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Timed out: " + command);
            return new Result(process.exitValue(), Files.readString(log));
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
    }
}
