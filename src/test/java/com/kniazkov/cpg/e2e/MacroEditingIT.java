/* Code Property Graph: preserve macro calls outside an edited expansion. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import static com.kniazkov.cpg.e2e.ExpressionSyntax.written;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MacroEditingIT {
    @TempDir Path temp;
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);
    private static final Path FIXTURE = Path.of("src/test/resources/cpp03/square");

    @Test void editOneOperandPreservesHeaderAndOtherInvocationThroughCliReparse() throws Exception {
        var project = Cpg.readCompilationDatabase(FIXTURE.resolve("compile_commands.json"), OPTIONS);
        var untouched = Cpg.sources(project, Language.CPP);
        for (String name : List.of("main.cpp", "second.cpp", "include/square.h"))
            assertEquals(Files.readString(FIXTURE.resolve("sources").resolve(name)), untouched.get(name));
        var edited = MacroEdits.squareRightOperand(project);
        var files = Cpg.sources(edited, Language.CPP);
        assertEquals(untouched.get("include/square.h"), files.get("include/square.h"));
        assertEquals(untouched.get("second.cpp"), files.get("second.cpp"));
        assertFalse(files.get("main.cpp").contains("SQUARE(a)"));
        assertTrue(files.get("second.cpp").contains("SQUARE(c)"));
        assertEquals(untouched, Cpg.sources(project, Language.CPP), "Original immutable snapshot is untouched");
        Path output = Files.createDirectory(temp.resolve("edited"));
        for (var file : files.entrySet()) {
            Path path = output.resolve("sources").resolve(file.getKey());
            Files.createDirectories(path.getParent()); Files.writeString(path, file.getValue());
        }
        Files.copy(FIXTURE.resolve("compile_commands.json"), output.resolve("compile_commands.json"));
        var cli = new CliSandbox(Files.createDirectory(temp.resolve("cli"))).directory(output);
        var run = cli.runDatabase(cli.input.resolve("compile_commands.json"), "--stage", "resolved").expectStatus(0);
        run.snapshot().containsNode("{\"kind\":\"MultiplyExpression\",\"left\":{\"kind\":\"ConversionExpression\",\"explicit\":false,\"operand\":{\"kind\":\"NameExpression\",\"name\":\"a\"}},\"right\":{\"kind\":\"ConversionExpression\",\"explicit\":false,\"operand\":{\"kind\":\"NameExpression\",\"name\":\"b\"}}}");
        run.snapshot().containsNode("{\"kind\":\"MultiplyExpression\",\"left\":{\"kind\":\"ConversionExpression\",\"explicit\":false,\"operand\":{\"name\":\"c\"}},\"right\":{\"kind\":\"ConversionExpression\",\"explicit\":false,\"operand\":{\"name\":\"c\"}}}");
        assertEquals(files.get("include/square.h"), Files.readString(run.work().resolve("generated/include/square.h")));
    }

    @Test void nestedMacroEditKeepsOuterPrecedenceAndUnrelatedInvocation() throws Exception {
        Path input = Files.createDirectory(temp.resolve("nested"));
        String header = "#define SQUARE(x) ((x) * (x))\n#define OUT(x) SQUARE(x)\n";
        Files.writeString(input.resolve("macros.h"), header);
        Files.writeString(input.resolve("main.cpp"), """
                #include "macros.h"
                int main() {
                    int a = 3;
                    int b = 5;
                    int changed = OUT(a) + 1;
                    int untouched = OUT(2);
                    return changed + untouched;
                }
                """);
        var project = Cpg.read(input, OPTIONS);
        var matches = new ArrayList<MultiplyExpression>();
        project.walk(new NodeVisitor() { @Override public void visit(MultiplyExpression n) {
            if (written(n.left()) instanceof NameExpression name && name.name().equals("a")) matches.add(n);
        }});
        assertEquals(1, matches.size());
        var editor = new ProjectEditor(project);
        editor.replace(written(matches.getFirst().right()), AddExpression.create()
                .setLeft(NameExpression.create().setName("b")).setRight(IntegerLiteral.create().setValue(1)).build());
        var files = Cpg.sources(editor.build(), Language.CPP);
        assertEquals(header, files.get("macros.h"));
        assertFalse(files.get("main.cpp").contains("OUT(a)"));
        assertTrue(files.get("main.cpp").contains("OUT(2)"));
        Path output = Files.createDirectory(temp.resolve("nested-generated"));
        for (var file : files.entrySet()) Files.writeString(output.resolve(file.getKey()), file.getValue());
        var reparsed = Cpg.read(output, OPTIONS);
        var found = new ArrayList<AddExpression>();
        reparsed.walk(new NodeVisitor() { @Override public void visit(AddExpression n) {
            if (written(n.left()) instanceof MultiplyExpression m && written(m.left()) instanceof NameExpression left
                    && left.name().equals("a") && written(m.right()) instanceof AddExpression right
                    && written(right.left()) instanceof NameExpression name && name.name().equals("b")
                    && written(right.right()) instanceof IntegerLiteral one && one.value() == 1
                    && written(n.right()) instanceof IntegerLiteral after && after.value() == 1) found.add(n);
        }});
        assertEquals(1, found.size(), "a * (b + 1) must remain inside the original + 1");
    }

    @Test void generationUsesCapturedSourceAfterInputsChangeOnDisk() throws Exception {
        var cli = new CliSandbox(temp).directory(FIXTURE);
        var project = Cpg.readCompilationDatabase(cli.input.resolve("compile_commands.json"), OPTIONS);
        var expected = Cpg.sources(project, Language.CPP);
        Files.writeString(cli.input.resolve("sources/include/square.h"), "#error changed after snapshot\n");
        Files.delete(cli.input.resolve("sources/second.cpp"));
        assertEquals(expected, Cpg.sources(project, Language.CPP));
        var edited = Cpg.sources(MacroEdits.squareRightOperand(project), Language.CPP);
        assertEquals(expected.get("include/square.h"), edited.get("include/square.h"));
        assertEquals(expected.get("second.cpp"), edited.get("second.cpp"));
    }
}
