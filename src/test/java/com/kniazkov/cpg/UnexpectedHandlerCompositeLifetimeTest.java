/* Subobject construction and destruction outcomes. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static com.kniazkov.cpg.model.AuxiliaryFlowVertex.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedHandlerCompositeLifetimeTest {
    private final UnexpectedHandlerLifetimeTest fixture = new UnexpectedHandlerLifetimeTest();
    Project read(String source) { return fixture.read(source); }
    FunctionFlow flow(Project project, String name) { return fixture.flow(project, name); }
    java.util.Set<AuxiliaryFlowVertex.Kind> outcomes(Project project) { return fixture.outcomes(project); }
    void check(String source, AuxiliaryFlowVertex.Kind... expected) { fixture.check(source, expected); }
    @Test void implicitCompositeSpecialMembersPreserveReplacement() {
        check("struct A{};struct B{};struct S:A,B{A a;B b[3];};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
    }
    @Test void baseAndMemberCleanupTerminateDuringUnwinding() {
        check("struct B{~B(){throw 2;}};struct S:B{};void h(){S s;throw 1;}",TERMINATE_EXIT);
        check("struct A{~A(){throw 2;}};struct S{A a;};void h(){S s;throw 1;}",TERMINATE_EXIT);
    }
    @Test void subobjectThrowsOnNormalExitCanReplace() {
        check("struct B{~B(){throw 1;}};struct S:B{};void h(){{S s;}throw true;}",EXCEPTION_REPLACEMENT);
        check("struct A{~A(){throw 1.0;}};struct S{A a;};void h(){{S s;}throw true;}",BAD_EXCEPTION_REPLACEMENT);
    }
    @Test void failedSubobjectDoesNotRunWholeObjectDestructor() {
        check("struct B{B(){throw 1;}~B(){std::terminate();}};struct S:B{~S(){std::terminate();}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
        check("struct A{A(){throw 1;}~A(){std::terminate();}};struct S{A a;~S(){std::terminate();}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void failedLaterSubobjectCleansCompletedPrefix() {
        check("struct A{~A(){throw 2;}};struct B{B(){throw 1;}};struct S:A,B{};void h(){S s;throw true;}",TERMINATE_EXIT);
        check("struct A{~A(){throw 2;}};struct B{B(){throw 1;}};struct S{A a;B b;};void h(){S s;throw true;}",TERMINATE_EXIT);
        check("struct A{~A(){throw 2;}};struct B{B(){throw 1;}};struct S:A{B b;};void h(){S s;throw true;}",TERMINATE_EXIT);
    }
    @Test void constructorBodyFailureCleansFieldsAndBases() {
        check("struct A{~A(){throw 2;}};struct S:A{S(){throw 1;}};void h(){S s;throw true;}",TERMINATE_EXIT);
        check("struct A{~A(){}};struct S{A a;S(){throw 1;}~S(){std::terminate();}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void successfulConstructionDoesNotPrematurelyDestroyParts() {
        check("struct A{~A(){throw 2;}};struct S{A a;};void h(){S s;std::terminate();}",TERMINATE_EXIT);
    }
    @Test void destructorBodyFailureStillCleansSubobjects() {
        check("struct A{~A(){throw 2;}};struct S{A a;~S(){throw 1;}};void h(){{S s;}throw true;}",TERMINATE_EXIT);
        check("struct A{~A(){}};struct S:A{~S(){throw 1;}};void h(){{S s;}throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void fieldArraysIncludePartialConstructionAndCleanup() {
        check("struct A{~A(){}};struct S{A a[3];};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
        check("struct A{A(){throw 1;}~A(){std::terminate();}};struct S{A a[3];};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
        check("int flag;struct A{A(){if(flag)throw 1;}~A(){throw 2;}};struct S{A a[3];};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void enclosingSpecificationCoversSubobjectExceptions() {
        check("struct A{A(){throw 1;}};struct S{A a;S()throw(){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
        check("struct A{~A(){throw 1;}};struct S{A a;~S()throw(){}};void h(){{S s;}throw 1;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void hiddenOrUnknownLifetimesKeepAlternatives() {
        for(String source:java.util.List.of(
            "struct A{~A();};struct S{A a;};void h(){S s;throw 1;}",
            "struct A{};struct S{A a[65];};void h(){S s;throw 1;}",
            "struct A{};struct S{A a[2][2];};void h(){S s;throw 1;}",
            "union S{int x;};void h(){S s;throw 1;}"))
            check(source,EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void nestedClassesHaveAFiniteSummaryBudget() {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var source=new StringBuilder("struct A0{};");
            for(int i=1;i<=10;i++)source.append("struct A").append(i).append("{A").append(i-1).append(" a;};");
            check(source+"void h(){A10 a;throw 1;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
        });
    }
    @Test void editingMemberDestructorRecomputesOuterOutcome() {
        var p=read("struct A{~A(){}};struct S{A a;};void h(){S s;throw 1;}void f()throw(int,std::bad_exception){std::set_unexpected(h);throw true;}");
        assertEquals(java.util.Set.of(EXCEPTION_REPLACEMENT),outcomes(p));
        var target=p.flows().stream().filter(f->f.entry().kind()==CallableDeclaration.Kind.DESTRUCTOR).findFirst().orElseThrow().entry();
        var replacement=read("void h(){throw 2;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var editor=new ProjectEditor(p);editor.replace(target.body().orElseThrow(),flow(replacement,"h").entry().body().orElseThrow());
        assertEquals(java.util.Set.of(TERMINATE_EXIT),outcomes(DataFlow.analyze(ControlFlow.analyze(editor.build()))));
    }
}
