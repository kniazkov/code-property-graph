/* Saved handler values and context-specific direct-call effects. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedHandlerStateTest {
    Project read(String code) {
        return DataFlow.analyze(ControlFlow.analyze(Project.create().addFile(com.kniazkov.cpg.parser.Parser.parse("a.cpp",
                "#include <exception>\nvoid h(){throw 1;}void k(){throw 2;}void opaque()throw();" + code, Language.CPP)).build()));
    }
    List<AuxiliaryFlowVertex> events(Project p, String function) {
        var f = p.flows().stream().filter(v -> v.entry().name().equals(function)).findFirst().orElseThrow();
        assertTrue(f.complete());
        return f.auxiliaryVertices().stream().filter(a -> a.kind() == AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL).toList();
    }
    void check(String code, boolean unknown, String... names) {
        var events = events(read(code), "f");assertFalse(events.isEmpty());
        for (var event : events) {
            assertEquals(unknown, event.unknownUnexpectedHandler(), "unknown at " + event.index());
            assertEquals(Set.of(names), new HashSet<>(event.unexpectedHandlers().stream().map(CallableDeclaration::name).toList()));
        }
    }
    @Test void savesRestoresCopiesAndOverwritesLocalPointers() {
        check("void f()throw(int){std::set_unexpected(h);std::unexpected_handler old=std::set_unexpected(k);std::set_unexpected(old);throw 1.0;}",false,"h");
        check("void f()throw(int){std::unexpected_handler p=h;std::unexpected_handler q=p;p=k;std::set_unexpected(q);throw 1.0;}",false,"h");
        check("void f()throw(int){std::unexpected_handler p=h;p=k;std::set_unexpected(p);throw 1.0;}",false,"k");
        check("void f()throw(int){std::set_unexpected(h);std::unexpected_handler p=k;p=std::set_unexpected(p);std::set_unexpected(p);throw 1.0;}",false,"h");
    }
    @Test void joinsPointerValuesAcrossBranchesAndLoops() {
        check("void f(bool b)throw(int){std::unexpected_handler p=h;if(b)p=k;std::set_unexpected(p);throw 1.0;}",false,"h","k");
        check("void f(bool b)throw(int){std::unexpected_handler p=h;while(b){p=k;b=false;}std::set_unexpected(p);throw 1.0;}",false,"h","k");
        check("void f(bool b)throw(int){std::unexpected_handler p;if(b)p=h;std::set_unexpected(p);throw 1.0;}",true,"h");
    }
    @Test void followsInstallerParametersAndReturnValues() {
        check("void install(std::unexpected_handler p)throw(){std::set_unexpected(p);}void f()throw(int){install(h);throw 1.0;}",false,"h");
        check("std::unexpected_handler exchange(std::unexpected_handler p)throw(){return std::set_unexpected(p);}void f()throw(int){std::set_unexpected(h);std::unexpected_handler old=exchange(k);exchange(old);throw 1.0;}",false,"h");
        check("std::unexpected_handler identity(std::unexpected_handler p)throw(){return p;}void f()throw(int){std::unexpected_handler p=identity(k);std::set_unexpected(p);throw 1.0;}",false,"k");
    }
    @Test void joinsHelperReturnValuesAndRestoresAcrossNormalCalls() {
        check("std::unexpected_handler pick(bool b)throw(){if(b)return h;return k;}void f(bool b)throw(int){std::unexpected_handler p=pick(b);std::set_unexpected(p);throw 1.0;}",false,"h","k");
        check("void temporary()throw(){std::unexpected_handler old=std::set_unexpected(k);std::set_unexpected(old);}void f()throw(int){std::set_unexpected(h);temporary();throw 1.0;}",false,"h");
    }
    @Test void followsNestedCallsAndKeepsCallContextsSeparate() {
        check("void install(std::unexpected_handler p)throw(){std::set_unexpected(p);}void outer(std::unexpected_handler p)throw(){install(p);}void f()throw(int){outer(h);outer(k);throw 1.0;}",false,"k");
        var p=read("void install(std::unexpected_handler p)throw(){std::set_unexpected(p);}void f()throw(int){install(h);throw 1.0;}void g()throw(int){install(k);throw 1.0;}");
        assertEquals("h",events(p,"f").getFirst().unexpectedHandlers().getFirst().name());
        assertEquals("k",events(p,"g").getFirst().unexpectedHandlers().getFirst().name());
    }
    @Test void carriesCapturedHandlerAcrossThrowingCallAndCleanup() {
        check("void fail(){throw 1.0;}void f()throw(int){std::set_unexpected(h);fail();}",false,"h");
        check("void fail(){std::set_unexpected(k);throw 1.0;}void f()throw(int){std::set_unexpected(h);fail();}",false,"k");
        check("struct G{G()throw();~G()throw(){std::set_unexpected(k);}};void fail(){G g;std::set_unexpected(h);throw 1.0;}void f()throw(int){fail();}",false,"h");
    }
    @Test void externalIndirectAndRecursiveCallsRemainConservative() {
        check("void f()throw(int){std::set_unexpected(h);opaque();throw 1.0;}",true);
        check("void recurse()throw(){recurse();}void f()throw(int){std::set_unexpected(h);recurse();throw 1.0;}",true);
        check("void f(void (*p)())throw(int){std::set_unexpected(h);p();}",true);
        check("void f()throw(int){std::unexpected_handler old=std::set_unexpected(h);std::set_unexpected(old);throw 1.0;}",true);
    }
    @Test void aliasedAndStaticStorageNeverInventsPrivateBindings() {
        check("void mutate(std::unexpected_handler *p)throw(){*p=k;}void f()throw(int){std::unexpected_handler p=h;mutate(&p);std::set_unexpected(p);throw 1.0;}",true);
        check("void f()throw(int){std::unexpected_handler p=h;std::unexpected_handler &r=p;r=k;std::set_unexpected(p);throw 1.0;}",true);
        check("void f()throw(int){static std::unexpected_handler p=h;std::set_unexpected(p);throw 1.0;}",true);
    }
    @Test void qualifiedHandlersAndBoundedDepth() {
        check("namespace N{void handler(){throw 3;}}void f()throw(int){std::unexpected_handler p=&N::handler;std::set_unexpected(p);throw 1.0;}",false,"handler");
        var code = new StringBuilder("void deep0()throw(){std::set_unexpected(k);}");
        for(int i=1;i<=10;i++)code.append("void deep").append(i).append("()throw(){deep").append(i-1).append("();}");
        check(code+"void f()throw(int){std::set_unexpected(h);deep10();throw 1.0;}",true);
    }
    @Test void directCallsResolveAcrossTranslationUnits() {
        var builder=Project.create();
        builder.addFile(com.kniazkov.cpg.parser.Parser.parse("a.cpp","#include <exception>\nvoid install(std::unexpected_handler)throw();void h(){throw 1;}void f()throw(int){install(h);throw 1.0;}",Language.CPP));
        builder.addFile(com.kniazkov.cpg.parser.Parser.parse("b.cpp","#include <exception>\nvoid install(std::unexpected_handler p)throw(){std::set_unexpected(p);}",Language.CPP));
        var p=DataFlow.analyze(ControlFlow.analyze(builder.build()));
        var event=events(p,"f").getFirst();assertFalse(event.unknownUnexpectedHandler());
        assertEquals("h",event.unexpectedHandlers().getFirst().name());
    }
    @Test void copyAndReanalysisKeepOnlySnapshotOwnedTargets() {
        var p=read("void install()throw(){std::set_unexpected(h);}void f()throw(int){install();throw 1.0;}");
        var copy=p.withDiagnostics("probe",Map.of(p,new Diagnostic("probe",Diagnostic.Severity.INFO,"copy")));
        assertNotSame(events(p,"f").getFirst().unexpectedHandlers().getFirst(),events(copy,"f").getFirst().unexpectedHandlers().getFirst());
        assertTrue(ProjectJson.encode(copy).toText("").contains("unexpectedHandlerIds"));
        var editor=new ProjectEditor(p);
        var target=p.flows().stream().filter(f->f.entry().name().equals("install")).findFirst().orElseThrow().entry();
        var names=new ArrayList<NameExpression>();
        target.body().orElseThrow().walk(new NodeVisitor(){@Override public void visit(NameExpression name){if(name.name().equals("h"))names.add(name);}});
        var retarget=new ProjectEditor(p);retarget.replace(names.getFirst(),NameExpression.create().setName("k").build());
        var rebound=DataFlow.analyze(ControlFlow.analyze(retarget.build()));
        assertEquals("k",events(rebound,"f").getFirst().unexpectedHandlers().getFirst().name());
        editor.replace(target.body().orElseThrow(),BlockStatement.create().build());
        var changed=DataFlow.analyze(ControlFlow.analyze(editor.build()));
        assertTrue(events(changed,"f").getFirst().unknownUnexpectedHandler());
    }
}
