package com.kniazkov.cpg.codegen;

import com.kniazkov.cpg.tree.Node;

import java.util.Objects;

/**
 * Converts a syntax tree or subtree back to source code.
 *
 * <p>A generator is language-specific. Implementations for different languages
 * are registered in a {@link CodeGeneratorRegistry} and selected by language
 * identifier.</p>
 */
public interface CodeGenerator {

    /**
     * Returns the language identifier handled by this generator.
     *
     * @return the language identifier, never {@code null}
     */
    String language();

    /**
     * Writes the source code for the given tree or subtree.
     *
     * @param root the root of the tree or subtree
     * @param writer the output writer
     * @throws NullPointerException if any argument is {@code null}
     */
    void generate(Node root, CodeWriter writer);

    /**
     * Generates source code as a string.
     *
     * @param root the root of the tree or subtree
     * @return the generated source code
     * @throws NullPointerException if {@code root} is {@code null}
     */
    default String generate(Node root) {
        Objects.requireNonNull(root, "root");
        TextCodeWriter writer = new TextCodeWriter();
        generate(root, writer);
        return writer.code();
    }
}
