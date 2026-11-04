/* Code Property Graph: Graphviz visualization of typed snapshots. MIT License. */
package com.kniazkov.cpg.visualization;

import com.kniazkov.cpg.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.IdentityHashMap;
import java.util.Map;

/** Exports syntax ownership and resolved references, using identity for DOT node IDs. */
public final class DotVisualizer {
    private DotVisualizer() {
    }

    /**
     * Traverses the selected subtree in source order. Solid edges are numbered child
     * positions (starting at zero). Dashed edges point to resolved declarations.
     * Targets outside a selected fragment receive a label without expanding their subtree.
     */
    public static String toDot(Node root) {
        Writer writer = new Writer();
        root.walk(writer);
        return writer.dot.append("}\n").toString();
    }

    /** Writes UTF-8 DOT source to the requested path. */
    public static void writeDot(Node root, Path output) throws IOException {
        Files.writeString(output, toDot(root), StandardCharsets.UTF_8);
    }

    /**
     * Runs the Graphviz dot executable from PATH and publishes its SVG on success.
     * A temporary source file avoids pipe deadlocks with large graphs. The renderer's
     * diagnostics are included in an IOException when it exits unsuccessfully.
     * @throws InterruptedException if interrupted while waiting for Graphviz
     * @throws IOException if the files or the renderer cannot be accessed, or rendering fails
     */
    public static void renderSvg(Node root, Path output) throws IOException, InterruptedException {
        Path directory = output.toAbsolutePath().getParent();
        Path source = Files.createTempFile(directory, "cpg-", ".dot");
        try {
            writeDot(root, source);
            Path svg = Files.createTempFile(directory, "cpg-", ".svg");
            try {
                render(source, svg);
                Files.move(svg, output, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(svg);
            }
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private static void render(Path source, Path svg) throws IOException, InterruptedException {
        Path diagnostics = Files.createTempFile(svg.getParent(), "cpg-", ".log");
        try {
            Process process = new ProcessBuilder("dot", "-Tsvg", "-o", svg.toString(), source.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(diagnostics.toFile())
                    .start();
            try {
                int status = process.waitFor();
                if (status != 0) {
                    throw new IOException("Graphviz exited with status " + status + ": "
                            + Files.readString(diagnostics));
                }
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor();
                }
            }
        } finally {
            Files.deleteIfExists(diagnostics);
        }
    }

    private static final class Writer implements NodeVisitor {
        private final Map<Node, Integer> ids = new IdentityHashMap<>();
        private final StringBuilder dot = new StringBuilder("digraph CPG {\n  node [shape=box];\n");

        @Override
        public void visit(Node node) {
            int from = id(node);
            int index = 0;
            for (Node child : node.children()) {
                int to = id(child);
                dot.append("  n").append(from).append(" -> n").append(to)
                        .append(" [label=\"").append(index++).append("\"];\n");
            }
            for (Node target : node.references()) {
                int to = id(target);
                dot.append("  n").append(from).append(" -> n").append(to)
                        .append(" [label=\"ref\", style=dashed, constraint=false];\n");
            }
        }

        private int id(Node node) {
            Integer known = ids.get(node);
            if (known != null) {
                return known;
            }
            int id = ids.size();
            ids.put(node, id);
            Label label = new Label();
            node.accept(label);
            String title = node.getClass().getSimpleName();
            if (!label.detail.isEmpty()) {
                title += "\n" + label.detail;
            }
            dot.append("  n").append(id).append(" [label=\"").append(escape(title)).append("\"];\n");
            return id;
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }

    private static final class Label implements NodeVisitor {
        private String detail = "";

        @Override
        public void visit(IntegerLiteral node) {
            detail = Integer.toString(node.value());
        }

        @Override
        public void visit(NameExpression node) {
            detail = node.name();
        }

        @Override
        public void visit(MemberExpression node) {
            detail = node.member();
        }

        @Override
        public void visit(CallableDeclaration node) {
            detail = node.name();
        }

        @Override
        public void visit(VariableDeclaration node) {
            detail = node.name();
        }

        @Override
        public void visit(ParameterDeclaration node) {
            detail = node.name();
        }

        @Override
        public void visit(ClassDeclaration node) {
            detail = node.name();
        }

        @Override
        public void visit(NamedType node) {
            detail = node.name();
        }

        @Override
        public void visit(IncludeDirective node) {
            detail = node.header();
        }

        @Override
        public void visit(SourceFile node) {
            detail = node.path();
        }

    }
}
