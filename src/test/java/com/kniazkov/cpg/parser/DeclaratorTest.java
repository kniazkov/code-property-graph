/* C++03 recursive declarator and alias-identity regression tests. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeclaratorTest {
    private static SourceFile parse(String text) { return Parser.parse("types.cpp", text, Language.CPP).build(); }
    private static List<VariableDeclaration> variables(SourceFile file) {
        var result = new ArrayList<VariableDeclaration>();
        file.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration n) { result.add(n); } });
        return result;
    }
    private static TypeNode type(SourceFile file, String name) {
        return variables(file).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow().type().orElseThrow();
    }
    @Test void arraysPointersFunctionsReferencesAndMemberPointersBindFromTheName() {
        var file = parse("struct C { int field; }; int *a[3], (*b)[3], (*f)(double), (&r)[3] = b; int C::*m; int (C::*mf)(double) const; int (*table[2])(double); int (*factory())(double);");
        assertInstanceOf(PointerType.class, assertInstanceOf(ArrayType.class, type(file, "a")).elementType());
        assertInstanceOf(ArrayType.class, assertInstanceOf(PointerType.class, type(file, "b")).pointee());
        assertInstanceOf(FunctionType.class, assertInstanceOf(PointerType.class, type(file, "f")).pointee());
        assertInstanceOf(ArrayType.class, assertInstanceOf(ReferenceType.class, type(file, "r")).referredType());
        assertEquals("C", assertInstanceOf(NamedType.class, assertInstanceOf(MemberPointerType.class, type(file, "m")).ownerType()).name());
        assertTrue(assertInstanceOf(FunctionType.class, assertInstanceOf(MemberPointerType.class, type(file, "mf")).pointee()).isConst());
        assertInstanceOf(FunctionType.class, assertInstanceOf(PointerType.class, assertInstanceOf(ArrayType.class, type(file, "table")).elementType()).pointee());
        var factory = file.items().stream().filter(FunctionDeclaration.class::isInstance).map(FunctionDeclaration.class::cast).findFirst().orElseThrow();
        assertInstanceOf(FunctionType.class, assertInstanceOf(PointerType.class, factory.returnType().orElseThrow()).pointee());
        var reparsed = parse(file.source(Language.CPP));
        for (var variable : variables(file)) assertEquals(variable.type().orElseThrow().canonicalIdentity(), type(reparsed, variable.name()).canonicalIdentity());
        assertEquals(file.source(Language.CPP), file.edit().build().source(Language.CPP));
    }
    @Test void qualifiersBelongToTheirActualLayerAndTypedefNamesRemainWritten() {
        var file = parse("typedef int *Pointer; typedef Pointer Other; const int *p; int *const q = 0; const Other r = 0; volatile const int v;");
        assertInstanceOf(QualifiedType.class, assertInstanceOf(PointerType.class, type(file, "p")).pointee());
        assertInstanceOf(PointerType.class, assertInstanceOf(QualifiedType.class, type(file, "q")).baseType());
        assertEquals(type(file, "q").canonicalIdentity(), type(file, "r").canonicalIdentity());
        var named = assertInstanceOf(NamedType.class, assertInstanceOf(QualifiedType.class, type(file, "r")).baseType());
        assertEquals("Other", named.name()); assertEquals("Other", named.alias().orElseThrow().name());
        assertEquals("Pointer", assertInstanceOf(NamedType.class, named.alias().orElseThrow().type()).name());
        assertTrue(file.source(Language.CPP).contains("Other const r"));
    }
    @Test void aliasesRespectBlockParameterAndTranslationUnitScopes() {
        var file = parse("typedef int T; T a; int f(int T) { T = 2; { typedef long T; T b; } return T; } int g(){ { typedef short T; T c; } T d; return 0; } T e;");
        assertEquals("int", type(file, "a").canonicalIdentity());
        assertEquals("long", type(file, "b").canonicalIdentity());
        assertEquals("short", type(file, "c").canonicalIdentity());
        assertEquals("int", type(file, "d").canonicalIdentity());
        assertEquals("int", type(file, "e").canonicalIdentity());
        assertThrows(ParseException.class, () -> parse("int f(){ T value; }"));
    }
    @Test void abstractDeclaratorsVariadicsAndVoidParameterListsRoundtrip() {
        var file = parse("int f(int (*)[3], int (*)(double), int C::*, const int&, ...); int g(void); int h(int a[3], int callback(double));");
        var f = assertInstanceOf(FunctionDeclaration.class, file.items().getFirst());
        assertTrue(f.variadic()); assertEquals(4, f.parameters().size());
        assertTrue(f.parameters().stream().allMatch(p -> p.name().isEmpty()));
        assertTrue(assertInstanceOf(FunctionDeclaration.class, file.items().get(1)).parameters().isEmpty());
        assertEquals(file.source(Language.CPP), parse(file.source(Language.CPP)).source(Language.CPP));
    }
    @Test void fundamentalIdentityNormalizesSynonymsButNeverWidths() {
        for (String spelling : List.of("signed", "signed int", "int signed", "int"))
            assertEquals("int", NamedType.create().setName(spelling).build().canonicalIdentity());
        for (String spelling : List.of("unsigned long int", "int long unsigned", "long unsigned"))
            assertEquals("unsigned long", NamedType.create().setName(spelling).build().canonicalIdentity());
        assertNotEquals(NamedType.create().setName("long").build().canonicalIdentity(), NamedType.create().setName("int").build().canonicalIdentity());
        assertEquals(15, FundamentalKind.values().length);
        for (String spelling : List.of("long long", "unsigned signed", "int double", "long float", "unsigned bool"))
            assertThrows(ParseException.class, () -> parse(spelling + " bad;"));
    }
    @Test void sizeofTypeIdsAndExpressionOperandsOwnTraversableSyntax() {
        var file = parse("typedef long T; int f(){ int x; return sizeof(int (*)[3]) + sizeof(T) + sizeof(x) + sizeof x; }");
        var sizes = new ArrayList<SizeofExpression>();
        file.walk(new NodeVisitor() { @Override public void visit(SizeofExpression n) { sizes.add(n); } });
        assertEquals(4, sizes.size());
        assertInstanceOf(ArrayType.class, assertInstanceOf(PointerType.class, sizes.getFirst().type().orElseThrow()).pointee());
        assertEquals("long", sizes.get(1).type().orElseThrow().canonicalIdentity());
        assertTrue(sizes.get(2).operand().isPresent()); assertTrue(sizes.get(3).operand().isPresent());
        assertEquals(file.source(Language.CPP), parse(file.source(Language.CPP)).source(Language.CPP));
    }
    @Test void graphCopiesRedirectAliasReferencesAndJsonContainsDeclaratorStructure() {
        var project = Project.create().addFile(Parser.parse("types.cpp", "typedef int T; T *a[3];", Language.CPP)).build();
        var copy = project.edit().build();
        var oldAlias = assertInstanceOf(TypedefDeclaration.class, project.files().getFirst().items().getFirst());
        var alias = assertInstanceOf(TypedefDeclaration.class, copy.files().getFirst().items().getFirst());
        var use = assertInstanceOf(NamedType.class, assertInstanceOf(PointerType.class, assertInstanceOf(ArrayType.class, type(copy.files().getFirst(), "a")).elementType()).pointee());
        assertNotSame(oldAlias, alias); assertSame(alias, use.alias().orElseThrow());
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(copy));
        var replacement = TypedefDeclaration.create().setName("T").setType(NamedType.create().setName("long")).build();
        var editor = new ProjectEditor(project); editor.replace(oldAlias, replacement);
        var edited = editor.build();
        assertEquals("array(3,pointer(long))", type(edited.files().getFirst(), "a").canonicalIdentity());
        assertEquals("array(3,pointer(int))", type(project.files().getFirst(), "a").canonicalIdentity());
        String json = ProjectJson.encode(edited).toText("  ");
        assertTrue(json.contains("aliasId")); assertTrue(json.contains("elementType")); assertTrue(json.contains("bound"));
    }
    @Test void portableArrayArithmeticHasCanonicalValueButUnknownBoundsRemainExpressions() {
        var file = parse("int a[1+2], b[3], c[40000+1], d[unknown+1];");
        assertEquals(type(file, "a").canonicalIdentity(), type(file, "b").canonicalIdentity());
        assertTrue(type(file, "c").canonicalIdentity().contains("40000"));
        assertInstanceOf(AddExpression.class, assertInstanceOf(ArrayType.class, type(file, "d")).bound().orElseThrow());
    }
    @Test void initializerCopiesAndTypesNeverShareOwnedChildren() {
        var file = parse("int f(){ int a = 1, *b = 0, c[3]; return a; }");
        assertEquals(3, variables(file).size());
        var seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        file.walk(new NodeVisitor() { @Override public void visit(Node n) { assertTrue(seen.add(n)); } });
        assertFalse(variables(file).get(2).initializer().isPresent());
    }
    @Test void preservedTypeEditsReplaceTheOwningDeclaratorAndRejectSharedDeclarations() {
        var project = Project.create().addFile(Parser.parse("types.cpp", "#define N 3\nint (*p)[3];\n", Language.CPP)).build();
        var old = assertInstanceOf(PointerType.class, type(project.files().getFirst(), "p"));
        var changed = PointerType.create().setPointee(ArrayType.create().setElementType(NamedType.create().setName("long")).setBound(IntegerLiteral.create().setValue(4))).build();
        var editor = new ProjectEditor(project); editor.replace(old, changed);
        var emitted = com.kniazkov.cpg.Cpg.sources(editor.build(), Language.CPP).get("types.cpp");
        assertTrue(emitted.contains("#define N 3"));
        assertEquals("pointer(array(4,long))", type(parse(emitted), "p").canonicalIdentity());
        var shared = Project.create().addFile(Parser.parse("types.cpp", "#define N 3\nint *a, (*b)[3];\n", Language.CPP)).build();
        var sharedEditor = new ProjectEditor(shared); sharedEditor.replace(type(shared.files().getFirst(), "b"), changed);
        var failure = assertThrows(IllegalArgumentException.class, () -> com.kniazkov.cpg.Cpg.sources(sharedEditor.build(), Language.CPP));
        assertTrue(failure.getMessage().contains("shared multi-declarator"), failure.getMessage());
    }
    @Test void cppLiteralSuffixesAndNondecimalCandidatesUseTheTarget() {
        var profile = com.kniazkov.cpg.build.PlatformProfile.builder().sizeBits("int", 16).alignmentBits("int", 16).sizeBits("wchar_t", 16).alignmentBits("wchar_t", 16).wideExecutionEncoding("UTF-16").build();
        var path = java.nio.file.Path.of("types.cpp").toAbsolutePath();
        var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03").withPlatform(profile);
        var file = Parser.parse(unit, "int f(){ return sizeof(0x8000) + sizeof(70000U); }").build();
        var literals = new ArrayList<IntegerLiteral>();
        file.walk(new NodeVisitor() { @Override public void visit(IntegerLiteral n) { literals.add(n); } });
        assertEquals("unsigned int", literals.get(0).cppType().orElseThrow());
        assertEquals("unsigned long", literals.get(1).cppType().orElseThrow());
        var negative = assertInstanceOf(NegateExpression.class, ((ReturnStatement) ((FunctionDeclaration) parse("int f(){return -2147483648;}").items().getFirst()).body().orElseThrow().items().getFirst()).value().orElseThrow());
        assertEquals("long", assertInstanceOf(IntegerLiteral.class, negative.operand()).cppType().orElseThrow());
    }

    @Test void parameterArrayAdjustmentPreservesTypedefAppliedElementQualifiers() {
        var file = parse("typedef int A[3]; typedef void F(const A); typedef void G(const int*); typedef void H(int*); typedef A B[2]; typedef void I(const B); typedef void J(const int(*)[3]); const B a; const int b[2][3];");
        var aliases = new HashMap<String, String>();
        file.items().stream().filter(TypedefDeclaration.class::isInstance).map(TypedefDeclaration.class::cast)
                .forEach(t -> aliases.put(t.name(), t.type().canonicalIdentity()));
        assertEquals(aliases.get("F"), aliases.get("G"));
        assertNotEquals(aliases.get("F"), aliases.get("H"));
        assertEquals(aliases.get("I"), aliases.get("J"));
        assertEquals(type(file, "a").canonicalIdentity(), type(file, "b").canonicalIdentity());
    }

    @Test void repeatedAliasCvIsMergedAndFunctionAliasDeclarationsRetainTheirSpelling() {
        var file = parse("typedef const int C; typedef C A[3]; const A a; const int b[3]; typedef int *P; typedef const P Q; const Q p = 0; int *const q = 0;");
        assertEquals(type(file, "a").canonicalIdentity(), type(file, "b").canonicalIdentity());
        assertEquals(type(file, "p").canonicalIdentity(), type(file, "q").canonicalIdentity());
        var aliases = parse("typedef int F(double); F f; int g(){ F local; return 0; }");
        var function = assertInstanceOf(FunctionDeclaration.class, aliases.items().get(1));
        assertEquals("F", assertInstanceOf(NamedType.class, function.writtenType().orElseThrow()).name());
        assertEquals("double", function.parameters().getFirst().type().orElseThrow().canonicalIdentity());
        assertTrue(aliases.source(Language.CPP).contains("F f;"));
        assertEquals(aliases.source(Language.CPP), parse(aliases.source(Language.CPP)).source(Language.CPP));
        assertThrows(ParseException.class, () -> parse("typedef int F(double); F f { return 0; }"));
    }

    @Test void cppOnlyTypesAndLiteralsCannotLeakThroughEntryPointTranslation() {
        for (String body : List.of("int *p = 0;", "int a[3];", "unsigned int x = 0;", "int x = 1U;", "int x = sizeof(int);", "int x; int &r = x;", "bool b;", "long x;", "double x;")) {
            var project = Project.create().addFile(Parser.parse("types.cpp", "class A { public: static void main(){ " + body + " } }; int main(){ A::main(); return 0; }", Language.CPP)).build();
            assertThrows(IllegalArgumentException.class, () -> com.kniazkov.cpg.Cpg.sources(project, Language.JAVA), body);
        }
    }

    @Test void ordinaryNamesHideClassNamesInDeclarationsAndSizeofDisambiguation() {
        var file = parse("struct C {}; int f(int C){ C=1; return sizeof(C); } int g(){ int C = 0; return sizeof(C); }");
        var sizes = new ArrayList<SizeofExpression>();
        file.walk(new NodeVisitor() { @Override public void visit(SizeofExpression n) { sizes.add(n); } });
        assertEquals(2, sizes.size());
        assertTrue(sizes.stream().allMatch(s -> s.operand().isPresent() && s.type().isEmpty()));
        assertEquals(file.source(Language.CPP), parse(file.source(Language.CPP)).source(Language.CPP));
    }

    @Test void foldedSignedMinimumKeepsItsTypeWhenRegeneratedForEachTarget() {
        var defaults = com.kniazkov.cpg.build.PlatformProfile.defaults();
        var narrow = defaults.toBuilder().sizeBits("int", 16).alignmentBits("int", 16)
                .sizeBits("wchar_t", 16).alignmentBits("wchar_t", 16).wideExecutionEncoding("UTF-16").build();
        var long32 = defaults.toBuilder().sizeBits("long", 32).alignmentBits("long", 32).build();
        var path = java.nio.file.Path.of("types.cpp").toAbsolutePath();
        int index = 0;
        for (var profile : List.of(defaults, narrow, long32)) {
            String type = index == 2 ? "long" : "int";
            String maximum = profile.integerRange(type).orElseThrow().max().toString();
            String suffix = type.equals("long") ? "L" : "";
            String half = profile.integerRange(type).orElseThrow().min().divide(java.math.BigInteger.TWO).toString();
            var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03").withPlatform(profile);
            var project = Project.create().addFile(Parser.parse(unit, "#define PRESERVE 1\nint pick(int x){return 1;} int pick(long x){return 2;} int main(){return pick(" + half + suffix + " * 2" + suffix + ");}")).build();
            var folded = com.kniazkov.cpg.analysis.ConstantFolder.fold(project);
            String emitted = com.kniazkov.cpg.Cpg.sources(folded, Language.CPP).get("types.cpp");
            assertTrue(emitted.contains("((-(" + maximum + suffix + ")) - 1" + suffix + ")"), emitted);
            assertTrue(emitted.contains("#define PRESERVE 1"), emitted);
            var reparsed = Parser.parse(unit, emitted).build();
            var arguments = new ArrayList<Expression>();
            reparsed.walk(new NodeVisitor() { @Override public void visit(CallExpression call) { arguments.addAll(call.arguments()); } });
            assertEquals(type, com.kniazkov.cpg.analysis.TypeSemantics.expressionType(arguments.getFirst(), profile).orElseThrow().canonicalIdentity());
            index++;
        }
    }

}
