/* Code Property Graph: non-syntax flow operations. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** A flow-owned operation, never an AST child or an explicit source-level call. */
public final class AuxiliaryFlowVertex implements FlowVertex {
    public enum Kind { EXCEPTIONAL_EXIT, TERMINATE_EXIT, IMPLICIT_DESTRUCTOR, IMPLICIT_CONSTRUCTOR }
    private final CallableDeclaration owner;
    private final int index;
    private final Kind kind;
    private final Node object;
    private final CallableDeclaration destructor;
    public AuxiliaryFlowVertex(CallableDeclaration owner, int index, Kind kind, Node object, CallableDeclaration destructor) {
        this.owner = Objects.requireNonNull(owner); this.index = index; this.kind = Objects.requireNonNull(kind);
        this.object = object; this.destructor = destructor;
        if (index < 0 || (kind == Kind.IMPLICIT_DESTRUCTOR || kind == Kind.IMPLICIT_CONSTRUCTOR) && object == null || (kind == Kind.EXCEPTIONAL_EXIT || kind == Kind.TERMINATE_EXIT) && (object != null || destructor != null))
            throw new IllegalArgumentException("Invalid auxiliary flow operation");
    }
    public CallableDeclaration owner() { return owner; }
    public int index() { return index; }
    public Kind kind() { return kind; }
    public Optional<Node> object() { return Optional.ofNullable(object); }
    public Optional<CallableDeclaration> destructor() { return kind == Kind.IMPLICIT_DESTRUCTOR ? Optional.ofNullable(destructor) : Optional.empty(); }
    public Optional<CallableDeclaration> constructor() { return kind == Kind.IMPLICIT_CONSTRUCTOR ? Optional.ofNullable(destructor) : Optional.empty(); }
}
