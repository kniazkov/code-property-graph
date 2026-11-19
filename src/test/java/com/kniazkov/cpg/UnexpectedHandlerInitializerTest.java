package com.kniazkov.cpg;
import org.junit.jupiter.api.Test;
import static com.kniazkov.cpg.model.AuxiliaryFlowVertex.Kind.*;
class UnexpectedHandlerInitializerTest {
    final UnexpectedHandlerLifetimeTest fixture = new UnexpectedHandlerLifetimeTest();
    @Test void scalarAndOutOfClassInitializers() {
        fixture.check("struct S{int x;S():x(1){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
        fixture.check("struct S{int x;S();};S::S():x(1){}void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);
    }
    @Test void throwingInitializerSkipsBodyAndWholeDestructor() {
        fixture.check("int fail(){throw 1;}struct S{int x;S():x(fail()){throw true;}~S(){throw 2;}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void declarationOrderOverridesWrittenOrder() {
        fixture.check("int first(){throw 1;}int second(){throw true;}struct S{int x;int y;S():y(second()),x(first()){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void completedFieldsUnwind() {
        fixture.check("int fail(){throw 1;}struct A{~A(){throw 2;}};struct S{A a;int x;S():x(fail()){}};void h(){S s;throw true;}",TERMINATE_EXIT);
    }
    @Test void virtualBaseUnwindsBeforeFailedConstructorCompletes() {
        fixture.check("int fail(){throw 1;}struct V{~V(){throw 2;}};struct S:virtual V{int x;S():x(fail()){}};void h(){S s;throw true;}",TERMINATE_EXIT);
    }
    @Test void specificationCoversInitializer() {
        fixture.check("int fail(){throw 1;}struct S{int x;S()throw():x(fail()){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
}
