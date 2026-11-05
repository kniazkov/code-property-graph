/* Common sequence operations participate in reaching definitions. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicArrayFlowTest {
    private Project analyze(String body) {
        return UnreadWrites.analyze(DynamicArraySemantics.green(NameResolver.resolve(Project.create()
                .addFile(Parser.parse("array.cpp", "#include <vector>\n" + body, Language.CPP)).build())));
    }
    private List<FunctionFlow.DataEdge> edges(Project p) {
        return p.flows().stream().flatMap(flow -> flow.data().stream()).toList();
    }
    @Test void commonOperationsReadInputsAndDefineSubsequentContainerReads() {
        var p = analyze("int f(){ int n=2; std::vector<int> a(n,7); a.push_back(9); return a.size(); }");
        assertFalse(p.hasErrors());
        assertEquals(3, p.dynamicArraySemantics().size());
        var fill = p.dynamicArraySemantics().get(0);
        var append = p.dynamicArraySemantics().get(1);
        for (var operation : p.dynamicArraySemantics()) for (var input : operation.sources())
            assertTrue(edges(p).stream().anyMatch(e -> e.from() == input && e.to() == operation
                    && e.kind() == FunctionFlow.Dependency.VALUE));
        assertTrue(edges(p).stream().anyMatch(e -> e.from() == fill && e.to() == append));
        assertTrue(edges(p).stream().anyMatch(e -> e.from() == append && e.to() instanceof NameExpression n && n.name().equals("a")));
        assertTrue(p.semanticDiagnostics().stream().noneMatch(d -> d.code().equals(UnreadWrites.CODE)));
    }
    @Test void assignmentKeepsReferenceIdentityAndCopiesHaveIndependentState() {
        var p = analyze("int f(){ std::vector<int> a(2,7); std::vector<int> b=a; std::vector<int>& r=a; r=b; b.push_back(9); return r.size()+a.size(); }");
        var assignment = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.ASSIGN_COPY).findFirst().orElseThrow();
        var append = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.APPEND).findFirst().orElseThrow();
        for (String name : List.of("a", "r")) assertTrue(edges(p).stream().anyMatch(e -> e.from() == assignment
                && e.to() instanceof NameExpression n && n.name().equals(name)), name);
        assertFalse(edges(p).stream().anyMatch(e -> e.from() == append && e.to() instanceof NameExpression n && n.name().equals("a")));
    }
    @Test void pointerElementsKeepOriginalPointeeInputsWithoutAliasingContainers() {
        var p = analyze("int f(){ int x=1; int* pointer=&x; std::vector<int*> a(1,pointer); std::vector<int*> b=a; b.push_back(pointer); return a.size(); }");
        var fill = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.FILL).findFirst().orElseThrow();
        var copy = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.COPY).findFirst().orElseThrow();
        var append = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.APPEND).findFirst().orElseThrow();
        assertTrue(fill.type().elementType() instanceof PointerType);
        assertSame(fill.type(), copy.type());
        assertTrue(edges(p).stream().anyMatch(e -> e.from() == fill && e.to() instanceof NameExpression n && n.name().equals("a")));
        assertFalse(edges(p).stream().anyMatch(e -> e.from() == append && e.to() instanceof NameExpression n && n.name().equals("a")));
        // Both copies reference actual written inputs; no synthetic pointee is created.
        assertTrue(fill.sources().stream().anyMatch(n -> n instanceof NameExpression name && name.name().equals("pointer")
                || n instanceof ConversionExpression conversion && conversion.operand() instanceof NameExpression converted && converted.name().equals("pointer")));
        assertTrue(copy.sources().stream().anyMatch(n -> n instanceof NameExpression name && name.name().equals("a")
                || n instanceof ConversionExpression conversion && conversion.operand() instanceof NameExpression converted && converted.name().equals("a")));
    }
    @Test void selectedElementCopyRetainsObservableEffectsAndExceptionPaths() {
        var p = analyze("int copies; struct R { R() {} R(const R& r) { ++copies; } }; int f(){ int x=1; int* p=&x; R r; std::vector<R> a; try { a.push_back(r); } catch (...) { return x; } return copies+x; }");
        var append = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.APPEND).findFirst().orElseThrow();
        assertTrue(append.elementCopyConstructor().isPresent());
        assertTrue(edges(p).stream().anyMatch(e -> e.from() == append && e.to() instanceof NameExpression n && n.name().equals("x")));
        assertTrue(p.flows().stream().flatMap(f -> f.control().stream()).anyMatch(e -> e.kind() == FunctionFlow.Branch.EXCEPTION));
        assertTrue(p.semanticDiagnostics().stream().noneMatch(d -> d.code().equals(UnreadWrites.CODE) && d.message().contains("'x'")));
    }
    @Test void implicitRecordCopiesRetainMemberAndBaseEffects() {
        for (String outer : List.of("struct Outer { Inner inner; };", "struct Outer : Inner {};")) {
            var p = analyze("int count; struct Inner { Inner(){} Inner(const Inner& x){ ++count; } }; "
                    + outer + " int f(){ Outer o; std::vector<Outer> a; count=7; a.push_back(o); return count; }");
            var append = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.APPEND).findFirst().orElseThrow();
            assertTrue(append.elementCopyConstructor().isEmpty(), "Outer has only an implicit copy");
            assertTrue(edges(p).stream().anyMatch(e -> e.from() == append && e.to() instanceof NameExpression n
                    && n.name().equals("count") && e.kind() == FunctionFlow.Dependency.DEFINITION), outer);
            assertTrue(edges(p).stream().anyMatch(e -> e.to() == append && e.from() instanceof AssignmentExpression
                    && e.kind() == FunctionFlow.Dependency.DEFINITION), "Element code may read incoming count");
        }
    }
    @Test void implicitCopyAndEffectfulDestructorRetainAccessibleReadsAndWrites() {
        var p = analyze("int count; struct R { ~R(){ ++count; } }; int f(){ int x=7; int* address=&x; R r; std::vector<R> a; a.push_back(r); a.push_back(r); x=9; return count+x; }");
        var appends = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.APPEND).toList();
        assertEquals(2, appends.size());
        for (var append : appends) {
            assertTrue(append.elementCopyConstructor().isEmpty());
            assertTrue(edges(p).stream().anyMatch(e -> e.from() == append && e.to() instanceof NameExpression n
                    && n.name().equals("count") && e.kind() == FunctionFlow.Dependency.DEFINITION));
        }
        assertTrue(edges(p).stream().anyMatch(e -> e.to() == appends.getFirst() && e.from() instanceof VariableDeclaration v
                && v.name().equals("x") && e.kind() == FunctionFlow.Dependency.DEFINITION));
        assertTrue(p.semanticDiagnostics().stream().noneMatch(d -> d.code().equals(UnreadWrites.CODE) && d.message().contains("'x'")));
    }
    @Test void foreignSemanticEndpointsAreRejectedAtBothBoundaries() throws Exception {
        var p = analyze("int f(){ std::vector<int> a(2,7); return a.size(); }");
        var other = analyze("int g(){ std::vector<int> a(2,7); return a.size(); }");
        var flow = p.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow();
        var op = p.dynamicArraySemantics().getFirst();
        var foreign = other.dynamicArraySemantics().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new FunctionFlow(flow.entry(), flow.exit(), flow.control(),
                List.of(new FunctionFlow.DataEdge(foreign, op.syntax(), FunctionFlow.Dependency.VALUE))));
        var forged = op.edit().build();
        // A copied syntax node is not owned by the analyzed callable either.
        assertThrows(IllegalArgumentException.class, () -> new FunctionFlow(flow.entry(), flow.exit(), flow.control(),
                List.of(new FunctionFlow.DataEdge(forged, op.syntax(), FunctionFlow.Dependency.VALUE))));
        // A forged operation with legitimate local syntax still needs project ownership.
        var syntax = DynamicArrayOperation.class.getDeclaredField("syntax");
        syntax.setAccessible(true); syntax.set(forged, op.syntax());
        var forgedFlow = new FunctionFlow(flow.entry(), flow.exit(), flow.control(),
                List.of(new FunctionFlow.DataEdge(forged, op.syntax(), FunctionFlow.Dependency.VALUE)));
        var flows = new ArrayList<>(p.flows()); flows.set(flows.indexOf(flow), forgedFlow);
        assertThrows(IllegalArgumentException.class, () -> p.withFlows(flows));
    }
}
