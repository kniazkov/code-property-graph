/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A source file and its ordered top-level items. */
public final class SourceFile extends Node {
    private String path;
    private Language language;
    private List<Node> items = List.of();
    private List<ParseProblem> parseProblems = List.of();
    private SourceRange stoppedAt;
    private boolean containsErrorExpression;

    /** Persistent syntax failures, unlike derived analysis findings. */
    public List<ParseProblem> parseProblems() { return parseProblems; }
    /** A zero-length physical position where reliable parsing stopped. */
    public Optional<SourceRange> stoppedAt() { return Optional.ofNullable(stoppedAt); }
    public boolean hasErrors() { return stoppedAt != null || !parseProblems.isEmpty() || containsErrorExpression; }

    private SourceFile() {
    }

    /** Configuration identity, absent for sources parsed without compilation inputs. */
    public Optional<String> variantId() {
        return provenance().map(TokenProvenance::unit).map(Preprocessing::configuration)
                .map(com.kniazkov.cpg.build.Build.TranslationUnit::variantId);
    }

    public String path() {
        return path;
    }

    public Language language() {
        return language;
    }

    public List<Node> items() {
        return items;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.addAll(items);
        return List.copyOf(children);
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }

    public static Builder create() {
        return new Builder();
    }

    @Override
    public Builder edit() {
        return new EditContext().edit(this, Builder.class);
    }

    @Override
    Builder copyBuilder(EditContext context) {
        Builder builder = new Builder();
        context.register(this, builder);
        builder.path = path;
        builder.language = language;
        builder.parseProblems = new ArrayList<>(parseProblems);
        builder.stoppedAt = stoppedAt;
        builder.items = context.copyAll(items, Node.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<SourceFile> {
        private String path;
        private Language language = Language.JAVA;
        private List<ParseProblem> parseProblems = new ArrayList<>();
        private SourceRange stoppedAt;

        public List<ParseProblem> parseProblems() { return List.copyOf(parseProblems); }
        public Builder setParseProblems(List<ParseProblem> values) { parseProblems = new ArrayList<>(List.copyOf(values)); return this; }
        public Builder addParseProblem(ParseProblem value) { parseProblems.add(Objects.requireNonNull(value)); return this; }
        public Builder setStoppedAt(SourceRange value) {
            Objects.requireNonNull(value);
            if (!value.start().equals(value.end())) throw new IllegalArgumentException("Stop position must be zero length");
            stoppedAt = value;
            return this;
        }
        private List<NodeBuilder<? extends Node>> items = new ArrayList<>();

        private Builder() {
            super(SourceFile.class);
        }

        public Optional<String> path() {
            return Optional.ofNullable(path);
        }

        public Language language() {
            return language;
        }

        public List<NodeBuilder<? extends Node>> items() {
            return List.copyOf(items);
        }

        public Builder setPath(String path) {
            this.path = Objects.requireNonNull(path);
            return this;
        }

        public Builder setLanguage(Language language) {
            this.language = Objects.requireNonNull(language);
            return this;
        }

        public Builder setItems(List<? extends NodeBuilder<? extends Node>> items) {
            this.items = new ArrayList<>(List.copyOf(items));
            return this;
        }

        public Builder addItem(NodeBuilder<? extends Node> value) {
            items.add(Objects.requireNonNull(value));
            return this;
        }

        @Override
        SourceFile newNode() {
            return new SourceFile();
        }

        @Override
        void fillNode(SourceFile node, BuildContext context) {
            node.path = requiredText(path, "SourceFile.path");
            node.language = language;
            node.parseProblems = List.copyOf(parseProblems);
            node.stoppedAt = stoppedAt;
            node.items = context.copyAll(items);
            node.walk(new NodeVisitor() {
                @Override public void visit(ErrorExpression error) { node.containsErrorExpression = true; }
            });
            for (Node item : node.items) {
                if (!(item instanceof Declaration) && !(item instanceof IncludeDirective)) {
                    throw new IllegalStateException("File item must be a declaration or include");
                }
            }
        }
    }
}
