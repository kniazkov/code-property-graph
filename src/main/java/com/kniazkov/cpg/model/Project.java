/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Root containing source files. */
public final class Project extends Node {
    private List<SourceFile> files = List.of();

    private Project() {
    }

    public List<SourceFile> files() {
        return files;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.addAll(files);
        return List.copyOf(children);
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
        builder.files = context.copyAll(files);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<Project> {
        private List<NodeBuilder<? extends SourceFile>> files = new ArrayList<>();

        private Builder() {
            super(Project.class);
        }

        public List<NodeBuilder<? extends SourceFile>> files() {
            return List.copyOf(files);
        }

        public Builder setFiles(List<? extends NodeBuilder<? extends SourceFile>> files) {
            this.files = new ArrayList<>(List.copyOf(files));
            return this;
        }

        public Builder addFile(NodeBuilder<? extends SourceFile> value) {
            files.add(Objects.requireNonNull(value));
            return this;
        }

        @Override
        Project newNode() {
            return new Project();
        }

        @Override
        void fillNode(Project node, BuildContext context) {
            node.files = context.copyAll(files);
        }
    }
}
