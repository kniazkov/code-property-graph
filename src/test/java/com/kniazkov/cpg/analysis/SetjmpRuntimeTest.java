/* Bounded nonlocal environment save: snapshot and negative-boundary evidence. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SetjmpRuntimeTest {
    @TempDir Path temp;
    private static PlatformProfile selected() { return PlatformProfile.builder().cRuntime("glibc").setjmp("gcc-clang-glibc").build(); }
    private Project resolve(String source) {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")), temp, List.of(), List.of(), "c++03").withPlatform(selected());
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    private static void healthy(Project graph) { assertFalse(graph.hasErrors(), () -> graph.parseProblems() + " / " + graph.semanticDiagnostics()); }
    private static final String BODY = "int f(){BUFFER env; if(setjmp(env)==0) return 1; return 2;}";

    @Test void capabilityIsExplicitAndRoundTripsWithoutInventingStorageLayout() {
        assertTrue(PlatformProfile.defaults().setjmp().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().setjmp("gcc-clang-glibc").build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().cRuntime("glibc").setjmp("builtin").build());
        var profile = PlatformProfile.fromSettings(selected().toSettings());
        assertEquals(selected().identityHash(), profile.identityHash());
        assertEquals("gcc-clang-glibc", profile.setjmp().orElseThrow().emission());
    }

    @Test void bothHeadersAliasesCopiesAndJsonPreserveBoundOpaqueBufferAndProvenance() throws Exception {
        for (String header : List.of("#include <csetjmp>\n", "#include <setjmp.h>\n", "#include <csetjmp>\n#include <setjmp.h>\n", "#include <setjmp.h>\n#include <csetjmp>\n")) {
            String type = header.startsWith("#include <csetjmp>") ? "std::jmp_buf" : "jmp_buf";
            var original = resolve(header + "typedef " + type + " Buffer;" + BODY.replace("BUFFER", "Buffer"));
            for (var graph : List.of(original, original.edit().build(), NameResolver.resolve(original.edit().build()))) {
                healthy(graph);
                var operation = nodes(graph, SetjmpExpression.class).getFirst();
                var buffer = nodes(graph.files().getFirst(), VariableDeclaration.class).stream().filter(v -> v.name().equals("env")).findFirst().orElseThrow();
                assertSame(buffer, ((NameExpression)operation.buffer()).target().orElseThrow());
                assertEquals("int", TypeSemantics.expressionType(operation, selected()).orElseThrow().canonicalIdentity());
                assertEquals(ValueCategory.RVALUE, operation.semanticInfo().orElseThrow().category());
                assertTrue(ConstantEvaluator.evaluate(operation, selected()).isEmpty());
                var facts = TypeSemantics.describe(buffer.type().orElseThrow(), selected());
                assertTrue(facts.sizeBytes().isEmpty()); assertTrue(facts.alignmentBits().isEmpty());
                var definition = graph.files().getFirst().provenance().orElseThrow().unit().definitions().stream()
                        .filter(d -> d.name().equals("setjmp")).findFirst().orElseThrow();
                assertEquals("library", definition.originKind()); assertTrue(definition.functionLike());
                assertNull(definition.unavailableReason()); assertTrue(definition.rawReplacement().contains("_setjmp"));
                assertTrue(operation.provenance().isPresent());
                assertFalse(com.kniazkov.cpg.generation.Source.languages(operation).contains(Language.JAVA));
                assertThrows(IllegalArgumentException.class, () -> com.kniazkov.cpg.generation.Source.render(operation,Language.JAVA));
                assertTrue(com.kniazkov.cpg.generation.Source.render(operation,Language.CPP).contains("::_setjmp(env)"));
                String json = ProjectJson.encode(graph).toText("  "); com.kniazkov.json.Json.parse(json);
                assertTrue(json.contains("SetjmpExpression")); assertTrue(json.contains("buffer"));
            }
        }
    }

    @Test void c90PermittedControllingAndDiscardedContextsRemainUnknown() {
        for (String statement : List.of("if(setjmp(env)) return 1;", "if(!setjmp(env)) return 1;",
                "if(setjmp(env)==0) return 1;", "if(0!=setjmp(env)) return 1;", "if(setjmp(env)<2) return 1;",
                "if(2>=setjmp(env)) return 1;", "while(setjmp(env)) break;", "do {} while(setjmp(env));",
                "for(;setjmp(env);) break;", "switch(setjmp(env)){case 0: break;}", "setjmp(env);", "(void)setjmp(env);")) {
            var graph = resolve("#include <csetjmp>\nint f(){std::jmp_buf env;" + statement + "return 0;}");
            healthy(graph); assertEquals(1, nodes(graph,SetjmpExpression.class).size());
        }
    }

    @Test void forbiddenPlacementsAreDiagnosticPolicyNotNativeUndefinedExecutions() {
        for (String statement : List.of("int result=setjmp(env);", "return setjmp(env);", "consume(setjmp(env));",
                "if(setjmp(env)+1) return 1;", "if(setjmp(env)&&1) return 1;", "if(setjmp(env)==variable) return 1;",
                "sizeof(setjmp(env));", "setjmp(env),1;")) {
            var graph = resolve("#include <csetjmp>\nvoid consume(int); int f(){std::jmp_buf env;int variable=0;" + statement + "return 0;}");
            assertTrue(graph.hasErrors(), statement);
            assertEquals(1,nodes(graph,SetjmpExpression.class).size(), statement);
            assertTrue(ConstantFolder.fold(graph).hasErrors());
            assertEquals(1,nodes(ConstantFolder.fold(graph),SetjmpExpression.class).size());
        }
    }

    @Test void unsupportedStorageAndFakeTypesAreNotAcceptedBySpelling() {
        for (String source : List.of(
                "int f(){int env;setjmp(env);return 0;}",
                "typedef int jmp_buf; int f(){jmp_buf env;setjmp(env);return 0;}",
                "std::jmp_buf env; int f(){setjmp(env);return 0;}",
                "int f(){static std::jmp_buf env;setjmp(env);return 0;}",
                "int f(){const std::jmp_buf env={0};setjmp(env);return 0;}",
                "int f(){volatile std::jmp_buf env;setjmp(env);return 0;}",
                "int f(std::jmp_buf env){setjmp(env);return 0;}",
                "int f(){std::jmp_buf env;setjmp(*(&env));return 0;}")) {
            assertTrue(resolve("#include <csetjmp>\n" + source).hasErrors(), source);
        }
    }


    @Test void unavailableAndMalformedMacrosDoNotBecomeOrdinaryCalls() {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")), temp, List.of(), List.of(), "c++03");
        assertThrows(ParseException.class, () -> Parser.parse(unit,"#include <csetjmp>\n" + BODY.replace("BUFFER","std::jmp_buf")));
        for (String bad : List.of("setjmp()", "setjmp(env,env)", "::_setjmp()", "::_setjmp(env,env)")) {
            assertThrows(ParseException.class, () -> resolve("#include <csetjmp>\nint f(){std::jmp_buf env;"+bad+";return 0;}"),bad);
        }
        assertThrows(NullPointerException.class, () -> SetjmpExpression.create().build());
    }


    @Test void generationRejectsJavaAndDestinationWithoutCapabilityDespiteOldLeafProvenance() {
        var graph = resolve("#include <csetjmp>\n" + BODY.replace("BUFFER","std::jmp_buf")); healthy(graph);
        assertFalse(com.kniazkov.cpg.generation.Source.languages(graph).contains(Language.JAVA));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(graph,Language.JAVA));
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("destination.cpp")),temp,List.of(),List.of(),"c++03");
        var twoFiles=graph.edit().addFile(Parser.parse(unit,"#include <csetjmp>\n")).build();
        var source=twoFiles.files().getFirst(); var destination=twoFiles.files().getLast();
        var function=nodes(source,CallableDeclaration.class).getFirst();
        var editor=new ProjectEditor(twoFiles).withoutRevalidation();
        var remaining=new ArrayList<NodeBuilder<? extends Node>>();
        source.items().stream().filter(item -> item!=function).forEach(item -> remaining.add(item.edit()));
        editor.replace(source,source.edit().setItems(remaining).build());
        editor.replace(destination,destination.edit().addItem(function.edit()).build());
        var moved=editor.build();
        var save=nodes(moved.files().getLast(),SetjmpExpression.class).getFirst();
        assertTrue(save.provenance().orElseThrow().unit().configuration().platform().setjmp().isPresent());
        assertTrue(moved.files().getLast().provenance().orElseThrow().unit().configuration().platform().setjmp().isEmpty());
        assertFalse(com.kniazkov.cpg.generation.Source.languages(moved).contains(Language.CPP));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(moved,Language.CPP));
    }

    @Test void foldingAndRealOperandEditsKeepIndependentSnapshots() throws Exception {
        var graph = resolve("#include <csetjmp>\nint f(){std::jmp_buf env;std::jmp_buf spare;if(setjmp(env)==0)return 1;return 2;}");
        healthy(graph);
        var operation = nodes(graph,SetjmpExpression.class).getFirst();
        var editor = new ProjectEditor(graph);
        editor.replace(operation.buffer(), NameExpression.create().setName("spare").build());
        var edited = NameResolver.resolve(editor.build()); healthy(edited);
        var changed = nodes(edited,SetjmpExpression.class).getFirst();
        assertEquals("spare", ((NameExpression)changed.buffer()).name());
        assertEquals("spare", ((VariableDeclaration)((NameExpression)changed.buffer()).target().orElseThrow()).name());
        assertEquals("env", ((NameExpression)operation.buffer()).name());
        var folded = ConstantFolder.fold(edited); healthy(folded);
        assertEquals(1,nodes(folded,SetjmpExpression.class).size());
        String generated = Cpg.sources(folded, Language.CPP).get("main.cpp");
        assertTrue(generated.contains("spare")); healthy(resolve(generated));
        var badEditor=new ProjectEditor(graph);
        badEditor.replace(operation.buffer(),IntegerLiteral.create().setValue(0).build());
        var invalid=NameResolver.resolve(badEditor.build()); assertTrue(invalid.hasErrors());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(invalid,Language.CPP));
        healthy(graph);
    }
}
