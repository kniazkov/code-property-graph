/* Conservative object memory and contextual summary regressions. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectMemoryDataFlowTest {
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
    @Test void referencedGlobalInitializerIsARealDefinition() {
        var p=analyze("int global=1;int f(){return global;}");var f=flow(p,"f");
        assertTrue(definitions(f,returned(f)).stream().anyMatch(n->n instanceof VariableDeclaration v&&v.name().equals("global")));
    }
}
