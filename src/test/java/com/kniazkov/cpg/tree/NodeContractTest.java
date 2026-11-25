package com.kniazkov.cpg.tree;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeContractTest {

    private static Source source(String text) {
        return (start, end) -> text.substring(start, end);
    }

    private static Fragment fragment(String text) {
        return new Fragment(
                source(text),
                new Position(0, 1, 1),
                new Position(text.length(), 1, text.length() + 1));
    }

    private static final class NullTagsNode implements Node {
        private final Fragment fragment;

        NullTagsNode(Fragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public Fragment fragment() {
            return fragment;
        }

        @Override
        public List<Node> children() {
            return List.of();
        }

        @Override
        public List<String> tags() {
            return null;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    private static final class NullChildrenNode implements Node {
        private final Fragment fragment;

        NullChildrenNode(Fragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public Fragment fragment() {
            return fragment;
        }

        @Override
        public List<Node> children() {
            return null;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    private static final class NullTagNode implements Node {
        private final Fragment fragment;
        private final Node child;

        NullTagNode(Fragment fragment, Node child) {
            this.fragment = fragment;
            this.child = child;
        }

        @Override
        public Fragment fragment() {
            return fragment;
        }

        @Override
        public List<Node> children() {
            return List.of(child);
        }

        @Override
        public List<String> tags() {
            return Collections.singletonList(null);
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    private static final class NullChildNode implements Node {
        private final Fragment fragment;

        NullChildNode(Fragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public Fragment fragment() {
            return fragment;
        }

        @Override
        public List<Node> children() {
            return Collections.singletonList(null);
        }

        @Override
        public List<String> tags() {
            return List.of("child");
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    private static final class MismatchNode extends TestNode {
        private final Node wrong;

        MismatchNode(Fragment fragment, Node child, Node wrong) {
            super(fragment, List.of(child), List.of("child"));
            this.wrong = wrong;
        }

        @Override
        public Optional<Node> child(String tag) {
            return Optional.of(wrong);
        }
    }

    private static final class TaggedList extends NodeList<Expression> {
        TaggedList(Fragment fragment, List<Expression> elements) {
            super(fragment, elements);
        }

        @Override
        public List<String> tags() {
            return List.of("unexpected");
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    private static final class SelfCycleNode implements Node {
        private final Fragment fragment;

        SelfCycleNode(Fragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public Fragment fragment() {
            return fragment;
        }

        @Override
        public List<Node> children() {
            return List.of(this);
        }

        @Override
        public List<String> tags() {
            return List.of("self");
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    @Test
    void validTreeHasNoViolations() {
        Fragment fragment = fragment("ab");
        TestNode left = TestNode.leaf(fragment("a"));
        TestNode right = TestNode.leaf(fragment("b"));
        TestNode root = TestNode.of(fragment, List.of(left, right), List.of("left", "right"));

        assertTrue(NodeContract.violations(root).isEmpty());
        NodeContract.requireValid(root);
    }

    @Test
    void nullRootIsRejected() {
        assertThrows(NullPointerException.class, () -> NodeContract.violations(null));
        assertThrows(NullPointerException.class, () -> NodeContract.requireValid(null));
    }

    @Test
    void duplicateTagsAreReported() {
        Fragment fragment = fragment("ab");
        TestNode root = TestNode.of(
                fragment,
                List.of(TestNode.leaf(fragment("a")), TestNode.leaf(fragment("b"))),
                List.of("same", "same"));

        List<String> violations = NodeContract.violations(root);

        assertTrue(violations.stream().anyMatch(value -> value.contains("duplicate tag")));
        assertThrows(IllegalStateException.class, () -> NodeContract.requireValid(root));
    }

    @Test
    void nullTagsAreReported() {
        List<String> violations = NodeContract.violations(new NullTagsNode(fragment("x")));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("null tags"));
    }

    @Test
    void nullChildrenAreReported() {
        List<String> violations = NodeContract.violations(new NullChildrenNode(fragment("x")));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("null children"));
    }

    @Test
    void nullTagIsReportedAndSkipped() {
        TestNode child = TestNode.leaf(fragment("x"));
        List<String> violations = NodeContract.violations(new NullTagNode(fragment("x"), child));

        assertTrue(violations.stream().anyMatch(value -> value.contains("null tag")));
    }

    @Test
    void tagCountMismatchIsReported() {
        TestNode root = TestNode.of(
                fragment("ab"),
                List.of(TestNode.leaf(fragment("a")), TestNode.leaf(fragment("b"))),
                List.of("only-one"));

        List<String> violations = NodeContract.violations(root);

        assertTrue(violations.stream().anyMatch(value -> value.contains("tags but")));
    }

    @Test
    void emptyListHasNoViolations() {
        ExpressionList list = new ExpressionList(fragment(""), List.of());

        assertTrue(NodeContract.violations(list).isEmpty());
    }

    @Test
    void taggedListIsReported() {
        TaggedList list = new TaggedList(
                fragment("1"), List.of(new IntegerLiteral(fragment("1"), "1")));

        List<String> violations = NodeContract.violations(list);

        assertTrue(violations.stream().anyMatch(value -> value.contains("NodeList but has tags")));
    }

    @Test
    void mismatchedChildLookupIsReported() {
        TestNode child = TestNode.leaf(fragment("x"));
        TestNode wrong = TestNode.leaf(fragment("y"));
        MismatchNode root = new MismatchNode(fragment("x"), child, wrong);

        List<String> violations = NodeContract.violations(root);

        assertTrue(violations.stream().anyMatch(value -> value.contains("does not match")));
    }

    @Test
    void nullChildIsReported() {
        List<String> violations = NodeContract.violations(new NullChildNode(fragment("x")));

        assertTrue(violations.stream().anyMatch(value -> value.contains("null child")));
    }

    @Test
    void cyclesAreVisitedOnlyOnce() {
        SelfCycleNode root = new SelfCycleNode(fragment("x"));

        assertTrue(NodeContract.violations(root).isEmpty());
    }

    @Test
    void anonymousNodeUsesQualifiedNameInViolation() {
        Node node = new Node() {
            @Override
            public Fragment fragment() {
                return NodeContractTest.fragment("x");
            }

            @Override
            public List<Node> children() {
                return List.of();
            }

            @Override
            public List<String> tags() {
                return null;
            }

            @Override
            public <R> R accept(Visitor<R> visitor) {
                return visitor.visit(this);
            }
        };

        List<String> violations = NodeContract.violations(node);

        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).startsWith(node.getClass().getName()));
    }
}
