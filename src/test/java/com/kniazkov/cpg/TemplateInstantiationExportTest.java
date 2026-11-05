/* Specialization syntax and typed instantiation exports. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateInstantiationExportTest {
    @Test void writtenSpecializationsAndExplicitInstantiationsRoundTrip() {
        String text = "template<class T> struct Box { T value; };\n"
                + "template<> struct Box<int> { int value; };\n"
                + "template<class T> struct Box<T*> { T* value; };\n"
                + "template struct Box<double>;\n"
                + "template<class T> T identity(T value) { return value; }\n"
                + "template<> int identity<int>(int value) { return value; }\n"
                + "template double identity<double>(double value);\n"
                + "export template<class T> T exported(T value);\n";
        var project = Project.create().addFile(Parser.parse("syntax.cpp", text, Language.CPP, "c++03")).build();
        for (var snapshot : List.of(project, project.edit().build())) {
            String source = Source.render(snapshot.files().getFirst(), Language.CPP);
            assertTrue(source.contains("template <>"), source);
            assertTrue(source.contains("Box<T * >"), source);
            assertTrue(source.contains("template struct Box<double >;"), source);
            assertTrue(source.contains("template double identity<double >(double value);"), source);
            assertTrue(source.contains("export template <class T >"), source);
            var reparsed = Parser.parse("again.cpp", source, Language.CPP, "c++03").build();
            assertEquals(snapshot.files().getFirst().items().size(), reparsed.items().size());
            assertEquals(List.of(TemplateKind.PRIMARY, TemplateKind.FULL_SPECIALIZATION,
                    TemplateKind.PARTIAL_SPECIALIZATION, TemplateKind.EXPLICIT_INSTANTIATION),
                    reparsed.items().stream().filter(ClassDeclaration.class::isInstance)
                        .map(ClassDeclaration.class::cast).map(ClassDeclaration::templateKind).toList());
            String json = ProjectJson.encode(snapshot).toText("");
            assertTrue(json.contains("EXPLICIT_INSTANTIATION"), json);
            assertTrue(json.contains("FULL_SPECIALIZATION"), json);
            assertTrue(json.contains("PARTIAL_SPECIALIZATION"), json);
            assertTrue(json.contains("exportedTemplate"), json);
        }
    }

    @Test void explicitOnlyInstancesExportWithoutANullCauseOrSyntheticSource() {
        String text = "template<class T> T id(T value) { return value; } template int id<int>(int value);"
                + "template<class T> struct Box { T value; }; template struct Box<int>;";
        var project = NameResolver.resolve(Project.create().addFile(Parser.parse("explicit.cpp", text, Language.CPP, "c++03")).build());
        assertEquals(1, project.templateInstances().size());
        assertEquals(1, project.classTemplateInstances().size());
        assertTrue(project.templateInstances().getFirst().causes().isEmpty());
        assertNull(project.templateInstances().getFirst().cause());
        for (var snapshot : List.of(project, project.edit().build())) {
            String json = ProjectJson.encode(snapshot).toText("");
            assertTrue(json.contains("classTemplateInstances"), json);
            assertFalse(json.contains("\"causeId\":"), json);
            assertTrue(json.contains("INSTANTIATION_REQUEST"), json);
            String source = Source.render(snapshot.files().getFirst(), Language.CPP);
            assertEquals(2, source.split("template <", -1).length - 1, source);
            assertEquals(1, source.split("return value", -1).length - 1, source);
            assertTrue(source.contains("template struct Box<int >;"), source);
        }
    }

    @Test void nestedBodyFailureExportsPrimaryTypeAndOrderedRequestRanges() {
        var project = NameResolver.resolve(Project.create().addFile(Parser.parse("failure.cpp",
                "template<class T> int inner(T x) { return x.size(); }\n"
                + "template<class T> int outer(T x) { return inner(x); }\n"
                + "int main() { return outer(1); }\n", Language.CPP, "c++03")).build());
        assertTrue(project.hasErrors());
        var diagnostic = project.semanticDiagnostics().stream().filter(d -> d.code().equals("template-body-member")).findFirst().orElseThrow();
        assertTrue(diagnostic.message().contains("int"));
        assertEquals(1, diagnostic.primaryRange().start().line());
        assertEquals(List.of(2, 3), diagnostic.relatedRanges().stream().map(r -> r.range().start().line()).toList());
        String json = ProjectJson.encode(project).toText("");
        assertTrue(json.contains("template-body-member"), json);
        assertTrue(json.contains("primaryRange"), json);
        assertTrue(json.contains("relatedRanges"), json);
        assertTrue(json.contains("Cannot access member"), json);
        assertTrue(json.contains("INSTANTIATION_REQUEST"), json);
        assertTrue(project.flows().isEmpty(), "Diagnostic API does not require graph construction");
    }

    @Test void structuredCallableArgumentsHaveOneOwnedJsonOccurrence() {
        for (String text : List.of(
                "template<class T> T id(T x) { return x; } int main() { return id(1); }",
                "template<int N> int value() { return N; } int main() { return value<4>(); }",
                "template<class T> struct Box { T value; }; template<template<class> class C,class T> T read(C<T> x) { return x.value; } int main() { Box<int> x; return read(x); }")) {
            var project = NameResolver.resolve(Project.create().addFile(Parser.parse("arguments.cpp", text, Language.CPP, "c++03")).build());
            assertEquals(1, project.templateInstances().size());
            for (var snapshot : List.of(project, project.edit().build())) {
                var instance = snapshot.templateInstances().getFirst();
                assertFalse(instance.structuredArguments().isEmpty());
                assertEquals(instance.structuredArguments().stream().flatMap(a -> a.type().stream()).toList(), instance.arguments());
                for (TypeNode type : instance.arguments()) assertTrue(instance.structuredArguments().stream().anyMatch(a -> a.type().orElse(null) == type));
                var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
                snapshot.walk(new NodeVisitor() { @Override public void visit(Node n) { assertTrue(owned.add(n)); }});
                String json = ProjectJson.encode(snapshot).toText("");
                assertTrue(json.contains("structuredArguments"), json);
                assertTrue(json.contains("structuredArgumentIds"), json);
                assertTrue(json.contains("argumentIds"), json);
                var matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"kind\"").matcher(json);
                var ids = new HashSet<Integer>();
                while (matcher.find()) assertTrue(ids.add(Integer.parseInt(matcher.group(1))), "Repeated owned JSON ID: " + matcher.group(1));
                assertEquals(owned.size(), ids.size(), "Every owned syntax node appears once in JSON");
                String dot = DotVisualizer.toDot(snapshot, false);
                assertTrue(dot.contains(instance.structuredArguments().getFirst().kind().name()), dot);
                String source = Source.render(snapshot.files().getFirst(), Language.CPP);
                assertEquals(text.split("template<", -1).length - 1, source.split("template <", -1).length - 1, source);
            }
        }
    }

    @Test void callsAndInstantiationRequestsHaveDifferentTypedEdges() {
        var project = NameResolver.resolve(Project.create().addFile(Parser.parse("requests.cpp",
                "template<class T> T id(T value) { return value; } int main() { return id(1) + id(2); }",
                Language.CPP, "c++03")).build());
        assertEquals(1, project.templateInstances().size());
        var instance = project.templateInstances().getFirst();
        assertEquals(2, instance.causes().size());
        for (var snapshot : List.of(project, project.edit().build())) {
            String json = ProjectJson.encode(snapshot).toText("");
            assertTrue(json.contains("CALL_TARGET"), json);
            assertTrue(json.contains("INSTANTIATION_CAUSE"), json);
            assertTrue(json.contains("INSTANTIATION_REQUEST"), json);
            assertTrue(json.contains("requestIds"), json);
            assertTrue(json.contains("causeIds"), json);
            String dot = DotVisualizer.toDot(snapshot, false);
            assertTrue(dot.contains("CALL_TARGET"), dot);
            assertTrue(dot.contains("INSTANTIATION_CAUSE"), dot);
            assertTrue(dot.contains("INSTANTIATION_REQUEST"), dot);
            var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
            snapshot.walk(new NodeVisitor() { @Override public void visit(Node n) { assertTrue(owned.add(n)); }});
            snapshot.walk(new NodeVisitor() { @Override public void visit(Node n) {
                for (Node ref : n.references()) assertTrue(owned.contains(ref), n.getClass() + " -> " + ref.getClass());
            }});
            String source = Source.render(snapshot.files().getFirst(), Language.CPP);
            assertEquals(1, source.split("template <", -1).length - 1, source);
        }
    }
}
