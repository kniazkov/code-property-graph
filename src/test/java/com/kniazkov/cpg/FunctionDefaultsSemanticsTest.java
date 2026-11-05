/* C++03 source-point defaults and function call semantics. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FunctionDefaultsSemanticsTest {
    @TempDir Path temp;
    private Project read(String text) throws Exception {
        Files.writeString(temp.resolve("defaults.cpp"), text);
        return Cpg.read(temp, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    private static List<CallExpression> calls(Project project, String name) {
        return nodes(project, CallExpression.class).stream().filter(call -> call.callee() instanceof NameExpression n && n.name().equals(name)).toList();
    }
    private static boolean diagnostic(Node node, String code) { return node.diagnostics().stream().anyMatch(d -> d.code().equals(code)); }
    @Test void omittedArgumentsShareOneDeclarationExpressionAcrossSnapshots() throws Exception {
        var project = read("int next(); void log(int level=next()); int main(){ log(); log(2); log(); } void log(int level){}");
        for (var snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project))) {
            var calls = calls(snapshot, "log");
            assertEquals(List.of(0, 1, 0), calls.stream().map(call -> call.arguments().size()).toList());
            var first = calls.get(0).defaultArguments().getFirst();
            assertSame(first.expression(), calls.get(2).defaultArguments().getFirst().expression());
            assertSame(first.parameter(), first.declaration().parameters().getFirst());
            assertNotSame(first.declaration(), calls.get(0).target().orElseThrow());
            assertFalse(calls.get(0).children().contains(first.expression()));
            assertTrue(calls.get(1).defaultArguments().isEmpty());
            assertEquals(1, calls(snapshot, "next").size());
            assertEquals("log()", calls.get(0).source(Language.CPP));
            Set<Node> owned = new HashSet<>(nodes(snapshot, Node.class));
            for (Node node : owned) assertTrue(owned.containsAll(node.references()));
        }
    }
    @Test void defaultsAccumulateOnlyBeforeCallInSameScope() throws Exception {
        var project = read("void sum(int a,int b=2); void sum(int a=1,int b); int main(){sum(); {void sum(int a,int b); sum();} } void late(int); void f(){late();} void late(int a=1);");
        var calls = calls(project,"sum");
        assertEquals(2, calls.getFirst().defaultArguments().size());
        assertEquals(0, calls.getLast().defaultArguments().size());
        assertTrue(diagnostic(calls.getLast(),"missing-call-argument"));
        assertTrue(diagnostic(calls(project,"late").getFirst(),"missing-call-argument"));
        assertFalse(nodes(project,ParameterDeclaration.class).stream().anyMatch(p -> diagnostic(p,"missing-default-argument")));
    }
    @Test void declarationPointNamesAndInvalidDefaultsAreDiagnosed() throws Exception {
        var project = read("int value=1; void f(int n=value); void f(int n=2); void bad(int a=1,int b); int main(){int value=9; f(); f(1,2);}");
        var provider = calls(project,"f").getFirst().defaultArguments().getFirst();
        var names = nodes(provider.expression(),NameExpression.class);
        assertSame(nodes(project,VariableDeclaration.class).getFirst(),names.getFirst().target().orElseThrow());
        assertTrue(nodes(project,ParameterDeclaration.class).stream().anyMatch(p -> diagnostic(p,"duplicate-default-argument")));
        assertTrue(nodes(project,ParameterDeclaration.class).stream().anyMatch(p -> diagnostic(p,"missing-default-argument")));
        assertTrue(diagnostic(calls(project,"f").getLast(),"excess-call-arguments"));
    }
    @Test void indirectCallsHaveTypesConversionsAndNoInventedTargetsOrDefaults() throws Exception {
        var project = read("int f(long a=1); int main(){int (*p)(long)=f; p(); p(2); (*p)(3); int (*v)(int,...); v(1,2.0f,'x');}");
        var calls = nodes(project,CallExpression.class);
        assertTrue(calls.stream().allMatch(call -> call.target().isEmpty() && call.defaultArguments().isEmpty()));
        assertTrue(diagnostic(calls.get(0),"missing-call-argument"));
        assertEquals("int", calls.get(1).semanticInfo().orElseThrow().resultType().orElseThrow());
        assertEquals("long", assertInstanceOf(ConversionExpression.class,calls.get(1).arguments().getFirst()).targetType().canonicalIdentity());
        assertEquals("long", assertInstanceOf(ConversionExpression.class,calls.get(2).arguments().getFirst()).targetType().canonicalIdentity());
        assertEquals("double", assertInstanceOf(ConversionExpression.class,calls.get(3).arguments().get(1)).targetType().canonicalIdentity());
        assertEquals("int", assertInstanceOf(ConversionExpression.class,calls.get(3).arguments().get(2)).targetType().canonicalIdentity());
    }
    @Test void defaultEffectsOccurAtCallNotLocalPrototype() throws Exception {
        var project = DataFlow.analyze(read("int n=1; void f(int); int main(){void f(int a=++n); f(); return n;}"));
        var flow = project.flows().stream().filter(f -> f.entry().name().equals("main")).findFirst().orElseThrow();
        var increment = nodes(project,PrefixIncrementExpression.class).getFirst();
        var call = calls(project,"f").getFirst();
        assertTrue(flow.data().stream().anyMatch(edge -> edge.from()==call.defaultArguments().getFirst().expression() && edge.to()==call));
        assertTrue(flow.data().stream().anyMatch(edge -> edge.from()==increment && edge.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void laterTranslationUnitDefaultsAndSelfDefaultsAreNotVisible() throws Exception {
        Files.writeString(temp.resolve("other.cpp"), "void f(int n=1); void f(int n){}");
        var project = read("void f(int); int main(){f();} void recursive(int n=recursive());");
        assertTrue(calls(project,"f").getFirst().defaultArguments().isEmpty());
        assertTrue(diagnostic(calls(project,"f").getFirst(),"missing-call-argument"));
        var recursive = calls(project,"recursive").getFirst();
        assertTrue(recursive.defaultArguments().isEmpty());
        assertTrue(recursive.target().isEmpty());
        assertTrue(assertInstanceOf(NameExpression.class,recursive.callee()).target().isEmpty());
    }
    @Test void functionNameIsNotVisibleUntilItsCompleteDeclarator() throws Exception {
        var project = read("int f(int x=f(1));");
        var call = calls(project,"f").getFirst();
        assertTrue(call.target().isEmpty());
        assertTrue(assertInstanceOf(NameExpression.class,call.callee()).target().isEmpty());
    }
    @Test void classDefaultsRetainCompleteClassLookup() throws Exception {
        var project = read("struct C { static int f(int x=g()); static int g(); }; struct D { static int f(int x=f(1)); };");
        assertTrue(nodes(project,CallExpression.class).stream().allMatch(call -> call.target().isPresent()));
        assertFalse(nodes(project,Node.class).stream().anyMatch(node -> diagnostic(node,"invalid-default-argument")));
    }
    @Test void defaultsCannotUseParametersOrLocalVariables() throws Exception {
        var project = read("void f(int a,int b=a); void g(){int n=1; void h(int a=n);}");
        assertEquals(2,nodes(project,NameExpression.class).stream().filter(name -> diagnostic(name,"invalid-default-argument")).count());
    }
    @Test void parameterScopeAndUnevaluatedRestrictionsFollowCpp03Baseline() throws Exception {
        var project = read("int n; void f(int n=n); void g(int a,int b=sizeof(a)); void h(){int local;void k(int x=sizeof(local));}");
        var parameter = nodes(project,ParameterDeclaration.class).getFirst();
        assertSame(parameter,nodes(parameter.defaultValue().orElseThrow(),NameExpression.class).getFirst().target().orElseThrow());
        assertEquals(3,nodes(project,Node.class).stream().filter(node -> diagnostic(node,"invalid-default-argument")).count());
    }
    @Test void validCallInsideDefaultDoesNotEnterFunctionSignature() throws Exception {
        var project = read("int f(int a); int f(int b=f(1)); int main(){f();}");
        assertEquals(2,calls(project,"f").size());
        assertEquals(1,calls(project,"f").getLast().defaultArguments().size());
    }
    @Test void editingCallSyntaxClearsDefaultReferencesUntilReanalysis() throws Exception {
        var project = read("void f(int n=1); void g(int n); int main(){f();}");
        var call = calls(project,"f").getFirst();
        var editor = new ProjectEditor(project);
        editor.replace(call.callee(), NameExpression.create().setName("g").build());
        var edited = editor.build();
        assertTrue(calls(edited,"g").getFirst().defaultArguments().isEmpty());
        var resolved = NameResolver.resolve(edited);
        assertTrue(diagnostic(calls(resolved,"g").getFirst(),"missing-call-argument"));
    }
    @Test void returnReferenceAndValueConversionsAreRetained() throws Exception {
        var project = read("long value(){return 1;} int &ref(int &n){return n;} int main(){int n=1; ref(n);}");
        var returns = nodes(project,ReturnStatement.class);
        assertEquals("long",assertInstanceOf(ConversionExpression.class,returns.getFirst().value().orElseThrow()).targetType().canonicalIdentity());
        assertEquals(ConversionExpression.Kind.REFERENCE_BINDING,assertInstanceOf(ConversionExpression.class,returns.get(1).value().orElseThrow()).kind());
        assertEquals(ValueCategory.LVALUE,calls(project,"ref").getFirst().semanticInfo().orElseThrow().category());
    }
}
