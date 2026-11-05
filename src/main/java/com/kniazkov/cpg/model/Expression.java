/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/** A construct in value position. Includes assignments, calls and operations. An ExpressionStatement owns an expression in statement position. */
public abstract class Expression extends Node {
    boolean dependent;
    /** True when lookup or type depends on an unsubstituted template parameter. */
    public final boolean dependent() { return dependent; }
    ExpressionInfo expressionInfo;
    java.util.List<CallableDeclaration> operatorCandidates = java.util.List.of();
    CallExpression.Resolution operatorResolution = CallExpression.Resolution.UNRESOLVED;
    /** Selected user-defined operator, independent of the written expression syntax. */
    public final java.util.Optional<CallableDeclaration> operatorTarget() {
        return operatorResolution == CallExpression.Resolution.RESOLVED
                ? java.util.Optional.of(operatorCandidates.getFirst()) : java.util.Optional.empty();
    }
    public final java.util.List<CallableDeclaration> operatorCandidates() { return operatorCandidates; }
    public final CallExpression.Resolution operatorResolution() { return operatorResolution; }
    @Override public java.util.List<Node> references() { return java.util.List.copyOf(operatorCandidates); }

    public final java.util.Optional<ExpressionInfo> semanticInfo() { return java.util.Optional.ofNullable(expressionInfo); }

    /** Construction is reserved for node implementations assembling a snapshot. */
    Expression() {
    }
}
