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

class SignedBitFieldLayoutTest {
    private static final String STORAGE = "x86_64-sysv-integral-v4";
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

    @Test void explicitV4PreservesPriorIdentityAndRejectsSignedFieldsInOldPolicies() {
        var omitted=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
        assertEquals("c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10",omitted.identityHash());
        for(String policy:List.of("x86_64-sysv-unsigned-v1","x86_64-sysv-unsigned-v2","x86_64-sysv-unsigned-v3")) {
            var old=omitted.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,policy).build();
            assertNotEquals(old.identityHash(),PROFILE.identityHash());
            for(String type:List.of("char","signed char","short","signed short","int","signed int","long","signed long"))
                unsupported(resolve(old,source(type+" b:3;")),old,"BIT_FIELD");
            assertEquals(old,PlatformProfile.fromSettings(old.toSettings()));
        }
        assertEquals(PROFILE,PlatformProfile.fromSettings(PROFILE.toSettings()));
        assertEquals(PROFILE,PROFILE.toBuilder().build());
        assertEquals(omitted,PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build());
        assertThrows(IllegalArgumentException.class,()->PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,"x86_64-sysv-integral-v5").build());
    }
    @Test void signedAndPlainFamiliesHaveIndependentLayoutFactsWithoutValueClaims() {
        for(String type:List.of("char","signed char","short","signed short","int","signed int","long","signed long")) {
            int bits=type.contains("char")?8:type.contains("short")?16:type.contains("long")?64:32;
            for(var entry:Map.of(type+" b:3;",1,type+" b:"+bits+";",bits/8,
                    type+" b:"+(bits-1)+";"+type+" c:2;",bits/8+1).entrySet()) {
                var p=resolve(source(entry.getKey()));healthy(p);value(p,"result",entry.getValue());
                var owner=nodes(p,ClassDeclaration.class).stream().filter(c->c.name().equals("S")).findFirst().orElseThrow();
                var layout=PodLayout.layout(p,owner,PROFILE).value().orElseThrow();
                assertEquals(bits,layout.alignmentBits());
                assertEquals(BigInteger.valueOf((entry.getValue()+bits/8)/(bits/8)*(bits/8)),layout.sizeBytes());
                assertTrue(layout.fields().stream().noneMatch(f->f.declaration().bitWidth().isPresent()));
            }
            var p=resolve("union U {"+type+" b:3;char bytes[1];};struct S {char p;U u;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
            healthy(p);value(p,"result",2*(bits/8));
        }
    }
    @Test void mixedSignedUnsignedUnitsAndUnnamedPaddingPreserveV3Rules() {
        for(var entry:Map.of("signed char b:7;unsigned short c:10;int d:20;long e:40;",13,
                "char prefix;short :3;short :0;",2,"char prefix;long :3;long :0;",8,
                "signed int :0;",0,"long b:63;unsigned short c:2;",9).entrySet()) {
            var p=resolve(source(entry.getKey()));healthy(p);value(p,"result",entry.getValue());
        }
        var p=resolve("typedef const signed short Small;typedef volatile long Large;struct I {Small b:3;char tail;};struct S {char c;I values[2];Large b:3;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        healthy(p);value(p,"result",7);
    }
    @Test void widthAndTypeEditsCopiesGreenFoldedJsonAndReparse() {
        var initial=resolve("struct I {static const int width=3;signed short b:width;char tail;};struct S {char c;I values[2];char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        for(var p:List.of(initial,initial.edit().build(),NameResolver.resolve(initial.edit().build()),TypeNormalizer.green(initial),NameResolver.resolve(ConstantFolder.fold(initial)))) {
            healthy(p);value(p,"result",6);assertTrue(ProjectJson.encode(p).toText("  ").contains(STORAGE));
            var edit=new ProjectEditor(p);edit.replace(variable(p,"b").type().orElseThrow(),NamedType.create().setName("long").build());
            var changed=NameResolver.resolve(edit.build());healthy(changed);value(changed,"result",24);
            var width=new ProjectEditor(changed);width.replace(variable(changed,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(64).build());
            changed=NameResolver.resolve(width.build());healthy(changed);value(changed,"result",40);
            var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",40);
            var reverse=new ProjectEditor(changed);reverse.replace(variable(changed,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(3).build());
            reverse.replace(variable(changed,"b").type().orElseThrow(),NamedType.create().setName("signed short").build());
            var restored=NameResolver.resolve(reverse.build());healthy(restored);value(restored,"result",6);
        }
    }
    @Test void unsupportedFormsDoNotExpandAlongWithSignedStorage() {
        for(String members:List.of("bool b:1;","enum E {a,b};E bits:2;","signed char b:9;","long b:65;",
                "int :3;","long :1;","const int :0;","volatile long :0;","int b:0;","int b:-1;"))
            unsupported(resolve(source(members)),PROFILE,"BIT_FIELD");
        for(String members:List.of("signed char b:9;","long b:65;","int :3;","const int :0;")) {
            var p=resolve(source(members));
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-offsetof")),p.semanticDiagnostics().toString());
            assertFalse(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("invalid-offsetof")),p.semanticDiagnostics().toString());
        }
        for(String declaration:List.of("class S {long :3;public:char tail;};","struct S {private:int :0;public:char tail;};")) {
            var p=resolve(declaration+"unsigned long result=__builtin_offsetof(S,tail);");unsupported(p,PROFILE,"BIT_FIELD");
            assertFalse(p.semanticDiagnostics().toString().contains("NOT_POD"));
        }
        unsupported(resolve("struct S {int b:3;};unsigned long result=__builtin_offsetof(S,b);"),PROFILE,"BIT_FIELD");
    }
    @Test void dependenciesCyclesCeilingAndPragmasRemainConservative() {
        var bounded=profile("7");unsupported(resolve(bounded,source("long b:1;")),bounded,"SUPPORTED_OBJECT_LIMIT");
        unsupported(resolve("#pragma pack(push,1)\n"+source("int b:3;")+"\n#pragma pack(pop)"),PROFILE,"UNSUPPORTED_PRAGMA");
        unsupported(resolve("class S {int b:3;public:char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"NOT_POD");
        var p=resolve("enum W {width=3};struct S {long b:width;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        p=NameResolver.resolve(ConstantFolder.fold(p));healthy(p);value(p,"result",1);
        var e=nodes(p,EnumeratorDeclaration.class).stream().filter(v->v.name().equals("width")).findFirst().orElseThrow();
        var edit=new ProjectEditor(p);edit.replace(e.value().orElseThrow(),IntegerLiteral.create().setValue(64).build());
        var changed=NameResolver.resolve(edit.build());healthy(changed);value(changed,"result",8);
        assertTimeoutPreemptively(Duration.ofSeconds(8),()->{
            var cyclic=resolve(source("long b:sizeof(S);"));assertTrue(cyclic.hasErrors());
            assertTrue(new IntegralConstants(cyclic,PROFILE).evaluate(variable(cyclic,"result").initializer().orElseThrow()).isEmpty());
        });
    }
}
