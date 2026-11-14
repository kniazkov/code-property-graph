/* Opt-in same-TU static environment storage. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaticSetjmpTest {
    private static PlatformProfile profile(boolean v2) {
        return PlatformProfile.builder().cRuntime("glibc").setjmp(v2 ? "gcc-clang-glibc-static-v2" : "gcc-clang-glibc").build();
    }
    private static SourceFile.Builder file(String path, String code, boolean v2) {
        Path p=Path.of(path).toAbsolutePath();
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(p),p.getParent(),List.of(),List.of(),"c++03").withPlatform(profile(v2));
        return Parser.parse(unit,code);
    }
    private static Project resolve(String body, boolean v2) {
        return NameResolver.resolve(Project.create().addFile(file("main.cpp","#include <csetjmp>\n"+body,v2)).build());
    }
    private static void healthy(Project graph) { assertFalse(graph.hasErrors(),()->graph.parseProblems()+" / "+graph.semanticDiagnostics()); }
    private static void diagnostic(Project graph,String code) {
        assertTrue(graph.parseProblems().isEmpty(),()->graph.parseProblems().toString());
        assertTrue(graph.semanticDiagnostics().toString().contains(code),()->graph.semanticDiagnostics().toString());
    }
    @Test void policyIdentityAndLegacyAcceptanceAreSeparate() {
        assertNotEquals(profile(false).identityHash(),profile(true).identityHash());
        assertEquals(profile(true).toSettings(),PlatformProfile.fromSettings(profile(true).toSettings()).toSettings());
        assertEquals("::_setjmp(env)",profile(true).setjmp().orElseThrow().replacement());
        assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().setjmp("gcc-clang-glibc-static-v2").build());
        for(String body:List.of("std::jmp_buf env;int f(){if(setjmp(env))return 1;return 0;}",
                "int f(){static std::jmp_buf env;if(setjmp(env))return 1;return 0;}")) {
            healthy(resolve(body,true)); diagnostic(resolve(body,false),"unsupported-setjmp-buffer-storage");
        }
        healthy(resolve("int f(){std::jmp_buf env;if(setjmp(env))return 1;return 0;}",false));
    }
    @Test void definitionsCopiesAndFoldingKeepOpaqueIdentity() {
        for(String body:List.of("typedef std::jmp_buf Buffer;Buffer env;int f(){if(setjmp(env))return 1;return 0;}",
                "static std::jmp_buf env;void resume(){std::longjmp(env,7);}int f(){if(setjmp(env))return 7;resume();return 0;}",
                "namespace n {std::jmp_buf env;}int f(){if(setjmp(n::env))return 1;return 0;}")) {
            var original=resolve(body,true);
            for(var graph:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),ConstantFolder.fold(original))) {
                healthy(graph); assertTrue(ProjectJson.encode(graph).toText("  ").contains("SetjmpExpression"));
                graph.walk(new NodeVisitor(){@Override public void visit(SetjmpExpression save){
                    var buffer=(VariableDeclaration)(save.buffer() instanceof NameExpression name ? name.target().orElseThrow() : ((MemberExpression)save.buffer()).target().orElseThrow());
                    assertTrue(TypeSemantics.describe(buffer.type().orElseThrow(),profile(true)).sizeBytes().isEmpty());
                    assertTrue(ConstantEvaluator.evaluate(save,profile(true)).isEmpty());
                }});
            }
        }
    }
    @Test void structuralExclusionsAreDiagnosticsOnly() {
        for(String body:List.of(
                "extern std::jmp_buf env;int f(){setjmp(env);return 0;}",
                "std::jmp_buf env;extern std::jmp_buf env;int f(){setjmp(env);return 0;}",
                "std::jmp_buf env;void sink(std::jmp_buf);int f(){setjmp(env);sink(env);return 0;}",
                "std::jmp_buf env;int f(){setjmp(env);std::longjmp(*(&env),1);return 0;}",
                "std::jmp_buf env;int f(){std::jmp_buf &ref=env;setjmp(ref);return 0;}",
                "struct X {static std::jmp_buf env;};int f(){setjmp(X::env);return 0;}",
                "struct X {static std::jmp_buf env;};std::jmp_buf X::env;int f(){setjmp(X::env);return 0;}",
                "struct X {static std::jmp_buf env;};std::jmp_buf X::env;void f(){std::longjmp(X::env,1);}",
                "namespace n {extern std::jmp_buf env;}namespace n {std::jmp_buf env;}int f(){setjmp(n::env);return 0;}",
                "const std::jmp_buf env={0};int f(){setjmp(env);return 0;}",
                "std::jmp_buf env;void longjmp(std::jmp_buf,int);int f(){setjmp(env);longjmp(env,1);return 0;}"))
            assertTrue(resolve(body,true).hasErrors(),body);
        diagnostic(resolve("std::jmp_buf env;int f(){return setjmp(env);}",true),"invalid-setjmp-context");
        diagnostic(resolve("std::jmp_buf env;int _setjmp(std::jmp_buf);int f(){setjmp(env);return 0;}",true),"unsupported-setjmp-runtime-declaration");
        diagnostic(resolve("std::jmp_buf env;void f(){std::longjmp(*(&env),1);}",true),"unsupported-setjmp-restore-buffer");
        healthy(resolve("std::jmp_buf env;void f(){std::longjmp(env,1);}",false));
        healthy(resolve("std::jmp_buf env;void f(){std::longjmp(env,1);}",true)); // structural admission, not a native lifetime test
        healthy(resolve("std::jmp_buf env;int f(){if(setjmp(env))return 1;sizeof(env);return 0;}",true));
    }
    @Test void crossTranslationUnitExternBufferIsNotAdmitted() {
        var graph=NameResolver.resolve(Project.create()
            .addFile(file("one.cpp","#include <csetjmp>\nstd::jmp_buf env;int save(){if(setjmp(env))return 1;return 0;}",true))
            .addFile(file("two.cpp","#include <csetjmp>\nextern std::jmp_buf env;void restore(){std::longjmp(env,1);}",true)).build());
        diagnostic(graph,"unsupported-setjmp-buffer-redeclaration");
    }
    @Test void globalRestoreAndCallersKeepConservativeFlow() {
        var graph=DataFlow.analyze(resolve("std::jmp_buf env;void jump(){std::longjmp(env,1);}void caller(){jump();}int saved(){if(setjmp(env))return 1;jump();return 0;}int ordinary(){return 3;}",true));
        healthy(graph);
        for(var flow:graph.flows()) {
            String name=flow.entry().name();
            if(List.of("jump","caller","saved").contains(name)) {
                assertFalse(flow.complete(),name);assertTrue(flow.control().isEmpty(),name);assertTrue(flow.data().isEmpty(),name);
            } else if(name.equals("ordinary")) assertTrue(flow.complete());
        }
    }

    @Test void profilesAndSameSpellingBuffersRemainTranslationUnitLocal() {
        String body="#include <csetjmp>\nstatic std::jmp_buf env;static int f(){if(setjmp(env))return 1;return 0;}";
        var both=NameResolver.resolve(Project.create().addFile(file("one.cpp",body,true)).addFile(file("two.cpp",body,true)).build());
        healthy(both);
        var mixed=NameResolver.resolve(Project.create().addFile(file("one.cpp",body,true)).addFile(file("two.cpp",body,false)).build());
        diagnostic(mixed,"unsupported-setjmp-buffer-storage");
    }

}
