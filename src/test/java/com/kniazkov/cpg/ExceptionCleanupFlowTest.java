/* C++03 control transfers and implicit cleanup regression tests. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ExceptionCleanupFlowTest {
    @TempDir Path dir;
    Project read(String text) throws Exception { Files.writeString(dir.resolve("a.cpp"), text); return Cpg.read(dir, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED)); }
    static <T extends Node> List<T> nodes(Node root, Class<T> type) { var out = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node n) { if(type.isInstance(n)) out.add(type.cast(n)); }}); return out; }
    FunctionFlow flow(Project p, String name) { return p.flows().stream().filter(f -> f.entry().name().equals(name)).findFirst().orElseThrow(); }
    List<String> cleanup(FunctionFlow f, FlowVertex start, FlowVertex end) { var result = new ArrayList<String>(); var seen = new HashSet<FlowVertex>(); FlowVertex cursor = start; while (cursor != end) { assertTrue(seen.add(cursor), "cycle"); if(cursor instanceof AuxiliaryFlowVertex a && a.kind() == AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR) result.add(((VariableDeclaration)a.object().orElseThrow()).name()); var at = cursor; cursor = f.control().stream().filter(e -> e.from() == at).findFirst().orElseThrow().to(); } return result; }
    @Test void normalReturnAndConstructorFailureCleanOnlyLiveObjects() throws Exception {
        var p=read("struct Guard { Guard(); ~Guard(); }; void work(); void f(){Guard a; Guard b; work(); return;}"); var f=flow(p,"f"); assertTrue(f.complete());
        var ret=nodes(f.entry(),ReturnStatement.class).getFirst(); assertEquals(List.of("b","a"),cleanup(f,ret,f.exit()));
        var vars=nodes(f.entry(),VariableDeclaration.class); var failure=f.control().stream().filter(e->e.from()==vars.get(1)&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow(); assertEquals(List.of("a"),cleanup(f,failure.to(),f.exceptionalExit().orElseThrow()));
        var call=nodes(f.entry(),CallExpression.class).getFirst(); var thrown=f.control().stream().filter(e->e.from()==call&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow(); assertEquals(List.of("b","a"),cleanup(f,thrown.to(),f.exceptionalExit().orElseThrow()));
        assertTrue(f.auxiliaryVertices().stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR).allMatch(a->a.destructor().isPresent()));
    }
    @Test void callExceptionCleansBeforeCatchButEmptySpecificationDoesNotThrow() throws Exception {
        var p=read("struct Guard { Guard(); ~Guard(); }; void work(); void quiet() throw(); void f(){try{Guard g;work();}catch(...){}} void q(){try{Guard g;quiet();}catch(...){}}");
        var f=flow(p,"f"); var call=nodes(f.entry(),CallExpression.class).getFirst(); var handler=nodes(f.entry(),CatchClause.class).getFirst(); var exception=f.control().stream().filter(e->e.from()==call&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow(); assertEquals(List.of("g"),cleanup(f,exception.to(),handler));
        var q=flow(p,"q"); var quiet=nodes(q.entry(),CallExpression.class).getFirst(); assertFalse(q.control().stream().anyMatch(e->e.from()==quiet&&e.kind()==Branch.EXCEPTION));
    }
    @Test void copyingDiagnosticsRemapsFlowOnlyObjectsWithoutAddingSyntax() throws Exception {
        var p=read("struct Guard{Guard();~Guard();};void f(){Guard g;}"); var before=flow(p,"f"); var copy=p.withDiagnostics("test",Map.of(before.entry(),new Diagnostic("test",Diagnostic.Severity.WARNING,"test"))); var after=flow(copy,"f"); assertNotSame(before.entry(),after.entry()); assertEquals(before.control().size(),after.control().size());
        for(var vertex:after.auxiliaryVertices()){ assertSame(after.entry(),vertex.owner()); vertex.object().ifPresent(n->assertTrue(nodes(copy,Node.class).contains(n))); }
        assertEquals(nodes(p,Node.class).size(),nodes(copy,Node.class).size());
    }
    @Test void constructorFunctionTryCleansSubobjectsBeforeHandler() throws Exception {
        var p=read("struct Guard{Guard(int);~Guard();};struct Owner{Guard first;Guard second;Owner() try:first(1),second(2){}catch(...){throw;}};"); var f=flow(p,"Owner"); assertTrue(f.complete()); var initializer=f.entry().initializers().get(1); var handler=f.entry().handlers().getFirst(); var failure=f.control().stream().filter(e->e.from()==initializer&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow(); assertEquals(List.of("first"),cleanup(f,failure.to(),handler));
    }
    @Test void implicitSubobjectInitializationCleansEarlierObjects() throws Exception {
        var p=read("struct Guard{Guard();~Guard();};struct Owner{Guard first;Guard second;Owner(){}};"); var f=flow(p,"Owner");
        var constructors=f.auxiliaryVertices().stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_CONSTRUCTOR).toList(); assertEquals(2,constructors.size());
        var second=constructors.stream().filter(a->((VariableDeclaration)a.object().orElseThrow()).name().equals("second")).findFirst().orElseThrow();
        var failure=f.control().stream().filter(e->e.from()==second&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow(); assertEquals(List.of("first"),cleanup(f,failure.to(),f.exceptionalExit().orElseThrow()));
    }
    @Test void typedHandlersAndRethrowUseOrderedEnclosingRegions() throws Exception {
        var p=read("void f(){try{throw 1;}catch(double d){}catch(const int &i){throw;}catch(...){}}"); var f=flow(p,"f");
        var handlers=nodes(f.entry(),CatchClause.class); var throwsList=nodes(f.entry(),ThrowExpression.class);
        assertEquals(List.of(handlers.get(1)),f.control().stream().filter(e->e.from()==throwsList.get(0)).map(ControlEdge::to).toList());
        assertEquals(List.of(f.exceptionalExit().orElseThrow()),f.control().stream().filter(e->e.from()==throwsList.get(1)).map(ControlEdge::to).toList());
    }
    @Test void breakContinueGotoAndCaseFallthroughRetainTheirTargets() throws Exception {
        var p=read("void f(int n){for(int i=0;i<n;++i){if(i==1)continue;if(i==2)break;}switch(n){case 0:n=1;case 1:n=2;break;default:;}goto done;n=4;done:;}"); var f=flow(p,"f"); assertTrue(f.complete());
        assertTrue(f.control().stream().anyMatch(e->e.kind()==Branch.CONTINUE)); assertTrue(f.control().stream().anyMatch(e->e.kind()==Branch.BREAK));
        var jump=nodes(f.entry(),GotoStatement.class).getFirst(); var label=nodes(f.entry(),LabelStatement.class).getFirst(); assertTrue(f.control().contains(new ControlEdge(jump,label,Branch.GOTO)));
        var cases=nodes(f.entry(),CaseStatement.class); assertEquals(3,f.control().stream().filter(e->e.kind()==Branch.CASE||e.kind()==Branch.DEFAULT).count());
        assertTrue(f.control().stream().anyMatch(e->e.to()==cases.get(1)&&e.kind()==Branch.NEXT));
    }
    @Test void scopedGotoDoesNotDestroyForOrCatchObjects() throws Exception {
        var p=read("struct Guard{Guard();~Guard();};void work();void f(){for(Guard g;;){L:goto L;}}void h(){try{work();}catch(Guard g){L:goto L;}}");
        for(String name:List.of("f","h")){var f=flow(p,name); assertTrue(f.complete());var jump=nodes(f.entry(),GotoStatement.class).getFirst();assertInstanceOf(LabelStatement.class,f.control().stream().filter(e->e.from()==jump).findFirst().orElseThrow().to());}
    }
    @Test void throwingDefaultsOperandsAndAllocationDifferFromSizeof() throws Exception {
        var p=read("int work();void quiet(int n=work())throw();void f(){sizeof(work());try{quiet();}catch(...){}}void h(){try{throw work();}catch(int){}catch(...){}}void n(){new int;}");
        var f=flow(p,"f"); var size=nodes(f.entry(),SizeofExpression.class).getFirst(); assertFalse(f.control().stream().anyMatch(e->e.from()==size&&e.kind()==Branch.EXCEPTION));
        var quiet=nodes(f.entry(),CallExpression.class).stream().filter(c->c.target().map(t->t.name().equals("quiet")).orElse(false)).findFirst().orElseThrow(); assertTrue(f.control().stream().anyMatch(e->e.from()==quiet&&e.kind()==Branch.EXCEPTION));
        var h=flow(p,"h");var thrown=nodes(h.entry(),ThrowExpression.class).getFirst();var catchall=nodes(h.entry(),CatchClause.class).getLast();assertTrue(h.control().stream().anyMatch(e->e.from()==thrown&&e.to()==catchall&&e.kind()==Branch.EXCEPTION));
        assertTrue(flow(p,"n").exceptionalExit().isPresent());
    }
    @Test void byValueParametersAndCatchCopiesHaveLifetimes() throws Exception {
        var p=read("struct Guard{Guard();~Guard();};void work();void f(Guard g){}void h(){try{work();}catch(Guard g){}}");
        for(String name:List.of("f","h")){var f=flow(p,name);assertTrue(f.auxiliaryVertices().stream().anyMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR&&a.object().orElse(null) instanceof ParameterDeclaration));}
        var h=flow(p,"h");var handler=nodes(h.entry(),CatchClause.class).getFirst();assertTrue(h.control().stream().anyMatch(e->e.from()==handler&&e.to() instanceof AuxiliaryFlowVertex a&&a.kind()==AuxiliaryFlowVertex.Kind.TERMINATE_EXIT));
    }
    @Test void destructorBodyCleansMembersBeforeFunctionTryHandler() throws Exception {
        var p=read("struct Guard{Guard();~Guard()throw();};void work();struct Owner{Guard first;Guard second;~Owner()try{work();}catch(...){throw;}};");var f=p.flows().stream().filter(x->x.entry().kind()==CallableDeclaration.Kind.DESTRUCTOR).findFirst().orElseThrow();var call=nodes(f.entry(),CallExpression.class).getFirst();var handler=f.entry().handlers().getFirst();
        var failure=f.control().stream().filter(e->e.from()==call&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow();assertEquals(List.of("second","first"),cleanup(f,failure.to(),handler));
        var normal=f.control().stream().filter(e->e.from()==call&&e.kind()==Branch.NEXT).findFirst().orElseThrow();assertEquals(List.of("second","first"),cleanup(f,normal.to(),f.exit()));
    }
    @Test void throwingDestructorsDuringUnwindingTerminate() throws Exception {
        var p=read("struct Guard{Guard();~Guard();};void work();void f(){Guard g;work();}");var f=flow(p,"f");assertTrue(f.auxiliaryVertices().stream().anyMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.TERMINATE_EXIT));
    }
    @Test void thrownArraysDecayAndSequencedThrowsDoNotFallThrough() throws Exception {
        var p=read("void work();void f(){try{throw \"x\";}catch(const char*){}}void h(){try{(0,throw 1);work();}catch(int){}}void q(){try{int x=(true?throw 1:2);work();}catch(int){}}");var f=flow(p,"f");assertTrue(f.control().stream().anyMatch(e->e.kind()==Branch.THROW&&e.to() instanceof CatchClause));
        for(String name:List.of("h","q")){var flow=flow(p,name);assertTrue(flow.complete());var call=nodes(flow.entry(),CallExpression.class).getFirst();assertFalse(flow.control().stream().anyMatch(e->e.from()==call||e.to()==call));}
    }
    @Test void partialArrayConstructionIsConservativelyIncomplete() throws Exception {
        var p=read("struct Guard{Guard();~Guard();};void f(){Guard objects[2];}");var f=flow(p,"f");assertFalse(f.complete());assertTrue(f.control().isEmpty());
    }
    @Test void exceptionSpecificationsEnforceTheirBoundary() throws Exception {
        var p=read("void work();void f()throw(){throw 1;}void h()throw(){work();}void q()throw(int){throw 1.0;}void good()throw(int){throw 1;}");
        for(String name:List.of("f","h")){var f=flow(p,name);assertTrue(f.complete());assertTrue(f.exceptionalExit().isEmpty());assertTrue(f.auxiliaryVertices().stream().anyMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.TERMINATE_EXIT));}
        assertFalse(flow(p,"q").complete());assertTrue(flow(p,"good").complete());assertTrue(flow(p,"good").exceptionalExit().isPresent());
    }
    @Test void localClassMethodsDoNotExecuteOrHijackOuterLabels() throws Exception {
        var p=read("void work();void f(){L:;struct C{void m(){L:;work();}};goto L;}");var f=flow(p,"f");assertTrue(f.complete());assertTrue(f.exceptionalExit().isEmpty());var label=nodes(f.entry(),LabelStatement.class).getFirst();var jump=nodes(f.entry(),GotoStatement.class).getFirst();assertTrue(f.control().contains(new ControlEdge(jump,label,Branch.GOTO)));
    }
    @Test void classReturningCallsRetainConservativeTemporaryBoundary() throws Exception {
        var p=read("struct Guard{Guard();~Guard();};Guard make();void f(){make();}");assertFalse(flow(p,"f").complete());
    }
    @Test void implicitFunctionTryRethrowObeysEmptyExceptionSpecification() throws Exception {
        var p=read("struct Guard{Guard()throw()try{throw 1;}catch(...){}~Guard()throw()try{throw 1;}catch(...){}};");
        for(var f:p.flows()){assertTrue(f.complete());assertTrue(f.exceptionalExit().isEmpty());assertTrue(f.auxiliaryVertices().stream().anyMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.TERMINATE_EXIT));}
    }
    @Test void conditionalThrowInAssignmentConditionHasNoNormalArm() throws Exception {
        var p=read("void work();void f(){int x=0;try{if(x=(true?throw 1:2))work();}catch(int){}}");var f=flow(p,"f");assertTrue(f.complete());var call=nodes(f.entry(),CallExpression.class).getFirst();assertFalse(f.control().stream().anyMatch(e->e.from()==call||e.to()==call));
    }
}
