/* Structured Doxygen entries with context-specific semantic bindings. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** One ordered tag in a physical comment. Its owner is the comment, never its targets. */
public final class DocumentationEntry extends Node {
    public enum Kind { BRIEF, PARAM, TPARAM, RETURN }
    /** A declaration occurrence identifies the translation unit and include context.
     * A null target retains an unresolved named tag without inventing a declaration. */
    public record Binding(CallableDeclaration declaration, Declaration target) {
        public Binding { Objects.requireNonNull(declaration); }
    }
    private Kind kind;
    private String name, description;
    private SourceRange tagRange, nameRange;
    private List<Binding> bindings = List.of();
    private DocumentationEntry() {}
    public Kind kind() { return kind; }
    public String name() { return name; }
    public String description() { return description; }
    public Optional<SourceRange> tagRange() { return Optional.ofNullable(tagRange); }
    public Optional<SourceRange> nameRange() { return Optional.ofNullable(nameRange); }
    public List<Binding> bindings() { return bindings; }
    @Override public List<Node> children() { return List.of(); }
    @Override public List<Node> references() {
        var result = new ArrayList<Node>();
        for (var binding : bindings) {
            if (!result.contains(binding.declaration())) result.add(binding.declaration());
            if (binding.target() != null && !result.contains(binding.target())) result.add(binding.target());
        }
        return List.copyOf(result);
    }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.kind = kind; builder.name = name; builder.description = description;
        builder.tagRange = tagRange; builder.nameRange = nameRange;
        for (var binding : bindings) builder.addBinding(context.edge(binding.declaration(), CallableDeclaration.class),
                binding.target() == null ? null : context.edge(binding.target(), Declaration.class));
        return builder;
    }
    public static final class Builder extends NodeBuilder<DocumentationEntry> {
        private Kind kind;
        private String name = "", description = "";
        private SourceRange tagRange, nameRange;
        private record Link(NodeBuilder<? extends CallableDeclaration> declaration, NodeBuilder<? extends Declaration> target) {}
        private List<Link> bindings = new ArrayList<>();
        private Builder() { super(DocumentationEntry.class); }
        public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
        public Builder setName(String value) { name = Objects.requireNonNull(value); return this; }
        public Builder setDescription(String value) { description = Objects.requireNonNull(value); return this; }
        public Builder setTagRange(SourceRange value) { tagRange = Objects.requireNonNull(value); return this; }
        public Builder setNameRange(SourceRange value) { nameRange = Objects.requireNonNull(value); return this; }
        public Builder clearBindings() { bindings.clear(); return this; }
        public Builder addBinding(NodeBuilder<? extends CallableDeclaration> declaration, NodeBuilder<? extends Declaration> target) {
            bindings.add(new Link(Objects.requireNonNull(declaration), target)); return this;
        }
        @Override DocumentationEntry newNode() { return new DocumentationEntry(); }
        @Override void fillNode(DocumentationEntry node, BuildContext context) {
            node.kind = Objects.requireNonNull(kind); node.name = name; node.description = description;
            node.tagRange = tagRange; node.nameRange = nameRange;
            node.bindings = bindings.stream().map(link -> new Binding(context.copy(link.declaration()),
                    link.target() == null ? null : context.copy(link.target()))).toList();
            if ((kind == Kind.PARAM || kind == Kind.TPARAM) && node.bindings.stream().anyMatch(b -> b.target() == null))
                node.findings = List.of(new Diagnostic("doxygen.unresolved-parameter", Diagnostic.Severity.WARNING,
                        "No " + (kind == Kind.TPARAM ? "template " : "") + "parameter named '" + name + "' in documented declaration"));
        }
    }
}
