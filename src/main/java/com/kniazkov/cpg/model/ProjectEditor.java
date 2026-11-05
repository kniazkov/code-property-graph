/* Code Property Graph: apply edits to a complete typed snapshot. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Stages replacements and bindings against one source snapshot. Each build creates independent objects. */
public final class ProjectEditor {
    private final Project source;
    private final Set<Node> members = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Node, Node> replacements = new IdentityHashMap<>();
    private final Set<Node> removed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<TypeNode, TypeRepresentation> typeRepresentations = new IdentityHashMap<>();

    /** Records green facts without cloning referenced typedef declarations outside the project. */
    public void describe(TypeNode type, TypeRepresentation representation) {
        requireMember(type);
        typeRepresentations.put(type, Objects.requireNonNull(representation));
    }

    private final Map<ConversionExpression, String> conversionSources = new IdentityHashMap<>();
    public void describeSource(ConversionExpression expression, String type) {
        requireMember(expression); conversionSources.put(expression, Objects.requireNonNull(type));
    }
    private final Map<Expression, ExpressionInfo> expressions = new IdentityHashMap<>();
    public void describe(Expression expression, ExpressionInfo info) {
        requireMember(expression); expressions.put(expression, Objects.requireNonNull(info));
    }

    /** Adds semantic wrappers without independently cloning the operand's declaration links. */
    public record ConversionStep(String sourceType, TypeNode targetType, ConversionExpression.Kind kind) {
        public ConversionStep { Objects.requireNonNull(sourceType); Objects.requireNonNull(targetType); Objects.requireNonNull(kind); }
    }
    private final Map<Expression, List<ConversionStep>> conversions = new IdentityHashMap<>();
    public void convert(Expression expression, List<ConversionStep> steps) {
        requireMember(expression); if (!steps.isEmpty()) conversions.put(expression, List.copyOf(steps));
    }

    private record DeclarationFacts(Declaration.Linkage linkage, boolean definitionOccurrence,
            Declaration previous, Declaration definition) {}
    private final Map<Declaration, DeclarationFacts> declarations = new IdentityHashMap<>();
    private record TypeBinding(Declaration declaration, boolean complete, List<TypeResolutionStep> steps) {}
    private final Map<Declaration, String> typeIdentities = new IdentityHashMap<>();
    private final Map<Declaration, String> languageLinkages = new IdentityHashMap<>();
    public void identifyType(Declaration declaration, String identity) {
        requireMember(declaration); typeIdentities.put(declaration, Objects.requireNonNull(identity));
    }
    public void languageLinkage(Declaration declaration, String language) {
        requireMember(declaration); languageLinkages.put(declaration, Objects.requireNonNull(language));
    }
    private final Map<NamedType, TypeBinding> types = new IdentityHashMap<>();
    private final Map<Declaration, Declaration> imports = new IdentityHashMap<>();
    private final Map<Node, List<Diagnostic>> diagnostics = new IdentityHashMap<>();

    /** Stages declaration identity without mutating either snapshot. Null relations clear old edges. */
    public void declare(Declaration declaration, Declaration.Linkage linkage, boolean isDefinition,
            Declaration previous, Declaration definition) {
        requireMember(declaration);
        if (previous != null) requireMember(previous);
        if (definition != null) requireMember(definition);
        declarations.put(declaration, new DeclarationFacts(Objects.requireNonNull(linkage), isDefinition, previous, definition));
    }

    /** Records the declaration visible at this exact written type occurrence. */
    public void bind(NamedType type, Declaration declaration, boolean complete, List<TypeResolutionStep> steps) {
        requireMember(type);
        if (declaration != null) requireMember(declaration);
        steps.forEach(step -> { requireMember(step.via()); requireMember(step.target()); });
        types.put(type, new TypeBinding(declaration, complete, List.copyOf(steps)));
    }

    /** Binds namespace aliases and using nodes. A null target clears a stale import. */
    public void bindImport(Declaration declaration, Declaration target) {
        requireMember(declaration);
        if (target != null) requireMember(target);
        if (!(declaration instanceof NamespaceAliasDeclaration || declaration instanceof UsingDeclaration
                || declaration instanceof UsingDirective)) throw new IllegalArgumentException("Not an import declaration");
        imports.put(declaration, target);
    }

