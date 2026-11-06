/* Printed ISO/IEC 14882:2003 nested class access regressions. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NestedAccessSemanticsTest {
    private static Project resolve(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("nested-access.cpp", source, Language.CPP)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }});
        return result;
    }
    private static List<Diagnostic> accessDiagnostics(Node root) {
        return nodes(root, Node.class).stream().flatMap(n -> n.diagnostics().stream()
                .map(d -> d.primaryRange() != null ? d : new Diagnostic(d.code(), d.severity(), d.message(),
                        n.range().orElse(null), d.relatedRanges())))
                .filter(d -> d.code().startsWith("inaccessible-")).toList();
    }
    private static Diagnostic one(Project project, String code) {
        var diagnostics = accessDiagnostics(project);
        assertEquals(List.of(code), diagnostics.stream().map(Diagnostic::code).toList());
        return diagnostics.getFirst();
    }
    private static void span(Diagnostic diagnostic, String source, String spelling) {
        var range = diagnostic.primaryRange();
        assertNotNull(range);
        int start = source.lastIndexOf(spelling);
        assertEquals(start, range.start().offset());
        assertEquals(start + spelling.length(), range.end().offset());
        assertEquals(spelling, source.substring(range.start().offset(), range.end().offset()));
        assertEquals(1, range.start().line());
        assertEquals(start + 1, range.start().column());
        assertEquals(1, range.end().line());
        assertEquals(start + spelling.length() + 1, range.end().column());
    }
    @Test void nestedMethodHasNoEnclosingPrivatePrivilege() {
        String source = "class Outer { void secret(); public: class Inner { public: void use(Outer& o) { o.secret(); } }; };";
        Project project = resolve(source);
        span(one(project, "inaccessible-overload"), source, "o.secret()");
        assertEquals("secret", nodes(project, CallExpression.class).getFirst().target().orElseThrow().name());
    }
    @Test void enclosingMethodHasNoNestedPrivatePrivilege() {
        String source = "class Outer { class Inner { void secret(); }; void use(Inner& i) { i.secret(); } };";
        Project project = resolve(source);
        span(one(project, "inaccessible-overload"), source, "i.secret()");
        assertEquals("secret", nodes(project, CallExpression.class).getFirst().target().orElseThrow().name());
    }
    @Test void nestedFieldHasNoEnclosingPrivatePrivilege() {
        String source = "class Outer { int secret; public: class Inner { public: int use(Outer& o) { return o.secret; } }; };";
        Project project = resolve(source);
        span(one(project, "inaccessible-member"), source, "o.secret");
        assertEquals("secret", ((VariableDeclaration) nodes(project, MemberExpression.class).getFirst().target().orElseThrow()).name());
    }
    @Test void enclosingFieldHasNoNestedPrivatePrivilege() {
        String source = "class Outer { class Inner { int secret; }; int use(Inner& i) { return i.secret; } };";
        Project project = resolve(source);
        span(one(project, "inaccessible-member"), source, "i.secret");
        assertEquals("secret", ((VariableDeclaration) nodes(project, MemberExpression.class).getFirst().target().orElseThrow()).name());
    }
    @Test void privateTypedefInNestedDeclaratorIsDiagnosedAndStillBound() {
        String source = "class Outer { typedef int Secret; public: class Inner { Secret value; }; };";
        Project project = resolve(source);
        span(one(project, "inaccessible-type"), source, "Secret");
        assertEquals("Secret", nodes(project, NamedType.class).stream().filter(t -> t.name().equals("Secret"))
                .findFirst().orElseThrow().alias().orElseThrow().name());
    }
    @Test void qualifiedAndUnqualifiedPrivateTypesInDeclaratorsAndTypedefs() {
        for (String declaration : List.of("Secret value;", "Secret* value;", "Secret value[2];", "typedef Secret Alias;",
                "Outer::Secret value;", "Outer::Secret* value;", "typedef Outer::Secret Alias;")) {
            String source = "class Outer { class Secret {}; public: class Inner { " + declaration + " }; };";
            Project project = resolve(source);
            Diagnostic diagnostic = one(project, "inaccessible-type");
            if (!declaration.startsWith("typedef"))
                span(diagnostic, source, declaration.contains("Outer::Secret") ? "Outer::Secret" : "Secret");
            else span(diagnostic, source, declaration.contains("Outer::Secret") ? "typedef Outer::Secret" : "typedef Secret");
            assertTrue(nodes(project, NamedType.class).stream().anyMatch(t -> t.declaration().isPresent()
                    && t.declaration().orElseThrow() instanceof ClassDeclaration c && c.name().equals("Secret")), declaration);
        }
    }
    @Test void accessDoesNotDiscardTheBestOverloadInFavorOfPublicCandidate() {
        Project project = resolve("class Outer { void secret(int); public: void secret(long); class Inner { void use(Outer& o){o.secret(1);} }; };");
        one(project, "inaccessible-overload");
        var call = nodes(project, CallExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
        assertEquals("int", call.target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
    }
    @Test void publicMembersAndTypesRemainAccessibleToNestedClass() {
        Project project = resolve("class Outer { public: typedef int Secret; int field; void method(); class Inner { Secret value; int use(Outer& o){o.method();return o.field;} }; };");
        assertEquals(List.of(), accessDiagnostics(project));
        assertTrue(nodes(project, CallExpression.class).getFirst().target().isPresent());
    }
    @Test void selfAccessAndDirectFriendFunctionRemainAllowed() {
        for (String source : List.of(
                "class Outer { typedef int Secret; int field; void method(); Secret use(){method();return field;} };",
                "class Outer { class Inner { typedef int Secret; int field; void method(); Secret use(){method();return field;} }; };",
                "class Outer { typedef int Secret; int field; void method(); friend int use(Outer& o){Outer::Secret n=0;o.method();return o.field+n;} };")) {
            Project project = resolve(source);
            assertEquals(List.of(), accessDiagnostics(project), source);
            assertTrue(nodes(project, CallExpression.class).getFirst().target().isPresent());
        }
    }
    @Test void directFriendClassCanUsePrivateMethodFieldAndType() {
        Project project = resolve("class Friend; class Outer { typedef int Secret; int field; void method(); friend class Friend; }; class Friend { Outer::Secret value; int use(Outer& o){o.method();return o.field;} };");
        assertEquals(List.of(), accessDiagnostics(project));
        assertTrue(nodes(project, CallExpression.class).getFirst().target().isPresent());
        assertTrue(nodes(project, MemberExpression.class).stream().filter(m -> m.member().equals("field")).findFirst().orElseThrow().target().isPresent());
    }
    @Test void original2003BaseClauseAndInjectedNamesRemainAccessible() {
        Project project = resolve("class C { class A {}; A *p; class B : A { A *q; B *s; }; };");
        assertEquals(List.of(), accessDiagnostics(project));
        assertTrue(nodes(project, NamedType.class).stream().filter(t -> t.name().equals("A") || t.name().equals("B"))
                .allMatch(t -> t.declaration().isPresent()));
    }
    @Test void qualifiedEnclosingNamesDoNotGetInjectedNameAccessExemption() {
        for (String name : List.of("A", "B")) {
            String source = "class C { class A {}; A *p; class B : A { A *q; B *s; C::" + name + " *r; }; };";
            Project project = resolve(source);
            span(one(project, "inaccessible-type"), source, "C::" + name);
        }
    }
    @Test void staticPrivateFieldsRemainInaccessibleWithAndWithoutQualification() {
        for (String expression : List.of("secret", "Outer::secret")) {
            String source = "class Outer { static int secret; public: class Inner { int use(){return " + expression + ";} }; };";
            Project project = resolve(source);
            span(one(project, "inaccessible-member"), source, expression);
        }
    }
    @Test void lookupChoosesTheShadowingDeclarationBeforeCheckingAccess() {
        for (String source : List.of(
                "struct Base { int field; }; class Outer : public Base { int field; public: class Inner { int use(Outer& o){return o.field;} }; };",
                "struct Base { typedef int Type; }; class Outer : public Base { typedef long Type; public: class Inner { Type value; }; };")) {
            Project project = resolve(source);
            one(project, source.contains("o.field") ? "inaccessible-member" : "inaccessible-type");
        }
        for (String source : List.of(
                "class Base { int field; }; struct Outer : Base { int field; class Inner { int use(Outer& o){return o.field;} }; };",
                "class Base { typedef int Type; }; struct Outer : Base { typedef long Type; class Inner { Type value; }; };",
                "class Outer { typedef int Secret; public: typedef Secret Alias; class Inner { Alias value; }; };")) {
            assertEquals(List.of(), accessDiagnostics(resolve(source)), source);
        }
    }
    @Test void externalFriendFunctionHasDirectAccess() {
        Project project = resolve("class Outer { typedef int Secret; int field; void method(); friend int use(Outer&); }; int use(Outer& o){Outer::Secret n=0;o.method();return o.field+n;}");
        assertEquals(List.of(), accessDiagnostics(project));
        assertTrue(nodes(project, CallExpression.class).getFirst().target().isPresent());
    }
    @Test void friendClassDoesNotGrantPrivilegeToItsNestedClass() {
        Project project = resolve("class Friend; class Outer { int field; void method(); typedef int Secret; friend class Friend; }; class Friend { class Nested { Outer::Secret value; int use(Outer& o){o.method();return o.field;} }; };");
        assertEquals(Set.of("inaccessible-type", "inaccessible-overload", "inaccessible-member"),
                new HashSet<>(accessDiagnostics(project).stream().map(Diagnostic::code).toList()));
        assertEquals(3, accessDiagnostics(project).size());
    }
    @Test void laterNestedClassCannotStealNamespaceClassFriendship() {
        String source = "class F; class Outer { friend class F; class F { int use(Outer& o){return o.secret;} }; int secret; };";
        Project project = resolve(source);
        span(one(project, "inaccessible-member"), source, "o.secret");
        assertEquals("secret", ((VariableDeclaration) nodes(project, MemberExpression.class).getFirst()
                .target().orElseThrow()).name());
    }
    @Test void inheritedInjectedNameRemainsAccessibleThroughQualifiedPublicDerivedClass() {
        Project project = resolve("class C { class A {}; public: class B : public A {}; }; C::B::A value;");
        assertEquals(List.of(), accessDiagnostics(project));
        NamedType type = nodes(project, NamedType.class).stream().filter(t -> t.name().equals("C::B::A"))
                .findFirst().orElseThrow();
        assertTrue(type.declaration().orElseThrow() instanceof ClassDeclaration c && c.name().equals("A"));
        assertTrue(type.resolutionSteps().stream().anyMatch(step -> step.kind().equals("injected-class-name")));
    }
    @Test void privateInheritanceStillBlocksQualifiedInjectedBaseName() {
        String source = "class C { class A {}; public: class B : A {}; }; C::B::A value;";
        Project project = resolve(source);
        span(one(project, "inaccessible-type"), source, "C::B::A");
        NamedType type = nodes(project, NamedType.class).stream().filter(t -> t.name().equals("C::B::A"))
                .findFirst().orElseThrow();
        assertTrue(type.declaration().orElseThrow() instanceof ClassDeclaration c && c.name().equals("A"));
        assertTrue(type.resolutionSteps().stream().anyMatch(step -> step.kind().equals("injected-class-name")));
    }
    @Test void distinctInheritedNamesStayAmbiguousInsteadOfFallingBackToOuterName() {
        for (String source : List.of(
                "struct A {}; struct L { struct A {}; }; struct R { struct A {}; }; struct D : L, R { A* p; };",
                "struct A {}; namespace x { struct A {}; } namespace y { struct A {}; } struct D : x::A, y::A { A* p; };")) {
            Project project = resolve(source);
            NamedType type = nodes(project, NamedType.class).stream().filter(t -> t.name().equals("A")).reduce((a, b) -> b).orElseThrow();
            assertTrue(type.declaration().isEmpty(), source);
            assertTrue(type.alias().isEmpty(), source);
            assertTrue(type.resolutionSteps().isEmpty(), source);
            assertEquals(List.of("ambiguous-name"), type.diagnostics().stream().map(Diagnostic::code).toList(), source);
        }
    }
    @Test void commonInjectedBaseIdentitySurvivesCopyAndRebuildWithoutOldGraphEdges() {
        Project original = resolve("struct A {}; struct L : A {}; struct R : A {}; struct D : L, R { A* p; };");
        Project copy = original.edit().build();
        Project rebuilt = NameResolver.resolve(copy);
        Set<Node> originals = Collections.newSetFromMap(new IdentityHashMap<>());
        originals.addAll(nodes(original, Node.class));
        for (Project snapshot : List.of(copy, rebuilt)) {
            Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>());
            owned.addAll(nodes(snapshot, Node.class));
            ClassDeclaration base = nodes(snapshot, ClassDeclaration.class).stream().filter(c -> c.name().equals("A"))
                    .findFirst().orElseThrow();
            assertFalse(originals.contains(base));
            assertEquals(List.of(), accessDiagnostics(snapshot));
            List<NamedType> uses = nodes(snapshot, NamedType.class).stream().filter(t -> t.name().equals("A")).toList();
            assertEquals(3, uses.size());
            for (NamedType type : uses) {
                assertSame(base, type.declaration().orElseThrow());
                assertTrue(type.diagnostics().isEmpty());
                assertTrue(type.resolutionSteps().stream().anyMatch(step -> step.kind().equals("injected-class-name")));
                for (TypeResolutionStep step : type.resolutionSteps()) {
                    assertTrue(owned.contains(step.via())); assertTrue(owned.contains(step.target()));
                    assertFalse(originals.contains(step.via())); assertFalse(originals.contains(step.target()));
                }
            }
        }
    }
    @Test void friendClassLookupIncludesUsingDeclarationsAndInheritedClasses() {
        for (String source : List.of(
                "namespace n { class F; } using n::F; class O { int x; friend class F; }; namespace n { class F { int f(O& o){return o.x;} }; }",
                "struct B { class F; }; class O : public B { int x; friend class F; }; class B::F { int f(O& o){return o.x;} };")) {
            Project project = resolve(source);
            for (Project snapshot : List.of(project, NameResolver.resolve(project.edit().build()))) {
                assertEquals(List.of(), accessDiagnostics(snapshot), source);
                MemberExpression use = nodes(snapshot, MemberExpression.class).getFirst();
                VariableDeclaration field = nodes(snapshot, VariableDeclaration.class).stream().filter(v -> v.name().equals("x"))
                        .findFirst().orElseThrow();
                assertSame(field, use.target().orElseThrow());
            }
        }
    }
    @Test void usingDirectiveDoesNotGrantNamespaceClassFriendship() {
        String source = "namespace n { class F; } using namespace n; class O { int x; friend class F; }; namespace n { class F { int f(O& o){return o.x;} }; }";
        Project project = resolve(source);
        span(one(project, "inaccessible-member"), source, "o.x");
        assertEquals(accessDiagnostics(project), accessDiagnostics(NameResolver.resolve(project.edit().build())));
    }
    @Test void sameNamedFieldWinsExpressionLookupAndCopiesKeepOwnedBindings() {
        for (String source : List.of(
                "struct S { int S; int f(){return S;} };",
                "struct S { int S; }; int f(S& s){return s.S;}",
                "struct S { int S; }; int f(class S& s){return s.S;}")) {
            Project project = resolve(source);
            VariableDeclaration originalField = nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals("S"))
                    .findFirst().orElseThrow();
            Project copy = project.edit().build();
            for (Project snapshot : List.of(project, copy, NameResolver.resolve(copy))) {
                assertEquals(List.of(), accessDiagnostics(snapshot));
                VariableDeclaration field = nodes(snapshot, VariableDeclaration.class).stream().filter(v -> v.name().equals("S"))
                        .findFirst().orElseThrow();
                if (snapshot != project) assertNotSame(originalField, field);
                Declaration target = nodes(snapshot, MemberExpression.class).isEmpty()
                        ? nodes(snapshot, NameExpression.class).stream().filter(n -> n.name().equals("S")).findFirst().orElseThrow().target().orElseThrow()
                        : nodes(snapshot, MemberExpression.class).getFirst().target().orElseThrow();
                assertSame(field, target, source);
                for (NamedType type : nodes(snapshot, NamedType.class)) {
                    if (type.name().equals("S")) assertTrue(type.declaration().orElseThrow() instanceof ClassDeclaration);
                }
            }
        }
    }
    @Test void sameNamedPrivateFieldCannotMasqueradeAsAccessibleInjectedClassName() {
        String source = "class S { int S; public: class N { int f(class S& s){return s.S;} }; };";
        Project project = resolve(source);
        span(one(project, "inaccessible-member"), source, "s.S");
        for (Project snapshot : List.of(project, NameResolver.resolve(project.edit().build()))) {
            VariableDeclaration field = nodes(snapshot, VariableDeclaration.class).stream().filter(v -> v.name().equals("S"))
                    .findFirst().orElseThrow();
            assertSame(field, nodes(snapshot, MemberExpression.class).getFirst().target().orElseThrow());
            span(one(snapshot, "inaccessible-member"), source, "s.S");
            NamedType type = nodes(snapshot, NamedType.class).stream().filter(t -> t.name().equals("S")).findFirst().orElseThrow();
            assertTrue(type.declaration().orElseThrow() instanceof ClassDeclaration);
        }
    }
    @Test void copiesRebuildAndAccessEditsRemoveStaleDiagnostics() {
        for (String member : List.of("int secret;", "void secret();", "typedef int secret;")) {
            String use = member.startsWith("int") ? "int use(Outer& o){return o.secret;}"
                    : member.startsWith("void") ? "void use(Outer& o){o.secret();}" : "secret value;";
            Project project = resolve("class Outer { " + member + " public: class Inner { " + use + " }; };");
            assertEquals(1, accessDiagnostics(project).size(), member);
            Project copy = project.edit().build();
            assertEquals(List.of(), accessDiagnostics(copy));
            Project rebuilt = NameResolver.resolve(copy);
            assertEquals(accessDiagnostics(project), accessDiagnostics(rebuilt));
            Declaration secret = nodes(project, Declaration.class).stream().filter(d -> d instanceof VariableDeclaration v && v.name().equals("secret") || d instanceof CallableDeclaration c && c.name().equals("secret") || d instanceof TypedefDeclaration t && t.name().equals("secret")).findFirst().orElseThrow();
            var replacement = secret.edit(); replacement.setAccess("public");
            var editor = new ProjectEditor(project); editor.replace(secret, replacement.build());
            assertEquals(List.of(), accessDiagnostics(NameResolver.resolve(editor.build())), member);
            assertEquals(1, accessDiagnostics(project).size(), "Editing must not mutate the source snapshot");
        }
    }
}
