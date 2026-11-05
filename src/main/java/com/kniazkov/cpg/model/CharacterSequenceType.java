/* Owned character-sequence and range semantics. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Derived semantic facts; all edges reference, rather than own, preserved syntax. */
public final class CharacterSequenceType extends Node {
    /** Concrete specialization when available; absent for incompletely resolved types. */
    public ClassDeclaration concreteClass() { return declaration; }
    private ClassDeclaration declaration;
    public Optional<ClassDeclaration> declaration() { return Optional.ofNullable(declaration); }
    private TypeNode characterType;
    public TypeNode characterType() { return characterType; }
    private TypeNode traitsType;
    public Optional<TypeNode> traitsType() { return Optional.ofNullable(traitsType); }
    private TypeNode allocatorType;
    public Optional<TypeNode> allocatorType() { return Optional.ofNullable(allocatorType); }
    private List<TypeNode> writtenTypes = List.of();
    public List<TypeNode> writtenTypes() { return writtenTypes; }
    @Override public List<Node> children() { return List.of(); }
    @Override public List<Node> references() {
        var result = new ArrayList<Node>();
        if (declaration != null) result.add(declaration);
        if (characterType != null) result.add(characterType);
        if (traitsType != null) result.add(traitsType);
        if (allocatorType != null) result.add(allocatorType);
        result.addAll(writtenTypes);
        return List.copyOf(result);
    }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    public static Builder create() { return new Builder(); }
    @Override Builder copyBuilder(EditContext c) {
        var b = new Builder(); c.register(this, b);
        b.declaration = declaration == null ? null : c.edge(declaration, ClassDeclaration.class);
        b.characterType = characterType == null ? null : c.edge(characterType, TypeNode.class);
        b.traitsType = traitsType == null ? null : c.edge(traitsType, TypeNode.class);
        b.allocatorType = allocatorType == null ? null : c.edge(allocatorType, TypeNode.class);
        b.writtenTypes = c.copyAll(writtenTypes, TypeNode.class);
        return b;
    }
    public static final class Builder extends NodeBuilder<CharacterSequenceType> {
        private Builder() { super(CharacterSequenceType.class); }
        private NodeBuilder<? extends ClassDeclaration> declaration;
        public Builder setDeclaration(NodeBuilder<? extends ClassDeclaration> value) { declaration = value; return this; }
        private NodeBuilder<? extends TypeNode> characterType;
        public Builder setCharacterType(NodeBuilder<? extends TypeNode> value) { characterType = value; return this; }
        private NodeBuilder<? extends TypeNode> traitsType;
        public Builder setTraitsType(NodeBuilder<? extends TypeNode> value) { traitsType = value; return this; }
        private NodeBuilder<? extends TypeNode> allocatorType;
        public Builder setAllocatorType(NodeBuilder<? extends TypeNode> value) { allocatorType = value; return this; }
        private List<NodeBuilder<? extends TypeNode>> writtenTypes = new ArrayList<>();
        public Builder setWrittenTypes(List<NodeBuilder<? extends TypeNode>> value) { writtenTypes = new ArrayList<>(value); return this; }
        @Override CharacterSequenceType newNode() { return new CharacterSequenceType(); }
        @Override void fillNode(CharacterSequenceType n, BuildContext c) {
            n.declaration = declaration == null ? null : c.copy(declaration);
            n.characterType = c.copy(Objects.requireNonNull(characterType, "characterType"));
            n.traitsType = traitsType == null ? null : c.copy(traitsType);
            n.allocatorType = allocatorType == null ? null : c.copy(allocatorType);
            n.writtenTypes = c.copyAll(writtenTypes);
        }
    }
}