    /** Attaches an analysis finding to the newly built node, never the input snapshot. */
    public void diagnose(Node node, Diagnostic diagnostic) {
        requireMember(node);
        diagnostics.computeIfAbsent(node, ignored -> new ArrayList<>()).add(Objects.requireNonNull(diagnostic));
    }

    public void unbind(NameExpression use) { requireMember(use); names.put(use, null); }

    private final Map<MemberInitializer, Declaration> initializerNames = new IdentityHashMap<>();
    public void bind(MemberInitializer use, Declaration target) { requireMember(use); if (target != null) requireMember(target); initializerNames.put(use, target); }

    private final Map<MemberExpression, Declaration> memberNames = new IdentityHashMap<>();
    public void bind(MemberExpression use, Declaration target) {
        requireMember(use); if (target != null) requireMember(target); memberNames.put(use, target);
    }
    private final Map<NameExpression, Declaration> names = new IdentityHashMap<>();
    private final Map<CallExpression, CallableDeclaration> calls = new IdentityHashMap<>();

    private record Resolution(List<CallableDeclaration> candidates, CallExpression.Resolution status) {}
    private final Map<CallExpression, Resolution> resolutions = new IdentityHashMap<>();
    public void resolve(CallExpression call, List<CallableDeclaration> candidates, CallExpression.Resolution status) {
        requireMember(call); candidates.forEach(this::requireMember);
        resolutions.put(call, new Resolution(List.copyOf(candidates), status));
    }

    private record VirtualTargets(List<CallableDeclaration> targets, boolean complete) {}
    private final Map<CallExpression, VirtualTargets> virtualTargets = new IdentityHashMap<>();
    /** Records conservative runtime implementations separately from static overload selection. */
    public void virtualTargets(CallExpression call, List<CallableDeclaration> targets, boolean complete) {
        requireMember(call); targets.forEach(this::requireMember);
        virtualTargets.put(call, new VirtualTargets(List.copyOf(targets), complete));
    }

    private final Map<CallExpression, List<CallExpression.DefaultArgument>> defaults = new IdentityHashMap<>();
    /** Records source-point default providers without making them children of the call. */
    public void defaultArguments(CallExpression call, List<CallExpression.DefaultArgument> arguments) {
        requireMember(call);
        arguments.forEach(argument -> { requireMember(argument.declaration()); requireMember(argument.parameter()); });
        defaults.put(call, List.copyOf(arguments));
    }

    public ProjectEditor(Project source) {
        this.source = Objects.requireNonNull(source);
        source.walk(new NodeVisitor() {
            @Override public void visit(Node node) { members.add(node); }
        });
    }

    private void requireMember(Node node) {
        if (!members.contains(node)) throw new IllegalArgumentException("Node is outside the source project");
    }

    /** Replaces the owning edge and redirects references. Missing replacement source range
     * and token provenance inherit the old node's metadata; explicit metadata wins. */
    public void replace(Node node, Node replacement) {
        requireMember(node);
        if (node == source || removed.contains(node)) throw new IllegalArgumentException("Cannot replace this node");
        replacements.put(node, Objects.requireNonNull(replacement));
    }

    /** Removes a list element only when no edge from outside its subtree references it. */
    public void remove(Node node) {
        requireMember(node);
        Set<Node> subtree = Collections.newSetFromMap(new IdentityHashMap<>());
        node.walk(new NodeVisitor() {
            @Override public void visit(Node child) { subtree.add(child); }
        });
        for (Node member : members) {
            if (!subtree.contains(member) && member.references().stream().anyMatch(subtree::contains))
                throw new IllegalStateException("Node has incoming references");
        }
        boolean listItem = members.stream().anyMatch(parent ->
                (parent instanceof BlockStatement || parent instanceof SourceFile || parent instanceof Project
                || parent instanceof ClassDeclaration || parent instanceof NamespaceDeclaration || parent instanceof EnumDeclaration || parent instanceof CallExpression call && call.arguments().contains(node)
                || parent instanceof CallableDeclaration callable && callable.parameters().contains(node))
                && parent.children().contains(node));
        if (!listItem) throw new IllegalArgumentException("Only list elements can be removed");
        removed.add(node);
    }

