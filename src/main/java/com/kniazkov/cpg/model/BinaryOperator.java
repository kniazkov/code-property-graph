/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** Supported operator identities, without semantic equivalence guarantees. */
public enum BinaryOperator {
    ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, SHIFT_LEFT, SHIFT_RIGHT,
    EQUAL, NOT_EQUAL, LESS, LESS_OR_EQUAL, GREATER, GREATER_OR_EQUAL
}
