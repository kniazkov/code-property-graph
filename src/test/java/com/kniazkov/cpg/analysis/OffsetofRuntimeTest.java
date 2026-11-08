/* Explicit C++03 offsetof capability, structured operands, and bounded ICE evaluation. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OffsetofRuntimeTest {
    @TempDir Path temp;
    private static final String EMISSION = "gcc-clang-builtin";
    private static final String LAYOUT = "x86_64-sysv-lp64-v1";
    private static PlatformProfile selected() { return selected("2147483647"); }
    private static PlatformProfile selected(String limit) {
        return PlatformProfile.builder().offsetof(EMISSION, LAYOUT, limit).build();
    }
    private Build.TranslationUnit unit(PlatformProfile profile) {
        return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")), temp,
                List.of(), List.of(), "c++03").withPlatform(profile);
    }
    private Project resolve(PlatformProfile profile, String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit(profile), source)).build());
    }
    private static final String SHAPES = """
            struct Inner { char tag; int value; };
            union Payload { char c; double d; Inner item; };
            struct Outer { char lead; Payload payload; Inner list[3]; unsigned long tail; };
            typedef Outer Alias;
            namespace api { typedef Alias Record; }
            """;
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }
        });
        return result;
    }
    private static VariableDeclaration variable(Project project, String name) {
        return nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }
    private static OffsetofExpression offset(Project project, String name) {
        return assertInstanceOf(OffsetofExpression.class,
                written(variable(project, name).initializer().orElseThrow()));
    }
    private static Expression written(Expression expression) {
        while (expression instanceof ConversionExpression conversion && !conversion.isExplicit())
            expression = conversion.operand();
        return expression;
    }
    private static BigInteger value(Project project, Expression expression, PlatformProfile profile) {
        var result = new IntegralConstants(project, profile).evaluate(expression).orElseThrow();
        assertEquals("unsigned long", result.type().spelling());
        return result.number();
    }
    private static void healthy(Project project) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
    }

    @Test void explicitCapabilityHasStrictSchemaRoundTripsAndNoRuntimeOrFloatingFormatDependency() throws Exception {
        var profile = selected();
        assertEquals("unknown", profile.cRuntime());
        assertTrue(PlatformProfile.defaults().offsetof().isEmpty());
        assertTrue(PlatformProfile.builder().cRuntime("glibc").build().offsetof().isEmpty());
        assertEquals(profile, profile.toBuilder().build());
        assertEquals(profile, PlatformProfile.fromSettings(profile.toSettings()));
        assertNotEquals(profile.identityHash(), PlatformProfile.defaults().identityHash());
        assertNotEquals(profile.identityHash(), selected("64").identityHash());
        var model = profile.offsetof().orElseThrow();
        assertEquals(EMISSION, model.emission());
        assertEquals(LAYOUT, model.layout());
        assertEquals(new BigInteger("2147483647"), model.supportedObjectBytes());
        assertEquals(Map.of("emission", EMISSION, "layout", LAYOUT,
                "supportedObjectBytes", "2147483647"), profile.toSettings().get("offsetof"));
        var empty = new LinkedHashMap<String, Object>(profile.toSettings());
        empty.put("offsetof", Map.of());
        assertTrue(PlatformProfile.fromSettings(empty).offsetof().isEmpty());
        for (String format : List.of("unknown", "binary80", "binary128"))
            assertTrue(profile.toBuilder().floatingFormat("long double", format).build().offsetof().isPresent());
        assertTrue(profile.toBuilder().cRuntime("glibc").build().offsetof().isPresent());
        Path file = temp.resolve("profile.json");
        Files.writeString(file, Json.serialize(profile.toSettings()));
        assertEquals(profile, PlatformProfile.read(file));
        for (String limit : List.of("", "0", "-1", "+1", "01", "1.0", " 1", "1 ", "2147483648", "18446744073709551615"))
            assertThrows(IllegalArgumentException.class, () -> selected(limit), limit);
        assertEquals(BigInteger.ONE, selected("1").offsetof().orElseThrow().supportedObjectBytes());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().offsetof("user_function", LAYOUT, "64").build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().offsetof(EMISSION, "lp64", "64").build());
        for (String body : List.of("null", "[]", "true", "{\"emission\":\"gcc-clang-builtin\"}",
                "{\"emission\":\"gcc-clang-builtin\",\"layout\":\"x86_64-sysv-lp64-v1\",\"supportedObjectBytes\":64}",
                "{\"emission\":\"gcc-clang-builtin\",\"layout\":\"x86_64-sysv-lp64-v1\",\"supportedObjectBytes\":\"64\",\"extra\":1}",
                "{\"emission\":\"gcc-clang-builtin\",\"emission\":\"gcc-clang-builtin\",\"layout\":\"x86_64-sysv-lp64-v1\",\"supportedObjectBytes\":\"64\"}")) {
            Files.writeString(file, "{\"inherits\":\"default\",\"offsetof\":" + body + "}");
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.read(file), body);
        }
    }

    @Test void everyScalarAndLayoutFactIsRequiredRatherThanInferredFromLp64() {
        var profile = selected();
        for (String type : List.of("bool", "char", "short", "int", "long", "wchar_t", "float", "double", "long double", "pointer")) {
            assertThrows(IllegalArgumentException.class,
                    () -> profile.toBuilder().sizeBits(type, profile.sizeBits(type).orElseThrow() * 2).build(), type + " storage");
            assertThrows(IllegalArgumentException.class,
                    () -> profile.toBuilder().alignmentBits(type, null).build(), type + " missing alignment");
            int alignment = profile.alignmentBits(type).orElseThrow();
            if (alignment > 8) assertThrows(IllegalArgumentException.class,
                    () -> profile.toBuilder().alignmentBits(type, alignment / 2).build(), type + " conflicting alignment");
        }
        assertThrows(IllegalArgumentException.class, () -> profile.toBuilder().sizeType("unsigned int").build());
        assertThrows(IllegalArgumentException.class, () -> profile.toBuilder().bitsPerByte(16).build());
        assertThrows(IllegalArgumentException.class, () -> profile.toBuilder().aggregateLayout("unknown").build());
        for (String policy : List.of("unknown", "packed1"))
            assertThrows(IllegalArgumentException.class, () -> profile.toBuilder().fieldAlignment(policy).build());
        assertThrows(IllegalArgumentException.class, () -> profile.toBuilder().aggregateAlignment("unknown").build());
        assertThrows(IllegalArgumentException.class, () -> profile.toBuilder().tailPadding("unknown").build());
    }

    @Test void structuredOperandsHaveExactSizeTypeBindingsAndTrueIntegralConstantContexts() throws Exception {
        var profile = selected();
        var project = resolve(profile, "#include <cstddef>\n" + SHAPES + """
                const int last = 2;
                unsigned long zero = offsetof(Outer, lead);
                unsigned long nested = offsetof(api::Record, list[last].value);
                unsigned long unionOffset = offsetof(Payload, d);
                unsigned long insideUnion = offsetof(Outer, payload.item.value);
                enum { position = offsetof(Outer, tail), next = position + 1 };
                char extent[offsetof(Alias, list[2].value) + 1];
                int f(unsigned long n) { switch(n) { case offsetof(Outer, tail): return 1; default: return 0; } }
                """);
        for (var snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
            healthy(snapshot);
            assertEquals(BigInteger.ZERO, value(snapshot, offset(snapshot, "zero"), profile));
            assertEquals(BigInteger.valueOf(36), value(snapshot, offset(snapshot, "nested"), profile));
            assertEquals(BigInteger.ZERO, value(snapshot, offset(snapshot, "unionOffset"), profile));
            assertEquals(BigInteger.valueOf(12), value(snapshot, offset(snapshot, "insideUnion"), profile));
            var nested = offset(snapshot, "nested");
            assertEquals("api::Record", nested.type().source(Language.CPP));
            assertEquals(3, nested.steps().size());
            assertEquals("list", assertInstanceOf(OffsetofExpression.Member.class, nested.steps().get(0)).name());
            assertInstanceOf(NameExpression.class, written(
                    assertInstanceOf(OffsetofExpression.Index.class, nested.steps().get(1)).expression()));
            assertEquals("value", assertInstanceOf(OffsetofExpression.Member.class, nested.steps().get(2)).name());
            var owned = nodes(snapshot, Node.class);
            for (var expression : nodes(snapshot, OffsetofExpression.class)) {
                assertEquals("unsigned long", TypeSemantics.expressionType(expression, profile).orElseThrow().canonicalIdentity());
                assertEquals(ValueCategory.RVALUE, TypeSemantics.valueCategory(expression, profile));
                assertTrue(expression.children().contains(expression.type()));
                assertTrue(expression.children().containsAll(expression.steps()));
                for (var member : nodes(expression, OffsetofExpression.Member.class))
                    assertTrue(owned.contains(member.target().orElseThrow()), "Member links must belong to this snapshot");
                for (var use : nodes(expression.type(), NamedType.class))
                    for (var target : use.references()) assertTrue(owned.contains(target));
            }
            var constants = new IntegralConstants(snapshot, profile);
            var enumeration = nodes(snapshot, EnumDeclaration.class).getFirst();
            assertEquals(BigInteger.valueOf(40), constants.apply(enumeration.enumerators().get(0)).orElseThrow().number());
            assertEquals(BigInteger.valueOf(41), constants.apply(enumeration.enumerators().get(1)).orElseThrow().number());
            var bound = assertInstanceOf(ArrayType.class, variable(snapshot, "extent").type().orElseThrow()).bound().orElseThrow();
            assertEquals(BigInteger.valueOf(37), constants.evaluate(bound).orElseThrow().number());
            var label = nodes(snapshot, CaseStatement.class).stream().filter(c -> c.value().isPresent()).findFirst().orElseThrow();
            assertEquals(BigInteger.valueOf(40), value(snapshot, label.value().orElseThrow(), profile));
            assertTrue(nodes(snapshot, CallExpression.class).isEmpty(), "The builtin is not a fabricated runtime call");
            String json = ProjectJson.encode(snapshot).toText("  ");
            Json.parse(json);
            for (String fact : List.of("OffsetofExpression", "list", "value", "api::Record", "gcc-clang-builtin"))
                assertTrue(json.contains(fact), fact + " must be exported as structured syntax/profile data");
        }
    }

    @Test void typeIndexAndMemberEditsRecomputeInsteadOfKeepingStaleOffsets() {
        var profile = selected();
        var original = resolve(profile, SHAPES + "unsigned long selected = __builtin_offsetof(Outer, list[2].value);");
        healthy(original);
        assertEquals(BigInteger.valueOf(36), value(original, offset(original, "selected"), profile));
        var typeEdit = new ProjectEditor(original);
        typeEdit.replace(variable(original, "value").type().orElseThrow(), NamedType.create().setName("long").build());
        var changed = NameResolver.resolve(typeEdit.build());
        healthy(changed);
        assertEquals(BigInteger.valueOf(64), value(changed, offset(changed, "selected"), profile));
        var indexEdit = new ProjectEditor(original);
        indexEdit.replace(assertInstanceOf(OffsetofExpression.Index.class, offset(original, "selected").steps().get(1)).expression(),
                IntegerLiteral.create().setValue(1).build());
        var indexed = NameResolver.resolve(indexEdit.build());
        healthy(indexed);
        assertEquals(BigInteger.valueOf(28), value(indexed, offset(indexed, "selected"), profile));
        var memberEdit = new ProjectEditor(original);
        memberEdit.replace(offset(original, "selected").steps().getLast(), OffsetofExpression.Member.create().setName("tag").build());
        var memberChanged = NameResolver.resolve(memberEdit.build());
        healthy(memberChanged);
        assertEquals(BigInteger.valueOf(32), value(memberChanged, offset(memberChanged, "selected"), profile));
        for (var snapshot : List.of(changed, indexed, memberChanged)) {
            String source = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
            assertTrue(source.contains("__builtin_offsetof"));
            var reparsed = resolve(profile, source);
            healthy(reparsed);
            assertEquals(value(snapshot, offset(snapshot, "selected"), profile), value(reparsed, offset(reparsed, "selected"), profile));
        }
        assertEquals(BigInteger.valueOf(36), value(original, offset(original, "selected"), profile));
    }

    @Test void foldedMacroArrayBoundPreservesSiblingSyntaxAndRecomputesAfterOwnerEdit() throws Exception {
        var profile = selected();
        String source = "#include <cstddef>\nstruct S {char tag; int value;}; "
                + "char extent[offsetof(S,value)+1]; unsigned long selected=offsetof(S,value);";
        Files.writeString(temp.resolve("main.cpp"), source);
        var folded = Cpg.read(temp, new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED, "c++03").withPlatform(profile));
        healthy(folded);
        assertEquals(BigInteger.valueOf(4), value(folded, offset(folded, "selected"), profile));
        var bound = assertInstanceOf(ArrayType.class, variable(folded, "extent").type().orElseThrow()).bound().orElseThrow();
        assertEquals(BigInteger.valueOf(5), value(folded, bound, profile));
        String generated = Cpg.sources(folded, Language.CPP).get("main.cpp");
        assertTrue(generated.contains("offsetof(S,value)+1"), generated);
        assertEquals(source, generated, "Folding a sibling int-to-size_t conversion must not require flattening the macro-bound declaration");
        healthy(resolve(profile, generated));
        var editor = new ProjectEditor(folded);
        editor.replace(variable(folded, "value").type().orElseThrow(), NamedType.create().setName("long").build());
        var edited = NameResolver.resolve(editor.build());
        healthy(edited);
        var reparsed = resolve(profile, Cpg.sources(edited, Language.CPP).get("main.cpp"));
        healthy(reparsed);
        assertEquals(BigInteger.valueOf(8), value(reparsed, offset(reparsed, "selected"), profile));
        var changedBound = assertInstanceOf(ArrayType.class, variable(reparsed, "extent").type().orElseThrow()).bound().orElseThrow();
        assertEquals(BigInteger.valueOf(9), value(reparsed, changedBound, profile));
    }

    @Test void declarationRenameUpdatesDirectDesignatorAndRetainsMacroEditSafety() {
        var profile = selected();
        String source = SHAPES + "unsigned long selected = __builtin_offsetof(Outer, list[2].value);";
        var original = resolve(profile, source);
        String before = Cpg.sources(original, Language.CPP).get("main.cpp");
        var renamed = com.kniazkov.cpg.utilities.DeclarationRename.rename(original, variable(original, "value"), "renamed");
        healthy(renamed);
        String generated = Cpg.sources(renamed, Language.CPP).get("main.cpp");
        assertTrue(generated.contains("int renamed;"), generated);
        assertTrue(generated.contains("list[2].renamed"), generated);
        var reparsed = resolve(profile, generated);
        healthy(reparsed);
        assertEquals(BigInteger.valueOf(36), value(reparsed, offset(reparsed, "selected"), profile));
        var terminal = assertInstanceOf(OffsetofExpression.Member.class, offset(reparsed, "selected").steps().getLast());
        assertEquals("renamed", terminal.name());
        assertSame(variable(reparsed, "renamed"), terminal.target().orElseThrow());
        assertEquals(before, Cpg.sources(original, Language.CPP).get("main.cpp"));
        var macro = resolve(profile, "#include <cstddef>\n" + SHAPES + "unsigned long selected=offsetof(Outer,list[2].value);");
        assertThrows(IllegalArgumentException.class, () -> com.kniazkov.cpg.utilities.DeclarationRename
                .rename(macro, variable(macro, "value"), "renamed"), "Macro-origin edits retain the existing safety boundary");
    }

    @Test void exactBuiltinTokenCannotEscapeAsAnOrdinaryQualifiedOrDeclaredName() {
        String prefix = "struct S {int value; int array[3];}; ";
        for (String suffix : List.of(
                "int x=__builtin_offsetof();", "int x=__builtin_offsetof(S);", "int x=__builtin_offsetof(S,value,0);",
                "int x=__builtin_offsetof(S,);", "int x=__builtin_offsetof(S,.value);", "int x=__builtin_offsetof(S,value.);",
                "int x=__builtin_offsetof(S,array[]);", "int x=__builtin_offsetof(S,value());", "int x=__builtin_offsetof(S,value->x);",
                "int x=__builtin_offsetof(S,value=1);", "int x=::__builtin_offsetof(S,value);",
                "namespace n{} int x=n::__builtin_offsetof(S,value);", "int __builtin_offsetof;",
                "int __builtin_offsetof(int);", "struct X {int __builtin_offsetof;};",
                "void f(){S x; x.__builtin_offsetof;}", "void f(){int (*p)(int)=&__builtin_offsetof;}",
                "int x=__builtin_offsetof(S,value)(1);", "int x=__builtin_offsetofx(S,value);"))
            assertThrows(ParseException.class, () -> resolve(selected(), prefix + suffix), suffix);
        assertThrows(ParseException.class, () -> resolve(PlatformProfile.defaults(), prefix + "int x=__builtin_offsetof(S,value);"));
        assertThrows(ParseException.class, () -> resolve(selected(), "#include <cstddef>\ntemplate<class T,class U> struct P {int value;}; int x=offsetof(P<int,int>,value);"),
                "Preprocessing commas are not C++ template commas");
    }

    @Test void diagnosticsDistinguishNonPodUnsupportedLayoutAndInvalidMemberPaths() {
        for (var test : List.of(
                List.of("struct S {static int member; int x;};", "member", "STATIC_MEMBER"),
                List.of("struct S {void member(); int x;};", "member", "FUNCTION_MEMBER"),
                List.of("struct S {int x;};", "missing", "MISSING_MEMBER"),
                List.of("struct S {unsigned int member:2;};", "member", "BIT_FIELD"),
                List.of("struct S {unsigned int bit:2; int member;};", "member", "BIT_FIELD"),
                List.of("struct S {S(); int member;};", "member", "NOT_POD"),
                List.of("struct S {private: int member;};", "member", "NOT_POD"),
                List.of("struct S {int& member;};", "member", "NOT_POD"),
                List.of("enum E {one}; struct S {E member;};", "member", "UNSUPPORTED_TYPE"),
                List.of("struct A {int value;}; struct S {int A::*member;};", "member", "UNSUPPORTED_TYPE"),
                List.of("struct S;", "member", "INCOMPLETE_TYPE"),
                List.of("struct S {int member[3];};", "member[3]", "INDEX_OUT_OF_BOUNDS"),
                List.of("struct S {int member[3];};", "member[-1]", "INDEX_OUT_OF_BOUNDS"),
                List.of("int f(); struct S {int member[3];};", "member[f()]", "NON_CONSTANT_INDEX"),
                List.of("int f(); struct S {int member[3];};", "member[0?f():1]", "NON_CONSTANT_INDEX"),
                List.of("int i; struct S {int member[3];};", "member[i++]", "NON_CONSTANT_INDEX"))) {
            var project = resolve(selected(), test.get(0) + " unsigned long result=__builtin_offsetof(S," + test.get(1) + ");");
            assertTrue(project.hasErrors(), test.toString());
            String diagnostics = project.semanticDiagnostics().toString().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            assertTrue(diagnostics.contains(test.get(2)), test + " / " + diagnostics);
            assertTrue(new IntegralConstants(project, selected()).evaluate(offset(project, "result")).isEmpty());
        }
    }

    @Test void sharedConstantDependenciesTerminateAndDoNotInventOffsets() {
        for (String source : List.of(
                "struct S {char member[__builtin_offsetof(S,member)+1];}; unsigned long result=__builtin_offsetof(S,member);",
                "extern const int b; const int a=b; const int b=a; struct S {char member[a];}; unsigned long result=__builtin_offsetof(S,member);",
                "struct S; const int n=__builtin_offsetof(S,member); struct S {char member[n];}; unsigned long result=__builtin_offsetof(S,member);",
                "struct A; typedef A Alias; struct A {Alias member;}; unsigned long result=__builtin_offsetof(Alias,member);")) {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                var project = resolve(selected(), source);
                assertTrue(project.hasErrors(), source);
                assertTrue(new IntegralConstants(project, selected()).evaluate(offset(project, "result")).isEmpty());
            }, source);
        }
        var valid = resolve(selected(), "struct First {char tag; int value;}; enum {n=__builtin_offsetof(First,value)}; const int last=n-1; struct S {int member[n];}; unsigned long result=__builtin_offsetof(S,member[last]);");
        healthy(valid);
        assertEquals(BigInteger.valueOf(12), value(valid, offset(valid, "result"), selected()));
    }

    @Test void supportedObjectCeilingIncludesPaddingNestedTypesAndUnrelatedSizeofDependencies() {
        var profile = selected("16");
        for (String source : List.of(
                "struct S {char member; char rest[16];}; unsigned long result=__builtin_offsetof(S,member);",
                "struct S {double member; char rest[9];}; unsigned long result=__builtin_offsetof(S,member);",
                "union U {char bytes[17]; int value;}; struct S {U member;}; unsigned long result=__builtin_offsetof(S,member.value);",
                "struct Giant {char data[17];}; struct S {char member[2];}; unsigned long result=__builtin_offsetof(S,member[sizeof(Giant)-17]);",
                "struct Giant {char data[17];}; struct S {char member[sizeof(Giant)-16];}; unsigned long result=__builtin_offsetof(S,member);")) {
            var project = resolve(profile, source);
            assertTrue(project.hasErrors(), source);
            assertTrue(project.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),
                    () -> source + " / " + project.semanticDiagnostics());
            assertTrue(new IntegralConstants(project, profile).evaluate(offset(project, "result")).isEmpty());
        }
        var exact = resolve(profile, "struct S {char member[16];}; unsigned long result=__builtin_offsetof(S,member[15]);");
        healthy(exact);
        assertEquals(BigInteger.valueOf(15), value(exact, offset(exact, "result"), profile));
        var maximum = resolve(selected(), "struct S {char member[2147483647];}; unsigned long result=__builtin_offsetof(S,member[2147483646]);");
        healthy(maximum);
        assertEquals(new BigInteger("2147483646"), value(maximum, offset(maximum, "result"), selected()));
        var unrepresentable = resolve(selected(), "struct S {int member[18446744073709551615UL];}; unsigned long result=__builtin_offsetof(S,member[0]);");
        assertTrue(unrepresentable.hasErrors());
        assertTrue(unrepresentable.semanticDiagnostics().toString().contains("SIZE_NOT_REPRESENTABLE"),
                unrepresentable.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(unrepresentable, selected()).evaluate(offset(unrepresentable, "result")).isEmpty());
    }

    @Test void cachedConstAndEnumValuesCannotHideOversizedLayoutDependencies() {
        var profile = selected("16");
        for (boolean enumeration : List.of(false, true)) {
            for (boolean arrayBound : List.of(false, true)) {
                for (boolean earlierUse : List.of(false, true)) {
                    String constant = enumeration ? "enum {n=sizeof(Giant)-" : "const unsigned long n=sizeof(Giant)-";
                    constant += arrayBound ? "16" : "17";
                    constant += enumeration ? "};" : ";";
                    String owner = arrayBound ? "struct S {char member[n];};" : "struct S {char member[2];};";
                    String query = "unsigned long result=__builtin_offsetof(S,member" + (arrayBound ? "" : "[n]") + ");";
                    String warm = "unsigned long prior=n;";
                    var project = resolve(profile, "struct Giant {char data[17];};" + constant + owner
                            + (earlierUse ? warm + query : query + warm));
                    assertTrue(project.hasErrors(), "Named constants must preserve dependency evidence");
                    assertTrue(project.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),
                            project.semanticDiagnostics().toString());
                    var constants = new IntegralConstants(project, profile);
                    Declaration declaration = enumeration ? nodes(project, EnumeratorDeclaration.class).getFirst() : variable(project, "n");
                    assertEquals(BigInteger.valueOf(arrayBound ? 1 : 0), constants.apply(declaration).orElseThrow().number(),
                            "Generic sizeof and ordinary constants keep their existing unbounded meaning");
                    assertTrue(constants.evaluate(offset(project, "result")).isEmpty());
                    assertEquals(PodLayout.Reason.SUPPORTED_OBJECT_LIMIT,
                            constants.offsetofResult(offset(project, "result")).failure().orElseThrow().reason(),
                            "A successful ordinary constant cache must not bypass bounded offsetof dependency checks");
                }
            }
        }
    }

    @Test void earlierEnumeratorAndUnrelatedLaterEnumeratorDoNotCauseFalseRecursion() {
        var profile = selected();
        var project = resolve(profile, "struct S {int member[3];}; enum {first=1, later=__builtin_offsetof(S,member[first])}; unsigned long result=__builtin_offsetof(S,member[first]);");
        healthy(project);
        assertEquals(BigInteger.valueOf(4), value(project, offset(project, "result"), profile));
        var constants = new IntegralConstants(project, profile);
        assertEquals(BigInteger.valueOf(4), constants.apply(nodes(project, EnumeratorDeclaration.class).get(1)).orElseThrow().number());
    }

    @Test void activePragmasRejectOffsetofWithoutChangingGenericPreprocessing() throws Exception {
        var profile = selected();
        String owner = "struct S {char tag; int value;}; typedef S Alias;\n";
        String query = "unsigned long result=__builtin_offsetof(Alias,value);";
        for (String pragma : List.of("#pragma pack(push,1)", "#pragma pack(1)", "#pragma pack(pop)",
                "#pragma options align=packed", "#pragma GCC diagnostic ignored \"-Wunused\"", "#pragma once extra")) {
            String source = pragma + "\n" + owner + query;
            var parsed = Project.create().addFile(Parser.parse(unit(profile), source)).build();
            var intrinsic = nodes(parsed, OffsetofExpression.class).getFirst();
            assertThrows(IllegalArgumentException.class, () -> intrinsic.source(Language.CPP), pragma);
            assertThrows(IllegalArgumentException.class, () -> parsed.source(Language.CPP), pragma);
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(parsed, Language.CPP), pragma);
            assertThrows(IllegalArgumentException.class, () -> com.kniazkov.cpg.generation.Source.physicalSources(parsed), pragma);
            var project = NameResolver.resolve(parsed);
            assertTrue(project.hasErrors(), pragma);
            assertTrue(project.semanticDiagnostics().toString().contains("UNSUPPORTED_PRAGMA"), project.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(project, profile).evaluate(offset(project, "result")).isEmpty());
            // Generic preprocessing continues recording and ignoring its existing unknown pragmas.
            healthy(resolve(PlatformProfile.defaults(), pragma + "\n" + owner + "unsigned long size=sizeof(S);"));
            healthy(resolve(profile, pragma + "\n" + owner));
        }
        for (String prefix : List.of("#pragma once\n", "# pragma /* retained harmless directive */ once\n",
                "#if 0\n#pragma pack(push,1)\n#pragma options align=packed\n#endif\n")) {
            var project = resolve(profile, prefix + owner + query);
            healthy(project);
            assertEquals(BigInteger.valueOf(4), value(project, offset(project, "result"), profile));
            assertTrue(Cpg.sources(project, Language.CPP).get("main.cpp").contains("__builtin_offsetof"));
        }
        var after = resolve(profile, owner + "#pragma pack(push,1)\n" + query + "\n#pragma pack(pop)\n");
        assertTrue(after.hasErrors(), "The selected conservative contract rejects active pragmas regardless of source order");
        assertTrue(after.semanticDiagnostics().toString().contains("UNSUPPORTED_PRAGMA"));
        Files.writeString(temp.resolve("packed.h"), "#pragma pack(push,1)\n" + owner + "#pragma pack(pop)\n");
        var header = resolve(profile, "#include \"packed.h\"\n" + query);
        assertTrue(header.hasErrors());
        assertTrue(header.semanticDiagnostics().toString().contains("UNSUPPORTED_PRAGMA"));
        assertTrue(new IntegralConstants(header, profile).evaluate(offset(header, "result")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> offset(header, "result").source(Language.CPP));
    }

    @Test void activePragmaOperatorsAndNewOperatorSyntaxCannotBypassTheLayoutPolicy() {
        var profile = selected();
        String owner = "struct S {char tag; int value;}; typedef S Alias; ";
        String query = "unsigned long result=__builtin_offsetof(Alias,value);";
        for (String operator : List.of("_Pragma", "__pragma")) {
            for (String prefix : List.of("void configure(){" + operator + "(\"pack(1)\");}\n",
                    "#define PACK " + operator + "(\"pack(1)\")\nvoid configure(){PACK;}\n",
                    "void " + operator + "(const char*);\n")) {
                var parsed = Project.create().addFile(Parser.parse(unit(profile), prefix + owner + query)).build();
                assertThrows(IllegalArgumentException.class, () -> nodes(parsed, OffsetofExpression.class).getFirst().source(Language.CPP));
                assertThrows(IllegalArgumentException.class, () -> Cpg.sources(parsed, Language.CPP));
                var project = NameResolver.resolve(parsed);
                assertTrue(project.hasErrors(), prefix);
                assertTrue(project.semanticDiagnostics().toString().contains("UNSUPPORTED_PRAGMA"), project.semanticDiagnostics().toString());
                assertTrue(new IntegralConstants(project, profile).evaluate(offset(project, "result")).isEmpty());
                healthy(resolve(PlatformProfile.defaults(), prefix + owner));
            }
            for (String prefix : List.of("#define UNUSED_PACK " + operator + "(\"pack(1)\")\n",
                    "#if 0\n" + operator + "(\"pack(1)\")\n#endif\n")) {
                var project = resolve(profile, prefix + owner + query);
                healthy(project);
                assertEquals(BigInteger.valueOf(4), value(project, offset(project, "result"), profile));
                assertTrue(Cpg.sources(project, Language.CPP).get("main.cpp").contains("__builtin_offsetof"));
            }
            var original = resolve(profile, "void ordinary(const char*); void configure(){ordinary(\"pack(1)\");} " + owner + query);
            healthy(original);
            var call = nodes(original, CallExpression.class).getFirst();
            var edit = new ProjectEditor(original).withoutRevalidation();
            edit.replace(call.callee(), NameExpression.create().setName(operator).build());
            var inserted = edit.build();
            assertTrue(inserted.files().getFirst().provenance().orElseThrow().unit().tokens().stream()
                    .noneMatch(token -> token.spelling().equals(operator)), "The operator exists only in the new AST, not captured tokens");
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(inserted, Language.CPP));
            assertThrows(IllegalArgumentException.class, () -> inserted.source(Language.CPP));
            assertThrows(IllegalArgumentException.class, () -> com.kniazkov.cpg.generation.Source.physicalSources(inserted));
            var analyzed = NameResolver.resolve(inserted);
            assertTrue(analyzed.semanticDiagnostics().toString().contains("UNSUPPORTED_PRAGMA"), analyzed.semanticDiagnostics().toString());
        }
        var literal = resolve(profile, "const char* note=\"_Pragma __pragma\"; " + owner + query);
        healthy(literal);
        assertEquals(BigInteger.valueOf(4), value(literal, offset(literal, "result"), profile));
    }

    @Test void nonPodSizeofDependencyIsUnsupportedWhileNonPodOwnerIsInvalid() {
        var profile = selected();
        String nonPod = "struct NonPod {NonPod(); int value;}; ";
        var dependent = resolve(profile, nonPod + "struct Owner {char bytes[sizeof(NonPod)]; char tail;}; "
                + "unsigned long result=__builtin_offsetof(Owner,tail);");
        assertTrue(dependent.hasErrors());
        var dependencyDiagnostics = offset(dependent, "result").diagnostics();
        assertTrue(dependencyDiagnostics.stream().anyMatch(d -> d.code().equals("unsupported-offsetof")
                && d.message().contains("UNSUPPORTED_TYPE")), dependencyDiagnostics.toString());
        assertTrue(dependencyDiagnostics.stream().noneMatch(d -> d.code().equals("invalid-offsetof")));
        assertTrue(new IntegralConstants(dependent, profile).evaluate(offset(dependent, "result")).isEmpty());
        var invalid = resolve(profile, nonPod + "unsigned long result=__builtin_offsetof(NonPod,value);");
        assertTrue(invalid.hasErrors());
        assertTrue(offset(invalid, "result").diagnostics().stream().anyMatch(d -> d.code().equals("invalid-offsetof")
                && d.message().contains("NOT_POD")), invalid.semanticDiagnostics().toString());
    }

    @Test void pragmaPolicyUsesEnclosingTranslationUnitAndDoesNotContaminateOtherUnits() {
        var profile = selected();
        String shape = "struct S {char tag; int value;}; ";
        var clean = resolve(profile, shape + "unsigned long result=__builtin_offsetof(S,value);");
        var dirty = resolve(profile, "#pragma pack(push,1)\n" + shape + "unsigned long result=0;");
        healthy(dirty);
        var manual = OffsetofExpression.create().setType(NamedType.create().setName("S")).addMember("value");
        manual.setProvenance(offset(clean, "result").provenance().orElseThrow());
        var editor = new ProjectEditor(dirty);
        editor.replace(variable(dirty, "result").initializer().orElseThrow(), manual.build());
        var inserted = editor.build();
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(inserted, Language.CPP),
                "Clean leaf provenance cannot authorize a pragma-bearing destination TU");
        assertThrows(IllegalArgumentException.class, () -> inserted.source(Language.CPP));
        var first = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("packed.cpp")), temp,
                List.of(), List.of(), "c++03").withPlatform(profile);
        var second = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("clean.cpp")), temp,
                List.of(), List.of(), "c++03").withPlatform(profile);
        var separate = NameResolver.resolve(Project.create()
                .addFile(Parser.parse(first, "#pragma pack(push,1)\nstruct Packed {char tag; int value;};\n#pragma pack(pop)\n"))
                .addFile(Parser.parse(second, shape + "unsigned long result=__builtin_offsetof(S,value);")).build());
        healthy(separate);
        assertEquals(BigInteger.valueOf(4), value(separate, offset(separate, "result"), profile));
        assertEquals(2, Cpg.sources(separate, Language.CPP).size());
        var mixed = NameResolver.resolve(Project.create()
                .addFile(Parser.parse(first, "#pragma pack(push,1)\nstruct Packed {char tag; int value;};\n#pragma pack(pop)\nunsigned long bad=__builtin_offsetof(Packed,value);"))
                .addFile(Parser.parse(second, shape + "unsigned long good=__builtin_offsetof(S,value);")).build());
        assertTrue(mixed.hasErrors());
        assertTrue(offset(mixed, "good").diagnostics().isEmpty());
        assertEquals(BigInteger.valueOf(4), value(mixed, offset(mixed, "good"), profile));
        assertTrue(new IntegralConstants(mixed, profile).evaluate(offset(mixed, "bad")).isEmpty());
    }

    @Test void foldedOwnerAndAliasBoundsRetainConstAndEnumSizeDependencies() {
        var profile = selected("16");
        for (boolean enumeration : List.of(false, true)) for (boolean arrayAlias : List.of(false, true)) {
            String constant = enumeration ? "enum {n=sizeof(Giant)-15}; " : "const unsigned long n=sizeof(Giant)-15; ";
            String shape = arrayAlias ? "typedef char Row[n]; struct S {Row a;}; " : "struct S {char a[n];}; ";
            var original = resolve(profile, "struct Giant {char data[16];}; " + constant + shape
                    + "typedef S Owner; unsigned long result=__builtin_offsetof(Owner,a);");
            healthy(original);
            var folded = NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original)));
            healthy(folded);
            Expression initializer = enumeration ? nodes(folded, EnumeratorDeclaration.class).getFirst().value().orElseThrow()
                    : variable(folded, "n").initializer().orElseThrow();
            assertEquals(1, nodes(initializer, SizeofExpression.class).size(), "Folding must retain the complete bounded-layout dependency");
            assertEquals(BigInteger.ZERO, value(folded, offset(folded, "result"), profile));
            var reparsed = resolve(profile, Cpg.sources(folded, Language.CPP).get("main.cpp"));
            healthy(reparsed);
            for (var snapshot : List.of(original, folded, reparsed)) {
                var bound = assertInstanceOf(ArrayType.class, variable(snapshot, "data").type().orElseThrow()).bound().orElseThrow();
                var edit = new ProjectEditor(snapshot);
                edit.replace(bound, IntegerLiteral.create().setValue(17).build());
                var grown = NameResolver.resolve(edit.build());
                assertTrue(grown.hasErrors(), "The original, folded and reparsed dependency must all enforce the selected ceiling");
                assertTrue(grown.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"), grown.semanticDiagnostics().toString());
                assertTrue(new IntegralConstants(grown, profile).evaluate(offset(grown, "result")).isEmpty());
            }
        }
    }

    @Test void foldedSizeofMemberBoundsRecomputeButMethodsAndPointerPointeesStillFold() {
        var profile = selected();
        var original = resolve(profile, "struct Leaf {int x;}; struct Owner {char a[sizeof(Leaf)]; char tail;}; "
                + "unsigned long result=__builtin_offsetof(Owner,tail);");
        var folded = NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original)));
        healthy(folded);
        assertEquals(BigInteger.valueOf(4), value(folded, offset(folded, "result"), profile));
        var bound = assertInstanceOf(ArrayType.class, variable(folded, "a").type().orElseThrow()).bound().orElseThrow();
        assertEquals(1, nodes(bound, SizeofExpression.class).size());
        var edit = new ProjectEditor(folded);
        edit.replace(variable(folded, "x").type().orElseThrow(), NamedType.create().setName("long").build());
        var changed = NameResolver.resolve(edit.build());
        healthy(changed);
        assertEquals(BigInteger.valueOf(8), value(changed, offset(changed, "result"), profile));
        var reparsed = resolve(profile, Cpg.sources(changed, Language.CPP).get("main.cpp"));
        healthy(reparsed);
        assertEquals(BigInteger.valueOf(8), value(reparsed, offset(reparsed, "result"), profile));
        var unrelated = resolve(profile, "struct Pointee {char bytes[1+2]; int pmethod(){return 3+4;}}; "
                + "struct S {Pointee* pointer; char tail; int smethod(){return 5+6;}}; "
                + "unsigned long result=__builtin_offsetof(S,tail); int ordinary=7+8;");
        var optimized = NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(unrelated)));
        healthy(optimized);
        assertEquals(BigInteger.valueOf(8), value(optimized, offset(optimized, "result"), profile));
        var pointeeBound = assertInstanceOf(ArrayType.class, variable(optimized, "bytes").type().orElseThrow()).bound().orElseThrow();
        assertEquals(BigInteger.valueOf(3), assertInstanceOf(IntegerLiteral.class, written(pointeeBound)).bigValue(),
                "Pointer representation does not depend on its pointee's member bounds");
        assertEquals(BigInteger.valueOf(15), assertInstanceOf(IntegerLiteral.class,
                written(variable(optimized, "ordinary").initializer().orElseThrow())).bigValue());
        for (var test : Map.of("pmethod", 7, "smethod", 11).entrySet()) {
            var method = nodes(optimized, CallableDeclaration.class).stream().filter(c -> c.name().equals(test.getKey())).findFirst().orElseThrow();
            var returned = nodes(method.body().orElseThrow(), ReturnStatement.class).getFirst().value().orElseThrow();
            assertEquals(BigInteger.valueOf(test.getValue()), assertInstanceOf(IntegerLiteral.class, written(returned)).bigValue(),
                    "Unrelated method bodies must not be frozen by offsetof owner dependencies");
        }
    }

    @Test void provenanceHeadersUndefPhysicalPriorityAndTranslationUnitsStaySeparate() throws Exception {
        var profile = selected();
        for (String headers : List.of("#include <cstddef>\n", "#include <stddef.h>\n",
                "#include <cstddef>\n#include <stddef.h>\n", "#include <stddef.h>\n#include <cstddef>\n")) {
            var project = resolve(profile, headers + SHAPES + "unsigned long result=offsetof(Outer, list[2].value);");
            healthy(project);
            var preprocessing = project.files().getFirst().provenance().orElseThrow().unit();
            var definitions = preprocessing.definitions().stream().filter(d -> d.name().equals("offsetof")).toList();
            long expected = headers.lines().count();
            assertEquals(expected, definitions.size());
            assertEquals(expected, definitions.stream().map(d -> d.id()).distinct().count());
            for (var definition : definitions) {
                assertTrue(definition.functionLike());
                assertNull(definition.unavailableReason());
                assertEquals("library", definition.originKind());
                assertTrue(definition.sourceKey().contains(profile.identityHash()));
                assertTrue(project.externalInput(definition.sourceKey()).isPresent());
            }
            assertTrue(preprocessing.expansions().stream().anyMatch(e -> e.name().equals("offsetof")));
            assertTrue(offset(project, "result").provenance().isPresent());
        }
        Parser.parse(unit(profile), "#include <cstddef>\n#undef offsetof\n#if defined(offsetof)\n#error still defined\n#endif\nint x;");
        assertTrue(CppPreprocessor.preprocess(unit(profile), "int x;").definitions().stream().noneMatch(d -> d.name().equals("offsetof")));
        for (String header : List.of("cstddef", "stddef.h")) {
            String include = "#include <" + header + ">\n";
            var unused = resolve(PlatformProfile.defaults(), include + "#if !defined(offsetof)\n#error missing\n#endif\nint x;");
            healthy(unused);
            var definition = unused.files().getFirst().provenance().orElseThrow().unit().definitions().stream()
                    .filter(d -> d.name().equals("offsetof")).findFirst().orElseThrow();
            assertNotNull(definition.unavailableReason());
            assertThrows(ParseException.class, () -> resolve(PlatformProfile.defaults(), include + SHAPES + "int x=offsetof(Outer,lead);"));
            assertThrows(ParseException.class, () -> resolve(profile, include + "#if offsetof(Outer,lead)\n#endif\n"));
        }
        Files.writeString(temp.resolve("stddef.h"), "#define offsetof(type, member) 7UL\n");
        var physicalUnit = new Build.TranslationUnit(unit(profile).source(), temp,
                List.of(new Build.IncludePath("-I", temp)), List.of(), "c++03").withPlatform(profile);
        var physical = NameResolver.resolve(Project.create().addFile(Parser.parse(physicalUnit,
                "#include <stddef.h>\n" + SHAPES + "unsigned long result=offsetof(Outer,lead);")).build());
        healthy(physical);
        assertTrue(nodes(physical, OffsetofExpression.class).isEmpty());
        assertEquals(BigInteger.valueOf(7), new IntegralConstants(physical, profile)
                .evaluate(variable(physical, "result").initializer().orElseThrow()).orElseThrow().number());
        Files.writeString(temp.resolve("stddef.h"), "#define offsetof(type, member) __builtin_offsetof(type, member)\n");
        var realBuiltin = NameResolver.resolve(Project.create().addFile(Parser.parse(physicalUnit,
                "#include <stddef.h>\n" + SHAPES + "unsigned long result=offsetof(Outer,list[2].value);")).build());
        healthy(realBuiltin);
        assertEquals(BigInteger.valueOf(36), value(realBuiltin, offset(realBuiltin, "result"), profile));
        var disabled = physicalUnit.withPlatform(PlatformProfile.defaults());
        assertThrows(ParseException.class, () -> Parser.parse(disabled,
                "#include <stddef.h>\n" + SHAPES + "unsigned long result=offsetof(Outer,lead);"));
    }

    @Test void noRuntimeMemberReadsAndGenerationCannotBorrowAnotherTranslationUnitsCapability() {
        var profile = selected();
        var project = DataFlow.analyze(resolve(profile, "#include <cstddef>\n" + SHAPES
                + "int touch(); int f(){int x=7; unsigned long n=offsetof(Outer,list[2].value); return x+touch()+n;}"));
        healthy(project);
        assertEquals(1, nodes(project, CallExpression.class).size());
        assertTrue(project.flows().stream().allMatch(FunctionFlow::complete));
        var intrinsic = nodes(project, OffsetofExpression.class).getFirst();
        var operandNodes = nodes(intrinsic.type(), Node.class);
        intrinsic.steps().forEach(step -> operandNodes.addAll(nodes(step, Node.class)));
        assertTrue(project.flows().stream().flatMap(f -> f.data().stream())
                .noneMatch(edge -> operandNodes.contains(edge.from()) || operandNodes.contains(edge.to())),
                "Type/path/index dependencies must not become runtime memory reads");
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        var unauthorized = resolve(PlatformProfile.defaults(), SHAPES + "unsigned long result=1;");
        var editor = new ProjectEditor(unauthorized);
        editor.replace(variable(unauthorized, "result").initializer().orElseThrow(), intrinsic);
        assertThrows(IllegalStateException.class, editor::build,
                "Moving a resolved path cannot import foreign member declarations");
        var manual = OffsetofExpression.create().setType(NamedType.create().setName("Outer"))
                .addMember("list").addIndex(IntegerLiteral.create().setValue(2)).addMember("value").build();
        assertThrows(IllegalArgumentException.class, () -> manual.source(Language.CPP));
        var inserted = new ProjectEditor(unauthorized);
        inserted.replace(variable(unauthorized, "result").initializer().orElseThrow(), manual);
        var moved = inserted.build();
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(moved, Language.CPP));
        assertThrows(IllegalArgumentException.class, () -> moved.source(Language.CPP));
        assertThrows(IllegalArgumentException.class, () -> com.kniazkov.cpg.generation.Source.physicalSources(moved));
    }
}
