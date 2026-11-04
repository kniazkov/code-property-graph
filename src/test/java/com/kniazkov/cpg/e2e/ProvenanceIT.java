/* Code Property Graph: macro provenance acceptance independent of syntax ownership. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProvenanceIT {
    @TempDir Path temp;
    private Project read() throws Exception {
        return Cpg.read(Path.of("src/test/resources/cpp03/provenance/sources"),
                new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
    }
    @Test void repeatedReadsAndCallsShareOneArgumentButNotSyntaxIdentity() throws Exception {
        var project = read();
        var multiplies = new ArrayList<MultiplyExpression>();
        project.walk(new NodeVisitor() {
            @Override public void visit(MultiplyExpression node) { multiplies.add(node); }
        });
        assertEquals(2, multiplies.size());
        var names = multiplies.get(0);
        assertNotSame(names.left(), names.right());
        assertEquals("a", ((NameExpression) names.left()).name());
        assertEquals("a", ((NameExpression) names.right()).name());
        var calls = multiplies.get(1);
        assertInstanceOf(CallExpression.class, calls.left());
        assertInstanceOf(CallExpression.class, calls.right());
        assertNotSame(calls.left(), calls.right());
        for (var multiply : multiplies) {
            var p = multiply.provenance().orElseThrow();
            var event = p.expansions().stream().filter(e -> e.name().equals("SQUARE")).findFirst().orElseThrow();
            assertEquals(1, event.arguments().size());
            assertTrue(event.definition().file().endsWith("macros.h"));
            assertTrue(event.invocation().file().endsWith("main.cpp"));
            assertTrue(p.tokens().stream().anyMatch(t -> t.spelling().equals("*") && t.spellingRange().file().endsWith("macros.h")));
            var left = multiply.left().provenance().orElseThrow();
            var right = multiply.right().provenance().orElseThrow();
            assertTrue(left.expansions().stream().anyMatch(e -> e.id() == event.id()));
            assertTrue(right.expansions().stream().anyMatch(e -> e.id() == event.id()));
            assertTrue(left.endToken() <= right.startToken());
        }
        var p = calls.provenance().orElseThrow();
        var nested = p.expansions().stream().filter(e -> e.name().equals("SQUARE")).findFirst().orElseThrow();
        assertNotNull(nested.parentId());
        assertEquals("NEST", p.unit().expansions().stream().filter(e -> e.id() == nested.parentId()).findFirst().orElseThrow().name());
        var arg = nested.arguments().getFirst();
        assertEquals("f()", arg.stream().map(Preprocessing.Fragment::spelling).reduce("", String::concat));
        var all = new ArrayList<Node>();
        project.walk(new NodeVisitor() { @Override public void visit(Node n) { all.add(n); } });
        var distinct = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        distinct.addAll(all);
        assertEquals(all.size(), distinct.size());
        var copied = new ArrayList<Node>();
        project.edit().build().walk(new NodeVisitor() { @Override public void visit(Node n) { copied.add(n); } });
        assertEquals(all.size(), copied.size());
        for (int i = 0; i < all.size(); i++) assertEquals(all.get(i).provenance(), copied.get(i).provenance());
    }

    @Test void tokenBoundariesDescribePartialMultipleAndEmptyExpansions() throws Exception {
        var project = read();
        var unit = project.files().getFirst().provenance().orElseThrow().unit();
        var empty = unit.expansions().stream().filter(e -> e.name().equals("EMPTY")).findFirst().orElseThrow();
        assertEquals(empty.startToken(), empty.endToken());
        assertTrue(empty.startToken() >= 0 && empty.endToken() <= unit.tokens().size());
        var two = unit.expansions().stream().filter(e -> e.name().equals("TWO")).findFirst().orElseThrow();
        var declarations = new ArrayList<VariableDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration v) {
            if (v.provenance().orElseThrow().expansions().stream().anyMatch(e -> e.id() == two.id())) declarations.add(v);
        }});
        assertEquals(List.of("left", "right"), declarations.stream().map(VariableDeclaration::name).toList());
        var tail = unit.expansions().stream().filter(e -> e.name().equals("TAIL")).findFirst().orElseThrow();
        assertEquals("+ 2", unit.tokens().subList(tail.startToken(), tail.endToken()).stream()
                .map(Preprocessing.Token::spelling).reduce("", String::concat));
    }

    @Test void packagedCliExportsTheSameScopedOriginsAndOptionalSvgLayer() throws Exception {
        var cli = new CliSandbox(temp).directory(Path.of("src/test/resources/cpp03/provenance/sources"));
        var api = Cpg.read(cli.input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        var run = cli.run("cpp", "--stage", "resolved").expectStatus(0);
        assertEquals(ProjectJson.encode(api), Json.parse(run.work().resolve("snapshot.json").toFile()));
        var json = Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("\"preprocessing\""));
        assertTrue(json.contains("\"expansionIds\""));
        assertTrue(json.contains("macros.h"));
        String with = DotVisualizer.toDot(api, true), without = DotVisualizer.toDot(api, false);
        assertTrue(with.length() > without.length());
        assertTrue(with.contains("SQUARE"));
        assertFalse(without.contains("SQUARE"));
    }
}
