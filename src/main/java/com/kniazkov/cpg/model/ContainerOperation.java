/* Owned language-independent container-family semantics. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Derived semantics referencing preserved written syntax; references never own syntax. */
public final class ContainerOperation extends Node {
    public enum Kind {
        EMPTY, COUNT, FILL, RANGE, COPY, ASSIGN_COPY, ASSIGN_FILL, ASSIGN_RANGE,
        INSERT, INSERT_FILL, INSERT_RANGE, ERASE, ERASE_RANGE, CLEAR, FIND, COUNT_KEY,
        LOWER_BOUND, UPPER_BOUND, EQUAL_RANGE, SUBSCRIPT, SPLICE, MERGE, SORT, REVERSE,
        REMOVE, REMOVE_IF, UNIQUE, PUSH_BACK, PUSH_FRONT, POP_BACK, POP_FRONT,
        PUSH, POP, TOP, FRONT, BACK, AT, BEGIN, END, RBEGIN, REND, SIZE, IS_EMPTY, MAX_SIZE, RESIZE, SWAP
    }
    /** Lookup does not mutate; map subscript may insert a default mapped value. */
    public boolean mayInsert() { return switch (kind) {
        case SUBSCRIPT -> type.kind() == ContainerType.Kind.MAP;
        case INSERT, INSERT_FILL, INSERT_RANGE, PUSH_BACK, PUSH_FRONT, PUSH -> true;
        default -> false;
    }; }
    public boolean preservesTransferredReferences() { return kind == Kind.SPLICE; }
    public enum TransferKind { ALL, ONE, RANGE }
    private TransferKind transferKind = TransferKind.ALL;
    public TransferKind transferKind() { return transferKind; }
    private Node sourceContainer;
    public Optional<Node> sourceContainer() { return Optional.ofNullable(sourceContainer); }
    private CallableDeclaration comparator;
    public Optional<CallableDeclaration> comparator() { return Optional.ofNullable(comparator); }
    public enum ResultKind { NONE, VALUE, REFERENCE, ITERATOR }
    private ResultKind resultKind = ResultKind.NONE;
    public ResultKind resultKind() { return resultKind; }
    private boolean writable;
    /** Whether the produced reference/iterator permits element modification. */
    public boolean writable() { return writable; }
    private Node index;
    /** Written index operand, when one exists; it is never owned here. */
    public Optional<Node> index() { return Optional.ofNullable(index); }
    private List<Node> mutatedObjects = List.of();
    /** Sequence objects modified by this operation, including both operands of swap. */
    public List<Node> mutatedObjects() { return mutatedObjects; }
    private Handle handle;
    public Optional<Handle> handle() { return Optional.ofNullable(handle); }

