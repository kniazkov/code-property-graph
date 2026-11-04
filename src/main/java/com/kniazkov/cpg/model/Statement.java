/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** A construct in statement position. Its children encode conditions, bodies or expressions in syntax order. */
public abstract class Statement extends Node {
    /** Construction is reserved for node implementations assembling a snapshot. */
    Statement() {
    }
}
