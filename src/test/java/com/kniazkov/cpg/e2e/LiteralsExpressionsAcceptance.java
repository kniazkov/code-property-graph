/* Code Property Graph: literal values and standard conversions through public paths. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class LiteralsExpressionsAcceptance {
    private LiteralsExpressionsAcceptance() {}
    record Output(Path original, Path generated, Path folded, Path invalid) {}
    static Output verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var options = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03");
        Project project = Cpg.read(cli.input, options);
        verifyGraph(project);
        verifyGraph(project.edit().build());
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        String emitted = Files.readString(run.work().resolve("generated/main.cpp"));
        assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), emitted);
        assertTrue(emitted.contains("static_cast<long>"), emitted);
        assertEquals(1, count(emitted, "static_cast<long>"), "Implicit conversion must not become a written cast");
        verifyGraph(Cpg.read(run.work().resolve("generated"), options));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        var folded = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        assertTrue(Files.isRegularFile(folded.work().resolve("generated/main.cpp")));
        var invalid = new CliSandbox(Files.createDirectory(work.resolve("invalid"))).directory(fixture.resolve("invalid"));
        Project broken = Cpg.readRecovering(invalid.input, options);
        assertTrue(broken.hasErrors(), "C++11 binary literals are not C++03");
        var failed = invalid.run("cpp", "--stage", "resolved", "--svg", "snapshot.svg").expectStatus(1);
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.json")));
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.svg")));
        assertFalse(Files.exists(failed.work().resolve("generated")));
        return new Output(cli.input, run.work().resolve("generated"), folded.work().resolve("generated"), invalid.input);
    }
    private static void verifyGraph(Project project) {
        assertFalse(project.hasErrors());
        var all = nodes(project, Node.class);
        assertTrue(all.stream().flatMap(n -> n.diagnostics().stream()).noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR));
        Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>()); owned.addAll(all);
        for (Node node : all) for (Node reference : node.references()) assertTrue(owned.contains(reference));
        var largest = nodes(project, IntegerLiteral.class).stream()
                .filter(n -> n.bigValue().equals(new BigInteger("18446744073709551615"))).findFirst().orElseThrow();
        assertEquals("unsigned long", largest.cppType().orElseThrow());
        assertThrows(ArithmeticException.class, largest::value);
        assertEquals(Set.of("float", "double", "long double"), nodes(project, FloatingLiteral.class).stream()
                .map(FloatingLiteral::cppType).collect(java.util.stream.Collectors.toSet()));
        var strings = nodes(project, StringLiteral.class);
        assertEquals(2, strings.size());
        var narrow = strings.stream().filter(s -> !s.wide()).findFirst().orElseThrow();
        var wide = strings.stream().filter(StringLiteral::wide).findFirst().orElseThrow();
        assertEquals(List.of(BigInteger.valueOf(65), BigInteger.TEN), narrow.codeUnits());
        assertEquals(List.of(BigInteger.valueOf(937), BigInteger.valueOf(33)), wide.codeUnits());
        assertTrue(nodes(project, CharacterLiteral.class).stream().anyMatch(c -> c.wide() && c.value().intValueExact() == 937));
        assertFalse(nodes(project, BooleanLiteral.class).isEmpty());
        var calls = nodes(project, CallExpression.class).stream().filter(c -> c.callee() instanceof NameExpression n && n.name().equals("accept")).toList();
        assertEquals(2, calls.size());
        for (var call : calls) assertEquals(CallExpression.Resolution.RESOLVED, call.resolution());
        var implicit = assertInstanceOf(ConversionExpression.class, calls.get(0).arguments().getFirst());
        var explicit = assertInstanceOf(ConversionExpression.class, calls.get(1).arguments().getFirst());
        assertFalse(implicit.isExplicit());
        assertTrue(explicit.isExplicit());
        assertEquals("int", implicit.sourceType().orElseThrow());
        assertEquals("int", explicit.sourceType().orElseThrow());
        assertEquals("long", implicit.targetType().canonicalIdentity());
        assertEquals("long", explicit.targetType().canonicalIdentity());
        assertEquals(ConversionExpression.Kind.STATIC_CAST, explicit.kind());
        assertTrue(explicit.range().isPresent());
        assertTrue(explicit.range().orElseThrow().end().offset() - explicit.range().orElseThrow().start().offset() > 1);
        var castRange = explicit.range().orElseThrow();
        String captured = explicit.provenance().orElseThrow().unit().sources().get(castRange.file());
        assertNotNull(captured);
        assertTrue(captured.substring(castRange.start().offset(), castRange.end().offset()).contains("static_cast<long>"));
        assertEquals("long", explicit.semanticInfo().orElseThrow().resultType().orElseThrow());
        assertEquals(ValueCategory.RVALUE, explicit.semanticInfo().orElseThrow().category());
        var kinds = nodes(project, ConversionExpression.class).stream().map(ConversionExpression::kind).collect(java.util.stream.Collectors.toSet());
        assertTrue(kinds.containsAll(Set.of(ConversionExpression.Kind.ARRAY_TO_POINTER,
                ConversionExpression.Kind.NULL_POINTER, ConversionExpression.Kind.QUALIFICATION,
                ConversionExpression.Kind.REFERENCE_BINDING)), kinds.toString());
        for (var prefix : nodes(project, PrefixIncrementExpression.class)) {
            assertEquals("int", prefix.semanticInfo().orElseThrow().resultType().orElseThrow());
            assertEquals(ValueCategory.LVALUE, prefix.semanticInfo().orElseThrow().category());
        }
        for (var postfix : nodes(project, PostfixIncrementExpression.class))
            assertEquals(ValueCategory.RVALUE, postfix.semanticInfo().orElseThrow().category());
        for (var logical : nodes(project, LogicalAndExpression.class)) {
            assertEquals("bool", logical.semanticInfo().orElseThrow().resultType().orElseThrow());
            assertEquals(ValueCategory.RVALUE, logical.semanticInfo().orElseThrow().category());
        }
        Set<String> names = all.stream().map(n -> n.getClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(names.containsAll(Set.of("IndexExpression", "CompoundAssignmentExpression", "ConditionalExpression",
                "CommaExpression", "DotMemberPointerExpression", "ArrowMemberPointerExpression",
                "PrefixIncrementExpression", "PostfixIncrementExpression", "PrefixDecrementExpression",
                "PostfixDecrementExpression", "LogicalAndExpression", "LogicalOrExpression", "BitwiseNotExpression")), names.toString());
    }
    private static int count(String source, String text) { return (source.length() - source.replace(text, "").length()) / text.length(); }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        List<T> found = new ArrayList<>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) found.add(type.cast(node)); } });
        return found;
    }
}
