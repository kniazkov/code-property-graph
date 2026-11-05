/* C++03 base class syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** A written direct base. Construction semantics are independent of initializer spelling order. */
public final class BaseSpecifier extends Node {
    private TypeNode type;
    private String access = "private";
    private boolean virtual;
    private BaseSpecifier() {}
    public TypeNode type() { return type; }
    public String access() { return access; }
    public boolean isVirtual() { return virtual; }
    @Override public List<Node> children() { return List.of(type); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) { var b = new Builder(); context.register(this, b); b.type = context.edge(type, TypeNode.class); b.access = access; b.virtual = virtual; return b; }
    public static final class Builder extends NodeBuilder<BaseSpecifier> {
        private NodeBuilder<? extends TypeNode> type;
        private String access = "private";
        private boolean virtual;
        private Builder() { super(BaseSpecifier.class); }
        public Builder setType(NodeBuilder<? extends TypeNode> value) { type = Objects.requireNonNull(value); return this; }
        public Builder setBaseAccess(String value) { if (!Set.of("public", "private", "protected").contains(value)) throw new IllegalArgumentException("Invalid base access"); access = value; return this; }
        public Builder setVirtual(boolean value) { virtual = value; return this; }
        @Override BaseSpecifier newNode() { return new BaseSpecifier(); }
        @Override void fillNode(BaseSpecifier node, BuildContext context) { node.type = context.copy(Objects.requireNonNull(type)); node.access = access; node.virtual = virtual; }
    }
}
