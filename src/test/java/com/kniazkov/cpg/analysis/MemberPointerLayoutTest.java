/* Explicit Itanium member-pointer storage in bounded offsetof queries. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MemberPointerLayoutTest {
    private static final String STORAGE = "itanium-x86_64-lp64-v1";
    private static final PlatformProfile PROFILE = profile("2147483647");
    @TempDir Path temp;
    private static PlatformProfile profile(String limit) { return PlatformProfile.builder()
            .offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", limit, STORAGE).build(); }
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
    private static VariableDeclaration variable(Project project, String name) {
        return nodes(project.files().getFirst(), VariableDeclaration.class).stream()
                .filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }
    private static Expression initializer(Project project, String name) { return variable(project, name).initializer().orElseThrow(); }
    private static TypedefDeclaration alias(Project project, String name) {
        return nodes(project.files().getFirst(), TypedefDeclaration.class).stream()
                .filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }
    private static void healthy(Project project) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
    }
    private static void value(Project project, String name, long expected) { value(project, PROFILE, name, expected); }
    private static void value(Project project, PlatformProfile profile, String name, long expected) {
        var result = new IntegralConstants(project, profile).evaluate(initializer(project, name)).orElseThrow();
        assertEquals("unsigned long", result.type().spelling());
        assertEquals(BigInteger.valueOf(expected), result.number());
    }
    private static void unsupported(Project project, PlatformProfile profile, String reason) {
        assertTrue(project.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("unsupported-offsetof")), project.semanticDiagnostics().toString());
        assertTrue(project.semanticDiagnostics().toString().contains(reason), project.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(project, profile).evaluate(initializer(project, "result")).isEmpty());
    }

    @Test void optionalClosedProfilePreservesOldSettingsAndApi() {
        var old = PlatformProfile.builder().offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "2147483647").build();
        var oldModel = new OffsetofModel("gcc-clang-builtin", "x86_64-sysv-lp64-v1", BigInteger.valueOf(2147483647));
        assertNull(oldModel.memberPointerStorage());
        assertEquals(oldModel, OffsetofModel.parse("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "2147483647"));
        assertEquals(Map.of("emission", "gcc-clang-builtin", "layout", "x86_64-sysv-lp64-v1", "supportedObjectBytes", "2147483647"), oldModel.settings());
        assertEquals(old, PlatformProfile.fromSettings(old.toSettings()));
        assertEquals("c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10", old.identityHash());
        assertEquals(old.identityHash(), old.toBuilder().build().identityHash());
        assertFalse(old.stableIdentity().contains("memberPointerStorage"));
        assertNotEquals(old.identityHash(), PROFILE.identityHash());
        assertEquals(STORAGE, PROFILE.offsetof().orElseThrow().memberPointerStorage());
        assertEquals(PROFILE, PROFILE.toBuilder().build());
        assertEquals(PROFILE, PlatformProfile.fromSettings(PROFILE.toSettings()));
        var inherited = PlatformProfile.fromSettings(Map.of("inherits", "default", "offsetof", PROFILE.offsetof().orElseThrow().settings()));
        assertEquals(PROFILE, inherited);
        var reset = new LinkedHashMap<>(PROFILE.toSettings()); reset.put("offsetof", Map.of());
        assertTrue(PlatformProfile.fromSettings(reset).offsetof().isEmpty());
        assertEquals(old, PROFILE.toBuilder().offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "2147483647").build());
    }

    @Test void schemaRejectsUnknownCapabilitiesTypesAndContradictoryTargetFacts() {
        for (Object bad : List.of("unavailable", "msvc", "", 8, true, List.of(STORAGE))) {
            var model = new LinkedHashMap<String,Object>(PROFILE.offsetof().orElseThrow().settings());
            model.put("memberPointerStorage", bad);
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(Map.of("inherits", "default", "offsetof", model)));
        }
        var nullOption = new LinkedHashMap<String,Object>(PROFILE.offsetof().orElseThrow().settings()); nullOption.put("memberPointerStorage", null);
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(Map.of("inherits", "default", "offsetof", nullOption)));
        for (String field : List.of("enumStorage", "memberPointerSize", "unknown")) {
            var model = new LinkedHashMap<String,Object>(PROFILE.offsetof().orElseThrow().settings()); model.put(field, STORAGE);
            assertThrows(IllegalArgumentException.class, () -> PlatformProfile.fromSettings(Map.of("inherits", "default", "offsetof", model)));
        }
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().sizeBits("pointer", 32).build());
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().alignmentBits("pointer", 32).build());
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().sizeType("unsigned int").build());
        assertThrows(IllegalArgumentException.class, () -> PROFILE.toBuilder().fieldAlignment("packed1").build());
        assertEquals("unknown", PROFILE.cRuntime(), "No C runtime selection is required");
    }

    @Test void categoriesAliasesAndCvSignaturesUseIndependentFixedRepresentations() {
        var p = resolve("""
                struct Incomplete; typedef Incomplete Owner;
                typedef int Owner::* Data; typedef Data const ConstData;
                typedef int Fn() const volatile; typedef Fn Owner::* MethodAlias;
                typedef void (Incomplete::* Method)(Incomplete&, ...) const volatile;
                typedef void (*Callback)(); typedef Callback Incomplete::* CallbackData;
                struct DataBox {char prefix; ConstData member; char tail;};
                struct FunctionBox {char prefix; MethodAlias member; char tail;};
                struct CvBox {char prefix; Method member; char tail;};
                struct CallbackBox {char prefix; CallbackData member; char tail;};
                unsigned long data=__builtin_offsetof(DataBox,tail);
                unsigned long function=__builtin_offsetof(FunctionBox,tail);
                unsigned long cv=__builtin_offsetof(CvBox,tail);
                unsigned long callback=__builtin_offsetof(CallbackBox,tail);
                """);
        for (var snapshot : List.of(p, p.edit().build(), NameResolver.resolve(p.edit().build()), TypeNormalizer.green(p), NameResolver.resolve(ConstantFolder.fold(p)))) {
            healthy(snapshot); value(snapshot, "data", 16); value(snapshot, "function", 24);
            value(snapshot, "cv", 24); value(snapshot, "callback", 16);
            var layout = PodLayout.layout(snapshot, alias(snapshot, "Data").type(), PROFILE).value().orElseThrow();
            assertEquals(BigInteger.valueOf(8), layout.sizeBytes()); assertEquals(64, layout.alignmentBits());
            layout = PodLayout.layout(snapshot, alias(snapshot, "MethodAlias").type(), PROFILE).value().orElseThrow();
            assertEquals(BigInteger.valueOf(16), layout.sizeBytes()); assertEquals(64, layout.alignmentBits());
        }
        var reparsed = resolve(Cpg.sources(p, Language.CPP).get("main.cpp")); healthy(reparsed);
        value(reparsed, "data", 16); value(reparsed, "function", 24); value(reparsed, "callback", 16);
    }

    @Test void arraysNestingAndUnionsUseMemberStorageAndTailPadding() {
        var p = resolve("""
                struct I; typedef int I::* Data; typedef void(I::*Method)();
                struct Inner {char c; Method methods[3]; char tail;};
                union U {Data data; Method method; char bytes[17];};
                struct Outer {char c; Inner inner; U unionValue; char tail;};
                struct Sized {char bytes[sizeof(U)]; char tail;};
                unsigned long innerTail=__builtin_offsetof(Inner,tail);
                unsigned long second=__builtin_offsetof(Outer,inner.methods[1]);
                unsigned long outerTail=__builtin_offsetof(Outer,tail);
                unsigned long unionMember=__builtin_offsetof(Outer,unionValue.method);
                unsigned long size=__builtin_offsetof(Sized,tail);
                """);
        healthy(p); value(p, "innerTail", 56); value(p, "second", 32);
        value(p, "outerTail", 96); value(p, "unionMember", 72); value(p, "size", 24);
    }

    @Test void sharedOwnerIdentityIsNotByValueRecursionButAliasCyclesAreRejected() {
        var owner = ClassDeclaration.create().setName("S").setKeyword("struct");
        var shared = NamedType.create().setName("S").setDeclaration(owner);
        owner.addMember(VariableDeclaration.create().setName("member").setType(
                MemberPointerType.create().setOwnerType(shared).setPointee(NamedType.create().setName("int"))));
        var type = shared.build();
        var layout = PodLayout.layout(type, PROFILE).value().orElseThrow();
        assertEquals(BigInteger.valueOf(8), layout.sizeBytes()); assertEquals(64, layout.alignmentBits());
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            var first = TypedefDeclaration.create().setName("First");
            var second = TypedefDeclaration.create().setName("Second");
            first.setType(NamedType.create().setName("Second").setAlias(second));
            second.setType(NamedType.create().setName("First").setAlias(first));
            var pointer = MemberPointerType.create().setOwnerType(NamedType.create().setName("First").setAlias(first))
                    .setPointee(NamedType.create().setName("int")).build();
            assertEquals(PodLayout.Reason.RECURSIVE_TYPE, PodLayout.layout(pointer, PROFILE).failure().orElseThrow().reason());
        });
    }

    @Test void ownerAndSignatureBodiesAreNotLayoutOrCeilingDependencies() {
        var bounded = profile("32");
        var p = resolve(bounded, """
                struct Base {virtual void f();};
                struct Owner: Base {Owner(); char irrelevant[1000+24]; int body(){return 1+2;} };
                struct Incomplete;
                typedef Incomplete (Owner::*Method)(Owner) const;
                struct S {char c; Method member; char tail;};
                unsigned long result=__builtin_offsetof(S,tail);
                """);
        healthy(p); value(p, bounded, "result", 24);
        var folded = NameResolver.resolve(ConstantFolder.fold(p)); healthy(folded); value(folded, bounded, "result", 24);
        assertTrue(((ArrayType) variable(folded, "irrelevant").type().orElseThrow()).bound().orElseThrow() instanceof IntegerLiteral,
                "Unrelated owner-body syntax may fold normally");
        var editor = new ProjectEditor(folded);
        editor.replace(((ArrayType) variable(folded, "irrelevant").type().orElseThrow()).bound().orElseThrow(), IntegerLiteral.create().setValue(2000).build());
        var edited = NameResolver.resolve(editor.build()); healthy(edited); value(edited, bounded, "result", 24);
    }

    @Test void omittedStorageUnresolvedAndDependentTypesStayUnsupported() {
        var old = PlatformProfile.builder().offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "128").build();
        String basic = "struct I; struct S {int I::* member;}; unsigned long result=__builtin_offsetof(S,member);";
        unsupported(resolve(old, basic), old, "UNSUPPORTED_TYPE");
        var known = resolve("struct I; typedef int I::*Data; typedef int(I::*Method)();");
        var data = (MemberPointerType) alias(known, "Data").type();
        var method = (MemberPointerType) alias(known, "Method").type();
        for (MemberPointerType uncertain : List.of(
                data.edit().setOwnerType(NamedType.create().setName("Unknown")).build(),
                data.edit().setPointee(NamedType.create().setName("Unknown")).build(),
                method.edit().setPointee(FunctionType.create().setReturnType(NamedType.create().setName("Unknown"))).build()))
            assertFalse(PodLayout.layout(uncertain, PROFILE).supported());
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            var p = resolve("template<class T> struct S {int T::* member;}; template<class T> unsigned long f(){return __builtin_offsetof(S<T>,member);}");
            assertTrue(p.hasErrors());
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("unsupported-offsetof")), p.semanticDiagnostics().toString());
        });
        var enumeration = resolve("enum E {first}; struct S {E member;}; unsigned long result=__builtin_offsetof(S,member);");
        unsupported(enumeration, PROFILE, "UNSUPPORTED_TYPE");
    }

    @Test void boundedSizeofUsesSameContextButDoesNotEnableGenericMemberSizeof() {
        String source = "struct I; typedef void(I::*Method)(); const unsigned long count=sizeof(Method);"
                + "struct S {char bytes[count];}; unsigned long result=__builtin_offsetof(S,bytes[count-1]);";
        var p = resolve(source); healthy(p);
        var context = new IntegralConstants(p, PROFILE);
        assertTrue(context.evaluate(initializer(p, "count")).isEmpty());
        assertTrue(context.apply(variable(p, "count")).isEmpty());
        assertEquals(BigInteger.valueOf(15), context.evaluate(initializer(p, "result")).orElseThrow().number());
        assertTrue(context.evaluate(initializer(p, "count")).isEmpty());
        var folded = NameResolver.resolve(ConstantFolder.fold(p)); healthy(folded); value(folded, "result", 15);
        assertInstanceOf(SizeofExpression.class, initializer(folded, "count"));
        var small = profile("15"); unsupported(resolve(small, source), small, "SUPPORTED_OBJECT_LIMIT");
        var foreign = resolve("int unrelated;");
        assertTrue(new IntegralConstants(foreign, PROFILE).evaluate(initializer(p, "result")).isEmpty());
        assertFalse(PodLayout.layout(foreign, alias(p, "Method").type(), PROFILE).supported());
    }

    @Test void categoryAliasEditsAfterFoldingRecomputeOffsetSizeAndGeneratedSource() {
        var original = resolve("struct I; typedef int Fn() const volatile; typedef int Member; typedef Member I::*Pointer;"
                + "struct S {char prefix; Pointer member; char tail;}; const unsigned long count=sizeof(Pointer);"
                + "struct Sized {char bytes[count]; char tail;}; unsigned long direct=__builtin_offsetof(S,tail);"
                + "unsigned long sized=__builtin_offsetof(Sized,tail);");
        healthy(original); value(original, "direct", 16); value(original, "sized", 8);
        var folded = NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))); healthy(folded);
        var editor = new ProjectEditor(folded); editor.replace(alias(folded, "Member").type(), NamedType.create().setName("Fn").build());
        var edited = NameResolver.resolve(editor.build()); healthy(edited);
        for (var snapshot : List.of(edited, edited.edit().build(), NameResolver.resolve(edited.edit().build()))) {
            value(snapshot, "direct", 24); value(snapshot, "sized", 16);
            var owner = nodes(snapshot, ClassDeclaration.class).stream().filter(c -> c.name().equals("S")).findFirst().orElseThrow();
            assertEquals(BigInteger.valueOf(32), PodLayout.layout(snapshot, owner, PROFILE).value().orElseThrow().sizeBytes());
        }
        value(original, "direct", 16); value(folded, "direct", 16);
        String source = Cpg.sources(edited, Language.CPP).get("main.cpp");
        assertTrue(source.contains("Fn Member"), source);
        var reparsed = resolve(source); healthy(reparsed); value(reparsed, "direct", 24); value(reparsed, "sized", 16);
    }

    @Test void writtenSignatureDependenciesRemainBoundedAndEditableWithoutOwnerRecursion() {
        String source = "struct I; struct Giant {char bytes[16];}; typedef char Array[sizeof(Giant)-15];"
                + "typedef void(I::*Method)(Array*); struct S {Method member;}; unsigned long result=__builtin_offsetof(S,member);";
        var bounded = profile("16"); var p = resolve(bounded, source); healthy(p); value(p, bounded, "result", 0);
        var folded = NameResolver.resolve(ConstantFolder.fold(p)); healthy(folded);
        assertFalse(nodes(alias(folded, "Array"), SizeofExpression.class).isEmpty());
        var editor = new ProjectEditor(folded);
        editor.replace(((ArrayType) variable(folded, "bytes").type().orElseThrow()).bound().orElseThrow(), IntegerLiteral.create().setValue(17).build());
        unsupported(NameResolver.resolve(editor.build()), bounded, "SUPPORTED_OBJECT_LIMIT");
    }

    @Test void ceilingsRepresentabilityPragmasAndCyclesRemainGuarded() {
        String source = "struct I; typedef int I::*Data; struct S {Data member;}; unsigned long result=__builtin_offsetof(S,member);";
        var tiny = profile("7"); unsupported(resolve(tiny, source), tiny, "SUPPORTED_OBJECT_LIMIT");
        var huge = resolve("struct I; typedef int I::*Data; struct S {Data array[18446744073709551615UL];}; unsigned long result=__builtin_offsetof(S,array);");
        unsupported(huge, PROFILE, "SIZE_NOT_REPRESENTABLE");
        unsupported(resolve("#pragma pack(push,1)\n" + source + "\n#pragma pack(pop)\n"), PROFILE, "UNSUPPORTED_PRAGMA");
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            var cyclic = resolve("struct I; typedef int I::*Data; struct S {char bytes[sizeof(S)]; Data member;}; unsigned long result=__builtin_offsetof(S,member);");
            assertTrue(cyclic.hasErrors());
            assertTrue(new IntegralConstants(cyclic, PROFILE).evaluate(initializer(cyclic, "result")).isEmpty());
        });
    }
}
