/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** A construct that introduces a named entity. Resolved uses may reference its identity across files. */
public abstract class Declaration extends Node {
    /** Construction is reserved for node implementations assembling a snapshot. */
    Declaration() {
    }
}
