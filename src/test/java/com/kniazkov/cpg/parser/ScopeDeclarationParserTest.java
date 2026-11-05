/* C++03 declaration grammar and immutable model regression tests. MIT License. */
package com.kniazkov.cpg.parser;
import com.kniazkov.cpg.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScopeDeclarationParserTest {
    private SourceFile parse(String source) { return Parser.parse("scopes.cpp", source, Language.CPP).build(); }
    private <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (kind.isInstance(node)) result.add(kind.cast(node)); } });
        return result;
    }
    @Test void reopenedNamespacesAliasesAndUsingFeedParserTypeEnvironment() {
        var file = parse("namespace shop { typedef long Raw; typedef Raw Id; } namespace shop { typedef int Count; } namespace market=shop; using namespace shop; Id id; Count count; shop::Id qualified; market::Count aliased; using shop::Raw; Raw raw;");
        assertEquals(2, nodes(file, NamespaceDeclaration.class).size());
        assertEquals(1, nodes(file, NamespaceAliasDeclaration.class).size());
        assertEquals(1, nodes(file, UsingDirective.class).size());
        assertEquals(1, nodes(file, UsingDeclaration.class).size());
        assertEquals(List.of("long", "int", "long", "int", "long"), nodes(file, VariableDeclaration.class).stream().map(v -> v.type().orElseThrow().canonicalIdentity()).toList());
    }
    @Test void declarationPreferenceUsesActualTypeNamesAndShadowing() {
        var file = parse("typedef int T; typedef int U; int g(){ T(a); T x(U()); { int T=0; T(a); } T(b); return a; }");
        var function = nodes(file, FunctionDeclaration.class).getFirst();
        var items = function.body().orElseThrow().items();
        assertEquals("a", assertInstanceOf(VariableDeclaration.class, items.get(0)).name());
        var vexing = assertInstanceOf(FunctionDeclaration.class, items.get(1));
        assertEquals("x", vexing.name());
        assertInstanceOf(FunctionType.class, vexing.parameters().getFirst().type().orElseThrow());
        var inner = assertInstanceOf(BlockStatement.class, items.get(2));
        assertInstanceOf(CallExpression.class, assertInstanceOf(ExpressionStatement.class, inner.items().get(1)).expression());
        assertEquals("b", assertInstanceOf(VariableDeclaration.class, items.get(3)).name());
    }
    @Test void functionNamesAndTypeNamesChooseDifferentValidStatementGrammars() {
        var file = parse("int T(int a){ return a; } int f(){ int a=1; T(a); { typedef int T; T(b); b=2; } return a; }");
        var function = nodes(file, FunctionDeclaration.class).get(1);
        var items = function.body().orElseThrow().items();
        assertInstanceOf(CallExpression.class, assertInstanceOf(ExpressionStatement.class, items.get(1)).expression());
        var local = assertInstanceOf(BlockStatement.class, items.get(2));
        assertEquals("b", assertInstanceOf(VariableDeclaration.class, local.items().get(1)).name());
    }
    @Test void localImportsAndNestedTypesDoNotLeakToOuterScopes() {
        var file = parse("namespace n { typedef short T; } class Outer { public: typedef long Id; class Inner; Inner *p; class Inner { int n; }; }; Outer::Id id; Outer::Inner *inner; int f(){ using n::T; T x; { typedef int T; T y; } T z; return 0; }");
        var variables = nodes(file, VariableDeclaration.class);
        assertEquals("long", variables.stream().filter(v -> v.name().equals("id")).findFirst().orElseThrow().type().orElseThrow().canonicalIdentity());
        assertEquals("short", variables.stream().filter(v -> v.name().equals("z")).findFirst().orElseThrow().type().orElseThrow().canonicalIdentity());
        assertFalse(nodes(file, ClassDeclaration.class).get(1).isDefinition());
        assertTrue(nodes(file, ClassDeclaration.class).get(2).isDefinition());
        assertThrows(ParseException.class, () -> parse("namespace n { typedef int T; } int f(){ using n::T; T x; } T leaked;"));
    }
    @Test void storageClassesLinkageAndDefinitionOccurrencesSurviveEdit() {
        var file = parse("extern int x; static int y; extern int z=1; extern \"C\" int c; extern \"C\" { int d; extern int e; int api(register int p); } class C { static int s; mutable int m; }; int f(){ auto int a; register int b; static int c; extern int d; return 0; }");
        var variables = nodes(file, VariableDeclaration.class);
        assertFalse(variables.get(0).isDefinition()); assertTrue(variables.get(1).isDefinition());
        assertTrue(variables.get(2).isDefinition()); assertFalse(variables.get(3).isDefinition());
        assertEquals("C", variables.get(3).languageLinkage()); assertTrue(variables.get(4).isDefinition());
        assertFalse(variables.get(5).isDefinition()); assertFalse(variables.get(6).isDefinition());
        assertEquals(List.of("mutable"), variables.get(7).modifiers());
        assertEquals(List.of("register"), nodes(file, ParameterDeclaration.class).getFirst().modifiers());
        assertEquals(file.source(Language.CPP), file.edit().build().source(Language.CPP));
        for (String bad : List.of("auto int x;", "register int x;", "mutable int x;", "static extern int x;", "inline int x;", "int f(static int p);"))
            assertThrows(ParseException.class, () -> parse(bad), bad);
    }
    @Test void elaboratedTypeNamesRetainTheirKeywordAndDoNotUnhideOrdinaryNames() {
        var file = parse("struct Tag {}; int Tag; struct Tag *p; int f(){ int a=1; Tag=a; return 0; }");
        var named = assertInstanceOf(NamedType.class, assertInstanceOf(PointerType.class, nodes(file, VariableDeclaration.class).get(1).type().orElseThrow()).pointee());
        assertEquals("struct", named.elaboratedKeyword().orElseThrow());
        assertInstanceOf(AssignmentExpression.class, nodes(file, ExpressionStatement.class).getFirst().expression());
        assertTrue(file.source(Language.CPP).contains("struct Tag *p"));
    }
    @Test void elaboratedFirstUseOwnsAnImplicitForwardDeclarationAtTheRightScope() {
        var file = parse("struct First *p; int f(struct Param *p); Param *q; int g(){ int n=sizeof(struct Local*); Local *p; return sizeof(struct Other*); }");
        var classes = nodes(file, ClassDeclaration.class);
        assertEquals(List.of("First", "Param", "Local", "Other"), classes.stream().map(ClassDeclaration::name).toList());
        assertTrue(classes.stream().allMatch(c -> c.implicit() && !c.isDefinition()));
        assertInstanceOf(ClassDeclaration.class, file.items().getFirst());
        var g = nodes(file, FunctionDeclaration.class).getLast();
        assertInstanceOf(ClassDeclaration.class, g.body().orElseThrow().items().getFirst());
        assertThrows(ParseException.class, () -> parse("enum Unknown *bad;"));
    }
    @Test void classTypedefsCanQualifyNestedTypesAndAbsoluteValueNames() {
        var file = parse("struct Original; typedef Original Alias; struct Original { typedef int Type; }; typedef Alias Other; Other::Type value; int f(){ return ::value; }");
        assertEquals("int", nodes(file, VariableDeclaration.class).getFirst().type().orElseThrow().canonicalIdentity());
        assertEquals("::value", nodes(file, NameExpression.class).getFirst().name());
        assertEquals(file.source(Language.CPP), parse(file.source(Language.CPP)).source(Language.CPP));
    }
    @Test void inlineBodiesSeeTheCompleteEnclosingClassTypeScope() {
        var file = parse("struct Outer { int f(){ T x; return x; } struct Inner { int g(){ T y; return y; } }; typedef int T; };");
        assertEquals(2, nodes(file, VariableDeclaration.class).size());
        assertTrue(nodes(file, VariableDeclaration.class).stream().allMatch(v -> v.type().orElseThrow().canonicalIdentity().equals("int")));
        assertEquals(file.source(Language.CPP), parse(file.source(Language.CPP)).source(Language.CPP));
    }
    @Test void elaboratedTagsInsideClassesBelongToTheEnclosingNonClassScope() {
        var file = parse("struct Container { struct Field *p; int f(struct Argument*); }; Field *field; Argument *arg;");
        assertEquals(List.of("Field", "Argument", "Container"), nodes(file, ClassDeclaration.class).stream().map(ClassDeclaration::name).toList());
        var container = assertInstanceOf(ClassDeclaration.class, file.items().get(2));
        assertFalse(container.members().stream().anyMatch(ClassDeclaration.class::isInstance));
        assertEquals(3, file.items().stream().filter(ClassDeclaration.class::isInstance).count());
    }
    @Test void delayedInlineBodiesStillRetainRecoveredAndTruncatedPrefixes() {
        for (String tail : List.of("@ } };", "\"unfinished", "")) {
            var file = Parser.parseRecovering("broken.cpp", "struct C { int f(){ int kept=2; int bad=; " + tail, Language.CPP).build();
            assertEquals(List.of("kept", "bad"), nodes(file, VariableDeclaration.class).stream().map(VariableDeclaration::name).toList());
            assertTrue(file.hasErrors());
            assertTrue(file.stoppedAt().isPresent());
        }
    }
    @Test void enumsPreserveExplicitValuesAndAnonymousForms() {
        var file = parse("enum State { start, next=start+2 }; enum { limit=4 }; State state; int f(){ enum Local { small=1, large=small+1 }; Local value; return large; }");
        var enums = nodes(file, EnumDeclaration.class);
        assertEquals(3, enums.size()); assertEquals("", enums.get(1).name());
        assertInstanceOf(AddExpression.class, enums.getFirst().enumerators().get(1).value().orElseThrow());
        assertEquals(4, assertInstanceOf(IntegerLiteral.class, enums.get(1).enumerators().getFirst().value().orElseThrow()).value());
        assertThrows(ParseException.class, () -> parse("enum E { a, };"));
    }
    @Test void lookupEdgesCopySharedImportsAndForwardCyclesWithoutOwningThem() {
        var forward = ClassDeclaration.create().setName("Order"); forward.setDeclarationDefinition(false);
        var definition = ClassDeclaration.create().setName("Order"); forward.setDefinition(definition); definition.setPreviousDeclaration(forward);
        var namespace = NamespaceDeclaration.create().setName("shop");
        var directive = UsingDirective.create().setNamespaceName("shop").setTarget(namespace);
        var a = NamedType.create().setName("Order").setDeclaration(forward).setCompleteAtUse(false).addResolutionStep("using-directive", directive, forward);
        var b = NamedType.create().setName("Order").setDeclaration(forward).addResolutionStep("using-directive", directive, forward);
        var project = Project.create().addFile(SourceFile.create().setPath("scopes.cpp").setLanguage(Language.CPP).addItem(namespace).addItem(directive).addItem(forward).addItem(definition)
                .addItem(VariableDeclaration.create().setName("a").setType(PointerType.create().setPointee(a)))
                .addItem(VariableDeclaration.create().setName("b").setType(PointerType.create().setPointee(b)))).build();
        var copy = project.edit().build(); var types = nodes(copy, NamedType.class); var copiedForward = nodes(copy, ClassDeclaration.class).getFirst();
        assertSame(types.get(0).resolutionSteps().getFirst().via(), types.get(1).resolutionSteps().getFirst().via());
        assertSame(copiedForward, types.getFirst().declaration().orElseThrow());
        assertSame(copiedForward, copiedForward.definition().orElseThrow().previousDeclaration().orElseThrow());
        assertFalse(types.getFirst().completeAtUse()); assertTrue(types.getFirst().children().isEmpty());
    }
}
