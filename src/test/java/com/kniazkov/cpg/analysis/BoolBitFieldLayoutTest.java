/* Selected SysV named bool bit-field allocation, never bit-field addresses. MIT License. */
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

class BoolBitFieldLayoutTest {
    private static final String STORAGE = "x86_64-sysv-bool-v6";
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

    @Test void namedBoolWidthsUseByteStorageWithoutChangingEarlierPolicies() {
        for (int width=1;width<=8;width++) {
            var p=resolve(source("char prefix;bool bits:"+width+";"));healthy(p);value(p,"result",2);
            var owner=nodes(p,ClassDeclaration.class).getFirst();
            var layout=PodLayout.layout(p,owner,PROFILE).value().orElseThrow();
            assertEquals(8,layout.alignmentBits());assertEquals(BigInteger.valueOf(3),layout.sizeBytes());
            assertTrue(layout.fields().stream().noneMatch(f->f.declaration().bitWidth().isPresent()));
        }
        for(String policy:List.of("x86_64-sysv-unsigned-v1","x86_64-sysv-unsigned-v2","x86_64-sysv-unsigned-v3","x86_64-sysv-integral-v4","x86_64-sysv-oversized-v5")) {
            var old=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,policy).build();
            unsupported(resolve(old,source("bool bits:1;")),old,"BIT_FIELD");
            assertNotEquals(old.identityHash(),PROFILE.identityHash());assertEquals(old,PlatformProfile.fromSettings(old.toSettings()));
        }
        assertEquals(PROFILE,PlatformProfile.fromSettings(PROFILE.toSettings()));
    }
    @Test void packingAcrossTypesUnionsAndAliases() {
        for(var entry:Map.of("bool a:7;bool b:2;",2,"bool a:7;unsigned int b:2;",2,
                "unsigned int a:7;bool b:2;",2,"unsigned int a:31;bool b:2;",5,
                "bool a:3;unsigned char b:9;bool c:2;",3).entrySet()) {
            var p=resolve(source(entry.getKey()));healthy(p);value(p,"result",entry.getValue());
        }
        var p=resolve("typedef const bool Flag;struct I {Flag b:8;char tail;};struct S {char c;I a[2];char tail;};unsigned long result=__builtin_offsetof(S,tail);");healthy(p);value(p,"result",5);
        p=resolve("union U {bool b:8;char tail;};struct S {char c;U u;char tail;};unsigned long result=__builtin_offsetof(S,tail);");healthy(p);value(p,"result",2);
    }
    @Test void editReversalCopiesStagesAndReparseKeepDependencies() {
        var original=resolve("struct S {char c;static const int width=1;bool b:width;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
        for(var p:List.of(original,original.edit().build(),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))) {
            healthy(p);value(p,"result",2);assertTrue(ProjectJson.encode(p).toText("  ").contains(STORAGE));
            var editor=new ProjectEditor(p);editor.replace(variable(p,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(8).build());
            var eight=NameResolver.resolve(editor.build());healthy(eight);value(eight,"result",2);
            editor=new ProjectEditor(eight);editor.replace(variable(eight,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(9).build());
            var bad=NameResolver.resolve(editor.build());unsupported(bad,PROFILE,"BIT_FIELD");
            editor=new ProjectEditor(bad);editor.replace(variable(bad,"b").type().orElseThrow(),NamedType.create().setName("int").build());
            var integral=NameResolver.resolve(editor.build());healthy(integral);value(integral,"result",3);
            var reparsed=resolve(Cpg.sources(integral,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",3);
            editor=new ProjectEditor(integral);editor.replace(variable(integral,"width").initializer().orElseThrow(),IntegerLiteral.create().setValue(1).build());
            editor.replace(variable(integral,"b").type().orElseThrow(),NamedType.create().setName("bool").build());
            var restored=NameResolver.resolve(editor.build());healthy(restored);value(restored,"result",2);value(p,"result",2);
        }
    }
    @Test void residualsAndUndefinedDesignatorsStayConservative() {
        for(String members:List.of("bool b:9;","bool b:65;","bool b:0;","bool b:-1;","char c;bool :0;","char c;bool :3;","enum E{a,b};E bits:1;","int :3;"))
            unsupported(resolve(source(members)),PROFILE,"BIT_FIELD");
        unsupported(resolve("struct S {bool b:1;};unsigned long result=__builtin_offsetof(S,b);"),PROFILE,"BIT_FIELD");
        unsupported(resolve("class S {bool b:1;public:char tail;};unsigned long result=__builtin_offsetof(S,tail);"),PROFILE,"NOT_POD");
        unsupported(resolve("#pragma pack(push,1)\n"+source("bool b:1;")+"\n#pragma pack(pop)"),PROFILE,"UNSUPPORTED_PRAGMA");
        var limited=profile("1");unsupported(resolve(limited,source("bool b:1;")),limited,"SUPPORTED_OBJECT_LIMIT");
        assertTimeoutPreemptively(Duration.ofSeconds(8),()->{var p=resolve(source("bool b:sizeof(S);"));assertTrue(p.hasErrors());});
    }
}
