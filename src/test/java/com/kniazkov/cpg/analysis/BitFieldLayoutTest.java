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

class BitFieldLayoutTest {
    private static final String STORAGE = "x86_64-sysv-unsigned-v1";
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
        assertNotEquals(old.identityHash(), PROFILE.identityHash());
        assertEquals(PROFILE, PlatformProfile.fromSettings(PROFILE.toSettings()));
        assertEquals(PROFILE, PROFILE.toBuilder().build());
        assertEquals(STORAGE, PROFILE.offsetof().orElseThrow().bitFieldStorage());
        assertEquals(old, PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build());
        var reset = new LinkedHashMap<>(PROFILE.toSettings()); reset.put("offsetof", Map.of());
        assertTrue(PlatformProfile.fromSettings(reset).offsetof().isEmpty());
        for (Object bad : Arrays.asList("", "sysv", "msvc", 8, true, List.of(STORAGE), null)) {
            var model = new LinkedHashMap<String,Object>(PROFILE.offsetof().orElseThrow().settings()); model.put("bitFieldStorage", bad);
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(Map.of("inherits","default","offsetof",model)));
        }
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().fieldAlignment("packed1").build());
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().sizeBits("int",16).build());
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().alignmentBits("int",16).build());
    }
    @Test void shortFieldsShareBytesWithoutConsumingWholeUnderlyingUnit() {
        for (var entry : Map.of("unsigned int b:1;",1, "char prefix; unsigned int b:1;",2,
                "unsigned int b:7; unsigned char c:2;",2, "unsigned char b:7; unsigned int c:2;",2,
                "unsigned int b:31; unsigned int c:2;",5, "char prefix; unsigned int b:24;",4,
                "char prefix; unsigned int b:25;",8, "unsigned int b:32;",4).entrySet()) {
            var p = resolve(source(entry.getKey())); healthy(p); value(p,"result",entry.getValue());
            var owner = nodes(p,ClassDeclaration.class).stream().filter(c -> c.name().equals("S")).findFirst().orElseThrow();
            var layout = PodLayout.layout(p,owner,PROFILE).value().orElseThrow();
            assertEquals(32,layout.alignmentBits());
            assertEquals(BigInteger.valueOf((entry.getValue()+4)/4*4),layout.sizeBytes());
            assertTrue(layout.fields().stream().noneMatch(f -> f.declaration().bitWidth().isPresent()));
        }
    }
    @Test void aliasesCvUnionsArraysNestedOffsetsAndBoundedSizeof() {
        var p = resolve("""
                typedef unsigned char Byte; typedef const unsigned int UInt;
                struct A {Byte b:3; char tail;}; union U {UInt b:3; char bytes[3];};
                struct Outer {char first; A array[2]; U u; char tail;};
                struct Sized {char bytes[sizeof(Outer)]; char tail;};
                unsigned long small=__builtin_offsetof(A,tail);
                unsigned long item=__builtin_offsetof(Outer,array[1].tail);
                unsigned long inner=__builtin_offsetof(Outer,u.bytes[2]);
                unsigned long result=__builtin_offsetof(Outer,tail);
                unsigned long size=__builtin_offsetof(Sized,tail);
                """);
        healthy(p); value(p,"small",1); value(p,"item",4); value(p,"inner",10); value(p,"result",12); value(p,"size",16);
    }
    @Test void copiesFoldingGreenSourceAndJsonRetainAllocationAndWidthEdits() {
        var initial = resolve(source("const static int width=3; unsigned int b:width;")); healthy(initial);
        for (var p : List.of(initial, initial.edit().build(), NameResolver.resolve(initial.edit().build()),
                TypeNormalizer.green(initial), NameResolver.resolve(ConstantFolder.fold(initial)))) {
            healthy(p); value(p,"result",1);
            assertTrue(ProjectJson.encode(p).toText("  ").contains("bitFieldStorage"));
            var reparsed = resolve(Cpg.sources(p,Language.CPP).get("main.cpp")); healthy(reparsed); value(reparsed,"result",1);
            var edit = new ProjectEditor(p); edit.replace(variable(p,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(32).build());
            var changed = NameResolver.resolve(edit.build()); healthy(changed); value(changed,"result",4);
            assertTrue(Cpg.sources(changed,Language.CPP).get("main.cpp").contains("width"));
        }
    }
    @Test void realTypeEditChangesAggregateAlignmentAndArrayStride() {
        var initial=resolve("struct Inner {unsigned char b:3;char tail;}; struct S {char c; Inner values[2];char tail;}; unsigned long result=__builtin_offsetof(S,tail);");
        for (var p : List.of(initial,NameResolver.resolve(ConstantFolder.fold(initial)),TypeNormalizer.green(initial))) {
            healthy(p);value(p,"result",5);
            var edit=new ProjectEditor(p);edit.replace(variable(p,"b").type().orElseThrow(),NamedType.create().setName("unsigned int").build());
            var changed=NameResolver.resolve(edit.build());healthy(changed);value(changed,"result",12);
        }
    }
    @Test void enumAndConstWidthDependenciesSurviveRepeatedFoldAndNestedEdits() {
        var p=resolve("enum Width { width=3 }; const unsigned int count=width+1; struct I {unsigned int b:count;char tail;}; struct S {I items[2];char tail;}; unsigned long result=__builtin_offsetof(S,tail);");
        for(int cycle=0;cycle<3;cycle++) {
            p=NameResolver.resolve(ConstantFolder.fold(p)); healthy(p); value(p,"result",8);
        }
        var enumerator=nodes(p,EnumeratorDeclaration.class).stream().filter(e->e.name().equals("width")).findFirst().orElseThrow();
        var edit=new ProjectEditor(p);edit.replace(enumerator.value().orElseThrow(),IntegerLiteral.create().setValue(31).build());
        var changed=NameResolver.resolve(edit.build());healthy(changed);value(changed,"result",16);
        var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",16);
    }
    @Test void unselectedContractAndAllUncertifiedCategoriesStayUnsupported() {
        var old=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
        unsupported(resolve(old,source("unsigned int b:3;")),old,"BIT_FIELD");
        for(String member:List.of("unsigned int :3;", "unsigned int :0;", "unsigned int b:0;", "unsigned int b:33;",
                "unsigned char b:9;", "int b:3;", "signed int b:3;", "bool b:1;", "unsigned short b:3;", "unsigned long b:3;",
                "enum E {a,b}; E bits:2;", "unsigned int b:-1;", "int width; unsigned int b:width;"))
            unsupported(resolve(source(member)),PROFILE,"BIT_FIELD");
    }
    @Test void bitFieldsNeverAcquireMemberDesignatorOffsets() {
        for(String designator:List.of("b","inner.b")) {
            var p=resolve("struct I {unsigned int b:3;char tail;};struct S {unsigned int b:3;I inner;};unsigned long result=__builtin_offsetof(S,"+designator+");");
            unsupported(p,PROFILE,"BIT_FIELD");
        }
    }
    @Test void ceilingAndRepresentabilityIncludePartialUnitAndTailPadding() {
        unsupported(resolve(profile("3"),source("unsigned int b:1;")),profile("3"),"SUPPORTED_OBJECT_LIMIT");
        var exact=resolve(profile("4"),source("unsigned int b:1;"));healthy(exact);value(exact,profile("4"),"result",1);
        unsupported(resolve("struct S {unsigned int b:1;unsigned int huge[18446744073709551615UL];char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"SIZE_NOT_REPRESENTABLE");
    }
    @Test void widthSizeofDependenciesRemainGuardedAfterFoldingAndEdits() {
        var bounded=profile("16");
        var p=resolve(bounded,"struct Bound {char bytes[8];};struct S {unsigned int b:sizeof(Bound);char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        healthy(p);value(p,bounded,"result",1);
        var folded=NameResolver.resolve(ConstantFolder.fold(p));healthy(folded);
        assertFalse(nodes(variable(folded,"b"),SizeofExpression.class).isEmpty());
        var edit=new ProjectEditor(folded);
        edit.replace(((ArrayType)variable(folded,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
        unsupported(NameResolver.resolve(edit.build()),bounded,"SUPPORTED_OBJECT_LIMIT");
    }
    @Test void pragmasNonPodsAndCyclesRemainGuarded() {
        unsupported(resolve("#pragma pack(push,1)\n"+source("unsigned int b:3;")+"\n#pragma pack(pop)\n"),PROFILE,"UNSUPPORTED_PRAGMA");
        unsupported(resolve("class S {unsigned int b:3;public:char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"NOT_POD");
        assertTimeoutPreemptively(Duration.ofSeconds(8),()->{
            var p=resolve(source("unsigned int b:sizeof(S);"));
            assertTrue(p.hasErrors());assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        });
    }
}
