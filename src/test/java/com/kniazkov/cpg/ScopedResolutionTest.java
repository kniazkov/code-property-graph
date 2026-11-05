/* Source-point scopes, declaration identity, and linkage. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.analysis.TypeSemantics;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScopedResolutionTest {
    @TempDir Path directory;
    static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }}); return result;
    }
    Project resolve(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("scopes.cpp", source, Language.CPP)).build());
    }
    static boolean error(Node node, String code) {
        return nodes(node, Node.class).stream().flatMap(n -> n.diagnostics().stream()).anyMatch(d -> d.code().equals(code));
    }
    @Test void lexicalScopesHonorDeclarationPointsAndInitializerShadowing() {
        Project project = resolve("int value = 1; int f(int parameter) { int before = value; { int value = value; int inner = value; } int after = value; return parameter; } int later = 2;");
        var variables = nodes(project, VariableDeclaration.class);
        var global = variables.getFirst(); var shadow = variables.stream().filter(v -> v.name().equals("value")).toList().getLast();
        var uses = nodes(project, NameExpression.class).stream().filter(n -> n.name().equals("value")).toList();
        assertSame(global, uses.get(0).target().orElseThrow());
        assertSame(shadow, uses.get(1).target().orElseThrow());
        assertSame(shadow, uses.get(2).target().orElseThrow());
        assertSame(global, uses.get(3).target().orElseThrow());
        assertInstanceOf(ParameterDeclaration.class, nodes(project, ReturnStatement.class).getFirst().value()
                .map(NameExpression.class::cast).orElseThrow().target().orElseThrow());
        Project early = resolve("int f() { return later; } int later = 2;");
        assertTrue(nodes(early, NameExpression.class).getFirst().target().isEmpty());
    }
    @Test void namespacesReopenAndUsingChainsShareTheirOwnedDirective() {
        Project project = resolve("namespace shop { typedef long Raw; } namespace shop { typedef Raw Id; typedef int Count; } namespace s = shop; using namespace shop; Id first; Count second; s::Id qualified; namespace other { using shop::Id; Id imported; }");
        var namespaces = nodes(project, NamespaceDeclaration.class);
        assertSame(namespaces.getFirst(), namespaces.get(1).previousDeclaration().orElseThrow());
        var variables = nodes(project, VariableDeclaration.class);
        var first = (NamedType)variables.get(0).type().orElseThrow();
        var second = (NamedType)variables.get(1).type().orElseThrow();
        var directive = nodes(project, UsingDirective.class).getFirst();
        assertSame(directive, first.resolutionSteps().getFirst().via());
        assertSame(directive, second.resolutionSteps().getFirst().via());
        assertEquals("long", first.canonicalIdentity());
        assertTrue(((NamedType)variables.get(2).type().orElseThrow()).resolutionSteps().stream().anyMatch(s -> s.kind().equals("namespace-alias")));
        assertTrue(((NamedType)variables.get(3).type().orElseThrow()).resolutionSteps().stream().anyMatch(s -> s.kind().equals("using-declaration")));
    }
    @Test void forwardTypesHavePointCompletenessAndDefinitionEdgesWithoutInventedLayout() {
        Project project = resolve("class Order; Order *pointer; int early() { return sizeof(Order); } class Order { int id; }; int late() { return sizeof(Order); }");
        var classes = nodes(project, ClassDeclaration.class); assertSame(classes.get(1), classes.get(0).definition().orElseThrow());
        var sizes = nodes(project, SizeofExpression.class);
        var early = (NamedType)sizes.getFirst().type().orElseThrow();
        var late = (NamedType)sizes.getLast().type().orElseThrow();
        assertSame(classes.getFirst(), early.declaration().orElseThrow());
        assertFalse(early.completeAtUse()); assertTrue(late.completeAtUse());
        assertTrue(error(sizes.getFirst(), "incomplete-type")); assertFalse(error(sizes.getLast(), "incomplete-type"));
        assertTrue(TypeSemantics.sizeof(late, TypeSemantics.profile(project.files().getFirst())).isEmpty());
        assertFalse(error(nodes(project, VariableDeclaration.class).getFirst(), "incomplete-type"));
        Project copy = project.edit().build();
        assertSame(nodes(copy, ClassDeclaration.class).getLast(), nodes(copy, ClassDeclaration.class).getFirst().definition().orElseThrow());
        assertNotSame(classes.getFirst(), nodes(copy, ClassDeclaration.class).getFirst());
    }
    @Test void typedefOfForwardTypeCanBeUsedAfterDefinition() {
        Project project = resolve("class C; typedef C Alias; class C { int x; }; int f(){ return sizeof(Alias); }");
        var named = (NamedType)nodes(project, SizeofExpression.class).getFirst().type().orElseThrow();
        assertTrue(named.completeAtUse()); assertFalse(error(project, "incomplete-type"));
    }
    @Test void compatiblePrototypesLinkWhileOverloadsAreNotOdrConflicts() {
        Project project = resolve("int f(int); int f(const int x); int f(int x) { return x; } int f(long x) { return 2; } int main(){ return f(1); }");
        var functions = nodes(project, FunctionDeclaration.class);
        assertSame(functions.get(2), functions.getFirst().definition().orElseThrow());
        assertSame(functions.getFirst(), functions.get(1).previousDeclaration().orElseThrow());
        assertFalse(error(project, "odr-conflict")); assertFalse(error(project, "conflicting-declaration"));
        var call = nodes(project, CallExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.AMBIGUOUS, call.resolution()); assertEquals(2, call.candidates().size());
    }
    @Test void conflictingReturnTypesAndMultipleDefinitionsAreDiagnosed() {
        assertTrue(error(resolve("int f(int); long f(int);"), "conflicting-declaration"));
        assertTrue(error(resolve("extern int value; extern long value;"), "conflicting-declaration"));
        Project duplicate = resolve("int f(){ return 1; } int f(){ return 2; } int main(){ return f(); }");
        assertTrue(error(duplicate, "odr-conflict"));
        assertEquals(CallExpression.Resolution.ODR_CONFLICT, nodes(duplicate, CallExpression.class).getFirst().resolution());
    }
    @Test void externalCLanguageLinkageIsInheritedAndCannotOverload() {
        Project project = resolve("extern \"C\" int bridge(int); int bridge(int value){return value;} int main(){ return bridge(1); }");
        var functions = nodes(project, FunctionDeclaration.class);
        assertEquals("C", functions.get(1).languageLinkage());
        assertSame(functions.get(1), functions.getFirst().definition().orElseThrow());
        assertSame(functions.get(1), nodes(project, CallExpression.class).getFirst().target().orElseThrow());
        assertTrue(error(resolve("extern \"C\" int bridge(int); extern \"C\" int bridge(long);"), "conflicting-declaration"));
    }
    @Test void internalFunctionsAndObjectsRemainDistinctInSelectedComposition() throws Exception {
        Files.writeString(directory.resolve("one.cpp"), "static int helper(){return 1;} static int value = 3; namespace { int hidden(){return 2;} } int one(){return helper()+hidden()+value;}");
        Files.writeString(directory.resolve("two.cpp"), "static int helper(){return 4;} static int value = 6; namespace { int hidden(){return 5;} } int two(){return helper()+hidden()+value;}");
        List<Build.TranslationUnit> units = new ArrayList<>();
        for (String path : List.of("one.cpp", "two.cpp")) units.add(new Build.TranslationUnit(new Build.PhysicalSource(directory.resolve(path)), directory, List.of(), List.of(), "c++03"));
        Build build = new Build(units, new Build.Composition("both", units.stream().map(Build.TranslationUnit::variantId).toList()));
        Project project = Cpg.read(build, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        assertFalse(error(project, "odr-conflict"));
        for (SourceFile file : project.files()) {
            Set<Node> owned = new HashSet<>(nodes(file, Node.class));
            for (CallExpression call : nodes(file, CallExpression.class)) {
                assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
                assertTrue(owned.contains(call.target().orElseThrow()));
                assertEquals(Declaration.Linkage.INTERNAL, call.target().orElseThrow().linkage());
            }
        }
    }
    @Test void enumValuesGlobalsAndFunctionVisibilityRespectScope() {
        Project project = resolve("enum Count { zero, one = zero + 1 }; int global = one; int before(){ return later(); } int later(){return global;}");
        var enumerators = nodes(project, EnumeratorDeclaration.class);
        var zeroUse = nodes(project, NameExpression.class).stream().filter(n -> n.name().equals("zero")).findFirst().orElseThrow();
        assertSame(enumerators.getFirst(), zeroUse.target().orElseThrow());
        var oneUse = nodes(project, NameExpression.class).stream().filter(n -> n.name().equals("one")).findFirst().orElseThrow();
        assertSame(enumerators.getLast(), oneUse.target().orElseThrow());
        assertEquals(CallExpression.Resolution.UNRESOLVED, nodes(project, CallExpression.class).getFirst().resolution());
    }
    @Test void resolvingAgainDoesNotMutateOrLeakReferences() {
        Project first = resolve("namespace N { typedef int I; int f(); int f(){return 1;} } using namespace N; I value; int main(){return f();}");
        Project second = NameResolver.resolve(first);
        Set<Node> owned = new HashSet<>(nodes(second, Node.class));
        for (Node node : owned) assertTrue(owned.containsAll(node.references()));
        assertNotSame(nodes(first, CallExpression.class).getFirst().target().orElseThrow(), nodes(second, CallExpression.class).getFirst().target().orElseThrow());
        assertEquals("int", ((NamedType)nodes(first, VariableDeclaration.class).getFirst().type().orElseThrow()).canonicalIdentity());
    }
    @Test void differentNamespaceClassesRemainDifferentOverloads() {
        Project project = resolve("namespace A { class Item {}; } namespace B { class Item {}; } int f(A::Item x){return 1;} int f(B::Item x){return 2;}");
        assertFalse(error(project, "odr-conflict"));
        var types = nodes(project, ParameterDeclaration.class).stream().map(p -> p.type().orElseThrow().canonicalIdentity()).toList();
        assertNotEquals(types.get(0), types.get(1));
    }
    @Test void blockExternAndConstRedeclarationsKeepTheirExternalIdentity() {
        Project project = resolve("extern const int value; const int value = 1; int f(){ extern const int value; return value; }");
        var variables = nodes(project, VariableDeclaration.class);
        for (VariableDeclaration variable : variables) assertEquals(Declaration.Linkage.EXTERNAL, variable.linkage());
        assertSame(variables.get(1), variables.getFirst().definition().orElseThrow());
        assertSame(variables.get(1), variables.getLast().definition().orElseThrow());
        assertFalse(error(project, "odr-conflict"));
    }
    @Test void unknownCompositionCanLinkAUniqueExternalDefinitionButNotAlternativeVariants() throws Exception {
        Files.writeString(directory.resolve("main.cpp"), "extern int value; int external(); int main(){return external()+value;}");
        Files.writeString(directory.resolve("definition.cpp"), "int value=1; int external(){return 2;}");
        Project project = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        var prototype = nodes(project, FunctionDeclaration.class).stream().filter(f -> f.name().equals("external") && f.body().isEmpty()).findFirst().orElseThrow();
        assertTrue(prototype.definition().isPresent());
        var variable = nodes(project, VariableDeclaration.class).stream().filter(v -> v.modifiers().contains("extern")).findFirst().orElseThrow();
        assertTrue(variable.definition().isPresent());
    }
    @Test void sharedInlineFunctionDefinitionsAreNotOdrConflicts() throws Exception {
        Files.writeString(directory.resolve("inline.hpp"), "inline int helper(){return 1;}");
        Files.writeString(directory.resolve("one.cpp"), "#include \"inline.hpp\"\nint one(){return helper();}");
        Files.writeString(directory.resolve("two.cpp"), "#include \"inline.hpp\"\nint two(){return helper();}");
        var units = new ArrayList<Build.TranslationUnit>();
        for (String file : List.of("one.cpp", "two.cpp")) units.add(new Build.TranslationUnit(new Build.PhysicalSource(directory.resolve(file)), directory, List.of(), List.of(), "c++03"));
        Project project = Cpg.read(new Build(units, new Build.Composition("both", units.stream().map(Build.TranslationUnit::variantId).toList())), new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        assertFalse(error(project, "odr-conflict"));
        for (SourceFile file : project.files()) {
            CallExpression call = nodes(file, CallExpression.class).getFirst();
            assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
            assertTrue(nodes(file, Node.class).contains(call.target().orElseThrow()));
        }
    }
    @Test void functionTypedefDeclarationsResolveToCompatibleDefinitions() {
        Project project = resolve("typedef int Signature(int); Signature f; int f(int x){return x;} int main(){return f(1);}");
        var function = nodes(project, FunctionDeclaration.class).getFirst();
        assertTrue(function.writtenType().isPresent());
        assertTrue(function.definition().isPresent());
        assertEquals(CallExpression.Resolution.RESOLVED, nodes(project, CallExpression.class).getFirst().resolution());
        assertFalse(error(project, "conflicting-declaration"));
    }

    @Test void classCompletionWaitsForClosingBraceOutsideMemberFunctionBodies() {
        Project project = resolve("class C { int data[sizeof(C)]; int method(){ return sizeof(C); } };");
        var sizes = nodes(project, SizeofExpression.class);
        assertTrue(error(sizes.getFirst(), "incomplete-type"));
        assertFalse(error(sizes.getLast(), "incomplete-type"));
    }

    @Test void cLanguageLinkageDoesNotInfectAnUnrelatedCppOverload() {
        Project project = resolve("extern \"C\" int bridge(int); int bridge(long value){return 1;}");
        var functions = nodes(project, FunctionDeclaration.class);
        assertEquals("C", functions.getFirst().languageLinkage());
        assertEquals("C++", functions.getLast().languageLinkage());
        assertFalse(error(project, "conflicting-declaration"));
    }
    @Test void conflictingStorageLinkageIsDiagnosed() {
        assertTrue(error(resolve("extern int value; static int value;"), "conflicting-declaration"));
        assertTrue(error(resolve("int f(); static int f(){return 0;}"), "conflicting-declaration"));
        assertFalse(error(resolve("static int f(int); int f(long){return 0;}"), "conflicting-declaration"));
    }

    @Test void elaboratedIntroductionOwnsAnImplicitForwardWithDefinitionLink() {
        Project project = resolve("struct Fresh *pointer; int early(){return sizeof(struct Fresh);} struct Fresh {int value;};");
        var classes = nodes(project, ClassDeclaration.class);
        assertEquals(2, classes.size()); assertTrue(classes.getFirst().implicit());
        assertFalse(classes.getFirst().isDefinition());
        assertSame(classes.getLast(), classes.getFirst().definition().orElseThrow());
        var pointer = (PointerType)nodes(project, VariableDeclaration.class).getFirst().type().orElseThrow();
        var named = (NamedType)pointer.pointee();
        assertSame(classes.getFirst(), named.declaration().orElseThrow()); assertFalse(named.completeAtUse());
        assertTrue(error(nodes(project, SizeofExpression.class).getFirst(), "incomplete-type"));
    }

    @Test void usingDirectivesMeetAtTheirCommonEnclosingNamespace() {
        Project project = resolve("int value; namespace A {int value;} namespace B {using namespace A; int f(){return value;}}");
        assertTrue(nodes(project, NameExpression.class).stream().filter(n -> n.name().equals("value")).allMatch(n -> n.target().isEmpty()));
        assertTrue(error(project, "ambiguous-name"));
        assertFalse(error(resolve("int value; namespace A {int value;} namespace B {using namespace A; int value; int f(){return value;}}"), "ambiguous-name"));
    }
    @Test void blockDeclarationsInheritInternalAndCLanguageLinkage() {
        Project project = resolve("extern \"C\" {static int helper(){return 2;}} static int value=1; int main(){extern int value; int helper(); return value+helper();}");
        var functions = nodes(project, FunctionDeclaration.class).stream().filter(f -> f.name().equals("helper")).toList();
        assertEquals("C", functions.getLast().languageLinkage());
        assertEquals(Declaration.Linkage.INTERNAL, functions.getLast().linkage());
        assertSame(functions.getFirst(), functions.getLast().definition().orElseThrow());
        assertSame(functions.getFirst(), nodes(project, CallExpression.class).getFirst().target().orElseThrow());
        var variables = nodes(project, VariableDeclaration.class);
        assertEquals(Declaration.Linkage.INTERNAL, variables.getLast().linkage());
        assertSame(variables.getFirst(), variables.getLast().definition().orElseThrow());
    }
    @Test void functionBodyCannotRedeclareItsParameterButNestedBlockCan() {
        assertTrue(error(resolve("int f(int x){int x=1;return x;}"), "conflicting-declaration"));
        assertFalse(error(resolve("int f(int x){{int x=1;}return x;}"), "conflicting-declaration"));
    }
    @Test void qualifiedNestedTypeMayTraverseAClassTypedef() {
        Project project = resolve("struct Original {typedef int Type;}; typedef Original Alias; Alias::Type value;");
        var named = (NamedType)nodes(project, VariableDeclaration.class).getFirst().type().orElseThrow();
        assertEquals("int", named.canonicalIdentity()); assertTrue(named.alias().isPresent());
    }
    @Test void enumsRemainIncompleteDuringTheirEnumeratorInitializersAndAnonymousEnumsAreDistinct() {
        assertTrue(error(resolve("enum E {N=sizeof(E)};"), "incomplete-type"));
        Project project = resolve("enum {A=1}; enum {B=2}; int main(){return A+B;}");
        assertFalse(error(project, "odr-conflict"));
        assertEquals(2, nodes(project, NameExpression.class).stream().filter(n -> n.target().isPresent()).count());
    }
    @Test void qualifiedNamespaceValuesAndEnumeratorsBindToTheirDeclarations() {
        Project project = resolve("namespace N {int value; enum State {active};} int main(){return N::value+N::active+sizeof(N::value);}");
        var members = nodes(project, ScopeMemberExpression.class);
        assertInstanceOf(VariableDeclaration.class, members.get(0).target().orElseThrow());
        assertInstanceOf(EnumeratorDeclaration.class, members.get(1).target().orElseThrow());
        assertTrue(TypeSemantics.sizeof(nodes(project, SizeofExpression.class).getFirst(), TypeSemantics.profile(project.files().getFirst())).isPresent());
    }

    @Test void transitiveUsingDirectivesDoNotApplyQualifiedNameHiding() {
        Project project = resolve("namespace A{int x;} namespace B{using namespace A;int x;} using namespace B; int f(){return x;} int g(){return B::x;}");
        var functions = nodes(project, FunctionDeclaration.class);
        assertTrue(error(functions.getFirst(), "ambiguous-name"));
        assertFalse(error(functions.getLast(), "ambiguous-name"));
        assertInstanceOf(VariableDeclaration.class, nodes(functions.getLast(), ScopeMemberExpression.class).getFirst().target().orElseThrow());
    }
    @Test void standaloneDeclarationEditsReconnectExternalSemanticTargets() {
        Project project = resolve("extern \"C\" int helper(){return 2;} int main(){int helper(); return helper();}");
        var declarations = nodes(project, FunctionDeclaration.class).stream().filter(f -> f.name().equals("helper")).toList();
        var editor = new ProjectEditor(project);
        editor.replace(declarations.getLast(), declarations.getLast().edit().addModifier("extern").build());
        Project changed = editor.build();
        var copies = nodes(changed, FunctionDeclaration.class).stream().filter(f -> f.name().equals("helper")).toList();
        assertSame(copies.getFirst(), copies.getLast().definition().orElseThrow());
        Set<Node> owned = new HashSet<>(nodes(changed, Node.class));
        for (Node node : owned) assertTrue(owned.containsAll(node.references()));
        assertFalse(declarations.getLast().modifiers().contains("extern"));
        assertTrue(copies.getLast().modifiers().contains("extern"));
    }

    @Test void functionTypeParameterNamesStayInTheirPrototypeScope() {
        Project project = resolve("typedef int F(int hidden); int f(){return hidden;}");
        assertTrue(nodes(project, NameExpression.class).getFirst().target().isEmpty());
    }

    @Test void containingScopeEditsReconnectIncomingReferencesToRetainedDeclarations() {
        Project project = resolve("namespace N{typedef int I;} using namespace N; I x;");
        var namespace = nodes(project, NamespaceDeclaration.class).getFirst();
        var editor = new ProjectEditor(project);
        editor.replace(namespace, namespace.edit().addMember(VariableDeclaration.create().setName("y")
                .setType(NamedType.create().setName("int"))).build());
        Project changed = editor.build();
        var retainedAlias = nodes(changed, TypedefDeclaration.class).getFirst();
        var outside = nodes(changed, VariableDeclaration.class).stream().filter(v -> v.name().equals("x")).findFirst().orElseThrow();
        assertSame(retainedAlias, ((NamedType)outside.type().orElseThrow()).alias().orElseThrow());
        Set<Node> owned = new HashSet<>(nodes(changed, Node.class));
        for (Node node : owned) assertTrue(owned.containsAll(node.references()));
        assertEquals(1, nodes(project, VariableDeclaration.class).size());
        assertEquals(2, nodes(changed, VariableDeclaration.class).size());

        var unrelated = new ProjectEditor(project);
        unrelated.replace(namespace, NamespaceDeclaration.create().setName("N")
                .addMember(TypedefDeclaration.create().setName("I").setType(NamedType.create().setName("int"))).build());
        assertThrows(IllegalStateException.class, unrelated::build, "Equal names do not prove declaration identity");
    }

}
