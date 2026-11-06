/* Snapshot-local pass invalidation inputs. MIT License. */
package com.kniazkov.cpg.model;

import com.kniazkov.cpg.generation.Source;
import java.util.*;

/** Written syntax and bindings, excluding diagnostics and derived green overlays. */
final class PassInputs {
    final List<Object> global;
    final Map<Object, List<Object>> functions = new IdentityHashMap<>();

    PassInputs(Project project) {
        var inputs = new ArrayList<Object>();
        inputs.add(project.build());
        for (SourceFile file : project.files()) collectFile(file, inputs);
        for (SourceFile file : project.librarySources()) collectFile(file, inputs);
        // Instantiated declarations are separate callable occurrences, never matched by name.
        for (var instance : project.templateInstances()) collectFunctions(instance, Language.CPP);
        for (var instance : project.classTemplateInstances()) collectFunctions(instance, Language.CPP);
        global = List.copyOf(inputs);
    }

    private void collectFile(SourceFile file, List<Object> inputs) {
        inputs.add(file.editIdentity); inputs.add(file.path()); inputs.add(file.language());
        inputs.add(file.variantId());
        inputs.add(com.kniazkov.cpg.analysis.TypeSemantics.profile(file).stableIdentity());
        for (Node item : file.items()) global(item, file.language(), inputs);
        collectFunctions(file, file.language());
    }

    private void collectFunctions(Node root, Language language) {
        root.walk(new NodeVisitor() { @Override public void visit(CallableDeclaration function) {
            if (function.body().isEmpty()) return;
            var facts = new ArrayList<Object>();
            facts.add(render(function, language));
            facts.add(function.bodyComplete());
            references(function, facts);
            functions.put(function.editIdentity, List.copyOf(facts));
        }});
    }

    private static void global(Node node, Language language, List<Object> facts) {
        facts.add(node.getClass()); facts.add(node.editIdentity);
        if (node instanceof Declaration declaration) declarationFacts(declaration, facts);
        if (node instanceof CallableDeclaration function) {
            facts.add(function.name()); facts.add(function.kind()); facts.add(function.modifiers());
            facts.add(function.variadic()); facts.add(function.isConst()); facts.add(function.isVolatile());
            facts.add(function.isPureVirtual()); facts.add(function.hasExceptionSpecification());
            facts.add(function.templateKind()); facts.add(function.exportedTemplate());
            facts.add(function.body().isPresent());
            for (Node child : function.children()) {
                if (child == function.body().orElse(null) || child == function.exit()
                        || function.initializers().contains(child) || function.handlers().contains(child)) continue;
                facts.add(render(child, language));
                references(child, facts);
            }
        } else if (node instanceof ClassDeclaration declaration) {
            facts.add(declaration.name()); facts.add(declaration.keyword()); facts.add(declaration.modifiers());
            facts.add(declaration.templateKind()); facts.add(declaration.exportedTemplate());
            facts.add(declaration.memberAccess());
            declaration.children().forEach(child -> global(child, language, facts));
        } else if (node instanceof NamespaceDeclaration namespace) {
            facts.add(namespace.name()); facts.add(namespace.anonymous());
            namespace.children().forEach(child -> global(child, language, facts));
        } else {
            facts.add(render(node, language));
            references(node, facts);
        }
    }

    private static void references(Node node, List<Object> facts) {
        // Implicit wrappers are derived facts. Their insertion does not change written inputs.
        if (node instanceof ConversionExpression conversion && !conversion.isExplicit()) {
            references(conversion.operand(), facts);
            return;
        }
        facts.add(node.getClass());
        facts.add(node.references().stream().map(target -> target.editIdentity).toList());
        if (node instanceof Declaration declaration) declarationFacts(declaration, facts);
        if (node instanceof Expression expression) {
            facts.add(expression.dependent());
            facts.add(expression.operatorResolution());
            facts.add(expression.operatorCandidates().stream().map(target -> target.editIdentity).toList());
        }
        if (node instanceof CallExpression call) {
            facts.add(call.resolution()); facts.add(call.target().map(target -> target.editIdentity));
            facts.add(call.candidates().stream().map(target -> target.editIdentity).toList());
            facts.add(call.isVirtualDispatch()); facts.add(call.virtualTargetsComplete());
            facts.add(call.virtualTargets().stream().map(target -> target.editIdentity).toList());
            facts.add(call.defaultArguments().stream().map(CallExpression.DefaultArgument::parameterIndex).toList());
        }
        if (node instanceof NamedType type) {
            facts.add(type.dependent()); facts.add(type.completeAtUse());
        }
        node.children().forEach(child -> references(child, facts));
    }

    private static void declarationFacts(Declaration declaration, List<Object> facts) {
        facts.add(declaration.access()); facts.add(declaration.isFriend());
        facts.add(declaration.languageLinkage()); facts.add(declaration.linkage());
        facts.add(declaration.isDefinition()); facts.add(declaration.typeIdentity());
    }

    private static Object render(Node node, Language language) {
        try { return Source.render(node, language); }
        catch (IllegalArgumentException | IllegalStateException | NoSuchElementException unsupported) {
            // Recovery syntax cannot safely claim unchanged semantics.
            return new Object();
        }
    }
}
