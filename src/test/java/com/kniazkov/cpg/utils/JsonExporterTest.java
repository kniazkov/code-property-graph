package com.kniazkov.cpg.utils;

import com.kniazkov.cpg.graph.Graph;
import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Position;
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
    void exportsTagsFragmentsAndChildren() {
        TestNode left = TestNode.leaf(fragment("a"));
        TestNode right = TestNode.leaf(fragment("b"));
        TestNode root = new TestNode(
                fragment("ab"),
                List.of(left, right),
                List.of("left", "right"));

        JsonObject json = JsonExporter.toJson(root);
        String text = json.toString();

        assertTrue(text.contains("\"format\": \"cpp03-cpg\""));
        assertTrue(text.contains("\"root\": 0"));
        assertTrue(text.contains("\"tags\": [\"left\", \"right\"]"));
        assertTrue(text.contains("\"code\": \"a\""));
        assertTrue(text.contains("\"children\": [1, 2]"));
    }

    @Test
    void toJsonStringIsIndented() {
        String text = JsonExporter.toJsonString(TestNode.leaf(fragment("x")));

        assertTrue(text.contains("\n"));
        assertTrue(text.contains("  \"format\""));
    }

    @Test
    void exportsRelations() {
        TestNode use = TestNode.leaf(fragment("a"));
        TestNode declaration = TestNode.leaf(fragment("b"));
        TestNode root = new TestNode(
                fragment("ab"), List.of(use), List.of("use"));
        Graph graph = Graph.builder(root).declaration(use, declaration).build();

        String text = JsonExporter.toJson(graph).toString();

        assertTrue(text.contains("\"version\": 2"));
        assertTrue(text.contains("\"declarations\": ["));
        assertTrue(text.contains("\"from\": 1, \"to\": 2"));
        assertTrue(text.contains("\"calls\": []"));
    }

    @Test
    void relationTargetsOutsideChildrenAreIncluded() {
        TestNode root = TestNode.leaf(fragment("x"));
        TestNode target = TestNode.leaf(fragment("y"));
        Graph graph = Graph.builder(root).call(root, target).build();

        String text = JsonExporter.toJson(graph).toString();

        assertTrue(text.contains("\"id\": 1"));
        assertTrue(text.contains("\"calls\": ["));
        assertTrue(text.contains("\"from\": 0, \"to\": 1"));
    }

    @Test
    void duplicateNodesAreExportedOnce() {
        TestNode shared = TestNode.leaf(fragment("a"));
        TestNode root = new TestNode(
                fragment("aa"), List.of(shared, shared), List.of("first", "second"));

        String text = JsonExporter.toJson(root).toString();

        assertEquals(2, count(text, "\"id\":"));
    }

    @Test
    void nullRootIsRejected() {
        assertThrows(NullPointerException.class, () -> JsonExporter.toJson((Node) null));
        assertThrows(NullPointerException.class, () -> JsonExporter.toJsonString((Node) null));
    }

    @Test
    void nullGraphIsRejected() {
        assertThrows(NullPointerException.class, () -> JsonExporter.toJson((Graph) null));
        assertThrows(NullPointerException.class, () -> JsonExporter.toJsonString((Graph) null));
    }

    @Test
    void nullFragmentIsExportedAsNull() {
        TestNode node = new TestNode(null, List.of(), List.of());

        String text = JsonExporter.toJsonString(node);

        assertTrue(text.contains("\"fragment\": null"));
    }

    @Test
    void customNameIsUsedAsType() {
        TestNode node = new TestNode(
                fragment("x"), List.of(), List.of(), "Custom", "data");

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
                List.of("first", "second"));

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
