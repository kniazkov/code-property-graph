/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A written type name; no semantic type inference is implied. */
public final class NamedType extends TypeNode {
    private String name;

    private NamedType() {
    }

    public String name() {
        return name;
    }

    @Override
    public List<Node> children() {
        return List.of();
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
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<NamedType> {
        private String name;

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

        @Override
        NamedType newNode() {
            return new NamedType();
        }

        @Override
        void fillNode(NamedType node, BuildContext context) {
            node.name = requiredText(name, "NamedType.name");
        }
    }
}
