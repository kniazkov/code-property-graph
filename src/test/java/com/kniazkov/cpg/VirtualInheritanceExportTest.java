/* C++03 virtual hierarchy source and semantic exports. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VirtualInheritanceExportTest {
    private static Project parse(String text) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("virtual.cpp", text, Language.CPP)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (kind.isInstance(node)) result.add(kind.cast(node));
        }});
        return result;
    }
    @Test void pureVirtualAndOutOfClassDefinitionsRoundTrip() {
        var original = parse("struct Base { virtual int run(int x = 1) = 0; }; struct Derived : Base { int run(int x = 2); }; int Derived::run(int x) { return x; }");
        String source = Source.render(original.files().getFirst(), Language.CPP);
        assertTrue(source.contains("virtual int run(int x = 1) = 0;"), source);
        assertTrue(source.contains("Derived::run(int x)"), source);
        var reparsed = parse(source);
        for (var project : List.of(original, original.edit().build(), reparsed)) {
            assertTrue(project.diagnostics().isEmpty(), project.diagnostics().toString());
            assertEquals(1, nodes(project, CallableDeclaration.class).stream().filter(CallableDeclaration::isPureVirtual).count());
            String json = ProjectJson.encode(project).toText("");
            assertTrue(json.contains("\"pureVirtual\""), json);
            assertTrue(json.contains("\"overridesIds\""), json);
            assertTrue(json.contains("\"overriddenByIds\""), json);
            String dot = DotVisualizer.toDot(project, false);
            assertTrue(dot.contains("overrides"), dot);
            assertTrue(dot.contains("overridden by"), dot);
            assertTrue(dot.contains("pure virtual"), dot);
            assertFalse(project.languages().contains(Language.JAVA));
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        }
        assertEquals(ProjectJson.encode(original), ProjectJson.encode(original.edit().build()));
    }
    @Test void staticSelectionDefaultsAndVirtualTargetsRemainDistinctOwnedReferences() {
        var original = parse("struct Base { virtual int run(int x = 1) { return x; } }; struct Derived : Base { int run(int x = 2) { return x + 10; } }; int invoke(Base& b) { return b.run(); } int main() { Derived d; return invoke(d) + d.run(); }");
        var copy = original.edit().build();
        for (var project : List.of(original, copy)) {
            assertTrue(project.diagnostics().isEmpty(), project.diagnostics().toString());
            var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
            project.walk(new NodeVisitor() { @Override public void visit(Node node) { owned.add(node); }});
            var calls = nodes(project, CallExpression.class).stream().filter(CallExpression::isVirtualDispatch).toList();
            assertEquals(2, calls.size());
            for (var call : calls) {
                assertTrue(call.arguments().isEmpty());
                assertEquals(1, call.defaultArguments().size());
                assertSame(call.target().orElseThrow(), call.defaultArguments().getFirst().declaration());
                assertFalse(call.virtualTargets().isEmpty());
                assertTrue(owned.containsAll(call.references()));
                assertTrue(owned.containsAll(call.virtualTargets()));
                assertTrue(call.children().stream().noneMatch(call.virtualTargets()::contains));
            }
            String json = ProjectJson.encode(project).toText("");
            for (String key : List.of("staticTargetId", "defaultArguments", "virtualTargetIds", "virtualTargetsComplete")) assertTrue(json.contains("\"" + key + "\""), json);
            String dot = DotVisualizer.toDot(project, false);
            assertTrue(dot.contains("static target"), dot);
            assertTrue(dot.contains("possible virtual implementation"), dot);
            assertTrue(dot.contains("default argument 0 expression"), dot);
            String source = Source.render(project.files().getFirst(), Language.CPP);
            assertTrue(source.contains("b.run()"), source);
            assertTrue(source.contains("d.run()"), source);
            assertEquals(2, nodes(parse(source), CallExpression.class).stream().filter(CallExpression::isVirtualDispatch).count());
        }
        assertEquals(ProjectJson.encode(original), ProjectJson.encode(copy));
        assertNotSame(nodes(original, CallableDeclaration.class).getFirst(), nodes(copy, CallableDeclaration.class).getFirst());
    }
    @Test void javaRejectsPureVirtualEvenWithoutRepeatedVirtualKeyword() {
        var project = parse("class Base { public: virtual int f(); }; class Derived : public Base { public: int f() = 0; };");
        var pure = nodes(project, CallableDeclaration.class).stream().filter(CallableDeclaration::isPureVirtual).findFirst().orElseThrow();
        assertFalse(pure.languages().contains(Language.JAVA));
        assertThrows(IllegalArgumentException.class, () -> pure.source(Language.JAVA));
    }
}
