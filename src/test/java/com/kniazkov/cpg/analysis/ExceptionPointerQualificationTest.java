package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExceptionPointerQualificationTest {
    boolean matches(String a,String b){return ExceptionMatching.catches(Project.create().build(),a,b);}
    @Test void deepQualificationRequiresAllInterveningConst(){
        assertTrue(matches("pointer(pointer(int))","pointer(const(pointer(const(int))))"));
        assertFalse(matches("pointer(pointer(int))","pointer(pointer(const(int)))"));
        assertTrue(matches("pointer(pointer(pointer(int)))","pointer(const(pointer(const(pointer(volatile(int))))))"));
        assertFalse(matches("pointer(pointer(pointer(int)))","pointer(const(pointer(pointer(volatile(int)))))"));
    }
    @Test void qualifiersCannotBeRemovedAtAnyLevel(){
        assertFalse(matches("pointer(const(volatile(int)))","pointer(volatile(int))"));
        assertFalse(matches("pointer(pointer(volatile(int)))","pointer(const(pointer(int)))"));
        assertTrue(matches("pointer(volatile(int))","pointer(const(volatile(int)))"));
    }
    @Test void voidConversionDoesNotInspectQualifiersInsideThePointedObject(){
        assertTrue(matches("pointer(pointer(volatile(int)))","pointer(void)"));
        assertFalse(matches("pointer(volatile(pointer(int)))","pointer(void)"));
        assertFalse(matches("pointer(pointer(int))","pointer(pointer(void))"));
        assertFalse(matches("pointer(function()->void)","pointer(void)"));
    }
    @Test void catchAndSpecificationUseTheSameConversion(){
        for(String code:new String[]{"void f(){int** p=0;try{throw p;}catch(const int*const*){}}", "void f()throw(const int*const*){int** p=0;throw p;}"}){
            var p=ControlFlow.analyze(Project.create().addFile(com.kniazkov.cpg.parser.Parser.parse("a.cpp",code,Language.CPP)).build());
            var f=p.flows().getFirst();assertTrue(f.complete());
            assertTrue(f.auxiliaryVertices().stream().noneMatch(v->v.kind()==AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL));
            if(!code.contains("f()throw"))assertTrue(f.exceptionalExit().isEmpty());
        }
    }
}
