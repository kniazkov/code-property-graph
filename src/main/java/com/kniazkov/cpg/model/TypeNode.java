/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Type syntax written in the source. A missing annotation is represented by the optional child on the owning declaration. */
public abstract class TypeNode extends Node {
    /** Construction is reserved for node implementations assembling a snapshot. */
    TypeNode() {
    }
}
