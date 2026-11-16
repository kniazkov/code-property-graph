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

class ExtendedBitFieldLayoutTest {
    private static final String STORAGE = "x86_64-gcc-extended-v11";
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

    @Test void extendedWidthsAndAlignment() {
        for(String type:List.of("bool","char","short","int","long")) for(String name:List.of("bits",""))
            for(int width:new int[]{128,129,255,256,257,1024}) {
                var p=resolve(source("char c;"+type+" "+name+":"+width+";"));healthy(p);
                value(p,"result",16+(width+7)/8);
                var layout=PodLayout.layout(p,nodes(p,ClassDeclaration.class).getFirst(),PROFILE).value().orElseThrow();
                assertEquals(128,layout.alignmentBits());
                assertEquals(BigInteger.valueOf(((16+(width+7)/8+1+15)/16)*16),layout.sizeBytes());
            }
    }
    @Test void hugeWidthsHitObjectCeilingWithoutIntegerOverflow() {
        var p=resolve(source("char c;unsigned int bits:4294967296UL;"));healthy(p);value(p,"result",536870928L);
        unsupported(resolve(source("char c;unsigned int bits:18446744073709551615UL;")),PROFILE,"SUPPORTED_OBJECT_LIMIT");
        var small=profile("32");unsupported(resolve(small,source("char c;bool :128;")),small,"SUPPORTED_OBJECT_LIMIT");
    }
    @Test void editsAndOldProfileRemainIndependent() {
        var old=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,"x86_64-sysv-cv-v10").build();
        unsupported(resolve(old,source("char c;bool :128;")),old,"BIT_FIELD");assertNotEquals(old.identityHash(),PROFILE.identityHash());
        assertEquals(PROFILE,PlatformProfile.fromSettings(PROFILE.toSettings()));
        var original=resolve(source("char c;bool bits:128;"));healthy(original);
        for(var p:List.of(original,original.edit().build(),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))) {
            healthy(p);value(p,"result",32);assertTrue(ProjectJson.encode(p).toText("  ").contains(STORAGE));
            var editor=new ProjectEditor(p);editor.replace(variable(p,"bits").bitWidth().orElseThrow(),IntegerLiteral.create().setValue(256).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",48);value(p,"result",32);
            var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",48);
            editor=new ProjectEditor(changed);editor.replace(variable(changed,"bits").bitWidth().orElseThrow(),IntegerLiteral.create().setValue(128).build());
            value(NameResolver.resolve(editor.build()),"result",32);
        }
    }
    @Test void guardsRemain() {
        for(String field:List.of("bool b:0;","bool :-1;","enum E{a};E b:1;"))unsupported(resolve(source("char c;"+field)),PROFILE,"BIT_FIELD");
        unsupported(resolve(source("bool :128;")),PROFILE,"BIT_FIELD");
        unsupported(resolve("struct S{bool b:128;};unsigned long result=__builtin_offsetof(S,b);"),PROFILE,"BIT_FIELD");
    }
}