    public void bind(NameExpression use, Declaration target) {
        requireMember(use); requireMember(target); names.put(use, target);
    }

    public void bind(CallExpression call, CallableDeclaration target) {
        requireMember(call); requireMember(target); calls.put(call, target);
    }

    private boolean invalidateAffected(Node node, EditContext context, boolean hierarchyChanged) {
        boolean changed = replacements.containsKey(node) || removed.contains(node) || conversions.containsKey(node);
        for (Node child : node.children()) changed |= invalidateAffected(child, context, hierarchyChanged);
        if (changed && node instanceof Expression && !removed.contains(node)) context.copy(node).clearExpressionInfo();
        if (node instanceof CallExpression call && !defaults.containsKey(call) && syntaxChanged(call))
            context.edit(call, CallExpression.Builder.class).setDefaultArguments(List.of());
        if (node instanceof CallExpression call && !virtualTargets.containsKey(call) && (hierarchyChanged || syntaxChanged(call)))
            context.edit(call, CallExpression.Builder.class).clearVirtualTargets();
        return changed;
    }

    private boolean containsHierarchyDeclaration(Node node) {
        return node instanceof ClassDeclaration || node instanceof CallableDeclaration || node instanceof BaseSpecifier
                || node.children().stream().anyMatch(this::containsHierarchyDeclaration);
    }

    private boolean syntaxChanged(Node node) {
        if (replacements.containsKey(node) || removed.contains(node)) return true;
        return node.children().stream().anyMatch(this::syntaxChanged);
    }

