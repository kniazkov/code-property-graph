/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** An include directive without expansion of its header. */
public final class IncludeDirective extends Node {
    private String header;
    private boolean system;

    private IncludeDirective() {
    }

    public String header() {
        return header;
    }

    public boolean system() {
        return system;
    }

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
        builder.header = header;
        builder.system = system;
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<IncludeDirective> {
        private String header;
        private boolean system = true;

        private Builder() {
            super(IncludeDirective.class);
        }

        public Optional<String> header() {
            return Optional.ofNullable(header);
        }

        public boolean system() {
            return system;
        }

        public Builder setHeader(String header) {
            this.header = Objects.requireNonNull(header);
            return this;
        }

        public Builder setSystem(boolean system) {
            this.system = system;
            return this;
        }

        @Override
        IncludeDirective newNode() {
            return new IncludeDirective();
        }

        @Override
        void fillNode(IncludeDirective node, BuildContext context) {
            node.header = requiredText(header, "IncludeDirective.header");
            node.system = system;
        }
    }
}
