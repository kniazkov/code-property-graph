package com.kniazkov.cpg.graph;

import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.TestNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphTest {

    private static Node node() {
        return TestNode.leaf(null);
    }

    @Test
    void emptyRelationsHaveNoLinks() {
        Relations relations = Relations.empty();

        assertTrue(relations.isEmpty());
        assertTrue(relations.sources().isEmpty());
        assertTrue(relations.targets(node()).isEmpty());
        assertTrue(relations.target(node()).isEmpty());
        assertFalse(relations.contains(node()));
    }

    @Test
    void relationsKeepInsertionOrderAndMultipleTargets() {
        Node source = node();
        Node first = node();
        Node second = node();
        Relations relations = Relations.builder()
                .add(source, first)
                .add(source, second)
                .build();

        assertFalse(relations.isEmpty());
        assertTrue(relations.contains(source));
        assertEquals(List.of(source), List.copyOf(relations.sources()));
        assertEquals(List.of(first, second), relations.targets(source));
        assertEquals(first, relations.target(source).orElseThrow());
        assertTrue(relations.targets(node()).isEmpty());
    }

    @Test
    void relationsRejectNulls() {
        Node node = node();
        Relations relations = Relations.builder().add(node, node).build();

        assertThrows(NullPointerException.class, () -> Relations.builder().add(null, node));
        assertThrows(NullPointerException.class, () -> Relations.builder().add(node, null));
        assertThrows(NullPointerException.class, () -> relations.targets(null));
        assertThrows(NullPointerException.class, () -> relations.target(null));
        assertThrows(NullPointerException.class, () -> relations.contains(null));
    }

    @Test
    void builtRelationsAreNotAffectedByLaterBuilderChanges() {
        Node source = node();
        Node first = node();
        Node second = node();
        Relations.Builder builder = Relations.builder().add(source, first);
        Relations frozen = builder.build();

        builder.add(source, second);

        assertEquals(List.of(first), frozen.targets(source));
        assertEquals(List.of(first, second), builder.build().targets(source));
    }

    @Test
    void graphRejectsNullRoot() {
        assertThrows(NullPointerException.class, () -> Graph.builder(null));
    }

    @Test
    void graphExposesRootAndEmptyRelationsByDefault() {
        Node root = node();
        Graph graph = Graph.builder(root).build();

        assertSame(root, graph.root());
        assertTrue(graph.declarations().isEmpty());
        assertTrue(graph.calls().isEmpty());
        assertTrue(graph.types().isEmpty());
        assertTrue(graph.members().isEmpty());
        assertTrue(graph.scopes().isEmpty());
        assertTrue(graph.includes().isEmpty());
        assertEquals(
                List.of("declarations", "calls", "types", "members", "scopes", "includes"),
                List.copyOf(graph.relations().keySet()));
    }

    @Test
    void graphResolvesDeclarationsAndCalls() {
        Node root = node();
        Node use = node();
        Node declaration = node();
        Node call = node();
        Node callee = node();
        Graph graph = Graph.builder(root)
                .declaration(use, declaration)
                .call(call, callee)
                .build();

        assertEquals(declaration, graph.declarationOf(use).orElseThrow());
        assertEquals(List.of(declaration), graph.declarationsOf(use));
        assertEquals(callee, graph.calleeOf(call).orElseThrow());
        assertEquals(1, graph.declarations().targets(use).size());
        assertEquals(1, graph.calls().targets(call).size());
    }

    @Test
    void graphResolvesTypesMembersScopesAndIncludes() {
        Node root = node();
        Node value = node();
        Node type = node();
        Node member = node();
        Node owner = node();
        Node scoped = node();
        Node scope = node();
        Node inclusion = node();
        Node included = node();
        Graph graph = Graph.builder(root)
                .type(value, type)
                .member(member, owner)
                .scope(scoped, scope)
                .include(inclusion, included)
                .build();

        assertEquals(type, graph.typeOf(value).orElseThrow());
        assertEquals(owner, graph.memberOf(member).orElseThrow());
        assertEquals(scope, graph.scopeOf(scoped).orElseThrow());
        assertEquals(included, graph.includeOf(inclusion).orElseThrow());
        assertEquals(1, graph.types().targets(value).size());
        assertEquals(1, graph.members().targets(member).size());
        assertEquals(1, graph.scopes().targets(scoped).size());
        assertEquals(1, graph.includes().targets(inclusion).size());
    }

    @Test
    void graphHelpersReturnEmptyForUnknownNodes() {
        Graph graph = Graph.builder(node()).build();
        Node unknown = node();

        assertTrue(graph.declarationOf(unknown).isEmpty());
        assertTrue(graph.declarationsOf(unknown).isEmpty());
        assertTrue(graph.calleeOf(unknown).isEmpty());
        assertTrue(graph.typeOf(unknown).isEmpty());
        assertTrue(graph.memberOf(unknown).isEmpty());
        assertTrue(graph.scopeOf(unknown).isEmpty());
        assertTrue(graph.includeOf(unknown).isEmpty());
    }

    @Test
    void graphHelpersRejectNulls() {
        Graph graph = Graph.builder(node()).build();

        assertThrows(NullPointerException.class, () -> graph.declarationOf(null));
        assertThrows(NullPointerException.class, () -> graph.declarationsOf(null));
        assertThrows(NullPointerException.class, () -> graph.calleeOf(null));
        assertThrows(NullPointerException.class, () -> graph.typeOf(null));
        assertThrows(NullPointerException.class, () -> graph.memberOf(null));
        assertThrows(NullPointerException.class, () -> graph.scopeOf(null));
        assertThrows(NullPointerException.class, () -> graph.includeOf(null));
    }

    @Test
    void relationsMapIsUnmodifiable() {
        Graph graph = Graph.builder(node()).build();
        Map<String, Relations> relations = graph.relations();

        assertThrows(UnsupportedOperationException.class,
                () -> relations.put("extra", Relations.empty()));
    }
}
