/* Selected SysV bool and unnamed oversized bit-field allocation, never bit-field addresses. MIT License. */
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

class PaddingBitFieldLayoutTest {
    private static final String STORAGE = "x86_64-sysv-padding-v7";
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

    @Test void boolAndUnnamedOversizedBoundariesHaveIndependentFacts() {
        int[][] cases={{1,2,3,1},{8,2,3,1},{9,3,4,1},{16,4,6,2},{17,5,6,2},
                {31,6,8,2},{32,8,12,4},{33,9,12,4},{63,12,16,4},{64,16,24,8}};
        for (int[] c:cases) for (String name:List.of("bits","")) {
            var p=resolve(source("char prefix;bool "+name+":"+c[0]+";")); healthy(p); value(p,"result",c[1]);
            var layout=PodLayout.layout(p,nodes(p,ClassDeclaration.class).getFirst(),PROFILE).value().orElseThrow();
            assertEquals(BigInteger.valueOf(c[2]),layout.sizeBytes());assertEquals(c[3]*8,layout.alignmentBits());
            assertTrue(layout.fields().stream().noneMatch(f->f.declaration().bitWidth().isPresent()));
        }
        for (String type:List.of("char","signed char","unsigned char","short","unsigned short","int","unsigned int")) {
            var p=resolve(source("char prefix;"+type+" :33;")); healthy(p);value(p,"result",9);
            var layout=PodLayout.layout(p,nodes(p,ClassDeclaration.class).getFirst(),PROFILE).value().orElseThrow();
            assertEquals(BigInteger.valueOf(12),layout.sizeBytes());assertEquals(32,layout.alignmentBits());
        }
    }
    @Test void ordinaryPaddingAndOversizedPaddingHaveDifferentAlignmentRules() {
        for (String field:List.of("bool :0;","bool :3;","int :3;","int :33;")) {
            var p=resolve(source("char prefix;"+field));healthy(p);
            var layout=PodLayout.layout(p,nodes(p,ClassDeclaration.class).getFirst(),PROFILE).value().orElseThrow();
            assertEquals(field.equals("int :33;")?32:8,layout.alignmentBits());
        }
        var p=resolve("union U{bool :64;char tail;};struct S{char c;U u;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        healthy(p);value(p,"result",16);
        p=resolve("struct I{char c;bool :64;};struct S{char c;I a[2];char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        healthy(p);value(p,"result",40);
        p=resolve(source("typedef bool B;char c;B :0;"));healthy(p);value(p,"result",1);
    }
    @Test void priorPoliciesKeepTheirIdentityAndLimits() {
        for (String policy:List.of("x86_64-sysv-unsigned-v1","x86_64-sysv-unsigned-v2","x86_64-sysv-unsigned-v3",
                "x86_64-sysv-integral-v4","x86_64-sysv-oversized-v5","x86_64-sysv-bool-v6")) {
            var old=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,policy).build();
            for (String field:List.of("bool b:9;","bool :0;","int :33;"))
                unsupported(resolve(old,source("char prefix;"+field)),old,"BIT_FIELD");
            assertNotEquals(old.identityHash(),PROFILE.identityHash());assertEquals(old,PlatformProfile.fromSettings(old.toSettings()));
        }
        assertEquals(PROFILE,PlatformProfile.fromSettings(PROFILE.toSettings()));
    }
    @Test void unnamedWidthEditsRecomputeAlignmentAndCopiesRemainIndependent() {
        var original=resolve(source("char c;bool :8;"));
        for (var p:List.of(original,original.edit().build(),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))) {
            healthy(p);value(p,"result",2);assertTrue(ProjectJson.encode(p).toText("  ").contains(STORAGE));
            var field=nodes(p,VariableDeclaration.class).stream().filter(f->f.bitWidth().isPresent()).findFirst().orElseThrow();
            var editor=new ProjectEditor(p);editor.replace(field.bitWidth().orElseThrow(),IntegerLiteral.create().setValue(64).build());
            var wide=NameResolver.resolve(editor.build());healthy(wide);value(wide,"result",16);
            var reparsed=resolve(Cpg.sources(wide,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",16);
            field=nodes(wide,VariableDeclaration.class).stream().filter(f->f.bitWidth().isPresent()).findFirst().orElseThrow();
            editor=new ProjectEditor(wide);editor.replace(field.type().orElseThrow(),NamedType.create().setName("unsigned long").build());
            var ordinary=NameResolver.resolve(editor.build());healthy(ordinary);value(ordinary,"result",16);
            assertEquals(8,PodLayout.layout(ordinary,nodes(ordinary,ClassDeclaration.class).getFirst(),PROFILE).value().orElseThrow().alignmentBits());
            field=nodes(ordinary,VariableDeclaration.class).stream().filter(f->f.bitWidth().isPresent()).findFirst().orElseThrow();
            editor=new ProjectEditor(ordinary);editor.replace(field.type().orElseThrow(),NamedType.create().setName("bool").build());
            editor.replace(field.bitWidth().orElseThrow(),IntegerLiteral.create().setValue(8).build());
            var reversed=NameResolver.resolve(editor.build());healthy(reversed);value(reversed,"result",2);value(p,"result",2);
        }
    }
    @Test void remainingFormsAndGuardsStayExplicit() {
        for (String field:List.of("bool :65;","bool b:65;","long b:65;","enum E{a,b};E bits:1;",
                "const bool :3;","private:bool :3;public:","bool b:0;","bool :-1;"))
            unsupported(resolve(source("char c;"+field)),PROFILE,"BIT_FIELD");
        unsupported(resolve(source("bool :3;")),PROFILE,"BIT_FIELD");
        unsupported(resolve("struct S{bool b:9;};unsigned long result=__builtin_offsetof(S,b);"),PROFILE,"BIT_FIELD");
        unsupported(resolve("#pragma pack(push,1)\n"+source("bool b:9;")+"\n#pragma pack(pop)"),PROFILE,"UNSUPPORTED_PRAGMA");
        var limit=profile("8");unsupported(resolve(limit,source("char c;bool :64;")),limit,"SUPPORTED_OBJECT_LIMIT");
        assertTimeoutPreemptively(Duration.ofSeconds(8),()->assertTrue(resolve(source("bool b:sizeof(S);")).hasErrors()));
    }
}
