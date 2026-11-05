/* C++03 overload bindings preserve written syntax and nominal types. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverloadExportTest {
    private static Project parse(String text) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("overloads.cpp", text, Language.CPP)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (kind.isInstance(node)) result.add(kind.cast(node));
        }});
        return result;
    }
    @Test void overloadedAdditionExportsImplementationWithoutRewritingSyntax() {
        var original = parse("struct Money { int value; }; Money operator+(Money price, Money tax) { return price; } Money total(Money price, Money tax) { return price + tax; }");
        var copy = original.edit().build();
        for (var project : List.of(original, copy, parse(Source.render(original.files().getFirst(), Language.CPP)))) {
            var sum = nodes(project, AddExpression.class).getFirst();
            assertEquals("operator+", sum.operatorTarget().orElseThrow().name());
            assertEquals(CallExpression.Resolution.RESOLVED, sum.operatorResolution());
            assertFalse(sum.languages().contains(Language.JAVA));
            assertThrows(IllegalArgumentException.class, () -> Source.render(sum, Language.JAVA));
            assertFalse(sum.children().contains(sum.operatorTarget().orElseThrow()));
            assertTrue(sum.references().contains(sum.operatorTarget().orElseThrow()));
            assertTrue(nodes(project, CallableDeclaration.class).contains(sum.operatorTarget().orElseThrow()));
            String source = Source.render(project.files().getFirst(), Language.CPP);
            assertTrue(source.contains("price + tax"), source);
            assertFalse(source.contains("operator+(price, tax)"), source);
            String json = ProjectJson.encode(project).toText("");
            for (String key : List.of("operatorResolution", "operatorCandidateIds", "operatorTargetId"))
                assertTrue(json.contains("\"" + key + "\""), json);
            assertTrue(DotVisualizer.toDot(project, false).contains("operator implementation"));
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        }
        assertEquals(ProjectJson.encode(original), ProjectJson.encode(copy));
        assertNotSame(nodes(original, AddExpression.class).getFirst().operatorTarget().orElseThrow(),
                nodes(copy, AddExpression.class).getFirst().operatorTarget().orElseThrow());
    }
    @Test void ambiguousOperatorCandidatesAreReferencesNotInventedTargets() {
        var original = parse("struct Money {}; Money operator+(Money a, Money b); Money operator+(Money a, int b); Money total(Money a, Money b) { return a + b; }");
        var editor = new ProjectEditor(original);
        var candidates = nodes(original, CallableDeclaration.class).stream().filter(c -> c.name().equals("operator+")).toList();
        editor.resolveOperator(nodes(original, AddExpression.class).getFirst(), candidates, CallExpression.Resolution.AMBIGUOUS);
        var ambiguous = editor.build();
        for (var project : List.of(ambiguous, ambiguous.edit().build())) {
            var sum = nodes(project, AddExpression.class).getFirst();
            assertTrue(sum.operatorTarget().isEmpty());
            assertEquals(2, sum.operatorCandidates().size());
            assertTrue(sum.references().containsAll(sum.operatorCandidates()));
            assertTrue(sum.children().stream().noneMatch(sum.operatorCandidates()::contains));
            String json = ProjectJson.encode(project).toText("");
            assertTrue(json.contains("\"operatorResolution\": \"ambiguous\""), json);
            assertFalse(json.contains("\"operatorTargetId\""), json);
            assertTrue(DotVisualizer.toDot(project, false).contains("operator candidate (ambiguous)"));
        }
        assertEquals(ProjectJson.encode(ambiguous), ProjectJson.encode(ambiguous.edit().build()));
    }
    @Test void conversionFunctionsHaveOwnedTargetAndNoWrittenReturnType() {
        var original = parse("struct Money { operator int() const { return 1; } }; int main() { return 0; }");
        String source = Source.render(original.files().getFirst(), Language.CPP);
        assertTrue(source.contains("operator int() const"), source);
        assertFalse(source.contains("int operator int"), source);
        for (var project : List.of(original, original.edit().build(), parse(source))) {
            var conversion = nodes(project, CallableDeclaration.class).stream()
                    .filter(c -> c.kind() == CallableDeclaration.Kind.CONVERSION).findFirst().orElseThrow();
            assertTrue(conversion.returnType().isEmpty());
            assertEquals("int", conversion.conversionType().orElseThrow().canonicalIdentity());
            assertTrue(conversion.children().contains(conversion.conversionType().orElseThrow()));
            assertTrue(ProjectJson.encode(project).toText("").contains("\"conversionType\""));
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
        }
        assertEquals(ProjectJson.encode(original), ProjectJson.encode(original.edit().build()));
    }
    @Test void qualifiedConversionDefinitionsKeepTheirOwnerAndWrittenTarget() {
        var original = parse("struct Money { operator int() const; }; Money::operator int() const { return 1; }");
        String source = Source.render(original.files().getFirst(), Language.CPP);
        assertTrue(source.contains("Money::operator int() const"), source);
        var reparsed = parse(source);
        var definitions = nodes(reparsed, CallableDeclaration.class).stream()
                .filter(c -> c.kind() == CallableDeclaration.Kind.CONVERSION && c.body().isPresent()).toList();
        assertEquals(1, definitions.size());
        assertEquals("Money::operator int", definitions.getFirst().name());
        assertEquals("int", definitions.getFirst().conversionType().orElseThrow().canonicalIdentity());
        assertTrue(definitions.getFirst().returnType().isEmpty());
        assertEquals(ProjectJson.encode(original), ProjectJson.encode(original.edit().build()));
    }
    @Test void equalWidthNominalTypesAndLongSuffixSurviveRoundTrip() {
        var profile = PlatformProfile.builder().name("equal-width").sizeBits("long", 32).alignmentBits("long", 32).build();
        Path file = Path.of("nominal.cpp").toAbsolutePath();
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(file), file.getParent(), List.of(), List.of(), "c++03").withPlatform(profile);
        String text = "int f(int x) { return 1; } int f(long x) { return 2; } int main() { return f(1) + f(1L); }";
        var original = NameResolver.resolve(Project.create().addFile(Parser.parse(unit, text)).build());
        String source = Source.render(original.files().getFirst(), Language.CPP);
        assertTrue(source.contains("f(1L)"), source);
        assertTrue(source.contains("long x"), source);
        for (var project : List.of(original, original.edit().build(),
                NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source)).build()))) {
            var calls = nodes(project, CallExpression.class);
            assertEquals(2, calls.size());
            assertEquals("int", calls.get(0).target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
            assertEquals("long", calls.get(1).target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
            assertNotSame(calls.get(0).target().orElseThrow(), calls.get(1).target().orElseThrow());
            String json = ProjectJson.encode(project).toText("");
            assertTrue(json.contains("1L"), json);
        }
    }
}
