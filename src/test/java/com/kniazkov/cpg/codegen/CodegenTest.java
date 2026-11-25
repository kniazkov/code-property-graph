package com.kniazkov.cpg.codegen;

import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Position;
import com.kniazkov.cpg.tree.Source;
import com.kniazkov.cpg.tree.TestNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodegenTest {

    private static Source source(String text) {
        return (start, end) -> text.substring(start, end);
    }

    private static Fragment fragment(String text) {
        return new Fragment(
                source(text),
                new Position(0, 1, 1),
                new Position(text.length(), 1, text.length() + 1));
    }

    @Test
    void textWriterFormatsIndentationAndNewlines() {
        TextCodeWriter writer = new TextCodeWriter();

        writer.write("a").space().write("b").newline()
                .indent().write("c").unindent().newline()
                .write("d");

        assertEquals("a b\n    c\nd", writer.code());
    }

    @Test
    void textWriterSupportsCustomFormatting() {
        TextCodeWriter writer = new TextCodeWriter("\t", "\r\n");

        writer.write("a\nb");

        assertEquals("a\r\nb", writer.code());
    }

    @Test
    void textWriterNormalizesCarriageReturns() {
        TextCodeWriter writer = new TextCodeWriter();

        writer.write("a\r\nb\rc\nd\r");

        assertEquals("a\nb\nc\nd\n", writer.code());
    }

    @Test
    void textWriterHandlesEmptyAndLineStartOperations() {
        TextCodeWriter writer = new TextCodeWriter();

        writer.write("");
        writer.newline();
        writer.indent().indent().write("x").unindent().unindent();
        writer.space();

        assertEquals("\n        x ", writer.code());
    }

    @Test
    void textWriterRejectsInvalidArgumentsAndUnderflow() {
        TextCodeWriter writer = new TextCodeWriter();

        assertThrows(NullPointerException.class, () -> writer.write(null));
        assertThrows(NullPointerException.class, () -> new TextCodeWriter(null, "\n"));
        assertThrows(NullPointerException.class, () -> new TextCodeWriter("    ", null));
        assertThrows(IllegalStateException.class, writer::unindent);
    }

    @Test
    void codeGeneratorDefaultBuildsString() {
        CodeGenerator generator = new CodeGenerator() {
            @Override
            public String language() {
                return "test";
            }

            @Override
            public void generate(Node root, CodeWriter writer) {
                writer.write(root.name());
            }
        };

        assertEquals("TestNode", generator.generate(TestNode.leaf(fragment("x"))));
        assertThrows(NullPointerException.class, () -> generator.generate(null));
    }

    @Test
    void registryRegistersFindsAndGeneratesCaseInsensitively() {
        FragmentCodeGenerator generator = new FragmentCodeGenerator(Languages.CPP03);
        CodeGeneratorRegistry registry = new CodeGeneratorRegistry().register(generator);
        Node node = TestNode.leaf(fragment("x"));

        assertSame(generator, registry.find("CPP03").orElseThrow());
        assertSame(generator, registry.require("cpp03"));
        assertEquals("x", registry.generate("Cpp03", node));
        assertTrue(registry.find("java").isEmpty());
    }

    @Test
    void registryRejectsDuplicatesAndNulls() {
        CodeGeneratorRegistry registry = new CodeGeneratorRegistry()
                .register(new FragmentCodeGenerator("x"));

        assertThrows(NullPointerException.class, () -> registry.register(null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new FragmentCodeGenerator("X")));
        assertThrows(NullPointerException.class, () -> registry.find(null));
        assertThrows(NullPointerException.class, () -> registry.require(null));
        assertThrows(IllegalArgumentException.class, () -> registry.require("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.generate("missing", TestNode.leaf(fragment("x"))));
    }

    @Test
    void fragmentGeneratorUsesExactFragmentText() {
        FragmentCodeGenerator generator = new FragmentCodeGenerator("x");
        Node node = TestNode.leaf(fragment("abc"));

        assertEquals("x", generator.language());
        assertEquals("abc", generator.generate(node));
    }

    @Test
    void fragmentGeneratorJoinsSyntheticChildren() {
        FragmentCodeGenerator generator = new FragmentCodeGenerator("x");
        Node node = new TestNode(
                null,
                List.of(TestNode.leaf(fragment("a")), TestNode.leaf(fragment("b"))),
                List.of());

        assertEquals("a b", generator.generate(node));
    }

    @Test
    void fragmentGeneratorSupportsCustomSeparatorAndEmptySyntheticNode() {
        FragmentCodeGenerator generator = new FragmentCodeGenerator("x", ",");
        Node node = new TestNode(
                null,
                List.of(TestNode.leaf(fragment("a")), TestNode.leaf(fragment("b"))),
                List.of());
        Node empty = new TestNode(null, List.of(), List.of());

        assertEquals("a,b", generator.generate(node));
        assertEquals("", generator.generate(empty));
    }

    @Test
    void fragmentGeneratorRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new FragmentCodeGenerator(null));
        assertThrows(NullPointerException.class, () -> new FragmentCodeGenerator("x", null));

        FragmentCodeGenerator generator = new FragmentCodeGenerator("x");
        TextCodeWriter writer = new TextCodeWriter();

        assertThrows(NullPointerException.class, () -> generator.generate(null, writer));
        assertThrows(NullPointerException.class,
                () -> generator.generate(TestNode.leaf(fragment("x")), null));
    }

    @Test
    void languageConstantsAreStable() {
        assertEquals("cpp03", Languages.CPP03);
        assertEquals("c", Languages.C);
        assertEquals("java", Languages.JAVA);
        assertFalse(Languages.CPP03.isEmpty());
    }
}

