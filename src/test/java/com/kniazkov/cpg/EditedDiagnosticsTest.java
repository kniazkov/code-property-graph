/* Diagnostic state belongs to the edited immutable syntax snapshot. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditedDiagnosticsTest {
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }});
        return result;
    }
    private static Project parse(String text) {
        return Project.create().addFile(Parser.parseRecovering("edited.cpp", text, Language.CPP)).build();
    }
    @Test void repairedPlaceholderRetiresOnlyItsOwnFailureAndPreservesWarning() {
        Project parsed = parse("int x = ; int other = 1;");
        var warning = new Diagnostic("independent-note", Diagnostic.Severity.WARNING, "Keep this warning");
        Project original = parsed.withDiagnostics(warning.code(), Map.of(parsed.files().getFirst(), warning));
        var editor = new ProjectEditor(original);
        editor.replace(nodes(original, ErrorExpression.class).getFirst(), IntegerLiteral.create().setValue(0).build());
        Project repaired = editor.build();
        assertTrue(original.hasErrors());
        assertEquals(1, original.parseProblems().size());
        assertFalse(repaired.hasErrors());
        assertTrue(repaired.parseProblems().isEmpty());
        assertTrue(repaired.semanticDiagnostics().contains(warning));
        assertTrue(repaired.files().getFirst().source(Language.CPP).contains("0"));
        assertFalse(nodes(original, ErrorExpression.class).isEmpty());
    }
    @Test void repairingOnePlaceholderKeepsTheOtherErrorAndBlocksGeneration() {
        Project original = parse("int x = ; int other = ;");
        var editor = new ProjectEditor(original);
        editor.replace(nodes(original, ErrorExpression.class).getFirst(), IntegerLiteral.create().setValue(0).build());
        Project repaired = editor.build();
        assertTrue(repaired.hasErrors());
        assertEquals(1, repaired.parseProblems().size());
        assertThrows(IllegalArgumentException.class, () -> repaired.files().getFirst().source(Language.CPP));
        assertEquals(2, original.parseProblems().size());
    }
    @Test void terminalParserFailureIsNotClearedByRepairingAnEarlierExpression() {
        Project original = parse("int x = ; int broken( {");
        assertTrue(original.files().getFirst().stoppedAt().isPresent());
        var editor = new ProjectEditor(original);
        editor.replace(nodes(original, ErrorExpression.class).getFirst(), IntegerLiteral.create().setValue(0).build());
        Project repaired = editor.build();
        assertTrue(repaired.hasErrors());
        assertTrue(repaired.files().getFirst().stoppedAt().isPresent());
        assertEquals(1, repaired.parseProblems().size());
        assertFalse(repaired.parseProblems().getFirst().recovered());
    }
    @Test void directFileBuilderRetiresOnlyProblemsWhosePlaceholdersWereRemoved() {
        SourceFile original = parse("int x = ; int y = ;").files().getFirst();
        SourceFile repaired = original.edit().setItems(List.of(original.items().getLast().edit())).build();
        assertEquals(1, repaired.parseProblems().size());
        assertTrue(repaired.hasErrors());
        assertEquals(2, original.parseProblems().size());
    }
    @Test void sourceEditInvalidatesDerivedFlowWarningButKeepsIndependentAnnotation() {
        Project analyzed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(
                NameResolver.resolve(parse("int f() { int x = 7; return 0; }")));
        assertTrue(analyzed.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("unread-write")));
        var independent = new Diagnostic("independent-note", Diagnostic.Severity.WARNING, "Keep this warning");
        Project original = analyzed.withDiagnostics(independent.code(), Map.of(analyzed.files().getFirst(), independent));
        var editor = new ProjectEditor(original);
        ReturnStatement returned = nodes(original, ReturnStatement.class).getFirst();
        editor.replace(returned, ReturnStatement.create().setValue(NameExpression.create().setName("x")).build());
        Project edited = editor.build();
        assertTrue(edited.flows().isEmpty());
        assertTrue(edited.semanticDiagnostics().stream().noneMatch(d -> d.code().equals("unread-write")));
        assertEquals(List.of(independent), edited.semanticDiagnostics());
        Project reanalyzed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(NameResolver.resolve(edited.edit().build()));
        assertTrue(reanalyzed.semanticDiagnostics().stream().noneMatch(d -> d.code().equals("unread-write")));
        assertTrue(original.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("unread-write")));
    }
    @Test void removingUnsupportedHierarchyClearsStaleConstructorFlowWarning() {
        Project original = com.kniazkov.cpg.analysis.ControlFlow.analyze(
                NameResolver.resolve(parse("struct V {}; struct B : virtual V { B() {} };")));
        assertTrue(original.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("unsupported-constructor-flow")));
        var editor = new ProjectEditor(original);
        BaseSpecifier base = nodes(original, BaseSpecifier.class).getFirst();
        editor.replace(base, base.edit().setVirtual(false).build());
        Project edited = editor.build();
        assertTrue(edited.semanticDiagnostics().stream().noneMatch(d -> d.code().equals("unsupported-constructor-flow")));
        Project reanalyzed = com.kniazkov.cpg.analysis.ControlFlow.analyze(NameResolver.resolve(edited.edit().build()));
        assertTrue(reanalyzed.semanticDiagnostics().stream().noneMatch(d -> d.code().equals("unsupported-constructor-flow")));
        assertTrue(original.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("unsupported-constructor-flow")));
    }
    @Test void unchangedDerivedResolverWarningIsRecomputedOnceWithItsLocation() {
        Project original = NameResolver.resolve(parse("int f() { throw; } int x = 1;"));
        assertEquals(1, original.semanticDiagnostics().stream().filter(d -> d.code().equals("rethrow-without-lexical-handler")).count());
        var editor = new ProjectEditor(original);
        IntegerLiteral value = nodes(original, IntegerLiteral.class).getFirst();
        editor.replace(value, IntegerLiteral.create().setValue(2).build());
        Project edited = editor.build();
        var warnings = edited.semanticDiagnostics().stream().filter(d -> d.code().equals("rethrow-without-lexical-handler")).toList();
        assertEquals(1, warnings.size());
        assertNotNull(warnings.getFirst().primaryRange());
        editor = new ProjectEditor(edited);
        value = nodes(edited, IntegerLiteral.class).getFirst();
        editor.replace(value, IntegerLiteral.create().setValue(3).build());
        assertEquals(1, editor.build().semanticDiagnostics().stream().filter(d -> d.code().equals("rethrow-without-lexical-handler")).count());
    }
    @Test void rawAndEditedDuplicateMemberSignaturesHaveEquivalentLocatedErrors() {
        Project raw = NameResolver.resolve(parse("struct Device { void restart(int); void restart(int); };"));
        Project original = NameResolver.resolve(parse("struct Device { void reset(int); void restart(int); };"));
        assertFalse(original.hasErrors());
        var editor = new ProjectEditor(original);
        MethodDeclaration first = nodes(original, MethodDeclaration.class).getFirst();
        editor.replace(first, first.edit().setName("restart").build());
        Project invalid = editor.build();
        assertTrue(raw.hasErrors());
        assertTrue(invalid.hasErrors());
        assertEquals(2, nodes(invalid, MethodDeclaration.class).size());
        var rawErrors = raw.semanticDiagnostics().stream().filter(d -> d.code().equals("conflicting-declaration")).toList();
        var editedErrors = invalid.semanticDiagnostics().stream().filter(d -> d.code().equals("conflicting-declaration")).toList();
        assertEquals(2, rawErrors.size());
        assertEquals(rawErrors.stream().map(Diagnostic::message).toList(), editedErrors.stream().map(Diagnostic::message).toList());
        assertTrue(editedErrors.stream().allMatch(d -> d.primaryRange() != null && !d.relatedRanges().isEmpty()));
        assertEquals(first.range().orElseThrow(), editedErrors.getFirst().primaryRange());
        assertThrows(IllegalArgumentException.class, () -> invalid.files().getFirst().source(Language.CPP));
        editor = new ProjectEditor(invalid);
        MethodDeclaration duplicate = nodes(invalid, MethodDeclaration.class).getFirst();
        editor.replace(duplicate, duplicate.edit().setName("reset").build());
        Project repaired = editor.build();
        assertFalse(repaired.hasErrors());
        assertTrue(repaired.semanticDiagnostics().stream().noneMatch(d -> d.code().equals("conflicting-declaration")));
        assertDoesNotThrow(() -> repaired.files().getFirst().source(Language.CPP));
        assertTrue(invalid.hasErrors());
        assertFalse(original.hasErrors());
    }
}
