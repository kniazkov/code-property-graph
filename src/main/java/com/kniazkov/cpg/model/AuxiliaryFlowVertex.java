/* Code Property Graph: non-syntax flow operations. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** A flow-owned operation, never an AST child or an explicit source-level call. */
public final class AuxiliaryFlowVertex implements FlowVertex {
    public enum Kind { EXCEPTIONAL_EXIT, TERMINATE_EXIT, IMPLICIT_DESTRUCTOR, IMPLICIT_CONSTRUCTOR, ARRAY_UNWIND, ARRAY_DEALLOCATION }
    private final CallableDeclaration owner;
    private final int index;
    private final Kind kind;
    private final Node object;
    private final OptionalInt elementIndex;
    private final CallableDeclaration destructor;
    public AuxiliaryFlowVertex(CallableDeclaration owner, int index, Kind kind, Node object, CallableDeclaration destructor) {
        this(owner, index, kind, object, destructor, OptionalInt.empty());
    }
    /** An indexed lifetime operation refers to one element of the object array. */
    public AuxiliaryFlowVertex(CallableDeclaration owner, int index, Kind kind, Node object, CallableDeclaration destructor, OptionalInt elementIndex) {
        this.elementIndex = Objects.requireNonNull(elementIndex);
        if (elementIndex.isPresent() && (elementIndex.getAsInt() < 0 || kind != Kind.IMPLICIT_CONSTRUCTOR && kind != Kind.IMPLICIT_DESTRUCTOR))
            throw new IllegalArgumentException("Invalid array element operation");
        this.owner = Objects.requireNonNull(owner); this.index = index; this.kind = Objects.requireNonNull(kind);
        this.object = object; this.destructor = destructor;
        if (index < 0 || (kind == Kind.IMPLICIT_DESTRUCTOR || kind == Kind.IMPLICIT_CONSTRUCTOR || kind == Kind.ARRAY_UNWIND || kind == Kind.ARRAY_DEALLOCATION) && object == null || (kind == Kind.EXCEPTIONAL_EXIT || kind == Kind.TERMINATE_EXIT) && (object != null || destructor != null))
            throw new IllegalArgumentException("Invalid auxiliary flow operation");
    }
    public CallableDeclaration owner() { return owner; }
    public int index() { return index; }
    public Kind kind() { return kind; }
    /** Zero-based element index; absent for scalar and whole-allocation events. */
    public OptionalInt elementIndex() { return elementIndex; }
    public Optional<Node> object() { return Optional.ofNullable(object); }
    public Optional<CallableDeclaration> destructor() { return kind == Kind.IMPLICIT_DESTRUCTOR ? Optional.ofNullable(destructor) : Optional.empty(); }
    public Optional<CallableDeclaration> constructor() { return kind == Kind.IMPLICIT_CONSTRUCTOR ? Optional.ofNullable(destructor) : Optional.empty(); }
}
