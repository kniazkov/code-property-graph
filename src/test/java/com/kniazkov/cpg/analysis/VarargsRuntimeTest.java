/* Bounded C++03 variable-argument parsing, binding, types and editable operands. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VarargsRuntimeTest {
    @TempDir Path temp;
    private static PlatformProfile selected() {
        return PlatformProfile.builder().floatingFormat("long double", "binary80")
                .varargs("gcc-clang-builtin", "x86_64-sysv-lp64-v1").build();
    }
    private Build.TranslationUnit unit(PlatformProfile p) {
        return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")), temp, List.of(), List.of(), "c++03").withPlatform(p);
    }
    private Project resolve(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit(selected()), source)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    private static void healthy(Project graph) { assertFalse(graph.hasErrors(), () -> graph.parseProblems() + " / " + graph.semanticDiagnostics()); }
    private static final String BODY = "int f(register int n,...){std::va_list ap;va_start(ap,n);int value=va_arg(ap,int);va_end(ap);return value;}";

    @Test void boundOperandsTypedResultsAndUnknownVaListLayoutAcrossHeadersAndCopies() throws Exception {
        for (String headers : List.of("#include <cstdarg>\n", "#include <stdarg.h>\n", "#include <cstdarg>\n#include <stdarg.h>\n", "#include <stdarg.h>\n#include <cstdarg>\n")) {
            var original = resolve(headers + BODY.replace("std::va_list", headers.startsWith("#include <stdarg.h>") ? "va_list" : "std::va_list"));
            for (Project graph : List.of(original, original.edit().build(), NameResolver.resolve(original.edit().build()))) {
                healthy(graph); var ops = nodes(graph, VaListExpression.class); assertEquals(3, ops.size());
                assertEquals(List.of(VaListExpression.Kind.START, VaListExpression.Kind.ARG, VaListExpression.Kind.END), ops.stream().map(VaListExpression::kind).toList());
                var function = nodes(graph.files().getFirst(), CallableDeclaration.class).getFirst();
                assertSame(function.parameters().getLast(), ((NameExpression)ops.getFirst().lastParameter().orElseThrow()).target().orElseThrow());
                var list = nodes(function, VariableDeclaration.class).stream().filter(v -> v.name().equals("ap")).findFirst().orElseThrow();
                for (var op : ops) {
                    assertSame(list, ((NameExpression)op.list()).target().orElseThrow());
                    assertTrue(ConstantEvaluator.evaluate(op, selected()).isEmpty());
                    assertEquals(ValueCategory.RVALUE, op.semanticInfo().orElseThrow().category());
                    assertEquals(op.kind() == VaListExpression.Kind.ARG ? "int" : "void", TypeSemantics.expressionType(op, selected()).orElseThrow().canonicalIdentity());
                }
                var facts = TypeSemantics.describe(list.type().orElseThrow(), selected());
                assertTrue(facts.sizeBytes().isEmpty()); assertTrue(facts.alignmentBits().isEmpty());
                assertInstanceOf(OpaqueLibraryType.class, TypeSemantics.unqualified(list.type().orElseThrow()));
                String json = ProjectJson.encode(graph).toText("  "); com.kniazkov.json.Json.parse(json);
                assertTrue(json.contains("VaListExpression")); assertTrue(json.contains("stateRepresentation")); assertTrue(json.contains("lastParameter"));
            }
        }
    }

    @Test void promotedScalarPointerAndBoundAliasTypeOperandsResolve() {
        for (String type : List.of("int", "unsigned int", "long", "unsigned long", "double", "long double", "const int*", "void*", "Integer", "api::Pointer")) {
            String source = "#include <cstdarg>\ntypedef int Integer; namespace api{typedef const int* Pointer;} typedef std::va_list List;"
                    + "int f(int n,...){List ap;va_start(ap,n);va_arg(ap," + type + ");va_end(ap);return 0;}";
            var graph = resolve(source); healthy(graph);
            var arg = nodes(graph, VaListExpression.class).get(1);
            assertTrue(arg.requestedType().isPresent()); assertTrue(arg.lastParameter().isEmpty());
            assertEquals(TypeSemantics.unqualified(arg.requestedType().orElseThrow()).canonicalIdentity(), TypeSemantics.unqualified(TypeSemantics.expressionType(arg,selected()).orElseThrow()).canonicalIdentity());
        }
    }

    @Test void sizeofAndAllPipelineStagesPreserveRuntimeUnknownOperations() throws Exception {
        String source = "#include <cstdarg>\nint f(int n,...){std::va_list ap;va_start(ap,n);unsigned long size=sizeof(va_arg(ap,int));int value=va_arg(ap,int);va_end(ap);return value+size;}";
        Files.writeString(temp.resolve("main.cpp"),source);
        for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
            Project graph = Cpg.read(temp,new Cpg.Options(Language.CPP,stage,"c++03").withPlatform(selected())); healthy(graph);
            assertTrue(nodes(graph,VaListExpression.class).size() >= 3);
            for (var op : nodes(graph,VaListExpression.class)) assertTrue(ConstantEvaluator.evaluate(op,selected()).isEmpty());
            if (stage != Cpg.Stage.FOLDED) assertEquals(BigInteger.valueOf(4), ConstantEvaluator.evaluate(nodes(graph,SizeofExpression.class).getFirst(),selected()).orElseThrow().number());
            var reparse = resolve(Cpg.sources(graph,Language.CPP).get("main.cpp")); healthy(reparse);
            assertTrue(nodes(reparse,VaListExpression.class).size() >= 3);
        }
    }

    @Test void graphEditsChangeExtractionTypeNamesAndCallArguments() {
        var graph = resolve("#include <cstdarg>\n" + BODY + "int main(){return f(1,3);}"); healthy(graph);
        var editor = new ProjectEditor(graph);
        var arg = nodes(graph,VaListExpression.class).get(1);
        editor.replace(arg.requestedType().orElseThrow(), NamedType.create().setName("long").build());
        var call = nodes(graph.files().getFirst(),CallExpression.class).getFirst();
        editor.replace(call.arguments().getLast(), IntegerLiteral.create().setValue(7).setCppType("long").build());
        var list = nodes(graph.files().getFirst(),VariableDeclaration.class).stream().filter(v -> v.name().equals("ap")).findFirst().orElseThrow();
        editor.replace(list,list.edit().setName("cursor").build());
        var parameter = nodes(graph.files().getFirst(),ParameterDeclaration.class).getFirst();
        editor.replace(parameter,parameter.edit().setName("count").build());
        for (var name : nodes(graph.files().getFirst(),NameExpression.class)) {
            if (name.target().orElse(null) == list) editor.replace(name,name.edit().setName("cursor").build());
            if (name.target().orElse(null) == parameter) editor.replace(name,name.edit().setName("count").build());
        }
        var edited = NameResolver.resolve(editor.build()); healthy(edited);
        String emitted = Cpg.sources(edited,Language.CPP).get("main.cpp");
        assertTrue(emitted.contains("cursor")); assertTrue(emitted.contains("count"));
        assertTrue(emitted.contains("long")); assertTrue(emitted.contains("7")); assertNotEquals(Cpg.sources(graph,Language.CPP).get("main.cpp"),emitted);
        var reparsed=resolve(emitted); healthy(reparsed);
        assertEquals("long",nodes(reparsed,VaListExpression.class).get(1).requestedType().orElseThrow().canonicalIdentity());
        assertEquals("int",arg.requestedType().orElseThrow().canonicalIdentity());
    }

    @Test void malformedAndUnavailableBuiltinsDoNotBecomeOrdinaryCalls() {
        assertThrows(ParseException.class, () -> NameResolver.resolve(Project.create().addFile(Parser.parse(unit(PlatformProfile.defaults()),"#include <cstdarg>\n"+BODY)).build()));
        for (String bad : List.of("va_start(ap)","va_start(ap,n,n)","va_arg(ap)","va_arg(ap,1)","va_arg(ap,int,1)","va_end()","va_end(ap,n)","::__builtin_va_end(ap)","__builtin_va_arg(ap,int)=2"))
            assertThrows(ParseException.class, () -> resolve("#include <cstdarg>\nint f(int n,...){std::va_list ap;"+bad+";return 0;}"),bad);
    }

    @Test void knownMisuseAndUnsupportedValidCasesRemainDistinctAndBlockGeneration() {
        Map<String,String> cases = new LinkedHashMap<>();
        cases.put("int f(int a,int n,...){std::va_list ap;va_start(ap,a);}","invalid-varargs-last-parameter");
        cases.put("int f(int n,...){std::va_list ap;{int n=1;va_start(ap,n);}}","invalid-varargs-last-parameter");
        cases.put("int f(int n){std::va_list ap;va_start(ap,n);}","invalid-varargs-function");
        cases.put("int f(int& n,...){std::va_list ap;va_start(ap,n);}","invalid-varargs-last-parameter-type");
        cases.put("int f(int n[],...){std::va_list ap;va_start(ap,n);}","invalid-varargs-last-parameter-type");
        cases.put("int f(int n(),...){std::va_list ap;va_start(ap,n);}","invalid-varargs-last-parameter-type");
        cases.put("int f(float n,...){std::va_list ap;va_start(ap,n);}","invalid-varargs-last-parameter-type");
        cases.put("enum E{A};int f(E n,...){std::va_list ap;va_start(ap,n);va_end(ap);return 0;}","invalid-varargs-last-parameter-type");
        cases.put("int helper(std::va_list ap,int n,...){return va_arg(ap,int);}","unsupported-varargs-list-storage");
        cases.put("typedef std::va_list List;int helper(List ap,int n,...){return va_arg(ap,int);}","unsupported-varargs-list-storage");
        cases.put("int helper(int ap,int n,...){return va_arg(ap,int);}","invalid-varargs-list-binding");
        cases.put("int helper(int n,...){return va_arg(missing,int);}","invalid-varargs-list-binding");
        cases.put("typedef int va_list;int f(int n,...){va_list ap;va_start(ap,n);}","invalid-varargs-list-type");
        cases.put("int f(int n,...){const std::va_list ap;va_start(ap,n);}","unsupported-varargs-qualified-list");
        cases.put("int f(int n,...){std::va_list ap;std::va_list* p=&ap;va_end(*p);}","unsupported-varargs-list-expression");
        cases.put("struct X{std::va_list ap;};int f(int n,...){X x;va_end(x.ap);}","unsupported-varargs-list-expression");
        cases.put("int f(int n,...){std::va_list ap;std::va_list copy=ap;va_end(ap);}","unsupported-varargs-escaped-list");
        cases.put("void sink(std::va_list);int f(int n,...){std::va_list ap;va_start(ap,n);sink(ap);va_end(ap);}","unsupported-varargs-escaped-list");
        cases.put("int f(int n,...){std::va_list ap;va_arg(ap,n);}","invalid-varargs-argument-type");
        for (String type : List.of("float","char","short","bool","wchar_t")) cases.put("int f(int n,...){std::va_list ap;va_arg(ap,"+type+");}","invalid-varargs-promoted-argument-type");
        for (String type : List.of("X","U","E","int X::*","int[2]","int (*)(int)")) cases.put("struct X{int x;};union U{int x;};enum E{a};int f(int n,...){std::va_list ap;va_arg(ap,"+type+");}","unsupported-varargs-argument-type");
        for (var entry : cases.entrySet()) {
            var graph = resolve("#include <cstdarg>\n"+entry.getKey());
            assertTrue(graph.semanticDiagnostics().stream().anyMatch(d -> d.code().equals(entry.getValue())), () -> entry + " / " + graph.semanticDiagnostics());
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(graph,Language.CPP),entry.getKey());
        }
    }
}
