/* Comment ownership, exports and non-destructive source generation. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommentExportTest {
    private static Project parse(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("comments.cpp", source, Language.CPP)).build());
    }
    private static int occurrences(String text, String value) { return text.split(java.util.regex.Pattern.quote(value), -1).length - 1; }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); }});
        return result;
    }
    @Test void commentsHaveOneOwnerAndExplicitExportedEdgesAfterCopy() {
        var original = parse("// heading\nint first, last; // trailing\nint main() { return 0; }\n");
        var copy = original.edit().build();
        assertEquals(ProjectJson.encode(original), ProjectJson.encode(copy));
        for (var project : List.of(original, copy)) {
            var file = project.files().getFirst();
            assertEquals(2, file.comments().size());
            assertEquals(2, nodes(project, Comment.class).size());
            assertTrue(file.children().containsAll(file.comments()));
            String json = ProjectJson.encode(project).toText("");
            assertEquals(2, occurrences(json, "\"kind\": \"Comment\""));
            assertTrue(json.contains("HAS_COMMENT"), json);
            assertTrue(json.contains("commentKind"), json);
            String dot = DotVisualizer.toDot(project, false);
            assertTrue(dot.contains("HAS_COMMENT"), dot);
            assertTrue(dot.contains("// trailing"), dot);
            project.walk(new NodeVisitor() { @Override public void visit(Node n) {
                for (var comment : n.commentReferences()) assertTrue(file.comments().stream().anyMatch(c -> c == comment));
            }});
        }
        assertNotSame(original.files().getFirst().comments().getFirst(), copy.files().getFirst().comments().getFirst());
    }
    @Test void ordinaryFilesRemainExactAndCanonicalFragmentsRetainCommentsOnce() {
        String text = "// heading\r\nint main() { /* interior */ return 0; } // tail\r\n";
        var project = parse(text);
        assertEquals(text, Source.files(project, Language.CPP).get("comments.cpp"));
        String canonical = Source.render(project.files().getFirst(), Language.CPP);
        for (var comment : project.files().getFirst().comments()) assertEquals(1, occurrences(canonical, comment.text()), canonical);
        var reparsed = parse(canonical);
        assertFalse(reparsed.hasErrors(), canonical);
        assertEquals(3, reparsed.files().getFirst().comments().size());
    }
    @Test void literalEditsKeepSurroundingCommentsAndOriginalSnapshot() {
        String text = "// heading\nint main() { return /* value */ 0; } // tail\n";
        var project = parse(text);
        var editor = new ProjectEditor(project);
        editor.replace(nodes(project, IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(1).build());
        var changed = NameResolver.resolve(editor.build());
        String result = Source.files(changed, Language.CPP).get("comments.cpp");
        assertEquals(text.replace("*/ 0", "*/ 1"), result);
        assertEquals(text, Source.files(project, Language.CPP).get("comments.cpp"));
        assertFalse(parse(result).hasErrors());
        assertEquals(result, Source.physicalSources(changed).values().iterator().next());
    }
    @Test void structuralReplacementRetainsInteriorCommentsWithoutDuplicatingAttachedOnes() {
        String text = "// heading\nint main() { return /* value */ 0; } // tail\n";
        var project = parse(text);
        var editor = new ProjectEditor(project);
        editor.replace(nodes(project, ReturnStatement.class).getFirst(),
                ReturnStatement.create().setValue(IntegerLiteral.create().setValue(2)).build());
        var changed = NameResolver.resolve(editor.build());
        String result = Source.files(changed, Language.CPP).get("comments.cpp");
        for (var comment : project.files().getFirst().comments()) assertEquals(1, occurrences(result, comment.text()), result);
        assertEquals(2, nodes(parse(result), IntegerLiteral.class).getFirst().value());
    }

    @Test void physicalGenerationDoesNotSilentlyIgnoreCommentCatalogueEdits() {
        var project = parse("// old heading\nint main() { return 0; }\n");
        var editor = new ProjectEditor(project);
        editor.replace(project.files().getFirst().comments().getFirst(),
                Comment.create().setKind(Comment.Kind.LINE).setText("// new heading").build());
        var changed = editor.build();
        assertThrows(IllegalArgumentException.class, () -> Source.physicalSources(changed));
        String canonical = Source.render(changed.files().getFirst(), Language.CPP);
        assertTrue(canonical.contains("// new heading"), canonical);
        assertFalse(canonical.contains("// old heading"), canonical);
    }

    @Test void eofCommentSpliceCannotSwallowCanonicalSyntax() {
        for (String tail : List.of("\\", "??/")) {
            var project = parse("int value; // EOF " + tail);
            String generated = Source.render(project.files().getFirst(), Language.CPP);
            var again = parse(generated);
            assertEquals(1, nodes(again, VariableDeclaration.class).size(), generated);
            assertEquals(1, again.files().getFirst().comments().size(), generated);
        }
    }

    @Test void cppSplicedCommentsCannotExposeTheirContentsInJava() {
        var comment = Comment.create().setKind(Comment.Kind.LINE).setText("// hidden \\\nint injected; ");
        var file = SourceFile.create().setPath("Main.java").setLanguage(Language.JAVA).addComment(comment).build();
        assertFalse(Source.languages(file).contains(Language.JAVA));
        assertThrows(IllegalArgumentException.class, () -> Source.render(file, Language.JAVA));
    }

    @Test void canonicalJavaRenderingAlsoRetainsExplicitCommentNodes() {
        var comment = Comment.create().setKind(Comment.Kind.LINE).setText("// portable heading");
        var file = SourceFile.create().setPath("Main.java").setLanguage(Language.JAVA)
                .addComment(comment).build();
        assertEquals("// portable heading\n", Source.render(file, Language.JAVA));
    }
}
