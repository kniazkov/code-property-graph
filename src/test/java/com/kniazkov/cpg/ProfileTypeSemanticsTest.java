/* Target-dependent type facts, integer folding, and unevaluated sizeof. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileTypeSemanticsTest {
    @TempDir Path temp;
    private static final PlatformProfile DEFAULT = PlatformProfile.defaults();
    private static NamedType.Builder type(String name) { return NamedType.create().setName(name); }
    private static IntegerLiteral.Builder integer(int value) { return IntegerLiteral.create().setValue(value); }
    private static PlatformProfile long32() {
        return DEFAULT.toBuilder().sizeBits("long", 32).alignmentBits("long", 32).build();
    }
    private Project read(String text, Cpg.Stage stage, PlatformProfile profile) throws Exception {
        Files.writeString(temp.resolve("types.cpp"), text);
        return Cpg.read(temp, new Cpg.Options(Language.CPP, stage).withPlatform(profile));
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (kind.isInstance(node)) result.add(kind.cast(node)); }
        });
        return result;
    }
    private static Expression returnValue(Project project) {
        return nodes(project, ReturnStatement.class).getFirst().value().orElseThrow();
    }
    @Test void widthsDoNotConflateCanonicalTypes() {
        var intType = type("int").build(); var longType = type("long").build();
        assertNotEquals(intType.canonicalIdentity(), longType.canonicalIdentity());
        var facts = TypeSemantics.describe(longType, long32());
        assertEquals(Optional.of(BigInteger.valueOf(4)), facts.sizeBytes());
        assertEquals(Optional.of(BigInteger.valueOf(32)), facts.sizeBits());
        assertEquals(BigInteger.valueOf(Integer.MAX_VALUE), facts.integerRange().orElseThrow().max());
        assertEquals(BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE),
                TypeSemantics.describe(longType, DEFAULT).integerRange().orElseThrow().max());
    }
    @Test void targetBytesAndRecursiveKindsDetermineSize() {
        var wordBytes = DEFAULT.toBuilder().bitsPerByte(16).sizeBits("char", 16).alignmentBits("char", 16)
                .sizeBits("bool", 16).alignmentBits("bool", 16).build();
        var array = ArrayType.create().setElementType(type("long")).setBound(integer(3)).build();
        assertEquals(BigInteger.valueOf(12), TypeSemantics.sizeof(array, wordBytes).orElseThrow());
        var pointerToArray = PointerType.create().setPointee(array.edit()).build();
        var arrayOfPointers = ArrayType.create().setElementType(PointerType.create().setPointee(type("long")))
                .setBound(integer(3)).build();
        assertEquals(BigInteger.valueOf(4), TypeSemantics.sizeof(pointerToArray, wordBytes).orElseThrow());
        assertEquals(BigInteger.valueOf(12), TypeSemantics.sizeof(arrayOfPointers, wordBytes).orElseThrow());
        assertTrue(TypeSemantics.sizeof(ArrayType.create().setElementType(type("int")).build(), DEFAULT).isEmpty());
        assertTrue(TypeSemantics.sizeof(FunctionType.create().setReturnType(type("int")).build(), DEFAULT).isEmpty());
        assertTrue(TypeSemantics.sizeof(type("void").build(), DEFAULT).isEmpty());
        assertTrue(TypeSemantics.sizeof(type("Incomplete").build(), DEFAULT).isEmpty());
        assertTrue(TypeSemantics.sizeof(MemberPointerType.create().setOwnerType(type("Owner")).setPointee(type("int")).build(), DEFAULT).isEmpty());
    }
    @Test void integerIntermediatesHonorTargetLongWithoutTruncation() throws Exception {
        String text = "int main() { return (2147483647L + 1L) / 2L; }";
        var value = assertInstanceOf(IntegerLiteral.class, returnValue(read(text, Cpg.Stage.FOLDED, DEFAULT)));
        assertEquals(1073741824, value.value());
        assertEquals(Optional.of("long"), value.cppType());
        assertInstanceOf(DivideExpression.class, returnValue(read(text, Cpg.Stage.FOLDED, long32())));
    }
    @Test void targetIntWidthControlsOverflowAndDecimalLiteralCandidates() throws Exception {
        var narrow = DEFAULT.toBuilder().sizeBits("int", 16).alignmentBits("int", 16)
                .wcharType("long").sizeBits("wchar_t", 64).alignmentBits("wchar_t", 64).build();
        assertInstanceOf(AddExpression.class, returnValue(read("int main() { return 30000 + 30000; }", Cpg.Stage.FOLDED, narrow)));
        var widerLiteral = assertInstanceOf(IntegerLiteral.class, returnValue(read("long main() { return 40000 + 2; }", Cpg.Stage.FOLDED, narrow)));
        assertEquals(40002, widerLiteral.value());
        assertEquals(Optional.of("long"), widerLiteral.cppType());
    }
    @Test void sizeofUnsignedTypeSurvivesFoldingAndReparsing() throws Exception {
        for (PlatformProfile profile : List.of(DEFAULT, long32())) {
            var project = read("int main() { return (sizeof(int) - 5) + 6; }", Cpg.Stage.FOLDED, profile);
            var literal = assertInstanceOf(IntegerLiteral.class, returnValue(project));
            assertEquals(5, literal.value());
            assertEquals(Optional.of("unsigned long"), literal.cppType());
            String output = Cpg.sources(project, Language.CPP).get("types.cpp");
            assertTrue(output.contains("5UL"));
            assertEquals(5, assertInstanceOf(IntegerLiteral.class,
                    returnValue(read(output, Cpg.Stage.FOLDED, profile))).value());
        }
    }
    @Test void greenFactsSurviveCopiesAndInvalidateChangedTypeSyntax() throws Exception {
        var project = read("int values[2 + 3]; int main() { return sizeof(values); }", Cpg.Stage.GREEN, DEFAULT);
        var array = nodes(project, ArrayType.class).getFirst();
        assertEquals(BigInteger.valueOf(20), array.semanticType().orElseThrow().sizeBytes().orElseThrow());
        assertEquals(array.semanticType(), array.edit().build().semanticType());
        assertTrue(array.edit().setBound(integer(7)).build().semanticType().isEmpty());
        assertTrue(array.edit().setElementType(type("long")).build().semanticType().isEmpty());
        var folded = ConstantFolder.fold(project);
        assertEquals(BigInteger.valueOf(20), nodes(folded, ArrayType.class).getFirst()
                .semanticType().orElseThrow().sizeBytes().orElseThrow());
    }
    @Test void sizeofDoesNotReadOrWriteItsExpressionOperand() throws Exception {
        var project = read("int main() { int value = 1; int size = sizeof(value = 7); return value; }", Cpg.Stage.RESOLVED, DEFAULT);
        var assignment = nodes(project, AssignmentExpression.class).getFirst();
        var value = nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals("value")).findFirst().orElseThrow();
        var data = project.flows().getFirst().data();
        assertTrue(data.stream().noneMatch(edge -> edge.from() == assignment || edge.to() == assignment));
        assertTrue(data.stream().anyMatch(edge -> edge.from() == value && edge.kind() == FunctionFlow.Dependency.DEFINITION));
        assertTrue(value.diagnostics().isEmpty());
    }
    @Test void unknownAlignmentDoesNotInventAggregateLayout() {
        var fields = List.of(type("char").build(), type("long").build(), type("char").build());
        var layout = TypeSemantics.layout(fields, DEFAULT).orElseThrow();
        assertEquals(List.of(BigInteger.ZERO, BigInteger.valueOf(8), BigInteger.valueOf(16)), layout.offsets());
        assertEquals(BigInteger.valueOf(24), layout.sizeBytes());
        assertTrue(TypeSemantics.layout(fields, DEFAULT.toBuilder().alignmentBits("long", null).build()).isEmpty());
        assertTrue(TypeSemantics.layout(fields, DEFAULT.toBuilder().aggregateLayout("unknown").build()).isEmpty());
    }
}
