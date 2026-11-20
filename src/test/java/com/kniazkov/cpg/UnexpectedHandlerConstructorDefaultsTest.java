package com.kniazkov.cpg;
import org.junit.jupiter.api.Test;
import static com.kniazkov.cpg.model.AuxiliaryFlowVertex.Kind.*;
class UnexpectedHandlerConstructorDefaultsTest {
    final UnexpectedHandlerLifetimeTest f=new UnexpectedHandlerLifetimeTest();
    @Test void scalarDefaultsCompleteNormally(){f.check("struct S{S(int n=1){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT);}
    @Test void argumentExceptionIsOutsideConstructorSpecification(){f.check("int fail(){throw 1;}struct S{S(int n=fail())throw(){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);}
    @Test void failedArgumentSkipsSubobjectsAndBody(){f.check("int fail(){throw 1;}struct A{A(){std::terminate();}};struct S:A{S(int n=fail()){std::terminate();}~S(){std::terminate();}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);}
    @Test void priorObjectsStillUnwind(){f.check("int fail(){throw 1;}struct A{~A(){throw 2;}};struct S{S(int n=fail()){}};void h(){A a;S s;throw true;}",TERMINATE_EXIT);}
    @Test void fieldDefaultsAreCoveredByOuterSpecification(){f.check("int fail(){throw 1;}struct A{A(int n=fail())throw(){}};struct S{A a;S()throw(){}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);}
    @Test void basesAndArraysUseDefaults(){
        f.check("int fail(){throw 1;}struct A{A(int n=fail()){}};struct S:A{};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);
        f.check("int fail(){throw 1;}struct S{S(int n=fail()){}~S(){std::terminate();}};void h(){S s[3];throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void allArgumentOrdersContribute(){f.check("int a(){throw 1;}int b(){throw true;}struct S{S(int x=a(),int y=b()){std::terminate();}};void h(){S s;throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT);}
    @Test void unknownDefaultKeepsAlternatives(){f.check("int unknown();struct S{S(int n=unknown()){}};void h(){S s;throw 1;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);}
    @Test void outOfClassBodyUsesDeclarationDefaults(){f.check("int fail(){throw 1;}struct S{S(int n=fail())throw();};S::S(int n)throw(){}void h(){S s;throw true;}",EXCEPTION_REPLACEMENT);}
}
