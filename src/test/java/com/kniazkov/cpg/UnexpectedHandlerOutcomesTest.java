/* Boundary alternatives justified by supported handler bodies. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedHandlerOutcomesTest {
    Project read(String code) {
        return DataFlow.analyze(ControlFlow.analyze(Project.create().addFile(com.kniazkov.cpg.parser.Parser.parse("a.cpp",
                "#include <exception>\n"+code,Language.CPP)).build()));
    }
    FunctionFlow flow(Project p) {return p.flows().stream().filter(f->f.entry().name().equals("f")).findFirst().orElseThrow();}
    Set<AuxiliaryFlowVertex.Kind> outcomes(Project p) {
        var flow=flow(p);assertTrue(flow.complete());var result=new HashSet<AuxiliaryFlowVertex.Kind>();
        for(var edge:flow.control()) if(edge.from() instanceof AuxiliaryFlowVertex a&&a.kind()==AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL
                && edge.to() instanceof AuxiliaryFlowVertex b)result.add(b.kind());
        return result;
    }
    void check(String handler,String spec,AuxiliaryFlowVertex.Kind... expected) {
        var p=read(handler+"void f()throw("+spec+"){std::set_unexpected(h);throw true;}");
        assertEquals(Set.of(expected),outcomes(p));
    }
    @Test void permittedAndForbiddenReplacement() {
        check("void h(){throw 1;}","int",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("void h(){throw 1.0;}","int",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
        check("void h(){throw 1;}","",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
    }
    @Test void badExceptionFallbackIsDistinctFromOrdinaryReplacement() {
        check("void h(){throw 1;}","std::bad_exception",AuxiliaryFlowVertex.Kind.BAD_EXCEPTION_REPLACEMENT);
        check("void h(){throw 1;}","int,std::bad_exception",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("bool b;void h(){if(b)throw 1;else throw 2.0;}","int,std::bad_exception",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT,AuxiliaryFlowVertex.Kind.BAD_EXCEPTION_REPLACEMENT);
    }
    @Test void terminationAndBranchUnion() {
        check("void h(){std::terminate();}","int,std::bad_exception",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
        check("bool b;void h(){if(b)std::terminate();else throw 1;}","int",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT,AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
    }
    @Test void helperCallsAndLocalCatchRethrow() {
        check("void fail(){throw 1;}void h(){fail();}","int",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("void fail(){throw 1.0;}void h(){try{fail();}catch(double){throw 1;}}","int",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("void h(){try{throw 1;}catch(int){throw;}}","int",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("void h(){try{throw 1.0;}catch(int){throw 1;}}","int",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
    }
    @Test void privateOpaqueRecursiveAndReturningBodiesRetainAlternatives() {
        for(String body:List.of("void work();void h(){work();}","void h(){h();}","void h(){}","void h(){throw;}","void h(){while(true){}}"))
            check(body,"int",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT,AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
        var p=read("void h(){throw 1;}void f()throw(int){throw 1.0;}");
        assertEquals(Set.of(AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT,AuxiliaryFlowVertex.Kind.TERMINATE_EXIT),outcomes(p));
    }
    @Test void pointerQualificationAndNoNumericConversion() {
        check("int x;void h(){throw &x;}","const int*",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("const int x=0;void h(){throw &x;}","int*",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
        check("void h(){throw 1;}","double",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
    }
    @Test void objectAndFunctionPointersAreDistinct() {
        check("int x;void h(){throw &x;}","void*",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("void target(){}void h(){throw &target;}","void*",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
    }
    @Test void unsupportedLifetimeAndNestedPointersKeepAlternatives() {
        check("struct E{E(const E&);};E& get();void h(){throw get();}","int",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT,AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
        check("int** p;void h(){throw p;}","const int*const*",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("int** p;void h(){try{throw p;}catch(const int*const*){throw 1;}catch(...){throw 1.0;}}","int",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
    }
    @Test void publicUnambiguousPointerBaseMatching() {
        check("struct B{};struct D:public B{};D* p;void h(){throw p;}","B*",AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT);
        check("struct B{};struct D:private B{};D* p;void h(){throw p;}","B*",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
        check("struct B{};struct L:public B{};struct R:public B{};struct D:public L,public R{};D* p;void h(){throw p;}","B*",AuxiliaryFlowVertex.Kind.TERMINATE_EXIT);
    }
    @Test void handlerSideEffectsRemainConservative() {
        var p=read("int global=0;void h(){global=2;throw 1;}void f()throw(int){global=1;std::set_unexpected(h);throw 1.0;}");
        assertEquals(Set.of(AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT),outcomes(p));
        assertTrue(flow(p).data().stream().anyMatch(e->e.to()==flow(p).entry()&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void allKnownCandidatesContribute() {
        var p=read("void h(){throw 1;}void k(){std::terminate();}void f(bool b)throw(int){if(b)std::set_unexpected(h);else std::set_unexpected(k);throw 1.0;}");
        assertEquals(Set.of(AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT,AuxiliaryFlowVertex.Kind.TERMINATE_EXIT),outcomes(p));
    }
    @Test void copyJsonAndHandlerEditsRecomputeAlternatives() {
        var p=read("void h(){throw 1;}void f()throw(int){std::set_unexpected(h);throw 1.0;}");
        var copy=p.withDiagnostics("probe",Map.of(p,new Diagnostic("probe",Diagnostic.Severity.INFO,"copy")));
        assertEquals(outcomes(p),outcomes(copy));assertTrue(ProjectJson.encode(copy).toText("").contains("EXCEPTION_REPLACEMENT"));
        assertFalse(flow(copy).auxiliaryVertices().stream().anyMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.TERMINATE_EXIT));
        var handler=p.flows().stream().filter(f->f.entry().name().equals("h")).findFirst().orElseThrow().entry();
        var editor=new ProjectEditor(p);editor.replace(handler.body().orElseThrow(),BlockStatement.create().build());
        var changed=DataFlow.analyze(ControlFlow.analyze(editor.build()));
        assertEquals(Set.of(AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT,AuxiliaryFlowVertex.Kind.TERMINATE_EXIT),outcomes(changed));
    }
}
