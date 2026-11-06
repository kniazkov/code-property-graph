/* C++03 object operators retain their written meaning in exports. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ObjectMemoryExportTest {
    private static Project parse(String text) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("memory.cpp", text, Language.CPP)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    @Test void castsRetainKindsAndRejectJava() {
        var project = parse("int f(int x, const int* p) { int a = (int)x; int b = int(x); int c = static_cast<int>(x); int* q = const_cast<int*>(p); void* r = reinterpret_cast<void*>(q); return a + b + c; }");
        String source = Source.render(project.files().getFirst(), Language.CPP);
        assertTrue(source.contains("const_cast<int *>"), source);
        assertTrue(source.contains("reinterpret_cast<void *>"), source);
        assertEquals(nodes(project, ConversionExpression.class).stream().map(ConversionExpression::kind).toList(),
                nodes(parse(source), ConversionExpression.class).stream().map(ConversionExpression::kind).toList());
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(project.edit().build()));
        assertThrows(IllegalArgumentException.class, () -> Source.render(project.files().getFirst(), Language.JAVA));
    }
    @Test void allocationsTypeidAndDeletionsRoundTrip() {
        var project = parse("#include <typeinfo>\nvoid f(void* arena) { int* a = new int; int* b = new int(); int* c = new int[3]; int* d = new (arena) int(7); int* e = ::new (int); typeid(int); typeid(*a); delete a; delete b; delete[] c; ::delete e; }");
        String source = Source.render(project.files().getFirst(), Language.CPP);
        var reparsed = parse(source);
        var before = nodes(project, NewExpression.class);
        var after = nodes(reparsed, NewExpression.class);
        assertEquals(5, before.size());
        assertEquals(before.stream().map(NewExpression::hasInitializer).toList(), after.stream().map(NewExpression::hasInitializer).toList());
        assertEquals(before.stream().map(NewExpression::isGlobal).toList(), after.stream().map(NewExpression::isGlobal).toList());
        assertEquals(before.stream().map(NewExpression::isParenthesizedType).toList(), after.stream().map(NewExpression::isParenthesizedType).toList());
        assertEquals(2, nodes(reparsed, TypeidExpression.class).size());
        assertEquals(List.of(false, false, true, false), nodes(reparsed, DeleteExpression.class).stream().map(DeleteExpression::isArray).toList());
        String json = ProjectJson.encode(project).toText("");
        assertTrue(json.contains("\"placementArguments\""));
        assertTrue(json.contains("\"hasInitializer\""));
        assertEquals(json, ProjectJson.encode(project.edit().build()).toText(""));
        String dot = DotVisualizer.toDot(project, false);
        assertTrue(dot.contains("placement"), dot);
        assertTrue(dot.contains("delete[]"), dot);
        assertThrows(IllegalArgumentException.class, () -> Source.render(project.files().getFirst(), Language.JAVA));
    }
    @Test void namedDynamicCastDoesNotBecomeStaticCast() {
        var project = parse("struct Base {}; struct Child : public Base {}; Base* f(Child* p) { return dynamic_cast<Base*>(p); }");
        String source = Source.render(project.files().getFirst(), Language.CPP);
        assertTrue(source.contains("dynamic_cast<Base *>"), source);
        assertFalse(source.contains("static_cast"), source);
        assertEquals(ConversionExpression.Kind.DYNAMIC_CAST, nodes(parse(source), ConversionExpression.class).getFirst().kind());
    }
}
