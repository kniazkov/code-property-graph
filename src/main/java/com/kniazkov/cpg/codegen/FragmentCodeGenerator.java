package com.kniazkov.cpg.codegen;

import com.kniazkov.cpg.ast.Fragment;
import com.kniazkov.cpg.ast.Node;

import java.util.List;
import java.util.Objects;

/**
 * A baseline generator that reconstructs source code from node fragments.
 *
 * <p>If a node has a source fragment, its exact text is written. Otherwise the
 * generator recursively writes the node's children separated by a configurable
 * separator. This is useful for round-trip tests of parsed trees and as a
 * fallback for languages that do not yet have a dedicated generator.</p>
 *
 * <p>A language-specific generator should override this behavior when it needs
 * to render modified or synthetic trees.</p>
 */
public final class FragmentCodeGenerator implements CodeGenerator {

    private final String language;
    private final String childSeparator;

    /**
     * Creates a generator with a single space between synthetic children.
     *
     * @param language the language identifier
     * @throws NullPointerException if {@code language} is {@code null}
     */
    public FragmentCodeGenerator(String language) {
        this(language, " ");
    }

    /**
     * Creates a generator.
     *
     * @param language the language identifier
     * @param childSeparator the separator between children of a synthetic node
     * @throws NullPointerException if any argument is {@code null}
     */
    public FragmentCodeGenerator(String language, String childSeparator) {
        this.language = Objects.requireNonNull(language, "language");
        this.childSeparator = Objects.requireNonNull(childSeparator, "childSeparator");
    }

    @Override
    public String language() {
        return language;
    }

    @Override
    public void generate(Node root, CodeWriter writer) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(writer, "writer");
        render(root, writer);
    }

    private void render(Node node, CodeWriter writer) {
        Fragment fragment = node.fragment();
        if (fragment != null) {
            writer.write(fragment.code());
            return;
        }

        List<Node> children = node.children();
        for (int index = 0; index < children.size(); index++) {
            if (index > 0) {
                writer.write(childSeparator);
            }
            render(children.get(index), writer);
        }
    }
}
