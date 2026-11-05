/* Physical comments and conservative non-owning associations. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CppCommentGraphTest {
    private SourceFile parse(String text) { return Parser.parse("comments.cpp", text, Language.CPP).build(); }
    @Test void exactPhysicalSpellingSurvivesTranslationPhases() {
        String text = "/??/\r\n/ first\\\r\ncontinued\r\nint x; /\\\n* block\r\ntext *\\\n/\n";
        var comments = CppLexer.scan("physical.cpp", text).comments();
        assertEquals(2, comments.size());
        assertEquals(Comment.Kind.LINE, comments.get(0).kind());
        assertEquals(Comment.Kind.BLOCK, comments.get(1).kind());
        for (var comment : comments) assertEquals(comment.text(), text.substring(comment.range().start().offset(), comment.range().end().offset()));
        assertTrue(comments.get(0).text().startsWith("/??/"));
        assertEquals(1, comments.get(0).range().start().line());
    }
    @Test void lineCommentIncludesTrailingPhysicalSpliceAtEof() {
        String text = "// tail" + "\\\n";
        var comment = CppLexer.scan("tail.cpp", text).comments().getFirst();
        assertEquals(text, comment.text());
        assertEquals(text.length(), comment.range().end().offset());
    }
    @Test void projectReplacementRetainsOwnedCommentIdentity() {
        var project = Project.create().addFile(parse("// attached\nint value;\n").edit()).build();
        var file = project.files().getFirst();
        var original = (VariableDeclaration) file.items().getFirst();
        var editor = new ProjectEditor(project);
        editor.replace(original, original.edit().setName("renamed").build());
        var edited = editor.build().files().getFirst();
        assertSame(edited.comments().getFirst(), edited.items().getFirst().commentReferences().getFirst());
        assertThrows(IllegalStateException.class, () -> new ProjectEditor(project).remove(file.comments().getFirst()));
    }
    @Test void onlyRealLexicalCommentsAreRecorded() {
        var file = parse("const char *s = \"// not /* comment */\"; // real\n#if 0\n/* inactive */\n#endif\n");
        assertEquals(List.of("// real", "/* inactive */"), file.comments().stream().map(Comment::text).toList());
        assertEquals(List.of(file.comments().getFirst()), file.items().getFirst().commentReferences());
        assertEquals(2, file.children().stream().filter(Comment.class::isInstance).count());
    }
    @Test void attachesConsecutiveLeadingAndLastTrailingDeclaration() {
        var file = parse("// first\n// second\nint x; int y; // trailing\n// detached\n\nint z;\n");
        assertEquals(List.of("// first", "// second"), file.items().get(0).commentReferences().stream().map(Comment::text).toList());
        assertEquals(List.of("// trailing"), file.items().get(1).commentReferences().stream().map(Comment::text).toList());
        assertTrue(file.items().get(2).commentReferences().isEmpty());
        var copied = file.edit().build();
        assertSame(copied.comments().getFirst(), copied.items().getFirst().commentReferences().getFirst());
        assertNotSame(file.comments().getFirst(), copied.comments().getFirst());
    }
    @Test void attachesAfterCommaToArgumentNotCall() {
        var file = parse("int f(int a, int b); int g() { return f(1, /* second */ 2); }\n");
        var calls = new ArrayList<CallExpression>();
        file.walk(new NodeVisitor() { @Override public void visit(CallExpression call) { calls.add(call); } });
        assertEquals(1, calls.size());
        assertSame(file.comments().getFirst(), calls.getFirst().arguments().get(1).commentReferences().getFirst());
        assertTrue(calls.getFirst().commentReferences().isEmpty());
    }
    @Test void includedAndInactiveCommentsHaveOneFileOwner() {
        Path path = Path.of("/tmp/comment-main.cpp");
        var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03");
        var file = Parser.parse(unit, "#include \"comment-header.h\"\n// main\nint mainValue;\n", Map.of("/tmp/comment-header.h", "// header\nint headerValue;\n#if 0\n/* inactive */\n#endif\n")).build();
        assertEquals(3, file.comments().size());
        assertEquals(2, file.comments().stream().filter(c -> c.range().orElseThrow().file().endsWith("comment-header.h")).count());
        var project = Project.create().addFile(file.edit()).build();
        var copy = project.edit().build();
        var comments = copy.files().getFirst().comments();
        for (Node node : copy.files().getFirst().items()) for (Comment comment : node.commentReferences()) assertTrue(comments.contains(comment));
    }
    @Test void javaBehaviorIsUnchanged() {
        assertTrue(Parser.parse("Example.java", "// old behavior\nclass Example {}", Language.JAVA).build().comments().isEmpty());
    }
}
