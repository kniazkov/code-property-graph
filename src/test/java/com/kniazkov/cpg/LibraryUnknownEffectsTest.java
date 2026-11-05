/* Unknown library behavior preserves possible object writes without inventing purity. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class LibraryUnknownEffectsTest {
    @TempDir Path directory;
    private Project read(String source)throws Exception {
        Files.writeString(directory.resolve("main.cpp"),source);
        return Cpg.read(directory,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03"));
    }
    @Test void opaqueReferenceCallMayWriteArgumentsAndKeepIncomingDefinitions()throws Exception {
        var p=read("#include <algorithm>\nint f(){int x=1;int y=2;std::swap(x,y);return x;}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var call=nodes(f,CallExpression.class).getFirst();assertTrue(call.target().isPresent());
        var x=nodes(f,VariableDeclaration.class).stream().filter(v->v.name().equals("x")).findFirst().orElseThrow();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();
        assertTrue(flow.data().stream().anyMatch(e->e.from()==call&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
        assertTrue(flow.data().stream().anyMatch(e->e.from()==x&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void ordinaryValueArgumentIsNotAReferenceToTheLocal()throws Exception {
        var p=read("#include <cstdlib>\nint f(){int x=1;std::abs(x);return x;}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var call=nodes(f,CallExpression.class).getFirst();assertTrue(call.target().isPresent());
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();
        assertFalse(flow.data().stream().anyMatch(e->e.from()==call&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void opaqueCodeMayReachAddressExposedOrGlobalStorage()throws Exception {
        var p=read("#include <cstdlib>\nint global; int f(){int x=1;int* p=&x;global=2;std::abs(1);return x+global;}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var call=nodes(f,CallExpression.class).getFirst();
        var ret=nodes(f,ReturnStatement.class).getFirst();var uses=nodes(ret,NameExpression.class);
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();
        for(var use:uses)assertTrue(flow.data().stream().anyMatch(e->e.from()==call&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION),use.name());
    }
    @Test void unevaluatedAddressDoesNotExposeStorage()throws Exception {
        var p=read("#include <cstdlib>\nint f(){int x=1;sizeof(&x);std::abs(1);return x;}");
        var f=function(p,"f");var call=nodes(f,CallExpression.class).getFirst();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();
        assertFalse(flow.data().stream().anyMatch(e->e.from()==call&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void opaqueLibraryOperatorMayChangeItsReceiverReferenceArgumentAndAccessibleData()throws Exception {
        var p=read("#include <iostream>\nint global; int f(){int x=1;int y=2;int* p=&y;global=3;std::cin >> x;return x+y+global;}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var operation=nodes(f,ShiftRightExpression.class).getFirst();assertTrue(operation.operatorTarget().isPresent());
        var uses=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class);
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();
        for(var use:uses)assertTrue(flow.data().stream().anyMatch(e->e.from()==operation&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION),use.name());
    }
    @Test void opaqueLibraryConstructorMayChangeAccessibleStorageBeforeReturn()throws Exception {
        assertLifetimeEffect("#include <vector>\nint global;int f(){global=1;std::vector<int> v;return global;}","global");
    }
    @Test void opaqueLibraryDestructorMayChangeStorageAfterAnInterveningAssignment()throws Exception {
        assertLifetimeEffect("#include <vector>\nint global;int f(){global=1;{std::vector<int> v;global=2;}return global;}","global");
        assertLifetimeEffect("#include <vector>\nint f(){int x=1;int* p=&x;{std::vector<int> v;x=2;}return x;}","x");
    }
    @Test void librarySubobjectConstructionMayChangeAccessibleStorage()throws Exception {
        for(String constructor:List.of("H(){int x=global;}","H():v(3){int x=global;}")) {
            var p=read("#include <vector>\nint global;struct H {std::vector<int> v;"+constructor+"};");
            assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
            var flow=p.flows().stream().filter(f->f.entry().name().equals("H")).findFirst().orElseThrow();assertTrue(flow.complete());
            var use=nodes(flow.entry(),NameExpression.class).stream().filter(n->n.name().equals("global")).findFirst().orElseThrow();
            assertTrue(flow.data().stream().anyMatch(e->e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION
                && (e.from() instanceof MemberInitializer || e.from() instanceof VariableDeclaration v&&v.name().equals("v"))),constructor);
        }
    }
    @Test void allocatorReturnedPointerKeepsPossiblePointeeWrites()throws Exception {
        var p=read("#include <memory>\nint f(){int x=1;std::allocator<int> a;int* p=a.address(x);*p=3;return x;}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var assignment=nodes(f,AssignmentExpression.class).getFirst();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==assignment&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void chainedStringStreamExtractionRetainsBothOutputAndReceiverEffects()throws Exception {
        var p=read("#include <sstream>\nint f(){std::stringstream s;int x=1;int y=2;s >> x >> y;return x+y+s.good();}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var operations=nodes(f,ShiftRightExpression.class);
        var outer=operations.stream().filter(o->o.left() instanceof ShiftRightExpression).findFirst().orElseThrow();
        assertTrue(outer.operatorTarget().isPresent());
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        for(var use:nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class))
            assertTrue(flow.data().stream().anyMatch(e->e.from()==outer&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION),use.name());
        assertTrue(nodes(p,PrintStatement.class).isEmpty());
    }
    @Test void streamStateMutatorIsAnOpaqueReceiverWrite()throws Exception {
        var p=read("#include <sstream>\nint f(){std::stringstream s;s.clear();return s.good();}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var clear=nodes(f,CallExpression.class).stream()
                .filter(c->c.target().map(t->t.name().equals("clear")).orElse(false)).findFirst().orElseThrow();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==clear&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void cCompatibilityPointerOutputMayWriteExposedLocal()throws Exception {
        var p=read("#include <stdio.h>\nint f(){int x=1;scanf(\"%d\",&x);return x;}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var call=nodes(f,CallExpression.class).getFirst();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==call&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void directDefaultStringMaterializationPreservesStreamEffects()throws Exception {
        var p=read("#include <sstream>\nint f(){std::stringstream s;s << 7;std::string text=s.str();return text.size();}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var flow=p.flows().stream().filter(value->value.entry().name().equals("f")).findFirst().orElseThrow();
        assertTrue(flow.complete());assertFalse(flow.data().isEmpty());
        assertTrue(nodes(p,PrintStatement.class).isEmpty());
    }
    @Test void savedInputIteratorRetainsItsStreamStateAlias()throws Exception {
        var p=read("#include <sstream>\n#include <iterator>\nint f(){std::istringstream s;std::istream_iterator<int> i(s);++i;return s.fail();}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var advance=nodes(f,PrefixIncrementExpression.class).getFirst();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==advance&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void savedOutputIteratorRetainsItsStreamStateAlias()throws Exception {
        var p=read("#include <sstream>\n#include <iterator>\nint f(){std::ostringstream s;std::ostream_iterator<int> i(s);*i++=3;return s.good();}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var write=nodes(f,AssignmentExpression.class).getFirst();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==write&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void copiedInputIteratorArgumentStillExposesTheStream()throws Exception {
        var p=read("#include <sstream>\n#include <iterator>\nint f(){std::istringstream s;std::istream_iterator<int> i(s),end;std::distance(i,end);return s.fail();}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var call=nodes(f,CallExpression.class).stream()
                .filter(c->c.target().map(t->t.name().equals("distance")).orElse(false)).findFirst().orElseThrow();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==call&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void inputIteratorAssignmentRetainsNewStreamAlias()throws Exception {
        var p=read("#include <sstream>\n#include <iterator>\nint f(){std::istringstream a,b;std::istream_iterator<int> i(a),j(b);i=j;++i;return b.fail();}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var f=function(p,"f");var advance=nodes(f,PrefixIncrementExpression.class).getFirst();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==advance&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void unmodeledRecordReturnLifetimeIsExplicitlyIncomplete()throws Exception {
        var p=read("#include <sstream>\nint f(){std::stringstream s;s.str();return 1;}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var flow=p.flows().stream().filter(value->value.entry().name().equals("f")).findFirst().orElseThrow();
        assertFalse(flow.complete(),"Discarded string temporaries must not acquire a guessed cleanup model");
        assertTrue(flow.data().isEmpty());
    }
    private void assertLifetimeEffect(String source,String name)throws Exception {
        var p=read(source);assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());var f=function(p,"f");
        var v=nodes(f,VariableDeclaration.class).stream().filter(d->d.name().equals("v")).findFirst().orElseThrow();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).stream().filter(n->n.name().equals(name)).findFirst().orElseThrow();
        var flow=p.flows().stream().filter(value->value.entry()==f).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==v&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION),"Opaque lifetime may write "+name);
    }
    private static CallableDeclaration function(Project p,String name){return nodes(p,CallableDeclaration.class).stream().filter(f->f.name().equals(name)).findFirst().orElseThrow();}
    private static <T extends Node>List<T> nodes(Node root,Class<T> type){var result=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))result.add(type.cast(n));}});return result;}
}
