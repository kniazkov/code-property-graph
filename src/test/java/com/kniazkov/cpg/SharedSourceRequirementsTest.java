/* Code Property Graph: shared physical-source requirement reconciliation. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SharedSourceRequirementsTest {
    @TempDir Path directory;
    private static final Cpg.Options RESOLVED = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);

    private Build.TranslationUnit unit(String file, String macro) {
        return new Build.TranslationUnit(new Build.PhysicalSource(directory.resolve(file)), directory,
                List.of(), macro == null ? List.of() : List.of(new Build.MacroOperation(true, macro)), "c++03");
    }
    private Project shared(String header) throws Exception {
        Files.writeString(directory.resolve("common.h"), header);
        Files.writeString(directory.resolve("main.cpp"), "#include \"common.h\"\nint main() { return retry_limit(); }\n");
        return Cpg.read(new Build(List.of(unit("main.cpp", "SERVER"), unit("main.cpp", "CLIENT"))), RESOLVED);
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }});
        return result;
    }
    private static Project replaceOnes(Project project, int... files) {
        var editor = new ProjectEditor(project);
        for (int index : files)
            for (var literal : nodes(project.files().get(index), IntegerLiteral.class))
                if (literal.value() == 1) editor.replace(literal, IntegerLiteral.create().setValue(2).build());
        return editor.build();
    }
    private String conflict(Project project) {
        return assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP)).getMessage();
    }
    private void identifiesBothVariants(Project project, String message) {
        assertTrue(message.contains("conflicting physical requirements"), message);
        assertTrue(message.contains(directory.resolve("common.h").toString()), message);
        for (var file : project.files()) assertTrue(message.contains(file.variantId().orElseThrow()), message);
        assertTrue(message.contains("-DSERVER"), message);
        assertTrue(message.contains("-DCLIENT"), message);
    }

    @Test void untouchedVariantConstrainsSharedLiteralWithPhysicalCoordinates() throws Exception {
        String header = "// shared\r\nint retry_limit() { return 1; }\r\n";
        var project = shared(header);
        String message = conflict(replaceOnes(project, 0));
        identifiesBothVariants(project, message);
        assertTrue(message.contains("common.h:2:28-2:29"), message);
        int offset = header.indexOf('1');
        assertTrue(message.contains("[offsets " + offset + ".." + (offset + 1) + ")"), message);
        assertTrue(message.contains("requires \"2\""), message);
        assertTrue(message.contains("requires \"1\""), message);
        assertEquals(header, Files.readString(directory.resolve("common.h")));
    }

    @Test void identicalEditsAreEmittedOnceAndUneditedFilesRemainExact() throws Exception {
        String header = "int retry_limit() { return 1; }\n";
        var project = shared(header);
        var generated = Cpg.sources(replaceOnes(project, 0, 1), Language.CPP);
        assertEquals(2, generated.size());
        assertEquals(header.replace("1", "2"), generated.get("common.h"));
        assertEquals(Files.readString(directory.resolve("main.cpp")), generated.get("main.cpp"));
    }

    @Test void inactiveArmDoesNotDemandItsOldTextAndInvalidInactiveCodeSurvives() throws Exception {
        String header = """
            int retry_limit() {
            #ifdef SERVER
                return 1;
            #else
                return 3;
            #endif
            #ifdef NEVER
            deliberately unfinished +
            #endif
            }
            """;
        var project = shared(header);
        assertEquals(header.replace("return 1;", "return 2;"),
                Cpg.sources(replaceOnes(project, 0), Language.CPP).get("common.h"));
    }

    @Test void sourceOnlyAlternativesAlsoReconcileWithoutPreprocessorDirectives() throws Exception {
        String source = "int main() { return 1; }\n";
        Files.writeString(directory.resolve("main.cpp"), source);
        var project = Cpg.read(new Build(List.of(unit("main.cpp", "SERVER"), unit("main.cpp", "CLIENT"))), RESOLVED);
        assertTrue(conflict(replaceOnes(project, 0)).contains("requires \"1\""));
        assertEquals(Map.of("main.cpp", source.replace("1", "2")), Cpg.sources(replaceOnes(project, 0, 1), Language.CPP));
        assertThrows(IllegalArgumentException.class, () -> project.source(Language.CPP));
    }

    @Test void containingEditCannotSilentlyOverwriteAnotherVariantsNestedEdit() throws Exception {
        var project = shared("int retry_limit() { return 1 + 3; }\n");
        var editor = new ProjectEditor(project);
        var expression = nodes(project.files().get(0), AddExpression.class).getFirst();
        editor.replace(expression, MultiplyExpression.create()
                .setLeft(IntegerLiteral.create().setValue(1)).setRight(IntegerLiteral.create().setValue(3)).build());
        var nested = nodes(project.files().get(1), IntegerLiteral.class).getFirst();
        editor.replace(nested, IntegerLiteral.create().setValue(2).build());
        String message = conflict(editor.build());
        identifiesBothVariants(project, message);
        assertTrue(message.contains("requires \"(1 * 3)\""), message);
        assertTrue(message.contains("requires \"2 + 3\""), message);
    }

    @Test void disjointChangesStillCheckOtherVariantsUnchangedRequirements() throws Exception {
        var project = shared("int retry_limit() { return 1 + 3; }\n");
        var editor = new ProjectEditor(project);
        editor.replace(nodes(project.files().get(0), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        editor.replace(nodes(project.files().get(1), IntegerLiteral.class).getLast(), IntegerLiteral.create().setValue(4).build());
        identifiesBothVariants(project, conflict(editor.build()));
    }

    @Test void repeatedIncludeOccurrencesConstrainEachOtherWithinOneVariant() throws Exception {
        Files.writeString(directory.resolve("common.h"), "int NAME() { return 1; }\n");
        Files.writeString(directory.resolve("main.cpp"), """
            #define NAME first
            #include "common.h"
            #undef NAME
            #define NAME second
            #include "common.h"
            int main() { return first() + second(); }
            """);
        var project = Cpg.read(new Build(List.of(unit("main.cpp", null))), RESOLVED);
        var literals = nodes(project, IntegerLiteral.class);
        assertEquals(2, literals.size());
        var editor = new ProjectEditor(project);
        editor.replace(literals.getFirst(), IntegerLiteral.create().setValue(2).build());
        String message = conflict(editor.build());
        assertTrue(message.contains("conflicting physical requirements"), message);
        for (var literal : literals)
            assertTrue(message.contains("include " + literal.provenance().orElseThrow().tokens().getFirst().includeId()), message);
        editor.replace(literals.getLast(), IntegerLiteral.create().setValue(2).build());
        assertEquals("int NAME() { return 2; }\n", Cpg.sources(editor.build(), Language.CPP).get("common.h"));
    }

    @Test void wholeExpressionReplacementCannotConsumeConditionalDirectives() throws Exception {
        var project = shared("""
            int retry_limit() { return 1 +
            #ifdef SERVER
            3
            #else
            4
            #endif
            ; }
            """);
        var editor = new ProjectEditor(project);
        editor.replace(nodes(project.files().getFirst(), AddExpression.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        String message = conflict(editor.build());
        assertTrue(message.contains("crosses preprocessing directive #ifdef"), message);
        assertTrue(message.contains("common.h"), message);
    }

    @Test void sameMacroEditInBothVariantsPreservesDefinitionAndOtherCalls() throws Exception {
        var project = shared("#define SQUARE(x) ((x)*(x))\nint retry_limit() { return SQUARE(1) + SQUARE(3); }\n");
        var editor = new ProjectEditor(project);
        for (var file : project.files())
            editor.replace(nodes(file, IntegerLiteral.class).get(1), IntegerLiteral.create().setValue(2).build());
        assertEquals("#define SQUARE(x) ((x)*(x))\nint retry_limit() { return (1 * 2) + SQUARE(3); }\n",
                Cpg.sources(editor.build(), Language.CPP).get("common.h"));
    }

    @Test void opposingEditsReportEveryObservingVariantIncludingUnchangedOnes() throws Exception {
        shared("int retry_limit() { return 1; }\n");
        var project = Cpg.read(new Build(List.of(unit("main.cpp", "SERVER"), unit("main.cpp", "CLIENT"),
                unit("main.cpp", "AUDIT"))), RESOLVED);
        var editor = new ProjectEditor(project);
        editor.replace(nodes(project.files().get(0), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        editor.replace(nodes(project.files().get(1), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(3).build());
        String message = conflict(editor.build());
        identifiesBothVariants(project, message);
        assertTrue(message.contains("-DAUDIT"), message);
        for (int value : List.of(1, 2, 3)) assertTrue(message.contains("requires \"" + value + "\""), message);
    }

    @Test void identicalOverlappingStructuralEditsAreEmittedOnce() throws Exception {
        String header = "int retry_limit() { return -1; }\n";
        var project = shared(header);
        var editor = new ProjectEditor(project);
        for (var file : project.files()) {
            var expression = nodes(file, NegateExpression.class).getFirst();
            editor.replace(expression, UnaryPlusExpression.create().setOperand(IntegerLiteral.create().setValue(2)).build());
        }
        assertEquals("int retry_limit() { return (+(2)); }\n", Cpg.sources(editor.build(), Language.CPP).get("common.h"));
    }

    @Test void literalEditCanBeCheckedAgainstAnotherVariantsEmptyMacroInvocation() throws Exception {
        Files.writeString(directory.resolve("common.h"), "int retry_limit() { return VALUE; }\n");
        Files.writeString(directory.resolve("main.cpp"), "#include \"common.h\"\nint main() { return retry_limit(); }\n");
        var project = Cpg.read(new Build(List.of(unit("main.cpp", "VALUE=1"), unit("main.cpp", "VALUE="))), RESOLVED);
        String message = conflict(replaceOnes(project, 0));
        assertTrue(message.contains("conflicting physical requirements"), message);
        assertTrue(message.contains("requires \"2\""), message);
        assertTrue(message.contains("requires \"VALUE\""), message);
        for (var file : project.files()) assertTrue(message.contains(file.variantId().orElseThrow()), message);
    }

    @Test void repeatedMacroInvocationsUseCallSiteOccurrenceNotDefinitionOccurrence() throws Exception {
        Files.writeString(directory.resolve("common.h"), "int NAME() { return VALUE; }\n");
        Files.writeString(directory.resolve("main.cpp"), """
            #define VALUE 1
            #define NAME first
            #include "common.h"
            #undef NAME
            #define NAME second
            #include "common.h"
            int main() { return first() + second(); }
            """);
        var project = Cpg.read(new Build(List.of(unit("main.cpp", null))), RESOLVED);
        var literals = nodes(project, IntegerLiteral.class);
        var editor = new ProjectEditor(project);
        editor.replace(literals.getFirst(), IntegerLiteral.create().setValue(2).build());
        String message = conflict(editor.build());
        assertTrue(message.contains("conflicting physical requirements"), message);
        assertTrue(message.contains("requires \"VALUE\""), message);
        editor.replace(literals.getLast(), IntegerLiteral.create().setValue(2).build());
        assertEquals("int NAME() { return 2; }\n", Cpg.sources(editor.build(), Language.CPP).get("common.h"));
    }

    @Test void splitMacroOptionsKeepTheirOperandsInConflictLabels() throws Exception {
        shared("int retry_limit() { return 1; }\n");
        var units = new ArrayList<Build.TranslationUnit>();
        for (String name : List.of("SERVER", "CLIENT")) units.add(new Build.TranslationUnit(
                new Build.PhysicalSource(directory.resolve("main.cpp")), directory, List.of(),
                List.of(new Build.MacroOperation(true, name, null, "-D", List.of("-D", name))), "c++03"));
        var project = Cpg.read(new Build(units), RESOLVED);
        String message = conflict(replaceOnes(project, 0));
        assertTrue(message.contains("-D SERVER"), message);
        assertTrue(message.contains("-D CLIENT"), message);
    }

    @Test void emptyExpansionInEitherRepeatedHeaderOccurrenceGivesDetailedConflict() throws Exception {
        Files.writeString(directory.resolve("common.h"), "int NAME() { return VALUE; }\n");
        for (boolean emptyFirst : List.of(false, true)) {
            Files.writeString(directory.resolve("main.cpp"), """
                #define VALUE %s
                #define NAME first
                #include "common.h"
                #undef NAME
                #undef VALUE
                #define NAME second
                #define VALUE %s
                #include "common.h"
                int main() { return first(); }
                """.formatted(emptyFirst ? "" : "1", emptyFirst ? "1" : ""));
            var project = Cpg.read(new Build(List.of(unit("main.cpp", null))), RESOLVED);
            String message = conflict(replaceOnes(project, 0));
            assertTrue(message.contains("conflicting physical requirements"), message);
            assertTrue(message.contains("requires \"2\""), message);
            assertTrue(message.contains("requires \"VALUE\""), message);
            assertTrue(message.contains("include 1"), message);
            assertTrue(message.contains("include 2"), message);
        }
    }
}
