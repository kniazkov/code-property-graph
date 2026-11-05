/* C++03 declaration syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** A written namespace alias and its resolved target. */
public final class NamespaceAliasDeclaration extends Declaration {
    private String name;
    private String targetName;
    private NamespaceDeclaration target;
    private NamespaceAliasDeclaration() {}
    public String name() { return name; }
    public String targetName() { return targetName; }
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
        builder.name = name;
        builder.targetName = targetName;
        builder.target = target == null ? null : context.edge(target, NamespaceDeclaration.class);
        return builder; }
    public static final class Builder extends NodeBuilder<NamespaceAliasDeclaration> {
        private String name;
        private String targetName;
        private NodeBuilder<? extends NamespaceDeclaration> target;
        private Builder() { super(NamespaceAliasDeclaration.class); }
        public Optional<String> name() { return Optional.ofNullable(name); }
        public Builder setName(String value) { name = Objects.requireNonNull(value); return this; }
        public Optional<String> targetName() { return Optional.ofNullable(targetName); }
        public Builder setTargetName(String value) { targetName = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends NamespaceDeclaration>> target() { return Optional.ofNullable(target); }
        public Builder setTarget(NodeBuilder<? extends NamespaceDeclaration> value) { target = Objects.requireNonNull(value); return this; }
        public Builder clearTarget() { target = null; return this; }
        @Override NamespaceAliasDeclaration newNode() { return new NamespaceAliasDeclaration(); }
        @Override void fillNode(NamespaceAliasDeclaration node, BuildContext context) {
            node.name = Objects.requireNonNull(name, "NamespaceAliasDeclaration.name");
            node.targetName = Objects.requireNonNull(targetName, "NamespaceAliasDeclaration.targetName");
            node.target = target == null ? null : context.copy(target);
        }
    }
}
