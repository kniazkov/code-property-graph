/* C++03 inheritance and virtual dispatch regressions. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.ClassInitializationSemanticsTest.*;
class InheritanceSemanticsTest {
    @Test void overrideRelationsDoNotRequireCallsAndRespectNominalSignatures() {
        var p = parse("struct Printer { virtual void reset() {} }; struct Scanner { virtual void reset() {} }; struct Device : Printer, Scanner { void reset() {} void reset(int) {} }; ");
        var methods = nodes(p, MethodDeclaration.class);
        assertEquals(List.of(methods.get(0), methods.get(1)), InheritanceSemantics.overrides(p, methods.get(2)));
        assertEquals(List.of(methods.get(2)), InheritanceSemantics.overriddenBy(p, methods.get(0)));
        assertTrue(InheritanceSemantics.overrides(p, methods.get(3)).isEmpty());
        var nominal = parse("struct X {}; struct Y {}; struct B { virtual void f(X) {} virtual void g() const {} virtual ~B() {} }; struct D:B { void f(Y) {} void g() {} ~D() {} };");
        var all = nodes(nominal, MethodDeclaration.class);
        assertTrue(InheritanceSemantics.overrides(nominal, all.get(3)).isEmpty());
        assertTrue(InheritanceSemantics.overrides(nominal, all.get(4)).isEmpty());
        assertEquals(List.of(all.get(2)), InheritanceSemantics.overrides(nominal, all.get(5)));
    }
    @Test void staticDefaultsAndVirtualImplementationsStaySeparate() {
        var p = parse("struct Base { virtual int run(int n=1) {return n;} }; struct Derived:Base { int run(int n=2) {return n;} }; int f(Base& b, Derived& d) {return b.run()+d.run();}");
        var methods = nodes(p, MethodDeclaration.class); var calls = nodes(p, CallExpression.class);
        assertSame(methods.get(0), calls.get(0).target().orElseThrow());
        assertSame(methods.get(1), calls.get(1).target().orElseThrow());
        assertSame(methods.get(0), calls.get(0).defaultArguments().getFirst().declaration());
        assertSame(methods.get(1), calls.get(1).defaultArguments().getFirst().declaration());
        assertTrue(calls.stream().allMatch(CallExpression::isVirtualDispatch));
        assertTrue(calls.stream().noneMatch(CallExpression::virtualTargetsComplete));
        assertTrue(calls.get(0).virtualTargets().contains(methods.get(1)));
        assertEquals(List.of(methods.get(1)), calls.get(1).virtualTargets());
    }
    @Test void virtualDiamondIsSharedButOrdinaryDiamondIsAmbiguous() {
        var p = parse("struct V { int x; int f() {return 1;} }; struct L:virtual V {}; struct R:virtual V {}; struct D:L,R {int read(){return x;}}; int use(D& d) {return d.x+d.f();}");
        var x = nodes(p, VariableDeclaration.class).getFirst();
        assertTrue(nodes(p, NameExpression.class).stream().filter(n -> n.name().equals("x")).allMatch(n -> n.target().orElse(null)==x));
        assertTrue(nodes(p, MemberExpression.class).stream().filter(n -> n.member().equals("x")).allMatch(n -> n.target().orElse(null)==x));
        assertTrue(nodes(p, CallExpression.class).getFirst().target().isPresent());
        var ambiguous = parse("struct V {int x; int f(){return 1;}}; struct L:V {}; struct R:V {}; struct D:L,R {}; int use(D& d){return d.x+d.f();}");
        assertTrue(nodes(ambiguous, MemberExpression.class).stream().allMatch(n -> n.target().isEmpty()));
        assertEquals(CallExpression.Resolution.AMBIGUOUS, nodes(ambiguous, CallExpression.class).getFirst().resolution());
    }
    @Test void hidingDoesNotFallThroughToBasesOrGlobals() {
        var p = parse("int f(){return 0;} struct B{int f(){return 1;}}; struct D:B{int f(int n){return n;}}; int use(D& d){return d.f();}");
        var call = nodes(p, CallExpression.class).getFirst();
        assertEquals(1, call.target().orElseThrow().parameters().size());
        assertTrue(call.diagnostics().stream().anyMatch(d -> d.code().equals("missing-call-argument")));
        var unknown = parse("struct Unknown; int f(){return 0;} int use(Unknown& u){return u.f();}");
        assertTrue(nodes(unknown, CallExpression.class).getFirst().target().isEmpty());
    }
    @Test void pureOverridesAndQualifiedCalls() {
        var p = parse("struct B {virtual int f()=0;}; struct D:B {int f(){return 1;}}; int use(B& b){return b.f();}");
        var methods = nodes(p, MethodDeclaration.class); var calls = nodes(p, CallExpression.class);
        assertTrue(methods.getFirst().isPureVirtual());
        assertEquals(List.of(methods.get(1)), calls.getFirst().virtualTargets());
        var qualified = parse("struct B {virtual int f(){return 1;}}; struct D:B {int g(){return B::f();}};");
        assertFalse(nodes(qualified, CallExpression.class).getFirst().isVirtualDispatch());
        var invalid = parse("struct B {int f()=0;};");
        assertTrue(nodes(invalid, MethodDeclaration.class).getFirst().diagnostics().stream().anyMatch(d -> d.code().equals("invalid-pure-virtual")));
    }
    @Test void derivedDeclarationDominatesSharedVirtualBase() {
        var p = parse("struct V {int x;}; struct L:virtual V {int x;}; struct R:virtual V {}; struct D:L,R {}; int use(D& d){return d.x;}");
        assertSame(nodes(p, VariableDeclaration.class).get(1), nodes(p, MemberExpression.class).getFirst().target().orElseThrow());
    }
    @Test void aritySelectsOverloadsWithoutGlobalFallback() {
        var p = parse("struct B {virtual int f(){return 1;}}; struct D:B {int f(){return 2;} int f(int n){return n;}}; int use(D& d){return d.f()+d.f(4);}");
        var calls = nodes(p, CallExpression.class);
        assertEquals(0, calls.getFirst().target().orElseThrow().parameters().size());
        assertTrue(calls.getFirst().isVirtualDispatch());
        assertEquals(1, calls.getLast().target().orElseThrow().parameters().size());
        assertFalse(calls.getLast().isVirtualDispatch());
    }
    @Test void outOfLineVirtualMethodsRetainRelationships() {
        var p = parse("struct A{virtual void f();}; void A::f(){} struct B:A{void f();}; void B::f(){} void use(B& b){b.f();}");
        var methods = nodes(p, CallableDeclaration.class);
        var baseDefinition = methods.stream().filter(m -> m.name().equals("A::f")).findFirst().orElseThrow();
        assertTrue(InheritanceSemantics.isVirtual(p, baseDefinition));
        var call = nodes(p, CallExpression.class).getFirst();
        assertTrue(call.isVirtualDispatch());
        assertEquals("B::f", call.virtualTargets().getFirst().name());
    }
    @Test void unrelatedStaticNamesAreAmbiguousBeforeArityFiltering() {
        var p = parse("struct A{static void f(){}}; struct B{static void f(int){}}; struct D:A,B{}; void use(D& d){d.f();}");
        assertEquals(CallExpression.Resolution.AMBIGUOUS, nodes(p, CallExpression.class).getFirst().resolution());
    }
    @Test void virtualAncestorDoesNotCoalesceItsNonvirtualBaseWithAnotherSubobject() {
        var p = parse("struct V{void f(){}}; struct A:V{}; struct B:V{void f(){}}; struct D:virtual A,B{}; void use(D& d){d.f();}");
        assertEquals(CallExpression.Resolution.AMBIGUOUS, nodes(p, CallExpression.class).getFirst().resolution());
    }
    @Test void constructionCallsDoNotDispatchIntoMoreDerivedClasses() {
        var p = parse("struct A{A(){f();this->f();} virtual ~A(){f();} virtual void f(){}}; struct B:A{void f(){}};");
        for (var call : nodes(p, CallExpression.class)) {
            assertTrue(call.isVirtualDispatch());
            assertEquals(1, call.virtualTargets().size());
            assertEquals("A", InheritanceSemantics.owningClass(p, call.virtualTargets().getFirst()).orElseThrow().name());
        }
        var unrelated = parse("struct A{A(A& other){other.f();} virtual void f(){}}; struct B:A{void f(){}};");
        assertEquals(2, nodes(unrelated, CallExpression.class).getFirst().virtualTargets().size());
    }
    @Test void explicitlyQualifiedObjectCallSuppressesVirtualDispatch() {
        var p = parse("struct A{virtual void f(){}}; struct B:A{void f(){}}; void use(B& b){b.A::f();}");
        var call = nodes(p, CallExpression.class).getFirst();
        assertTrue(call.target().isPresent());
        assertEquals("A", InheritanceSemantics.owningClass(p, call.target().orElseThrow()).orElseThrow().name());
        assertFalse(call.isVirtualDispatch());
    }
    @Test void pureOutOfLineBodiesAreNeverVirtualImplementations() {
        var p = parse("struct A{virtual void f(){}}; struct B:A{void f()=0;}; void B::f(){} void use(A& a){a.f();}");
        var base = nodes(p, MethodDeclaration.class).getFirst();
        assertEquals(List.of(base), InheritanceSemantics.possibleTargets(p, base, null));
        assertEquals(List.of(base), nodes(p, CallExpression.class).getFirst().virtualTargets());
    }
    @Test void outOfLineVirtualCallsUseInClassDefaults() {
        var p = parse("struct B{virtual int f(int n=1);}; int B::f(int n){return n;} struct D:B{int f(int n=2);}; int D::f(int n){return n;} int use(B& b,D& d){return b.f()+d.f();}");
        var calls = nodes(p, CallExpression.class);
        assertEquals("f", calls.getFirst().defaultArguments().getFirst().declaration().name());
        assertEquals("B", InheritanceSemantics.owningClass(p, calls.getFirst().defaultArguments().getFirst().declaration()).orElseThrow().name());
        assertEquals("D", InheritanceSemantics.owningClass(p, calls.getLast().defaultArguments().getFirst().declaration()).orElseThrow().name());
        assertEquals("B::f", calls.getFirst().target().orElseThrow().name());
        assertEquals("D::f", calls.getLast().target().orElseThrow().name());
    }
    @Test void dominanceIncludesNonvirtualSubobjectsInsideSharedVirtualBases() {
        var p = parse("struct V{void f();}; struct A:V{}; struct B:virtual A{void f();}; struct C:virtual A{}; struct D:B,C{}; void use(D& d){d.f();}");
        var call = nodes(p, CallExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
        assertEquals("B", InheritanceSemantics.owningClass(p, call.target().orElseThrow()).orElseThrow().name());
        assertTrue(nodes(p, Node.class).stream().flatMap(n -> n.diagnostics().stream())
                .noneMatch(d -> d.code().equals("ambiguous-name")));
    }
}
