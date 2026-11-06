package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransformationProvenanceModelTest {
    private Project project() {
        return Project.create().addFile(SourceFile.create().setPath("test.cpp").setLanguage(Language.CPP)
                .addItem(VariableDeclaration.create().setName("x").setType(NamedType.create().setName("int")).setInitializer(IntegerLiteral.create().setValue(2)))).build();
    }
    private Node value(Project project) {
        return ((VariableDeclaration) project.files().getFirst().items().getFirst()).initializer().orElseThrow();
    }
    @Test void copiesKeepHistoricalIdentityAndStageWithoutOwnershipPollution() {
        Project original = project();
        Node input = value(original);
        var replacement = IntegerLiteral.create().setValue(5);
        replacement.addTransformationProvenance("fold", "value", input);
        var editor = new ProjectEditor(original).withoutRevalidation().setPassId("fold")
                .setSnapshotStage(SnapshotStage.FOLDED);
        editor.replace(input, replacement.build());
        Project result = editor.build();
        assertTrue(input.transformationProvenance().isEmpty());
        assertEquals(SnapshotStage.PARSED, original.snapshotStage());
        assertEquals(SnapshotStage.FOLDED, result.snapshotStage());
        Node output = value(result);
        assertEquals(2, output.transformationProvenance().size());
        assertSame(input, output.transformationProvenance().getFirst().source());
        assertTrue(output.references().isEmpty());
        assertTrue(output.children().isEmpty());
        assertEquals(2, result.transformationResults(input).size());
        assertThrows(UnsupportedOperationException.class, () -> output.transformationProvenance().clear());
        Project copied = result.edit().build();
        assertEquals(SnapshotStage.FOLDED, copied.snapshotStage());
        assertSame(input, value(copied).transformationProvenance().getFirst().source());
        assertTrue(copied.transformationProvenance().isEmpty());
    }
    @Test void repeatedTypeNormalizationPreservesHistoryWithoutNoOpTransitions() {
        Project original = project();
        Project first = com.kniazkov.cpg.analysis.TypeNormalizer.green(original);
        TypeNode initialType = ((VariableDeclaration) original.files().getFirst().items().getFirst()).type().orElseThrow();
        TypeNode firstType = ((VariableDeclaration) first.files().getFirst().items().getFirst()).type().orElseThrow();
        assertEquals(1, firstType.transformationProvenance().size());
        assertSame(initialType, firstType.transformationProvenance().getFirst().source());
        Project repeated = com.kniazkov.cpg.analysis.TypeNormalizer.green(first);
        TypeNode repeatedType = ((VariableDeclaration) repeated.files().getFirst().items().getFirst()).type().orElseThrow();
        assertEquals(firstType.transformationProvenance(), repeatedType.transformationProvenance());
        assertSame(initialType, repeatedType.transformationProvenance().getFirst().source());
    }
    @Test void sharedReplacementHistoryUsesSourceOrderAcrossIndependentBuilds() {
        for (int iteration = 0; iteration < 40; iteration++) {
            var wrapper = ConversionExpression.create().setKind(ConversionExpression.Kind.STATIC_CAST)
                    .setTargetType(NamedType.create().setName("int"))
                    .setOperand(IntegerLiteral.create().setValue(7));
            Project original = Project.create().addFile(SourceFile.create().setPath("test.cpp").setLanguage(Language.CPP)
                    .addItem(VariableDeclaration.create().setName("x").setType(NamedType.create().setName("int"))
                            .setInitializer(wrapper))).build();
            var before = (ConversionExpression) value(original);
            var literal = before.operand();
            var editor = new ProjectEditor(original).withoutRevalidation().setPassId("test");
            // Both replaced occurrences contribute to one output; registration order is deliberately reversed.
            editor.replace(literal, literal);
            editor.replace(before, literal);
            Node output = value(editor.build());
            assertEquals(2, output.transformationProvenance().size());
            assertSame(before, output.transformationProvenance().get(0).source());
            assertSame(literal, output.transformationProvenance().get(1).source());
        }
    }
    @Test void reverseQueryTraversesImmediatePredecessors() {
        Project original = project();
        Node first = value(original);
        var e = new ProjectEditor(original).withoutRevalidation().setPassId("first");
        e.replace(first, IntegerLiteral.create().setValue(3).build());
        Project middle = e.build(); Node second = value(middle);
        var next = new ProjectEditor(middle).withoutRevalidation().setPassId("second");
        next.replace(second, IntegerLiteral.create().setValue(4).build());
        Project last = next.build();
        assertSame(second, value(last).transformationProvenance().getFirst().source());
        assertEquals(1, last.transformationResults(first).size());
        assertSame(value(last), last.transformationResults(first).getFirst().result());
        assertTrue(original.transformationResults(first).isEmpty());
    }
}
