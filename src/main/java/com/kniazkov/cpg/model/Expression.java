/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** A construct in value position. Includes assignments, calls and operations. An ExpressionStatement owns an expression in statement position. */
public abstract class Expression extends Node {
    ExpressionInfo expressionInfo;
    public final java.util.Optional<ExpressionInfo> semanticInfo() { return java.util.Optional.ofNullable(expressionInfo); }

    /** Construction is reserved for node implementations assembling a snapshot. */
    Expression() {
    }
}
