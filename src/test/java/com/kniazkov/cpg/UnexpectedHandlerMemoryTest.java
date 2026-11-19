/* Concrete may-effects of implicitly invoked unexpected handlers. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedHandlerMemoryTest {
    Project read(String text) {
        return DataFlow.analyze(ControlFlow.analyze(Project.create().addFile(Parser.parse("memory.cpp",
                "#include <exception>\n"+text,Language.CPP)).build()));
    }
    FunctionFlow flow(Project p,String name) {return p.flows().stream().filter(f->f.entry().name().equals(name)).findFirst().orElseThrow();}
    <T extends Node> List<T> nodes(Node root,Class<T> type) {
        var result=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))result.add(type.cast(n));}});return result;
    }
    boolean edge(FunctionFlow f,Node from,Node to) {
        return f.data().stream().anyMatch(e->e.kind()==FunctionFlow.Dependency.DEFINITION&&e.from()==from&&e.to()==to);
    }
    @Test void boundaryInputReachesHandlerRead() {
        var p=read("int g=0;void h(){throw g;}void f()throw(int){std::set_unexpected(h);g=7;throw true;}");
        var f=flow(p,"f");var write=nodes(f.entry(),AssignmentExpression.class).getFirst();
        var read=nodes(flow(p,"h").entry(),NameExpression.class).stream().filter(n->n.name().equals("g")).findFirst().orElseThrow();
        assertTrue(edge(f,write,read));
    }
    @Test void handlerWriteReachesCallerCatch() {
        var p=read("int g=0;void h(){g=7;throw 1;}void f()throw(int){std::set_unexpected(h);throw true;}int caller(){try{f();}catch(int){return g;}return 0;}");
        var write=nodes(flow(p,"h").entry(),AssignmentExpression.class).getFirst();
        var caller=flow(p,"caller");var read=nodes(caller.entry(),NameExpression.class).stream().filter(n->n.name().equals("g")).findFirst().orElseThrow();
        assertTrue(edge(caller,write,read));
    }
    @Test void qualifiedHandlerEffectsReachCallerCatch() {
        var p=read("namespace N{int g=0;void h(){g=7;throw 1;}}void f()throw(int){std::set_unexpected(N::h);throw true;}int caller(){try{f();}catch(int){return N::g;}return 0;}");
        var write=nodes(flow(p,"h").entry(),AssignmentExpression.class).getFirst();
        var caller=flow(p,"caller");var read=nodes(caller.entry(),ScopeMemberExpression.class).getFirst();
        assertTrue(edge(caller,write,read));
    }
    @Test void helpersAndAliasedWritesReachHandlerReads() {
        var p=read("int g=0;void put(int&a,int&b){a=3;b=7;}void h(){put(g,g);throw g;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var f=flow(p,"f");var write=nodes(flow(p,"put").entry(),AssignmentExpression.class).getLast();
        var read=nodes(flow(p,"h").entry(),NameExpression.class).stream().filter(n->n.name().equals("g")).toList().getLast();
        assertTrue(edge(f,write,read));
    }
    @Test void allCandidatesPublishEffects() {
        var p=read("int g=0;void h(){g=1;throw 1;}void k(){g=2;throw 2;}void f(bool b)throw(int){if(b)std::set_unexpected(h);else std::set_unexpected(k);throw true;}");
        var f=flow(p,"f");
        for(String name:List.of("h","k"))assertTrue(f.data().stream().anyMatch(e->e.from()==nodes(flow(p,name).entry(),AssignmentExpression.class).getFirst()));
    }
    @Test void terminatingAndReturningPrefixesAreRetained() {
        for(String tail:List.of("std::terminate();","return;")) {
            var p=read("int g=0;void h(){g=8;"+tail+"}void f()throw(int){std::set_unexpected(h);throw true;}");
            var f=flow(p,"f");var write=nodes(flow(p,"h").entry(),AssignmentExpression.class).getFirst();
            assertTrue(f.data().stream().anyMatch(e->e.from()==write));
        }
    }
    @Test void nestedImplicitHandlersPublishOwnedEffects() {
        var p=read("int g=0;void k(){g=9;throw 1;}void h()throw(int){std::set_unexpected(k);throw true;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var write=nodes(flow(p,"k").entry(),AssignmentExpression.class).getFirst();
        assertTrue(flow(p,"f").data().stream().anyMatch(e->e.from()==write));
    }
    @Test void recursiveHandlerBoundariesConverge() {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var p=read("int g=0;void h()throw(int){std::set_unexpected(h);g=1;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}");
            assertTrue(flow(p,"f").data().stream().anyMatch(e->e.to()==flow(p,"f").entry()));
        });
    }
    @Test void lifetimeEffectsRemainOpaque() {
        var p=read("int g=0;struct S{~S(){g=9;}};void h(){S s;throw g;}void f()throw(int){std::set_unexpected(h);g=1;throw true;}");
        var f=flow(p,"f");var local=nodes(flow(p,"h").entry(),VariableDeclaration.class).getFirst();
        assertTrue(f.data().stream().anyMatch(e->e.to()==local&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void pointerWritesPublishConcreteSources() {
        var p=read("int g=0;void h(){int*p=&g;*p=7;throw g;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var f=flow(p,"f");var h=flow(p,"h");
        var read=nodes(h.entry(),NameExpression.class).stream().filter(n->n.name().equals("g")).toList().getLast();
        assertTrue(edge(f,nodes(h.entry(),AssignmentExpression.class).getFirst(),read));
    }
    @Test void unknownCandidateRetainsBoundaryEffect() {
        var p=read("int g=0;void h(){g=7;throw 1;}void f(bool b)throw(int){if(b)std::set_unexpected(h);throw true;}");
        var f=flow(p,"f");
        assertTrue(f.auxiliaryVertices().stream().anyMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL&&a.unknownUnexpectedHandler()));
        assertTrue(f.data().stream().anyMatch(e->e.to()==f.entry()&&e.kind()==FunctionFlow.Dependency.DEFINITION));
        assertTrue(f.data().stream().anyMatch(e->e.from()==nodes(flow(p,"h").entry(),AssignmentExpression.class).getFirst()));
    }
    @Test void uninvokedBodyCannotSupplyDataEndpoints() {
        var p=read("int g=0;void other(){g=9;}void h(){throw 1;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var f=flow(p,"f");var alien=nodes(flow(p,"other").entry(),AssignmentExpression.class).getFirst();
        assertThrows(IllegalArgumentException.class,()->new FunctionFlow(f.entry(),f.exit(),f.control(),
            List.of(new FunctionFlow.DataEdge(alien,f.entry(),FunctionFlow.Dependency.DEFINITION)),true));
    }
    @Test void copiesAndReanalysisPreserveOwnedEndpoints() {
        var p=read("int g=0;void h(){g=7;throw g;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var copy=p.withDiagnostics("probe",Map.of(p,new Diagnostic("probe",Diagnostic.Severity.INFO,"copy")));
        assertEquals(flow(p,"f").data().size(),flow(copy,"f").data().size());
        assertDoesNotThrow(()->ProjectJson.encode(copy).toText(""));
        var again=DataFlow.analyze(copy);
        assertEquals(flow(copy,"f").data().size(),flow(again,"f").data().size());
    }
}
