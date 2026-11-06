/* C++ reference binding reads remain distinct from referred storage. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceBindingDataFlowTest {
    @TempDir Path directory;

    private Project analyze(String source) {
        var resolved = NameResolver.resolve(Project.create()
                .addFile(Parser.parse("references.cpp", source, Language.CPP)).build());
        assertFalse(resolved.hasErrors(), resolved.semanticDiagnostics().toString());
        return UnreadWrites.analyze(resolved);
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        return FlowTest.nodes(root, type);
    }
    private FunctionFlow flow(Project project, String name) {
        return project.flows().stream().filter(f -> f.entry().name().equals(name)).findFirst().orElseThrow();
    }
    private VariableDeclaration local(FunctionFlow flow, String name) {
        return nodes(flow.entry(), VariableDeclaration.class).stream()
                .filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }
    private Expression returned(FunctionFlow flow) {
        Expression value = nodes(flow.entry(), ReturnStatement.class).getLast().value().orElseThrow();
        while (value instanceof ConversionExpression conversion) value = conversion.operand();
        return value;
    }
    private Set<Node> definitions(FunctionFlow flow, Node read) {
        Set<Node> result = Collections.newSetFromMap(new IdentityHashMap<>());
        flow.data().stream().filter(e -> e.kind() == FunctionFlow.Dependency.DEFINITION && e.to() == read)
                .forEach(e -> result.add(e.from()));
        return result;
    }
    private void assertBindingRead(FunctionFlow flow, String name) {
        var binding = local(flow, name);
        assertTrue(binding.diagnostics().isEmpty(), binding.diagnostics().toString());
        assertTrue(flow.data().stream().anyMatch(e -> e.kind() == FunctionFlow.Dependency.DEFINITION
                && e.from() == binding && e.to() instanceof NameExpression use
                && use.target().orElse(null) == binding), "An evaluated use must depend on its reference binding");
    }

    @Test void scalarReadUsesBothBindingAndCurrentReferent() {
        var project = analyze("int f(){int x=1;int& r=x;r=7;return r;}");
        var flow = flow(project, "f");
        assertBindingRead(flow, "r");
        assertEquals(Set.of(local(flow, "r"), nodes(flow.entry(), AssignmentExpression.class).getFirst()),
                definitions(flow, returned(flow)));
        assertFalse(definitions(flow, returned(flow)).contains(local(flow, "x")),
                "Reading the binding must not resurrect an overwritten referent definition");
    }

    @Test void writeThroughReferenceReadsBindingWithoutReadingOldReferent() {
        var project = analyze("int f(){int x=1;int& r=x;r=7;return x;}");
        var flow = flow(project, "f");
        var assignment = nodes(flow.entry(), AssignmentExpression.class).getFirst();
        assertBindingRead(flow, "r");
        assertEquals(Set.of(local(flow, "r")), definitions(flow, assignment.target()));
        assertEquals(Set.of(assignment), definitions(flow, returned(flow)));
    }

    @Test void referenceMemberReceiverReadsBinding() {
        for (String body : List.of("r.value=7;return s.value;", "return r.value;", "return r.get();")) {
            var project = analyze("struct S{int value;int get(){return value;}};"
                    + "int f(){S s;s.value=1;S& r=s;" + body + "}");
            assertBindingRead(flow(project, "f"), "r");
        }
    }

    @Test void referenceToPointerKeepsBindingAcrossPointerReassignment() {
        var project = analyze("int f(){int x=1;int y=2;int* p=&x;int*& r=p;r=&y;*r=7;return y;}");
        var flow = flow(project, "f");
        var writes = nodes(flow.entry(), AssignmentExpression.class);
        assertBindingRead(flow, "r");
        assertEquals(Set.of(local(flow, "r")), definitions(flow, writes.getFirst().target()));
        var pointerRead = ((DereferenceExpression) writes.getLast().target()).operand();
        assertEquals(Set.of(local(flow, "r"), writes.getFirst()), definitions(flow, pointerRead));
        assertEquals(Set.of(writes.getLast()), definitions(flow, returned(flow)));
    }

    @Test void addressOfReferenceUsesBinding() {
        var project = analyze("int f(){int x=1;int& r=x;int* p=&r;return *p;}");
        assertBindingRead(flow(project, "f"), "r");
    }

    @Test void chainedAndTypedefReferencesReadEachBinding() {
        var project = analyze("typedef int& Reference;int f(){int x=1;Reference r=x;Reference s=r;return s;}");
        var flow = flow(project, "f");
        assertBindingRead(flow, "r");
        assertBindingRead(flow, "s");
        assertEquals(Set.of(local(flow, "x"), local(flow, "s")), definitions(flow, returned(flow)));
    }

    @Test void passingReferenceToIgnoringCalleeStillReadsBinding() {
        var project = analyze("void ignore(int&){} int f(){int x=1;int& r=x;ignore(r);return 0;}");
        assertBindingRead(flow(project, "f"), "r");
    }

    @Test void referenceParameterWritesAreNotMistakenForBindingReads() {
        var project = analyze("void update(int& x){x=3;x=7;}int f(){int x=1;update(x);return x;}");
        var callee = flow(project, "update");
        var writes = nodes(callee.entry(), AssignmentExpression.class);
        assertTrue(writes.getFirst().diagnostics().stream().anyMatch(d -> d.code().equals(UnreadWrites.CODE)));
        assertTrue(definitions(callee, writes.getLast().target()).isEmpty());
        assertEquals(Set.of(writes.getLast()), definitions(flow(project, "f"), returned(flow(project, "f"))));
    }

    @Test void unusedOrUnevaluatedReferencesStillWarn() {
        for (String body : List.of("return 0;", "return sizeof(r);", "return 0; r=7;", "if(false)r=7;return 0;")) {
            var project = analyze("int f(){int x=1;int& r=x;" + body + "}");
            var flow = flow(project, "f");
            var binding = local(flow, "r");
            assertTrue(binding.diagnostics().stream().anyMatch(d -> d.code().equals(UnreadWrites.CODE)), body);
            assertFalse(flow.data().stream().anyMatch(e -> e.kind() == FunctionFlow.Dependency.DEFINITION
                    && e.from() == binding), body);
        }
    }

    @Test void crossTranslationUnitVirtualCallsReadLocalReferenceAtEveryStage() throws Exception {
        Files.writeString(directory.resolve("evaluator.h"), """
                struct Evaluator { virtual int apply(int value) const = 0; virtual ~Evaluator() {} };
                struct Doubler : Evaluator { int apply(int value) const { return value + value; } };
                int evaluate(const Evaluator& evaluator, int value);
                """);
        Files.writeString(directory.resolve("evaluate.cpp"), """
                #include "evaluator.h"
                int evaluate(const Evaluator& evaluator, int value) { return evaluator.apply(value); }
                """);
        Files.writeString(directory.resolve("main.cpp"), """
                #include "evaluator.h"
                int main() {
                    Doubler doubler;
                    const Evaluator& evaluator = doubler;
                    int first = evaluate(evaluator, 3);
                    return first + evaluate(evaluator, 4);
                }
                """);
        for (Cpg.Stage stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
            var project = Cpg.read(directory, new Cpg.Options(Language.CPP, stage, "c++03"));
            assertFalse(project.hasErrors(), project.semanticDiagnostics().toString());
            var flow = flow(project, "main");
            assertBindingRead(flow, "evaluator");
            var binding = local(flow, "evaluator");
            var uses = nodes(flow.entry(), NameExpression.class).stream()
                    .filter(n -> n.target().orElse(null) == binding).toList();
            assertEquals(2, uses.size());
            for (var use : uses) assertTrue(definitions(flow, use).contains(binding), stage.toString());
        }
    }
}
