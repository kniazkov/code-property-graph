/* Bound pointer typedefs and type-only C++03 va_arg validation. MIT License. */
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

class TypedPointerVarargsTest {
    @TempDir Path temp;
    private static final PlatformProfile PROFILE = PlatformProfile.builder().floatingFormat("long double", "binary80")
            .varargs("gcc-clang-builtin", "x86_64-sysv-lp64-v1").build();
    private static final String ALIASES = "namespace types { typedef int (*Callback)(int); typedef Callback Alias; typedef int (*Row)[3]; typedef const int (*ConstRow)[3]; }";
    private Project resolve(String body) { return resolveWithProfile("#include <cstdarg>\n" + body, PROFILE); }
    private Project resolveWithProfile(String source, PlatformProfile profile) {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")), temp,
                List.of(), List.of(), "c++03").withPlatform(profile);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }}); return result;
    }
    private static void healthy(Project graph) { assertFalse(graph.hasErrors(), () -> graph.parseProblems() + " / " + graph.semanticDiagnostics()); }
    private static void diagnostic(Project graph, String code) {
        assertTrue(graph.semanticDiagnostics().stream().anyMatch(d -> d.code().equals(code)), () -> graph.semanticDiagnostics().toString());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(graph, Language.CPP));
    }
    @Test void boundWrittenAliasesRetainSignaturesUnknownValuesAndSnapshotLocalReferences() {
        var original = resolve(ALIASES + "int f(types::Alias last,...){std::va_list ap;va_start(ap,last);"
                + "types::Alias callback=va_arg(ap,types::Alias);types::Row row=va_arg(ap,types::Row);"
                + "types::ConstRow fixed=va_arg(ap,types::ConstRow);va_end(ap);return callback((*row)[1])+(*fixed)[0];}");
        for (var graph : List.of(original, original.edit().build(), NameResolver.resolve(original.edit().build()))) {
            healthy(graph); Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>()); owned.addAll(nodes(graph, Node.class));
            for (var arg : nodes(graph, VaListExpression.class).stream().filter(v -> v.kind() == VaListExpression.Kind.ARG).toList()) {
                var written = assertInstanceOf(NamedType.class, arg.requestedType().orElseThrow());
                assertTrue(written.alias().isPresent()); assertTrue(owned.contains(written.alias().orElseThrow()));
                assertInstanceOf(PointerType.class, TypeSemantics.unqualified(TypeSemantics.expressionType(arg, PROFILE).orElseThrow()));
                assertTrue(ConstantEvaluator.evaluate(arg, PROFILE).isEmpty());
            }
            var call = nodes(graph.files().getFirst(), CallExpression.class).getFirst();
            assertTrue(call.target().isEmpty());
            var signature = TypeSemantics.callType(call, PROFILE).orElseThrow();
            assertEquals("int", signature.returnType().canonicalIdentity()); assertEquals(1, signature.parameters().size());
            String emitted = Cpg.sources(graph, Language.CPP).get("main.cpp");
            assertTrue(emitted.contains("types::Alias")); assertTrue(emitted.contains("types::Row"));
            healthy(resolveWithProfile(emitted, PROFILE));
            String json = ProjectJson.encode(graph).toText("  ");
            assertTrue(json.contains("VaListExpression")); assertTrue(json.contains("requestedType"));
        }
    }
    @Test void pointerAliasesAreValidAnchorsButDeclaredArraysFunctionsAndReferencesAreNot() {
        for (String anchor : List.of("types::Alias n", "types::Row n", "types::ConstRow n"))
            healthy(resolve(ALIASES + "int f("+anchor+",...){std::va_list ap;va_start(ap,n);int value=va_arg(ap,int);va_end(ap);return value;}"));
        for (String anchor : List.of("int n[3]", "int n(int)", "types::Row& n", "Array n", "Function n"))
            diagnostic(resolve(ALIASES + "typedef int Array[3];typedef int Function(int);int f("+anchor+",...){std::va_list ap;va_start(ap,n);}"), "invalid-varargs-last-parameter-type");
    }
    @Test void typeOnlyScalarsEnumsAndAliasesDoNotRequireRuntimeOwnerOrStorage() {
        String prefix = "enum E{one=1};typedef E EnumAlias;typedef float FloatAlias;typedef std::va_list List;";
        for (String type : List.of("float", "bool", "char", "short", "wchar_t", "E", "EnumAlias", "FloatAlias")) {
            for (String body : List.of("unsigned long f(int n,...){List ap;return sizeof(va_arg(ap,TYPE));}",
                    "unsigned long f(){List ap;return sizeof(va_arg(ap,TYPE));}",
                    "List ap;unsigned long bytes=sizeof(va_arg(ap,TYPE));",
                    "unsigned long f(){static List ap;return sizeof(va_arg(ap,TYPE));}",
                    "extern List ap;unsigned long bytes=sizeof(va_arg(ap,TYPE));",
                    "unsigned long f(List ap){return sizeof(va_arg(ap,TYPE));}")) {
                Project graph = resolve(prefix + body.replace("TYPE",type)); healthy(graph);
                healthy(NameResolver.resolve(graph.edit().build()));
                healthy(resolveWithProfile(Cpg.sources(graph,Language.CPP).get("main.cpp"),PROFILE));
                if (type.equals("E") || type.equals("EnumAlias"))
                    assertTrue(TypeSemantics.sizeof(nodes(graph,SizeofExpression.class).getFirst(),PROFILE).isEmpty(), "Do not invent enum layout");
            }
        }
    }
    @Test void typeOnlyDoesNotRelaxBindingsShapesOrRuntimeRules() {
        for (String body : List.of("typedef int List;List ap;unsigned long n=sizeof(va_arg(ap,float));",
                "unsigned long n=sizeof(va_arg(missing,float));", "std::va_list ap;int x;unsigned long n=sizeof(va_arg(ap,x));")) {
            var graph = resolve(body); assertTrue(graph.hasErrors()); assertThrows(IllegalArgumentException.class, () -> Cpg.sources(graph,Language.CPP));
        }
        for (String request : List.of("int (*)(int)", "int (*)[3]", "int[3]", "int X::*", "X", "void"))
            diagnostic(resolve("struct X{int x;};std::va_list ap;unsigned long n=sizeof(va_arg(ap,"+request+"));"),"unsupported-varargs-argument-type");
        for (String type : List.of("float", "E", "Alias"))
            diagnostic(resolve("enum E{one};typedef E Alias;int f(int n,...){std::va_list ap;va_arg(ap,"+type+");}"),"invalid-varargs-promoted-argument-type");
        for (String storage : List.of("static ", "extern "))
            diagnostic(resolve("int f(int n,...){"+storage+"std::va_list ap;return va_arg(ap,int);}"),"unsupported-varargs-list-storage");
        diagnostic(resolve("int f(std::va_list ap,int n,...){return va_arg(ap,int);}"),"unsupported-varargs-list-storage");
        diagnostic(resolve("int f(){std::va_list ap;return va_arg(ap,int);}"),"unsupported-varargs-function");
        diagnostic(resolve("std::va_list ap;int n=va_arg(ap,int);"),"unsupported-varargs-function");
        for (String qualifier : List.of("const ","volatile "))
            diagnostic(resolve(qualifier+"std::va_list ap;unsigned long n=sizeof(va_arg(ap,float));"),"unsupported-varargs-qualified-list");
        diagnostic(resolve("unsigned long f(std::va_list& ap){return sizeof(va_arg(ap,float));}"),"unsupported-varargs-list-expression");
    }
    @Test void unsupportedPointerShapesRemainExplicitAndNoCapabilityStillFails() {
        for (String alias : List.of("typedef int (*Alias)[];", "extern int n;typedef int (*Alias)[n];",
                "typedef int (*Alias)[0];", "struct X{int x;};typedef int X::* Alias;",
                "struct X{int f();};typedef int (X::*Alias)();"))
            diagnostic(resolve(alias+"int f(int n,...){std::va_list ap;va_arg(ap,Alias);}"),"unsupported-varargs-argument-type");
        assertThrows(ParseException.class, () -> resolveWithProfile("#include <cstdarg>\n"+ALIASES+"int f(int n,...){std::va_list ap;va_arg(ap,types::Alias);}",PlatformProfile.defaults()));
    }
    @Test void actualAliasEditsAreRevalidatedAndRestoredWithoutChangingOriginal() {
        var original = resolve("typedef int (*Callback)(int);int f(int n,...){std::va_list ap;va_start(ap,n);Callback cb=va_arg(ap,Callback);va_end(ap);return cb(3);}"); healthy(original);
        String json = ProjectJson.encode(original).toText("  "); var source = Cpg.sources(original,Language.CPP);
        for (TypeNode type : List.of(NamedType.create().setName("float").build(), ArrayType.create().setElementType(NamedType.create().setName("int")).setBound(IntegerLiteral.create().setValue(3)).build())) {
            var alias = nodes(original,TypedefDeclaration.class).stream().filter(t -> t.name().equals("Callback")).findFirst().orElseThrow();
            var editor = new ProjectEditor(original); editor.replace(alias.type(),type);
            var changed = NameResolver.resolve(editor.build()); assertTrue(changed.hasErrors());
            var editedAlias = nodes(changed,TypedefDeclaration.class).stream().filter(t -> t.name().equals("Callback")).findFirst().orElseThrow();
            var restore = new ProjectEditor(changed); restore.replace(editedAlias.type(),alias.type());
            var restored = NameResolver.resolve(restore.build()); healthy(restored);
            assertEquals(source,Cpg.sources(restored,Language.CPP));
        }
        assertEquals(json,ProjectJson.encode(original).toText("  ")); assertEquals(source,Cpg.sources(original,Language.CPP));
    }
    @Test void typeidOfKnownNonclassOperandsIsTypeOnlyToo() {
        healthy(resolve("#include <typeinfo>\nenum E{one};typedef E Alias;void f(){std::va_list ap;typeid(va_arg(ap,float));typeid(va_arg(ap,Alias));}"));
    }
    @Test void namedFixedBoundsAndTheirEditsUseSnapshotOwnedConstants() {
        var graph = resolve("const int count=3;typedef int (*Row)[count];int f(int n,...){std::va_list ap;va_arg(ap,Row);return 0;}"); healthy(graph);
        var count = nodes(graph.files().getFirst(),VariableDeclaration.class).stream().filter(v -> v.name().equals("count")).findFirst().orElseThrow();
        var edit = new ProjectEditor(graph); edit.replace(count.initializer().orElseThrow(),IntegerLiteral.create().setValue(0).build());
        diagnostic(NameResolver.resolve(edit.build()),"unsupported-varargs-argument-type");
        healthy(graph);
    }
    @Test void cyclicForeignAndDependentAliasBindingsCannotEnterTheSubset() {
        var original = resolve("typedef int (*Callback)(int);std::va_list ap;unsigned long size=sizeof(va_arg(ap,Callback));"); healthy(original);
        var alias = nodes(original,TypedefDeclaration.class).stream().filter(t -> t.name().equals("Callback")).findFirst().orElseThrow();
        var cycle = new ProjectEditor(original).withoutRevalidation();
        cycle.replace(alias.type(),NamedType.create().setName("Callback").setAlias(alias.edit()).build());
        diagnostic(VarargsSemantics.analyze(cycle.build()),"invalid-varargs-argument-type");
        var dependent = new ProjectEditor(original).withoutRevalidation();
        dependent.replace(alias.type(),PointerType.create().setPointee(NamedType.create().setName("T").setDependent(true)).build());
        diagnostic(VarargsSemantics.analyze(dependent.build()),"unsupported-varargs-argument-type");
        var foreign = TypedefDeclaration.create().setName("Foreign").setType(NamedType.create().setName("int"));
        var use = TypedefDeclaration.create().setName("Use").setType(NamedType.create().setName("Foreign").setAlias(foreign));
        assertThrows(IllegalStateException.class, () -> Project.create().addFile(SourceFile.create().setPath("foreign.cpp").setLanguage(Language.CPP).addItem(use)).build());
    }

    @Test void typeOnlyUsesDoNotCertifyInitializersOrImplicitMembers() {
        for (String source : List.of("void f(){std::va_list ap=0;sizeof(va_arg(ap,float));}",
                "void f(){std::va_list ap={};sizeof(va_arg(ap,float));}",
                "void f(){static std::va_list ap=0;sizeof(va_arg(ap,float));}",
                "void f(std::va_list original){std::va_list ap=original;sizeof(va_arg(ap,float));}",
                "void f(std::va_list incoming){std::va_list copied=incoming;std::va_list ap;sizeof(va_arg(ap,float));}",
                "std::va_list ap=0;unsigned long bytes=sizeof(va_arg(ap,float));",
                "std::va_list original;std::va_list copied=original;std::va_list ap;unsigned long bytes=sizeof(va_arg(ap,float));"))
            diagnostic(resolve(source),"unsupported-varargs-list-initialization");
        for (String storage : List.of("", "static "))
            diagnostic(resolve("struct S{"+storage+"std::va_list ap;void f(){sizeof(va_arg(ap,float));}};"),"unsupported-varargs-list-expression");
    }
    @Test void malformedAliasedFunctionAndArrayShapesRemainExcluded() {
        for (String alias : List.of("typedef int (*Alias)()[3];", "typedef int Function();typedef Function (*Alias)();",
                "typedef int (*Alias)(void,int);", "typedef int (*Alias)(const void);",
                "typedef const void Void;typedef int (*Alias)(Void);",
                "typedef int Function(int);typedef const Function* Alias;",
                "typedef int Function() const;typedef Function* Alias;", "typedef int Function() volatile;typedef Function* Alias;",
                "typedef int (*Alias)() const;", "typedef void (*Alias)[3];", "typedef int& Ref;typedef Ref (*Alias)[3];",
                "typedef int Function();typedef Function (*Alias)[3];", "typedef void Void;typedef Void (*Alias)[3];")) {
            diagnostic(resolve(alias+"int f(int n,...){std::va_list ap;va_arg(ap,Alias);}"),"unsupported-varargs-argument-type");
            diagnostic(resolve(alias+"std::va_list ap;unsigned long n=sizeof(va_arg(ap,Alias));"),"unsupported-varargs-argument-type");
        }
        for (String alias : List.of("typedef void (*Alias)();", "typedef void (*Alias)(void);", "typedef void Void;typedef int (*Alias)(Void);",
                "typedef int(*Pointer)(int);typedef const Pointer Alias;", "typedef int& (*Alias)(int&);",
                "struct Incomplete;typedef Incomplete* (*Alias)(Incomplete*);"))
            healthy(resolve(alias+"int f(int n,...){std::va_list ap;va_arg(ap,Alias);return 0;}"));
    }

}
