/* C++03 statement syntax, exception metadata and flow-only exports. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StatementExceptionExportTest {
    private static Project parse(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("statements.cpp", source, Language.CPP)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); } });
        return result;
    }
    @Test void loopsSwitchLabelsAndEmptyStatementsRoundTrip() {
        var project = parse("int f(int x) { ; for (int i = 0, j = 2; i < j; ++i) { if (i) continue; x += i; } do { --x; } while (x > 2); switch (x) { case 0: ; case 1: x = 2; break; default: x = 3; } goto done; done: return x; }");
        assertFalse(project.hasErrors(), ProjectJson.encode(project).toText(""));
        String source = Source.render(project.files().getFirst(), Language.CPP);
        var again = parse(source);
        assertFalse(again.hasErrors(), source);
        for (var kind : List.of(EmptyStatement.class, ForStatement.class, DoWhileStatement.class, CaseStatement.class, LabelStatement.class, GotoStatement.class))
            assertEquals(nodes(project, kind).size(), nodes(again, kind).size(), source);
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(project.edit().build()));
        assertTrue(DotVisualizer.toDot(project, false).contains("done"));
    }
    @Test void exceptionSpecificationsAndHandlersRoundTrip() {
        var project = parse("void quiet() throw(); int f(int x) throw(int) { try { if (x) throw x; quiet(); } catch (int value) { return value; } catch (...) { throw; } return 0; }");
        assertFalse(project.hasErrors());
        String source = Source.render(project.files().getFirst(), Language.CPP);
        assertTrue(source.contains("throw()"), source);
        assertTrue(source.contains("throw(int)"), source);
        assertTrue(source.contains("catch (...)"), source);
        var again = parse(source);
        assertFalse(again.hasErrors(), source);
        assertEquals(2, nodes(again, CatchClause.class).size());
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(project.edit().build()));
        assertTrue(ProjectJson.encode(project).toText("").contains("\"rethrow\": true"));
        assertThrows(IllegalArgumentException.class, () -> Source.render(project.files().getFirst(), Language.JAVA));
    }
    @Test void constructorFunctionTryBlockKeepsInitializerBeforeBody() {
        var project = parse("struct Guard { int value; Guard(int x) try : value(x) { } catch (...) { throw; } }; ");
        assertFalse(project.hasErrors());
        String source = Source.render(project.files().getFirst(), Language.CPP);
        assertTrue(source.contains("try : value(x)"), source);
        assertTrue(nodes(parse(source), CallableDeclaration.class).getFirst().functionTryBlock());
    }
    @Test void incompleteBodiesCannotBeGeneratedButCanBeExported() {
        var body = BlockStatement.create().setComplete(false);
        var function = FunctionDeclaration.create().setName("broken").setReturnType(NamedType.create().setName("void")).setBody(body).setBodyComplete(false);
        var project = Project.create().addFile(SourceFile.create().setPath("broken.cpp").setLanguage(Language.CPP).addItem(function)).build();
        assertThrows(IllegalArgumentException.class, () -> Source.render(project.files().getFirst(), Language.CPP));
        assertTrue(ProjectJson.encode(project).toText("").contains("\"bodyComplete\": false"));
        assertTrue(DotVisualizer.toDot(project, false).contains("incomplete body"));
    }
    @Test void auxiliaryVerticesAreStableNonOwningAndNeverEmittedAsSource() {
        var project = parse("void f() { int value = 1; }");
        var function = nodes(project, CallableDeclaration.class).getFirst();
        var variable = nodes(project, VariableDeclaration.class).getFirst();
        var cleanup = new AuxiliaryFlowVertex(function, 0, AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR, variable, null);
        var exceptional = new AuxiliaryFlowVertex(function, 1, AuxiliaryFlowVertex.Kind.EXCEPTIONAL_EXIT, null, null);
        var terminate = new AuxiliaryFlowVertex(function, 2, AuxiliaryFlowVertex.Kind.TERMINATE_EXIT, null, null);
        var flow = new FunctionFlow(function, function.exit(), List.of(
                new FunctionFlow.ControlEdge(function, variable, FunctionFlow.Branch.ENTRY),
                new FunctionFlow.ControlEdge(variable, cleanup, FunctionFlow.Branch.CLEANUP),
                new FunctionFlow.ControlEdge(cleanup, function.exit(), FunctionFlow.Branch.NEXT),
                new FunctionFlow.ControlEdge(variable, exceptional, FunctionFlow.Branch.EXCEPTION),
                new FunctionFlow.ControlEdge(cleanup, terminate, FunctionFlow.Branch.EXCEPTION)), List.of());
        var analyzed = project.withFlows(List.of(flow));
        String json = ProjectJson.encode(analyzed).toText("");
        assertTrue(json.contains("IMPLICIT_DESTRUCTOR"), json);
        assertTrue(json.contains("EXCEPTIONAL_EXIT"), json);
        assertTrue(json.contains("TERMINATE_EXIT"), json);
        assertTrue(json.contains("objectId"), json);
        assertEquals(json, ProjectJson.encode(analyzed).toText(""));
        var copied = analyzed.withDiagnostics("export-probe", Map.of(variable,
                new Diagnostic("export-probe", Diagnostic.Severity.INFO, "Copy the snapshot")));
        var copiedCleanup = copied.flows().getFirst().auxiliaryVertices().getFirst();
        assertNotSame(variable, copiedCleanup.object().orElseThrow());
        assertSame(copied.flows().getFirst().entry(), copiedCleanup.owner());
        assertEquals(json, ProjectJson.encode(copied.withDiagnostics("export-probe", Map.of())).toText(""));
        assertEquals(nodes(project, Node.class).size(), nodes(analyzed, Node.class).size());
        assertEquals(Source.render(project.files().getFirst(), Language.CPP), Source.render(analyzed.files().getFirst(), Language.CPP));
        String dot = DotVisualizer.toDot(analyzed, false);
        assertTrue(dot.contains("IMPLICIT_DESTRUCTOR"), dot);
        assertTrue(dot.contains("object ref"), dot);
        assertTrue(dot.contains("CFG exception"), dot);
    }

    @Test void forDeclaratorsRetainSharedSpecifiersAndOptionalSlots() {
        var file = Parser.parse("for.cpp", "void f() { for (int *p = 0, *q = 0; ; ) { break; } for (;;) ; }", Language.CPP).build();
        String source = Source.render(file, Language.CPP);
        assertTrue(source.contains("int *p = 0, *q = 0"), source);
        var again = Parser.parse("for.cpp", source, Language.CPP).build();
        assertFalse(again.hasErrors(), source);
        assertEquals(2, nodes(again, ForStatement.class).size());
        assertTrue(nodes(again, ForStatement.class).getLast().initializers().isEmpty());
        assertTrue(nodes(again, ForStatement.class).getLast().condition().isEmpty());
    }

    @Test void implicitConstructorReferenceIsNotExportedAsDestructor() {
        var project = parse("struct Guard { Guard(); }; void f() { Guard value; }");
        var constructor = nodes(project, CallableDeclaration.class).stream()
                .filter(c -> c.kind() == CallableDeclaration.Kind.CONSTRUCTOR).findFirst().orElseThrow();
        var function = nodes(project, CallableDeclaration.class).stream().filter(c -> c.name().equals("f")).findFirst().orElseThrow();
        var variable = nodes(project, VariableDeclaration.class).getFirst();
        var operation = new AuxiliaryFlowVertex(function, 0, AuxiliaryFlowVertex.Kind.IMPLICIT_CONSTRUCTOR, variable, constructor);
        var flow = new FunctionFlow(function, function.exit(), List.of(
                new FunctionFlow.ControlEdge(function, operation, FunctionFlow.Branch.ENTRY),
                new FunctionFlow.ControlEdge(operation, function.exit(), FunctionFlow.Branch.NEXT)), List.of());
        var analyzed = project.withFlows(List.of(flow));
        assertSame(constructor, operation.constructor().orElseThrow());
        assertTrue(operation.destructor().isEmpty());
        String json = ProjectJson.encode(analyzed).toText("");
        assertTrue(json.contains("constructorId"), json);
        assertFalse(json.contains("destructorId"), json);
        String dot = DotVisualizer.toDot(analyzed, false);
        assertTrue(dot.contains("constructor ref"), dot);
        assertFalse(dot.contains("destructor ref"), dot);
        var copied = analyzed.withDiagnostics("constructor-copy", Map.of(variable,
                new Diagnostic("constructor-copy", Diagnostic.Severity.INFO, "Copy constructor reference")));
        var copiedOperation = copied.flows().getFirst().auxiliaryVertices().getFirst();
        assertNotSame(constructor, copiedOperation.constructor().orElseThrow());
        assertTrue(copiedOperation.destructor().isEmpty());
        assertEquals(json, ProjectJson.encode(copied.withDiagnostics("constructor-copy", Map.of())).toText(""));
    }

    @Test void flowIdsDoNotDependOnExternalConfigurationCatalogueSize() {
        var project = parse("void work(); void f() { work(); }");
        var extra = project.edit().addExternalInput(ExternalInput.create("extra", "test-attribution", "CLI origin")
                .addInput(ExternalInput.create("extra:argument", "test-argument", "--cpp-standard=c++03"))).build();
        var originalFlow = com.kniazkov.cpg.analysis.ControlFlow.analyze(project);
        var extraFlow = com.kniazkov.cpg.analysis.ControlFlow.analyze(NameResolver.resolve(extra));
        assertFalse(originalFlow.flows().getFirst().auxiliaryVertices().isEmpty());
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(originalFlow), ProjectJson.encode(extraFlow));
    }

}