    /** Copies all owned nodes and redirects links in the same identity context before validation. */
    public Project build() {
        var adjusted = new IdentityHashMap<Node, Node>(replacements);
        var origins = new IdentityHashMap<Object, Node>();
        var ambiguousOrigins = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (Node member : members) {
            Node previous = origins.putIfAbsent(member.editIdentity, member);
            if (previous != null && previous != member) ambiguousOrigins.add(member.editIdentity);
        }
        var ownedReplacements = new IdentityHashMap<Node, Set<Node>>();
        var copies = new IdentityHashMap<Object, Node>();
        var ambiguousCopies = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (Node replacement : replacements.values()) {
            Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>());
            replacement.walk(new NodeVisitor() { @Override public void visit(Node node) { owned.add(node); } });
            ownedReplacements.put(replacement, owned);
            for (Node node : owned) {
                Node previous = copies.putIfAbsent(node.editIdentity, node);
                if (previous != null && previous != node) ambiguousCopies.add(node.editIdentity);
            }
        }
        // Retained descendants are still the same logical declarations. Redirect incoming
        // references when a containing scope was edited, but never match a fresh subtree by
        // source range/name (header occurrences can deliberately share both).
        for (Node original : replacements.keySet()) original.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (ambiguousOrigins.contains(node.editIdentity) || ambiguousCopies.contains(node.editIdentity)) return;
                Node retained = copies.get(node.editIdentity);
                if (retained != null && retained != node) adjusted.putIfAbsent(node, retained);
            }
        });
        // A standalone node.edit().build() also copies its non-owning targets. Reconnect
        // those external copies by exact edit lineage, never by possibly equal locations.
        for (Set<Node> owned : ownedReplacements.values()) {
            for (Node node : owned) for (Node reference : node.references()) {
                if (owned.contains(reference) || ambiguousOrigins.contains(reference.editIdentity)) continue;
                Node original = origins.get(reference.editIdentity);
                if (original != null && reference != original)
                    adjusted.putIfAbsent(reference, adjusted.getOrDefault(original, original));
            }
        }
        var context = new EditContext(adjusted, removed, conversions);
        var root = context.edit(source, Project.Builder.class);
        names.forEach((use, target) -> {
            var builder = context.edit(use, NameExpression.Builder.class).clearTarget();
            if (target != null) builder.setTarget(context.edge(target, Declaration.class));
        });
        initializerNames.forEach((use, target) -> {
            var builder = context.edit(use, MemberInitializer.Builder.class).clearTarget();
            if (target != null) builder.setTarget(context.edge(target, Declaration.class));
        });
        memberNames.forEach((use, target) -> {
            NodeBuilder<?> copied = context.copy(use);
            var builder = (MemberExpression.Builder<?, ?>) copied;
            builder.clearTarget();
            if (target != null) builder.setTarget(context.edge(target, Declaration.class));
        });
        declarations.forEach((declaration, value) -> {
            var builder = context.copy(declaration);
            builder.setLinkage(value.linkage()); builder.setDeclarationDefinition(value.definitionOccurrence());
            builder.clearPreviousDeclaration(); builder.clearDefinition();
            if (value.previous() != null) builder.setPreviousDeclaration(context.edge(value.previous(), Declaration.class));
            if (value.definition() != null) builder.setDefinition(context.edge(value.definition(), Declaration.class));
        });
        typeIdentities.forEach((declaration, identity) -> context.copy(declaration).setTypeIdentity(identity));
        languageLinkages.forEach((declaration, language) -> context.copy(declaration).setLanguageLinkage(language));
        types.forEach((type, value) -> {
            var builder = context.edit(type, NamedType.Builder.class).clearAlias().clearDeclaration()
                    .clearResolutionSteps().setCompleteAtUse(value.complete());
            if (value.declaration() != null) builder.setDeclaration(context.edge(value.declaration(), Declaration.class));
            if (value.declaration() instanceof TypedefDeclaration alias) builder.setAlias(context.edge(alias, TypedefDeclaration.class));
            value.steps().forEach(step -> builder.addResolutionStep(step.kind(),
                    context.edge(step.via(), Declaration.class), context.edge(step.target(), Declaration.class)));
        });
        imports.forEach((declaration, target) -> {
            if (declaration instanceof NamespaceAliasDeclaration alias) {
                var builder = context.edit(alias, NamespaceAliasDeclaration.Builder.class).clearTarget();
                if (target != null) builder.setTarget(context.edge(target, NamespaceDeclaration.class));
            } else if (declaration instanceof UsingDirective directive) {
                var builder = context.edit(directive, UsingDirective.Builder.class).clearTarget();
                if (target != null) builder.setTarget(context.edge(target, NamespaceDeclaration.class));
            } else if (declaration instanceof UsingDeclaration using) {
                var builder = context.edit(using, UsingDeclaration.Builder.class).clearTarget();
                if (target != null) builder.setTarget(context.edge(target, Declaration.class));
            }
        });
        calls.forEach((use, target) -> context.edit(use, CallExpression.Builder.class).setTarget(context.edge(target, CallableDeclaration.class)));
        resolutions.forEach((use, value) -> context.edit(use, CallExpression.Builder.class)
                .setCandidates(context.copyAll(value.candidates(), CallableDeclaration.class), value.status()));
        defaults.forEach((call, arguments) -> context.edit(call, CallExpression.Builder.class).setDefaultArguments(
                arguments.stream().map(argument -> new CallExpression.DefaultArgumentBuilder(argument.parameterIndex(),
                        context.edge(argument.declaration(), CallableDeclaration.class),
                        context.edge(argument.parameter(), ParameterDeclaration.class))).toList()));
        virtualTargets.forEach((call, value) -> context.edit(call, CallExpression.Builder.class)
                .setVirtualTargets(context.copyAll(value.targets(), CallableDeclaration.class), value.complete()));
        typeRepresentations.forEach((type, representation) -> context.copy(type).setTypeRepresentation(representation));
        // Until dependency-scoped reanalysis is requested, declaration syntax edits
        // conservatively invalidate dispatch facts throughout the snapshot.
        boolean hierarchyChanged = members.stream().anyMatch(node ->
                (node instanceof ClassDeclaration || node instanceof CallableDeclaration || node instanceof BaseSpecifier) && syntaxChanged(node))
                || replacements.keySet().stream().anyMatch(this::containsHierarchyDeclaration)
                || removed.stream().anyMatch(this::containsHierarchyDeclaration);
        invalidateAffected(source, context, hierarchyChanged);
        conversionSources.forEach((expression, type) -> context.edit(expression, ConversionExpression.Builder.class).setSourceType(type));
        expressions.forEach((expression, info) -> context.copy(expression).setExpressionInfo(info));
        var build = new BuildContext();
        Project result = build.build(root);
        diagnostics.forEach((node, findings) -> build.copy(context.copy(node)).findings = List.copyOf(findings));
        return result;
    }
}
