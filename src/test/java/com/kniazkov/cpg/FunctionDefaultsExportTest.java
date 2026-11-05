/* C++03 written defaults and non-owning call-site exports. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FunctionDefaultsExportTest {
    @TempDir Path directory;
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (kind.isInstance(node)) result.add(kind.cast(node));
        }});
        return result;
    }
    private static Project parse(String text) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("defaults.cpp", text, Language.CPP)).build());
    }
    @Test void sharedDefaultExportsReferencesWithoutCloningOrExpandingCalls() {
        var original = parse("int next(); void log(int level = next()); int main() { log(); log(2); log(); return 0; }");
        var copy = original.edit().build();
        for (var project : List.of(original, copy)) {
            var calls = nodes(project, CallExpression.class).stream()
                    .filter(call -> call.callee() instanceof NameExpression name && name.name().equals("log")).toList();
            assertEquals(List.of(0, 1, 0), calls.stream().map(call -> call.arguments().size()).toList());
            assertEquals(List.of(1, 0, 1), calls.stream().map(call -> call.defaultArguments().size()).toList());
            var first = calls.getFirst().defaultArguments().getFirst();
            var last = calls.getLast().defaultArguments().getFirst();
            assertSame(first.expression(), last.expression());
            assertSame(first.parameter().defaultValue().orElseThrow(), first.expression());
            assertFalse(calls.getFirst().children().contains(first.expression()));
            var ids = new IdentityHashMap<Node, Integer>();
            project.walk(new NodeVisitor() { @Override public void visit(Node node) { ids.put(node, ids.size()); }});
            assertEquals(1, nodes(project, CallExpression.class).stream().filter(call -> call == first.expression()).count());
            String json = ProjectJson.encode(project).toText("").replaceAll("\\s+", "");
            String link = "{\"parameterIndex\":0,\"declarationId\":" + ids.get(first.declaration())
                    + ",\"parameterId\":" + ids.get(first.parameter()) + ",\"expressionId\":" + ids.get(first.expression()) + "}";
            assertEquals(2, json.split(java.util.regex.Pattern.quote(link), -1).length - 1, json);
            assertTrue(ids.keySet().containsAll(calls.getFirst().references()));
            String dot = DotVisualizer.toDot(project, false);
            assertTrue(dot.contains("default argument 0 expression"), dot);
            String source = Source.render(project.files().getFirst(), Language.CPP);
            assertTrue(source.contains("level = next()"), source);
            assertFalse(source.contains("log(next())"), source);
            var reparsed = parse(source);
            assertEquals(2, nodes(reparsed, CallExpression.class).stream().filter(call -> !call.defaultArguments().isEmpty()).count());
        }
        assertNotSame(nodes(original, ParameterDeclaration.class).getFirst().defaultValue().orElseThrow(),
                nodes(copy, ParameterDeclaration.class).getFirst().defaultValue().orElseThrow());
        assertEquals(ProjectJson.encode(original), ProjectJson.encode(copy));
    }
    @Test void defaultEditPreservesCommentsMacrosAndOmittedArguments() throws Exception {
        String text = "#define KEEP 7\n// retain this comment\nint f(int value = 2);\nint main() { f(); return KEEP; }\n";
        Files.writeString(directory.resolve("main.cpp"), text);
        var project = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        assertEquals(text, Cpg.sources(project, Language.CPP).get("main.cpp"));
        var parameter = nodes(project, ParameterDeclaration.class).getFirst();
        var editor = new ProjectEditor(project);
        editor.replace(parameter.defaultValue().orElseThrow(), IntegerLiteral.create().setValue(3).build());
        var edited = NameResolver.resolve(editor.build());
        String source = Cpg.sources(edited, Language.CPP).get("main.cpp");
        assertEquals(text.replace("value = 2", "value = 3"), source);
        assertEquals(1, nodes(parse(source), CallExpression.class).stream().filter(call -> !call.defaultArguments().isEmpty()).count());
        assertEquals(text, Cpg.sources(project, Language.CPP).get("main.cpp"));
    }
    @Test void javaRejectsDefaultParametersAndCppFunctionQualifiers() {
        for (String source : List.of("class A { static int f(int x = 2) { return x; } };",
                "class A { int f(int x, ...); };", "class A { int f() const; };")) {
            var project = parse(source);
            assertFalse(project.languages().contains(Language.JAVA));
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        }
    }
}
