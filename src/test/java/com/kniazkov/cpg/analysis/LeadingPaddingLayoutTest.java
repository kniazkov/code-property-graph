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

class LeadingPaddingLayoutTest {
    private static final String STORAGE = "x86_64-gcc-leading-v13";
    private static final PlatformProfile PROFILE = profile("2147483647");
    @TempDir Path temp;
    private static PlatformProfile profile(String limit) { return PlatformProfile.builder()
            .offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", limit, null, "gcc-clang-x86_64-default-enum-v1", STORAGE).build(); }
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

    @Test void leadingAndPaddingOnlyHaveFixedLayout() {
        for(String type:List.of("unsigned int","bool","unsigned long")) for(int width:new int[]{1,3,8,33,128}) {
            var p=resolve(source(type+" :"+width+";"));healthy(p);value(p,"result",(width+7)/8);
        }
        var p=resolve("struct P{unsigned int :3;};struct S{char c;P a[2];char tail;};unsigned long result=__builtin_offsetof(S,tail);");healthy(p);value(p,"result",3);
        p=resolve("enum E{A=0};"+source("private:const E :3;public:"));healthy(p);value(p,"result",1);
        p=resolve(source("unsigned int :3;unsigned int :0;"));healthy(p);value(p,"result",4);
        p=resolve(source("unsigned int :3;unsigned int bits:3;"));healthy(p);value(p,"result",1);
    }
    @Test void oldPolicyStillRejectsLeadingPadding() {
        var old=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,"gcc-clang-x86_64-default-enum-v1","x86_64-gcc-enum-v12").build();
        unsupported(resolve(old,source("unsigned int :3;")),old,"BIT_FIELD");assertNotEquals(old.identityHash(),PROFILE.identityHash());
        assertEquals(PROFILE,PlatformProfile.fromSettings(PROFILE.toSettings()));
    }
    @Test void editsCopiesAndReparsePreserveLeadingLayout() {
        var original=resolve(source("bool :3;"));healthy(original);
        for(var p:List.of(original,original.edit().build(),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))) {
            healthy(p);value(p,"result",1);assertTrue(ProjectJson.encode(p).toText("  ").contains(STORAGE));
            var field=nodes(p,VariableDeclaration.class).stream().filter(v->v.bitWidth().isPresent()).findFirst().orElseThrow();
            var editor=new ProjectEditor(p);editor.replace(field.bitWidth().orElseThrow(),IntegerLiteral.create().setValue(128).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",16);value(p,"result",1);
            var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",16);
            field=nodes(changed,VariableDeclaration.class).stream().filter(v->v.bitWidth().isPresent()).findFirst().orElseThrow();
            editor=new ProjectEditor(changed);editor.replace(field.bitWidth().orElseThrow(),IntegerLiteral.create().setValue(3).build());value(NameResolver.resolve(editor.build()),"result",1);
        }
    }
    @Test void guardsRemainActive() {
        unsupported(resolve(source("bool bits:0;")),PROFILE,"BIT_FIELD");
        unsupported(resolve("struct S{unsigned int :3;int hidden;private:int secret;public:char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"NOT_POD");
        var small=profile("8");unsupported(resolve(small,source("bool :128;")),small,"SUPPORTED_OBJECT_LIMIT");
        unsupported(resolve("struct S{unsigned int :3;unsigned int bits:3;};unsigned long result=__builtin_offsetof(S,bits);"),PROFILE,"BIT_FIELD");
    }
}
