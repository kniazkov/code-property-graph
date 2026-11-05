/* C++03 declaration syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** A written using-declaration and the declaration it introduces. */
public final class UsingDeclaration extends Declaration {
    private String qualifiedName;
    private Declaration target;
    private UsingDeclaration() {}
    public String qualifiedName() { return qualifiedName; }
    public Optional<Declaration> target() { return Optional.ofNullable(target); }
    @Override public List<Node> children() { var result = new ArrayList<Node>();
        return List.copyOf(result); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(super.references());
        if (target != null) result.add(target);
        return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.qualifiedName = qualifiedName;
        builder.target = target == null ? null : context.edge(target, Declaration.class);
        return builder; }
    public static final class Builder extends NodeBuilder<UsingDeclaration> {
        private String qualifiedName;
        private NodeBuilder<? extends Declaration> target;
        private Builder() { super(UsingDeclaration.class); }
        public Optional<String> qualifiedName() { return Optional.ofNullable(qualifiedName); }
        public Builder setQualifiedName(String value) { qualifiedName = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends Declaration>> target() { return Optional.ofNullable(target); }
        public Builder setTarget(NodeBuilder<? extends Declaration> value) { target = Objects.requireNonNull(value); return this; }
        public Builder clearTarget() { target = null; return this; }
        @Override UsingDeclaration newNode() { return new UsingDeclaration(); }
        @Override void fillNode(UsingDeclaration node, BuildContext context) {
            node.qualifiedName = Objects.requireNonNull(qualifiedName, "UsingDeclaration.qualifiedName");
            node.target = target == null ? null : context.copy(target);
        }
    }
}
