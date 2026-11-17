/* Indexed C++03 array lifetime flow regressions. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ArrayCleanupFlowTest {
    @TempDir Path dir;
    static final String GUARD="struct Guard{Guard();~Guard()throw();};";
    Project read(String source) throws Exception {
        Files.writeString(dir.resolve("a.cpp"),source);
        return Cpg.read(dir,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED));
    }
    static FunctionFlow flow(Project p,String name){return p.flows().stream().filter(f->f.entry().name().equals(name)).findFirst().orElseThrow();}
    static List<AuxiliaryFlowVertex> constructors(FunctionFlow f){return f.auxiliaryVertices().stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_CONSTRUCTOR&&a.elementIndex().isPresent()).sorted(Comparator.comparingInt(a->a.elementIndex().orElseThrow())).toList();}
    static FlowVertex failure(FunctionFlow f,FlowVertex v){return f.control().stream().filter(e->e.from()==v&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow().to();}
    static List<AuxiliaryFlowVertex> path(FunctionFlow f,FlowVertex start,FlowVertex end){
        var result=new ArrayList<AuxiliaryFlowVertex>();var seen=new HashSet<FlowVertex>();var at=start;
        while(at!=end){assertTrue(seen.add(at),"cycle");if(at instanceof AuxiliaryFlowVertex a)result.add(a);var current=at;
            at=f.control().stream().filter(e->e.from()==current&&(e.kind()!=Branch.EXCEPTION||current instanceof AuxiliaryFlowVertex a&&a.kind()==AuxiliaryFlowVertex.Kind.ARRAY_UNWIND)).findFirst().orElseThrow().to();}
        return result;
    }
    static List<Integer> destroyed(List<AuxiliaryFlowVertex> path){return path.stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR&&a.elementIndex().isPresent()).map(a->a.elementIndex().getAsInt()).toList();}
    @Test void firstMiddleLastFailureAndSuccessDestroyOnlyConstructedElements() throws Exception {
        var p=read(GUARD+"void f(){try{Guard before;Guard objects[3];Guard after;}catch(...){}}");var f=flow(p,"f");assertTrue(f.complete());
        var handler=ExceptionCleanupFlowTest.nodes(f.entry(),CatchClause.class).getFirst();var ctors=constructors(f);assertEquals(3,ctors.size());
        for(int failed=0;failed<3;failed++){
            var actual=path(f,failure(f,ctors.get(failed)),handler);var expected=new ArrayList<Integer>();for(int i=failed-1;i>=0;i--)expected.add(i);
            assertEquals(expected,destroyed(actual));assertEquals(List.of("before"),actual.stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR&&a.elementIndex().isEmpty()).map(a->((VariableDeclaration)a.object().orElseThrow()).name()).toList());
            assertTrue(actual.stream().noneMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.ARRAY_DEALLOCATION));
        }
        assertEquals(List.of(2,1,0),destroyed(path(f,f.entry(),f.exit())));
        for(var a:f.auxiliaryVertices())if(a.elementIndex().isPresent()){assertSame(ctors.getFirst().object().orElseThrow(),a.object().orElseThrow());assertTrue(a.constructor().or(a::destructor).isPresent());}
        assertTrue(f.data().stream().anyMatch(e->e.kind()==Dependency.DEFINITION&&e.from()==ctors.getFirst().object().orElseThrow()&&e.to()==e.from()));
    }
    @Test void newArrayReleasesStorageAfterPartialCleanupButNotOnAllocationFailureOrSuccess() throws Exception {
        var p=read(GUARD+"void f(){try{Guard before;Guard* p=new Guard[3];}catch(...){}}");var f=flow(p,"f");assertTrue(f.complete());
        var allocation=ExceptionCleanupFlowTest.nodes(f.entry(),NewExpression.class).getFirst();var handler=ExceptionCleanupFlowTest.nodes(f.entry(),CatchClause.class).getFirst();
        assertTrue(path(f,failure(f,allocation),handler).stream().noneMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.ARRAY_DEALLOCATION||a.elementIndex().isPresent()));
        for(int failed=0;failed<3;failed++){
            var actual=path(f,failure(f,constructors(f).get(failed)),handler);var expected=new ArrayList<Integer>();for(int i=failed-1;i>=0;i--)expected.add(i);
            assertEquals(expected,destroyed(actual));var releases=actual.stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.ARRAY_DEALLOCATION).toList();assertEquals(1,releases.size());assertSame(allocation,releases.getFirst().object().orElseThrow());
            assertEquals(failed,actual.indexOf(releases.getFirst()));
        }
        assertTrue(path(f,f.entry(),f.exit()).stream().noneMatch(a->a.kind()==AuxiliaryFlowVertex.Kind.ARRAY_DEALLOCATION||a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR&&a.elementIndex().isPresent()));
        assertTrue(f.data().stream().anyMatch(e->e.from()==allocation&&e.to()==allocation&&e.kind()==Dependency.DEFINITION));
    }
    @Test void memberArraysCleanBeforeEarlierSubobjectsAndFunctionTryHandler() throws Exception {
        var p=read(GUARD+"struct Owner{Guard first;Guard items[3];Guard last;Owner()try{}catch(...){throw;}~Owner(){}};");var f=flow(p,"Owner");assertTrue(f.complete());
        var actual=path(f,failure(f,constructors(f).get(2)),f.entry().handlers().getFirst());assertEquals(List.of(1,0),destroyed(actual));
        assertEquals("first",((VariableDeclaration)actual.getLast().object().orElseThrow()).name());
        var destructor=p.flows().stream().filter(x->x.entry().kind()==CallableDeclaration.Kind.DESTRUCTOR&&x.entry().body().isPresent()).findFirst().orElseThrow();assertTrue(destructor.complete());assertEquals(List.of(2,1,0),destroyed(path(destructor,destructor.entry(),destructor.exit())));
    }
    @Test void destructorFailureDestroysRemainingPrefixAndUnwindFailureTerminates() throws Exception {
        var p=read("struct Guard{Guard();~Guard();};void f(){Guard a[3];}");var f=flow(p,"f");assertTrue(f.complete());
        var normal=path(f,f.entry(),f.exit()).stream().filter(a->a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR).findFirst().orElseThrow();
        assertEquals(List.of(1,0),destroyed(path(f,failure(f,normal),f.exceptionalExit().orElseThrow())));
        var partial=path(f,failure(f,constructors(f).get(2)),f.exceptionalExit().orElseThrow());
        for(var a:partial)if(a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR)assertEquals(AuxiliaryFlowVertex.Kind.TERMINATE_EXIT,((AuxiliaryFlowVertex)failure(f,a)).kind());
    }
    @Test void copiesReanalysisEditsJsonAndGenerationRetainElementIdentity() throws Exception {
        var p=read(GUARD+"void f(){Guard a[3];}");
        var copied=p.withDiagnostics("copy-test",Map.of(p,new Diagnostic("copy-test",Diagnostic.Severity.WARNING,"copy")));
        for(var snapshot:List.of(p,copied,DataFlow.analyze(ControlFlow.analyze(p.edit().build())),DataFlow.analyze(ControlFlow.analyze(TypeNormalizer.green(p))),DataFlow.analyze(ControlFlow.analyze(ConstantFolder.fold(p))))){
            var f=flow(snapshot,"f");assertTrue(f.complete());assertEquals(3,constructors(f).size());
            for(var a:constructors(f)){assertSame(f.entry(),a.owner());assertTrue(ExceptionCleanupFlowTest.nodes(snapshot,Node.class).contains(a.object().orElseThrow()));}
            var json=ProjectJson.encode(snapshot).toText("  ");assertTrue(json.contains("\"elementIndex\": 2"));assertTrue(json.contains("\"constructorId\""));assertTrue(json.contains("\"destructorId\""));
            var source=Cpg.sources(snapshot,Language.CPP).get("a.cpp");assertFalse(source.contains(".~"));assertEquals(3,constructors(flow(read(source),"f")).size());
        }
        var editor=new ProjectEditor(p);var bound=ExceptionCleanupFlowTest.nodes(p,ArrayType.class).getFirst().bound().orElseThrow();editor.replace(bound,IntegerLiteral.create().setValue(1).build());
        var edited=DataFlow.analyze(ControlFlow.analyze(editor.build()));assertEquals(1,constructors(flow(edited,"f")).size());
    }
    @Test void noexceptZeroLengthAndDirectReturnContexts() throws Exception {
        var p=read("struct Guard{Guard()throw();~Guard()throw();};void f(){Guard a[2];}Guard* r(){return new Guard[2];}void zero(){new Guard[0];}");
        var f=flow(p,"f");assertTrue(f.complete());assertTrue(f.exceptionalExit().isEmpty());assertEquals(2,constructors(flow(p,"r")).size());assertTrue(flow(p,"zero").complete());assertTrue(constructors(flow(p,"zero")).isEmpty());
    }
    @Test void unevaluatedAllocationDoesNotIntroduceLifetimes() throws Exception {
        var p=read(GUARD+"void f(int n){sizeof(new Guard[n]);}");var f=flow(p,"f");assertTrue(f.complete());assertTrue(constructors(f).isEmpty());assertTrue(f.exceptionalExit().isEmpty());
    }
    @Test void nestedTryAndEarlyExitPreserveReverseOrder() throws Exception {
        var p=read(GUARD+"void f(){Guard outer;try{Guard a[2];try{Guard b[3];return;}catch(...){}}catch(...){}}");var f=flow(p,"f");assertTrue(f.complete());
        assertEquals(List.of(2,1,0,1,0),destroyed(path(f,f.entry(),f.exit())));
    }
    @Test void placementAndClassSpecificAllocationRemainIncomplete() throws Exception {
        for(String source:List.of(GUARD+"void* operator new[](unsigned long,void*);void f(void* p){new(p) Guard[2];}",
                "struct Guard{Guard();~Guard();static void* operator new[](unsigned long);static void operator delete[](void*);};void f(){new Guard[2];}")){
            var f=flow(read(source),"f");assertFalse(f.complete());assertTrue(f.control().isEmpty());
        }
    }
    @Test void unsupportedShapesNeverPublishApparentlyCompleteFlow() throws Exception {
        for(String body:List.of("Guard a[65];","Guard a[2][2];","static Guard a[2];","new Guard[n];","consume(new Guard[2]);","Guard a[2]={Guard(),Guard()};")){
            var p=read(GUARD+"void consume(Guard*);void f(int n){"+body+"}");var f=flow(p,"f");assertFalse(f.complete(),body);assertTrue(f.control().isEmpty());assertTrue(f.data().isEmpty());
        }
    }
}