    /** A conservative result handle snapshot at creation. Later storage transfers and validity
     * changes are represented by HandleEffect, not by rewriting this snapshot. UNKNOWN never promises
     * dereferenceability. END is a boundary, not an element, even when VALID. */
    public static final class Handle {
        public enum Kind { REFERENCE, ITERATOR }
        public enum Direction { FORWARD, REVERSE }
        public enum Position { INDEX, BEGIN, END, LAST, UNKNOWN }
        public enum Validity { VALID, INVALID, UNKNOWN }
        private final Kind kind;
        private final Node sequence;
        private final Node position;
        private final Direction direction;
        private final Position positionKind;
        private final boolean writable;
        private final Validity validity;
        private Handle(Builder b, BuildContext c) {
            kind = Objects.requireNonNull(b.kind, "handle kind");
            sequence = c.copy(Objects.requireNonNull(b.sequence, "handle sequence"));
            position = b.position == null ? null : c.copy(b.position);
            direction = Objects.requireNonNull(b.direction);
            positionKind = Objects.requireNonNull(b.positionKind);
            writable = b.writable;
            validity = Objects.requireNonNull(b.validity);
            if (positionKind == Position.INDEX && position == null)
                throw new IllegalStateException("Indexed handle requires a position operand");
        }
        public Kind kind() { return kind; }
        public Node sequence() { return sequence; }
        public Optional<Node> position() { return Optional.ofNullable(position); }
        public Direction direction() { return direction; }
        public Position positionKind() { return positionKind; }
        public boolean writable() { return writable; }
        public Validity validity() { return validity; }
        public static Builder create() { return new Builder(); }
        private Builder copyBuilder(EditContext c) {
            return create().setKind(kind).setSequence(c.edge(sequence, Node.class))
                    .setPosition(position == null ? null : c.edge(position, Node.class))
                    .setDirection(direction).setPositionKind(positionKind)
                    .setWritable(writable).setValidity(validity);
        }
        public static final class Builder {
            private Kind kind;
            private NodeBuilder<? extends Node> sequence, position;
            private Direction direction = Direction.FORWARD;
            private Position positionKind = Position.UNKNOWN;
            private boolean writable;
            private Validity validity = Validity.UNKNOWN;
            private Builder() { }
            public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
            public Builder setSequence(NodeBuilder<? extends Node> value) { sequence = value; return this; }
            public Builder setPosition(NodeBuilder<? extends Node> value) { position = value; return this; }
            public Builder setDirection(Direction value) { direction = Objects.requireNonNull(value); return this; }
            public Builder setPositionKind(Position value) { positionKind = Objects.requireNonNull(value); return this; }
            public Builder setWritable(boolean value) { writable = value; return this; }
            public Builder setValidity(Validity value) { validity = Objects.requireNonNull(value); return this; }
        }
    }
    /** Checked access retains the original call's exceptional control-flow path. */
    public boolean boundsChecked() { return kind == Kind.AT; }
    private List<HandleEffect> handleEffects = List.of();
    /** Temporal effects on previously produced handles; creation snapshots remain unchanged. */
    public List<HandleEffect> handleEffects() { return handleEffects; }
    public static final class HandleEffect {
        private final ContainerOperation producer;
        private final Handle.Validity validityAfter;
        private final Node sequenceAfter, positionAfter;
        private HandleEffect(Builder b, BuildContext c) {
            producer = c.copy(Objects.requireNonNull(b.producer, "handle producer"));
            validityAfter = Objects.requireNonNull(b.validityAfter);
            sequenceAfter = b.sequenceAfter == null ? null : c.copy(b.sequenceAfter);
            positionAfter = b.positionAfter == null ? null : c.copy(b.positionAfter);
        }
        public ContainerOperation producer() { return producer; }
        public Handle.Validity validityAfter() { return validityAfter; }
        /** Present when storage association after this operation is known, notably swap. */
        public Optional<Node> sequenceAfter() { return Optional.ofNullable(sequenceAfter); }
        public Optional<Node> positionAfter() { return Optional.ofNullable(positionAfter); }
        public static Builder create() { return new Builder(); }
        private Builder copyBuilder(EditContext c) {
            return create().setProducer(c.edge(producer, ContainerOperation.class))
                    .setValidityAfter(validityAfter)
                    .setSequenceAfter(sequenceAfter == null ? null : c.edge(sequenceAfter, Node.class))
                    .setPositionAfter(positionAfter == null ? null : c.edge(positionAfter, Node.class));
        }
        public static final class Builder {
            private NodeBuilder<? extends ContainerOperation> producer;
            private Handle.Validity validityAfter = Handle.Validity.UNKNOWN;
            private NodeBuilder<? extends Node> sequenceAfter, positionAfter;
            private Builder() { }
            public Builder setProducer(NodeBuilder<? extends ContainerOperation> value) { producer = value; return this; }
            public Builder setValidityAfter(Handle.Validity value) { validityAfter = Objects.requireNonNull(value); return this; }
            public Builder setSequenceAfter(NodeBuilder<? extends Node> value) { sequenceAfter = value; return this; }
            public Builder setPositionAfter(NodeBuilder<? extends Node> value) { positionAfter = value; return this; }
        }
    }
    private Kind kind;
    private List<Node> sources = List.of();
    public Kind kind() { return kind; }
    /** Ordered inputs: count/value, first/last, source sequence, or appended value. */
    public List<Node> sources() { return sources; }
    private Node syntax;
    public Node syntax() { return syntax; }
    private Node destination;
    public Node destination() { return destination; }
    private ContainerType type;
    public ContainerType type() { return type; }
    private CallableDeclaration selectedTarget;
    public CallableDeclaration selectedTarget() { return selectedTarget; }
    private CallableDeclaration elementCopyConstructor;
    public Optional<CallableDeclaration> elementCopyConstructor() { return Optional.ofNullable(elementCopyConstructor); }
    private CallableDeclaration elementDestructor;
    public Optional<CallableDeclaration> elementDestructor() { return Optional.ofNullable(elementDestructor); }
    private CallableDeclaration elementAssignment;
    public Optional<CallableDeclaration> elementAssignment() { return Optional.ofNullable(elementAssignment); }
    @Override public List<Node> children() { return List.of(); }
    @Override public List<Node> references() { var result = new ArrayList<Node>(); result.add(type);
        if (syntax != null) result.add(syntax);
        if (destination != null) result.add(destination);
        if (selectedTarget != null) result.add(selectedTarget);
        if (elementCopyConstructor != null) result.add(elementCopyConstructor);
        if (elementDestructor != null) result.add(elementDestructor);
        if (elementAssignment != null) result.add(elementAssignment);
        if (sourceContainer != null) result.add(sourceContainer);
        if (comparator != null) result.add(comparator);
        result.addAll(sources);
        if (index != null) result.add(index);
        result.addAll(mutatedObjects);
        if (handle != null) {
            result.add(handle.sequence());
            handle.position().ifPresent(result::add);
        }
        for (var effect : handleEffects) {
            result.add(effect.producer());
            effect.sequenceAfter().ifPresent(result::add);
            effect.positionAfter().ifPresent(result::add);
        }
        return List.copyOf(result); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    public static Builder create() { return new Builder(); }
    @Override Builder copyBuilder(EditContext c) { var b = new Builder(); c.register(this, b);
        b.syntax = syntax == null ? null : c.edge(syntax, Node.class);
        b.destination = destination == null ? null : c.edge(destination, Node.class);
        b.type = type == null ? null : c.edge(type, ContainerType.class);
        b.selectedTarget = selectedTarget == null ? null : c.edge(selectedTarget, CallableDeclaration.class);
        b.elementCopyConstructor = elementCopyConstructor == null ? null : c.edge(elementCopyConstructor, CallableDeclaration.class);
        b.elementDestructor = elementDestructor == null ? null : c.edge(elementDestructor, CallableDeclaration.class);
        b.elementAssignment = elementAssignment == null ? null : c.edge(elementAssignment, CallableDeclaration.class);
        b.transferKind = transferKind;
        b.sourceContainer = sourceContainer == null ? null : c.edge(sourceContainer, Node.class);
        b.comparator = comparator == null ? null : c.edge(comparator, CallableDeclaration.class);
        b.kind = kind; b.sources = c.copyAll(sources, Node.class);
        b.resultKind = resultKind; b.writable = writable;
        b.index = index == null ? null : c.edge(index, Node.class);
        b.mutatedObjects = c.copyAll(mutatedObjects, Node.class);
        b.handle = handle == null ? null : handle.copyBuilder(c);
        b.handleEffects = handleEffects.stream().map(effect -> effect.copyBuilder(c)).toList();
        return b; }
    public static final class Builder extends NodeBuilder<ContainerOperation> {
        private Builder() { super(ContainerOperation.class); }
        private TransferKind transferKind = TransferKind.ALL;
        public Builder setTransferKind(TransferKind value) { transferKind = Objects.requireNonNull(value); return this; }
        private NodeBuilder<? extends Node> sourceContainer;
        public Builder setSourceContainer(NodeBuilder<? extends Node> value) { sourceContainer = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> comparator;
        public Builder setComparator(NodeBuilder<? extends CallableDeclaration> value) { comparator = value; return this; }
        private Kind kind;
        private ResultKind resultKind = ResultKind.NONE;
        private boolean writable;
        private NodeBuilder<? extends Node> index;
        private List<NodeBuilder<? extends Node>> mutatedObjects;
        private Handle.Builder handle;
        private List<HandleEffect.Builder> handleEffects = List.of();
        public Builder setHandleEffects(List<HandleEffect.Builder> value) { handleEffects = new ArrayList<>(value); return this; }
        public Builder setResultKind(ResultKind value) { resultKind = Objects.requireNonNull(value); return this; }
        public Builder setWritable(boolean value) { writable = value; return this; }
        public Builder setIndex(NodeBuilder<? extends Node> value) { index = value; return this; }
        public Builder setMutatedObjects(List<NodeBuilder<? extends Node>> value) { mutatedObjects = new ArrayList<>(value); return this; }
        public Builder setHandle(Handle.Builder value) { handle = value; return this; }
        private List<NodeBuilder<? extends Node>> sources = new ArrayList<>();
        public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
        public Builder setSources(List<NodeBuilder<? extends Node>> value) { sources = new ArrayList<>(value); return this; }
        private NodeBuilder<? extends Node> syntax;
        public Builder setSyntax(NodeBuilder<? extends Node> value) { syntax = value; return this; }
        private NodeBuilder<? extends Node> destination;
        public Builder setDestination(NodeBuilder<? extends Node> value) { destination = value; return this; }
        private NodeBuilder<? extends ContainerType> type;
        public Builder setType(NodeBuilder<? extends ContainerType> value) { type = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> selectedTarget;
        public Builder setSelectedTarget(NodeBuilder<? extends CallableDeclaration> value) { selectedTarget = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> elementCopyConstructor;
        public Builder setElementCopyConstructor(NodeBuilder<? extends CallableDeclaration> value) { elementCopyConstructor = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> elementDestructor;
        public Builder setElementDestructor(NodeBuilder<? extends CallableDeclaration> value) { elementDestructor = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> elementAssignment;
        public Builder setElementAssignment(NodeBuilder<? extends CallableDeclaration> value) { elementAssignment = value; return this; }
        @Override ContainerOperation newNode() { return new ContainerOperation(); }
        @Override void fillNode(ContainerOperation n, BuildContext c) {
            n.syntax = c.copy(Objects.requireNonNull(syntax, "syntax"));
            n.destination = c.copy(Objects.requireNonNull(destination, "destination"));
            n.type = c.copy(Objects.requireNonNull(type, "type"));
            n.selectedTarget = c.copy(Objects.requireNonNull(selectedTarget, "selectedTarget"));
            n.elementCopyConstructor = elementCopyConstructor == null ? null : c.copy(elementCopyConstructor);
            n.elementDestructor = elementDestructor == null ? null : c.copy(elementDestructor);
            n.elementAssignment = elementAssignment == null ? null : c.copy(elementAssignment);
            n.transferKind = transferKind;
            n.sourceContainer = sourceContainer == null ? null : c.copy(sourceContainer);
            n.comparator = comparator == null ? null : c.copy(comparator);
            n.kind = Objects.requireNonNull(kind); n.sources = c.copyAll(sources);
            n.resultKind = resultKind; n.writable = writable;
            n.index = index == null ? null : c.copy(index);
            n.handle = handle == null ? null : new Handle(handle, c);
            n.handleEffects = handleEffects.stream().map(effect -> new HandleEffect(effect, c)).toList();
            n.mutatedObjects = mutatedObjects == null ? switch (kind) {
                case FIND, COUNT_KEY, LOWER_BOUND, UPPER_BOUND, EQUAL_RANGE, TOP, FRONT, BACK, AT, SIZE, IS_EMPTY, MAX_SIZE,
                        BEGIN, END, RBEGIN, REND -> List.of();
                case SUBSCRIPT -> n.type.kind() == ContainerType.Kind.MAP ? List.of(n.destination) : List.of();
                case SPLICE, MERGE -> List.of(n.destination, Objects.requireNonNull(n.sourceContainer, "sourceContainer"));
                default -> List.of(n.destination);
            } : c.copyAll(mutatedObjects);
            if (kind == Kind.SPLICE && (n.type.kind() != ContainerType.Kind.LIST || n.sourceContainer == null
                    || !n.mutatedObjects.contains(n.destination) || !n.mutatedObjects.contains(n.sourceContainer)))
                throw new IllegalStateException("Splice requires both list operands");
            if (kind == Kind.SWAP && n.mutatedObjects.size() != 2)
                throw new IllegalStateException("Swap requires both mutated sequence operands");
        }
    }
}
