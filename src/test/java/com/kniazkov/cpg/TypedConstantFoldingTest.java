/* Target arithmetic and potentially unevaluated expression regressions. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TypedConstantFoldingTest {
    @TempDir Path directory;
    private Project read(String source, Cpg.Stage stage, PlatformProfile profile) throws Exception {
        Files.writeString(directory.resolve("constants.cpp"), source);
        return Cpg.read(directory, new Cpg.Options(Language.CPP, stage).withPlatform(profile));
    }
    private Project read(String source, Cpg.Stage stage) throws Exception {
        return read(source, stage, PlatformProfile.defaults());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (kind.isInstance(node)) result.add(kind.cast(node));
        }});
        return result;
    }
    private Expression fold(String expression) throws Exception {
        return nodes(read("int main() { " + expression + "; }", Cpg.Stage.FOLDED), ExpressionStatement.class)
                .getFirst().expression();
    }
    private BigInteger number(String expression) throws Exception {
        return assertInstanceOf(IntegerLiteral.class, fold(expression)).bigValue();
    }
    @Test void explicitIntegralConversionsAndUnsignedWrapping() throws Exception {
        assertEquals(BigInteger.valueOf(4294967295L), number("static_cast<unsigned int>(-1)"));
        assertEquals(BigInteger.valueOf(4294967295L), number("(unsigned int)-1"));
        assertEquals(BigInteger.ONE, number("4294967295U + 2U"));
        assertEquals(BigInteger.valueOf(4), number("int(4)"));
        assertEquals(BigInteger.ZERO, number("int()"));
        assertInstanceOf(ConversionExpression.class, fold("static_cast<int>(4294967295U)"));
        assertEquals(BigInteger.valueOf(256), number("static_cast<unsigned char>(-1) + 1"));
    }
    @Test void comparisonsLogicalBitwiseAndConditionalKeepResultTypes() throws Exception {
        assertFalse(assertInstanceOf(BooleanLiteral.class, fold("-1 < 1U")).value());
        assertTrue(assertInstanceOf(BooleanLiteral.class, fold("2 == 2")).value());
        assertTrue(assertInstanceOf(BooleanLiteral.class, fold("!0")).value());
        assertEquals(BigInteger.valueOf(7), number("(5U | 2U) ^ 0U"));
        assertEquals(BigInteger.valueOf(4294967295L), number("~0U"));
        assertEquals(BigInteger.valueOf(4294967295L), number("true ? -1 : 1U"));
        assertEquals(BigInteger.valueOf(5), number("false ? 1 / 0 : 5"));
        assertEquals(BigInteger.valueOf(66), number("'A' + true"));
    }
    @Test void shiftsAndUndefinedOrImplementationDefinedOperationsStayConservative() throws Exception {
        assertEquals(BigInteger.ZERO, number("2147483648U << 1"));
        assertEquals(BigInteger.valueOf(2), number("8 >> 2"));
        for (String expression : List.of("1 << 32", "1 << -1", "-1 >> 1", "1 << 31",
                "2147483647 + 1", "(-2147483647 - 1) / -1", "(-2147483647 - 1) % -1", "~0", "-5 / 2", "-5 % 2"))
            assertFalse(fold(expression) instanceof IntegerLiteral, expression);
    }
    @Test void foldedShortCircuitNeverDropsExecutedSideEffects() throws Exception {
        assertFalse(assertInstanceOf(BooleanLiteral.class, fold("false && (1 / 0)")).value());
        assertTrue(assertInstanceOf(BooleanLiteral.class, fold("true || (1 / 0)")).value());
        assertInstanceOf(LogicalAndExpression.class, fold("true && f()"));
        assertInstanceOf(ConditionalExpression.class, fold("true ? f() : 0"));
        assertInstanceOf(AddExpression.class, fold("f() + 0"));
    }
    @Test void sizeofAndScalarTypeidDoNotExecuteUpdatesButAllocationBoundsDo() throws Exception {
        var project = read("int main() { int i = 1; sizeof(i++); typeid(i++); int *p = new int[i++]; delete[] p; return i; }", Cpg.Stage.RESOLVED);
        var updates = nodes(project, PostfixIncrementExpression.class);
        assertEquals(3, updates.size());
        var data = project.flows().getFirst().data();
        for (var update : updates.subList(0, 2))
            assertTrue(data.stream().noneMatch(e -> e.from() == update || e.to() == update));
        assertTrue(data.stream().anyMatch(e -> e.to() == updates.get(2)));
        assertTrue(data.stream().anyMatch(e -> e.from() == updates.get(2) && e.kind() == FunctionFlow.Dependency.DEFINITION));
    }
    @Test void unknownTypeidRetainsPotentialOperandEffects() throws Exception {
        var project = read("int main() { int i = 1; typeid(unknown(i = 2)); return i; }", Cpg.Stage.RESOLVED);
        var assignment = nodes(project, AssignmentExpression.class).getFirst();
        var local = nodes(project, VariableDeclaration.class).getFirst();
        var data = project.flows().getFirst().data();
        assertTrue(data.stream().anyMatch(e -> e.to() == assignment));
        assertTrue(data.stream().anyMatch(e -> e.from() == assignment && e.kind() == FunctionFlow.Dependency.DEFINITION));
        assertTrue(data.stream().anyMatch(e -> e.from() == local && e.kind() == FunctionFlow.Dependency.DEFINITION));
    }
    @Test void shiftsUsePromotedLeftWidthRatherThanRightWidth() throws Exception {
        var narrow = PlatformProfile.defaults().toBuilder().sizeBits("int", 16).alignmentBits("int", 16)
                .wcharType("long").sizeBits("wchar_t", 64).alignmentBits("wchar_t", 64).build();
        var project = read("int main() { 32768U << 1L; 1U << 16L; }", Cpg.Stage.FOLDED, narrow);
        var expressions = nodes(project, ExpressionStatement.class);
        assertEquals(BigInteger.ZERO, assertInstanceOf(IntegerLiteral.class, expressions.get(0).expression()).bigValue());
        assertInstanceOf(ShiftLeftExpression.class, expressions.get(1).expression());
    }
    @Test void foldingRetainsErrorsEvenInsideUnevaluatedBranches() throws Exception {
        for (String source : List.of(
                "struct S { unsigned b:3; }; int f(){ S s; return sizeof(s.b); }",
                "int f(){ false && sizeof(void); return 0; }",
                "int f(){ return true ? 0 : const_cast<int>(0); }")) {
            var resolved = read(source, Cpg.Stage.RESOLVED);
            var expected = nodes(resolved, Expression.class).stream().flatMap(n -> n.diagnostics().stream())
                    .filter(d -> d.severity() == Diagnostic.Severity.ERROR).map(Diagnostic::code).toList();
            assertFalse(expected.isEmpty(), source);
            assertSame(resolved, com.kniazkov.cpg.analysis.ConstantFolder.fold(resolved));
            var folded = read(source, Cpg.Stage.FOLDED);
            var actual = nodes(folded, Expression.class).stream().flatMap(n -> n.diagnostics().stream())
                    .filter(d -> d.severity() == Diagnostic.Severity.ERROR).map(Diagnostic::code).toList();
            assertEquals(expected, actual, source);
        }
    }
    @Test void booleanNarrowingAffectsShortCircuitDataFlow() throws Exception {
        var project = read("int main() { int i = 1; static_cast<unsigned char>(256) && (i = 2); return i; }", Cpg.Stage.RESOLVED);
        var assignment = nodes(project, AssignmentExpression.class).getFirst();
        assertTrue(project.flows().getFirst().data().stream().noneMatch(e -> e.to() == assignment || e.from() == assignment));
    }
}
