/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Objects;

/** Mutable counterpart of one immutable node. Subclasses hold builders for children
 * and references and may be incomplete while a graph is being assembled.
 * Reusing a builder as a reference target preserves object identity in the snapshot.
 * @param <T> exact snapshot class allocated by this builder */
public abstract class NodeBuilder<T extends Node> {
    boolean dependent;
    public NodeBuilder<T> setDependent(boolean value) { dependent = value; return this; }
    ExpressionInfo expressionInfo;
    boolean syntaxEdited, operatorBindingCopied;
    public final void clearExpressionInfo() { expressionInfo = null; syntaxEdited = true; clearOperatorBinding(); }
    java.util.List<NodeBuilder<? extends CallableDeclaration>> operatorCandidates = java.util.List.of();
    CallExpression.Resolution operatorResolution = CallExpression.Resolution.UNRESOLVED;
    public final void clearOperatorBinding() {
        operatorCandidates = java.util.List.of(); operatorResolution = CallExpression.Resolution.UNRESOLVED;
    }
    public final void setOperatorTarget(NodeBuilder<? extends CallableDeclaration> target) {
        setOperatorCandidates(java.util.List.of(target), CallExpression.Resolution.RESOLVED);
    }
    /** UNRESOLVED may retain known candidates when operand types prevent selection.
     * AMBIGUOUS may have no declaration candidates when only built-in alternatives compete. */
    public final void setOperatorCandidates(java.util.List<? extends NodeBuilder<? extends CallableDeclaration>> values,
            CallExpression.Resolution status) {
        if (!Expression.class.isAssignableFrom(nodeType)) throw new IllegalStateException("Not an expression builder");
        java.util.List<NodeBuilder<? extends CallableDeclaration>> candidates = java.util.List.copyOf(values);
        Objects.requireNonNull(status);
        if (status == CallExpression.Resolution.RESOLVED && candidates.size() != 1
                || status == CallExpression.Resolution.ODR_CONFLICT && candidates.size() < 2)
            throw new IllegalArgumentException("Candidate count does not match resolution status");
        operatorBindingCopied = false; operatorCandidates = candidates; operatorResolution = status; expressionInfo = null;
    }
    public final void setExpressionInfo(ExpressionInfo value) {
        if (!Expression.class.isAssignableFrom(nodeType)) throw new IllegalStateException("Not an expression builder");
        expressionInfo = Objects.requireNonNull(value);
    }
    String declarationTypeIdentity;
    public final void setTypeIdentity(String value) { declarationTypeIdentity = Objects.requireNonNull(value); }
    public final void clearTypeIdentity() { declarationTypeIdentity = null; }
    Declaration.Linkage declarationLinkage = Declaration.Linkage.NONE;
    String languageLinkage = "C++";
    boolean declarationFriend;
    public final void setFriend(boolean value) { declarationFriend = value; }
    String declarationAccess;
    public final void setAccess(String value) {
        if (!java.util.Set.of("public", "protected", "private").contains(value)) throw new IllegalArgumentException("Invalid member access");
        declarationAccess = value;
    }
    public final void clearAccess() { declarationAccess = null; }
    boolean declarationDefinition = true;
    NodeBuilder<? extends Declaration> previousDeclaration, definition;
    public final void setLinkage(Declaration.Linkage value) { declarationLinkage = Objects.requireNonNull(value); }
    public final void setLanguageLinkage(String value) { languageLinkage = Objects.requireNonNull(value); }
    public final void setDeclarationDefinition(boolean value) { declarationDefinition = value; }
    public final void setPreviousDeclaration(NodeBuilder<? extends Declaration> value) { previousDeclaration = Objects.requireNonNull(value); }
    public final void clearPreviousDeclaration() { previousDeclaration = null; }
    public final void setDefinition(NodeBuilder<? extends Declaration> value) { definition = Objects.requireNonNull(value); }
    public final void clearDefinition() { definition = null; }
    Object editIdentity;
    java.util.List<NodeBuilder<? extends Comment>> commentReferences = java.util.List.of();
    public final void setCommentReferences(java.util.List<? extends NodeBuilder<? extends Comment>> values) {
        commentReferences = java.util.List.copyOf(values);
    }
    public final void addCommentReference(NodeBuilder<? extends Comment> value) {
        var values = new java.util.ArrayList<>(commentReferences);
        values.add(Objects.requireNonNull(value)); commentReferences = java.util.List.copyOf(values);
    }
    SourceRange origin;
    TokenProvenance provenance;
    TypeRepresentation typeRepresentation;

    /** Assigns immutable green facts; permitted only on type builders. */
    public final void setTypeRepresentation(TypeRepresentation value) {
        if (!TypeNode.class.isAssignableFrom(nodeType)) throw new IllegalStateException("Not a type builder");
        typeRepresentation = Objects.requireNonNull(value);
    }


    /** Assigns immutable source-token provenance to the next snapshot. */
    public final void setProvenance(TokenProvenance value) { provenance = Objects.requireNonNull(value); }

    /** Assigns the source span copied into the next immutable snapshot. */
    public final void setRange(SourceRange range) { origin = Objects.requireNonNull(range); }

    private final Class<T> nodeType;

    /** Records the runtime type used to retrieve a previously allocated shell. */
    NodeBuilder(Class<T> nodeType) {
        this.nodeType = Objects.requireNonNull(nodeType);
    }

    /** Starts one assembly session for the reachable builders and validates the resulting ownership tree before returning the root. */
    public final T build() {
        return new BuildContext().build(this);
    }

    /** Provides the checked conversion for identity-map lookups during cyclic assembly. */
    final Class<T> nodeType() {
        return nodeType;
    }

    /** Allocates an unpopulated shell for registration before any outgoing edge is followed. */
    abstract T newNode();

    /** Copies state into the registered shell. Every outgoing edge must use the same context. */
    abstract void fillNode(T node, BuildContext context);

    /** Validates mandatory text at build time and names the missing field in the diagnostic. */
    static String requiredText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(field + " is required");
        }
        return text;
    }
}
