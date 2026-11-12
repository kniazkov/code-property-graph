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

class ShortLongBitFieldLayoutTest {
    private static final String STORAGE = "x86_64-sysv-unsigned-v2";
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

    @Test void optionalClosedContractPreservesAllEarlierApisAndIdentity() {
        var old = PlatformProfile.builder().offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "2147483647").build();
        assertEquals("c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10", old.identityHash());
        for (var model : List.of(new OffsetofModel("gcc-clang-builtin","x86_64-sysv-lp64-v1",BigInteger.ONE),
                new OffsetofModel("gcc-clang-builtin","x86_64-sysv-lp64-v1",BigInteger.ONE,null),
                new OffsetofModel("gcc-clang-builtin","x86_64-sysv-lp64-v1",BigInteger.ONE,null,null))) {
            assertNull(model.bitFieldStorage()); assertFalse(model.settings().containsKey("bitFieldStorage"));
        }
        var v1=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,"x86_64-sysv-unsigned-v1").build();
        assertEquals("92bb62417af0d2d82a9e883b0fb79a2ca39cf8061be3f6f9b91804b41e855327",v1.identityHash());
        for(String type:List.of("unsigned short","unsigned long")) unsupported(resolve(v1,source(type+" b:1;")),v1,"BIT_FIELD");
        assertNotEquals(v1.identityHash(),PROFILE.identityHash());
        assertNotEquals(old.identityHash(), PROFILE.identityHash());
        assertEquals(PROFILE, PlatformProfile.fromSettings(PROFILE.toSettings()));
        assertEquals(PROFILE, PROFILE.toBuilder().build());
        assertEquals(STORAGE, PROFILE.offsetof().orElseThrow().bitFieldStorage());
        assertEquals(old, PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build());
        var reset = new LinkedHashMap<>(PROFILE.toSettings()); reset.put("offsetof", Map.of());
        assertTrue(PlatformProfile.fromSettings(reset).offsetof().isEmpty());
        for (Object bad : Arrays.asList("", "sysv", "msvc", "x86_64-sysv-unsigned-v4", 8, true, List.of(STORAGE), null)) {
            var model = new LinkedHashMap<String,Object>(PROFILE.offsetof().orElseThrow().settings()); model.put("bitFieldStorage", bad);
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(Map.of("inherits","default","offsetof",model)));
        }
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().fieldAlignment("packed1").build());
        for(String type:List.of("short","long")) {
            assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().sizeBits(type,32).build());
            assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().alignmentBits(type,32).build());
        }
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().sizeBits("int",16).build());
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().alignmentBits("int",16).build());
    }
    @Test void shortAndLongFieldsUseDeclaredBoundariesNotWholeUnits() {
        for (var entry : Map.ofEntries(Map.entry("unsigned short b:1;",1), Map.entry("unsigned short b:16;",2),
                Map.entry("char prefix; unsigned short b:8;",2), Map.entry("char prefix; unsigned short b:9;",4),
                Map.entry("unsigned short b:15; unsigned short c:2;",3), Map.entry("unsigned long b:1;",1),
                Map.entry("unsigned long b:64;",8), Map.entry("char prefix; unsigned long b:56;",8),
                Map.entry("char prefix; unsigned long b:57;",16), Map.entry("unsigned long b:63; unsigned short c:2;",9),
                Map.entry("unsigned short b:15; unsigned long c:2;",3),
                Map.entry("unsigned char b:7;unsigned short c:10;unsigned int d:20;unsigned long e:40;",13),
                Map.entry("unsigned long b:31;unsigned int c:2;unsigned char d:7;unsigned short e:9;",8)).entrySet()) {
            var p = resolve(source(entry.getKey())); healthy(p); value(p,"result",entry.getValue());
            var owner = nodes(p,ClassDeclaration.class).stream().filter(c -> c.name().equals("S")).findFirst().orElseThrow();
            var layout = PodLayout.layout(p,owner,PROFILE).value().orElseThrow();
            int bytes=entry.getKey().contains("unsigned long")?8:2;
            assertEquals(bytes*8,layout.alignmentBits());
            assertEquals(BigInteger.valueOf((entry.getValue()+bytes)/bytes*bytes),layout.sizeBytes());
            assertTrue(layout.fields().stream().noneMatch(f -> f.declaration().bitWidth().isPresent()));
        }
    }
    @Test void aliasesCvUnionsArraysNestedOffsetsAndBoundedSizeof() {
        var p = resolve("""
                typedef volatile unsigned short Byte; typedef const unsigned long UInt;
                struct A {Byte b:3; char tail;}; union U {UInt b:3; char bytes[3];};
                struct Outer {char first; A array[2]; U u; char tail;};
                struct Sized {char bytes[sizeof(Outer)]; char tail;};
                unsigned long small=__builtin_offsetof(A,tail);
                unsigned long item=__builtin_offsetof(Outer,array[1].tail);
                unsigned long inner=__builtin_offsetof(Outer,u.bytes[2]);
                unsigned long result=__builtin_offsetof(Outer,tail);
                unsigned long size=__builtin_offsetof(Sized,tail);
                """);
        healthy(p); value(p,"small",1); value(p,"item",5); value(p,"inner",10); value(p,"result",16); value(p,"size",24);
    }
    @Test void copiesFoldingGreenSourceAndJsonRetainAllocationAndWidthEdits() {
        var initial = resolve(source("const static int width=3; unsigned long b:width;")); healthy(initial);
        for (var p : List.of(initial, initial.edit().build(), NameResolver.resolve(initial.edit().build()),
                TypeNormalizer.green(initial), NameResolver.resolve(ConstantFolder.fold(initial)))) {
            healthy(p); value(p,"result",1);
            assertTrue(ProjectJson.encode(p).toText("  ").contains("bitFieldStorage"));
            var reparsed = resolve(Cpg.sources(p,Language.CPP).get("main.cpp")); healthy(reparsed); value(reparsed,"result",1);
            var edit = new ProjectEditor(p); edit.replace(variable(p,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(64).build());
            var changed = NameResolver.resolve(edit.build()); healthy(changed); value(changed,"result",8);
            assertTrue(Cpg.sources(changed,Language.CPP).get("main.cpp").contains("width"));
            changed=NameResolver.resolve(ConstantFolder.fold(changed));
            var reverse=new ProjectEditor(changed); reverse.replace(variable(changed,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(1).build());
            var restored=NameResolver.resolve(reverse.build()); healthy(restored); value(restored,"result",1);
        }
    }
    @Test void realTypeEditChangesAggregateAlignmentAndArrayStride() {
        var initial=resolve("struct Inner {unsigned short b:3;char tail;}; struct S {char c; Inner values[2];char tail;}; unsigned long result=__builtin_offsetof(S,tail);");
        for (var p : List.of(initial,NameResolver.resolve(ConstantFolder.fold(initial)),TypeNormalizer.green(initial))) {
            healthy(p);value(p,"result",6);
            var edit=new ProjectEditor(p);edit.replace(variable(p,"b").type().orElseThrow(),NamedType.create().setName("unsigned long").build());
            var changed=NameResolver.resolve(edit.build());healthy(changed);value(changed,"result",24);
            changed=NameResolver.resolve(ConstantFolder.fold(changed));
            var reverse=new ProjectEditor(changed); reverse.replace(variable(changed,"b").type().orElseThrow(),NamedType.create().setName("unsigned short").build());
            var restored=NameResolver.resolve(reverse.build());healthy(restored);value(restored,"result",6);
        }
    }
    @Test void enumAndConstWidthDependenciesSurviveRepeatedFoldAndNestedEdits() {
        var p=resolve("enum Width { width=3 }; const unsigned int count=width+1; struct I {unsigned long b:count;char tail;}; struct S {I items[2];char tail;}; unsigned long result=__builtin_offsetof(S,tail);");
        for(int cycle=0;cycle<3;cycle++) {
            p=NameResolver.resolve(ConstantFolder.fold(p)); healthy(p); value(p,"result",16);
        }
        var enumerator=nodes(p,EnumeratorDeclaration.class).stream().filter(e->e.name().equals("width")).findFirst().orElseThrow();
        var edit=new ProjectEditor(p);edit.replace(enumerator.value().orElseThrow(),IntegerLiteral.create().setValue(63).build());
        var changed=NameResolver.resolve(edit.build());healthy(changed);value(changed,"result",32);
        var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",32);
    }
    @Test void unselectedContractAndAllUncertifiedCategoriesStayUnsupported() {
        var old=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
        unsupported(resolve(old,source("unsigned int b:3;")),old,"BIT_FIELD");
        for(String member:List.of("unsigned int :3;", "unsigned int :0;", "unsigned int b:0;", "unsigned int b:33;",
                "unsigned char b:9;", "int b:3;", "signed int b:3;", "short b:3;", "signed short b:3;", "long b:3;", "signed long b:3;", "bool b:1;", "unsigned short b:17;", "unsigned long b:65;",
                "enum E {a,b}; E bits:2;", "unsigned int b:-1;", "int width; unsigned int b:width;"))
            unsupported(resolve(source(member)),PROFILE,"BIT_FIELD");
    }
    @Test void longLongRemainsOutsideStrictCpp03() {
        assertThrows(com.kniazkov.cpg.parser.ParseException.class,()->resolve(source("unsigned long long b:3;")));
    }
    @Test void oversizedValidBitfieldsStayUnsupportedRatherThanInvalid() {
        for(String member:List.of("unsigned short b:17;","unsigned long b:65;")) {
            var p=resolve(source(member));unsupported(p,PROFILE,"BIT_FIELD");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-offsetof")));
            assertFalse(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("invalid-offsetof")));
        }
    }
    @Test void unnamedFieldsDoNotBecomePrivateNonPodMembers() {
        for(String declaration:List.of("class S {unsigned short :3;public:char tail;};",
                "class S {unsigned long :0;public:char tail;};",
                "struct S {private:unsigned long :3;public:char tail;};")) {
            var p=resolve(declaration+"unsigned long result=__builtin_offsetof(S,tail);");
            unsupported(p,PROFILE,"BIT_FIELD");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-offsetof")));
            assertFalse(p.semanticDiagnostics().toString().contains("NOT_POD"));
        }
    }
    @Test void bitFieldsNeverAcquireMemberDesignatorOffsets() {
        for(String designator:List.of("b","inner.b")) {
            var p=resolve("struct I {unsigned short b:3;char tail;};struct S {unsigned long b:3;I inner;};unsigned long result=__builtin_offsetof(S,"+designator+");");
            unsupported(p,PROFILE,"BIT_FIELD");
        }
    }
    @Test void ceilingAndRepresentabilityIncludePartialUnitAndTailPadding() {
        unsupported(resolve(profile("7"),source("unsigned long b:1;")),profile("7"),"SUPPORTED_OBJECT_LIMIT");
        var exact=resolve(profile("8"),source("unsigned long b:1;"));healthy(exact);value(exact,profile("8"),"result",1);
        unsupported(resolve("struct S {unsigned long b:1;unsigned int huge[18446744073709551615UL];char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"SIZE_NOT_REPRESENTABLE");
    }
    @Test void widthSizeofDependenciesRemainGuardedAfterFoldingAndEdits() {
        var bounded=profile("16");
        var p=resolve(bounded,"struct Bound {char bytes[8];};struct S {unsigned long b:sizeof(Bound);char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        healthy(p);value(p,bounded,"result",1);
        var folded=NameResolver.resolve(ConstantFolder.fold(p));healthy(folded);
        assertFalse(nodes(variable(folded,"b"),SizeofExpression.class).isEmpty());
        var edit=new ProjectEditor(folded);
        edit.replace(((ArrayType)variable(folded,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
        unsupported(NameResolver.resolve(edit.build()),bounded,"SUPPORTED_OBJECT_LIMIT");
    }
    @Test void pragmasNonPodsAndCyclesRemainGuarded() {
        unsupported(resolve("#pragma pack(push,1)\n"+source("unsigned short b:3;")+"\n#pragma pack(pop)\n"),PROFILE,"UNSUPPORTED_PRAGMA");
        unsupported(resolve("class S {unsigned long b:3;public:char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"NOT_POD");
        assertTimeoutPreemptively(Duration.ofSeconds(8),()->{
            var p=resolve(source("unsigned long b:sizeof(S);"));
            assertTrue(p.hasErrors());assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        });
    }
}
