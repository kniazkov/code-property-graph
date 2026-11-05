/* Code Property Graph: explicit comment-removal library contract. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.CppLexer;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.parser.PreprocessingToken;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommentsTest {
    private static final String FILE = Path.of("/virtual/comments/main.cpp").toString();
    private static final String HEADER = Path.of("/virtual/comments/local.h").toString();

    @Test void lexicalRemovalUsesTranslationPhasesAndPreservesLiteralsAndPhysicalLines() {
        String input = "int/**/value; // hidden\\\r\ncontinued\r\n"
                + "const char *s = \"/* text */ // text\";\r\n"
                + "int /??/\n* split ??/\ncomment */ other;\n"
                + "#define N /*first\n\nlast*/ 7\nint n = N;\n";
        String output = Comments.remove(FILE, input);
        assertFalse(CppLexer.scan(FILE, input).comments().isEmpty());
        assertTrue(CppLexer.scan(FILE, output).comments().isEmpty());
        assertEquals(tokens(input), tokens(output));
        assertTrue(output.contains("\"/* text */ // text\""));
        assertTrue(output.startsWith("int    value;"));
        assertEquals(input.chars().filter(c -> c == '\n').count(), output.chars().filter(c -> c == '\n').count());
        assertEquals(input.chars().filter(c -> c == '\r').count(), output.chars().filter(c -> c == '\r').count());
        assertEquals(output, Comments.remove(FILE, output));
    }

    @Test void headerNamesAndSinglePassSplicingAreNotRescannedAsComments() {
        for (String input : List.of("#include <a/*b>\n", "#include \"a//b\"\n",
                "// hidden" + "\\\\\n\n" + "int visible;\n",
                "int/**/a;\r// hidden\rint b;\r",
                "int a; /" + "\\\n" + "/ hidden\nint b;\n")) {
            String output = Comments.remove(FILE, input);
            assertEquals(tokens(input), tokens(output));
            assertTrue(CppLexer.scan(FILE, output).comments().isEmpty());
        }
    }

    @Test void allVariantsHeadersAndRawProvenanceAreCleanAndIndependent() {
        String source = "#include \"local.h\" // include marker\n"
                + "#define N /* macro marker */ 4\n"
                + "#ifdef FEATURE\nint/**/value = N; // enabled marker\n"
                + "#else\nint value = 5; /* inactive marker */\n#endif\n"
                + "int main() { /* body marker */ return value + helper(); }\n";
        var sources = Map.of(FILE, source, HEADER, "// header marker\nint helper() { return 1; }\n");
        Build build = new Build(List.of(unit(false), unit(true)));
        Instant time = Instant.parse("2020-01-01T00:00:00Z");
        var root = Project.create().setBuild(build);
        for (var unit : build.units()) root.addFile(Parser.parse(unit, source, sources, time));
        Project original = NameResolver.resolve(root.build());
        String before = ProjectJson.encode(original).toText("");
        Project clean = Comments.remove(original);
        assertNotSame(original, clean);
        assertEquals(before, ProjectJson.encode(original).toText(""));
        assertEquals(build, clean.build().orElseThrow());
        assertEquals(2, clean.files().size());
        for (var file : clean.files()) {
            assertTrue(file.comments().isEmpty());
            var record = file.provenance().orElseThrow().unit();
            assertEquals(time, record.translationTime());
            assertEquals(2, record.sources().size());
            record.sources().forEach((path, text) -> assertTrue(CppLexer.scan(path, text).comments().isEmpty()));
            assertFalse(record.toString().contains("marker"));
            assertFalse(file.commentReferences().iterator().hasNext());
        }
        Set<Node> oldNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        original.walk(new NodeVisitor() { @Override public void visit(Node node) { oldNodes.add(node); } });
        clean.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                assertFalse(oldNodes.contains(node));
                assertTrue(node.commentReferences().isEmpty());
            }
            @Override public void visit(NameExpression name) {
                visit((Node) name);
                name.target().ifPresent(target -> assertFalse(oldNodes.contains(target)));
            }
        });
        var generated = Source.files(clean, Language.CPP);
        assertTrue(generated.containsKey("local.h"));
        generated.forEach((path, text) -> assertTrue(CppLexer.scan(path, text).comments().isEmpty()));
        assertTrue(Source.files(original, Language.CPP).get("main.cpp").contains("inactive marker"));
    }

    @Test void currentAstEditsAreAppliedBeforeCleaning() {
        Project original = Project.create().addFile(Parser.parse(FILE,
                "int main() { /* keep only in old snapshot */ return 1; }\n", Language.CPP)).build();
        var literals = new ArrayList<IntegerLiteral>();
        original.walk(new NodeVisitor() { @Override public void visit(IntegerLiteral value) { literals.add(value); } });
        var editor = new ProjectEditor(original);
        editor.replace(literals.getFirst(), IntegerLiteral.create().setValue(9).build());
        Project edited = editor.build();
        Project clean = Comments.remove(edited);
        String generated = Source.files(clean, Language.CPP).get("main.cpp");
        assertTrue(generated.contains("return 9;"));
        assertFalse(generated.contains("keep only"));
        assertTrue(Source.files(original, Language.CPP).get("main.cpp").contains("return 1;"));
    }

    @Test void multilineCommentInMacroDoesNotTerminateDirectiveOrChangeLineBuiltin() {
        String input = "#define N /* first\r\n\r\nlast */ 7\r\nint value = N;\r\nint line = __LINE__;\r\n";
        Project original = Project.create().addFile(Parser.parse(FILE, input, Language.CPP)).build();
        Project clean = Comments.remove(original);
        var before = original.files().getFirst().provenance().orElseThrow().unit().tokens();
        var after = clean.files().getFirst().provenance().orElseThrow().unit().tokens();
        assertEquals(before.stream().map(Preprocessing.Token::spelling).filter(s -> !s.isBlank()).toList(),
                after.stream().map(Preprocessing.Token::spelling).filter(s -> !s.isBlank()).toList());
    }

    @Test void rejectsUnsupportedInputsWithoutChangingThemAndCopiesEmptyProjects() {
        Project java = Project.create().addFile(Parser.parse("A.java", "class A {}", Language.JAVA)).build();
        assertThrows(IllegalArgumentException.class, () -> Comments.remove(java));
        Project empty = Project.create().build();
        assertNotSame(empty, Comments.remove(empty));
    }

    private static List<String> tokens(String source) {
        return CppLexer.scan(FILE, source).tokens().stream()
                .filter(t -> t.kind() != PreprocessingToken.Kind.WHITESPACE)
                .map(t -> t.kind() + ":" + t.spelling()).toList();
    }
    private static Build.TranslationUnit unit(boolean enabled) {
        return new Build.TranslationUnit(new Build.PhysicalSource(Path.of(FILE)), Path.of(FILE).getParent(),
                List.of(), enabled ? List.of(new Build.MacroOperation(true, "FEATURE=1")) : List.of(), "c++03");
    }
}
