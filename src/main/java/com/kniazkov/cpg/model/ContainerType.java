/* Owned language-independent container-family semantics. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Derived semantics referencing preserved written syntax; references never own syntax. */
public final class ContainerType extends Node {
    public enum Kind { DEQUE, LIST, SET, MULTISET, MAP, MULTIMAP, STACK, QUEUE, PRIORITY_QUEUE }
    public enum IteratorCategory { RANDOM_ACCESS, BIDIRECTIONAL, NONE }
    public enum Order { SEQUENCE, KEY, LIFO, FIFO, PRIORITY }
    public enum ReferenceStability { NODE_STABLE, DEQUE_RULES, UNDERLYING_CONTAINER }
    private Kind kind;
    public Kind kind() { return kind; }
    public TypeNode valueType() { return elementType; }
    public boolean uniqueKeys() { return kind == Kind.SET || kind == Kind.MAP; }
    public boolean associative() { return kind == Kind.SET || kind == Kind.MULTISET || kind == Kind.MAP || kind == Kind.MULTIMAP; }
    public IteratorCategory iteratorCategory() { return switch (kind) {
        case DEQUE -> IteratorCategory.RANDOM_ACCESS;
        case LIST, SET, MULTISET, MAP, MULTIMAP -> IteratorCategory.BIDIRECTIONAL;
        default -> IteratorCategory.NONE;
    }; }
    public Order order() { return switch (kind) {
        case STACK -> Order.LIFO; case QUEUE -> Order.FIFO; case PRIORITY_QUEUE -> Order.PRIORITY;
        case SET, MULTISET, MAP, MULTIMAP -> Order.KEY; default -> Order.SEQUENCE;
    }; }
    /** Node-based insertion preserves existing element references; erasure invalidates erased elements only.
     * Deque and adapter guarantees depend on the particular operation, never vector reallocation rules. */
    public ReferenceStability referenceStability() { return switch (kind) {
        case DEQUE -> ReferenceStability.DEQUE_RULES;
        case STACK, QUEUE, PRIORITY_QUEUE -> ReferenceStability.UNDERLYING_CONTAINER;
        default -> ReferenceStability.NODE_STABLE;
    }; }
    private TypeNode keyType;
    public Optional<TypeNode> keyType() { return Optional.ofNullable(keyType); }
    private TypeNode mappedType;
    public Optional<TypeNode> mappedType() { return Optional.ofNullable(mappedType); }
    private TypeNode comparatorType;
    public Optional<TypeNode> comparatorType() { return Optional.ofNullable(comparatorType); }
    private TypeNode underlyingType;
    public Optional<TypeNode> underlyingType() { return Optional.ofNullable(underlyingType); }
    private ClassDeclaration declaration;
    public ClassDeclaration declaration() { return declaration; }
    private TypeNode elementType;
    public TypeNode elementType() { return elementType; }
    private List<TypeNode> writtenTypes = List.of();
    public List<TypeNode> writtenTypes() { return writtenTypes; }
    public ClassDeclaration concreteClass() { return declaration; }
    @Override public List<Node> children() { return List.of(); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(); if (declaration != null) result.add(declaration);
        if (elementType != null) result.add(elementType);
        if (keyType != null) result.add(keyType);
        if (mappedType != null) result.add(mappedType);
        if (comparatorType != null) result.add(comparatorType);
        if (underlyingType != null) result.add(underlyingType);
        result.addAll(writtenTypes);
        return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    public static Builder create() { return new Builder(); }
    @Override Builder copyBuilder(EditContext c) { var b = new Builder(); c.register(this, b);
        b.kind = kind;
        b.declaration = declaration == null ? null : c.edge(declaration, ClassDeclaration.class);
        b.elementType = elementType == null ? null : c.edge(elementType, TypeNode.class);
        b.keyType = keyType == null ? null : c.edge(keyType, TypeNode.class);
        b.mappedType = mappedType == null ? null : c.edge(mappedType, TypeNode.class);
        b.comparatorType = comparatorType == null ? null : c.edge(comparatorType, TypeNode.class);
        b.underlyingType = underlyingType == null ? null : c.edge(underlyingType, TypeNode.class);
        b.writtenTypes = c.copyAll(writtenTypes, TypeNode.class);
        return b; }
    public static final class Builder extends NodeBuilder<ContainerType> {
        private NodeBuilder<? extends TypeNode> keyType;
        public Builder setKeyType(NodeBuilder<? extends TypeNode> value) { keyType = value; return this; }
        private NodeBuilder<? extends TypeNode> mappedType;
        public Builder setMappedType(NodeBuilder<? extends TypeNode> value) { mappedType = value; return this; }
        private NodeBuilder<? extends TypeNode> comparatorType;
        public Builder setComparatorType(NodeBuilder<? extends TypeNode> value) { comparatorType = value; return this; }
        private NodeBuilder<? extends TypeNode> underlyingType;
        public Builder setUnderlyingType(NodeBuilder<? extends TypeNode> value) { underlyingType = value; return this; }
        private Kind kind;
        public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
        public Builder setValueType(NodeBuilder<? extends TypeNode> value) { return setElementType(value); }
        private Builder() { super(ContainerType.class); }
        private NodeBuilder<? extends ClassDeclaration> declaration;
        public Builder setDeclaration(NodeBuilder<? extends ClassDeclaration> value) { declaration = value; return this; }
        private NodeBuilder<? extends TypeNode> elementType;
        public Builder setElementType(NodeBuilder<? extends TypeNode> value) { elementType = value; return this; }
        private List<NodeBuilder<? extends TypeNode>> writtenTypes = new ArrayList<>();
        public Builder setWrittenTypes(List<NodeBuilder<? extends TypeNode>> value) { writtenTypes = new ArrayList<>(value); return this; }
        public Builder setConcreteClass(NodeBuilder<? extends ClassDeclaration> value) { return setDeclaration(value); }
        @Override ContainerType newNode() { return new ContainerType(); }
        @Override void fillNode(ContainerType n, BuildContext c) {
            n.kind = Objects.requireNonNull(kind, "kind");
            n.declaration = declaration == null ? null : c.copy(declaration);
            n.elementType = c.copy(Objects.requireNonNull(elementType, "elementType"));
            n.keyType = keyType == null ? null : c.copy(keyType);
            n.mappedType = mappedType == null ? null : c.copy(mappedType);
            n.comparatorType = comparatorType == null ? null : c.copy(comparatorType);
            n.underlyingType = underlyingType == null ? null : c.copy(underlyingType);
            n.writtenTypes = c.copyAll(writtenTypes);
        }
    }
}
