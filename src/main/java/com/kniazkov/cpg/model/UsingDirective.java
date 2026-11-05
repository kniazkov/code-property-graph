/* C++03 declaration syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** One using-namespace directive shared by every lookup that traverses it. */
public final class UsingDirective extends Declaration {
    private String namespaceName;
    private NamespaceDeclaration target;
    private UsingDirective() {}
    public String namespaceName() { return namespaceName; }
    public Optional<NamespaceDeclaration> target() { return Optional.ofNullable(target); }
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
        builder.namespaceName = namespaceName;
        builder.target = target == null ? null : context.edge(target, NamespaceDeclaration.class);
        return builder; }
    public static final class Builder extends NodeBuilder<UsingDirective> {
        private String namespaceName;
        private NodeBuilder<? extends NamespaceDeclaration> target;
        private Builder() { super(UsingDirective.class); }
        public Optional<String> namespaceName() { return Optional.ofNullable(namespaceName); }
        public Builder setNamespaceName(String value) { namespaceName = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends NamespaceDeclaration>> target() { return Optional.ofNullable(target); }
        public Builder setTarget(NodeBuilder<? extends NamespaceDeclaration> value) { target = Objects.requireNonNull(value); return this; }
        public Builder clearTarget() { target = null; return this; }
        @Override UsingDirective newNode() { return new UsingDirective(); }
        @Override void fillNode(UsingDirective node, BuildContext context) {
            node.namespaceName = Objects.requireNonNull(namespaceName, "UsingDirective.namespaceName");
            node.target = target == null ? null : context.copy(target);
        }
    }
}
