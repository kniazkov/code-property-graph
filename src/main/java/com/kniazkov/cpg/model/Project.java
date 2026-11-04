/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Root containing source files. */
public final class Project extends Node {
    private List<SourceFile> files = List.of();
    private com.kniazkov.cpg.build.Build build;

    public java.util.Optional<com.kniazkov.cpg.build.Build> build() { return java.util.Optional.ofNullable(build); }
    public java.util.Optional<com.kniazkov.cpg.build.Build.Composition> composition() {
        return build().map(com.kniazkov.cpg.build.Build::composition);
    }
    public List<SourceFile> variants(java.nio.file.Path path) {
        var normalized = path.toAbsolutePath().normalize();
        return files.stream().filter(f -> java.nio.file.Path.of(f.range().map(SourceRange::file)
                .orElse(f.path())).toAbsolutePath().normalize().equals(normalized)).toList();
    }
    /** Selects owned variants, clears old call edges and recomputes resolution and flow analyses. */
    public Project selectBuild(com.kniazkov.cpg.build.Build.Composition composition) {
        if (build == null) throw new IllegalStateException("Project has no compilation build");
        var selectedBuild = new com.kniazkov.cpg.build.Build(build.units(), composition);
        var selected = files.stream().filter(f -> f.variantId().map(composition.variantIds()::contains).orElse(false)).toList();
        if (selected.size() != composition.variantIds().size())
            throw new IllegalArgumentException("Requested variant is not present in this snapshot");
        var context = new EditContext();
        var root = context.edit(this, Builder.class);
        walk(new NodeVisitor() {
            @Override public void visit(CallExpression call) { context.edit(call, CallExpression.Builder.class).clearTarget(); }
        });
        root.setFiles(context.copyAll(selected, SourceFile.class)).setBuild(selectedBuild);
        return com.kniazkov.cpg.analysis.UnreadWrites.analyze(
                com.kniazkov.cpg.analysis.NameResolver.resolve(root.build()));
    }

    private List<FunctionFlow> flows = List.of();

    /** Returns flow analyses associated with this particular syntax snapshot. */
    public List<FunctionFlow> flows() { return flows; }

    /** Shares immutable syntax and attaches complete per-callable analysis results. */
    public Project withFlows(List<FunctionFlow> results) {
        var copy = new Project();
        copy.files = files;
        copy.build = build;
        copy.flows = List.copyOf(results);
        var owners = new java.util.HashSet<CallableDeclaration>();
        walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration callable) {
                if (callable.body().isPresent()) owners.add(callable);
            }
        });
        for (FunctionFlow flow : copy.flows) {
            if (!owners.remove(flow.entry()))
                throw new IllegalArgumentException("Foreign or duplicate function flow");
        }
        if (!owners.isEmpty()) throw new IllegalArgumentException("Missing function flow");
        return copy;
    }

    /**
     * Replaces the findings of one rule. When annotations change, copies the snapshot
     * privately and reconnects its flow edges to the copied nodes before publication.
     * Ordinary edit/build deliberately discards findings and flow information.
     */
    public Project withDiagnostics(String rule, java.util.Map<Node, Diagnostic> findingsByNode) {
        var pending = new java.util.IdentityHashMap<>(findingsByNode);
        var annotations = new java.util.IdentityHashMap<Node, List<Diagnostic>>();
        var changed = new java.util.HashSet<Node>();
        walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (node.diagnostics().isEmpty() && !pending.containsKey(node)) return;
                var updated = new ArrayList<>(node.diagnostics());
                updated.removeIf(d -> d.code().equals(rule));
                Diagnostic finding = pending.remove(node);
                if (finding != null) {
                    if (!finding.code().equals(rule)) throw new IllegalArgumentException("Unexpected rule code");
                    updated.add(finding);
                }
                if (!updated.equals(node.diagnostics())) changed.add(node);
                if (!updated.isEmpty()) annotations.put(node, List.copyOf(updated));
            }
        });
        if (!pending.isEmpty()) throw new IllegalArgumentException("Finding outside this project");
        if (changed.isEmpty()) return this;
        var edit = new EditContext();
        var build = new BuildContext();
        Project copy = build.build(edit.edit(this, Builder.class));
        java.util.function.Function<Node, Node> copied = n -> build.copy(edit.copy(n));
        annotations.forEach((node, list) -> copied.apply(node).findings = list);
        copy.flows = flows.stream().map(flow -> {
            CallableDeclaration entry = (CallableDeclaration) copied.apply(flow.entry());
            var control = flow.control().stream().map(e -> new FunctionFlow.ControlEdge(
                    copied.apply((Node)e.from()), copied.apply((Node)e.to()), e.kind())).toList();
            var data = flow.data().stream().map(e -> new FunctionFlow.DataEdge(
                    copied.apply(e.from()), copied.apply(e.to()), e.kind())).toList();
            return new FunctionFlow(entry, entry.exit(), control, data);
        }).toList();
        return copy;
    }

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
        builder.files = context.copyAll(files, SourceFile.class);
        builder.build = build;
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<Project> {
        private List<NodeBuilder<? extends SourceFile>> files = new ArrayList<>();
        private com.kniazkov.cpg.build.Build build;
        public Builder setBuild(com.kniazkov.cpg.build.Build value) { build = value; return this; }

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
            node.build = build;
        }
    }
}
