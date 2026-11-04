/* Code Property Graph: structurally owned external configuration inputs. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An external configuration source, owned by the project rather than by source syntax. */
public final class ExternalInput extends Node {
    private String key;
    private String role;
    private String text;
    private List<ExternalInput> inputs = List.of();
    private ExternalInput() {}
    public String key() { return key; }
    public String role() { return role; }
    public String text() { return text; }
    public List<ExternalInput> inputs() { return inputs; }
    @Override public List<Node> children() { return List.copyOf(inputs); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    public static Builder create(String key, String role, String text) {
        return new Builder().setKey(key).setRole(role).setText(text);
    }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext context) {
        var builder = create(key, role, text);
        context.register(this, builder);
        builder.inputs = context.copyAll(inputs, ExternalInput.class);
        return builder;
    }
    public static final class Builder extends NodeBuilder<ExternalInput> {
        private String key, role, text;
        private List<NodeBuilder<? extends ExternalInput>> inputs = new ArrayList<>();
        private Builder() { super(ExternalInput.class); }
        public Builder setKey(String value) { key = Objects.requireNonNull(value); return this; }
        public Builder setRole(String value) { role = Objects.requireNonNull(value); return this; }
        public Builder setText(String value) { text = Objects.requireNonNull(value); return this; }
        public Builder addInput(NodeBuilder<? extends ExternalInput> value) { inputs.add(Objects.requireNonNull(value)); return this; }
        /** Adds new configuration descendants while retaining explicit snapshot edits. */
        void mergeMissing(Builder generated) {
            for (var child : generated.inputs) {
                var addition = (Builder) child;
                Builder existing = inputs.stream().map(Builder.class::cast).filter(b -> b.key.equals(addition.key)).findFirst().orElse(null);
                if (existing == null) inputs.add(addition); else existing.mergeMissing(addition);
            }
        }
        String inputKey() { return key; }
        @Override ExternalInput newNode() { return new ExternalInput(); }
        @Override void fillNode(ExternalInput node, BuildContext context) {
            node.key = Objects.requireNonNull(key); node.role = Objects.requireNonNull(role);
            node.text = Objects.requireNonNull(text); node.inputs = context.copyAll(inputs);
        }
    }
}
