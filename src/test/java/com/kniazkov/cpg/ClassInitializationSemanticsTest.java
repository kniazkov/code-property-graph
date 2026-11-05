/* C++03 initialization plans use original declaration identities. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassInitializationSemanticsTest {
    static Project parse(String text) { return NameResolver.resolve(Project.create().addFile(Parser.parse("classes.cpp", text, Language.CPP)).build()); }
    static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (kind.isInstance(node)) result.add(kind.cast(node)); }}); return result;
    }
    @Test void constructionOrderIgnoresWrittenInitializerOrderAndDestructionReversesIt() {
        var project = parse("struct B { B(int n) {} }; struct C : B { int first; int second; static int count; C(int n) : second(n + 1), B(n), first(n) {} };");
        var cls = nodes(project, ClassDeclaration.class).getLast();
        var ctor = nodes(cls, MethodDeclaration.class).getFirst();
        var order = ClassSemantics.constructionOrder(cls, ctor);
        assertSame(cls.bases().getFirst(), order.get(0).declaration());
        assertEquals(List.of("first", "second"), order.subList(1, 3).stream().map(value -> ((VariableDeclaration)value.declaration()).name()).toList());
        assertSame(ctor.initializers().get(2), order.get(1).writtenInitializer().orElseThrow());
        assertSame(order.get(2).declaration(), ClassSemantics.destructionOrder(cls, ctor).getFirst().declaration());
        assertEquals(ClassSemantics.Origin.SUPPRESSED, ClassSemantics.specialMembers(cls).getFirst().origin());
        assertFalse(ClassSemantics.aggregate(cls));
    }
    @Test void implicitMembersAndAggregateRemainDerivedNotDuplicateSourceDeclarations() {
        var project = parse("struct Plain { int value; }; struct Copy { Copy(const Copy&); Copy& operator=(const Copy&); ~Copy(); };");
        var classes = nodes(project, ClassDeclaration.class);
        assertTrue(ClassSemantics.aggregate(classes.getFirst()));
        assertTrue(ClassSemantics.specialMembers(classes.getFirst()).stream().allMatch(member -> member.origin() == ClassSemantics.Origin.IMPLICIT));
        assertEquals(List.of(ClassSemantics.Origin.SUPPRESSED, ClassSemantics.Origin.USER_DECLARED, ClassSemantics.Origin.USER_DECLARED, ClassSemantics.Origin.USER_DECLARED), ClassSemantics.specialMembers(classes.getLast()).stream().map(ClassSemantics.SpecialMember::origin).toList());
        assertEquals(1, classes.getFirst().members().size());
    }
    @Test void storageAndTemporaryBoundariesAreExplicit() {
        var project = parse("struct Plain { int value; }; Plain global; int main() { Plain local; static Plain once; const Plain& extended = Plain(); Plain(); return 0; }");
        var variables = nodes(project, VariableDeclaration.class);
        var global = variables.stream().filter(v -> v.name().equals("global")).findFirst().orElseThrow();
        var local = variables.stream().filter(v -> v.name().equals("local")).findFirst().orElseThrow();
        assertEquals(List.of(ClassSemantics.InitializationKind.ZERO, ClassSemantics.InitializationKind.DEFAULT), ClassSemantics.initialization(project, global).phases());
        assertEquals(List.of(ClassSemantics.InitializationKind.DEFAULT), ClassSemantics.initialization(project, local).phases());
        var lifetimes = ClassSemantics.lifetimes(project);
        assertEquals(2, lifetimes.stream().filter(l -> l.end() == ClassSemantics.LifetimeEnd.PROGRAM_EXIT).count());
        assertEquals(1, lifetimes.stream().filter(l -> l.end() == ClassSemantics.LifetimeEnd.BLOCK_EXIT).count());
        assertEquals(1, lifetimes.stream().filter(l -> l.end() == ClassSemantics.LifetimeEnd.REFERENCE_LIFETIME).count());
        assertEquals(1, lifetimes.stream().filter(l -> l.end() == ClassSemantics.LifetimeEnd.FULL_EXPRESSION).count());
        assertTrue(nodes(project, InitializationExpression.class).stream().allMatch(i -> ClassSemantics.temporaryInitialization(i) == ClassSemantics.InitializationKind.VALUE));
    }
    @Test void transitiveVirtualPlanDoesNotInventContextIndependentFlow() {
        var project = parse("struct V {}; struct B : virtual V {}; struct D : B { D() : V(), B() {} };");
        var owner = nodes(project, ClassDeclaration.class).getLast();
        var constructor = nodes(owner, MethodDeclaration.class).getFirst();
        assertFalse(ClassSemantics.hasCompleteNonvirtualHierarchy(owner));
        assertTrue(ClassSemantics.hasCompleteHierarchy(owner));
        assertEquals(List.of("V", "B"), ClassSemantics.constructionOrder(owner, constructor).stream()
                .map(item -> item.writtenInitializer().orElseThrow().name()).toList());
        assertTrue(nodes(project, Node.class).stream().flatMap(node -> node.diagnostics().stream()).noneMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR));
        var flowed = ControlFlow.analyze(project);
        assertEquals(1, flowed.flows().size());
        assertFalse(flowed.flows().getFirst().complete());
        assertTrue(flowed.flows().getFirst().control().isEmpty());
        assertFalse(DataFlow.analyze(flowed).flows().getFirst().complete());
        assertTrue(nodes(flowed, Node.class).stream().flatMap(node -> node.diagnostics().stream()).anyMatch(diagnostic -> diagnostic.code().equals("unsupported-constructor-flow")));
        var unresolved = Parser.parse("unknown.cpp", "struct Unknown; struct D : Unknown { D() {} };", Language.CPP).build();
        var unknownOwner = nodes(unresolved, ClassDeclaration.class).getLast();
        assertFalse(ClassSemantics.hasCompleteNonvirtualHierarchy(unknownOwner));
        assertFalse(ClassSemantics.hasCompleteHierarchy(unknownOwner));
        assertThrows(IllegalArgumentException.class, () -> ClassSemantics.constructionOrder(unknownOwner, nodes(unknownOwner, MethodDeclaration.class).getFirst()));
    }
    @Test void incompleteAndCyclicVirtualHierarchiesDoNotPublishPartialPlans() {
        var incomplete = parse("struct Unknown; struct V : virtual Unknown {}; struct D : V { D() {} };");
        var owner = nodes(incomplete, ClassDeclaration.class).getLast();
        var constructor = nodes(owner, MethodDeclaration.class).getFirst();
        assertFalse(ClassSemantics.hasCompleteHierarchy(owner));
        assertThrows(IllegalArgumentException.class, () -> ClassSemantics.constructionOrder(owner, constructor));
        assertThrows(IllegalArgumentException.class, () -> ClassSemantics.constructionOrder(owner, constructor, ClassSemantics.ConstructionContext.BASE_SUBOBJECT));
        assertFalse(ControlFlow.analyze(incomplete).flows().getFirst().complete());
        var cyclic = ClassDeclaration.create().setName("Cyclic");
        cyclic.addBase(BaseSpecifier.create().setVirtual(true).setType(NamedType.create().setName("Cyclic").setDeclaration(cyclic)));
        var cycle = cyclic.build();
        assertFalse(ClassSemantics.hasCompleteHierarchy(cycle));
        assertThrows(IllegalArgumentException.class, () -> ClassSemantics.virtualBases(cycle));
    }
    @Test void virtualDiamondOrdersSharedBasesOnceBeforeDirectBasesAndFields() {
        var project = parse("struct W {}; struct V : virtual W {}; struct X {}; struct L : virtual V {}; struct R : virtual V, virtual X {}; struct D : L, R { int value; typedef V Alias; D() : value(1), R(), Alias(), L(), W(), X() {} };");
        for (var snapshot : List.of(project, project.edit().build())) {
            var owner = nodes(snapshot, ClassDeclaration.class).getLast();
            var constructor = nodes(owner, MethodDeclaration.class).getFirst();
            var order = ClassSemantics.constructionOrder(owner, constructor);
            assertEquals(List.of("W", "Alias", "X", "L", "R", "value"), order.stream()
                    .map(item -> item.writtenInitializer().orElseThrow().name()).toList());
            assertEquals(3, ClassSemantics.virtualBases(owner).size());
            assertTrue(order.stream().allMatch(item -> nodes(snapshot, Node.class).contains(item.declaration())));
            assertEquals(order.reversed(), ClassSemantics.destructionOrder(owner, constructor));
            var baseOrder = ClassSemantics.constructionOrder(owner, constructor, ClassSemantics.ConstructionContext.BASE_SUBOBJECT);
            assertEquals(List.of("L", "R", "value"), baseOrder.stream().map(item -> item.writtenInitializer().orElseThrow().name()).toList());
            assertEquals(baseOrder.reversed(), ClassSemantics.destructionOrder(owner, constructor, ClassSemantics.ConstructionContext.BASE_SUBOBJECT));
            assertTrue(nodes(snapshot, Node.class).stream().flatMap(node -> node.diagnostics().stream()).noneMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR));
        }
    }
    @Test void virtualAndNonvirtualOccurrencesRemainSeparateAndMissingInitializersStayAbsent() {
        var project = parse("struct V {}; struct L : virtual V {}; struct R : V {}; struct D : L, R { D() {} D(const D& other) : V(other), L(other), R(other) {} };");
        var owner = nodes(project, ClassDeclaration.class).getLast();
        var constructors = nodes(owner, MethodDeclaration.class);
        var implicit = ClassSemantics.constructionOrder(owner, constructors.getFirst());
        assertEquals(3, implicit.size());
        assertTrue(implicit.stream().allMatch(item -> item.writtenInitializer().isEmpty()));
        assertEquals(List.of("V", "L", "R"), ClassSemantics.constructionOrder(owner, constructors.getLast()).stream()
                .map(item -> item.writtenInitializer().orElseThrow().name()).toList());
        assertEquals(ClassSemantics.Origin.USER_DECLARED, ClassSemantics.specialMembers(owner).get(1).origin());
    }
    @Test void nonBaseAndIndirectNonvirtualInitializersAreRejectedEvenWithVirtualBases() {
        for (String source : List.of(
                "struct V {}; struct B {}; struct D : virtual V { D() : B() {} };",
                "struct V {}; struct B : V {}; struct D : B { D() : V() {} };",
                "struct V {}; struct B : virtual V {}; struct D : V, B { D() : V() {} };")) {
            var project = parse(source);
            assertTrue(nodes(project, Node.class).stream().flatMap(node -> node.diagnostics().stream())
                    .anyMatch(diagnostic -> diagnostic.code().equals("invalid-member-initializer")), source);
        }
    }
    @Test void initializerTargetsDistinguishAliasesShadowingAndLaterFields() {
        var project = parse("struct B { B() {} }; struct D : B { int B; D() : B(3), ::B() {} }; struct E : B { typedef B Alias; E(int later) : Alias(), later(later) {} int later; };");
        var classes = nodes(project, ClassDeclaration.class);
        var d = classes.get(1); var dc = nodes(d, MethodDeclaration.class).getFirst();
        var order = ClassSemantics.constructionOrder(d, dc);
        assertEquals("::B", order.getFirst().writtenInitializer().orElseThrow().name());
        assertEquals("B", order.getLast().writtenInitializer().orElseThrow().name());
        assertInstanceOf(ClassDeclaration.class, order.getFirst().writtenInitializer().orElseThrow().target().orElseThrow());
        assertInstanceOf(VariableDeclaration.class, order.getLast().writtenInitializer().orElseThrow().target().orElseThrow());
        var e = classes.getLast(); var ec = nodes(e, MethodDeclaration.class).getFirst();
        assertInstanceOf(TypedefDeclaration.class, ec.initializers().getFirst().target().orElseThrow());
        assertInstanceOf(VariableDeclaration.class, ec.initializers().getLast().target().orElseThrow());
        assertEquals("Alias", ClassSemantics.constructionOrder(e, ec).getFirst().writtenInitializer().orElseThrow().name());
        assertTrue(nodes(project, Node.class).stream().flatMap(node -> node.diagnostics().stream()).noneMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR));
        assertDoesNotThrow(() -> project.edit().build());
        var duplicate = parse("struct B {}; struct D : B { typedef B Alias; D() : B(), Alias() {} };");
        assertTrue(nodes(duplicate, Node.class).stream().flatMap(node -> node.diagnostics().stream()).anyMatch(diagnostic -> diagnostic.code().equals("duplicate-member-initializer")));
    }
    @Test void constructorFlowWarningsAreReplacedWithoutDuplicatingOtherFindings() {
        var original = parse("struct B {}; struct C : B { C() {} };");
        var constructor = nodes(original, MethodDeclaration.class).getFirst();
        var stale = original.withDiagnostics("unsupported-constructor-flow", Map.of(constructor,
                new Diagnostic("unsupported-constructor-flow", Diagnostic.Severity.WARNING, "Old unresolved hierarchy")));
        var copiedConstructor = nodes(stale, MethodDeclaration.class).getFirst();
        stale = stale.withDiagnostics("other-rule", Map.of(copiedConstructor, new Diagnostic("other-rule", Diagnostic.Severity.WARNING, "Keep this finding")));
        var analyzed = ControlFlow.analyze(ControlFlow.analyze(stale));
        var findings = nodes(analyzed, MethodDeclaration.class).getFirst().diagnostics();
        assertEquals(1, findings.size()); assertEquals("other-rule", findings.getFirst().code());
        assertTrue(analyzed.flows().getFirst().complete());
        var unsupported = parse("struct V {}; struct B : virtual V { B() {} };");
        var repeated = ControlFlow.analyze(ControlFlow.analyze(unsupported));
        assertEquals(1, nodes(repeated, MethodDeclaration.class).getFirst().diagnostics().stream().filter(d -> d.code().equals("unsupported-constructor-flow")).count());
    }
    @Test void inheritedLookupDoesNotBindAnUnrelatedGlobal() {
        var project = parse("int x; struct B { int x; }; struct D : B {}; int f(D d) { return d.x; }");
        var selection = nodes(project, DotMemberExpression.class).getFirst();
        assertSame(nodes(project, ClassDeclaration.class).getFirst().members().getFirst(), selection.target().orElseThrow());
    }
    @Test void namespacesAndMultipleUnnamedBitFieldsRemainDistinct() {
        var project = parse("namespace N { struct C { unsigned int : 0; unsigned int : 0; C(); ~C(); }; } N::C::C() {} N::C::~C() {}");
        assertTrue(nodes(project, Node.class).stream().flatMap(node -> node.diagnostics().stream()).noneMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR));
        assertEquals(2, nodes(project, MethodDeclaration.class).stream().filter(method -> method.previousDeclaration().isPresent()).count());
    }
    @Test void invalidInitializersAndBitWidthsAreDiagnosed() {
        for (String text : List.of("struct C { int x; C() : x(1), x(2) {} };", "struct C { static int x; C() : x(1) {} };",
                "struct C { int x : 0; };", "struct C { int x : -1; };")) {
            var project = parse(text);
            assertTrue(nodes(project, Node.class).stream().flatMap(node -> node.diagnostics().stream()).anyMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR), text);
        }
    }
    @Test void nestedMemberTypesAndClassSignaturesDoNotDuplicateOwnership() {
        var project = parse("struct C { struct Nested { int value; }; C(const C&); C& take(const C&); int read(Nested n); }; C::C(const C& other) {} C& C::take(const C& other) { return *this; } int C::read(Nested n) { Nested local; return n.value; } int test(C& c, const C& other) { c.take(other); return 0; }");
        assertTrue(nodes(project, Node.class).stream().flatMap(node -> node.diagnostics().stream()).noneMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR));
        assertDoesNotThrow(() -> project.edit().build());
    }
    @Test void unionConstructionInitializesOnlyTheSelectedMember() {
        var project = parse("union U { int first; int second; U() : second(1) {} };");
        var owner = nodes(project, ClassDeclaration.class).getFirst();
        var constructor = nodes(owner, MethodDeclaration.class).getFirst();
        assertEquals(1, ClassSemantics.constructionOrder(owner, constructor).size());
        assertEquals("second", ((VariableDeclaration)ClassSemantics.constructionOrder(owner, constructor).getFirst().declaration()).name());
    }
    @Test void externalDefinitionsAndObjectMembersResolveToTheirDeclarations() {
        var project = ClassInitializationExportTest.parse(ClassInitializationExportTest.TEXT);
        var method = nodes(project, MethodDeclaration.class).stream().filter(m -> m.name().equals("C::value")).findFirst().orElseThrow();
        assertTrue(method.previousDeclaration().isPresent());
        var call = nodes(project, CallExpression.class).stream().filter(c -> c.callee() instanceof DotMemberExpression).findFirst().orElseThrow();
        assertSame(method, call.target().orElseThrow());
        var first = nodes(method, NameExpression.class).stream().filter(n -> n.name().equals("first")).findFirst().orElseThrow();
        assertTrue(first.target().orElseThrow() instanceof VariableDeclaration);
    }
}
