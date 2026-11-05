/* C++03 type syntax and immutable graph builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** A pointer to a member of an explicitly named owner type. */
public final class MemberPointerType extends TypeNode {
    private TypeNode pointee;
    private TypeNode ownerType;
    private MemberPointerType() {}
    public TypeNode pointee() { return pointee; }
    public TypeNode ownerType() { return ownerType; }
    @Override public List<Node> children() { return List.of(pointee, ownerType); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.pointee = context.edge(pointee, TypeNode.class);
        builder.ownerType = context.edge(ownerType, TypeNode.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<MemberPointerType> {
        private NodeBuilder<? extends TypeNode> pointee;
        private NodeBuilder<? extends TypeNode> ownerType;
        private Builder() { super(MemberPointerType.class); }
        public Optional<NodeBuilder<? extends TypeNode>> pointee() { return Optional.ofNullable(pointee); }
        public Builder setPointee(NodeBuilder<? extends TypeNode> value) { pointee = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends TypeNode>> ownerType() { return Optional.ofNullable(ownerType); }
        public Builder setOwnerType(NodeBuilder<? extends TypeNode> value) { ownerType = Objects.requireNonNull(value); return this; }
        @Override MemberPointerType newNode() { return new MemberPointerType(); }
        @Override void fillNode(MemberPointerType node, BuildContext context) {
            node.pointee = context.copy(pointee);
            node.ownerType = context.copy(ownerType);
        }
    }
}
