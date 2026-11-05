/* C++03 statement and exception grammar regression tests. MIT License. */
package com.kniazkov.cpg.parser;
import com.kniazkov.cpg.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Cpp03StatementParserTest {
    private SourceFile parse(String source) { return Parser.parse("statements.cpp", source, Language.CPP).build(); }
    private CallableDeclaration function(String source) { return (CallableDeclaration)parse(source).items().getLast(); }
    @Test void loopsSwitchLabelsAndEmptyStatementsHaveExplicitOwnedSyntax() {
        var function = function("int f(int n) { ; do { --n; } while (n); for (int i=0, j=1; i<n; ++i, --j) { if (i) continue; else break; } switch(n) { case 1: case 2: n=3; default: goto done; } done: return n; }");
        var body = function.body().orElseThrow();
        assertInstanceOf(EmptyStatement.class, body.items().get(0));
        var loop = assertInstanceOf(ForStatement.class, body.items().get(2));
        assertEquals(2, loop.initializers().size());
        assertInstanceOf(CommaExpression.class, loop.update().orElseThrow());
        var branch = assertInstanceOf(SwitchStatement.class, body.items().get(3));
        var cases = ((BlockStatement)branch.body()).items();
        assertEquals(1, ((IntegerLiteral)((CaseStatement)cases.getFirst()).value().orElseThrow()).value());
        assertInstanceOf(CaseStatement.class, ((CaseStatement)cases.getFirst()).statement());
        assertTrue(((CaseStatement)cases.getLast()).value().isEmpty());
        assertEquals("done", ((LabelStatement)body.items().getLast()).label());
        assertTrue(function.bodyComplete());
        assertDoesNotThrow(() -> function.edit().build());
        function.walk(new NodeVisitor() { @Override public void visit(Node n) { if (!(n instanceof Exit)) assertTrue(n.range().isPresent(), n.getClass().getName()); } });
    }
    @Test void optionalForClausesAndDeclarationConditionsRetainScopeSyntax() {
        var body = function("void f() { for (;;) ; for (int x=1; int y=x; --x) ; if (int z=1) z=2; else z=3; while (int w=1) break; switch (int v=0) { default: ; } }").body().orElseThrow();
        var empty = (ForStatement)body.items().getFirst();
        assertTrue(empty.initializers().isEmpty()); assertTrue(empty.condition().isEmpty()); assertTrue(empty.update().isEmpty());
        assertInstanceOf(ConditionDeclarationExpression.class, ((ForStatement)body.items().get(1)).condition().orElseThrow());
        assertInstanceOf(ConditionDeclarationExpression.class, ((IfStatement)body.items().get(2)).condition());
        assertInstanceOf(ConditionDeclarationExpression.class, ((WhileStatement)body.items().get(3)).condition());
        assertInstanceOf(ConditionDeclarationExpression.class, ((SwitchStatement)body.items().get(4)).condition());
    }
    @Test void throwsAndHandlersPreserveTypesNamesAndRethrow() {
        var f = function("void f() { try { throw 1; } catch (const int& e) { throw; } catch (...) { ; } }");
        var t = (TryStatement)f.body().orElseThrow().items().getFirst();
        assertEquals(1, ((IntegerLiteral)((ThrowExpression)((ExpressionStatement)t.body().items().getFirst()).expression()).value().orElseThrow()).value());
        assertEquals(2,t.handlers().size());
        assertEquals("e",t.handlers().getFirst().parameter().orElseThrow().name());
        assertTrue(t.handlers().getLast().parameter().isEmpty());
        var expression = ((ExpressionStatement)t.handlers().getFirst().body().items().getFirst()).expression();
        assertTrue(((ThrowExpression)expression).value().isEmpty());
        assertDoesNotThrow(() -> parse("void f() { try {} catch (int) {} }"));
        assertThrows(ParseException.class, () -> parse("void f() { try {} catch (...) {} catch (int) {} }"));
        assertThrows(ParseException.class, () -> parse("void f() { try {} }"));
    }
    @Test void throwIsAnAssignmentExpressionWithCommaAndConditionalBoundaries() {
        var body = function("void f(int x) { x ? throw 1 : throw 2; throw x, x; }").body().orElseThrow();
        var conditional = (ConditionalExpression)((ExpressionStatement)body.items().getFirst()).expression();
        assertInstanceOf(ThrowExpression.class, conditional.thenExpression());
        assertInstanceOf(ThrowExpression.class, conditional.elseExpression());
        assertInstanceOf(CommaExpression.class, ((ExpressionStatement)body.items().getLast()).expression());
    }
    @Test void dynamicExceptionSpecificationsAndFunctionTryBlocksRoundTripBuilders() {
        var file = parse("void first() throw(); void second() throw(int, const char*); int f() try { return 1; } catch (...) { return 2; } struct C { C() throw(int) try : n(1) {} catch (int e) { throw; } int n; operator int() const throw() try { return n; } catch (...) { return 0; } };");
        var first = (CallableDeclaration)file.items().get(0); assertTrue(first.hasExceptionSpecification()); assertTrue(first.exceptionTypes().isEmpty());
        assertEquals(2, ((CallableDeclaration)file.items().get(1)).exceptionTypes().size());
        var f = (CallableDeclaration)file.items().get(2); assertTrue(f.functionTryBlock()); assertEquals(1, f.handlers().size());
        var cls = (ClassDeclaration)file.items().get(3);
        var constructor = (CallableDeclaration)cls.members().getFirst(); assertTrue(constructor.functionTryBlock()); assertEquals("n",constructor.initializers().getFirst().name());
        var conversion = (CallableDeclaration)cls.members().getLast(); assertTrue(conversion.functionTryBlock()); assertTrue(conversion.hasExceptionSpecification());
        var copied = file.edit().build(); assertTrue(((CallableDeclaration)copied.items().get(2)).functionTryBlock());
        assertDoesNotThrow(() -> parse("struct C { C(); int n; }; C::C() try : n(0) {} catch (...) { throw; }"));
        assertDoesNotThrow(() -> parse("void (*p)() throw(int);"));
    }
    @Test void declarationSubstatementsAndFunctionalConditionsPreserveTheirSyntax() {
        assertDoesNotThrow(() -> parse("void f(int x) { if (int(x)) int y=1; else int z=2; while (bool(x)) int w=0; typedef int target; goto target; target: ; }"));
        var body = function("void f() { if (1) int x=0; }").body().orElseThrow();
        assertInstanceOf(BlockStatement.class, ((IfStatement)body.items().getFirst()).thenBranch());
    }
    @Test void truncatedBodiesAreExplicitlyIncompleteWithoutTaintingEarlierFunctions() {
        var file = Parser.parseRecovering("truncated.cpp", "int good() { return 7; } void bad() { int x=1; try { throw x; } catch (...) {", Language.CPP).build();
        assertEquals(2,file.items().size());
        assertTrue(((CallableDeclaration)file.items().getFirst()).bodyComplete());
        var bad = (CallableDeclaration)file.items().getLast(); assertFalse(bad.bodyComplete()); assertFalse(bad.body().orElseThrow().complete());
        assertEquals(2,bad.body().orElseThrow().items().size());
        var t = (TryStatement)bad.body().orElseThrow().items().getLast(); assertFalse(t.handlers().getFirst().body().complete());
        assertFalse(file.parseProblems().isEmpty());
    }
    @Test void incompleteInlineFunctionTryBlockRetainsEarlierMethodsAndProtectedBody() {
        for (String declaration : List.of("C() try {} catch (...) {", "operator int() try { return 1; } catch (...) {", "int bad() try { return 1; } catch (...) {")) {
            var file = Parser.parseRecovering("inline.cpp", "struct C { int good() { return 7; } " + declaration, Language.CPP).build();
            var cls = (ClassDeclaration)file.items().getFirst();
            assertEquals(2, cls.members().size());
            assertTrue(((CallableDeclaration)cls.members().getFirst()).bodyComplete());
            var bad = (CallableDeclaration)cls.members().getLast();
            assertFalse(bad.bodyComplete()); assertTrue(bad.body().isPresent()); assertTrue(bad.functionTryBlock());
        }
        var file = Parser.parseRecovering("missing.cpp", "void f() { try { throw 1; }", Language.CPP).build();
        var f = (CallableDeclaration)file.items().getFirst();
        assertFalse(f.bodyComplete()); assertInstanceOf(TryStatement.class, f.body().orElseThrow().items().getFirst());
        assertThrows(ParseException.class, () -> parse("struct C { C() try; };"));
    }

}
