/* Bounded glibc environment-save model. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SetjmpModelTest {
    private static PlatformProfile profile() { return PlatformProfile.builder().cRuntime("glibc").setjmp("gcc-clang-glibc").build(); }
    private static Project resolve(String body) {
        Path path = Path.of("main.cpp").toAbsolutePath();
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03").withPlatform(profile());
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit,"#include <csetjmp>\n" + body)).build());
    }
    private static List<SetjmpExpression> saves(Project graph) {
        var list = new ArrayList<SetjmpExpression>(); graph.walk(new NodeVisitor(){ @Override public void visit(SetjmpExpression save){ list.add(save); }}); return list;
    }
    @Test void capabilityIsExplicitAndRoundTrips() {
        assertTrue(PlatformProfile.defaults().setjmp().isEmpty());
        assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().setjmp("gcc-clang-glibc").build());
        assertThrows(IllegalArgumentException.class,()->new SetjmpModel("builtin"));
        assertEquals(profile().toSettings(), PlatformProfile.fromSettings(profile().toSettings()).toSettings());
        assertEquals("::_setjmp(env)",profile().setjmp().orElseThrow().replacement());
    }
    @Test void validContextsAndCopiesRetainTypedUnknownOperand() {
        for (String statement : List.of("setjmp(b);", "(void)setjmp(b);", "if(setjmp(b)){}", "if(!setjmp(b)){}", "if(setjmp(b)==0){}", "while(0!=setjmp(b)){}", "for(;setjmp(b);){}", "do{}while(setjmp(b));", "switch(setjmp(b)){default:break;}")) {
            Project original = resolve("int f(){std::jmp_buf b;"+statement+"return 0;}");
            for(Project graph:List.of(original, original.edit().build(),NameResolver.resolve(original.edit().build()))) {
                assertFalse(graph.hasErrors(),()->statement+": "+graph.semanticDiagnostics());
                var save=saves(graph).getFirst(); assertEquals("int",TypeSemantics.expressionType(save,profile()).orElseThrow().canonicalIdentity());
                assertTrue(ConstantEvaluator.evaluate(save,profile()).isEmpty());
                assertTrue(ProjectJson.encode(graph).toText("  ").contains("SetjmpExpression"));
            }
        }
    }
    @Test void invalidPlacementsAreNotHiddenByFolding() {
        for(String statement:List.of("int x=setjmp(b);", "return setjmp(b);", "if(setjmp(b)+1){}", "if(setjmp(b)&&1){}", "sizeof(setjmp(b));")) {
            var graph=resolve("int f(){std::jmp_buf b;"+statement+"return 0;}"); assertTrue(graph.hasErrors(),statement);
            assertFalse(saves(ConstantFolder.fold(graph)).isEmpty());
        }
    }
    @Test void unsupportedStorageAndEscapesReject() {
        for(String body:List.of("int f(){static std::jmp_buf b;setjmp(b);return 0;}", "int f(){const std::jmp_buf b;setjmp(b);return 0;}", "int f(){int b;setjmp(b);return 0;}", "void g(std::jmp_buf);int f(){std::jmp_buf b;setjmp(b);g(b);return 0;}")) assertTrue(resolve(body).hasErrors(),body);
    }
    @Test void runtimeDeclarationsCannotMasqueradeAsTheSelectedBridge() {
        for (String declarations : List.of("int _setjmp(std::jmp_buf);", "namespace custom { int _setjmp(std::jmp_buf); } using namespace custom;")) {
            var graph=resolve(declarations+"int f(){std::jmp_buf b;setjmp(b);return 0;}");
            assertTrue(graph.hasErrors());
            assertTrue(graph.semanticDiagnostics().toString().contains("unsupported-setjmp-runtime-declaration"));
        }
    }
    @Test void localRuntimeShadowIsExplicitlyOutsidePreservedSourceSubset() {
        var graph=resolve("int f(){int _setjmp=7;std::jmp_buf b;if(setjmp(b))return _setjmp;std::longjmp(b,1);}");
        assertTrue(graph.hasErrors()); assertEquals(1,saves(graph).size());
        assertTrue(graph.semanticDiagnostics().toString().contains("unsupported-setjmp-runtime-declaration"));
    }
}
