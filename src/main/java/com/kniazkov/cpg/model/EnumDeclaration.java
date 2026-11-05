/* C++03 declaration syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** An unscoped C++03 enumeration with source-ordered enumerators. */
public final class EnumDeclaration extends Declaration {
    private String name;
    private List<EnumeratorDeclaration> enumerators = List.of();
    private EnumDeclaration() {}
    public String name() { return name; }
    public List<EnumeratorDeclaration> enumerators() { return enumerators; }
    @Override public List<Node> children() { var result = new ArrayList<Node>();
        result.addAll(enumerators);
        return List.copyOf(result); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(super.references());
        return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.name = name;
        builder.enumerators = context.copyAll(enumerators, EnumeratorDeclaration.class);
        return builder; }
    public static final class Builder extends NodeBuilder<EnumDeclaration> {
        private String name;
        private List<NodeBuilder<? extends EnumeratorDeclaration>> enumerators = new ArrayList<>();
        private Builder() { super(EnumDeclaration.class); }
        public Optional<String> name() { return Optional.ofNullable(name); }
        public Builder setName(String value) { name = Objects.requireNonNull(value); return this; }
        public List<NodeBuilder<? extends EnumeratorDeclaration>> enumerators() { return List.copyOf(enumerators); }
        public Builder setEnumerators(List<? extends NodeBuilder<? extends EnumeratorDeclaration>> value) { enumerators = new ArrayList<>(List.copyOf(value)); return this; }
        public Builder addEnumerator(NodeBuilder<? extends EnumeratorDeclaration> value) { enumerators.add(Objects.requireNonNull(value)); return this; }
        @Override EnumDeclaration newNode() { return new EnumDeclaration(); }
        @Override void fillNode(EnumDeclaration node, BuildContext context) {
            node.name = Objects.requireNonNull(name, "EnumDeclaration.name");
            node.enumerators = context.copyAll(enumerators);
        }
    }
}
