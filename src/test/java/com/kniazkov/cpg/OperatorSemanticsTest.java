/* Overloaded operators remain syntax with conservative call semantics. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OperatorSemanticsTest {
    @TempDir Path temp;
    Project read(String source, Cpg.Stage stage) throws Exception {
        Files.writeString(temp.resolve("operators.cpp"), source);
        return Cpg.read(temp, new Cpg.Options(Language.CPP, stage));
    }
    static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    @Test void moneyAdditionKeepsNominalReturnAndImplementationAcrossStages() throws Exception {
        String source = "struct Money {}; Money operator+(const Money& a, const Money& b); "
                + "Money operator+(const Money& a, const Money& b){ return a; } "
                + "int main(){ Money price; Money tax; Money total=price+tax; }";
        for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
            var project = read(source, stage);
            var addition = nodes(project, AddExpression.class).getFirst();
            assertEquals(CallExpression.Resolution.RESOLVED, addition.operatorResolution(), stage.name());
            assertEquals("operator+", addition.operatorTarget().orElseThrow().name());
            assertTrue(addition.operatorTarget().orElseThrow().definition().isPresent() || addition.operatorTarget().orElseThrow().body().isPresent());
            assertInstanceOf(NameExpression.class, addition.left());
            assertInstanceOf(NameExpression.class, addition.right());
            assertEquals("named(Money)", addition.semanticInfo().orElseThrow().resultType().orElseThrow());
            assertEquals(ValueCategory.RVALUE, addition.semanticInfo().orElseThrow().category());
            assertTrue(addition.source(Language.CPP).contains("price + tax"));
        }
    }
    @Test void logicalOverloadReadsBothOperandsAndCannotFold() throws Exception {
        var project = read("struct Flag {}; bool operator&&(Flag a, int b); int main(){ Flag a; int n=3; a && n; }", Cpg.Stage.FOLDED);
        var logical = nodes(project, LogicalAndExpression.class).getFirst();
        assertTrue(logical.operatorTarget().isPresent());
        var right = assertInstanceOf(ConversionExpression.class, logical.right());
        assertEquals("int", right.targetType().canonicalIdentity());
        assertInstanceOf(NameExpression.class, right.operand());
        var n = nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals("n")).findFirst().orElseThrow();
        assertTrue(n.diagnostics().stream().noneMatch(d -> d.code().equals(UnreadWrites.CODE)));
        assertTrue(project.flows().stream().flatMap(f -> f.data().stream()).anyMatch(e -> e.from() == n && e.to() == right.operand()));
    }
    @Test void builtinShortCircuitStillOmitsRightRead() throws Exception {
        var project = read("int main(){ int n=3; false && n; }", Cpg.Stage.RESOLVED);
        var logical = nodes(project, LogicalAndExpression.class).getFirst();
        assertTrue(logical.operatorTarget().isEmpty());
        var n = nodes(project, VariableDeclaration.class).getFirst();
        assertTrue(n.diagnostics().stream().anyMatch(d -> d.code().equals(UnreadWrites.CODE)));
    }
    @Test void memberOperatorsUseDeclaredResultCategories() throws Exception {
        var project = read("struct Counter { Counter& operator++(); Counter operator++(int); int& operator[](int); int operator()(int); }; "
                + "int main(){ Counter c; ++c; c++; c[1]; c(2); }", Cpg.Stage.RESOLVED);
        for (Class<? extends Expression> kind : List.of(PrefixIncrementExpression.class, PostfixIncrementExpression.class, IndexExpression.class, CallExpression.class)) {
            var expression = nodes(project, kind).getFirst();
            assertTrue(expression.operatorTarget().isPresent(), kind.getSimpleName());
            assertEquals(kind == PrefixIncrementExpression.class || kind == IndexExpression.class ? ValueCategory.LVALUE : ValueCategory.RVALUE,
                    TypeSemantics.valueCategory(expression, PlatformProfile.defaults()));
        }
    }
    @Test void conversionFunctionIsAnExplicitEffectFactOnImplicitWrapper() throws Exception {
        var project = read("struct Number { operator int() const; }; void accept(int); int main(){ Number n; accept(n); }", Cpg.Stage.RESOLVED);
        var call = nodes(project, CallExpression.class).getFirst();
        var conversion = assertInstanceOf(ConversionExpression.class, call.arguments().getFirst());
        assertEquals(ConversionExpression.Kind.USER_DEFINED, conversion.kind());
        assertTrue(conversion.operatorTarget().orElseThrow().conversionType().isPresent());
        assertEquals("int", TypeSemantics.expressionType(conversion, PlatformProfile.defaults()).orElseThrow().canonicalIdentity());
        assertEquals("n", conversion.source(Language.CPP));
    }
    @Test void builtinArithmeticThroughSingleConversionKeepsCallEffect() throws Exception {
        var project = read("struct Number { operator int() const; }; int main(){ Number n; int x=n+1; }", Cpg.Stage.FOLDED);
        var addition = nodes(project, AddExpression.class).getFirst();
        assertTrue(addition.operatorTarget().isEmpty());
        var conversion = assertInstanceOf(ConversionExpression.class, addition.left());
        assertEquals(ConversionExpression.Kind.USER_DEFINED, conversion.kind());
        assertTrue(conversion.operatorTarget().isPresent());
        assertEquals("int", addition.semanticInfo().orElseThrow().resultType().orElseThrow());
    }
    @Test void convertingConstructorWrapperPreservesBoundNominalType() throws Exception {
        var project = read("struct Box { Box(int); }; void accept(Box); int main(){ accept(1); }", Cpg.Stage.RESOLVED);
        var conversion = assertInstanceOf(ConversionExpression.class, nodes(project, CallExpression.class).getFirst().arguments().getFirst());
        assertEquals(ConversionExpression.Kind.USER_DEFINED, conversion.kind());
        assertEquals(CallableDeclaration.Kind.CONSTRUCTOR, conversion.operatorTarget().orElseThrow().kind());
        assertEquals("named(Box)", TypeSemantics.expressionType(conversion, PlatformProfile.defaults()).orElseThrow().canonicalIdentity());
        assertTrue(ClassSemantics.objectClass(conversion.targetType()).isPresent());
    }

    @Test void overloadedOperatorArgumentsRetainUserDefinedConversionCalls() throws Exception {
        var project = read("struct Money {}; struct Number { operator int() const; }; "
                + "Money operator+(Money, int); int main(){ Money m; Number n; m+n; }", Cpg.Stage.RESOLVED);
        var addition = nodes(project, AddExpression.class).getFirst();
        assertTrue(addition.operatorTarget().isPresent());
        var conversion = assertInstanceOf(ConversionExpression.class, addition.right());
        assertEquals(ConversionExpression.Kind.USER_DEFINED, conversion.kind());
        assertTrue(conversion.operatorTarget().isPresent());
    }

    @Test void builtinAndUserOperatorCompeteByConversionVector() throws Exception {
        String source = "struct A { A(int); operator int() const; }; A operator+(A,A); "
                + "int main(){ A a(1); a+1; }";
        var project = read(source, Cpg.Stage.RESOLVED);
        var addition = nodes(project, AddExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.AMBIGUOUS, addition.operatorResolution());
        assertTrue(addition.operatorTarget().isEmpty());
        assertEquals(1, addition.operatorCandidates().size());
        assertTrue(addition.operatorCandidates().stream().noneMatch(c -> c.name().equals("<builtin>")));
        assertTrue(addition.diagnostics().stream().anyMatch(d -> d.code().equals("ambiguous-overload")));
        assertSame(project, ConstantFolder.fold(project));
    }
    @Test void nativeCompilerConfirmsCrossedBuiltinAndUserConversionAmbiguity() throws Exception {
        var compiler = Path.of("/usr/bin/g++");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(compiler), "Native GCC is not installed");
        var source = temp.resolve("competition.cpp");
        Files.writeString(source, "struct A { A(int); operator int() const; }; A operator+(A,A); int main(){ A a(1); a+1; }");
        var process = new ProcessBuilder(compiler.toString(), "-std=c++03", "-pedantic-errors", "-fsyntax-only", source.toString())
                .redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertNotEquals(0, process.waitFor(), output);
        assertTrue(output.contains("ambiguous"), output);
        assertTrue(output.contains("built-in"), output);
    }
    @Test void exactUserOperatorBeatsBuiltinUserConversion() throws Exception {
        var project = read("struct A { operator int() const; }; A operator+(A,int); int main(){ A a; a+1; }", Cpg.Stage.RESOLVED);
        var addition = nodes(project, AddExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.RESOLVED, addition.operatorResolution());
        assertEquals("operator+", addition.operatorTarget().orElseThrow().name());
    }
    @Test void builtinBeatsWorseUserOperatorAndPreservesConversionEffect() throws Exception {
        var project = read("struct A { operator int() const; }; struct B { B(int); }; int operator+(int,B); int main(){ A a; a+1; }", Cpg.Stage.RESOLVED);
        var addition = nodes(project, AddExpression.class).getFirst();
        assertTrue(addition.operatorTarget().isEmpty());
        assertTrue(addition.operatorCandidates().isEmpty());
        var conversion = assertInstanceOf(ConversionExpression.class, addition.left());
        assertEquals(ConversionExpression.Kind.USER_DEFINED, conversion.kind());
        assertTrue(conversion.operatorTarget().isPresent());
    }

    @Test void selectedPrivateOperatorIsRejectedWithoutSelectingWorseCandidate() throws Exception {
        var project = read("class A { int operator+(int); public: A(); }; int main(){ A a; a+1; }", Cpg.Stage.RESOLVED);
        var addition = nodes(project, AddExpression.class).getFirst();
        assertTrue(addition.operatorTarget().isPresent());
        assertTrue(addition.diagnostics().stream().anyMatch(d -> d.code().equals("inaccessible-overload")));
    }
    @Test void logicalBuiltinCandidateAlsoCompetesWithUserOperator() throws Exception {
        var project = read("struct A { A(bool); operator bool() const; }; bool operator&&(A,A); int main(){ A a(true); a && true; }", Cpg.Stage.RESOLVED);
        var logical = nodes(project, LogicalAndExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.AMBIGUOUS, logical.operatorResolution());
        assertTrue(logical.operatorTarget().isEmpty());
        assertTrue(logical.diagnostics().stream().anyMatch(d -> d.code().equals("ambiguous-overload")));
    }

}
