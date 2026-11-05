/* Invocation identity and immutable ownership of cross-function DFG edges. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContextualDataEdgeTest {
    private Project graph() {
        return ControlFlow.analyze(NameResolver.resolve(Project.create().addFile(Parser.parse("context.cpp",
                "void update(int& x){x=7;} void unrelated(int& x){x=9;} void bridge(int& x){update(x);} int main(){int a=1;bridge(a);return a;}", Language.CPP)).build()));
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node n) { if(type.isInstance(n))result.add(type.cast(n)); } });
        return result;
    }
    private FunctionFlow flow(Project p, String name) { return p.flows().stream().filter(f->f.entry().name().equals(name)).findFirst().orElseThrow(); }
    private FunctionFlow contextual(Project p) {
        var main=flow(p,"main"); var call=nodes(main.entry(),CallExpression.class).getFirst();
        var write=nodes(flow(p,"update").entry(),AssignmentExpression.class).getFirst();
        var read=nodes(nodes(main.entry(),ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        return new FunctionFlow(main.entry(),main.exit(),main.control(),List.of(new FunctionFlow.DataEdge(write,read,FunctionFlow.Dependency.DEFINITION,call)));
    }
    @Test void contextPermitsOnlySelectedTransitiveBodies() {
        var p=graph(); var edge=contextual(p).data().getFirst(); var main=flow(p,"main");
        assertThrows(IllegalArgumentException.class,()->new FunctionFlow(main.entry(),main.exit(),main.control(),List.of(new FunctionFlow.DataEdge(edge.from(),edge.to(),edge.kind()))));
        var unrelated=nodes(flow(p,"unrelated").entry(),AssignmentExpression.class).getFirst();
        assertThrows(IllegalArgumentException.class,()->new FunctionFlow(main.entry(),main.exit(),main.control(),List.of(new FunctionFlow.DataEdge(unrelated,edge.to(),edge.kind(),edge.callSite()))));
        var nested=nodes(flow(p,"bridge").entry(),CallExpression.class).getFirst();
        assertThrows(IllegalArgumentException.class,()->new FunctionFlow(main.entry(),main.exit(),main.control(),List.of(new FunctionFlow.DataEdge(edge.from(),edge.to(),edge.kind(),nested))));
    }
    @Test void diagnosticCopyAndExportsRetainInvocationIdentity() {
        var p=graph();var main=contextual(p);
        var analyzed=p.withFlows(p.flows().stream().map(f->f.entry().name().equals("main")?main:f).toList());
        var copied=analyzed.withDiagnostics("context-check",Map.of(main.entry(),new Diagnostic("context-check",Diagnostic.Severity.INFO,"Copy this analyzed snapshot")));
        var original=main.data().getFirst();var edge=flow(copied,"main").data().getFirst();
        assertNotSame(original.callSite(),edge.callSite());assertNotSame(original.from(),edge.from());
        assertSame(nodes(flow(copied,"main").entry(),CallExpression.class).getFirst(),edge.callSite());
        assertSame(nodes(flow(copied,"update").entry(),AssignmentExpression.class).getFirst(),edge.from());
        assertSame(original.callSite(),main.data().getFirst().callSite());
        assertTrue(ProjectJson.encode(copied).toText("  ").contains("callSiteId"));
        assertTrue(DotVisualizer.toDot(copied).contains("via call n"));
        assertTrue(copied.edit().build().flows().isEmpty());
    }
    @Test void edgeEqualityDistinguishesTwoInvocationSites() {
        var p=graph();var edge=contextual(p).data().getFirst();
        var other=nodes(flow(p,"bridge").entry(),CallExpression.class).getFirst();
        assertNotEquals(edge,new FunctionFlow.DataEdge(edge.from(),edge.to(),edge.kind(),other));
        assertNull(new FunctionFlow.DataEdge(edge.to(),edge.to(),edge.kind()).callSite());
    }
    @Test void globalSourcesRequireAnActualStorageBinding() {
        var p=ControlFlow.analyze(NameResolver.resolve(Project.create().addFile(Parser.parse("globals.cpp",
                "int selected=1;int unrelated=2;int main(){return selected;}",Language.CPP)).build()));
        var main=flow(p,"main");var read=nodes(main.entry(),NameExpression.class).getFirst();
        var selected=nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals("selected")).findFirst().orElseThrow();
        var unrelated=nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals("unrelated")).findFirst().orElseThrow();
        var allowed=new FunctionFlow(main.entry(),main.exit(),main.control(),List.of(new FunctionFlow.DataEdge(selected,read,FunctionFlow.Dependency.DEFINITION)));
        assertDoesNotThrow(()->p.withFlows(List.of(allowed)));
        assertThrows(IllegalArgumentException.class,()->new FunctionFlow(main.entry(),main.exit(),main.control(),List.of(new FunctionFlow.DataEdge(unrelated,read,FunctionFlow.Dependency.DEFINITION))));
    }

    @Test void opaqueInvocationAllowsReferencedGlobalsButNotPrivateBodyStorage() {
        var resolved=NameResolver.resolve(Project.create().addFile(Parser.parse("implicit.cpp",
                "int global=1;int unrelated=2;struct A{A(){int privateValue=3;global=privateValue;}};int main(){A object;return 0;}",Language.CPP)).build());
        var analyzed=UnreadWrites.analyze(resolved);var main=flow(analyzed,"main");
        var global=nodes(analyzed,VariableDeclaration.class).stream().filter(v->v.name().equals("global")).findFirst().orElseThrow();
        var object=nodes(main.entry(),VariableDeclaration.class).getFirst();
        assertTrue(main.data().stream().anyMatch(e->e.from()==global&&e.to()==object));
        for(String name:List.of("privateValue","unrelated")) {
            var foreign=nodes(analyzed,VariableDeclaration.class).stream().filter(v->v.name().equals(name)).findFirst().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->new FunctionFlow(main.entry(),main.exit(),main.control(),List.of(new FunctionFlow.DataEdge(foreign,object,FunctionFlow.Dependency.DEFINITION))));
        }
    }

}
