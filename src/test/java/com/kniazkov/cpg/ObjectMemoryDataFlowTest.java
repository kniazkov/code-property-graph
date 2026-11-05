/* Conservative object memory and contextual summary regressions. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.time.Duration;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectMemoryDataFlowTest {
    @TempDir Path directory;
    private Project analyze(String text) {
        return DataFlow.analyze(NameResolver.resolve(Project.create()
            .addFile(Parser.parse("memory.cpp",text,Language.CPP)).build()));
    }
    private FunctionFlow flow(Project project,String name) {
        return project.flows().stream().filter(f->f.entry().name().equals(name)).findFirst().orElseThrow();
    }
    private <T extends Node> List<T> nodes(Node root,Class<T> type) {
        var result=new ArrayList<T>();
        root.walk(new NodeVisitor(){@Override public void visit(Node node){if(type.isInstance(node))result.add(type.cast(node));}});
        return result;
    }
    private Expression returned(FunctionFlow flow) {
        Expression value=nodes(flow.entry(),ReturnStatement.class).getLast().value().orElseThrow();
        while(value instanceof ConversionExpression conversion)value=conversion.operand();
        return value;
    }
    private Set<Node> definitions(FunctionFlow flow,Node read) {
        var result=new LinkedHashSet<Node>();
        for(var edge:flow.data())if(edge.to()==read&&edge.kind()==FunctionFlow.Dependency.DEFINITION)result.add(edge.from());
        return result;
    }
    @Test void externalPointerResultMayAliasAnActualObject() {
        var f=flow(analyze("int* unknown(int*);int f(){int x=1;int* p=unknown(&x);*p=7;return x;}"),"f");
        var sources=definitions(f,returned(f));
        assertTrue(sources.contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        assertTrue(sources.contains(nodes(f.entry(),VariableDeclaration.class).getFirst()));
    }
    @Test void opaqueReferenceToPointerMayRebindToAnotherActual() {
        var f=flow(analyze("void unknown(int*&,int*);int f(){int x=1;int y=2;int* p=&x;unknown(p,&y);*p=7;return y;}"),"f");
        assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),VariableDeclaration.class).get(1)));
    }
    @Test void opaqueAggregateAndReceiverWritesReachExistingFields() {
        for(String code:List.of(
            "struct S{int x;};void unknown(S&);int f(){S s;s.x=1;unknown(s);return s.x;}",
            "struct S{int x;void unknown();};int f(){S s;s.x=1;s.unknown();return s.x;}")) {
            var f=flow(analyze(code),"f");var sources=definitions(f,returned(f));
            assertTrue(sources.contains(nodes(f.entry(),CallExpression.class).getFirst()));
            assertTrue(sources.contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        }
    }
    @Test void siblingBodyDependenciesKeepCallerOwnedContexts() {
        var p=analyze("void w(int&x){x=7;}int r(int&x){return x;}int h(int&x){w(x);return r(x);}int f(){int x=1;return h(x);}");
        for(var f:p.flows()) {
            var calls=nodes(f.entry(),CallExpression.class);
            for(var edge:f.data())if(edge.callSite()!=null)assertTrue(calls.contains(edge.callSite()));
        }
        var f=flow(p,"f");var outer=nodes(f.entry(),CallExpression.class).getFirst();
        var write=nodes(flow(p,"w").entry(),AssignmentExpression.class).getFirst();
        assertTrue(f.data().stream().anyMatch(e->e.from()==write&&e.callSite()==outer));
    }
    @Test void twoRecursiveInvocationsOfTheSameObjectConverge() {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var p=analyze("void update(int&x,int n){if(n)update(x,n-1);else x=7;}int f(int n){int x=1;update(x,n);update(x,n);return x;}");
            var f=flow(p,"f");
            assertTrue(definitions(f,returned(f)).stream().anyMatch(AssignmentExpression.class::isInstance));
        });
    }
    @Test void throwingOnlyCallDoesNotPublishStaleNormalRead() {
        var f=flow(analyze("void fail(int&x){x=7;throw 1;}int f(){int x=1;fail(x);return x;}"),"f");
        assertTrue(definitions(f,returned(f)).isEmpty());
    }
    @Test void unknownFormalObjectsMayAlias() {
        for(String text:List.of("int f(int*p,int*q){*p=7;return *q;}","int f(int&a,int&b){a=7;return b;}",
            "struct S{int x;};int f(S&a,S&b){a.x=7;return b.x;}","int global;int f(int&a){a=7;return global;}")) {
            var f=flow(analyze(text),"f");
            assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()),text);
        }
    }
    @Test void indirectPointerResultRetainsPointeeAliases() {
        var f=flow(analyze("int f(int*(*fn)(int*)){int x=1;int*p=fn(&x);*p=7;return x;}"),"f");
        assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
    }
    @Test void aggregatePointerMembersMayAliasAtUnknownEntry() {
        for(String source:List.of("struct S{int*p;};int f(S a,S b){*a.p=7;return *b.p;}",
            "struct I{int*p;};struct S{I i;};int f(S a,S b){*a.i.p=7;return *b.i.p;}")) {
            var f=flow(analyze(source),"f");
            assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        }
    }
    @Test void aggregateCopiesPreservePointeesWithoutMergingObjectIdentity() {
        for(String code:List.of(
            "struct S{int*p;};void g(S a){*a.p=7;}int f(){int x=1;S a;a.p=&x;g(a);return x;}",
            "struct S{int*p;};int f(){int x=1;S a;a.p=&x;S b=a;*b.p=7;return x;}")) {
            var p=analyze(code);var f=flow(p,"f");
            assertTrue(definitions(f,returned(f)).stream().anyMatch(n->n instanceof AssignmentExpression a&&a.target() instanceof DereferenceExpression));
        }
        var f=flow(analyze("struct S{int*p;};int f(){int x=1;int y=2;S a;S b;a.p=&x;b.p=&y;*a.p=7;return y;}"),"f");
        assertEquals(Set.of(nodes(f.entry(),VariableDeclaration.class).get(1)),definitions(f,returned(f)));
    }
    @Test void opaqueCallsRetainEscapesAcrossInterveningWrites() {
        for(String code:List.of(
            "void keep(int*);void unknown();int f(){int x=1;keep(&x);x=2;unknown();return x;}",
            "void keep(int&);void unknown();int f(){int x=1;keep(x);x=2;unknown();return x;}",
            "int*p;void unknown();int f(){int x=1;p=&x;unknown();return x;}")) {
            var f=flow(analyze(code),"f");
            assertTrue(definitions(f,returned(f)).stream().anyMatch(n->n instanceof CallExpression call&&call.callee() instanceof NameExpression name&&name.name().equals("unknown")));
        }
    }
    @Test void implicitLifetimesAndOperatorsKeepConservativeGlobalEffects() {
        for(String code:List.of(
            "int g;struct A{A(){g=7;}};int f(){g=1;A a;return g;}",
            "int g;struct A{~A(){g=7;}};int f(){g=1;{A a;}return g;}",
            "int g;struct A{A&operator+=(int){g=7;return*this;}};int f(){g=1;A a;a+=1;return g;}")) {
            var f=flow(analyze(code),"f");var sources=definitions(f,returned(f));
            assertTrue(sources.contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
            assertTrue(sources.stream().anyMatch(n->n instanceof VariableDeclaration v&&v.name().equals("a")||n instanceof CompoundAssignmentExpression));
        }
    }
    @Test void pointerToMemberCallsMayWriteTheirReceiver() {
        for(String call:List.of("(a.*fn)()","(p->*fn)()")) {
            var f=flow(analyze("struct A{int x;void g(){x=7;}};int f(void(A::*fn)()){A a;A*p=&a;a.x=1;"+call+";return a.x;}"),"f");
            assertTrue(definitions(f,returned(f)).stream().anyMatch(CallExpression.class::isInstance));
            assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        }
    }
    @Test void exactScalarOverwriteKillsEarlierUnknownAliasEffect() {
        var p=analyze("int* unknown(int*);void update(int&x){x=9;}int f(){int x=1;int*p=unknown(&x);*p=7;update(x);return x;}");
        var f=flow(p,"f");
        assertEquals(Set.of(nodes(flow(p,"update").entry(),AssignmentExpression.class).getFirst()),definitions(f,returned(f)));
    }
    @Test void referenceFieldsMayWriteTheirConstructorActualsAndEntryAliases() {
        var f=flow(analyze("struct R{int&r;R(int&x):r(x){}};int f(){int x=1;R a(x);a.r=7;return x;}"),"f");
        assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),VariableDeclaration.class).getFirst()));
        for(String source:List.of("struct R{int&r;};int f(R a,R b){a.r=7;return b.r;}",
            "struct R{int&r;};struct S{R r;};int f(S a,S b){a.r.r=7;return b.r.r;}")) {
            f=flow(analyze(source),"f");
            assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        }
    }
    @Test void dataMemberPointerAndSequencedLvaluesKeepWrites() {
        for(String target:List.of("a.*field","p->*field")) {
            var f=flow(analyze("struct A{int x;};int f(int A::*field){A a;A*p=&a;a.x=1;("+target+")=7;return a.x;}"),"f");
            assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getLast()));
            assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        }
        var f=flow(analyze("int f(){int x=1;(0,x)=7;return x;}"),"f");
        assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).getFirst()),definitions(f,returned(f)));
    }
    @Test void byteViewWritesDoNotKillTheWholeObject() {
        for(String access:List.of("reinterpret_cast<char&>(x)=0;","char*p=reinterpret_cast<char*>(&x);*p=0;")) {
            var f=flow(analyze("int f(){int x=257;"+access+"return x;}"),"f");
            assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),VariableDeclaration.class).getFirst()));
            assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        }
    }
    @Test void hiddenFieldsUseDeclarationIdentityRatherThanSpelling() {
        var f=flow(analyze("struct B{int x;};struct D:B{int x;};int f(){D d;B&b=d;d.x=1;b.x=2;return d.x;}"),"f");
        assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).getFirst()),definitions(f,returned(f)));
        f=flow(analyze("struct B{int x;};struct D:B{int x;};int f(){D d;B&b=d;d.x=1;b.x=2;return b.x;}"),"f");
        assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).getLast()),definitions(f,returned(f)));
    }
    @Test void repeatedBaseSubobjectsDoNotProveAnExactKill() {
        var f=flow(analyze("struct B{int x;};struct L:B{};struct R:B{};struct D:L,R{};int f(){D d;L&l=d;R&r=d;l.x=1;r.x=2;return l.x;}"),"f");
        assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
        assertTrue(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getLast()));
    }
    @Test void normalizedPrintReadsKeepWritesLiveAtGreenAndFoldedStages() throws Exception {
        Path source=directory.resolve("print.cpp");
        Files.writeString(source,"#include <iostream>\nint main(){int result=1;result=result+2;std::cout<<result<<std::endl;int x=1;std::cout<<(x++,x)<<std::endl;return x;}");
        for(Cpg.Stage stage:List.of(Cpg.Stage.GREEN,Cpg.Stage.FOLDED)) {
            Project project=Cpg.read(directory,new Cpg.Options(Language.CPP,stage));
            assertFalse(project.hasErrors(),project.semanticDiagnostics().toString());
            var f=flow(project,"main");
            assertEquals(2,nodes(f.entry(),PrintStatement.class).size());
            assertTrue(nodes(f.entry(),Node.class).stream().flatMap(n->n.diagnostics().stream()).noneMatch(d->d.code().equals(UnreadWrites.CODE)));
            for(var increment:nodes(f.entry(),PostfixIncrementExpression.class))
                assertFalse(f.data().stream().anyMatch(e->e.from()==increment&&nodes(increment.operand(),NameExpression.class).contains(e.to())&&e.kind()==FunctionFlow.Dependency.DEFINITION));
        }
    }
    @Test void memberInitializerArgumentsKeepReadsAndCallEffects() {
        var p=analyze("struct A{int x;A(int value):x(value){}};");
        var constructor=flow(p,"A");
        var argument=nodes(constructor.entry().initializers().getFirst(),NameExpression.class).getFirst();
        assertTrue(definitions(constructor,argument).contains(constructor.entry().parameters().getFirst()));
        p=analyze("int bump(int&v){v=7;return v;}struct A{int x;A(int value):x(bump(value)){int observed=value;}};");
        constructor=flow(p,"A");
        var observed=nodes(constructor.entry().body().orElseThrow(),NameExpression.class).getFirst();
        var assignment=nodes(flow(p,"bump").entry(),AssignmentExpression.class).getFirst();
        assertEquals(Set.of(assignment),definitions(constructor,observed));
        p=analyze("struct A{int x;A(int value):x((value++,value)){int observed=value;}};");
        constructor=flow(p,"A");
        for(var increment:nodes(constructor.entry(),PostfixIncrementExpression.class))
            assertFalse(constructor.data().stream().anyMatch(e->e.from()==increment&&nodes(increment.operand(),NameExpression.class).contains(e.to())&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void separatelyExpandedMemberArgumentsAreNotReplayed() {
        var p=NameResolver.resolve(Project.create().addFile(Parser.parse("expanded.cpp", "struct B{B(int,int);};struct A:B{A(int x,int y):B(x++,y++){}};",Language.CPP)).build());
        p=ControlFlow.analyze(p);
        var constructor=flow(p,"A");var initializer=constructor.entry().initializers().getFirst();
        var increments=nodes(initializer,PostfixIncrementExpression.class);
        var control=new ArrayList<FunctionFlow.ControlEdge>();
        for(var edge:constructor.control()) {
            if(edge.to()==initializer) {
                control.add(new FunctionFlow.ControlEdge(edge.from(),increments.getFirst(),edge.kind()));
                control.add(new FunctionFlow.ControlEdge(increments.getFirst(),increments.getLast(),FunctionFlow.Branch.NEXT));
                control.add(new FunctionFlow.ControlEdge(increments.getLast(),initializer,FunctionFlow.Branch.NEXT));
            } else control.add(edge);
        }
        var replacement=new FunctionFlow(constructor.entry(),constructor.exit(),control,List.of());
        var original=constructor;
        p=DataFlow.analyze(p.withFlows(p.flows().stream().map(f->f==original?replacement:f).toList()));
        constructor=flow(p,"A");
        for(var increment:increments)
            assertFalse(constructor.data().stream().anyMatch(e->e.from()==increment&&nodes(increment.operand(),NameExpression.class).contains(e.to())&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void referencedGlobalInitializerIsARealDefinition() {
        var p=analyze("int global=1;int f(){return global;}");var f=flow(p,"f");
        assertTrue(definitions(f,returned(f)).stream().anyMatch(n->n instanceof VariableDeclaration v&&v.name().equals("global")));
    }
}
