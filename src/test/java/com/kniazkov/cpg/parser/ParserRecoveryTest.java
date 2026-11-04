/* Code Property Graph: bounded, source-preserving diagnostic parsing. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParserRecoveryTest {
    private static SourceFile parse(String source) { return Parser.parseRecovering("broken.cpp", source, Language.CPP).build(); }
    private static List<VariableDeclaration> variables(SourceFile file) {
        var result = new ArrayList<VariableDeclaration>();
        file.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration node) { result.add(node); } });
        return result;
    }
    private static List<String> functions(SourceFile file) {
        return file.items().stream().filter(FunctionDeclaration.class::isInstance).map(FunctionDeclaration.class::cast)
                .map(FunctionDeclaration::name).toList();
    }

    @Test void missingInitializerRetainsDeclarationAndFollowingStatements() {
        String text = "int main(){ int x = ; int y = 2; return y; }";
        var file = parse(text);
        assertTrue(file.hasErrors());
        assertTrue(file.stoppedAt().isEmpty());
        assertEquals(List.of("x", "y"), variables(file).stream().map(VariableDeclaration::name).toList());
        var error = assertInstanceOf(ErrorExpression.class, variables(file).getFirst().initializer().orElseThrow());
        var problem = file.parseProblems().getFirst();
        assertEquals(problem, error.problem());
        assertEquals("", problem.skippedText());
        assertTrue(problem.recovered());
        assertEquals(text.indexOf(';'), problem.range().start().offset());
        assertEquals(problem.range().start(), problem.range().end());
        assertEquals(problem.range(), error.range().orElseThrow());
        var body = ((FunctionDeclaration) file.items().getFirst()).body().orElseThrow();
        assertEquals(3, body.items().size());
        assertInstanceOf(ReturnStatement.class, body.items().getLast());
        assertThrows(ParseException.class, () -> Parser.parse("broken.cpp", text, Language.CPP));
    }

    @Test void malformedInitializerExportsExactNonemptyFragment() {
        String text = "int main(){ int x = 1 +  ; int y = 2; }";
        var file = parse(text);
        var problem = file.parseProblems().getFirst();
        assertEquals("1 +  ", problem.skippedText());
        assertEquals(problem.skippedText(), text.substring(problem.range().start().offset(), problem.range().end().offset()));
        assertEquals(2, variables(file).size());
        assertTrue(file.stoppedAt().isEmpty());
    }

    @Test void unbalancedInitializerStopsWithoutCrossingTheBoundary() {
        String text = "int main(){ int kept = 1; int x = (1 + ; int ignored = 2; }";
        var file = parse(text);
        assertEquals(List.of("kept"), variables(file).stream().map(VariableDeclaration::name).toList());
        assertEquals(text.indexOf(';', text.indexOf("(1")), file.stoppedAt().orElseThrow().start().offset());
        assertFalse(file.parseProblems().getFirst().recovered());
        var problem = file.parseProblems().getFirst();
        assertEquals(text.substring(problem.range().start().offset()), problem.skippedText());
    }

    @Test void truncatedBodyRetainsItsStatementsAndAllEarlierDiagnostics() {
        String text = "int earlier(){return 7;} int main(){ int x = ; int y = ;";
        var file = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> parse(text));
        assertEquals(List.of("earlier", "main"), functions(file));
        assertEquals(List.of("x", "y"), variables(file).stream().map(VariableDeclaration::name).toList());
        assertEquals(3, file.parseProblems().size());
        assertEquals(List.of(true, true, false), file.parseProblems().stream().map(ParseProblem::recovered).toList());
        assertEquals(text.length(), file.stoppedAt().orElseThrow().start().offset());
    }

    @Test void repeatedUnexpectedTokensAndEofAlwaysTerminate() {
        for (String text : List.of("", "? ? ? ?", "int main(){", "int main(){return", "int main(){int x =", "int main(){int x = + + ; int y = ;}"))
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> parse(text), text);
    }

    @Test void compilerAttributeWinsOverUnsupportedStructGrammarOnlyInActiveCode() {
        String declaration = "struct __attribute__((packed)) Packed { int value; };";
        var file = parse(declaration);
        assertEquals(8, file.parseProblems().getFirst().range().start().column());
        assertTrue(file.parseProblems().getFirst().message().contains("__attribute__"));
        var strict = assertThrows(ParseException.class, () -> Parser.parse("broken.cpp", declaration, Language.CPP));
        assertEquals(8, strict.column());
        assertFalse(parse("#if 0\n" + declaration + "\n#endif\nint main(){return 0;}").hasErrors());
        assertFalse(parse("#define UNUSED __attribute__((packed))\nint main(){return 0;}").hasErrors());
    }

    @Test void lexicalAndEditionFaultsDoNotDiscardCompletedDeclarations() {
        String prefix = "int retained(){return 1;}\n";
        for (String tail : List.of("@", "struct __attribute__((packed)) Packed { int x; };", "\"unterminated", "/*unfinished")) {
            var file = parse(prefix + tail);
            assertEquals(List.of("retained"), functions(file), tail);
            assertTrue(file.stoppedAt().isPresent(), tail);
            assertTrue(file.hasErrors(), tail);
            assertTrue(file.provenance().isPresent(), tail);
        }
    }

    @Test void lexicalFailureKeepsRecoveredDiagnosticsAndCallablePrefix() {
        var file = parse("int main(){int x = ; int y = 2; \"unfinished");
        assertEquals(List.of("x", "y"), variables(file).stream().map(VariableDeclaration::name).toList());
        assertEquals(2, file.parseProblems().size());
        assertTrue(file.parseProblems().getFirst().recovered());
        assertTrue(file.parseProblems().getLast().message().contains("Unterminated string"));
    }

    @Test void failedMacroExpansionKeepsPreviouslyExpandedTokens() {
        String text = "#define F(x) x\nint retained(){return 1;}\nint main(){int x = ; F(";
        var file = parse(text);
        assertEquals(List.of("retained", "main"), functions(file));
        assertEquals(1, variables(file).size());
        assertEquals(2, file.parseProblems().size());
        assertTrue(file.parseProblems().getLast().message().contains("Unterminated invocation"));
        assertEquals(text.lastIndexOf("F("), file.stoppedAt().orElseThrow().start().offset());
    }

    @Test void variadicMacroFailureKeepsPrefixAndSkipsInactiveDefinitions() {
        String prefix = "int retained(){return 1;}\n";
        for (String macro : List.of("#define F(...) 1", "#define F(x...) 1", "#define F(x, ...) 1")) {
            var file = parse(prefix + macro);
            assertEquals(List.of("retained"), functions(file));
            assertTrue(file.parseProblems().getFirst().message().toLowerCase().contains("variadic"));
            assertEquals((prefix + macro).indexOf("..."), file.stoppedAt().orElseThrow().start().offset());
            assertFalse(parse("#if 0\n" + macro + "\n#endif\n" + prefix).hasErrors());
        }
    }

    @Test void modernLiteralsAndCompilerExtensionsAreRejectedAfterConditionals() {
        for (String value : List.of("0b101", "1LL", "1ULL", "0x1p2", "12_suffix", "u8\"text\"", "u'x'", "U\"text\"", "R\"(text)\"", "\"text\"_suffix")) {
            String declaration = "int main(){int x = " + value + ";}";
            var error = assertThrows(ParseException.class, () -> Parser.parse("broken.cpp", declaration, Language.CPP), value);
            assertTrue(error.reason().contains("C++03"), value);
            assertTrue(parse(declaration).stoppedAt().isPresent(), value);
            assertFalse(parse("#if 0\n" + declaration + "\n#endif\nint main(){return 0;}").hasErrors(), value);
        }
        for (String extension : List.of("__typeof__", "__asm__", "__declspec", "__builtin_expect", "__alignof__")) {
            var file = parse("int main(){int x = " + extension + "(1);}");
            assertTrue(file.stoppedAt().isPresent(), extension);
            assertTrue(file.parseProblems().getFirst().message().contains(extension));
        }
        assertFalse(parse("int main(){int u8 = 1; return u8;}").hasErrors());
        assertFalse(parse("int main(){int typeof = 1; return typeof;}").hasErrors());
    }

    @Test void includedFailureRetainsPhysicalCoordinatesAndConfiguration() {
        Path root = Path.of("root.cpp").toAbsolutePath(), header = root.resolveSibling("bad.h");
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(root), root.getParent(), List.of(),
                List.of(new Build.MacroOperation(true, "MODE=1")), null);
        String source = "int retained(){return 1;}\n#include \"bad.h\"\n";
        var file = Parser.parseRecovering(unit, source, Map.of(root.toString(), source, header.toString(), "int inHeader(){int x = ; \"unfinished")).build();
        assertEquals(List.of("retained", "inHeader"), functions(file));
        assertEquals(header.toString(), file.stoppedAt().orElseThrow().file());
        assertEquals(unit.variantId(), file.variantId().orElseThrow());
        assertEquals(2, file.provenance().orElseThrow().unit().sources().size());
        var project = Project.create().addFile(file.edit()).build();
        assertFalse(project.externalInputs().isEmpty());
        assertTrue(project.hasErrors());
    }

    @Test void errorsSurviveEditingAndVisitorDispatch() {
        var file = parse("int main(){int x = ;}");
        var project = Project.create().addFile(file.edit()).build();
        var copy = project.edit().build();
        assertTrue(copy.hasErrors());
        assertEquals(file.parseProblems(), copy.files().getFirst().parseProblems());
        var expressions = new ArrayList<Expression>();
        copy.walk(new NodeVisitor() { @Override public void visit(Expression expression) { expressions.add(expression); } });
        assertInstanceOf(ErrorExpression.class, expressions.getFirst());
        var edited = file.edit().setParseProblems(List.of()).build();
        assertTrue(edited.hasErrors(), "An owned error expression cannot make the root look successful");
    }

    @Test void truncatedOrInvalidClassTailKeepsItsCompletedMembers() {
        for (String tail : List.of("", " bad }", "}")) {
            var file = parse("class A { public: static void main(){int x = ;}" + tail);
            assertEquals(1, file.items().size());
            var clazz = assertInstanceOf(ClassDeclaration.class, file.items().getFirst());
            assertEquals(1, clazz.members().size());
            assertEquals(List.of("x"), variables(file).stream().map(VariableDeclaration::name).toList());
            assertEquals(2, file.parseProblems().size());
            assertTrue(file.stoppedAt().isPresent());
        }
    }

    @Test void javaRecoveryAlsoKeepsTheLexicalAndMemberPrefix() {
        var file = Parser.parseRecovering("A.java", "class A { public static void main(String[] args){int x = ; @", Language.JAVA).build();
        assertEquals(List.of("x"), variables(file).stream().map(VariableDeclaration::name).toList());
        assertEquals(2, file.parseProblems().size());
        assertTrue(file.stoppedAt().isPresent());
    }

    @Test void logicalDiagnosticCoordinatesRemainVisibleAlongsidePhysicalRecoveryRanges() {
        String text = "#line 100 \"virtual.cpp\"\n#error boom\n";
        var file = parse(text);
        var problem = file.parseProblems().getFirst();
        assertTrue(problem.message().contains("logical location: virtual.cpp:100:2"));
        assertEquals(2, problem.range().start().line());
        assertEquals(text.indexOf("error boom"), problem.range().start().offset());
        assertEquals(text.substring(problem.range().start().offset()), problem.skippedText());
        assertEquals(Path.of("broken.cpp").toAbsolutePath().toString(), file.stoppedAt().orElseThrow().file());
    }

    @Test void unsupportedEditionIsNotTurnedIntoAParseProblem() {
        assertThrows(IllegalArgumentException.class, () -> Parser.parseRecovering("x.cpp", "", Language.CPP, "c++11"));
    }
}
