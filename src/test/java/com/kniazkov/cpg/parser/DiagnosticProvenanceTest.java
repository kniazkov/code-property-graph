/* Code Property Graph: diagnostic primary spans and immutable macro causes. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticProvenanceTest {
    private static Build.TranslationUnit unit(Path root, String... definitions) {
        return new Build.TranslationUnit(new Build.PhysicalSource(root), root.getParent(), List.of(),
                java.util.Arrays.stream(definitions).map(d -> new Build.MacroOperation(true, d)).toList(), null);
    }
    private static void fragment(SourceRange range, Path file, String text, String spelling) {
        int offset = text.indexOf(spelling);
        assertEquals(file.toString(), range.file());
        assertEquals(offset, range.start().offset());
        assertEquals(offset + spelling.length(), range.end().offset());
        assertEquals(ParseRecovery.position(text, offset), range.start());
        assertEquals(ParseRecovery.position(text, offset + spelling.length()), range.end());
    }
    @Test void missingMacroAssignmentRetainsGapAndEqualsOriginWithoutHidingLaterError() {
        Path root = Path.of("diagnostics/main.cpp").toAbsolutePath(), header = root.resolveSibling("assign.h");
        String source = "#include \"assign.h\"\nint main(){int total = 0; ASSIGN(total); int second = ; return total;}\n";
        String definition = "#define ASSIGN(x) x =\n";
        var config = unit(root, "MODE=1");
        var file = Parser.parseRecovering(config, source, Map.of(root.toString(), source, header.toString(), definition)).build();
        assertEquals(2, file.parseProblems().size());
        assertTrue(file.stoppedAt().isEmpty());
        var first = file.parseProblems().getFirst();
        assertEquals("expected-expression", first.code());
        assertEquals(source.indexOf(';', source.indexOf("ASSIGN(total)")), first.primaryRange().start().offset());
        assertEquals(first.primaryRange().start(), first.primaryRange().end());
        assertEquals(first.primaryRange(), first.range());
        assertEquals("", first.skippedText());
        assertEquals(List.of("macro-invocation", "macro-definition"), first.relatedRanges().stream().map(DiagnosticRelatedRange::role).toList());
        fragment(first.relatedRanges().getFirst().range(), root, source, "ASSIGN(total)");
        fragment(first.relatedRanges().getLast().range(), header, definition, "=");
        assertEquals(root.toString(), first.context().translationUnit());
        assertEquals(config.variantId(), first.context().variantId());
        assertEquals(List.of(root.toString()), first.context().includeChain().stream().map(Preprocessing.Include::file).toList());
        assertEquals(List.of("ASSIGN"), first.context().expansionChain().stream().map(Preprocessing.Expansion::name).toList());
        assertTrue(file.parseProblems().getLast().context().expansionChain().isEmpty());
        var body = ((FunctionDeclaration) file.items().getFirst()).body().orElseThrow();
        var assignment = assertInstanceOf(AssignmentExpression.class, ((ExpressionStatement) body.items().get(1)).expression());
        assertInstanceOf(ErrorExpression.class, assignment.value());
        assertEquals(4, body.items().size());
    }
    @Test void invalidPasteReportsExactlyThreePhysicalFragmentsAndRetainsFailedOperation() {
        Path root = Path.of("diagnostics/paste.cpp").toAbsolutePath(), header = root.resolveSibling("join.h");
        String source = "#include \"join.h\"\nint main(){int value = JOIN(+, *);}\n";
        String definition = "#define JOIN(a, b) a ## b\n";
        var config = unit(root);
        var sources = Map.of(root.toString(), source, header.toString(), definition);
        var file = Parser.parseRecovering(config, source, sources).build();
        assertEquals(1, file.parseProblems().size(), "The parser must not add a dependent EOF/initializer error");
        var problem = file.parseProblems().getFirst();
        assertEquals("invalid-token-paste", problem.code());
        fragment(problem.primaryRange(), root, source, "JOIN(+, *)");
        assertEquals(List.of("macro-argument-left", "macro-argument-right", "macro-paste-operator"),
                problem.relatedRanges().stream().map(DiagnosticRelatedRange::role).toList());
        fragment(problem.relatedRanges().get(0).range(), root, source, "+");
        fragment(problem.relatedRanges().get(1).range(), root, source, "*");
        fragment(problem.relatedRanges().get(2).range(), header, definition, "##");
        var expansion = problem.context().expansionChain().getFirst();
        assertEquals("JOIN", expansion.name());
        assertEquals("+*", expansion.operations().getFirst().spelling());
        assertEquals("paste", expansion.operations().getFirst().kind());
        assertEquals(List.of("+", "*"), expansion.operations().getFirst().fragments().stream().map(Preprocessing.Fragment::spelling).toList());
        assertTrue(expansion.startToken() >= 0);
        assertTrue(expansion.endToken() >= expansion.startToken());
        assertEquals(file.provenance().orElseThrow().unit().expansions().stream().filter(e -> e.id() == expansion.id()).findFirst().orElseThrow(), expansion);
        assertTrue(expansion.startToken() > 0, "The failed expansion follows the committed token prefix");
        var strict = assertThrows(ParseException.class, () -> Parser.parse(config, source, sources));
        assertTrue(strict.reason().contains("C++03"));
        assertEquals(problem.primaryRange(), strict.primaryRange().orElseThrow());
        assertEquals(problem.relatedRanges(), strict.relatedRanges());
        assertEquals(problem.reason(), strict.causeReason());
        assertEquals(problem.context().variantId(), strict.context().variantId());
        assertEquals("JOIN", strict.context().expansionChain().getFirst().name());
    }
    @Test void sharedHeaderCauseExcludesRootAndVariantButIncludesActualPasteOperands() {
        Path root = Path.of("diagnostics/a.cpp").toAbsolutePath(), other = root.resolveSibling("b.cpp"), header = root.resolveSibling("shared.h");
        String source = "#include \"shared.h\"\n";
        // Prescanning OP is not performed next to ##, so use an enclosing macro to expand it.
        String definition = "#define JOIN_RAW(a, b) a ## b\n#define JOIN(a, b) JOIN_RAW(a, b)\nint shared(){int x = JOIN(+, OP);}\n";
        var sources = Map.of(root.toString(), source, other.toString(), source, header.toString(), definition);
        var first = Parser.parseRecovering(unit(root, "OP=*"), source, sources).build().parseProblems().getFirst();
        var second = Parser.parseRecovering(unit(other, "OP=*"), source, sources).build().parseProblems().getFirst();
        var different = Parser.parseRecovering(unit(root, "OP=/"), source, sources).build().parseProblems().getFirst();
        assertEquals(first.code(), second.code());
        assertEquals(first.primaryRange(), second.primaryRange());
        assertEquals(first.reason(), second.reason());
        assertFalse(first.reason().contains(root.toString()));
        assertNotEquals(first.reason(), different.reason());
        assertNotEquals(first.context().variantId(), different.context().variantId());
        assertEquals(List.of(root.toString(), header.toString()), first.context().includeChain().stream().map(Preprocessing.Include::file).toList());
        assertEquals(List.of("JOIN", "JOIN_RAW"), first.context().expansionChain().stream().map(Preprocessing.Expansion::name).toList());
    }
    @Test void strictSuffixAndLogicalNotePreservePhysicalContext() {
        Path root = Path.of("diagnostics/logical.cpp").toAbsolutePath(), header = root.resolveSibling("failure.h");
        String source = "#include \"failure.h\"\n", definition = "#line 100 \"virtual.h\"\n#error broken\n";
        var config = unit(root, "MODE=1");
        var sources = Map.of(root.toString(), source, header.toString(), definition);
        var problem = Parser.parseRecovering(config, source, sources).build().parseProblems().getFirst();
        var strict = assertThrows(ParseException.class, () -> Parser.parse(config, source, sources));
        assertEquals("virtual.h", strict.file());
        assertEquals(100, strict.line());
        assertEquals(header.toString(), problem.primaryRange().file());
        assertTrue(problem.message().contains("logical location: virtual.h:100:2"));
        assertEquals(problem.context(), strict.context());
        assertEquals(problem.reason(), strict.causeReason());
        assertFalse(problem.reason().contains("include chain"));
    }
    @Test void conditionalCauseRetainsExpandedOperandsAtSameOperator() {
        Path root = Path.of("diagnostics/conditional.cpp").toAbsolutePath();
        String source = "#if 1 << COUNT\n#endif\n";
        var first = Parser.parseRecovering(unit(root, "COUNT=64"), source).build().parseProblems().getFirst();
        var second = Parser.parseRecovering(unit(root, "COUNT=65"), source).build().parseProblems().getFirst();
        assertEquals(first.primaryRange(), second.primaryRange());
        assertEquals(first.code(), second.code());
        assertNotEquals(first.reason(), second.reason());
    }
    @Test void reparseAndEditKeepValueProvenanceWithoutStaleNodes() {
        Path root = Path.of("diagnostics/reparse.cpp").toAbsolutePath();
        String source = "#define ASSIGN(x) x =\nint main(){int total = 0; ASSIGN(total);}\n";
        var first = Parser.parseRecovering(unit(root), source).build();
        var second = Parser.parseRecovering(unit(root), source).build();
        assertEquals(first.parseProblems(), second.parseProblems());
        assertEquals(first.parseProblems(), first.edit().build().parseProblems());
        assertThrows(UnsupportedOperationException.class, () -> first.parseProblems().getFirst().relatedRanges().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.parseProblems().getFirst().context().expansionChain().clear());
    }
    @Test void directiveCausesRetainExpandedValuesAndMacroAncestry() {
        Path root = Path.of("diagnostics/directive.cpp").toAbsolutePath();
        for (String source : List.of("#line COUNT\n", "#include COUNT\n")) {
            var first = Parser.parseRecovering(unit(root, "COUNT=40000"), source).build().parseProblems().getFirst();
            var second = Parser.parseRecovering(unit(root, "COUNT=50000"), source).build().parseProblems().getFirst();
            assertEquals(first.primaryRange(), second.primaryRange());
            assertEquals(first.code(), second.code());
            assertNotEquals(first.reason(), second.reason());
            assertTrue(first.reason().contains("40000"));
            assertEquals(List.of("COUNT"), first.context().expansionChain().stream().map(Preprocessing.Expansion::name).toList());
            assertEquals(List.of("COUNT"), second.context().expansionChain().stream().map(Preprocessing.Expansion::name).toList());
        }
    }
    @Test void emptyDirectiveOperandsKeepOnlyTheirExecutedExpansionAncestry() {
        Path root = Path.of("diagnostics/empty-directive.cpp").toAbsolutePath();
        String prefix = "#define BEFORE 1\n#define EMPTY\n#define WRAP EMPTY\nint before(){return BEFORE;}\n";
        for (String directive : List.of("#line", "#include", "#if")) {
            for (String operand : List.of("EMPTY", "WRAP")) {
                String source = prefix + directive + " " + operand + "\n";
                var file = Parser.parseRecovering(unit(root), source).build();
                assertEquals(1, file.parseProblems().size(), directive + " " + operand);
                var problem = file.parseProblems().getFirst();
                var chain = problem.context().expansionChain();
                var names = operand.equals("EMPTY") ? List.of("EMPTY") : List.of("WRAP", "EMPTY");
                assertEquals(names, chain.stream().map(Preprocessing.Expansion::name).toList(), directive);
                var owner = file.provenance().orElseThrow().unit();
                for (var expansion : chain) {
                    assertEquals(owner.expansions().stream().filter(e -> e.id() == expansion.id()).findFirst().orElseThrow(), expansion);
                    assertEquals(expansion.startToken(), expansion.endToken());
                    assertTrue(expansion.startToken() > 0);
                }
                var strict = assertThrows(ParseException.class, () -> Parser.parse(unit(root), source));
                assertEquals(names, strict.context().expansionChain().stream().map(Preprocessing.Expansion::name).toList());
            }
        }
    }
    @Test void redefinitionCauseRetainsBothReplacements() {
        Path root = Path.of("diagnostics/redefinition.cpp").toAbsolutePath();
        String source = "#define COUNT 3\n";
        var first = Parser.parseRecovering(unit(root, "COUNT=1"), source).build().parseProblems().getFirst();
        var second = Parser.parseRecovering(unit(root, "COUNT=2"), source).build().parseProblems().getFirst();
        assertEquals(first.primaryRange(), second.primaryRange());
        assertEquals("macro-redefinition", first.code());
        assertNotEquals(first.reason(), second.reason());
        assertTrue(first.reason().contains("previous replacement: 1"));
        assertTrue(first.reason().contains("new replacement: 3"));
        assertEquals(List.of("previous-definition", "new-definition"), first.relatedRanges().stream().map(DiagnosticRelatedRange::role).toList());
    }
    @Test void independentMacroErrorsSharingOneInvocationRemainSeparateGroups() {
        Path root = Path.of("diagnostics/two-errors.cpp").toAbsolutePath();
        String source = "#define BAD int x = ; int y = ;\nint main(){BAD}\n";
        var file = Parser.parseRecovering(unit(root), source).build();
        assertEquals(2, file.parseProblems().size());
        var first = file.parseProblems().getFirst();
        var second = file.parseProblems().getLast();
        assertEquals(first.primaryRange(), second.primaryRange());
        assertNotEquals(first.reason(), second.reason());
        assertEquals(2, Project.create().addFile(file.edit()).build().diagnosticGroups().size());
    }
    @Test void repeatedIncludeKeepsTheActualOccurrenceAfterTheStackUnwinds() {
        Path root = Path.of("diagnostics/repeated.cpp").toAbsolutePath(), header = root.resolveSibling("repeated.h");
        String source = "#include \"repeated.h\"\n#include \"repeated.h\"\n";
        String definition = "#ifdef SECOND\nint main(){int value = ;}\n#endif\n#define SECOND\n";
        var file = Parser.parseRecovering(unit(root), source, Map.of(root.toString(), source, header.toString(), definition)).build();
        var context = file.parseProblems().getFirst().context();
        assertEquals(List.of(0L, 2L), context.includeChain().stream().map(Preprocessing.Include::id).toList());
        assertEquals(List.of(root.toString(), header.toString()), context.includeChain().stream().map(Preprocessing.Include::file).toList());
        assertTrue(context.expansionChain().isEmpty());
    }
    @Test void javaAssignmentRecoveryUsesTheSameSafeBoundary() {
        String prefix = "class A { public static void main(String[] args){int x = 0; ";
        var recovered = Parser.parseRecovering("diagnostics/A.java", prefix + "x = ; int y = ;}}", Language.JAVA).build();
        assertEquals(2, recovered.parseProblems().size());
        assertTrue(recovered.parseProblems().stream().allMatch(ParseProblem::recovered));
        assertTrue(recovered.stoppedAt().isEmpty());
        var stopped = Parser.parseRecovering("diagnostics/A.java", prefix + "if(x = ; int y = ;}}", Language.JAVA).build();
        assertEquals(1, stopped.parseProblems().size());
        assertEquals("expected-expression", stopped.parseProblems().getFirst().code());
        assertFalse(stopped.parseProblems().getFirst().recovered());
        assertTrue(stopped.stoppedAt().isPresent());
    }
    @Test void nestedAssignmentNeverRecoversAcrossAnEnclosingDelimiter() {
        for (String statement : List.of("int x = (y = ;", "if (x = ;", "f(x = ;", "(x = ;", "x = (y = ;")) {
            String source = "int main(){" + statement + " int after = ;}";
            var file = Parser.parseRecovering("diagnostics/nested.cpp", source, Language.CPP).build();
            assertEquals(1, file.parseProblems().size(), statement);
            var problem = file.parseProblems().getFirst();
            assertEquals("expected-expression", problem.code(), statement);
            assertEquals(source.indexOf(';'), problem.primaryRange().start().offset(), statement);
            assertEquals(problem.primaryRange().start(), problem.primaryRange().end(), statement);
            assertFalse(problem.recovered(), statement);
            assertTrue(file.stoppedAt().isPresent(), statement);
        }
    }
    @Test void assignmentRecoveryDoesNotCrossAnUnbalancedParenthesis() {
        String source = "int main(){int x = 0; x = (1 + ; int after = ;}";
        var file = Parser.parseRecovering("diagnostics/unsafe.cpp", source, Language.CPP).build();
        assertEquals(1, file.parseProblems().size());
        assertFalse(file.parseProblems().getFirst().recovered());
        assertTrue(file.stoppedAt().isPresent());
    }
}
