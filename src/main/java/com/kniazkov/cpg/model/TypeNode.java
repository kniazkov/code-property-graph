/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Type syntax written in the source. A missing annotation is represented by the optional child on the owning declaration. */
public abstract class TypeNode extends Node {
    TypeRepresentation representation;

    /** Returns green facts only while they still describe the current type syntax.
     * Editing an element, bound, qualifier or alias target invalidates stale facts. */
    public final java.util.Optional<TypeRepresentation> semanticType() {
        return representation != null && representation.canonicalIdentity().equals(canonicalIdentity())
                ? java.util.Optional.of(representation) : java.util.Optional.empty();
    }

    /** Resolves outer typedef names without discarding the written syntax. */
    public final TypeNode canonicalType() { return TypeIdentity.canonicalType(this); }

    /** Structural C++ identity; target widths never equate distinct fundamental types. */
    public final String canonicalIdentity() { return TypeIdentity.canonicalIdentity(this); }

    @Override public abstract NodeBuilder<? extends TypeNode> edit();

    /** Construction is reserved for node implementations assembling a snapshot. */
    TypeNode() {
    }
}
