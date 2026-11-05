/* C++03 operator precedence and delimiter regression tests. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CppOperatorExpressionTest {
    private static SourceFile parse(String text) { return Parser.parse("operators.cpp", text, Language.CPP).build(); }
    private static Expression expression(String text) {
        var file = parse("int f() { return " + text + "; }");
        var function = (FunctionDeclaration) file.items().getFirst();
        return ((ReturnStatement) function.body().orElseThrow().items().getFirst()).value().orElseThrow();
    }
    private static String shape(Node node) {
        String value = node instanceof NameExpression n ? n.name()
                : node instanceof IntegerLiteral n ? n.bigValue().toString()
                : node instanceof CompoundAssignmentExpression n ? n.operator()
                : node instanceof NamedType n ? n.name() : "";
        return node.getClass().getSimpleName() + "[" + value + "](" + node.children().stream()
                .map(CppOperatorExpressionTest::shape).collect(java.util.stream.Collectors.joining(",")) + ")";
    }
    @Test void everyBinaryLevelHasCppBindingPower() {
        var comma = assertInstanceOf(CommaExpression.class, expression("a = b ? c : d || e && f | g ^ h & i == j < k << l + m * n.*p, q"));
        assertEquals("q", ((NameExpression) comma.right()).name());
        var assignment = assertInstanceOf(AssignmentExpression.class, comma.left());
        var conditional = assertInstanceOf(ConditionalExpression.class, assignment.value());
        var logicalOr = assertInstanceOf(LogicalOrExpression.class, conditional.elseExpression());
        var logicalAnd = assertInstanceOf(LogicalAndExpression.class, logicalOr.right());
        var bitOr = assertInstanceOf(BitwiseOrExpression.class, logicalAnd.right());
        var bitXor = assertInstanceOf(BitwiseXorExpression.class, bitOr.right());
        var bitAnd = assertInstanceOf(BitwiseAndExpression.class, bitXor.right());
        var equality = assertInstanceOf(EqualExpression.class, bitAnd.right());
        var less = assertInstanceOf(LessExpression.class, equality.right());
        var shift = assertInstanceOf(ShiftLeftExpression.class, less.right());
        var add = assertInstanceOf(AddExpression.class, shift.right());
        var multiply = assertInstanceOf(MultiplyExpression.class, add.right());
        assertInstanceOf(DotMemberPointerExpression.class, multiply.right());
        assertInstanceOf(ArrowMemberPointerExpression.class, expression("p->*member"));
    }
    @Test void assignmentAndConditionalAssociateRightWhileArithmeticAssociatesLeft() {
        assertInstanceOf(AssignmentExpression.class, ((AssignmentExpression) expression("a=b=c")).value());
        assertInstanceOf(ConditionalExpression.class, ((ConditionalExpression) expression("a?b:c?d:e")).elseExpression());
        var conditional = (ConditionalExpression) expression("a ? b,c : d=e");
        assertInstanceOf(CommaExpression.class, conditional.thenExpression());
        assertInstanceOf(AssignmentExpression.class, conditional.elseExpression());
        assertInstanceOf(SubtractExpression.class, ((SubtractExpression) expression("a-b-c")).left());
        assertInstanceOf(ShiftRightExpression.class, ((ShiftRightExpression) expression("a>>b>>c")).left());
    }
    @Test void prefixPostfixIndexAndMembersBindBeforeInfixOperations() {
        var add = (AddExpression) expression("++a[1] + b->member--");
        assertInstanceOf(IndexExpression.class, ((PrefixIncrementExpression) add.left()).operand());
        assertInstanceOf(PointerMemberExpression.class, ((PostfixDecrementExpression) add.right()).operand());
        assertInstanceOf(PostfixIncrementExpression.class, ((DereferenceExpression) expression("*p++")).operand());
        assertInstanceOf(LogicalNotExpression.class, expression("!x"));
        assertInstanceOf(BitwiseNotExpression.class, expression("~x"));
        assertInstanceOf(PrefixDecrementExpression.class, expression("--x"));
        assertInstanceOf(RemainderExpression.class, expression("a%b"));
    }
    @Test void allCompoundAssignmentsAreRightAssociativeAndPreserveTheirOperator() {
        for (String operator : List.of("+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "^=", "|=")) {
            var assignment = assertInstanceOf(CompoundAssignmentExpression.class, expression("a" + operator + "b" + operator + "c"));
            assertEquals(operator, assignment.operator());
            assertEquals(operator, ((CompoundAssignmentExpression) assignment.value()).operator());
            assertEquals(operator, assignment.edit().build().operator());
        }
        assertInstanceOf(DereferenceExpression.class, ((AssignmentExpression) expression("*p=1")).target());
        assertInstanceOf(IndexExpression.class, ((AssignmentExpression) expression("p[0]=1")).target());
    }
    @Test void commasInArgumentsDeclarationsAndParenthesesHaveDifferentOwners() {
        var file = parse("int f() { int a=1,b=2; return g(a,b,(a,b)); }");
        var body = ((FunctionDeclaration) file.items().getFirst()).body().orElseThrow();
        assertEquals(3, body.items().size());
        var call = (CallExpression) ((ReturnStatement) body.items().get(2)).value().orElseThrow();
        assertEquals(3, call.arguments().size());
        assertInstanceOf(CommaExpression.class, call.arguments().get(2));
        assertInstanceOf(CommaExpression.class, ((IndexExpression) expression("a[b,c]")).right());
        parse("int f(){ int a[1+2],b[3]; if(a[0],b[0]) return 1,2; return 0; }");
    }
    private static NameExpression.Builder typedName(String name) {
        var builder = NameExpression.create().setName(name);
        builder.setExpressionInfo(new ExpressionInfo(java.util.Optional.of("int"), ValueCategory.LVALUE));
        return builder;
    }
    private static IntegerLiteral.Builder typedInteger(int value) {
        var builder = IntegerLiteral.create().setValue(value);
        builder.setExpressionInfo(new ExpressionInfo(java.util.Optional.of("int"), ValueCategory.RVALUE));
        return builder;
    }
    @Test void editingAssignmentAndConditionalSyntaxInvalidatesSemanticFacts() {
        var facts = new ExpressionInfo(java.util.Optional.of("int"), ValueCategory.LVALUE);
        var assignmentBuilder = AssignmentExpression.create().setTarget(typedName("x")).setValue(typedInteger(1));
        assignmentBuilder.setExpressionInfo(facts);
        var assignment = assignmentBuilder.build();
        assertEquals(facts, assignment.edit().build().semanticInfo().orElseThrow());
        assertTrue(assignment.edit().setTarget(typedName("y")).build().semanticInfo().isEmpty());
        assertTrue(assignment.edit().setValue(typedInteger(2)).build().semanticInfo().isEmpty());
        var compoundBuilder = CompoundAssignmentExpression.create().setTarget(typedName("x"))
                .setValue(typedInteger(1)).setOperator("+=");
        compoundBuilder.setExpressionInfo(facts);
        var compound = compoundBuilder.build();
        assertEquals(facts, compound.edit().build().semanticInfo().orElseThrow());
        assertTrue(compound.edit().setTarget(typedName("y")).build().semanticInfo().isEmpty());
        assertTrue(compound.edit().setValue(typedInteger(2)).build().semanticInfo().isEmpty());
        assertTrue(compound.edit().setOperator("-=").build().semanticInfo().isEmpty());
        var conditionalBuilder = ConditionalExpression.create().setCondition(typedName("c"))
                .setThenExpression(typedName("x")).setElseExpression(typedName("y"));
        conditionalBuilder.setExpressionInfo(facts);
        var conditional = conditionalBuilder.build();
        assertEquals(facts, conditional.edit().build().semanticInfo().orElseThrow());
        assertTrue(conditional.edit().setCondition(typedInteger(1)).build().semanticInfo().isEmpty());
        assertTrue(conditional.edit().setThenExpression(typedInteger(1)).build().semanticInfo().isEmpty());
        assertTrue(conditional.edit().setElseExpression(typedInteger(1)).build().semanticInfo().isEmpty());
    }
    @Test void sizeofParenthesizedExpressionIncludesItsPostfixContinuation() {
        var index = assertInstanceOf(SizeofExpression.class, expression("sizeof (a)[0]"));
        assertInstanceOf(IndexExpression.class, index.operand().orElseThrow());
        var call = assertInstanceOf(SizeofExpression.class, expression("sizeof (f)(x)"));
        assertInstanceOf(CallExpression.class, call.operand().orElseThrow());
        var member = assertInstanceOf(SizeofExpression.class, expression("sizeof (s).member"));
        assertInstanceOf(DotMemberExpression.class, member.operand().orElseThrow());
        var increment = assertInstanceOf(SizeofExpression.class, expression("sizeof (x)++"));
        assertInstanceOf(PostfixIncrementExpression.class, increment.operand().orElseThrow());
        for (String text : List.of("sizeof (a)[0]", "sizeof (f)(x)", "sizeof (s).member", "sizeof (x)++", "sizeof(int)")) {
            var original = expression(text);
            assertEquals(shape(original), shape(expression(original.source(Language.CPP))), text);
        }
        parse("int main() { int a[3]; return sizeof (a)[0] == sizeof(int) ? 0 : 1; }");
    }
    @Test void staticCastOwnsTypeAndExpressionAndRemainsAnExplicitOperation() {
        var cast = assertInstanceOf(ConversionExpression.class, expression("static_cast<long>(x)"));
        assertEquals("long", cast.targetType().canonicalIdentity());
        assertTrue(cast.isExplicit());
        assertEquals("x", ((NameExpression) cast.operand()).name());
        assertInstanceOf(CommaExpression.class, ((ConversionExpression) expression("static_cast<long>((x,y))")).operand());
    }
    @Test void cppExtensionsDoNotSilentlyExpandJavaGrammar() {
        for (String text : List.of("a,b", "a+=b", "a?b:c", "a&&b", "a[0]", "a++", "~a", "static_cast<long>(a)"))
            assertThrows(ParseException.class, () -> Parser.parse("A.java", "class A { int f() { return " + text + "; } }", Language.JAVA));
    }
    @Test void sourceRoundtripPreservesGroupingAndOperatorTrees() {
        for (String text : List.of("a-(b-c)", "a/(b*c)", "(a=b)=c", "a*(b+c)", "a ? (b,c) : (d=e)", "(a,b)+c", "a&&(b||c)", "(a.*p)+b", "f((a,b),c)", "static_cast<long>(x)", "(*p)++")) {
            var original = expression(text);
            var generated = original.source(Language.CPP);
            assertEquals(shape(original), shape(expression(generated)), text);
            assertEquals(generated, expression(generated).source(Language.CPP), text);
            assertEquals(generated, original.edit().build().source(Language.CPP), text);
        }
    }
}
