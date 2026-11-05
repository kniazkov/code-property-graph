/* Owned character-sequence and range semantics. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Derived semantic facts; all edges reference, rather than own, preserved syntax. */
public final class SequenceOperation extends Node {
    public enum Kind {
        STRING_EMPTY, STRING_COPY, STRING_C_STRING, STRING_BUFFER, STRING_FILL, STRING_RANGE,
        STRING_ASSIGN, STRING_APPEND, STRING_FIND, STRING_SUBSTR, STRING_SIZE, STRING_AT,
        STRING_SUBSCRIPT, STRING_BEGIN, STRING_END, STRING_RBEGIN, STRING_REND,
        ADVANCE, DISTANCE, BACK_INSERTER, FRONT_INSERTER, INSERTER, REVERSE_ITERATOR,
        FIND_IF, TRANSFORM, SORT, REMOVE, COPY, ACCUMULATE
    }
    /** Explicit counts and iterator ranges retain embedded NUL characters. */
    public enum LengthKind { NONE, NULL_TERMINATED, EXPLICIT_COUNT, RANGE, SEQUENCE }
    public enum Direction { FORWARD, REVERSE, UNKNOWN }
    /** Remove changes values and logical end, not physical sequence size. */
    public enum SizeEffect { UNCHANGED, MAY_CHANGE, UNKNOWN }
    /** REFERENCE denotes an element; SEQUENCE_REFERENCE aliases the whole receiver. */
    public enum ResultKind { NONE, VALUE, REFERENCE, ITERATOR, SEQUENCE, SEQUENCE_REFERENCE, OUTPUT_ADAPTER }
    /** Describes effects of an invoked callable, not the enclosing algorithm itself. */
    public enum CallableEffect { NONE, READ_ONLY, MAY_WRITE, UNKNOWN }
    private Node syntax;
    public Node syntax() { return syntax; }
    private CallableDeclaration selectedTarget;
    public CallableDeclaration selectedTarget() { return selectedTarget; }
    public Optional<CharacterSequenceType> stringType() { return characterType(); }
    private CharacterSequenceType characterType;
    public Optional<CharacterSequenceType> characterType() { return Optional.ofNullable(characterType); }
    private Node destination;
    public Optional<Node> destination() { return Optional.ofNullable(destination); }
    private Node inputSequence;
    public Optional<Node> inputSequence() { return Optional.ofNullable(inputSequence); }
    private Node outputDestination;
    public Optional<Node> outputDestination() { return Optional.ofNullable(outputDestination); }
    private Node first;
    public Optional<Node> first() { return Optional.ofNullable(first); }
    private Node last;
    public Optional<Node> last() { return Optional.ofNullable(last); }
    private Node secondFirst;
    public Optional<Node> secondFirst() { return Optional.ofNullable(secondFirst); }
    /** Starting character offset for substring-copy, append, find, and substr overloads. */
    private Node position;
    public Optional<Node> position() { return Optional.ofNullable(position); }
    private Node count;
    public Optional<Node> count() { return Optional.ofNullable(count); }
    private Node initialValue;
    public Optional<Node> initialValue() { return Optional.ofNullable(initialValue); }
    private Node callable;
    public Optional<Node> callable() { return Optional.ofNullable(callable); }
    private CallableDeclaration callableTarget;
    public Optional<CallableDeclaration> callableTarget() { return Optional.ofNullable(callableTarget); }
    private TypeNode resultType;
    public Optional<TypeNode> resultType() { return Optional.ofNullable(resultType); }
    private TypeNode accumulatorType;
    public Optional<TypeNode> accumulatorType() { return Optional.ofNullable(accumulatorType); }
    private List<Node> sources = List.of();
    public List<Node> sources() { return sources; }
    private List<Node> mutatedObjects = List.of();
    public List<Node> mutatedObjects() { return mutatedObjects; }
    private List<Node> callableEffects = List.of();
    public List<Node> callableEffects() { return callableEffects; }
    private Kind kind = null;
    public Kind kind() { return kind; }
    private LengthKind lengthKind = LengthKind.NONE;
    public LengthKind lengthKind() { return lengthKind; }
    private ResultKind resultKind = ResultKind.NONE;
    public ResultKind resultKind() { return resultKind; }
    private CallableEffect callableEffect = CallableEffect.NONE;
    public CallableEffect callableEffect() { return callableEffect; }
    private boolean callableMayThrow = false;
    public boolean callableMayThrow() { return callableMayThrow; }
    private boolean writable = false;
    public boolean writable() { return writable; }
    private Direction direction = Direction.UNKNOWN;
    public Direction direction() { return direction; }
    private SizeEffect sizeEffect = SizeEffect.UNKNOWN;
    public SizeEffect sizeEffect() { return sizeEffect; }
    @Override public List<Node> children() { return List.of(); }
    @Override public List<Node> references() {
        var result = new ArrayList<Node>();
        if (syntax != null) result.add(syntax);
        if (selectedTarget != null) result.add(selectedTarget);
        if (characterType != null) result.add(characterType);
        if (destination != null) result.add(destination);
        if (inputSequence != null) result.add(inputSequence);
        if (outputDestination != null) result.add(outputDestination);
        if (first != null) result.add(first);
        if (last != null) result.add(last);
        if (secondFirst != null) result.add(secondFirst);
        if (position != null) result.add(position);
        if (count != null) result.add(count);
        if (initialValue != null) result.add(initialValue);
        if (callable != null) result.add(callable);
        if (callableTarget != null) result.add(callableTarget);
        if (resultType != null) result.add(resultType);
        if (accumulatorType != null) result.add(accumulatorType);
        result.addAll(sources);
        result.addAll(mutatedObjects);
        result.addAll(callableEffects);
        return List.copyOf(result);
    }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    public static Builder create() { return new Builder(); }
    @Override Builder copyBuilder(EditContext c) {
        var b = new Builder(); c.register(this, b);
        b.syntax = syntax == null ? null : c.edge(syntax, Node.class);
        b.selectedTarget = selectedTarget == null ? null : c.edge(selectedTarget, CallableDeclaration.class);
        b.characterType = characterType == null ? null : c.edge(characterType, CharacterSequenceType.class);
        b.destination = destination == null ? null : c.edge(destination, Node.class);
        b.inputSequence = inputSequence == null ? null : c.edge(inputSequence, Node.class);
        b.outputDestination = outputDestination == null ? null : c.edge(outputDestination, Node.class);
        b.first = first == null ? null : c.edge(first, Node.class);
        b.last = last == null ? null : c.edge(last, Node.class);
        b.secondFirst = secondFirst == null ? null : c.edge(secondFirst, Node.class);
        b.position = position == null ? null : c.edge(position, Node.class);
        b.count = count == null ? null : c.edge(count, Node.class);
        b.initialValue = initialValue == null ? null : c.edge(initialValue, Node.class);
        b.callable = callable == null ? null : c.edge(callable, Node.class);
        b.callableTarget = callableTarget == null ? null : c.edge(callableTarget, CallableDeclaration.class);
        b.resultType = resultType == null ? null : c.edge(resultType, TypeNode.class);
        b.accumulatorType = accumulatorType == null ? null : c.edge(accumulatorType, TypeNode.class);
        b.sources = c.copyAll(sources, Node.class);
        b.mutatedObjects = c.copyAll(mutatedObjects, Node.class);
        b.callableEffects = c.copyAll(callableEffects, Node.class);
        b.direction = direction;
        b.sizeEffect = sizeEffect;
        b.kind = kind;
        b.lengthKind = lengthKind;
        b.resultKind = resultKind;
        b.callableEffect = callableEffect;
        b.callableMayThrow = callableMayThrow;
        b.writable = writable;
        return b;
    }
    public static final class Builder extends NodeBuilder<SequenceOperation> {
        private Builder() { super(SequenceOperation.class); }
        private NodeBuilder<? extends Node> syntax;
        public Builder setSyntax(NodeBuilder<? extends Node> value) { syntax = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> selectedTarget;
        public Builder setSelectedTarget(NodeBuilder<? extends CallableDeclaration> value) { selectedTarget = value; return this; }
        public Builder setStringType(NodeBuilder<? extends CharacterSequenceType> value) { return setCharacterType(value); }
        private NodeBuilder<? extends CharacterSequenceType> characterType;
        public Builder setCharacterType(NodeBuilder<? extends CharacterSequenceType> value) { characterType = value; return this; }
        private NodeBuilder<? extends Node> destination;
        public Builder setDestination(NodeBuilder<? extends Node> value) { destination = value; return this; }
        private NodeBuilder<? extends Node> inputSequence;
        public Builder setInputSequence(NodeBuilder<? extends Node> value) { inputSequence = value; return this; }
        private NodeBuilder<? extends Node> outputDestination;
        public Builder setOutputDestination(NodeBuilder<? extends Node> value) { outputDestination = value; return this; }
        private NodeBuilder<? extends Node> first;
        public Builder setFirst(NodeBuilder<? extends Node> value) { first = value; return this; }
        private NodeBuilder<? extends Node> last;
        public Builder setLast(NodeBuilder<? extends Node> value) { last = value; return this; }
        private NodeBuilder<? extends Node> secondFirst;
        public Builder setSecondFirst(NodeBuilder<? extends Node> value) { secondFirst = value; return this; }
        private NodeBuilder<? extends Node> position;
        public Builder setPosition(NodeBuilder<? extends Node> value) { position = value; return this; }
        private NodeBuilder<? extends Node> count;
        public Builder setCount(NodeBuilder<? extends Node> value) { count = value; return this; }
        private NodeBuilder<? extends Node> initialValue;
        public Builder setInitialValue(NodeBuilder<? extends Node> value) { initialValue = value; return this; }
        private NodeBuilder<? extends Node> callable;
        public Builder setCallable(NodeBuilder<? extends Node> value) { callable = value; return this; }
        private NodeBuilder<? extends CallableDeclaration> callableTarget;
        public Builder setCallableTarget(NodeBuilder<? extends CallableDeclaration> value) { callableTarget = value; return this; }
        private NodeBuilder<? extends TypeNode> resultType;
        public Builder setResultType(NodeBuilder<? extends TypeNode> value) { resultType = value; return this; }
        private NodeBuilder<? extends TypeNode> accumulatorType;
        public Builder setAccumulatorType(NodeBuilder<? extends TypeNode> value) { accumulatorType = value; return this; }
        private List<NodeBuilder<? extends Node>> sources = new ArrayList<>();
        public Builder setSources(List<NodeBuilder<? extends Node>> value) { sources = new ArrayList<>(value); return this; }
        private List<NodeBuilder<? extends Node>> mutatedObjects = new ArrayList<>();
        public Builder setMutatedObjects(List<NodeBuilder<? extends Node>> value) { mutatedObjects = new ArrayList<>(value); return this; }
        private List<NodeBuilder<? extends Node>> callableEffects = new ArrayList<>();
        public Builder setCallableEffects(List<NodeBuilder<? extends Node>> value) { callableEffects = new ArrayList<>(value); return this; }
        private Direction direction = Direction.UNKNOWN;
        public Builder setDirection(Direction value) { direction = Objects.requireNonNull(value); return this; }
        private SizeEffect sizeEffect = SizeEffect.UNKNOWN;
        public Builder setSizeEffect(SizeEffect value) { sizeEffect = Objects.requireNonNull(value); return this; }
        private Kind kind = null;
        public Builder setKind(Kind value) { kind = Objects.requireNonNull(value); return this; }
        private LengthKind lengthKind = LengthKind.NONE;
        public Builder setLengthKind(LengthKind value) { lengthKind = Objects.requireNonNull(value); return this; }
        private ResultKind resultKind = ResultKind.NONE;
        public Builder setResultKind(ResultKind value) { resultKind = Objects.requireNonNull(value); return this; }
        private CallableEffect callableEffect = CallableEffect.NONE;
        public Builder setCallableEffect(CallableEffect value) { callableEffect = Objects.requireNonNull(value); return this; }
        private boolean callableMayThrow = false;
        public Builder setCallableMayThrow(boolean value) { callableMayThrow = value; return this; }
        private boolean writable = false;
        public Builder setWritable(boolean value) { writable = value; return this; }
        @Override SequenceOperation newNode() { return new SequenceOperation(); }
        @Override void fillNode(SequenceOperation n, BuildContext c) {
            n.syntax = c.copy(Objects.requireNonNull(syntax, "syntax"));
            n.selectedTarget = c.copy(Objects.requireNonNull(selectedTarget, "selectedTarget"));
            n.characterType = characterType == null ? null : c.copy(characterType);
            n.destination = destination == null ? null : c.copy(destination);
            n.inputSequence = inputSequence == null ? null : c.copy(inputSequence);
            n.outputDestination = outputDestination == null ? null : c.copy(outputDestination);
            n.first = first == null ? null : c.copy(first);
            n.last = last == null ? null : c.copy(last);
            n.secondFirst = secondFirst == null ? null : c.copy(secondFirst);
            n.position = position == null ? null : c.copy(position);
            n.count = count == null ? null : c.copy(count);
            n.initialValue = initialValue == null ? null : c.copy(initialValue);
            n.callable = callable == null ? null : c.copy(callable);
            n.callableTarget = callableTarget == null ? null : c.copy(callableTarget);
            n.resultType = resultType == null ? null : c.copy(resultType);
            n.accumulatorType = accumulatorType == null ? null : c.copy(accumulatorType);
            n.sources = c.copyAll(sources);
            n.mutatedObjects = c.copyAll(mutatedObjects);
            n.callableEffects = c.copyAll(callableEffects);
            n.direction = direction;
            n.sizeEffect = sizeEffect;
            n.kind = Objects.requireNonNull(kind, "kind");
            n.lengthKind = lengthKind;
            n.resultKind = resultKind;
            n.callableEffect = callableEffect;
            n.callableMayThrow = callableMayThrow;
            n.writable = writable;
            if (lengthKind == LengthKind.EXPLICIT_COUNT && n.count == null)
                throw new IllegalStateException("Explicit character length requires a count operand");
            if (lengthKind == LengthKind.RANGE && (n.first == null || n.last == null))
                throw new IllegalStateException("A character range requires both endpoints");
        }
    }
}
