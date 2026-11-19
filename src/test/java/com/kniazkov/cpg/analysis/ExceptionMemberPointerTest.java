package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExceptionMemberPointerTest {
    boolean matches(String a,String b){return ExceptionMatching.catches(Project.create().build(),a,b);}
    @Test void directMembersRequireIdentityInTheOriginalProfile(){
        assertTrue(matches("member-pointer(named(A),int)","member-pointer(named(A),int)"));
        assertFalse(matches("member-pointer(named(A),int)","member-pointer(named(B),int)"));
        assertFalse(matches("member-pointer(named(A),int)","member-pointer(named(A),const(int))"));
        assertFalse(matches("member-pointer(named(A),int)","pointer(void)"));
    }
    @Test void mixedQualificationRetainsOwnersAndIntermediateConst(){
        assertTrue(matches("pointer(member-pointer(named(A),int))","pointer(const(member-pointer(named(A),const(int))))"));
        assertFalse(matches("pointer(member-pointer(named(A),int))","pointer(member-pointer(named(A),const(int)))"));
        assertFalse(matches("pointer(member-pointer(named(A),int))","pointer(const(member-pointer(named(B),const(int))))"));
        assertFalse(matches("pointer(member-pointer(named(A),const(int)))","pointer(const(member-pointer(named(A),int)))"));
    }
    @Test void functionMemberSignaturesAreNotSplitAtParameterCommas(){
        String a="pointer(member-pointer(named(A),function(int,double)->void))";
        assertTrue(matches(a,"pointer(const(member-pointer(named(A),function(int,double)->void)))"));
        assertFalse(matches(a,"pointer(const(member-pointer(named(A),function(double,int)->void)))"));
    }
    @Test void apiThrowAndCatchRetainMemberIdentity(){
        var p=ControlFlow.analyze(Project.create().addFile(com.kniazkov.cpg.parser.Parser.parse("a.cpp","struct A{int x;};void f(){int A::* p=&A::x;try{throw p;}catch(int A::*){}}",Language.CPP)).build());
        var f=p.flows().getFirst();assertTrue(f.complete());assertTrue(f.exceptionalExit().isEmpty());
    }
}
