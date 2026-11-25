package com.kniazkov.cpg.tree;

import com.kniazkov.cpg.codegen.Languages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeModelTest {

    private static Source source(String text) {
        return (start, end) -> text.substring(start, end);
    }

    private static Fragment fragment(String text, int start, int end) {
        return new Fragment(
                source(text),
                new Position(start, 1, start + 1),
                new Position(end, 1, end + 1));
    }

    private static final class Stmt extends TestNode implements Statement {
        Stmt(Fragment fragment) {
            super(fragment, List.of(), List.of(), List.of());
        }
    }

    private static final class Decl extends TestNode implements Declaration {
        Decl(Fragment fragment) {
            super(fragment, List.of(), List.of(), List.of());
        }
    }

    private static final class Expr extends TestNode implements Expression {
        Expr(Fragment fragment) {
            super(fragment, List.of(), List.of(), List.of());
        }
    }

    @Test
    void positionStoresAndComparesCoordinates() {
        Position first = new Position(1, 2, 3);
        Position second = new Position(2, 2, 4);

        assertEquals(1, first.offset());
        assertEquals(2, first.line());
        assertEquals(3, first.column());
        assertTrue(first.compareTo(second) < 0);
        assertEquals(0, first.compareTo(new Position(1, 9, 9)));
        assertTrue(second.compareTo(first) > 0);
        assertEquals(first, new Position(1, 2, 3));
        assertEquals(first.hashCode(), new Position(1, 2, 3).hashCode());
        assertNotEquals(first, second);
        assertTrue(first.toString().contains("offset=1"));
    }

    @Test
    void positionRejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new Position(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, 1, 0));
    }

    @Test
    void fragmentReturnsCodeAndValidatesBoundaries() {
        Source source = source("abcdef");
        Fragment fragment = new Fragment(
                source, new Position(1, 1, 2), new Position(4, 1, 5));
        Fragment same = new Fragment(
                source, new Position(1, 1, 2), new Position(4, 1, 5));

        assertEquals("bcd", fragment.code());
        assertEquals(1, fragment.start().offset());
        assertEquals(4, fragment.end().offset());
        assertEquals(fragment, same);
        assertEquals(fragment.hashCode(), same.hashCode());
        assertTrue(fragment.toString().contains("start="));
        assertEquals("", new Fragment(
                source, new Position(2, 1, 3), new Position(2, 1, 3)).code());
    }

    @Test
    void fragmentRejectsNullsAndReversedRange() {
        Source source = source("abc");
        Position start = new Position(0, 1, 1);
        Position end = new Position(1, 1, 2);

        assertThrows(NullPointerException.class, () -> new Fragment(null, start, end));
        assertThrows(NullPointerException.class, () -> new Fragment(source, null, end));
        assertThrows(NullPointerException.class, () -> new Fragment(source, start, null));
        assertThrows(IllegalArgumentException.class, () -> new Fragment(source, end, start));
    }

    @Test
    void referenceStoresKindAndTarget() {
        TestNode target = TestNode.leaf(fragment("x", 0, 1));
        Reference reference = new Reference(EdgeKind.REFERS_TO, target);

        assertEquals(EdgeKind.REFERS_TO, reference.kind());
        assertEquals(target, reference.target());
        assertEquals(reference, new Reference(EdgeKind.REFERS_TO, target));
        assertEquals(reference.hashCode(), new Reference(EdgeKind.REFERS_TO, target).hashCode());
        assertTrue(reference.toString().contains("REFERS_TO"));
    }

    @Test
    void referenceRejectsNulls() {
        TestNode target = TestNode.leaf(fragment("x", 0, 1));

        assertThrows(NullPointerException.class, () -> new Reference(null, target));
        assertThrows(NullPointerException.class, () -> new Reference(EdgeKind.CALL, null));
    }

    @Test
    void nodeDefaultsWorkForLeaves() {
        TestNode node = TestNode.leaf(fragment("x", 0, 1));

        assertEquals("TestNode", node.name());
        assertEquals("", node.data());
        assertTrue(node.references().isEmpty());
        assertTrue(node.tags().isEmpty());
        assertTrue(node.isLeaf());
        assertTrue(node.child(-1).isEmpty());
        assertTrue(node.child(0).isEmpty());
        assertTrue(node.child("missing").isEmpty());
        assertThrows(NullPointerException.class, () -> node.child((String) null));
    }

    @Test
    void nodeChildAccessorsWorkForTaggedChildren() {
        TestNode left = TestNode.leaf(fragment("a", 0, 1));
        TestNode right = TestNode.leaf(fragment("b", 1, 2));
        TestNode node = TestNode.of(fragment("ab", 0, 2), List.of(left, right), List.of("left", "right"));

        assertFalse(node.isLeaf());
        assertEquals(left, node.child(0).orElseThrow());
        assertEquals(right, node.child(1).orElseThrow());
        assertEquals(left, node.child("left").orElseThrow());
        assertEquals(right, node.child("right").orElseThrow());
        assertTrue(node.child(2).isEmpty());
    }

    @Test
    void nodeChildByTagHandlesTagBeyondChildren() {
        TestNode node = new TestNode(
                fragment("x", 0, 1), List.of(), List.of("ghost"), List.of());

        assertTrue(node.child("ghost").isEmpty());
    }

    @Test
    void nodeNameFallsBackToClassNameForAnonymousNode() {
        Node node = new Node() {
            @Override
            public Fragment fragment() {
                return TreeModelTest.fragment("x", 0, 1);
            }

            @Override
            public List<Node> children() {
                return List.of();
            }

            @Override
            public <R> R accept(Visitor<R> visitor) {
                return visitor.visit(this);
            }
        };

        assertEquals(node.getClass().getName(), node.name());
    }

    @Test
    void visitorReceivesNode() {
        TestNode node = TestNode.leaf(fragment("x", 0, 1));
        Visitor<String> visitor = value -> value.name();

        assertEquals("TestNode", node.accept(visitor));
    }

    @Test
    void expressionListExposesElementsAndHasNoTags() {
        Fragment fragment = fragment("ab", 0, 2);
        ExpressionList list = new ExpressionList(
                fragment,
                List.of(new Expr(fragment("a", 0, 1)), new Expr(fragment("b", 1, 2))));

        assertEquals(fragment, list.fragment());
        assertEquals(2, list.elements().size());
        assertEquals(2, list.children().size());
        assertTrue(list.tags().isEmpty());
        assertEquals("ExpressionList", list.name());
        assertEquals("ExpressionList", list.accept(value -> value.name()));
    }

    @Test
    void nodeListBaseAcceptDispatchesVisitor() {
        NodeList<Expression> list = new NodeList<>(
                fragment("a", 0, 1), List.of(new Expr(fragment("a", 0, 1)))) { };

        assertEquals("ok", list.accept(value -> "ok"));
    }

    @Test
    void statementAndParameterListsWork() {
        Fragment fragment = fragment("ab", 0, 2);
        StatementList statements = new StatementList(fragment, List.of(new Stmt(fragment)));
        ParameterList parameters = new ParameterList(fragment, List.of(new Decl(fragment)));

        assertEquals(1, statements.elements().size());
        assertEquals(1, statements.children().size());
        assertEquals("StatementList", statements.accept(value -> value.name()));
        assertEquals(1, parameters.elements().size());
        assertEquals(1, parameters.children().size());
        assertEquals("ParameterList", parameters.accept(value -> value.name()));
    }

    @Test
    void emptyListIsStillAListNode() {
        ExpressionList list = new ExpressionList(fragment("", 0, 0), List.of());

        assertTrue(list.elements().isEmpty());
        assertTrue(list.children().isEmpty());
        assertTrue(list.tags().isEmpty());
    }

    @Test
    void listsRejectNulls() {
        Fragment fragment = fragment("x", 0, 1);

        assertThrows(NullPointerException.class, () -> new ExpressionList(null, List.of()));
        assertThrows(NullPointerException.class, () -> new ExpressionList(fragment, null));
        assertThrows(NullPointerException.class,
                () -> new ExpressionList(fragment, List.of((Expression) null)));
    }

    @Test
    void additionExposesBinaryStructure() {
        Fragment fragment = fragment("a+b", 0, 3);
        Expr left = new Expr(fragment("a", 0, 1));
        Expr right = new Expr(fragment("b", 2, 3));
        Addition addition = new Addition(fragment, left, right);

        assertEquals(fragment, addition.fragment());
        assertEquals(left, addition.left());
        assertEquals(right, addition.right());
        assertEquals(List.of(left, right), addition.children());
        assertEquals(List.of(BinaryExpression.LEFT_TAG, BinaryExpression.RIGHT_TAG), addition.tags());
        assertEquals("+", addition.operator());
        assertEquals("Addition", addition.name());
        assertEquals("Addition", addition.accept(value -> value.name()));
    }

    @Test
    void binaryExpressionBaseAcceptDispatchesVisitor() {
        Expr left = new Expr(fragment("a", 0, 1));
        Expr right = new Expr(fragment("b", 2, 3));
        BinaryExpression expression = new BinaryExpression(
                fragment("a+b", 0, 3), left, right) {
            @Override
            public String operator() {
                return "?";
            }
        };

        assertEquals("ok", expression.accept(value -> "ok"));
    }

    @Test
    void binaryExpressionRejectsNulls() {
        Fragment fragment = fragment("a+b", 0, 3);
        Expression left = new Expr(fragment("a", 0, 1));
        Expression right = new Expr(fragment("b", 2, 3));

        assertThrows(NullPointerException.class, () -> new Addition(null, left, right));
        assertThrows(NullPointerException.class, () -> new Addition(fragment, null, right));
        assertThrows(NullPointerException.class, () -> new Addition(fragment, left, null));
    }

    @Test
    void edgeKindsAndLanguageConstantsAreAvailable() {
        assertTrue(EdgeKind.values().length > 0);
        assertEquals(EdgeKind.CALL, EdgeKind.valueOf("CALL"));
        assertEquals("cpp03", Languages.CPP03);
        assertEquals("c", Languages.C);
        assertEquals("java", Languages.JAVA);
    }
}
