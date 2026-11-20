/* Explicit constructor argument outcomes. MIT License. */
package com.kniazkov.cpg;
import org.junit.jupiter.api.Test;
import static com.kniazkov.cpg.model.AuxiliaryFlowVertex.Kind.*;
class UnexpectedHandlerExplicitConstructionTest {
    final UnexpectedHandlerLifetimeTest f = new UnexpectedHandlerLifetimeTest();
    @Test void directConstructorBodyThrows() {
        f.check("struct S{S(int n){throw 1;}~S(){std::terminate();}};void h(){S s(3);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void directArgumentIsOutsideSpecification() {
        f.check("int fail(){throw 1;}struct S{S(int n)throw(){std::terminate();}};void h(){S s(fail());throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void suppliedArgumentReplacesDefault() {
        f.check("int fail(){std::terminate();}struct S{S(int n=fail()){throw 1;}};void h(){S s(2);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void remainingDefaultsAreEvaluated() {
        f.check("int fail(){throw 1;}struct S{S(int n,int m=fail())throw(){std::terminate();}};void h(){S s(2);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void allExplicitArgumentOrdersContribute() {
        f.check("int a(){throw 1;}int b(){throw true;}struct S{S(int x,int y){std::terminate();}};void h(){S s(a(),b());throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT);
    }
    @Test void mixedArgumentOrdersContribute() {
        f.check("int a(){throw 1;}int b(){throw true;}struct S{S(int x,int y=b()){std::terminate();}};void h(){S s(a());throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT);
    }
    @Test void overloadSelectionUsesTypes() {
        f.check("struct S{S(int n){throw 1;}S(bool b){std::terminate();}};void h(){S s(2);throw true;}",EXCEPTION_REPLACEMENT);
        f.check("struct S{S(int n){throw 1;}S(bool b){std::terminate();}};void h(){S s(true);throw true;}",TERMINATE_EXIT);
    }
    @Test void failedArgumentUnwindsEarlierObjects() {
        f.check("int fail(){throw 1;}struct A{~A(){throw 2;}};struct S{S(int n){}};void h(){A a;S s(fail());throw true;}",TERMINATE_EXIT);
    }
    @Test void baseArgumentPrecedesBaseConstruction() {
        f.check("int fail(){throw 1;}struct A{A(int n){std::terminate();}~A(){std::terminate();}};struct S:A{S():A(fail()){std::terminate();}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void fieldArgumentFailureUnwindsPreviousField() {
        f.check("int fail(){throw 1;}struct A{~A(){throw 2;}};struct B{B(int n){std::terminate();}};struct S{A a;B b;S():b(fail()){}};void h(){S s;throw true;}",TERMINATE_EXIT);
    }
    @Test void fieldOrderIsDeclarationOrder() {
        f.check("int fail(){throw 1;}int stop(){std::terminate();}struct A{A(int n){}};struct S{A a;A b;S():b(stop()),a(fail()){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void baseOrderIsDeclarationOrder() {
        f.check("int fail(){throw 1;}int stop(){std::terminate();}struct A{A(int n){}};struct B{B(int n){}};struct S:A,B{S():B(stop()),A(fail()){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void nondefaultConstructorInitializesItsMembers() {
        f.check("int fail(){throw 1;}struct A{A(int n){}};struct S{A a;S(int n):a(fail()){std::terminate();}};void h(){S s(2);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void outerSpecificationStillConstrainsFieldArguments() {
        f.check("int fail(){throw 1;}struct A{A(int n)throw(){}};struct S{A a;S()throw():a(fail()){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void arraysUseExplicitSubobjectArguments() {
        f.check("int fail(){throw 1;}struct A{A(int n){}};struct S{A a;S():a(fail()){}~S(){std::terminate();}};void h(){S s[3];throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void ignoredVirtualBaseArgumentsAreNotEvaluated() {
        f.check("int stop(){std::terminate();}struct V{V(int n){}};struct A:virtual V{A():V(stop()){}};struct S:A{S():V(1){throw 1;}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void outOfClassDefinitionUsesSelectedDeclaration() {
        f.check("struct S{S(int n);};S::S(int n){throw 1;}void h(){S s(2);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void namedScalarReferencesBindWithoutConstruction() {
        f.check("struct S{S(int& n){throw 1;}};void h(){int n=1;S s(n);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void copyInitializationRemainsConservative() {
        f.check("struct S{S(int n){throw 1;}};void h(){S s=2;throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void pointerAndMemberPointerArguments() {
        f.check("struct S{S(int* p){throw 1;}};void h(){int n=0;S s(&n);throw true;}",EXCEPTION_REPLACEMENT);
        f.check("struct A{int n;};struct S{S(int A::*p){throw 1;}};void h(){S s(&A::n);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void onlySelectedSpecificationApplies() {
        f.check("struct S{S(int n)throw(int){throw 1;}S(bool b)throw(){}};void h(){S s(2);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void userDefinedConversionsStayConservative() {
        f.check("struct A{operator int(){throw true;}};struct S{S(int n){throw 1;}};void h(){A a;S s(a);throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void unknownConstructorBodyStaysConservative() {
        f.check("struct S{S(int n);};void h(){S s(2);throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
}
