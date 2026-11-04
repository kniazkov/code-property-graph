/* Code Property Graph: conservative macro-edit source generation. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MacroSourceTest {
    @TempDir Path directory;

    private Project parse(String source) throws Exception {
        Files.writeString(directory.resolve("main.cpp"), source);
        return Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
    }
    private List<NameExpression> names(Project project, String name) {
        var result = new ArrayList<NameExpression>();
        project.walk(new NodeVisitor() { @Override public void visit(NameExpression n) {
            if (n.name().equals(name)) result.add(n);
        }});
        return result;
    }
    @Test void freshReplacementInheritsProvenanceWithoutMutatingEitherInput() throws Exception {
        var project = parse("#define S(x) ((x)*(x))\nint main() { int a = 2; return S(a); }\n");
        var old = names(project, "a").getLast();
        var replacement = NameExpression.create().setName("b").build();
        var editor = new ProjectEditor(project);
        editor.replace(old, replacement);
        var result = names(editor.build(), "b").getFirst();
        assertEquals(old.range(), result.range());
        assertEquals(old.provenance(), result.provenance());
        assertTrue(replacement.provenance().isEmpty());
        assertEquals(2, names(project, "a").size());
    }
    @Test void explicitReplacementProvenanceIsNotOverwritten() throws Exception {
        var project = parse("#define A 2\nint main() { int a = A; return a + a; }\n");
        var nodes = names(project, "a");
        var replacement = NameExpression.create().setName("b");
        replacement.setProvenance(nodes.getFirst().provenance().orElseThrow());
        replacement.setRange(nodes.getFirst().range().orElseThrow());
        var editor = new ProjectEditor(project);
        editor.replace(nodes.getLast(), replacement.build());
        var result = names(editor.build(), "b").getFirst();
        assertEquals(nodes.getFirst().provenance(), result.provenance());
        assertEquals(nodes.getFirst().range(), result.range());
    }
    @Test void partialExpressionMacroFailsRatherThanEditingItsDefinition() throws Exception {
        var project = parse("#define PART a +\nint main() { int a = 2; return PART 1; }\n");
        var editor = new ProjectEditor(project);
        editor.replace(names(project, "a").getFirst(), NameExpression.create().setName("b").build());
        var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(editor.build(), Language.CPP));
        assertTrue(error.getMessage().contains("whole syntax subtree"), error.getMessage());
    }
    @Test void replayRejectsAnEditWhoseSpellingWouldExpandAgain() throws Exception {
        var project = parse("#define S(x) ((x)*(x))\n#define b 9\nint main() { int a = 2; return S(a); }\n");
        var editor = new ProjectEditor(project);
        editor.replace(names(project, "a").getLast(), NameExpression.create().setName("b").build());
        var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(editor.build(), Language.CPP));
        assertTrue(error.getMessage().contains("effective expansion"), error.getMessage());
    }
    @Test void capturedPhysicalTextIsImmutable() throws Exception {
        var project = parse("#define A 2\nint main() { return A; }\n");
        var sources = project.files().getFirst().provenance().orElseThrow().unit().sources();
        assertThrows(UnsupportedOperationException.class, () -> sources.put("main.cpp", "changed"));
        assertEquals(Files.readString(directory.resolve("main.cpp")), sources.get(directory.resolve("main.cpp").toString()));
    }
    @Test void absoluteLiteralAndMacroComputedIncludesAreRejectedForRelocation() throws Exception {
        Path header = directory.resolve("absolute.h");
        Files.writeString(header, "#define V 3\n");
        for (String include : List.of("#include \"" + header + "\"\n",
                "#define HEADER \"" + header + "\"\n#include HEADER\n")) {
            var project = parse(include + "int main() { return V; }\n");
            var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
            assertTrue(error.getMessage().contains("absolute include is not relocatable"), error.getMessage());
        }
    }
    @Test void includesThatEscapeAndReenterTheOriginalRootAreRejected() throws Exception {
        Files.writeString(directory.resolve("a.h"), "#define V 3\n");
        String operand = "../" + directory.getFileName() + "/a.h";
        for (String include : List.of("#include \"" + operand + "\"\n",
                "#define HEADER \"" + operand + "\"\n#include HEADER\n")) {
            var project = parse(include + "int main() { return V; }\n");
            var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
            assertTrue(error.getMessage().contains("Header not found"), error.getMessage());
        }
    }
    @Test void structuralRemovalCannotSilentlyFlattenUntouchedMacroCalls() throws Exception {
        var project = parse("#define V 3\nint main() { int unused = 1; return V; }\n");
        var variables = new ArrayList<VariableDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration n) { variables.add(n); }});
        var editor = new ProjectEditor(project);
        editor.remove(variables.getFirst());
        var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(editor.build(), Language.CPP));
        assertTrue(error.getMessage().contains("structural edit"), error.getMessage());
    }
    @Test void structuralRemovalCannotEraseAnEmptyMacroInvocation() throws Exception {
        var project = parse("#define EMPTY\nint main() { int unused = 1; return EMPTY 3; }\n");
        var variables = new ArrayList<VariableDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration n) { variables.add(n); }});
        var editor = new ProjectEditor(project);
        editor.remove(variables.getFirst());
        var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(editor.build(), Language.CPP));
        assertTrue(error.getMessage().contains("empty macro invocation"), error.getMessage());
    }
    @Test void unusedDefinitionsAndInactiveTextArePreserved() throws Exception {
        String source = "#define UNUSED 2\n#ifdef NEVER\nnot parsed syntax!\n#endif\nint main() { return 0; }\n";
        var project = parse(source);
        assertEquals(source, Cpg.sources(project, Language.CPP).get("main.cpp"));
    }
    @Test void foldedMacroExpressionsStillGenerateValidatedSource() throws Exception {
        Files.writeString(directory.resolve("main.cpp"), "#define S(x) ((x)*(x))\nint main() { return S(3); }\n");
        var project = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED));
        assertEquals("#define S(x) ((x)*(x))\nint main() { return 9; }\n", Cpg.sources(project, Language.CPP).get("main.cpp"));
    }
}
