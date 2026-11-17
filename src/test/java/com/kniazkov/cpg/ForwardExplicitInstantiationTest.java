package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForwardExplicitInstantiationTest {
    private static Project resolve(String source){return TemplateInstantiationSemanticsTest.resolve(source);}
    private static void healthy(Project p){assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());}
    private static void check(String source,int requests){
        var original=resolve(source);healthy(original);
        for(var p:List.of(original,NameResolver.resolve(original),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))) {
            healthy(p);assertEquals(1,p.templateInstances().size());
            var instance=p.templateInstances().getFirst();assertTrue(instance.declaration().body().isPresent());assertEquals(requests,instance.requests().size());
            assertTrue(instance.declaration().range().isPresent());
            assertFalse(ProjectJson.encode(p).toText("  ").isEmpty());
            var reparsed=resolve(Cpg.sources(p,Language.CPP).get("templates.cpp"));healthy(reparsed);assertEquals(1,reparsed.templateInstances().size());
        }
    }
    @Test void forwardAndSingleDefinitionWithoutCall(){
        for(String prefix:List.of("","template<class T> void f();\n","template<class T> void f();template<class U> void f();\n"))
            check(prefix+"#include <typeinfo>\ntemplate<class V> void f(){typeid(V);} template void f<int>();",1);
    }
    @Test void renamedParametersNamespacesAndCalls(){
        check("namespace Q{template<class T>void f();template<class U>void f(){}}template void Q::f<int>();",1);
        check("namespace N{template<class T>T f(T);template<class U>U f(U x){return x;}template int f<int>(int);}int main(){return N::f(0);}",2);
        check("template<class T>T f(T);template<class U>U f(U x){return x;}template<class U>U f(U* x){return *x;}template int f<int>(int);",1);
    }
    @Test void missingOrForeignDefinitionDoesNotSatisfyRequest(){
        for(String source:List.of("template<class T>void f();template void f<int>();","namespace A{template<class T>void f();template void f<int>();}namespace B{template<class T>void f(){}}","template<class T>void f();template<class T>void f(T){}template void f<int>();")) {
            var p=resolve(source);assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("invalid-explicit-instantiation")),p.semanticDiagnostics().toString());
        }
    }
    @Test void editingDefinitionRebuildsInstanceBody(){
        var p=resolve("template<class T>int f();template<class U>int f(){return 7;}template int f<int>();");healthy(p);
        var definition=TemplateInstantiationSemanticsTest.nodes(p.files().getFirst(),CallableDeclaration.class).stream().filter(f->f.body().isPresent()).findFirst().orElseThrow();
        var literal=TemplateInstantiationSemanticsTest.nodes(definition,IntegerLiteral.class).getFirst();
        var editor=new ProjectEditor(p);editor.replace(literal,IntegerLiteral.create().setValue(9).build());
        var changed=NameResolver.resolve(editor.build());healthy(changed);assertEquals(1,changed.templateInstances().size());
        assertEquals(9,TemplateInstantiationSemanticsTest.nodes(changed.templateInstances().getFirst().declaration(),IntegerLiteral.class).getFirst().value());
        assertEquals(7,TemplateInstantiationSemanticsTest.nodes(p.templateInstances().getFirst().declaration(),IntegerLiteral.class).getFirst().value());
    }
    @Test void bodyTypeinfoContextIsNotHidden(){
        for(String suffix:List.of("","\n#include <typeinfo>\n")) {
            var p=resolve("template<class T>void f();template<class U>void f(){typeid(U);}template void f<int>();"+suffix);
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("missing-typeinfo")),p.semanticDiagnostics().toString());
        }
    }
}
