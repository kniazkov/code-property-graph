/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Owns the inverse identity map for one edit. A node registers its mutable
 * counterpart before copying children and references. Back edges therefore
 * resolve to the same builder, including cycles that cross file boundaries.
 * Independent edit calls allocate independent maps. */
public final class EditContext {
    private EditContext referenceContext;
    String passId;
    private boolean independentLineage;
    private java.util.Set<Node> localDeclarations = java.util.Set.of();
    /** Independent owned syntax copy with shared external declaration references. */
    EditContext fork(Node owned, Map<Node, Node> substitutions) {
        var result = new EditContext(substitutions, java.util.Set.of());
        result.referenceContext = this; result.independentLineage=true;
        result.localDeclarations = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        owned.walk(new NodeVisitor() { @Override public void visit(Declaration node) { result.localDeclarations.add(node); } });
        return result;
    }
    private final Map<Node, NodeBuilder<?>> builders = new IdentityHashMap<>();

    private final Map<Node, Node> replacements;
    private final java.util.Set<Node> removed;

    private final Map<Expression, List<ProjectEditor.ConversionStep>> conversions;

    /** A parser occurrence owns new syntax but shares references with its assembly. */
    static EditContext syntaxCopy(Map<Node, NodeBuilder<?>> external) {
        var context = new EditContext();
        context.independentLineage = true;
        context.builders.putAll(external);
        return context;
    }

    public EditContext() {
        this(Map.of(), java.util.Set.of());
    }

    EditContext(Map<Node, Node> replacements, java.util.Set<Node> removed) {
        this(replacements, removed, Map.of());
    }

    EditContext(Map<Node, Node> replacements, java.util.Set<Node> removed, Map<Expression, List<ProjectEditor.ConversionStep>> conversions) {
        this.conversions = conversions;
        this.replacements = replacements;
        this.removed = removed;
    }

    /** Applies scheduled replacements at an edge and verifies the destination slot type. */
    @SuppressWarnings("unchecked")
    <T extends Node> NodeBuilder<? extends T> edge(Node node, Class<T> type) {
        if (referenceContext != null && node instanceof Declaration && !localDeclarations.contains(node)) return referenceContext.edge(node, type);
        Node replacement = replacements.getOrDefault(node, node);
        NodeBuilder<? extends T> result;
        if (replacement != node && (replacement instanceof TypeNode || replacement instanceof Expression) && referenceContext != null) {
            var occurrence = new EditContext(); occurrence.referenceContext = referenceContext;
            result = occurrence.copy(type.cast(replacement));
        } else result = copy(type.cast(replacement));
        if (replacement != node) {
            if (result.origin == null) result.origin = node.origin;
            if (result.provenance == null) result.provenance = node.provenance;
        }
        if (node instanceof Expression expression && conversions.containsKey(expression)) {
            NodeBuilder<? extends Expression> operand = (NodeBuilder<? extends Expression>) result;
            for (var step : conversions.get(expression)) {
                var wrapper = ConversionExpression.create().setOperand(operand).setTargetType(copyTypeOccurrence(step.targetType()))
                        .setSourceType(step.sourceType()).setKind(step.kind()).setExplicit(false);
                if (step.userDefined() != null) wrapper.setOperatorTarget(edge(step.userDefined(), CallableDeclaration.class));
                if (node.origin != null) wrapper.setRange(node.origin);
                if (node.provenance != null) wrapper.setProvenance(node.provenance);
                if (passId != null) wrapper.addTransformationProvenance(passId, "source-construct", node);
                wrapper.syntaxEdited = false; // Derived wrapper does not change the written operands.
                operand = wrapper;
            }
            return (NodeBuilder<? extends T>) operand;
        }
        return result;
    }

    /** A fresh owned type occurrence shares declaration references with the enclosing edit. */
    private NodeBuilder<? extends TypeNode> copyTypeOccurrence(TypeNode type) {
        var context = new EditContext(replacements, removed);
        context.referenceContext = this;
        return context.edge(type, TypeNode.class);
    }

    /** Starts from a concrete node and returns its corresponding concrete builder through a checked cast. */
    public <T extends Node, B extends NodeBuilder<T>> B edit(T node, Class<B> builderType) {
        return builderType.cast(copy(node));
    }

    /** Reuses a registered builder or delegates to the node to register and populate one. The register method checks the correspondence on insertion. */
    @SuppressWarnings("unchecked")
    public <T extends Node> NodeBuilder<T> copy(T node) {
        NodeBuilder<?> existing = builders.get(node);
        if (existing != null) {
            // Registration below guarantees the node/builder type correspondence.
            return (NodeBuilder<T>) existing;
        }
        NodeBuilder<?> builder = node.copyBuilder(this);
        return (NodeBuilder<T>) builder;
    }

    /** Registers the one builder whose runtime node type matches this node, before following any outgoing edge. */
    void register(Node node, NodeBuilder<?> builder) {
        if (builder.nodeType() != node.getClass()) {
            throw new IllegalStateException("Builder does not match node type");
        }
        builder.editIdentity = independentLineage ? new Object() : node.editIdentity;
        builder.transformationProvenance = node.transformationProvenance;
        builder.origin = node.origin;
        builder.provenance = node.provenance;
        if (node instanceof TypeNode type) builder.typeRepresentation = type.semanticType().orElse(null);
        if (node instanceof Expression expression) { builder.expressionInfo = expression.semanticInfo().orElse(null); builder.dependent = expression.dependent(); }
        if (builders.putIfAbsent(node, builder) != null) {
            throw new IllegalStateException("Node already registered");
        }
        builder.commentReferences = (referenceContext == null ? this : referenceContext).copyAll(node.commentReferences(), Comment.class);
        if (node instanceof Expression expression) {
            builder.operatorCandidates = copyAll(expression.operatorCandidates(), CallableDeclaration.class);
            builder.operatorResolution = expression.operatorResolution();
            builder.operatorBindingCopied = true;
        }
        if (node instanceof Declaration declaration) {
            builder.templateOrigin = declaration.templateOrigin().isEmpty() ? null : edge(declaration.templateOrigin().orElseThrow(), Declaration.class);
            builder.declarationTypeIdentity = declaration.typeIdentity().orElse(null);
            builder.declarationLinkage = declaration.linkage();
            builder.languageLinkage = declaration.languageLinkage();
            builder.declarationAccess = declaration.access().orElse(null);
            builder.declarationFriend = declaration.isFriend();
            builder.declarationDefinition = declaration.isDefinition();
            builder.previousDeclaration = declaration.previousDeclaration().isEmpty() ? null : edge(declaration.previousDeclaration().orElseThrow(), Declaration.class);
            builder.definition = declaration.definition().isEmpty() ? null : edge(declaration.definition().orElseThrow(), Declaration.class);
        }
    }

    /** Copies an ordered child list and returns the mutable list owned by the destination builder. */
    public <T extends Node> List<NodeBuilder<? extends T>> copyAll(List<T> nodes, Class<T> type) {
        List<NodeBuilder<? extends T>> result = new ArrayList<>();
        for (T node : nodes) {
            if (!removed.contains(node)) result.add(edge(node, type));
        }
        return result;
    }
}
