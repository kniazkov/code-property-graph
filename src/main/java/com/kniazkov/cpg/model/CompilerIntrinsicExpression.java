/* Typed CPG model: explicitly selected compiler value intrinsics. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;

/** A closed compiler-supplied value, not a call to a library declaration.
 * Non-finite values remain symbolic: no finite decimal or integer stands in for them. */
public final class CompilerIntrinsicExpression extends Expression {
    /** Only explicitly modelled semantics may be represented by this leaf. */
    public enum Kind {
        DOUBLE_POSITIVE_INFINITY
    }

    private Kind kind;
    private CompilerIntrinsicExpression() { }

    public Kind kind() { return kind; }
    public String cppType() { return cppType(kind); }
    /** Identifies the selected compiler emission contract, independently of the value kind. */
    public String emission() { return emission(kind); }

    private static String cppType(Kind kind) {
        return switch (kind) {
            case DOUBLE_POSITIVE_INFINITY -> "double";
        };
    }
    private static String emission(Kind kind) {
        return switch (kind) {
            case DOUBLE_POSITIVE_INFINITY -> "gcc-clang-builtin-huge-val";
        };
    }

    @Override public List<Node> children() { return List.of(); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        Builder builder = new Builder(); context.register(this, builder);
        builder.kind = kind;
        return builder;
    }

    public static final class Builder extends NodeBuilder<CompilerIntrinsicExpression> {
        private Kind kind = Kind.DOUBLE_POSITIVE_INFINITY;
        private Builder() { super(CompilerIntrinsicExpression.class); }
        public Kind kind() { return kind; }
        public String cppType() { return CompilerIntrinsicExpression.cppType(kind); }
        public String emission() { return CompilerIntrinsicExpression.emission(kind); }
        public Builder setKind(Kind value) {
            clearExpressionInfo(); kind = Objects.requireNonNull(value); return this;
        }
        @Override CompilerIntrinsicExpression newNode() { return new CompilerIntrinsicExpression(); }
        @Override void fillNode(CompilerIntrinsicExpression node, BuildContext context) { node.kind = kind; }
    }
}
