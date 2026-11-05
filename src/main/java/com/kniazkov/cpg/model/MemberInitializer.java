/* C++03 constructor initializer. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Written constructor initializer. Order here is source order, not construction order. */
public final class MemberInitializer extends Node {
    private Declaration target;
    public Optional<Declaration> target() { return Optional.ofNullable(target); }
    @Override public List<Node> references() { return target == null ? List.of() : List.of(target); }
    private String name;
    private List<Expression> arguments = List.of();
    private MemberInitializer() {}
    public String name() { return name; }
    public List<Expression> arguments() { return arguments; }
    @Override public List<Node> children() { return List.copyOf(arguments); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create() { return new Builder(); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) { var b = new Builder(); context.register(this, b); b.name = name; b.target = target == null ? null : context.edge(target, Declaration.class); b.arguments = context.copyAll(arguments, Expression.class); return b; }
    public static final class Builder extends NodeBuilder<MemberInitializer> {
        private NodeBuilder<? extends Declaration> target;
        public Builder setTarget(NodeBuilder<? extends Declaration> value) { target = Objects.requireNonNull(value); return this; }
        public Builder clearTarget() { target = null; return this; }
        private String name;
        private List<NodeBuilder<? extends Expression>> arguments = new ArrayList<>();
        private Builder() { super(MemberInitializer.class); }
        public Optional<String> name() { return Optional.ofNullable(name); }
        public List<NodeBuilder<? extends Expression>> arguments() { return List.copyOf(arguments); }
        public Builder setArguments(List<? extends NodeBuilder<? extends Expression>> values) { arguments = new ArrayList<>(List.copyOf(values)); return this; }
        public Builder setName(String value) { name = Objects.requireNonNull(value); target = null; return this; }
        public Builder addArgument(NodeBuilder<? extends Expression> value) { arguments.add(Objects.requireNonNull(value)); return this; }
        @Override MemberInitializer newNode() { return new MemberInitializer(); }
        @Override void fillNode(MemberInitializer node, BuildContext context) { node.name = requiredText(name, "MemberInitializer.name"); node.target = target == null ? null : context.copy(target); node.arguments = context.copyAll(arguments); }
    }
}
