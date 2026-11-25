package com.kniazkov.cpg.utils;

import com.kniazkov.cpg.tree.EdgeKind;
import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Position;
import com.kniazkov.cpg.tree.Reference;
import com.kniazkov.cpg.tree.Source;
import com.kniazkov.cpg.tree.TestNode;
import com.kniazkov.cpg.tree.Visitor;
import com.kniazkov.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonExporterTest {

    private static Source source(String text) {
        return (start, end) -> text.substring(start, end);
    }

    private static Fragment fragment(String text) {
        return new Fragment(
                source(text),
                new Position(0, 1, 1),
                new Position(text.length(), 1, text.length() + 1));
    }

    @Test
    void exportsTagsFragmentsChildrenAndReferences() {
        TestNode left = TestNode.leaf(fragment("a"));
        TestNode right = TestNode.leaf(fragment("b"));
        TestNode root = new TestNode(
                fragment("ab"),
                List.of(left, right),
                List.of("left", "right"),
                List.of(new Reference(EdgeKind.REFERS_TO, right)));

        JsonObject json = JsonExporter.toJson(root);
        String text = json.toString();

        assertTrue(text.contains("\"format\": \"cpp03-cpg\""));
        assertTrue(text.contains("\"root\": 0"));
        assertTrue(text.contains("\"tags\": [\"left\", \"right\"]"));
        assertTrue(text.contains("\"code\": \"a\""));
        assertTrue(text.contains("\"kind\": \"REFERS_TO\""));
        assertTrue(text.contains("\"target\": 2"));
    }

    @Test
    void toJsonStringIsIndented() {
        String text = JsonExporter.toJsonString(TestNode.leaf(fragment("x")));

        assertTrue(text.contains("\n"));
        assertTrue(text.contains("  \"format\""));
    }

    @Test
    void nullRootIsRejected() {
        assertThrows(NullPointerException.class, () -> JsonExporter.toJson(null));
        assertThrows(NullPointerException.class, () -> JsonExporter.toJsonString(null));
    }

    @Test
    void nullFragmentIsExportedAsNull() {
        TestNode node = new TestNode(null, List.of(), List.of(), List.of());

        String text = JsonExporter.toJsonString(node);

        assertTrue(text.contains("\"fragment\": null"));
    }

    @Test
    void referencedNodesAreIncludedInClosure() {
        TestNode target = TestNode.leaf(fragment("x"));
        TestNode root = new TestNode(
                fragment("x"),
                List.of(),
                List.of(),
                List.of(new Reference(EdgeKind.CALL, target)));

        String text = JsonExporter.toJsonString(root);

        assertTrue(text.contains("\"target\": 1"));
        assertTrue(text.contains("\"id\": 1"));
    }

    @Test
    void cyclesAreExportedOnlyOnce() {
        Node cycle = new Node() {
            @Override
            public Fragment fragment() {
                return JsonExporterTest.fragment("a");
            }

            @Override
            public List<Node> children() {
                return List.of();
            }

            @Override
            public List<Reference> references() {
                return List.of(new Reference(EdgeKind.NEXT, this));
            }

            @Override
            public <R> R accept(Visitor<R> visitor) {
                return visitor.visit(this);
            }
        };
        TestNode root = new TestNode(
                fragment("r"),
                List.of(),
                List.of(),
                List.of(new Reference(EdgeKind.NEXT, cycle)));

        String text = JsonExporter.toJsonString(root);

        assertEquals(2, count(text, "\"id\":"));
    }

    @Test
    void multipleChildrenAndReferencesKeepOrder() {
        TestNode first = TestNode.leaf(fragment("a"));
        TestNode second = TestNode.leaf(fragment("b"));
        TestNode root = new TestNode(
                fragment("ab"),
                List.of(first, second),
                List.of("first", "second"),
                List.of(
                        new Reference(EdgeKind.NEXT, second),
                        new Reference(EdgeKind.CALL, first)));

        String text = JsonExporter.toJson(root).toString();

        assertTrue(text.contains("\"children\": [1, 2]"));
        assertTrue(text.contains("\"kind\": \"NEXT\", \"target\": 2"));
        assertTrue(text.contains("\"kind\": \"CALL\", \"target\": 1"));
    }

    @Test
    void customNameIsUsedAsType() {
        TestNode node = new TestNode(
                fragment("x"), List.of(), List.of(), List.of(), "Custom", "data");

        String text = JsonExporter.toJsonString(node);

        assertTrue(text.contains("\"type\": \"Custom\""));
    }

    @Test
    void anonymousNodeUsesQualifiedName() {
        Node node = new Node() {
            @Override
            public Fragment fragment() {
                return JsonExporterTest.fragment("x");
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

        String text = JsonExporter.toJsonString(node);

        assertTrue(text.contains("\"type\": \"" + node.getClass().getName() + "\""));
    }

    @Test
    void idsStartAtZeroAndIncreaseMonotonically() {
        TestNode first = TestNode.leaf(fragment("a"));
        TestNode second = TestNode.leaf(fragment("b"));
        TestNode root = new TestNode(
                fragment("ab"),
                List.of(first, second),
                List.of("first", "second"),
                List.of());

        String text = JsonExporter.toJsonString(root);
        Matcher matcher = Pattern.compile("\"id\": (\\d+)").matcher(text);

        int expected = 0;
        while (matcher.find()) {
            assertEquals(expected, Integer.parseInt(matcher.group(1)));
            expected++;
        }
        assertEquals(3, expected);
    }

    private static int count(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
