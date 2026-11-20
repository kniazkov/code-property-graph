package com.kniazkov.cpg;
import org.junit.jupiter.api.Test;
import static com.kniazkov.cpg.model.AuxiliaryFlowVertex.Kind.*;
class UnexpectedHandlerObjectInitializerTest {
    final UnexpectedHandlerLifetimeTest f = new UnexpectedHandlerLifetimeTest();
    @Test void explicitBasesAndFields() {
        f.check("struct A{};struct S:A{A a;S():A(),a(){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
        f.check("struct A{};struct S{A a[2];S():a(){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
    }
    @Test void reorderedMemberFailureUsesDeclarationOrder() {
        f.check("struct A{A(){throw 1;}};struct B{B(){throw true;}};struct S{A a;B b;S():b(),a(){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void baseFailurePrecedesField() {
        f.check("struct A{A(){throw 1;}};struct B{B(){throw true;}};struct S:A{B b;S():b(),A(){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void failedFieldCleansBase() {
        f.check("struct A{~A(){throw 2;}};struct B{B(){throw 1;}};struct S:A{B b;S():A(),b(){}};void h(){S s;throw true;}",TERMINATE_EXIT);
    }
    @Test void virtualInitializerRunsForCompleteObject() {
        f.check("struct V{V(){throw 1;}};struct S:virtual V{S():V(){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void ignoredVirtualArgumentsDoNotThrow() {
        f.check("int fail(){throw true;}struct V{V(){}V(int){}};struct B:virtual V{B():V(fail()){}};struct D:B{};void h(){D d;throw 1;}",EXCEPTION_REPLACEMENT);
        f.check("int fail(){throw true;}struct V{V(){}V(int){}};struct B:virtual V{B():V(fail()){}};void h(){B b;throw 1;}",BAD_EXCEPTION_REPLACEMENT);
    }
    @Test void writtenSpecificationIncludesInitializedBase() {
        f.check("struct A{A(){throw 1;}};struct S:A{S()throw():A(){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void outOfClassInitializerBinding() {
        f.check("struct A{};struct S:A{A a;S();};S::S():A(),a(){}void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
    }
    @Test void nonemptyScalarObjectArgumentsComplete() {
        f.check("struct A{A(int){}};struct S{A a;S():a(1){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
    }
}
