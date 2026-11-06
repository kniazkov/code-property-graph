/* Conservative, target-owned C++03 object representation. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AggregateLayoutSemanticsTest {
    @TempDir Path temp;
    private static final PlatformProfile DEFAULT = PlatformProfile.defaults();
    private Project read(String source, PlatformProfile profile) throws Exception {
        Files.writeString(temp.resolve("layout.cpp"), source);
        return Cpg.read(temp, new Cpg.Options(Language.CPP, Cpg.Stage.GREEN).withPlatform(profile));
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (kind.isInstance(node)) result.add(kind.cast(node)); }
        });
        return result;
    }
    private static TypeNode variableType(Project project, String name) {
        return nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals(name))
                .findFirst().orElseThrow().type().orElseThrow();
    }
    @Test void structSizeOffsetsAndTailPaddingFollowExplicitPolicy() throws Exception {
        var project = read("struct S { char c; int i; }; S value;", DEFAULT);
        var owner = nodes(project, ClassDeclaration.class).getFirst();
        var natural = TypeSemantics.layout(owner, DEFAULT).orElseThrow();
        assertEquals(List.of(BigInteger.ZERO, BigInteger.valueOf(4)), natural.offsets());
        assertEquals(BigInteger.valueOf(8), natural.sizeBytes());
        assertEquals(32, natural.alignmentBits());
        assertEquals(Optional.of(BigInteger.valueOf(8)), TypeSemantics.sizeof(variableType(project,"value"), DEFAULT));
        var packed = DEFAULT.toBuilder().fieldAlignment("packed1").build();
        var packedFacts = TypeSemantics.layout(owner, packed).orElseThrow();
        assertEquals(List.of(BigInteger.ZERO, BigInteger.ONE), packedFacts.offsets());
        assertEquals(BigInteger.valueOf(5), packedFacts.sizeBytes());
        assertEquals(8, packedFacts.alignmentBits());
        var trailing = read("struct S { int i; char c; }; S value;", DEFAULT);
        assertEquals(Optional.of(BigInteger.valueOf(8)), TypeSemantics.sizeof(variableType(trailing,"value"), DEFAULT));
        assertEquals(Optional.of(BigInteger.valueOf(5)), TypeSemantics.sizeof(variableType(trailing,"value"), packed));
    }
    @Test void everyRequiredLayoutPolicyMustBeKnownAndRoundTrips() throws Exception {
        var project = read("struct S { char c; int i; }; S value;", DEFAULT);
        for (var profile : List.of(DEFAULT.toBuilder().aggregateLayout("unknown").build(),
                DEFAULT.toBuilder().fieldAlignment("unknown").build(), DEFAULT.toBuilder().aggregateAlignment("unknown").build(),
                DEFAULT.toBuilder().tailPadding("unknown").build(), DEFAULT.toBuilder().alignmentBits("int", null).build())) {
            assertTrue(TypeSemantics.sizeof(variableType(project,"value"), profile).isEmpty());
            assertEquals(profile, PlatformProfile.fromSettings(profile.toSettings()));
            assertNotEquals(DEFAULT.stableIdentity(), profile.stableIdentity());
        }
        var oldSettings = new LinkedHashMap<>(DEFAULT.toSettings());
        oldSettings.remove("fieldAlignment"); oldSettings.remove("aggregateAlignment"); oldSettings.remove("tailPadding");
        var oldProfile = PlatformProfile.fromSettings(oldSettings);
        assertEquals("unknown", oldProfile.fieldAlignment());
        assertTrue(TypeSemantics.sizeof(variableType(project,"value"), oldProfile).isEmpty());
        var packed = PlatformProfile.fromSettings(Map.of("inherits","default","fieldAlignment","packed1"));
        assertEquals(packed, PlatformProfile.fromSettings(packed.toSettings()));
        assertNotEquals(DEFAULT.identityHash(), packed.identityHash());
        assertThrows(IllegalArgumentException.class, () -> DEFAULT.toBuilder().tailPadding("guess").build());
        assertThrows(IllegalArgumentException.class, () -> DEFAULT.toBuilder().fieldAlignment("").build());
        assertThrows(IllegalArgumentException.class, () -> DEFAULT.toBuilder().aggregateAlignment("guess").build());
    }
    @Test void profileReanalysisKeepsSymbolicBoundAndOriginalSnapshot() throws Exception {
        var unknown = DEFAULT.toBuilder().tailPadding("unknown").build();
        var original = read("struct S { char c; int i; }; char buf[sizeof(S)+1];", unknown);
        var array = assertInstanceOf(ArrayType.class, variableType(original,"buf"));
        assertTrue(array.semanticType().orElseThrow().sizeBytes().isEmpty());
        assertInstanceOf(AddExpression.class, array.bound().orElseThrow());
        var editor = new ProjectEditor(original);
        for (var type : nodes(original, TypeNode.class)) editor.describe(type, TypeSemantics.describe(type, DEFAULT));
        var analyzed = editor.build();
        var known = assertInstanceOf(ArrayType.class, variableType(analyzed,"buf"));
        assertEquals(Optional.of(BigInteger.valueOf(9)), known.semanticType().orElseThrow().sizeBytes());
        assertInstanceOf(AddExpression.class, known.bound().orElseThrow());
        assertTrue(array.semanticType().orElseThrow().sizeBytes().isEmpty());
        assertEquals(unknown.stableIdentity(), array.semanticType().orElseThrow().profileIdentity());
        assertEquals(DEFAULT.stableIdentity(), known.semanticType().orElseThrow().profileIdentity());
        assertEquals(Optional.of(BigInteger.valueOf(9)), TypeSemantics.sizeof(array, DEFAULT));
    }
    @Test void nestedArraysEmptyAggregatesAndTargetBytesAreModeled() throws Exception {
        var project = read("struct Empty {}; struct S { char c; int i; }; struct T { S values[2]; char end; }; T value; Empty empty;", DEFAULT);
        assertEquals(Optional.of(BigInteger.valueOf(20)), TypeSemantics.sizeof(variableType(project,"value"), DEFAULT));
        assertEquals(Optional.of(BigInteger.ONE), TypeSemantics.sizeof(variableType(project,"empty"), DEFAULT));
        var wordByte = DEFAULT.toBuilder().bitsPerByte(16).sizeBits("char",16).alignmentBits("char",16)
                .sizeBits("bool",16).alignmentBits("bool",16).build();
        var words = read("struct S { char c; int i; }; S value;", wordByte);
        assertEquals(Optional.of(BigInteger.valueOf(4)), TypeSemantics.sizeof(variableType(words,"value"), wordByte));
        assertEquals(Optional.of(BigInteger.valueOf(3)), TypeSemantics.sizeof(variableType(words,"value"), wordByte.toBuilder().fieldAlignment("packed1").build()));
    }
    @Test void unsupportedAbiAndRecursiveObjectsNeverAcquireInventedSize() throws Exception {
        for (var source : List.of("union S { int i; char c; };", "struct S;", "struct S { S value; };",
                "struct S { int &ref; };", "struct S { unsigned bits:3; };", "struct S { unsigned :3; int i; };",
                "struct B { int i; }; struct S : B { char c; };",
                "struct S { S(); int i; };")) {
            var project = assertDoesNotThrow(() -> read(source + " S object;", DEFAULT), source);
            assertTrue(TypeSemantics.sizeof(variableType(project,"object"), DEFAULT).isEmpty(), source);
        }
        var virtualOwner = ClassDeclaration.create().setName("S").setKeyword("struct")
                .addMember(MethodDeclaration.create().setName("f").setReturnType(NamedType.create().setName("void"))
                        .addModifier("virtual")).build();
        assertTrue(TypeSemantics.layout(virtualOwner, DEFAULT).isEmpty());
        var pointer = read("struct S { S *next; static int count; }; S object;", DEFAULT);
        assertEquals(Optional.of(BigInteger.valueOf(8)), TypeSemantics.sizeof(variableType(pointer,"object"), DEFAULT));
    }
    @Test void equalWidthsNeverEraseIntAndLongIdentity() throws Exception {
        var project = read("int i; long l;", DEFAULT);
        var narrow = DEFAULT.toBuilder().sizeBits("long",32).alignmentBits("long",32).build();
        var i = variableType(project,"i"); var l = variableType(project,"l");
        assertEquals(Optional.of(BigInteger.valueOf(8)), TypeSemantics.sizeof(l, DEFAULT));
        assertEquals(Optional.of(BigInteger.valueOf(4)), TypeSemantics.sizeof(l, narrow));
        assertEquals(TypeSemantics.sizeof(i,narrow), TypeSemantics.sizeof(l,narrow));
        assertNotEquals(TypeSemantics.describe(i,narrow).canonicalIdentity(), TypeSemantics.describe(l,narrow).canonicalIdentity());
    }
    @Test void allocationAndTypeidHaveLanguageTypesIndependentOfLayout() {
        var scalar = NewExpression.create().setType(NamedType.create().setName("int")).build();
        assertEquals("int", assertInstanceOf(PointerType.class, TypeSemantics.expressionType(scalar,DEFAULT).orElseThrow())
                .pointee().canonicalIdentity());
        var matrix = NewExpression.create().setType(ArrayType.create().setElementType(ArrayType.create()
                .setElementType(NamedType.create().setName("int")).setBound(IntegerLiteral.create().setValue(3)))
                .setBound(IntegerLiteral.create().setValue(2))).build();
        var row = assertInstanceOf(ArrayType.class, assertInstanceOf(PointerType.class,
                TypeSemantics.expressionType(matrix,DEFAULT).orElseThrow()).pointee());
        assertEquals(Optional.of(BigInteger.valueOf(12)), TypeSemantics.sizeof(row,DEFAULT));
        var deletion = DeleteExpression.create().setOperand(scalar.edit()).build();
        assertEquals("void", TypeSemantics.expressionType(deletion,DEFAULT).orElseThrow().canonicalIdentity());
        var rtti = TypeidExpression.create().setOperand(IntegerLiteral.create().setValue(3)).build();
        var result = assertInstanceOf(QualifiedType.class, TypeSemantics.expressionType(rtti,DEFAULT).orElseThrow());
        assertTrue(result.isConst());
        assertEquals("std::type_info", assertInstanceOf(NamedType.class,result.baseType()).name());
        assertEquals(ValueCategory.LVALUE, TypeSemantics.valueCategory(rtti,DEFAULT));
        assertTrue(TypeSemantics.typeidOperandUnevaluated(rtti,DEFAULT));
        assertTrue(TypeSemantics.typeidOperandUnevaluated(TypeidExpression.create().setType(NamedType.create().setName("S")).build(),DEFAULT));
        assertFalse(TypeSemantics.typeidOperandUnevaluated(TypeidExpression.create().setOperand(NameExpression.create().setName("unknown")).build(),DEFAULT));
    }

    @Test void sizeofDereferencedAllocationUsesWrittenClassWithoutEvaluatingNew() throws Exception {
        var project = read("struct S { char c; int i; }; unsigned long f(){ return sizeof(*new S); }",DEFAULT);
        var expression = nodes(project,SizeofExpression.class).getFirst();
        assertEquals(Optional.of(BigInteger.valueOf(8)), TypeSemantics.sizeof(expression,DEFAULT));
        assertEquals(Optional.of(BigInteger.valueOf(5)), TypeSemantics.sizeof(expression,DEFAULT.toBuilder().fieldAlignment("packed1").build()));
        var allocation = nodes(project,NewExpression.class).getFirst();
        assertTrue(project.flows().stream().flatMap(flow -> flow.data().stream())
                .noneMatch(edge -> edge.from() == allocation || edge.to() == allocation));
        assertEquals(1,nodes(project,NewExpression.class).size());
    }

    @Test void publicProfileReanalysisRetainsWrittenBoundThroughCopyJsonAndSource() throws Exception {
        String source = "struct S { char c; int i; }; char buf[sizeof(S)+1];";
        var unknown = DEFAULT.toBuilder().tailPadding("unknown").build();
        var original = read(source,unknown);
        var originalArray = assertInstanceOf(ArrayType.class,variableType(original,"buf"));
        var originalJson = ProjectJson.encode(original);
        assertTrue(originalArray.semanticType().orElseThrow().sizeBytes().isEmpty());
        // Public reanalysis repeats preprocessing and binding under the new effective profile.
        var analyzed = Cpg.read(temp,new Cpg.Options(Language.CPP,Cpg.Stage.GREEN).withPlatform(DEFAULT));
        assertEquals(DEFAULT,TypeSemantics.profile(analyzed.files().getFirst()));
        assertEquals(unknown,TypeSemantics.profile(original.files().getFirst()));
        assertNotEquals(original.files().getFirst().variantId(),analyzed.files().getFirst().variantId());
        for (var snapshot : List.of(analyzed,analyzed.edit().build())) {
            var array = assertInstanceOf(ArrayType.class,variableType(snapshot,"buf"));
            assertEquals(Optional.of(BigInteger.valueOf(9)),array.semanticType().orElseThrow().sizeBytes());
            var add = assertInstanceOf(AddExpression.class,array.bound().orElseThrow());
            var size = assertInstanceOf(SizeofExpression.class,add.left());
            var named = assertInstanceOf(NamedType.class,size.type().orElseThrow());
            assertSame(nodes(snapshot,ClassDeclaration.class).getFirst(),named.declaration().orElseThrow());
            assertEquals("S",named.name());
            assertEquals(1,nodes(add.right(),IntegerLiteral.class).getFirst().value());
        }
        var copied = analyzed.edit().build();
        SnapshotAssertions.assertGraphEquals(ProjectJson.encode(analyzed), ProjectJson.encode(copied));
        assertEquals(analyzed.passRuns().size(), copied.passRuns().size());
        for (int index = 0; index < analyzed.passRuns().size(); index++) {
            var before = analyzed.passRuns().get(index);
            var after = copied.passRuns().get(index);
            assertEquals(before.sequence(), after.sequence());
            assertEquals(before.passId(), after.passId());
            assertEquals(before.scope(), after.scope());
            assertTrue(before.results().isEmpty());
            assertTrue(after.results().isEmpty());
            // A raw builder copy does not retain derived flow/diagnostic products,
            // even in this declaration-only fixture where the graph itself is equal.
            assertEquals(Set.of("ControlFlow", "DataFlow", "UnreadWrites").contains(before.passId())
                    ? PassRun.Status.STALE : before.status(), after.status());
        }
        var root = (Map<?,?>) ProjectJson.encode(analyzed).toJavaObject();
        var file = (Map<?,?>) ((List<?>) root.get("files")).getFirst();
        var items = (List<?>) file.get("items");
        var owner = (Map<?,?>) items.get(0);
        var variable = (Map<?,?>) items.get(1);
        var array = (Map<?,?>) variable.get("type");
        assertEquals("9",((Map<?,?>)array.get("semanticType")).get("sizeBytes").toString());
        var bound = (Map<?,?>) array.get("bound");
        assertEquals("AddExpression",bound.get("kind"));
        var size = (Map<?,?>) bound.get("left");
        assertEquals("SizeofExpression",size.get("kind"));
        assertEquals(owner.get("id"),((Map<?,?>)size.get("type")).get("declarationId"));
        String generated = Cpg.sources(analyzed,Language.CPP).get("layout.cpp");
        assertTrue(generated.contains("sizeof(S)"),generated);
        var reparsed = read(generated,DEFAULT);
        var reparsedArray = assertInstanceOf(ArrayType.class,variableType(reparsed,"buf"));
        assertInstanceOf(AddExpression.class,reparsedArray.bound().orElseThrow());
        assertEquals(Optional.of(BigInteger.valueOf(9)),reparsedArray.semanticType().orElseThrow().sizeBytes());
        assertTrue(originalArray.semanticType().orElseThrow().sizeBytes().isEmpty());
        assertEquals(originalJson,ProjectJson.encode(original));
    }
    @Test void overloadCandidatesRetainNominalIntAndLongUnderEqualWidths() throws Exception {
        String source = "int choose(int); long choose(long); int i; long l; unsigned long f(){ choose(i); choose(l); return sizeof(long); }";
        var narrow = DEFAULT.toBuilder().sizeBits("long",32).alignmentBits("long",32).build();
        for (var profile : List.of(DEFAULT,narrow)) {
            var project = read(source,profile);
            var calls = nodes(project,CallExpression.class);
            assertEquals(2,calls.size());
            // Equal representation widths never erase nominal overload identities.
            assertEquals(List.of(CallExpression.Resolution.RESOLVED,CallExpression.Resolution.RESOLVED),
                    calls.stream().map(CallExpression::resolution).toList());
            assertEquals(List.of("int","long"),calls.stream().map(call -> call.target().orElseThrow()
                    .parameters().getFirst().type().orElseThrow().canonicalIdentity()).toList());
            assertEquals(List.of("int","long"),calls.stream().map(call -> TypeSemantics.expressionType(
                    call.arguments().getFirst(),profile).orElseThrow().canonicalIdentity()).toList());
            assertEquals(Optional.of(BigInteger.valueOf(profile == DEFAULT ? 8 : 4)),
                    TypeSemantics.sizeof(nodes(project,SizeofExpression.class).getFirst(),profile));
        }
    }
    @Test void typeidReferenceToIntIsUnevaluated() throws Exception {
        var project = read("#include <typeinfo>\nint f(){ int i=1; typeid(static_cast<int&>(i=2)); return i; }",DEFAULT);
        assertTrue(TypeSemantics.typeidOperandUnevaluated(nodes(project,TypeidExpression.class).getFirst(),DEFAULT));
        var assignment = nodes(project,AssignmentExpression.class).getFirst();
        assertTrue(project.flows().stream().flatMap(flow -> flow.data().stream())
                .noneMatch(edge -> edge.from() == assignment || edge.to() == assignment));
    }

}
