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

class EnumBitFieldLayoutTest {
    private static final String STORAGE = "x86_64-gcc-enum-v12";
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

    @Test void explicitEnumStorageControlsAllocation() {
        for(String values:List.of("A=0,B=3","A=-1,B=3","A=4294967296UL")) {
            var p=resolve("enum E{"+values+"};"+source("char c;E bits:3;"));healthy(p);value(p,"result",2);
            var layout=PodLayout.layout(p,nodes(p,ClassDeclaration.class).getFirst(),PROFILE).value().orElseThrow();
            assertEquals(values.contains("4294967296")?64:32,layout.alignmentBits());
        }
        var p=resolve("enum E{A=0,B=3};typedef const E Alias;"+source("char c;Alias bits:128;"));healthy(p);value(p,"result",32);
        p=resolve("enum E{A=0};"+source("char c;private:E :33;public:"));healthy(p);value(p,"result",9);
        assertEquals(PROFILE,PlatformProfile.fromSettings(PROFILE.toSettings()));
    }
    @Test void storageOptInAndOldPoliciesRemainRequired() {
        String code="enum E{A=1};"+source("char c;E bits:1;");
        var missing=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,STORAGE).build();
        assertTrue(resolve(missing,code).hasErrors());
        var old=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,"gcc-clang-x86_64-default-enum-v1","x86_64-gcc-extended-v11").build();
        unsupported(resolve(old,code),old,"BIT_FIELD");assertNotEquals(old.identityHash(),PROFILE.identityHash());
    }
    @Test void editedWidthsRefreshEnumFieldLayout() {
        var original=resolve("enum E{A=1};"+source("char c;E bits:3;"));healthy(original);
        for(var p:List.of(original,original.edit().build(),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))) {
            healthy(p);value(p,"result",2);assertTrue(ProjectJson.encode(p).toText("  ").contains(STORAGE));
            var editor=new ProjectEditor(p);editor.replace(variable(p,"bits").bitWidth().orElseThrow(),IntegerLiteral.create().setValue(128).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",32);
            var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",32);value(p,"result",2);
        }
    }
    @Test void invalidAndUnsupportedFormsRemainDiagnosed() {
        for(String field:List.of("E bits:0;","E :-1;"))unsupported(resolve("enum E{A=0};"+source("char c;"+field)),PROFILE,"BIT_FIELD");
        unsupported(resolve("enum E{A=0};"+source("E :1;")),PROFILE,"BIT_FIELD");
        unsupported(resolve("enum E{A=0};struct S{E b:3;};unsigned long result=__builtin_offsetof(S,b);"),PROFILE,"BIT_FIELD");
    }
}
