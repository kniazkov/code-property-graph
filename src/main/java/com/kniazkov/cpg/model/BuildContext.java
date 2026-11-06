/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns the identity map for one build. Each builder is mapped to a fresh shell
 * before its fields are populated, so a back edge finds the same shell.
 * After all reachable shells are filled, ownership is checked separately from
 * reference connectivity. Failure discards the session and its unfinished nodes. */
final class BuildContext {
    private final Map<NodeBuilder<?>, Node> nodes = new IdentityHashMap<>();

    /** An edited child invalidates enclosing derived expression facts until analysis reruns. */
    private boolean invalidateDependentFacts(Node node) {
        boolean changed = false;
        for (Node child : node.children()) changed |= invalidateDependentFacts(child);
        if (node instanceof CallExpression call)
            changed |= call.defaultArguments().stream().anyMatch(argument -> argument.expression().semanticInfo().isEmpty());
        if (node instanceof Expression expression) {
            if (changed && expression.expressionInfo != null) {
                expression.expressionInfo = null;
                if (expression instanceof ConversionExpression conversion) conversion.clearDerivedSource();
            }
            return changed || expression.expressionInfo == null;
        }
        return changed;
    }

    /** Syntax edits invalidate copied operator bindings, never freshly supplied analysis. */
    private boolean invalidateOperatorBindings(Node node, Set<Node> edited, Set<Node> copied, boolean declarationsChanged) {
        boolean changed = edited.contains(node);
        for (Node child : node.children()) changed |= invalidateOperatorBindings(child, edited, copied, declarationsChanged);
        if (node instanceof Expression expression && copied.contains(node) && (changed || declarationsChanged)) {
            expression.operatorCandidates = List.of();
            expression.operatorResolution = CallExpression.Resolution.UNRESOLVED;
            expression.expressionInfo = null;
        }
        return changed;
    }

    /** Copies the reachable graph, validates ownership, then publishes its root. */
    <T extends Node> T build(NodeBuilder<T> root) {
        T result = copy(root);
        if (result instanceof Project project) {
            var discarded = project.invalidateTemplateInstances();
            int semanticCount = project.dynamicArraySemantics().size() + project.dynamicArrayTypes().size() + project.containerSemantics().size() + project.containerTypes().size() + project.sequenceSemantics().size() + project.characterSequenceTypes().size();
            project.validateDynamicArraySemantics();
            project.validateContainerSemantics();
            project.validateSequenceSemantics();
            if (!discarded.isEmpty() || semanticCount != project.dynamicArraySemantics().size() + project.dynamicArrayTypes().size() + project.containerSemantics().size() + project.containerTypes().size() + project.sequenceSemantics().size() + project.characterSequenceTypes().size()) {
                Set<Node> retained = Collections.newSetFromMap(new IdentityHashMap<>());
                collect(project, retained);
                nodes.entrySet().removeIf(entry -> !retained.contains(entry.getValue()));
            }
        }
        for (Node node : nodes.values()) if (node instanceof CallExpression call) call.validateDefaultArguments();
        validateStructure(result);
        invalidateDependentFacts(result);
        Set<Node> edited = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> copied = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean declarationsChanged = false;
        for (var entry : nodes.entrySet()) {
            if (entry.getKey().syntaxEdited) edited.add(entry.getValue());
            if (entry.getKey().operatorBindingCopied) copied.add(entry.getValue());
            if (entry.getKey().syntaxEdited && entry.getKey().editIdentity != null
                    && entry.getValue() instanceof CallableDeclaration) declarationsChanged = true;
        }
        invalidateOperatorBindings(result, edited, copied, declarationsChanged);
        if (result instanceof Project project && root instanceof Project.Builder builder)
            project.inheritPassHistory(builder.historySource);
        return result;
    }

