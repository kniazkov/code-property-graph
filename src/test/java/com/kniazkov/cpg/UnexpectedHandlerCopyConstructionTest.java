/* Named-lvalue copy and reference argument outcomes. MIT License. */
package com.kniazkov.cpg;
import org.junit.jupiter.api.Test;
import static com.kniazkov.cpg.model.AuxiliaryFlowVertex.Kind.*;
class UnexpectedHandlerCopyConstructionTest {
    final UnexpectedHandlerLifetimeTest f = new UnexpectedHandlerLifetimeTest();
    @Test void copyInitializationCallsUserCopy() {
        f.check("struct S{S(){}S(const S& s){throw 1;}};void h(){S a;S b=a;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void directCopyCallsUserCopy() {
        f.check("struct S{S(){}S(const S& s){throw 1;}};void h(){S a;S b(a);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void constAndMutableCopiesSelectDifferentBodies() {
        f.check("struct S{S(){}S(S& s){throw 1;}S(const S& s){std::terminate();}};void h(){S a;S b=a;throw true;}",EXCEPTION_REPLACEMENT);
        f.check("struct S{S(){}S(S& s){throw 1;}S(const S& s){std::terminate();}};void h(){const S a;S b=a;throw true;}",TERMINATE_EXIT);
    }
    @Test void sourceUnwindsAfterCopyFailure() {
        f.check("struct S{S(){}S(const S& s){throw 1;}~S(){throw 2;}};void h(){S a;S b=a;throw true;}",TERMINATE_EXIT);
    }
    @Test void unstartedCopyIsNotDestroyed() {
        f.check("struct S{S(){}S(const S& s){throw 1;}~S(){std::terminate();}};S original;void h(){S b=original;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void successfulCopyIsDestroyedOnScopeExit() {
        f.check("struct S{S(){}S(const S& s){}~S(){throw 1;}};S original;void h(){{S b=original;}throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void copyDefaultsAreOutsideCalleeSpecification() {
        f.check("int fail(){throw 1;}struct S{S(){}S(const S& s,int n=fail())throw(){std::terminate();}};void h(){S a;S b=a;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void copyBodySpecificationIsEnforced() {
        f.check("struct S{S(){}S(const S& s)throw(){throw 1;}};void h(){S a;S b=a;throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void copyConstructorCanCallReferenceHelper() {
        f.check("struct S;void helper(const S& s){throw 1;}struct S{S(){}S(const S& s){helper(s);}};void h(){S a;S b=a;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void fieldCopyFailureCleansPreviousField() {
        f.check("struct A{~A(){throw 2;}};struct B{B(){}B(const B& b){throw 1;}};B original;struct S{A a;B b;S():b(original){}};void h(){S s;throw true;}",TERMINATE_EXIT);
    }
    @Test void baseCopyPrecedesFieldInitialization() {
        f.check("struct B{B(){}B(const B& b){throw 1;}};B original;struct A{A(){std::terminate();}};struct S:B{A a;S():B(original){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void ignoredVirtualCopyIsNotInvoked() {
        f.check("struct V{V(){}V(const V& v){std::terminate();}};V original;struct B:virtual V{B():V(original){}};struct S:B{S(){throw 1;}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void arrayElementFieldCopyUsesLifetimeComposition() {
        f.check("struct B{B(){}B(const B& b){throw 1;}};B original;struct S{B b;S():b(original){}~S(){std::terminate();}};void h(){S s[3];throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void outOfClassCopyDefinition() {
        f.check("struct S{S(){}S(const S&);};S::S(const S& s){throw 1;}void h(){S a;S b=a;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void implicitCopiesRemainConservative() {
        f.check("struct S{};void h(){S a;S b=a;throw 1;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void temporaryReferenceArgumentsRemainConservative() {
        f.check("struct S{S(const int& n){throw 1;}};void h(){S s(1);throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void referenceDefaultBindsNamedObject() {
        f.check("int n=0;struct S{S(int& r=n){throw 1;}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void helperDefaultsAreOutsideSpecification() {
        f.check("int fail(){throw 1;}void helper(int n=fail())throw(){std::terminate();}void h(){helper();throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void failedHelperArgumentSkipsBody() {
        f.check("int fail(){throw 1;}void helper(int n){std::terminate();}void h(){helper(fail());throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void helperArgumentOrdersRemainUnspecified() {
        f.check("int a(){throw 1;}int b(){throw true;}void helper(int x,int y){std::terminate();}void h(){helper(a(),b());throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT);
    }
    @Test void scalarReferenceHelper() {
        f.check("void helper(int& n){throw 1;}void h(){int n=0;helper(n);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void explicitCopyIsAllowedForDirectInitialization() {
        f.check("struct S{S(){}explicit S(const S& s){throw 1;}};void h(){S a;S b(a);throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void helperReferenceDefaultBindsNamedObject() {
        f.check("int n=0;void helper(int& r=n){throw 1;}void h(){helper();throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void convertedReferenceTemporaryRemainsUnknown() {
        f.check("void helper(const double& r){throw 1;}void h(){int n=0;helper(n);throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void subobjectReferenceArgumentRemainsUnknown() {
        f.check("struct A{int n;};void helper(int& r){throw 1;}void h(){A a;helper(a.n);throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void byValueObjectParameterRemainsUnknown() {
        f.check("struct A{};void helper(A a){throw 1;}void h(){A a;helper(a);throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
}
