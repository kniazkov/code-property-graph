/* Selected SysV unsigned bit-field allocation, never bit-field addresses. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UnnamedBitFieldLayoutTest {
    private static final String STORAGE = "x86_64-sysv-unsigned-v3";
    private static final PlatformProfile PROFILE = profile("2147483647");
    @TempDir Path temp;
    private static PlatformProfile profile(String limit) { return PlatformProfile.builder()
            .offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", limit, null, null, STORAGE).build(); }
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
    private static VariableDeclaration variable(Project p, String name) {
        return nodes(p.files().getFirst(), VariableDeclaration.class).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }
    private static void healthy(Project p) { assertFalse(p.hasErrors(), () -> p.parseProblems()+" / "+p.semanticDiagnostics()); }
    private static void value(Project p, String name, long expected) { value(p, PROFILE, name, expected); }
    private static void value(Project p, PlatformProfile profile, String name, long expected) {
        var actual = new IntegralConstants(p, profile).evaluate(variable(p, name).initializer().orElseThrow()).orElseThrow();
        assertEquals("unsigned long", actual.type().spelling()); assertEquals(BigInteger.valueOf(expected), actual.number());
    }
    private static void unsupported(Project p, PlatformProfile profile, String reason) {
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("invalid-offsetof") || d.code().equals("unsupported-offsetof")), p.semanticDiagnostics().toString());
        assertTrue(p.semanticDiagnostics().toString().contains(reason), p.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(p, profile).evaluate(variable(p, "result").initializer().orElseThrow()).isEmpty());
    }
    private static String source(String members) { return "struct S {"+members+" char tail;}; unsigned long result=__builtin_offsetof(S,tail);"; }

    private static VariableDeclaration padding(Project p) {
        return nodes(p,VariableDeclaration.class).stream().filter(v->v.name().isEmpty() && v.bitWidth().isPresent()).findFirst().orElseThrow();
    }
    private static PodLayout.Layout layout(Project p,String name) {
        var owner=nodes(p,ClassDeclaration.class).stream().filter(c->c.name().equals(name)).findFirst().orElseThrow();
        return PodLayout.layout(p,owner,PROFILE).value().orElseThrow();
    }
    private static void boundedUnsupported(Project p) {
        unsupported(p,PROFILE,"BIT_FIELD");
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-offsetof")),p.semanticDiagnostics().toString());
        assertFalse(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("invalid-offsetof")),p.semanticDiagnostics().toString());
        assertFalse(p.semanticDiagnostics().toString().contains("NOT_POD"));
    }
    @Test void closedContractPreservesEarlierIdentitiesAndSettings() {
        var hashes=Map.of("none","c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10",
                "x86_64-sysv-unsigned-v1","92bb62417af0d2d82a9e883b0fb79a2ca39cf8061be3f6f9b91804b41e855327",
                "x86_64-sysv-unsigned-v2","049c96de48f5b7425f207fdcd2f7176d3fb1c1d91de2b7e0ab9951f8f80aa412");
        for(var entry:hashes.entrySet()) {
            var old=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,entry.getKey().equals("none")?null:entry.getKey()).build();
            assertEquals(entry.getValue(),old.identityHash());
            assertEquals(old,PlatformProfile.fromSettings(old.toSettings()));
            unsupported(resolve(old,source("char first;unsigned int :0;")),old,"BIT_FIELD");
        }
        for(var model:List.of(new OffsetofModel("gcc-clang-builtin","x86_64-sysv-lp64-v1",BigInteger.ONE),
                new OffsetofModel("gcc-clang-builtin","x86_64-sysv-lp64-v1",BigInteger.ONE,null),
                new OffsetofModel("gcc-clang-builtin","x86_64-sysv-lp64-v1",BigInteger.ONE,null,null))) {
            assertNull(model.bitFieldStorage());assertFalse(model.settings().containsKey("bitFieldStorage"));
        }
        assertEquals(PROFILE,PlatformProfile.fromSettings(PROFILE.toSettings()));assertEquals(PROFILE,PROFILE.toBuilder().build());
        for(Object bad:Arrays.asList("","sysv","x86_64-sysv-unsigned-v4",3,true,null)) {
            var model=new LinkedHashMap<String,Object>(PROFILE.offsetof().orElseThrow().settings());model.put("bitFieldStorage",bad);
            assertThrows(IllegalArgumentException.class,()->PlatformProfile.fromSettings(Map.of("inherits","default","offsetof",model)));
        }
        assertThrows(IllegalArgumentException.class,()->PROFILE.toBuilder().fieldAlignment("packed1").build());
        for(String type:List.of("char","short","int","long")) {
            assertThrows(IllegalArgumentException.class,()->PROFILE.toBuilder().sizeBits(type,128).build());
            assertThrows(IllegalArgumentException.class,()->PROFILE.toBuilder().alignmentBits(type,128).build());
        }
    }
    @Test void nativeConfirmedInteriorConsecutiveAndZeroBoundaries() {
        // GCC and Clang strict C++03: offset, sizeof, alignment in bytes.
        var cases=new LinkedHashMap<String,int[]>();
        for(String type:List.of("unsigned char","unsigned short","unsigned int","unsigned long"))
            cases.put("char a;"+type+" :1;",new int[]{2,3,1});
        cases.put("char a;unsigned char :8;",new int[]{2,3,1});
        cases.put("char a;unsigned int :32;",new int[]{8,9,1});
        cases.put("char a;unsigned int :3;",new int[]{2,3,1});
        cases.put("char a;unsigned int :0;",new int[]{4,5,1});
        cases.put("char a;unsigned long :0;",new int[]{8,9,1});
        cases.put("unsigned int b:3;unsigned int :0;",new int[]{4,8,4});
        cases.put("char a;unsigned short :16;",new int[]{4,5,1});
        cases.put("char a;unsigned char :0;unsigned short :0;unsigned int :0;unsigned long :0;",new int[]{8,9,1});
        cases.put("unsigned long :0;",new int[]{0,1,1});
        cases.put("char a;unsigned long :64;",new int[]{16,17,1});
        cases.put("char a;unsigned long :3;unsigned short :0;",new int[]{2,3,1});
        for(var entry:cases.entrySet()) {
            var p=resolve(source(entry.getKey()));healthy(p);value(p,"result",entry.getValue()[0]);
            var l=layout(p,"S");assertEquals(BigInteger.valueOf(entry.getValue()[1]),l.sizeBytes());assertEquals(entry.getValue()[2]*8,l.alignmentBits());
            assertTrue(l.fields().stream().noneMatch(f->f.declaration().bitWidth().isPresent()));
        }
    }
    @Test void trailingUnionAllZeroAndNestedArrays() {
        var p=resolve("""
                struct I {char first;unsigned long :0;};
                union U {unsigned long :64;char bytes[3];};
                struct Z {unsigned long :0;}; union ZU {unsigned short :0;};
                struct S {char prefix;I items[2];U u;Z z;ZU zu;char tail;};
                unsigned long item=__builtin_offsetof(S,items[1].first);
                unsigned long inner=__builtin_offsetof(S,u.bytes[2]);
                unsigned long result=__builtin_offsetof(S,tail);
                """);
        healthy(p);value(p,"item",9);value(p,"inner",19);value(p,"result",27);
        assertEquals(BigInteger.valueOf(8),layout(p,"I").sizeBytes());
        assertEquals(BigInteger.valueOf(8),layout(p,"U").sizeBytes());
        for(String name:List.of("I","U","Z","ZU","S"))assertEquals(8,layout(p,name).alignmentBits());
        for(String name:List.of("Z","ZU"))assertEquals(BigInteger.ONE,layout(p,name).sizeBytes());
    }
    @Test void copiesJsonGreenAndFoldsRetainPositiveZeroPositiveWidthEdits() {
        var initial=resolve(source("static const int width=3;char first;unsigned long :width;"));healthy(initial);
        for(var p:List.of(initial,initial.edit().build(),NameResolver.resolve(initial.edit().build()),TypeNormalizer.green(initial),NameResolver.resolve(ConstantFolder.fold(initial)))) {
            healthy(p);value(p,"result",2);assertTrue(ProjectJson.encode(p).toText("  ").contains(STORAGE));
            var reparsed=resolve(Cpg.sources(p,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",2);
            for(int width:List.of(0,3)) {
                var edit=new ProjectEditor(p);edit.replace(variable(p,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(width).build());
                p=NameResolver.resolve(edit.build());healthy(p);value(p,"result",width==0?8:2);
                p=NameResolver.resolve(ConstantFolder.fold(p));healthy(p);value(p,"result",width==0?8:2);
            }
        }
    }
    @Test void realUnnamedTypeEditAndReverseChangeZeroBarrier() {
        var initial=resolve(source("char first;unsigned short :0;"));
        for(var p:List.of(initial,TypeNormalizer.green(initial),NameResolver.resolve(ConstantFolder.fold(initial)))) {
            healthy(p);value(p,"result",2);
            for(String type:List.of("unsigned long","unsigned short")) {
                var edit=new ProjectEditor(p);edit.replace(padding(p).type().orElseThrow(),NamedType.create().setName(type).build());
                p=NameResolver.resolve(edit.build());healthy(p);value(p,"result",type.equals("unsigned long")?8:2);
            }
        }
    }
    @Test void enumConstAndSizeofDependenciesSurviveRepeatedFolding() {
        var p=resolve("enum Width {width=3};const int count=width;"+source("char first;unsigned long :count;"));
        for(int i=0;i<3;i++){p=NameResolver.resolve(ConstantFolder.fold(p));healthy(p);value(p,"result",2);}
        var e=nodes(p,EnumeratorDeclaration.class).getFirst();var edit=new ProjectEditor(p);
        edit.replace(e.value().orElseThrow(),IntegerLiteral.create().setValue(0).build());
        var zero=NameResolver.resolve(edit.build());healthy(zero);value(zero,"result",8);
        var bounded=profile("16");
        p=resolve(bounded,"struct Bound {char bytes[8];};"+source("char first;unsigned long :sizeof(Bound);"));healthy(p);value(p,bounded,"result",2);
        for(int i=0;i<3;i++)p=NameResolver.resolve(ConstantFolder.fold(p));
        assertFalse(nodes(padding(p),SizeofExpression.class).isEmpty());edit=new ProjectEditor(p);
        edit.replace(((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
        unsupported(NameResolver.resolve(edit.build()),bounded,"SUPPORTED_OBJECT_LIMIT");
    }
    @Test void leadingPositiveAndNoNamedMemberRemainBoundedUnsupported() {
        for(String members:List.of("unsigned int :3;","unsigned long :0;unsigned int :3;","static int first;unsigned int :3;"))boundedUnsupported(resolve(source(members)));
        var p=resolve("struct I {unsigned int :3;};struct S {I i;char tail;};unsigned long result=__builtin_offsetof(S,tail);");boundedUnsupported(p);
    }
    @Test void nonpublicAndCvPaddingRemainUnsupportedNotInvalidOrNonPod() {
        for(String body:List.of("char first;private:unsigned int :3;public:char tail;",
                "char first;protected:unsigned long :0;public:char tail;",
                "char first;const unsigned int :3;char tail;","char first;volatile unsigned long :0;char tail;"))
            boundedUnsupported(resolve("struct S {"+body+"};unsigned long result=__builtin_offsetof(S,tail);"));
        for(String qualifier:List.of("const","volatile"))boundedUnsupported(resolve("typedef "+qualifier+" unsigned long A;typedef A B;"+source("char first;B :0;")));
        var p=resolve("typedef unsigned short A;typedef A B;"+source("char first;B :0;"));healthy(p);value(p,"result",2);
    }
    @Test void realCvAliasDependencyEditAndReverseSurviveTransformations() {
        var initial=resolve("typedef unsigned long A;typedef A B;"+source("char first;B :0;"));
        for(var p:List.of(initial,initial.edit().build(),TypeNormalizer.green(initial),NameResolver.resolve(ConstantFolder.fold(initial)))) {
            healthy(p);value(p,"result",8);
            var alias=nodes(p,TypedefDeclaration.class).stream().filter(t->t.name().equals("A")).findFirst().orElseThrow();
            var edit=new ProjectEditor(p);
            edit.replace(alias.type(),QualifiedType.create().setConst(true).setBaseType(NamedType.create().setName("unsigned long")).build());
            var changed=NameResolver.resolve(edit.build());boundedUnsupported(changed);
            var changedAlias=nodes(changed,TypedefDeclaration.class).stream().filter(t->t.name().equals("A")).findFirst().orElseThrow();
            assertInstanceOf(QualifiedType.class,changedAlias.type());
            var reverse=new ProjectEditor(changed);
            reverse.replace(changedAlias.type(),NamedType.create().setName("unsigned long").build());
            var restored=NameResolver.resolve(reverse.build());healthy(restored);value(restored,"result",8);
            var reparsed=resolve(Cpg.sources(restored,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",8);
        }
    }
    @Test void uncertifiedTypesWidthsAndNamedZeroStayExcluded() {
        for(String field:List.of("int :3;","signed int :3;","short :0;","long :3;","bool :1;","enum E {e};E :1;",
                "unsigned char :9;","unsigned short :17;","unsigned int :33;","unsigned long :65;","unsigned int b:0;"))boundedUnsupported(resolve(source("char first;"+field)));
        assertThrows(com.kniazkov.cpg.parser.ParseException.class,()->resolve(source("char first;unsigned long long :0;")));
        unsupported(resolve(source("char first;unsigned int :-1;")),PROFILE,"BIT_FIELD");
    }
    @Test void namedBitfieldsStillNeverAcquireAddressableOffsets() {
        var p=resolve("struct S {unsigned int b:3;unsigned long :0;char tail;};unsigned long result=__builtin_offsetof(S,b);");boundedUnsupported(p);
    }
    @Test void ceilingRepresentabilityPragmaNonPodAndCyclesRemainGuarded() {
        var small=profile("8");unsupported(resolve(small,source("char first;unsigned long :0;")),small,"SUPPORTED_OBJECT_LIMIT");
        var exact=profile("9");var p=resolve(exact,source("char first;unsigned long :0;"));healthy(p);value(p,exact,"result",8);
        unsupported(resolve(source("char first;unsigned long :0;unsigned int huge[18446744073709551615UL];")),PROFILE,"SIZE_NOT_REPRESENTABLE");
        unsupported(resolve("#pragma pack(push,1)\n"+source("char first;unsigned int :0;")+"\n#pragma pack(pop)\n"),PROFILE,"UNSUPPORTED_PRAGMA");
        unsupported(resolve("class S {unsigned int b:3;public:unsigned long :0;char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"NOT_POD");
        assertTimeoutPreemptively(Duration.ofSeconds(8),()->{var cyclic=resolve(source("char first;unsigned long :sizeof(S);"));assertTrue(cyclic.hasErrors());assertTrue(new IntegralConstants(cyclic,PROFILE).evaluate(variable(cyclic,"result").initializer().orElseThrow()).isEmpty());});
    }
}
