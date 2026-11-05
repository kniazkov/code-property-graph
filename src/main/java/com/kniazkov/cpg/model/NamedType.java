/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A written type name; no semantic type inference is implied. */
public final class NamedType extends TypeNode {
    private String name;
    private TypedefDeclaration alias;

    private NamedType() {
    }

    public String name() {
        return name;
    }

    public Optional<FundamentalKind> fundamentalKind() { return alias == null ? FundamentalKind.fromSpelling(name) : Optional.empty(); }

    /** Immediate written alias link; following links preserves the complete typedef chain. */
    public Optional<TypedefDeclaration> alias() { return Optional.ofNullable(alias); }

    @Override public List<Node> references() { return alias == null ? List.of() : List.of(alias); }

    @Override
    public List<Node> children() {
        return List.of();
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
        builder.name = name;
        builder.alias = alias == null ? null : context.edge(alias, TypedefDeclaration.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<NamedType> {
        private String name;
        private NodeBuilder<? extends TypedefDeclaration> alias;

        private Builder() {
            super(NamedType.class);
        }

        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        public Builder setName(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Optional<NodeBuilder<? extends TypedefDeclaration>> alias() { return Optional.ofNullable(alias); }
        public Builder setAlias(NodeBuilder<? extends TypedefDeclaration> value) { alias = Objects.requireNonNull(value); return this; }
        public Builder clearAlias() { alias = null; return this; }

        @Override
        NamedType newNode() {
            return new NamedType();
        }

        @Override
        void fillNode(NamedType node, BuildContext context) {
            node.name = requiredText(name, "NamedType.name");
            node.alias = alias == null ? null : context.copy(alias);
        }
    }
}
