package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TemplateInstantiationSemanticsTest {
    static Project resolve(String source) {return NameResolver.resolve(Project.create().addFile(Parser.parse("templates.cpp",source,Language.CPP,"c++03")).build());}
    static <T extends Node> List<T> nodes(Node root,Class<T> type){var result=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))result.add(type.cast(n));}});return result;}
    @Test void nestedRequestsShareInstancesAndRetainCallTargets(){
        var p=resolve("template<class T> T inner(T x){return x;} template<class T> T outer(T x){return inner(x);} int main(){return outer(1)+outer(2);}");
        assertEquals(2,p.templateInstances().size());
        var outer=p.templateInstances().stream().filter(i->i.origin().name().equals("outer")).findFirst().orElseThrow();
        var inner=p.templateInstances().stream().filter(i->i.origin().name().equals("inner")).findFirst().orElseThrow();
        assertEquals(2,outer.requests().size()); assertEquals(1,inner.requests().size());
        assertTrue(nodes(outer.declaration(),CallExpression.class).contains(inner.cause()));
        assertSame(inner.declaration(),inner.cause().target().orElseThrow());
        assertEquals(2,p.edit().build().templateInstances().size());
    }
    @Test void immediateContextFailureDoesNotHideFallbackOrCheckRejectedBody(){
        var p=resolve("struct Tagged { typedef int tag; }; template<class T> typename T::tag choose(const T& x){return x.bad();} int choose(...); int main(){return choose(1);}");
        assertTrue(p.templateInstances().isEmpty());
        var call=nodes(p.files().getFirst(),CallExpression.class).getLast();
        assertTrue(call.target().orElseThrow().templateParameters().isEmpty());
        assertFalse(p.hasErrors());
    }
    @Test void selectedBodyFailureContainsConcreteTypeAndOrderedRequests(){
        var p=resolve("template<class T> int inner(T x){return x.size();}\ntemplate<class T> int outer(T x){return inner(x);}\nint main(){return outer(1);}");
        var error=p.semanticDiagnostics().stream().filter(d->d.code().equals("template-body-member")).findFirst().orElseThrow();
        assertTrue(p.hasErrors());assertTrue(error.message().contains("int"));assertTrue(error.message().contains("size"));
        assertEquals(List.of(2,3),error.relatedRanges().stream().map(r->r.range().start().line()).toList());
    }
    @Test void fullSpecializationAndExplicitInstantiation(){
        var p=resolve("template<class T> T identity(T x){return x;} template<> int identity<int>(int x){return 7;} template int identity<int>(int); int main(){return identity(2);}");
        assertEquals(1,p.templateInstances().size());
        assertEquals(2,p.templateInstances().getFirst().requests().size());
        assertEquals(7,nodes(p.templateInstances().getFirst().declaration(),IntegerLiteral.class).getFirst().bigValue().intValue());
    }
    @Test void valueArgumentsAndArrayBoundDeduction() {
        var p=resolve("template<int N> int value(){return N;} template<int N> int length(const int (&a)[N]){return N;} int main(){int a[3];return value<4>()+length(a);}");
        assertEquals(2,p.templateInstances().size());
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        assertEquals(Set.of(3,4),p.templateInstances().stream().map(i->((IntegerLiteral)i.structuredArguments().getFirst().expression().orElseThrow()).value()).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void templateTemplateArgumentIsDeducedAndSubstituted() {
        var p=resolve("template<class T> struct Box {T value;}; template<template<class> class C,class T> T read(C<T> x){C<T> other; return x.value;} int main(){Box<int> x;return read(x);}");
        assertEquals(1,p.templateInstances().size());
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        assertEquals(TemplateArgument.Kind.TEMPLATE,p.templateInstances().getFirst().structuredArguments().getFirst().kind());
        assertTrue(nodes(p.templateInstances().getFirst().declaration(),MemberExpression.class).getFirst().target().isPresent());
    }
    @Test void qualifiedTemplateKeepsLexicalScopeAndExplicitArgumentsMayBeDeduced() {
        var p=resolve("namespace A {int help(int x){return x;} template<class T> int f(T x){return help(x);} template int f(int);} int main(){return A::f(1);}");
        assertEquals(1,p.templateInstances().size());assertEquals(2,p.templateInstances().getFirst().requests().size());
        assertEquals("help",nodes(p.templateInstances().getFirst().declaration(),CallExpression.class).getFirst().target().orElseThrow().name());
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
    }
    @Test void recursiveInstantiationHasAnExplicitResourceLimit() {
        var p=resolve("template<class T> int recurse(T x){return recurse(&x);} int main(){return recurse(0);}");
        assertTrue(p.hasErrors());
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("template-instantiation-resource-limit")));
        assertTrue(p.templateInstances().size()<=256);
    }
    @Test void classInstanceEditsClearAllReferences() {
        var p=resolve("template<class T> struct Box {T value;}; Box<int> a;");
        var variable=nodes(p.files().getFirst(),VariableDeclaration.class).getLast();
        var editor=new ProjectEditor(p);editor.replace(variable,variable.edit().setName("b").build());
        var edited=editor.build();assertTrue(edited.classTemplateInstances().isEmpty());
        assertEquals(1,NameResolver.resolve(edited).classTemplateInstances().size());
    }
    @Test void templateTemplateParametersRequireCpp03ArityEvenWithDefaults() {
        for(String call:List.of("f<Box>()","g(x)")) {
            var p=resolve("template<class T,class U=int> struct Box {}; template<template<class> class C> int f(){return 1;} template<template<class> class C,class T> int g(C<T> x){return 1;} int main(){Box<int> x;return "+call+";}");
            assertTrue(p.templateInstances().isEmpty());assertTrue(p.hasErrors());
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("no-viable-template")));
        }
    }
    @Test void breadthBudgetStopsBeforePublishingTooManyInstances() {
        for(int count:List.of(256,260)) {
            var source=new StringBuilder("template<int N> int f(){return N;} int main(){");
            for(int i=0;i<count;i++)source.append("f<").append(i).append(">();");
            source.append("return 0;}");
            var p=resolve(source.toString());
            assertEquals(256,p.templateInstances().size());
            assertEquals(count>256,p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("template-instantiation-resource-limit")));
        }
    }
    @Test void partialOrderingIgnoresUnusedDefaultedParameters() {
        var p=resolve("template<class T> int f(T x){return 1;} template<class T> int f(T* x,int n=0){return 2;} int main(){int* p=0;return f(p);}");
        assertEquals(1,p.templateInstances().size());assertEquals(2,p.templateInstances().getFirst().origin().parameters().size());
        assertEquals(1,p.templateInstances().getFirst().cause().defaultArguments().size());
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
    }
    @Test void ordinarySourceDoesNotConsumeInstantiationNodeBudget() {
        var p=resolve("int main(){"+";".repeat(20001)+"return 0;}");
        assertTrue(p.templateInstances().isEmpty());
        assertFalse(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("template-instantiation-resource-limit")));
    }
}
