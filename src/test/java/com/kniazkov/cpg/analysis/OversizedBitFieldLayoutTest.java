/* Selected SysV signed/plain bit-field allocation, never bit-field addresses. MIT License. */
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

class OversizedBitFieldLayoutTest {
    private static final String STORAGE = "x86_64-sysv-oversized-v5";
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

    @Test void oldPoliciesDoNotAcquireOversizedLayout() {
        for(String policy:List.of("x86_64-sysv-unsigned-v1","x86_64-sysv-unsigned-v2","x86_64-sysv-unsigned-v3","x86_64-sysv-integral-v4")) {
            var old=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,policy).build();
            unsupported(resolve(old,source("unsigned char b:9;")),old,"BIT_FIELD");
            assertNotEquals(old.identityHash(),PROFILE.identityHash());
            assertEquals(old,PlatformProfile.fromSettings(old.toSettings()));
        }
        var omitted=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
        assertEquals("c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10",omitted.identityHash());
        assertEquals(PROFILE,PlatformProfile.fromSettings(PROFILE.toSettings()));
    }
    @Test void boundariesUseAllocationAlignmentNotDeclaredStorage() {
        for(String type:List.of("char","signed char","unsigned char","short","signed short","unsigned short","int","signed int","unsigned int")) {
            int declared=type.contains("char")?8:type.contains("short")?16:32;
            for(int width:new int[]{9,15,16,17,31,32,33,63,64}) {
                if(width<=declared)continue;
                int alignment=width<16?1:width<32?2:width<64?4:8;
                int tail=alignment+(width+7)/8;
                int size=(tail+1+alignment-1)/alignment*alignment;
                var p=resolve(source("char prefix;"+type+" b:"+width+";"));healthy(p);value(p,"result",tail);
                var owner=nodes(p,ClassDeclaration.class).stream().filter(c->c.name().equals("S")).findFirst().orElseThrow();
                var layout=PodLayout.layout(p,owner,PROFILE).value().orElseThrow();
                assertEquals(alignment*8,layout.alignmentBits());assertEquals(BigInteger.valueOf(size),layout.sizeBytes());
                assertTrue(layout.fields().stream().noneMatch(f->f.declaration().bitWidth().isPresent()));
            }
        }
    }
    @Test void mixedPackingUnionAliasesAndNestedArrays() {
        for(var entry:Map.of("unsigned char big:9;unsigned char next:1;",2,
                "unsigned char big:15;unsigned char next:1;",2,
                "unsigned char first:7;unsigned short big:17;unsigned int next:3;",5,
                "unsigned int big:33;unsigned int next:3;",5,
                "unsigned char first:7;unsigned char big:64;",16).entrySet()) {
            var p=resolve(source(entry.getKey()));healthy(p);value(p,"result",entry.getValue());
        }
        var mixed=resolve(source("unsigned char a:3;char big:9;unsigned char b:2;"));healthy(mixed);value(mixed,"result",3);
        var p=resolve("typedef const signed short Small;struct I {char c;Small b:17;char tail;};struct S {char c;I values[2];char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        healthy(p);value(p,"result",14);
        p=resolve("union U {int big:33;char bytes[1];};struct S {char c;U u;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        healthy(p);value(p,"result",12);
    }
    @Test void widthTypeChangesCrossOversizedBoundaryAndReverse() {
        var initial=resolve("struct S {char prefix;static const int width=9;char b:width;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        for(var p:List.of(initial,initial.edit().build(),NameResolver.resolve(initial.edit().build()),TypeNormalizer.green(initial),NameResolver.resolve(ConstantFolder.fold(initial)))) {
            healthy(p);value(p,"result",3);assertTrue(ProjectJson.encode(p).toText("  ").contains(STORAGE));
            var edit=new ProjectEditor(p);edit.replace(variable(p,"b").type().orElseThrow(),NamedType.create().setName("short").build());
            var ordinary=NameResolver.resolve(edit.build());healthy(ordinary);value(ordinary,"result",4);
            var width=new ProjectEditor(ordinary);width.replace(variable(ordinary,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(33).build());
            var changed=NameResolver.resolve(width.build());healthy(changed);value(changed,"result",9);
            var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",9);
            var reverse=new ProjectEditor(changed);reverse.replace(variable(changed,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(9).build());
            reverse.replace(variable(changed,"b").type().orElseThrow(),NamedType.create().setName("char").build());
            var restored=NameResolver.resolve(reverse.build());healthy(restored);value(restored,"result",3);
        }
    }
    @Test void residualsAndUndefinedDesignatorsAreNotRunNatively() {
        for(String members:List.of("bool b:1;","enum E {a,b};E bits:2;","char b:65;","long b:65;","char prefix;char :9;",
                "int :3;","const int :0;","int b:0;","int b:-1;"))
            unsupported(resolve(source(members)),PROFILE,"BIT_FIELD");
        unsupported(resolve("struct S {char b:9;};unsigned long result=__builtin_offsetof(S,b);"),PROFILE,"BIT_FIELD");
        unsupported(resolve("class S {char b:9;public:char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"NOT_POD");
    }
    @Test void boundsPragmasAndCyclesRemainConservative() {
        var bounded=profile("7");unsupported(resolve(bounded,source("int b:33;")),bounded,"SUPPORTED_OBJECT_LIMIT");
        unsupported(resolve("#pragma pack(push,1)\n"+source("char b:9;")+"\n#pragma pack(pop)"),PROFILE,"UNSUPPORTED_PRAGMA");
        assertTimeoutPreemptively(Duration.ofSeconds(8),()->{
            var cyclic=resolve(source("char b:sizeof(S);"));assertTrue(cyclic.hasErrors());
            assertTrue(new IntegralConstants(cyclic,PROFILE).evaluate(variable(cyclic,"result").initializer().orElseThrow()).isEmpty());
        });
    }
}
