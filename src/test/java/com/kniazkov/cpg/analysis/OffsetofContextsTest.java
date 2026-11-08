/* Bounded offsetof contexts for concrete template instances and union sizeof. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OffsetofContextsTest {
    @TempDir Path temp;
    private static PlatformProfile profile(String limit) { return PlatformProfile.builder()
            .offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", limit).build(); }
    private static final PlatformProfile PROFILE = profile("2147483647");
    private Project resolve(String source) { return resolve(PROFILE, source); }
    private Project resolve(PlatformProfile profile, String source) {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")), temp,
                List.of(), List.of(), "c++03").withPlatform(profile);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }
        }); return result;
    }
    private static VariableDeclaration variable(Project p, String name) { return nodes(p, VariableDeclaration.class).stream()
            .filter(v -> v.name().equals(name)).findFirst().orElseThrow(); }
    private static Expression initializer(Project p, String name) { return nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals(name)&&v.initializer().isPresent()).findFirst().orElseThrow().initializer().orElseThrow(); }
    private static void healthy(Project p) { assertFalse(p.hasErrors(), () -> p.parseProblems()+" / "+p.semanticDiagnostics()); }
    private static void value(Project p, String name, long expected) {
        var result = new IntegralConstants(p,PROFILE).evaluate(initializer(p,name)).orElseThrow();
        assertEquals("unsigned long",result.type().spelling()); assertEquals(BigInteger.valueOf(expected),result.number());
    }
    @Test void concretePrimaryExplicitPartialAndNonTypeInstancesUseSelectedFields() {
        var p=resolve("""
                template<class T> struct Box {char c; T member;};
                template<> struct Box<int> {char c; double member;};
                template<class T> struct Box<T*> {char c; T* member;};
                template<class T,int N> struct Array {char c; T member[N];};
                typedef Box<short> Primary; typedef Box<int> Explicit; typedef Box<long*> Partial;
                typedef Array<int,3> Fixed; namespace api {typedef Fixed Alias;}
                unsigned long primary=__builtin_offsetof(Primary,member);
                unsigned long full=__builtin_offsetof(Explicit,member);
                unsigned long partial=__builtin_offsetof(Partial,member);
                unsigned long fixed=__builtin_offsetof(api::Alias,member[2]);
                """);
        for(var q:List.of(p,p.edit().build(),NameResolver.resolve(p.edit().build()))) {
            healthy(q); value(q,"primary",2);value(q,"full",8);value(q,"partial",8);value(q,"fixed",12);
            assertFalse(q.classTemplateInstances().isEmpty());
        }
        String source=Cpg.sources(p,Language.CPP).get("main.cpp");
        assertTrue(source.contains("api::Alias")); assertTrue(source.replace(" ","").contains("Box<short>"));
    }
    @Test void unionSizesIncludeMaxMemberTailPaddingArraysAliasesAndUnevaluatedOperands() {
        var p=resolve("""
                union U {char bytes[9]; double weight;}; typedef U Alias; typedef Alias Pair[2];
                U make(); struct S {char data[sizeof(Pair)]; int tail;};
                unsigned long tail=__builtin_offsetof(S,tail);
                unsigned long index=__builtin_offsetof(S,data[sizeof(make())-1]);
                unsigned long nested=__builtin_offsetof(S,data[__builtin_offsetof(S,tail)-1]);
                """);
        healthy(p);value(p,"tail",32);value(p,"index",15);value(p,"nested",31);
        var call=nodes(p,CallExpression.class).stream().filter(c->c.callee() instanceof NameExpression n&&n.name().equals("make")).findFirst().orElseThrow();
        assertTrue(nodes(p,SizeofExpression.class).stream().anyMatch(s->s.operand().isPresent()&&nodes(s,CallExpression.class).contains(call)));
        var standalone=nodes(p,SizeofExpression.class).getFirst();
        assertTrue(ConstantEvaluator.evaluate(standalone,PROFILE).isEmpty(),"Generic sizeof(union array) remains outside this bounded query");
    }
    @Test void namedConstantsAndEnumsAreReevaluatedWithinBudgetAfterCacheWarmup() {
        for(String declaration:List.of("const unsigned long count=sizeof(U);","enum {count=sizeof(U)};")) {
            String source="union U {char bytes[9];double d;};"+declaration+
                    "struct S {char data[count];}; unsigned long result=__builtin_offsetof(S,data[count-1]);";
            var p=resolve(source); healthy(p);
            var context=new IntegralConstants(p,PROFILE);
            var named=nodes(p,Declaration.class).stream().filter(d->d instanceof VariableDeclaration v && v.name().equals("count") || d instanceof EnumeratorDeclaration e && e.name().equals("count")).findFirst().orElseThrow();
            context.apply(named); // A failed or successful generic cache entry must not bypass the bounded context.
            assertEquals(BigInteger.valueOf(15),context.evaluate(initializer(p,"result")).orElseThrow().number());
            value(p,"result",15);
            var limited=resolve(profile("15"),source);
            assertTrue(limited.hasErrors()); assertTrue(limited.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"));
        }
    }
    @Test void boundedUnionSizeDoesNotAuthorizeGenericSizeofOrForeignSnapshotFacts() {
        var p=resolve("union U {char bytes[9];double d;}; const unsigned long n=sizeof(U); struct S {char a[n];}; unsigned long result=__builtin_offsetof(S,a[n-1]);");
        healthy(p);value(p,"result",15);
        var context=new IntegralConstants(p,PROFILE);assertTrue(context.evaluate(initializer(p,"n")).isEmpty());
        assertEquals(BigInteger.valueOf(15),context.evaluate(initializer(p,"result")).orElseThrow().number());
        assertTrue(context.evaluate(initializer(p,"n")).isEmpty(),"Bounded cache must not leak into unrelated sizeof");
        assertTrue(context.apply(variable(p,"n")).isEmpty());
        var boundedFirst=new IntegralConstants(p,PROFILE);
        assertEquals(BigInteger.valueOf(15),boundedFirst.evaluate(initializer(p,"result")).orElseThrow().number());
        assertTrue(boundedFirst.apply(variable(p,"n")).isEmpty());
        assertTrue(boundedFirst.evaluate(initializer(p,"n")).isEmpty());
        var other=resolve("int unrelated;");assertTrue(new IntegralConstants(other,PROFILE).evaluate(initializer(p,"result")).isEmpty());
    }
    @Test void recursiveAndUnsupportedDependenciesRemainConservative() {
        for(String source:List.of(
                "union U; struct S {char a[sizeof(U)];}; union U {S s;}; unsigned long result=__builtin_offsetof(S,a);",
                "template<class T> struct Box {T member;}; template<class T> unsigned long f(){return __builtin_offsetof(Box<T>,member);}",
                "template<class T> struct Missing; unsigned long result=__builtin_offsetof(Missing<int>,member);",
                "template<class T> struct Bad {typename T::missing member;}; unsigned long result=__builtin_offsetof(Bad<int>,member);")) {
            assertTimeoutPreemptively(Duration.ofSeconds(8),()->{var p=resolve(source);assertTrue(p.hasErrors(),source);});
        }
        var unsupported=resolve("struct Non {Non();int x;}; struct S {char a[8];}; unsigned long result=__builtin_offsetof(S,a[sizeof(Non)-4]);");
        assertTrue(unsupported.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-offsetof")));
    }
    @Test void foldedUnionDependencyEditsRecomputeSizeAndRespectCeiling() {
        String source="union U {char bytes[9];double d;}; const unsigned long n=sizeof(U); struct S {char a[n];int tail;}; unsigned long result=__builtin_offsetof(S,tail);";
        var p=resolve(source);healthy(p);value(p,"result",16);
        var folded=NameResolver.resolve(ConstantFolder.fold(p));healthy(folded);value(folded,"result",16);
        assertFalse(nodes(initializer(folded,"n"),SizeofExpression.class).isEmpty());
        var bound=((ArrayType)variable(folded,"bytes").type().orElseThrow()).bound().orElseThrow();
        var editor=new ProjectEditor(folded);editor.replace(bound,IntegerLiteral.create().setValue(17).build());
        var edited=NameResolver.resolve(editor.build());healthy(edited);value(edited,"result",24);
        assertTrue(Cpg.sources(edited,Language.CPP).get("main.cpp").contains("17"));
        var small=resolve(profile("24"),source);healthy(small);
        var smallFolded=NameResolver.resolve(ConstantFolder.fold(small));
        var edit=new ProjectEditor(smallFolded);edit.replace(((ArrayType)variable(smallFolded,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(25).build());
        assertTrue(NameResolver.resolve(edit.build()).semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"));
    }
    @Test void templateArgumentEditsAfterFoldingRebuildConcreteInstances() {
        var p=resolve("template<class T> struct Box {char c;T member;}; typedef Box<int> Selected; unsigned long result=__builtin_offsetof(Selected,member);");
        healthy(p);value(p,"result",4);
        var folded=NameResolver.resolve(ConstantFolder.fold(p));
        var selected=nodes(folded.files().getFirst(),TypedefDeclaration.class).stream().filter(t->t.name().equals("Selected")).findFirst().orElseThrow();
        var argument=nodes(selected,NamedType.class).stream().filter(t->t.name().equals("int")).findFirst().orElseThrow();
        var editor=new ProjectEditor(folded);editor.replace(argument,NamedType.create().setName("long").build());
        var edited=NameResolver.resolve(editor.build());healthy(edited);value(edited,"result",8);
        String generated=Cpg.sources(edited,Language.CPP).get("main.cpp");assertTrue(generated.replace(" ","").contains("Box<long>"));
        healthy(resolve(generated));value(resolve(generated),"result",8);
    }
    @Test void foldedTemplateOriginFieldEditInvalidatesInstanceLayout() {
        var p=resolve("template<class T> struct Box {char prefix;T member;}; typedef Box<int> Selected; unsigned long result=__builtin_offsetof(Selected,member);");
        healthy(p);value(p,"result",4);
        var folded=NameResolver.resolve(ConstantFolder.fold(p));
        var prefix=nodes(folded.files().getFirst(),VariableDeclaration.class).stream().filter(v->v.name().equals("prefix")).findFirst().orElseThrow();
        var editor=new ProjectEditor(folded);editor.replace(prefix.type().orElseThrow(),NamedType.create().setName("long").build());
        var edited=NameResolver.resolve(editor.build());healthy(edited);value(edited,"result",8);
        var reparsed=resolve(Cpg.sources(edited,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",8);
    }

    @Test void normalizedNonTypeArgumentsCannotHideOversizedWrittenDependencies() {
        var bounded=profile("16");
        for(String[] test:List.of(
                new String[]{"", "sizeof(Giant)-16", ""},
                new String[]{"", "", "=sizeof(Giant)-16"})) {
            String argument=test[1].isEmpty()?"":","+test[1];
            String prefix="struct Giant {char bytes[17];};"+test[0]+"template<class T,int N"+test[2]+"> struct Box {T data[N];char tail;};";
            String selected="typedef Box<char"+argument+"> Selected;";
            for(String body:List.of(selected,"Box<char,1> warmed;"+selected,selected+"Box<char,1> warmed;")) {
                var p=resolve(bounded,prefix+body+"unsigned long result=__builtin_offsetof(Selected,tail);");
                assertTrue(p.hasErrors(),prefix+body);
                assertTrue(p.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),prefix+body+" / "+p.semanticDiagnostics());
                assertTrue(new IntegralConstants(p,bounded).evaluate(initializer(p,"result")).isEmpty());
            }
        }
    }
    @Test void foldedWrittenAndDefaultTemplateArgumentsRetainCeilingDependencies() {
        var bounded=profile("16");
        for(String[] test:List.of(
                new String[]{"", "sizeof(Giant)-15", ""},
                new String[]{"", "", "=sizeof(Giant)-15"})) {
            String argument=test[1].isEmpty()?"":","+test[1];
            String source="struct Giant {char bytes[16];};"+test[0]+"template<class T,int N"+test[2]+"> struct Box {T data[N];char tail;};"
                    +"Box<char,1> warmed;typedef Box<char"+argument+"> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
            var p=resolve(bounded,source);assertFalse(p.hasErrors(),source+" / "+p.semanticDiagnostics());
            var folded=NameResolver.resolve(ConstantFolder.fold(p));healthy(folded);
            assertEquals(BigInteger.ONE,new IntegralConstants(folded,bounded).evaluate(initializer(folded,"result")).orElseThrow().number());
            assertFalse(nodes(folded.files().getFirst(),SizeofExpression.class).isEmpty(),source);
            var bytes=nodes(folded.files().getFirst(),VariableDeclaration.class).stream().filter(v->v.name().equals("bytes")).findFirst().orElseThrow();
            var editor=new ProjectEditor(folded);editor.replace(((ArrayType)bytes.type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
            var edited=NameResolver.resolve(editor.build());
            assertTrue(edited.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),source+" / "+edited.semanticDiagnostics());
        }
    }

    @Test void namedNonTypeArgumentsKeepExistingGenericInstantiationBoundaryExplicit() {
        for(String declaration:List.of("const unsigned long n=sizeof(Giant)-15;","enum {n=sizeof(Giant)-15};")) {
            String source="struct Giant {char bytes[16];};"+declaration+"template<class T,int N> struct Box {T data[N];char tail;};typedef Box<char,n> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
            var p=resolve(profile("16"),source);
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("invalid-class-template-arguments")));
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-offsetof")));
            assertTrue(new IntegralConstants(p,profile("16")).evaluate(initializer(p,"result")).isEmpty());
        }
    }
    @Test void unrelatedOversizedEnumAndConstDependenciesDoNotPoisonBoundedQueries() {
        var bounded=profile("16");
        String types="struct Giant {char bytes[17];};struct Non {Non();int x;};struct S {char c;int member;};";
        for(String declarations:List.of(
                "enum {unrelated=sizeof(Giant)};unsigned long result=__builtin_offsetof(S,member);",
                "const unsigned long unrelated=sizeof(Giant);unsigned long result=__builtin_offsetof(S,member);",
                "enum {before=sizeof(Giant),good=__builtin_offsetof(S,member)};unsigned long result=__builtin_offsetof(S,member);",
                "enum {good=__builtin_offsetof(S,member),later=sizeof(Giant)};unsigned long result=__builtin_offsetof(S,member);",
                "const unsigned long unrelated=sizeof(Non);unsigned long result=__builtin_offsetof(S,member);")) {
            var p=resolve(bounded,types+declarations);assertFalse(p.hasErrors(),declarations+" / "+p.semanticDiagnostics());
            assertEquals(BigInteger.valueOf(4),new IntegralConstants(p,bounded).evaluate(initializer(p,"result")).orElseThrow().number());
        }
    }

    private static String typeArgumentFixture(int giant, int subtract, boolean defaultArgument) {
        String argument="char[sizeof(Giant)-"+subtract+"]";
        return "struct Giant {char bytes["+giant+"];};template<class T"+(defaultArgument?"="+argument:"")
                +"> struct Box {char data;char tail;};template<> struct Box<char[1]> {long data;char tail;};"
                +"typedef Box<"+(defaultArgument?"":argument)+"> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
    }
    @Test void typeTemplateArgumentsAndDefaultsEnforceBoundedDependenciesBeforeSpecialization() {
        var bounded=profile("16");
        for(boolean defaults:List.of(false,true)) {
            var p=resolve(bounded,typeArgumentFixture(17,16,defaults));
            assertTrue(p.hasErrors());
            assertTrue(p.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,bounded).evaluate(initializer(p,"result")).isEmpty());
        }
        var pointerSyntax=resolve(bounded,"struct Giant {char bytes[17];};template<class T> struct Box {char data;char tail;};template<> struct Box<char(*)[1]> {long data;char tail;};typedef Box<char(*)[sizeof(Giant)-16]> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
        assertTrue(pointerSyntax.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),pointerSyntax.semanticDiagnostics().toString());
        for (String argument : List.of("Shape", "Shape*")) {
            var aliasSyntax=resolve(bounded,"struct Giant {char bytes[17];};typedef char Shape[sizeof(Giant)-16];template<class T> struct Box {char data;char tail;};typedef Box<"+argument+"> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
            assertTrue(aliasSyntax.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),aliasSyntax.semanticDiagnostics().toString());
        }
        var exempt=resolve(bounded,"struct Giant {char bytes[100];};template<class T> struct Box {T pointer;char tail;};typedef Box<Giant*> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
        healthy(exempt);assertEquals(BigInteger.valueOf(8),new IntegralConstants(exempt,bounded).evaluate(initializer(exempt,"result")).orElseThrow().number());
    }
    @Test void foldedTypeArgumentSyntaxPreservesSpecializationSelectionAndBudget() {
        for(boolean defaults:List.of(false,true)) for(String limit:List.of("16","64")) {
            var bounded=profile(limit);
            var p=resolve(bounded,typeArgumentFixture(16,15,defaults));healthy(p);
            assertEquals(BigInteger.valueOf(8),new IntegralConstants(p,bounded).evaluate(initializer(p,"result")).orElseThrow().number());
            var folded=NameResolver.resolve(ConstantFolder.fold(p));healthy(folded);
            assertFalse(nodes(folded.files().getFirst(),SizeofExpression.class).isEmpty(),"Type argument/default syntax must remain dependent");
            var bytes=nodes(folded.files().getFirst(),VariableDeclaration.class).stream().filter(v->v.name().equals("bytes")).findFirst().orElseThrow();
            var editor=new ProjectEditor(folded);editor.replace(((ArrayType)bytes.type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
            var edited=NameResolver.resolve(editor.build());
            if(limit.equals("16")) {
                assertTrue(edited.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),edited.semanticDiagnostics().toString());
            } else {
                healthy(edited);assertEquals(BigInteger.ONE,new IntegralConstants(edited,bounded).evaluate(initializer(edited,"result")).orElseThrow().number());
                var reparsed=resolve(bounded,Cpg.sources(edited,Language.CPP).get("main.cpp"));healthy(reparsed);
                assertEquals(BigInteger.ONE,new IntegralConstants(reparsed,bounded).evaluate(initializer(reparsed,"result")).orElseThrow().number());
            }
        }
    }

    private static String specializationHeaderFixture(boolean typeArgument, int request) {
        String parameter=typeArgument?"class T":"int N";
        String header=typeArgument?"char[sizeof(Giant)-15]":"sizeof(Giant)-15";
        String argument=typeArgument?"char["+request+"]":Integer.toString(request);
        return "struct Giant {char bytes[16];};template<"+parameter+"> struct Box {char data;char tail;};"
                +"template<> struct Box<"+header+"> {long data;char tail;};typedef Box<"+argument+"> Selected;"
                +"unsigned long result=__builtin_offsetof(Selected,tail);";
    }
    @Test void selectedAndUnselectedSpecializationHeadersSurviveFoldingAndReselectAfterEdit() {
        for(boolean typeArgument:List.of(false,true)) for(int request:List.of(1,2)) for(String limit:List.of("16","64")) {
            var bounded=profile(limit);String source=specializationHeaderFixture(typeArgument,request);
            var p=resolve(bounded,source);healthy(p);
            assertEquals(BigInteger.valueOf(request==1?8:1),new IntegralConstants(p,bounded).evaluate(initializer(p,"result")).orElseThrow().number());
            var folded=NameResolver.resolve(ConstantFolder.fold(p));healthy(folded);
            assertFalse(nodes(folded.files().getFirst(),SizeofExpression.class).isEmpty(),"Relevant family header syntax is a selection dependency even when unselected");
            var bytes=nodes(folded.files().getFirst(),VariableDeclaration.class).stream().filter(v->v.name().equals("bytes")).findFirst().orElseThrow();
            var editor=new ProjectEditor(folded);editor.replace(((ArrayType)bytes.type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
            var edited=NameResolver.resolve(editor.build());
            if(limit.equals("16")) assertTrue(edited.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),source+" / "+edited.semanticDiagnostics());
            else {
                healthy(edited);assertEquals(BigInteger.valueOf(request==1?1:8),new IntegralConstants(edited,bounded).evaluate(initializer(edited,"result")).orElseThrow().number());
                var reparsed=resolve(bounded,Cpg.sources(edited,Language.CPP).get("main.cpp"));healthy(reparsed);
                assertEquals(BigInteger.valueOf(request==1?1:8),new IntegralConstants(reparsed,bounded).evaluate(initializer(reparsed,"result")).orElseThrow().number());
            }
        }
    }
    @Test void unrelatedTemplateSpecializationHeadersDoNotConsumeTheSelectedFamilyBudget() {
        String source="struct Giant {char bytes[100];};template<class T> struct Other {char x;};"
                +"template<> struct Other<char[sizeof(Giant)-99]> {long x;};"
                +"template<class T> struct Box {char data;char tail;};typedef Box<int> Selected;"
                +"unsigned long result=__builtin_offsetof(Selected,tail);";
        var bounded=profile("16");var p=resolve(bounded,source);healthy(p);
        assertEquals(BigInteger.ONE,new IntegralConstants(p,bounded).evaluate(initializer(p,"result")).orElseThrow().number());
        var folded=NameResolver.resolve(ConstantFolder.fold(p));healthy(folded);
        assertEquals(BigInteger.ONE,new IntegralConstants(folded,bounded).evaluate(initializer(folded,"result")).orElseThrow().number());
    }

}
