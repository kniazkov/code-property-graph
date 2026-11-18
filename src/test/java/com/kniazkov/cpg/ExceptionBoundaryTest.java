/* Unknown-handler boundary summaries retain all permitted alternatives. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExceptionBoundaryTest {
    Project read(String s){return DataFlow.analyze(ControlFlow.analyze(Project.create().addFile(com.kniazkov.cpg.parser.Parser.parse("a.cpp",s,Language.CPP)).build()));}
    FunctionFlow flow(Project p){return p.flows().stream().filter(f->f.entry().name().equals("f")).findFirst().orElseThrow();}
    long count(FunctionFlow f,AuxiliaryFlowVertex.Kind k){return f.auxiliaryVertices().stream().filter(a->a.kind()==k).count();}
    @Test void knownAllowedUnknownAndEmptySpecifications(){
        assertEquals(0,count(flow(read("void f()throw(int){throw 1;}")),AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL));
        var violation=flow(read("void f()throw(int){throw 1.0;}"));assertTrue(violation.complete());assertEquals(1,count(violation,AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT));
        var unknown=flow(read("void work();void f()throw(int){work();}"));assertTrue(unknown.complete());assertEquals(1,count(unknown,AuxiliaryFlowVertex.Kind.EXCEPTION_SPEC_CHECK));
        var empty=flow(read("void f()throw(){throw 1;}"));assertTrue(empty.complete());assertEquals(1,count(empty,AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL));assertTrue(empty.exceptionalExit().isEmpty());
        assertEquals(0,count(flow(read("void f(){throw 1;}")),AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL));
    }
    @Test void badExceptionFallbackUsesStandardDeclaration(){
        var f=flow(read("#include <exception>\nvoid f()throw(std::bad_exception){throw 1;}"));assertTrue(f.complete());assertEquals(1,count(f,AuxiliaryFlowVertex.Kind.BAD_EXCEPTION_REPLACEMENT));
        assertEquals(0,count(flow(read("struct bad_exception{};void f()throw(bad_exception){throw 1;}")),AuxiliaryFlowVertex.Kind.BAD_EXCEPTION_REPLACEMENT));
    }
    @Test void cleanupPrecedesUnexpectedAndLocalCatchAvoidsBoundary(){
        var f=flow(read("struct G{G()throw();~G()throw();};void f()throw(int){G g;throw 1.0;}"));assertTrue(f.complete());
        var unexpected=f.auxiliaryVertices().stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL).findFirst().orElseThrow();
        assertTrue(f.control().stream().anyMatch(e->e.to()==unexpected&&e.from() instanceof AuxiliaryFlowVertex a&&a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR));
        assertEquals(0,count(flow(read("void f()throw(){try{throw 1;}catch(int){}}")),AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL));
    }
    @Test void copiesJsonAndSpecificationEditsPreserveAndRecompute(){
        var p=read("void f()throw(int){throw 1.0;}");var copy=p.withDiagnostics("probe",Map.of(p,new Diagnostic("probe",Diagnostic.Severity.INFO,"copy")));
        assertEquals(1,count(flow(copy),AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT));
        var event=flow(copy).auxiliaryVertices().stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.EXCEPTION_REPLACEMENT).findFirst().orElseThrow();assertSame(flow(copy).entry().exceptionTypes().getFirst(),event.object().orElseThrow());
        assertTrue(ProjectJson.encode(copy).toText("").contains("UNEXPECTED_CALL"));
        var editor=new ProjectEditor(p);editor.replace(flow(p).entry().exceptionTypes().getFirst(),NamedType.create().setName("double").build());
        assertEquals(0,count(flow(DataFlow.analyze(ControlFlow.analyze(editor.build()))),AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL));
    }
}
