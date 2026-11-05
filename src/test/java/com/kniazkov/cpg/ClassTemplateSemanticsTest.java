/* Structural class-template specialization regression tests. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassTemplateSemanticsTest {
    static Project resolve(String source) { return NameResolver.resolve(Project.create().addFile(Parser.parse("classes.cpp", source, Language.CPP, "c++03")).build()); }
    static <T extends Node> List<T> nodes(Node node, Class<T> type) { var result = new ArrayList<T>(); node.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); }}); return result; }
    @Test void selectsFullAndOrdersPointerCvPatterns() {
        var p = resolve("template<class T> struct Box { T value; }; template<class T> struct Box<T*> { T* pointer; }; template<class T> struct Box<const T*> { const T* const_pointer; }; template<> struct Box<int> { int integer; }; Box<double> a; Box<double*> b; Box<const double*> c; Box<int> d;");
        assertEquals(4, p.classTemplateInstances().size());
        assertEquals(List.of("value", "pointer", "const_pointer", "integer"), p.classTemplateInstances().stream().map(i -> ((VariableDeclaration)i.declaration().members().getFirst()).name()).toList());
        assertEquals(List.of("double", "pointer(double)", "pointer(const(double))", "int"), p.classTemplateInstances().stream().map(i -> ((VariableDeclaration)i.declaration().members().getFirst()).type().orElseThrow().canonicalIdentity()).toList());
        for (var instance : p.classTemplateInstances()) assertTrue(instance.declaration().templateParameters().isEmpty());
    }
    @Test void incomparablePartialSpecializationsAreAmbiguous() {
        var p = resolve("template<class A,class B> struct Pair {}; template<class A,class B> struct Pair<A*,B> {}; template<class A,class B> struct Pair<A,B*> {}; Pair<int*,int*> pair;");
        assertTrue(p.classTemplateInstances().isEmpty());
        assertTrue(nodes(p, NamedType.class).stream().flatMap(n -> n.diagnostics().stream()).anyMatch(d -> d.code().equals("ambiguous-class-specialization")));
    }
    @Test void repeatedParametersConstrainDeduction() {
        var p = resolve("template<class A,class B> struct Pair { int general; }; template<class T> struct Pair<T,T> { T repeated; }; Pair<int,int> a; Pair<int,double> b;");
        assertEquals(List.of("repeated", "general"), p.classTemplateInstances().stream().map(i -> ((VariableDeclaration)i.declaration().members().getFirst()).name()).toList());
    }
    @Test void defaultsValuesAndExplicitRequestsShareIdentityAndPreserveOrigin() {
        var p = resolve("template<class T,int N=3> struct Array { T data[N]; }; Array<int> a; Array<int,1+2> b; template struct Array<int,3>;");
        assertEquals(1, p.classTemplateInstances().size());
        var i = p.classTemplateInstances().getFirst();
        assertEquals(3, i.requests().size());
        var field = (VariableDeclaration)i.declaration().members().getFirst();
        assertEquals("array(3,int)", field.type().orElseThrow().canonicalIdentity());
        assertEquals(2, i.origin().templateParameters().size());
        assertFalse(nodes(i.origin(), NameExpression.class).isEmpty());
        var copy = p.edit().build();
        assertEquals(1, copy.classTemplateInstances().size());
        assertTrue(copy.classTemplateInstances().getFirst().requests().stream().allMatch(r -> nodes(copy, Node.class).contains(r)));
        assertEquals(p.files().getFirst().source(Language.CPP), copy.files().getFirst().source(Language.CPP));
    }
    @Test void namespaceFamiliesCannotCrossBind() {
        var p = resolve("namespace A { template<class T> struct Box { int a; }; } namespace B { template<class T> struct Box { int b; }; } A::Box<int> a; B::Box<int> b;");
        assertEquals(2, p.classTemplateInstances().size());
        assertNotEquals(p.classTemplateInstances().get(0).identity(), p.classTemplateInstances().get(1).identity());
        assertEquals(List.of("a", "b"), p.classTemplateInstances().stream().map(i -> ((VariableDeclaration)i.declaration().members().getFirst()).name()).toList());
    }
    @Test void staticMemberCallUsesSelectedConcreteClass() {
        var p = resolve("template<class T> struct Box { static int value() { return 1; } }; template<> struct Box<int> { static int value() { return 2; } }; int f() { return Box<int>::value(); }");
        assertEquals(1, p.classTemplateInstances().size());
        var call = nodes(p.files().getFirst(), CallExpression.class).getFirst();
        assertSame(p.classTemplateInstances().getFirst().declaration().members().getFirst(), call.target().orElseThrow());
    }
    @Test void substitutesDependentDefaultsAndTemplateTemplateArguments() {
        var p = resolve("template<class T> struct Wrap { T value; }; template<class T,class U=T*> struct Pair { U value; }; template<template<class> class C,class T> struct Holder { C<T> value; }; Pair<int> pair; Holder<Wrap,int> holder;");
        assertEquals(3, p.classTemplateInstances().size());
        var pair = p.classTemplateInstances().stream().filter(i -> i.origin().name().equals("Pair")).findFirst().orElseThrow();
        assertEquals("pointer(int)", ((VariableDeclaration)pair.declaration().members().getFirst()).type().orElseThrow().canonicalIdentity());
        var wrap = p.classTemplateInstances().stream().filter(i -> i.origin().name().equals("Wrap")).findFirst().orElseThrow();
        assertEquals("int", ((VariableDeclaration)wrap.declaration().members().getFirst()).type().orElseThrow().canonicalIdentity());
    }
    @Test void editsDiscardOwnedClassInstances() {
        var p = resolve("template<class T> struct Box { T value; }; Box<int> box;");
        var edited = p.edit(); ((SourceFile.Builder)edited.files().getFirst()).setItems(List.of());
        assertTrue(edited.build().classTemplateInstances().isEmpty());
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void concreteMethodsHaveSourceContextAtEveryPipelineStage() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("class.cpp"), "template<class T> struct Box { unsigned long size() { return sizeof(T); } }; int main() { Box<int> b; return b.size(); }");
        for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
            var p = Cpg.read(directory, new Cpg.Options(Language.CPP, stage, "c++03"));
            assertEquals(1, p.classTemplateInstances().size(), stage.toString());
            var instance = p.classTemplateInstances().getFirst();
            var method = (CallableDeclaration)instance.declaration().members().getFirst();
            assertTrue(p.flows().stream().anyMatch(f -> f.entry() == method), stage.toString());
            assertEquals(1, nodes(instance.origin(), SizeofExpression.class).size());
            if (stage == Cpg.Stage.FOLDED) assertTrue(nodes(method, SizeofExpression.class).isEmpty());
        }
    }
    @Test void dependentMemberTypesSubstituteOrDiagnoseWithoutInstantiatingUnusedBodies() {
        var valid = resolve("struct Has { typedef int type; }; template<class T> struct Box { typename T::type value; }; Box<Has> b;");
        assertEquals("int", ((VariableDeclaration)valid.classTemplateInstances().getFirst().declaration().members().getFirst()).type().orElseThrow().canonicalIdentity());
        var invalid = resolve("template<class T> struct Box { typename T::type value; }; Box<int> b;");
        assertTrue(nodes(invalid, Node.class).stream().flatMap(n -> n.diagnostics().stream()).anyMatch(d -> d.code().equals("invalid-class-template-substitution")));
        var unused = resolve("template<class T> struct Box { int good(){return 1;} int bad(){return T::missing;} }; Box<int> x;");
        assertEquals(1, unused.classTemplateInstances().size());
        assertFalse(nodes(unused, Node.class).stream().flatMap(n -> n.diagnostics().stream()).anyMatch(d -> d.code().equals("invalid-class-template-substitution")));
    }
    @Test void nonTypePartialSpecializationsAndDependentDefaultsAreStructural() {
        var p = resolve("template<class T,int N,int M=N+1> struct Seq { T general[M]; }; template<class T,int M> struct Seq<T,0,M> { T zero[M]; }; Seq<int,2> a; Seq<int,0> b;");
        assertEquals(2, p.classTemplateInstances().size());
        assertEquals(List.of("general", "zero"), p.classTemplateInstances().stream().map(i -> ((VariableDeclaration)i.declaration().members().getFirst()).name()).toList());
        assertEquals(List.of("array(3,int)", "array(1,int)"), p.classTemplateInstances().stream().map(i -> ((VariableDeclaration)i.declaration().members().getFirst()).type().orElseThrow().canonicalIdentity()).toList());
    }
    @Test void demandedAndExplicitClassMemberBodiesReportSubstitutionErrors() {
        for (String use : List.of("int main(){Box<int> b;return b.bad(1);}", "template struct Box<int>;")) {
            var p = resolve("template<class T> struct Box { int bad(T x){return x.size();} }; " + use);
            var errors = nodes(p, Node.class).stream().flatMap(n -> n.diagnostics().stream()).filter(d -> d.code().equals("class-template-body-member")).toList();
            assertEquals(1, errors.size());
            assertTrue(errors.getFirst().message().contains("int"));
            assertFalse(errors.getFirst().relatedRanges().isEmpty());
        }
    }
    @Test void pointerUseDoesNotDemandACompleteClassDefinition() {
        var p = resolve("template<class T> struct Box { typename T::missing value; }; Box<int>* pointer;");
        assertFalse(p.hasErrors());
    }
    @Test void compatibleTranslationUnitsShareClassIdentityAndRequests() {
        String declaration = "template<class T> struct Box { T value; }; ";
        var p = NameResolver.resolve(Project.create().addFile(Parser.parse("a.cpp", declaration + "Box<int> a;", Language.CPP, "c++03"))
                .addFile(Parser.parse("b.cpp", declaration + "Box<int> b;", Language.CPP, "c++03")).build());
        assertEquals(1, p.classTemplateInstances().size());
        assertEquals(2, p.classTemplateInstances().getFirst().requests().size());
    }
    @Test void namespaceAliasAndUsingResolveTheActualTemplateFamily() {
        var p = resolve("namespace A { template<class T> struct Box { T value; }; } namespace Alias=A; using A::Box; Alias::Box<int> a; Box<int> b;");
        assertEquals(1, p.classTemplateInstances().size());
        assertEquals(2, p.classTemplateInstances().getFirst().requests().size());
    }
    @Test void normalizedDefaultArgumentsGiveTheSameTypeIdentity() {
        var p = resolve("template<class T=int> struct Box { T value; }; Box<> a; Box<int> b;");
        var vars = nodes(p.files().getFirst(), VariableDeclaration.class).stream().filter(v -> v.name().equals("a") || v.name().equals("b")).toList();
        assertEquals(vars.get(0).type().orElseThrow().canonicalIdentity(), vars.get(1).type().orElseThrow().canonicalIdentity());
    }
    @Test void equivalentAndUndeduciblePartialsAreRejectedEvenWithoutUse() {
        for (String partial : List.of("template<class T> struct A<T> {};", "template<class T> struct A<int> {};")) {
            var p = resolve("template<class T> struct A {}; " + partial);
            assertTrue(nodes(p, Node.class).stream().flatMap(n -> n.diagnostics().stream()).anyMatch(d -> d.code().equals("invalid-class-partial-specialization")));
        }
    }
}