    /** Returns the registered shell on a repeated visit. On first encounter allocates, registers and fills a shell using this session. */
    <T extends Node> T copy(NodeBuilder<T> builder) {
        if (builder == null) {
            throw new IllegalStateException("Required child builder is missing");
        }
        Node existing = nodes.get(builder);
        if (existing != null) {
            return builder.nodeType().cast(existing);
        }
        T node = builder.newNode();
        if (builder.editIdentity != null) node.editIdentity = builder.editIdentity;
        node.origin = builder.origin;
        node.provenance = builder.provenance;
        node.transformationProvenance = builder.transformationProvenance;
        if (node instanceof TypeNode type) type.representation = builder.typeRepresentation;
        if (node instanceof Expression expression) { expression.expressionInfo = builder.expressionInfo; expression.dependent = builder.dependent; }
        nodes.put(builder, node);
        builder.fillNode(node, this);
        node.commentReferences = copyAll(builder.commentReferences);
        if (node instanceof Expression expression) {
            expression.operatorCandidates = copyAll(builder.operatorCandidates);
            expression.operatorResolution = builder.operatorResolution;
        }
        if (node instanceof Declaration declaration) {
            declaration.templateOrigin = builder.templateOrigin == null ? null : copy(builder.templateOrigin);
            declaration.typeIdentity = builder.declarationTypeIdentity;
            declaration.linkage = builder.declarationLinkage;
            declaration.languageLinkage = builder.languageLinkage;
            declaration.access = builder.declarationAccess;
            declaration.friend = builder.declarationFriend;
            declaration.definitionOccurrence = builder.declarationDefinition;
            declaration.previousDeclaration = builder.previousDeclaration == null ? null : copy(builder.previousDeclaration);
            declaration.definition = builder.definition == null ? null : copy(builder.definition);
        }
        return node;
    }

    /** Copies ordered child builders in the same session and freezes the resulting list. */
    <T extends Node> List<T> copyAll(List<? extends NodeBuilder<? extends T>> builders) {
        List<T> result = new ArrayList<>();
        for (NodeBuilder<? extends T> builder : builders) {
            result.add(copy(builder));
        }
        return List.copyOf(result);
    }

    /** Checks every reachable component. A Project additionally requires each reference target to be owned by its syntax tree. */
    private void validateStructure(Node root) {
        Map<Node, Node> parents = new IdentityHashMap<>();
        Set<Node> active = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> complete = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node : nodes.values()) {
            visit(node, parents, active, complete);
        }
        if (root instanceof Project project) {
            for (var file : project.files()) file.provenance().ifPresent(provenance -> {
                for (var definition : provenance.unit().definitions()) {
                    if ((definition.originKind().equals("command-line") || definition.originKind().equals("predefined"))
                            && project.externalInput(definition.sourceKey()).isEmpty())
                        throw new IllegalStateException("Preprocessing source outside project: " + definition.sourceKey());
                }
            });
            Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>());
            collect(root, owned);
            for (Node node : owned) {
                for(Node reference:java.util.stream.Stream.concat(node.references().stream(),node.commentReferences().stream()).toList())if(!owned.contains(reference))
                    throw new IllegalStateException("Project reference points outside its syntax tree: "+node.getClass().getSimpleName()+" -> "+reference.getClass().getSimpleName()+" "+(node instanceof NamedType t?t.name():""));
            }
            if (owned.size() != nodes.size()) {
                throw new IllegalStateException("Project reference points outside its syntax tree");
            }
        }
    }

    /** Depth-first ownership check. The active path detects cycles, the parent map detects duplicate positions, and the completed set avoids rescanning subtrees. */
    private void visit(Node node, Map<Node, Node> parents, Set<Node> active, Set<Node> complete) {
        if (active.contains(node)) {
            throw new IllegalStateException("Cycle in syntax children");
        }
        if (complete.contains(node)) {
            return;
        }
        active.add(node);
        for (Node child : node.children()) {
            if (parents.put(child, node) != null) {
                throw new IllegalStateException("Syntax child has multiple owners or positions");
            }
            visit(child, parents, active, complete);
        }
        active.remove(node);
        complete.add(node);
    }

    /** Collects descendants of a verified acyclic ownership tree for the project boundary check. */
    private void collect(Node node, Set<Node> result) {
        result.add(node);
        for (Node child : node.children()) {
            collect(child, result);
        }
    }
}
