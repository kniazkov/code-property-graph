/* Scope-sensitive lifetime outcomes for unexpected handlers. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.kniazkov.cpg.model.AuxiliaryFlowVertex.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedHandlerLifetimeTest {
    Project read(String text) {
        return DataFlow.analyze(ControlFlow.analyze(Project.create().addFile(Parser.parse("lifetime.cpp",
                "#include <exception>\n"+text,Language.CPP)).build()));
    }
    FunctionFlow flow(Project p,String name) {return p.flows().stream().filter(f->f.entry().name().equals(name)).findFirst().orElseThrow();}
    Set<AuxiliaryFlowVertex.Kind> outcomes(Project p) {
        var f=flow(p,"f");assertTrue(f.complete());var result=new HashSet<AuxiliaryFlowVertex.Kind>();
        for(var edge:f.control())if(edge.from() instanceof AuxiliaryFlowVertex a&&a.kind()==UNEXPECTED_CALL
                &&edge.to() instanceof AuxiliaryFlowVertex b)result.add(b.kind());
        assertTrue(f.auxiliaryVertices().stream().anyMatch(a->a.kind()==UNEXPECTED_CALL&&!a.unknownUnexpectedHandler()));
        return result;
    }
    void check(String source,AuxiliaryFlowVertex.Kind... expected) {
        assertEquals(Set.of(expected),outcomes(read(source+"void f()throw(int,std::bad_exception){std::set_unexpected(h);throw true;}")),source);
    }
    @Test void nonthrowingCleanupPreservesReplacement() {
        check("struct S{~S(){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
    }
    @Test void implicitScalarMemberLifetimesPreserveReplacement() {
        check("struct S{int x;int*p;};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
    }
    @Test void destructorThrowDuringUnwindingTerminates() {
        check("struct S{~S(){throw 2;}};void h(){S s;throw 1;}",TERMINATE_EXIT);
    }
    @Test void destructorCanCatchItsOwnException() {
        check("struct S{~S(){try{throw 2;}catch(int){}}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
    }
    @Test void destructorThrowOnNormalExitCanReplace() {
        check("struct S{~S(){throw 2;}};void h(){{S s;}throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void destructorThrowCanReachAnEnclosingCatch() {
        check("struct S{~S(){throw 2.0;}};void h(){try{S s;}catch(double){throw 1;}}",EXCEPTION_REPLACEMENT);
    }
    @Test void cleanupOfHelperReturnCanThrow() {
        check("struct S{~S(){throw 2;}};void helper(){S s;return;}void h(){helper();throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void failedConstructorDoesNotRunItsDestructor() {
        check("struct S{S(){throw 1;}~S(){std::terminate();}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void failedLaterConstructionUnwindsEarlierObject() {
        check("struct A{~A(){throw 2;}};struct B{B(){throw 1;}};void h(){A a;B b;throw true;}",TERMINATE_EXIT);
    }
    @Test void reverseCleanupPreservesTermination() {
        String types="struct Stop{~Stop(){std::terminate();}};struct Throw{~Throw(){throw 1;}};";
        check(types+"void h(){{Throw a;Stop b;}throw true;}",TERMINATE_EXIT);
        check(types+"void h(){{Stop a;Throw b;}throw true;}",TERMINATE_EXIT);
        check("struct A{~A(){throw 1;}};struct B{~B(){}};void h(){{A a;B b;}throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void terminationDoesNotBecomeReplacementDuringCleanup() {
        check("struct S{~S(){throw 1;}};void h(){S s;std::terminate();}",TERMINATE_EXIT);
    }
    @Test void branchKeepsBothCleanupOutcomes() {
        check("int flag;struct S{~S(){if(flag)throw 2;}};void h(){S s;throw 1;}",TERMINATE_EXIT,EXCEPTION_REPLACEMENT);
    }
    @Test void fixedArrayCleanupAndPartialConstruction() {
        check("struct S{~S(){}};void h(){S s[3];throw 1;}",EXCEPTION_REPLACEMENT);
        check("struct S{~S(){}};void h(){S s[64];throw 1;}",EXCEPTION_REPLACEMENT);
        check("struct S{~S(){throw 2;}};void h(){S s[3];throw 1;}",TERMINATE_EXIT);
        check("struct S{S(){throw 1;}~S(){std::terminate();}};void h(){S s[3];throw true;}",EXCEPTION_REPLACEMENT);
        check("int flag;struct S{S(){if(flag)throw 1;}~S(){throw 2;}};void h(){S s[3];throw true;}",EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void emptySpecificationViolationInsideDestructorStaysUnknown() {
        check("struct S{~S()throw(){throw 1;}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void opaqueLifetimesAndHiddenSubobjectsStayUnknown() {
        for(String source:List.of(
            "struct S{~S();};void h(){S s;throw 1;}",
            "struct B{~B(){throw 2;}};struct S:B{};void h(){S s;throw 1;}",
            "struct A{~A(){throw 2;}};struct S{A a;};void h(){S s;throw 1;}",
            "struct S{S(int n=1){}};void h(){S s;throw 1;}",
            "struct S{int x;S():x(1){}};void h(){S s;throw 1;}",
            "int init();struct S{int x;S();};S::S():x(init()){}void h(){S s;throw 1;}",
            "struct S{};void h(){static S s;throw 1;}",
            "struct S{};void h(){S s[65];throw 1;}",
            "struct S{};void h(){S s[2][2];throw 1;}"))
            check(source,EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void cleanupNeverTurnsTerminationIntoBadException() {
        check("struct S{~S(){throw 2.0;}};void h(){S s;throw 1;}",TERMINATE_EXIT);
        check("struct S{~S(){throw 2.0;}};void h(){{S s;}throw 1;}",BAD_EXCEPTION_REPLACEMENT);
    }
    @Test void mixedHandlersCombineLifetimeOutcomes() {
        var p=read("struct S{~S(){throw 2;}};void h(){S s;throw 1;}void k(){throw 1;}void f(bool b)throw(int,std::bad_exception){if(b)std::set_unexpected(h);else std::set_unexpected(k);throw true;}");
        assertEquals(Set.of(TERMINATE_EXIT,EXCEPTION_REPLACEMENT),outcomes(p));
    }
    @Test void copiesExportsAndEditsRecomputeCleanup() {
        var p=read("struct S{~S(){}};void h(){S s;throw 1;}void f()throw(int,std::bad_exception){std::set_unexpected(h);throw true;}");
        var copy=p.withDiagnostics("probe",Map.of(p,new Diagnostic("probe",Diagnostic.Severity.INFO,"copy")));
        assertEquals(Set.of(EXCEPTION_REPLACEMENT),outcomes(copy));
        assertDoesNotThrow(()->ProjectJson.encode(copy).toText(""));
        var destructor=p.flows().stream().filter(f->f.entry().kind()==CallableDeclaration.Kind.DESTRUCTOR).findFirst().orElseThrow().entry();
        var replacement=read("void h(){throw 2;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var editor=new ProjectEditor(p);editor.replace(destructor.body().orElseThrow(),flow(replacement,"h").entry().body().orElseThrow());
        assertEquals(Set.of(TERMINATE_EXIT),outcomes(DataFlow.analyze(ControlFlow.analyze(editor.build()))));
    }
}
