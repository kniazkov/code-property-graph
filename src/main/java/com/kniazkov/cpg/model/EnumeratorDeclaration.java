/* C++03 declaration syntax. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** An enumerator with its optional written constant expression. */
public final class EnumeratorDeclaration extends Declaration {
    private String name;
    private Expression value;
    private EnumeratorDeclaration() {}
    public String name() { return name; }
    public Optional<Expression> value() { return Optional.ofNullable(value); }
    @Override public List<Node> children() { var result = new ArrayList<Node>();
        if (value != null) result.add(value);
        return List.copyOf(result); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(super.references());
        return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = new Builder(); context.register(this, builder);
        builder.name = name;
        builder.value = value == null ? null : context.edge(value, Expression.class);
        return builder; }
    public static final class Builder extends NodeBuilder<EnumeratorDeclaration> {
        private String name;
        private NodeBuilder<? extends Expression> value;
        private Builder() { super(EnumeratorDeclaration.class); }
        public Optional<String> name() { return Optional.ofNullable(name); }
        public Builder setName(String value) { name = Objects.requireNonNull(value); return this; }
        public Optional<NodeBuilder<? extends Expression>> value() { return Optional.ofNullable(value); }
        public Builder setValue(NodeBuilder<? extends Expression> value) { this.value = Objects.requireNonNull(value); return this; }
        public Builder clearValue() { value = null; return this; }
        @Override EnumeratorDeclaration newNode() { return new EnumeratorDeclaration(); }
        @Override void fillNode(EnumeratorDeclaration node, BuildContext context) {
            node.name = Objects.requireNonNull(name, "EnumeratorDeclaration.name");
            node.value = value == null ? null : context.copy(value);
        }
    }
}
