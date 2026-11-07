/* C++03 overload vectors, ADL, and nominal conversion identities. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OverloadResolutionTest {
    static Project resolve(String text) { return NameResolver.resolve(Project.create().addFile(Parser.parse("overloads.cpp", text, Language.CPP)).build()); }
    static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var found = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) found.add(type.cast(node)); }}); return found;
    }
    static List<CallExpression> calls(Project p, String name) { return nodes(p, CallExpression.class).stream().filter(c -> c.callee() instanceof NameExpression n && n.name().equals(name)).toList(); }
    static String parameter(CallExpression call) { return call.target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity(); }
    static boolean diagnostic(Node n,String code) { return n.diagnostics().stream().anyMatch(d -> d.code().equals(code)); }
    @Test void exactNominalTypesBeatEqualWidthAndPromotionBeatsConversion() {
        var p = resolve("int f(int); int f(long); int g(int); int g(long); int h(double); int h(...); int main(){short s=1; f(1);f(1L);g(s);h(1.0f);}");
        assertEquals("int",parameter(calls(p,"f").get(0))); assertEquals("long",parameter(calls(p,"f").get(1)));
        assertEquals("int",parameter(calls(p,"g").getFirst())); assertEquals("double",parameter(calls(p,"h").getFirst()));
    }
    @Test void pairwiseVectorsRemainAmbiguousAndNoViableIsNotBound() {
        var p = resolve("int f(int,long); int f(long,int); int g(int*); int main(){ f(1,1);g(2); }");
        var f=calls(p,"f").getFirst(); assertEquals(CallExpression.Resolution.AMBIGUOUS,f.resolution()); assertTrue(f.target().isEmpty()); assertTrue(diagnostic(f,"ambiguous-overload"));
        var g=calls(p,"g").getFirst(); assertEquals(CallExpression.Resolution.UNRESOLVED,g.resolution()); assertTrue(g.target().isEmpty()); assertTrue(diagnostic(g,"no-viable-overload"));
    }
    @Test void adlFindsAssociatedNamespaceAndHiddenFriendOnlyForClassArgument() {
        var p=resolve("namespace n { struct A { friend int friendOnly(const A&); }; int tagged(const A&); } int main(){n::A a;tagged(a);friendOnly(a);friendOnly(1);}");
        assertTrue(calls(p,"tagged").getFirst().target().isPresent());
        var friends=calls(p,"friendOnly"); assertTrue(friends.getFirst().target().isPresent()); assertTrue(friends.getLast().target().isEmpty());
    }
    @Test void constReferenceCannotDropConstOrBindMutableReferenceToTemporary() {
        var p=resolve("int f(int&); int f(const int&); int g(int&); int main(){int a=1; const int b=2; f(a);f(b);f(3);g(4);}");
        var f=calls(p,"f"); assertEquals("reference(int)",parameter(f.get(0))); assertEquals("reference(const(int))",parameter(f.get(1))); assertEquals("reference(const(int))",parameter(f.get(2)));
        assertTrue(calls(p,"g").getFirst().target().isEmpty());
    }
    @Test void userConversionsAndExplicitConstructorExclusion() {
        var p=resolve("struct A { operator int() const; }; struct B { B(int); }; struct C { explicit C(int); }; int f(int); int g(B); int h(C); int main(){A a;f(a);g(1);h(1);}");
        assertTrue(calls(p,"f").getFirst().target().isPresent()); assertTrue(calls(p,"g").getFirst().target().isPresent()); assertTrue(calls(p,"h").getFirst().target().isEmpty());
    }
    @Test void selectedPrivateMemberIsDiagnosedEvenWhenPublicWorseMatchExists() {
        var p=resolve("class A { int f(int); public: int f(long); }; int main(){A a;a.f(1);}");
        var call=nodes(p,CallExpression.class).getFirst(); assertEquals("int",parameter(call)); assertTrue(diagnostic(call,"inaccessible-overload"));
    }
    @Test void nestedCallsProvideBoundClassResultsForAdl() {
        var p=resolve("namespace n { struct A {}; A make(); int tagged(const A&); } int main(){ tagged(n::make()); }");
        assertTrue(calls(p,"tagged").getFirst().target().isPresent());
    }
    @Test void valueAndConstReferenceRemainAmbiguousAndFunctionPointerCannotBecomeVoidPointer() {
        var p=resolve("int f(int); int f(const int&); int g(void*); int fn(); int main(){int n=1;f(n);g(&fn);}");
        assertEquals(CallExpression.Resolution.AMBIGUOUS,calls(p,"f").getFirst().resolution());
        assertTrue(calls(p,"g").getFirst().target().isEmpty());
    }
    @Test void enumArgumentsContributeTheirNamespaceToAdl() {
        var p=resolve("namespace n { enum E { e }; int f(E); } int main(){return f(n::e);}");
        assertTrue(calls(p,"f").getFirst().target().isPresent());
    }
    @Test void selectedOutOfLineDefinitionKeepsDeclaredPublicAccess() {
        var p=resolve("class A { public: int f(int); }; int A::f(int x){return x;} int main(){A a;return a.f(1);}");
        assertFalse(diagnostic(nodes(p,CallExpression.class).getFirst(),"inaccessible-overload"));
    }
    @Test void implicitObjectConstQualificationParticipatesInMemberSelection() {
        var p=resolve("struct A { int f(); int f() const; }; int main(){A a;const A b; a.f();b.f();}");
        var c=nodes(p,CallExpression.class); assertFalse(c.get(0).target().orElseThrow().isConst()); assertTrue(c.get(1).target().orElseThrow().isConst());
    }
    @Test void conversionFunctionAndConstructorOverloadsUseStandardRanks() {
        var p=resolve("struct A { operator int() const; operator long() const; }; struct B { B(int); B(long); }; int f(int);int g(B);int main(){A a; f(a);g(1);}");
        assertTrue(calls(p,"f").getFirst().target().isPresent()); assertTrue(calls(p,"g").getFirst().target().isPresent());
    }
    @Test void explicitConstructorsRemainEligibleForDirectInitialization() {
        var p=resolve("struct A { explicit A(int); A(const char*); }; int main(){A a(1);}");
        assertTrue(nodes(p,Node.class).stream().flatMap(n -> n.diagnostics().stream()).noneMatch(d -> d.code().equals("no-viable-overload")));
        var owner=nodes(p,ClassDeclaration.class).getFirst();
        var constructors=owner.members().stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).toList();
        var selected=OverloadResolution.select(constructors,List.of(IntegerLiteral.create().setValue(1).build()),com.kniazkov.cpg.build.PlatformProfile.defaults());
        assertTrue(selected.selected().orElseThrow().modifiers().contains("explicit"));
    }
    @Test void staticMemberHasNeutralImplicitObjectAndEnumPromotesToInt() {
        var p=resolve("enum E{e}; struct A{void f(int);static void f(long);};void g(int);void g(long);int main(){A a;a.f(1);g(e);}");
        assertEquals("int",parameter(nodes(p,CallExpression.class).getFirst())); assertEquals("int",parameter(calls(p,"g").getFirst()));
    }
    @Test void derivedPointerConversionBeatsVoidPointerAndRetainsNominalBase() {
        var p=resolve("struct A{};struct B:public A{};void f(A*);void f(void*);int main(){B b;f(&b);}");
        assertEquals("pointer(named(A))",parameter(calls(p,"f").getFirst()));
    }
    @Test void worseAmbiguousUserConversionDoesNotDefeatExactStandardCandidate() {
        var p=resolve("struct A{A(long);A(double);};void f(int);void f(A);int main(){f(1);}");
        assertEquals("int",parameter(calls(p,"f").getFirst()));
    }
    @Test void pointerArgumentsContributeNominalClassAndEnumNamespaces() {
        var p=resolve("namespace n{struct A{};enum E{e};void f(A*);void g(E*);}int main(){n::A a;n::E* p;f(&a);g(p);}");
        assertTrue(calls(p,"f").getFirst().target().isPresent());assertTrue(calls(p,"g").getFirst().target().isPresent());
    }
    @Test void selectedPrivateOperatorAndBaseConversionAreDiagnosed() {
        var p=resolve("class A{int operator+(int);};struct B{};struct D:private B{};void f(B*);int main(){A a;a+1;D d;f(&d);}");
        assertTrue(diagnostic(nodes(p,AddExpression.class).getFirst(),"inaccessible-overload"));
        assertTrue(diagnostic(calls(p,"f").getFirst(),"inaccessible-base-conversion"));
    }
    @Test void conversionFunctionBodyDoesNotSuppressVirtualDispatch() {
        var p=resolve("struct B{virtual int f(){return 1;} operator int(){return f();}};struct D:B{int f(){return 2;}};");
        var call=calls(p,"f").getFirst();assertTrue(call.virtualTargets().stream().anyMatch(target -> target.body().isPresent() && target != call.target().orElse(null)));
    }
    @Test void protectedAccessRequiresDerivedReceiverInDerivedContext() {
        var p=resolve("struct A{protected:void f();};struct D:A{void g(A& a,D& d){a.f();d.f();f();this->f();}};");
        var calls=nodes(p,CallExpression.class);assertTrue(diagnostic(calls.get(0),"inaccessible-overload"));
        for(int i=1;i<calls.size();i++)assertFalse(diagnostic(calls.get(i),"inaccessible-overload"));
    }
    @Test void matchingUnboundTypeSpellingsDoNotInventDependentTypeIdentity() {
        var argument=InitializationExpression.create().setForm(InitializationExpression.Form.TEMPORARY)
                .setType(NamedType.create().setName("T")).build();
        var result=OverloadResolution.conversion(argument,NamedType.create().setName("T").build(),
                com.kniazkov.cpg.build.PlatformProfile.defaults(),true);
        assertEquals(OverloadResolution.Rank.UNKNOWN,result.orElseThrow().rank());
    }
    @Test void protectedOperatorAccessRequiresDerivedReceiverInDerivedContext() {
        var p=resolve("struct A{protected:int operator+(int);};struct D:A{void g(A& a,D& d){a+1;d+1;operator+(1);this->operator+(1);}};");
        var operators=nodes(p,AddExpression.class);assertTrue(diagnostic(operators.get(0),"inaccessible-overload"));
        assertFalse(diagnostic(operators.get(1),"inaccessible-overload"));
        for(var call:nodes(p,CallExpression.class))assertFalse(diagnostic(call,"inaccessible-overload"));
    }
    @Test void enumOperatorUsesAdlAndRetainsUserCallEffects() {
        var p=resolve("namespace n{enum E{e};E operator+(E,E);}int main(){n::e+n::e;n::E x=n::e;x+x;}");
        var operators=nodes(p,AddExpression.class);assertEquals(2,operators.size());
        for(var operator:operators){assertEquals(CallExpression.Resolution.RESOLVED,operator.operatorResolution());
            assertEquals("operator+",operator.operatorTarget().orElseThrow().name());
            assertTrue(OperatorSemantics.mayInvokeUserCode(operator));}
    }
    @Test void selectedAmbiguousBaseConversionIsDiagnosedButVirtualBaseIsShared() {
        var p=resolve("struct A{};struct B:A{};struct C:A{};struct D:B,C{};void f(A*);int main(){D* d;f(d);}");
        var call=calls(p,"f").getFirst();assertTrue(call.target().isPresent());assertTrue(diagnostic(call,"ambiguous-base-conversion"));
        var shared=resolve("struct A{};struct B:virtual A{};struct C:virtual A{};struct D:B,C{};void f(A*);int main(){D* d;f(d);}");
        assertFalse(diagnostic(calls(shared,"f").getFirst(),"ambiguous-base-conversion"));
    }
    @Test void recursivePointerQualificationRequiresConstAtIntermediateLevels() {
        var p=resolve("void valid(const int* const*);void invalid(const int**);int main(){int** p;valid(p);invalid(p);}");
        assertTrue(calls(p,"valid").getFirst().target().isPresent());assertTrue(calls(p,"invalid").getFirst().target().isEmpty());
    }
    @Test void equalOpaqueConstructorArgumentsAllowKnownArgumentsToDecide() {
        var p=resolve("#include <iosfwd>\nstruct Buffer { Buffer(char*,std::streamsize); Buffer(const char*,std::streamsize); }; void f(char* data){Buffer b(data,1);}");
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var b=nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals("b")).findFirst().orElseThrow();
        var constructors=nodes(p,CallableDeclaration.class).stream().filter(c->c.kind()==CallableDeclaration.Kind.CONSTRUCTOR && c.name().equals("Buffer")).toList();
        var selected=OverloadResolution.select(constructors,((InitializationExpression)b.initializer().orElseThrow()).arguments(),com.kniazkov.cpg.build.PlatformProfile.defaults());
        assertEquals("pointer(char)",selected.selected().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
        var unknown=resolve("#include <iosfwd>\nstruct Buffer { Buffer(char*,std::streamsize); Buffer(const char*,std::streamoff); }; void f(char* data){Buffer b(data,1);}");
        var other=nodes(unknown,VariableDeclaration.class).stream().filter(v->v.name().equals("b")).findFirst().orElseThrow();
        var candidates=nodes(unknown,CallableDeclaration.class).stream().filter(c->c.kind()==CallableDeclaration.Kind.CONSTRUCTOR && c.name().equals("Buffer")).toList();
        assertTrue(OverloadResolution.select(candidates,((InitializationExpression)other.initializer().orElseThrow()).arguments(),com.kniazkov.cpg.build.PlatformProfile.defaults()).selected().isEmpty());
    }

}
