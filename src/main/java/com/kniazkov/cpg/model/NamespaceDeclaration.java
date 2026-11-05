/* C++03 declaration syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** One namespace opening; anonymous openings retain their own source identity. */
public final class NamespaceDeclaration extends Declaration {
    private String name;
    private boolean anonymous;
    private List<Declaration> members = List.of();
    private NamespaceDeclaration() {}
    public String name() { return name; }
    public boolean anonymous() { return anonymous; }
    public List<Declaration> members() { return members; }
    @Override public List<Node> children() { var result = new ArrayList<Node>();
        result.addAll(members);
        return List.copyOf(result); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(super.references());
        return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.name = name;
        builder.anonymous = anonymous;
        builder.members = context.copyAll(members, Declaration.class);
        return builder; }
    public static final class Builder extends NodeBuilder<NamespaceDeclaration> {
        private String name;
        private boolean anonymous;
        private List<NodeBuilder<? extends Declaration>> members = new ArrayList<>();
        private Builder() { super(NamespaceDeclaration.class); }
        public Optional<String> name() { return Optional.ofNullable(name); }
        public Builder setName(String value) { name = Objects.requireNonNull(value); return this; }
        public boolean anonymous() { return anonymous; }
        public Builder setAnonymous(boolean value) { anonymous = value; return this; }
        public List<NodeBuilder<? extends Declaration>> members() { return List.copyOf(members); }
        public Builder setMembers(List<? extends NodeBuilder<? extends Declaration>> value) { members = new ArrayList<>(List.copyOf(value)); return this; }
        public Builder addMember(NodeBuilder<? extends Declaration> value) { members.add(Objects.requireNonNull(value)); return this; }
        @Override NamespaceDeclaration newNode() { return new NamespaceDeclaration(); }
        @Override void fillNode(NamespaceDeclaration node, BuildContext context) {
            node.name = Objects.requireNonNull(name, "NamespaceDeclaration.name");
            node.anonymous = anonymous;
            node.members = context.copyAll(members);
        }
    }
}
