/* C++03 type syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Written const and volatile qualifiers on a type. */
public final class QualifiedType extends TypeNode {
    private TypeNode baseType;
    private boolean isConst, isVolatile;
    private QualifiedType() {}
    public TypeNode baseType() { return baseType; }
    public boolean isConst() { return isConst; }
    public boolean isVolatile() { return isVolatile; }
    @Override public List<Node> children() { return List.of(baseType); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.baseType = context.edge(baseType, TypeNode.class);
        builder.isConst = isConst; builder.isVolatile = isVolatile;
        return builder;
    }
    public static final class Builder extends NodeBuilder<QualifiedType> {
        private NodeBuilder<? extends TypeNode> baseType;
        private boolean isConst, isVolatile;
        private Builder() { super(QualifiedType.class); }
        public Optional<NodeBuilder<? extends TypeNode>> baseType() { return Optional.ofNullable(baseType); }
        public Builder setBaseType(NodeBuilder<? extends TypeNode> value) { baseType = Objects.requireNonNull(value); return this; }
        public boolean isConst() { return isConst; }
        public boolean isVolatile() { return isVolatile; }
        public Builder setConst(boolean value) { isConst = value; return this; }
        public Builder setVolatile(boolean value) { isVolatile = value; return this; }
        @Override QualifiedType newNode() { return new QualifiedType(); }
        @Override void fillNode(QualifiedType node, BuildContext context) {
            node.baseType = context.copy(baseType);
            if (!isConst && !isVolatile) throw new IllegalStateException("QualifiedType requires a qualifier");
            node.isConst = isConst; node.isVolatile = isVolatile;
        }
    }
}
