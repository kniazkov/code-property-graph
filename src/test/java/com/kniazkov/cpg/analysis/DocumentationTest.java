/* Doxygen text, source coordinates and context-specific bindings. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.utilities.Comments;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationTest {
    private Project parse(String text) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("docs.cpp", text, Language.CPP)).build());
    }
    private List<CallableDeclaration> functions(Project project) {
        var result = new ArrayList<CallableDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(CallableDeclaration node) { result.add(node); } });
        return result;
    }
    @Test void orderedTagsPreserveRawUnknownCommandsAndExactNameRanges() {
        String raw = "/** @brief Summary.\r\n * \\param[in] count first line\r\n * second line\r\n * @unknown untouched\r\n * @return result\r\n */";
        Project project = parse(raw + "\nint f(int count);\n");
        Comment comment = project.files().getFirst().comments().getFirst();
        assertEquals(raw, comment.text());
        var entries = comment.documentationEntries();
        assertEquals(List.of(DocumentationEntry.Kind.BRIEF, DocumentationEntry.Kind.PARAM, DocumentationEntry.Kind.RETURN), entries.stream().map(DocumentationEntry::kind).toList());
        var parameter = entries.get(1);
        assertEquals("first line\r\nsecond line", parameter.description());
        SourceRange range = parameter.nameRange().orElseThrow();
        assertEquals("count", raw.substring(range.start().offset(), range.end().offset()));
        assertEquals(new SourcePosition(2, 15, raw.indexOf("count")), range.start());
        assertSame(functions(project).getFirst().parameters().getFirst(), parameter.bindings().getFirst().target());
        assertTrue(parameter.diagnostics().isEmpty());
    }
    @Test void unresolvedNameIsAnUntargetedWarningAtTheNameAndCopiesRemainValid() {
        String source = "/** @param size wrong */\nint f(int count);\n";
        Project project = parse(source);
        DocumentationEntry entry = project.files().getFirst().comments().getFirst().documentationEntries().getFirst();
        assertNull(entry.bindings().getFirst().target());
        assertEquals(Diagnostic.Severity.WARNING, entry.diagnostics().getFirst().severity());
        assertEquals("size", source.substring(entry.range().orElseThrow().start().offset(), entry.range().orElseThrow().end().offset()));
        assertFalse(project.hasErrors());
        Project copy = project.edit().build();
        var copied = copy.files().getFirst().comments().getFirst().documentationEntries().getFirst();
        assertSame(functions(copy).getFirst(), copied.bindings().getFirst().declaration());
        assertEquals(entry.diagnostics(), copied.diagnostics());
    }
    @Test void separateDeclarationBriefsAndPositionallyRelatedParameterDescriptions() {
        Project project = parse("/** @brief Public brief @param count number */\nint f(int count);\n/** @brief Implementation brief */\nint f(int n) { return n; }\n");
        var declaration = functions(project).getFirst(); var definition = functions(project).getLast();
        assertEquals(2, Documentation.commentsForDeclaration(project, definition).size());
        assertEquals(List.of("Public brief", "Implementation brief"), Documentation.forDeclaration(project, definition).stream()
                .filter(e -> e.kind() == DocumentationEntry.Kind.BRIEF).map(DocumentationEntry::description).toList());
        var entry = Documentation.forParameter(project, definition.parameters().getFirst()).getFirst();
        assertEquals("count", entry.name());
        assertSame(declaration.parameters().getFirst(), entry.bindings().getFirst().target());
        var editor = new ProjectEditor(project);
        editor.replace(declaration.parameters().getFirst(), declaration.parameters().getFirst().edit().setName("changed").build());
        var edited = editor.build();
        var target = edited.files().getFirst().comments().getFirst().documentationEntries().get(1).bindings().getFirst().target();
        assertSame(functions(edited).getFirst().parameters().getFirst(), target);
    }
    @Test void repeatedHeaderCommentHasOneOwnerAndEveryIncludeOccurrenceBinding() {
        Path path = Path.of("/tmp/docs-main.cpp");
        var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03");
        var file = Parser.parse(unit, "#include \"docs.h\"\n#include \"docs.h\"\n", Map.of("/tmp/docs.h", "/** @param count items */\nint f(int count);\n"));
        Project project = NameResolver.resolve(Project.create().addFile(file).build());
        assertEquals(1, project.files().getFirst().comments().size());
        var entry = project.files().getFirst().comments().getFirst().documentationEntries().getFirst();
        assertEquals(2, entry.bindings().size());
        assertNotSame(entry.bindings().get(0).target(), entry.bindings().get(1).target());
        assertNotEquals(entry.bindings().get(0).declaration().provenance(), entry.bindings().get(1).declaration().provenance());
        assertEquals(1, Documentation.forDeclaration(project, functions(project).getFirst()).size());
        var cleaned = Comments.remove(project);
        assertTrue(cleaned.files().getFirst().comments().isEmpty());
        assertTrue(Documentation.forDeclaration(cleaned, functions(cleaned).getFirst()).isEmpty());
    }
    @Test void conditionalHeaderSignaturesKeepDistinctTargets() {
        Path path = Path.of("/tmp/docs-conditional.cpp");
        var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03");
        var file = Parser.parse(unit, "#include \"docs.h\"\n#define WIDE\n#include \"docs.h\"\n", Map.of("/tmp/docs.h",
                "/** @param count items */\nint f(\n#ifdef WIDE\nlong count\n#else\nint count\n#endif\n);\n"));
        Project project = NameResolver.resolve(Project.create().addFile(file).build());
        var entry = project.files().getFirst().comments().getFirst().documentationEntries().getFirst();
        assertEquals(2, entry.bindings().size());
        assertNotSame(entry.bindings().get(0).target(), entry.bindings().get(1).target());
        var functions = functions(project);
        assertEquals(1, Documentation.relatedDeclarations(project, functions.getFirst()).size());
        assertEquals(1, Documentation.relatedDeclarations(project, functions.getLast()).size());
        assertEquals(List.of(entry), Documentation.forParameter(project, functions.getFirst().parameters().getFirst()));
        assertEquals(List.of(entry), Documentation.forParameter(project, functions.getLast().parameters().getFirst()));
    }
    @Test void templateNamesBindOnlyInTheirDocumentedDeclaration() {
        Project project = parse("/** @tparam T element type @param value item */\ntemplate<class T> T identity(T value) { return value; }\n");
        var function = functions(project).getFirst();
        var entries = project.files().getFirst().comments().getFirst().documentationEntries();
        assertEquals(2, entries.size());
        assertSame(function.templateParameters().getFirst(), entries.getFirst().bindings().getFirst().target());
        assertSame(function.parameters().getFirst(), entries.getLast().bindings().getFirst().target());
    }
    @Test void multilineNamesSkipDecorativeStarsButKeepExactPhysicalRanges() {
        for (String newline : List.of("\n", "\r\n")) {
            String text = String.join(newline, "/** @param", " * [in]", " * count items", " * \\tparam", " * T value type", " */",
                    "template<class T> T f(int count);");
            Project project = parse(text);
            var entries = project.files().getFirst().comments().getFirst().documentationEntries();
            assertEquals(List.of("count", "T"), entries.stream().map(DocumentationEntry::name).toList());
            for (var entry : entries) {
                var range = entry.nameRange().orElseThrow();
                assertEquals(entry.name(), text.substring(range.start().offset(), range.end().offset()));
                assertEquals(4, range.start().column());
                assertTrue(entry.diagnostics().isEmpty());
                assertNotNull(entry.bindings().getFirst().target());
            }
            assertEquals(3, entries.getFirst().range().orElseThrow().start().line());
            assertEquals(5, entries.getLast().range().orElseThrow().start().line());
        }
    }
    @Test void missingMultilineNameNeverConsumesTheNextCommand() {
        Project project = parse("/** @param\n * @return result\n */\nint f(int count);\n");
        var entries = project.files().getFirst().comments().getFirst().documentationEntries();
        assertEquals(2, entries.size());
        assertEquals("", entries.getFirst().name());
        assertNull(entries.getFirst().bindings().getFirst().target());
        assertEquals(Diagnostic.Severity.WARNING, entries.getFirst().diagnostics().getFirst().severity());
        assertEquals(DocumentationEntry.Kind.RETURN, entries.getLast().kind());
        assertEquals("result", entries.getLast().description());
    }
    @Test void macroExpandedParameterTypesRetainEveryPhysicalDeclarationBinding() {
        Path path = Path.of("/tmp/docs-macro.cpp");
        var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03");
        var file = Parser.parse(unit, "#define TYPE int\n#include \"docs.h\"\n#undef TYPE\n#define TYPE long\n#include \"docs.h\"\n", Map.of("/tmp/docs.h",
                "/** @param count items */\nint f(TYPE count);\n"));
        Project project = NameResolver.resolve(Project.create().addFile(file).build());
        var entry = project.files().getFirst().comments().getFirst().documentationEntries().getFirst();
        assertEquals(2, entry.bindings().size());
        assertNotSame(entry.bindings().get(0).target(), entry.bindings().get(1).target());
        assertEquals(1, Documentation.relatedDeclarations(project, functions(project).getFirst()).size());
    }
    @Test void macroGeneratedDeclarationAndParameterNamesRemainUnbound() {
        for (String text : List.of(
                "#define NAME f\n/** @param count items */\nint NAME(int count);\n",
                "#define ARG cb\n/** @param cb docs */\nint f(int (*ARG)(int));\n",
                "#define FN f\n/** @brief docs */\nint (*FN())(int);\n",
                "#define NAME count\n/** @param count items */\nint f(int NAME);\n",
                "#define DECL int f(int count);\n/** @param count items */\nDECL\n")) {
            Project project = parse(text);
            assertFalse(project.hasErrors());
            assertEquals(1, functions(project).size());
            assertTrue(project.files().getFirst().comments().getFirst().documentationEntries().getFirst().bindings().isEmpty());
        }
    }
    @Test void lineCommentProseContinuesWithoutMergingPhysicalOwnership() {
        Project project = parse("/// @brief Summary\n/// continued brief\n/// @param count items\n/// continued parameter\n/// @unknown untouched\n/// not part of parameter\nint f(int count);\n");
        assertEquals(6, project.files().getFirst().comments().size());
        var function = functions(project).getFirst();
        assertEquals(6, Documentation.commentsForDeclaration(project, function).size());
        var entries = Documentation.forDeclaration(project, function);
        assertEquals(List.of("Summary\ncontinued brief", "items\ncontinued parameter"), entries.stream().map(DocumentationEntry::description).toList());
        assertEquals("/// continued brief", project.files().getFirst().comments().get(1).text());
        assertEquals(1, entries.getFirst().tagRange().orElseThrow().end().line());
        assertSame(function.parameters().getFirst(), entries.getLast().bindings().getFirst().target());
    }
    @Test void macrosInFunctionBodiesAndDefaultsCannotHidePhysicalParameterDocumentation() {
        Project project = parse("#define ZERO 0\n/** @param value input */\nint f(int value = ZERO) { return value + ZERO; }\n");
        var function = functions(project).getFirst();
        var entry = Documentation.forParameter(project, function.parameters().getFirst()).getFirst();
        assertSame(function.parameters().getFirst(), entry.bindings().getFirst().target());
        assertTrue(entry.diagnostics().isEmpty());
        Project fullBody = parse("#define BODY { return value; }\n/** @param value input */\nint f(int value) BODY\n");
        var fullFunction = functions(fullBody).getFirst();
        assertEquals(1, Documentation.forParameter(fullBody, fullFunction.parameters().getFirst()).size());
    }
    @Test void macrosInExceptionTypesAndConstructorInitializersRetainBindings() {
        Project project = parse("#define ERROR int\n#define INIT(v) field(v)\nstruct C { int field;\n/** @param value initial */\nC(int value) throw(ERROR) : INIT(value) {}\n};\n");
        var constructor = functions(project).getFirst();
        var entries = Documentation.forParameter(project, constructor.parameters().getFirst());
        assertEquals(1, entries.size());
        assertSame(constructor.parameters().getFirst(), entries.getFirst().bindings().getFirst().target());
    }
    @Test void ordinaryCommentsAreNotInterpreted() {
        assertTrue(parse("/* @param count ordinary */\nint f(int count);\n").files().getFirst().comments().getFirst().documentationEntries().isEmpty());
    }
}
