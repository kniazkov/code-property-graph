/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Root containing source files. */
public final class Project extends Node {
    private List<Object> dynamicArrayBindings;
    private List<Object> dynamicArrayBindings() {
        var facts = new ArrayList<Object>();
        var roots = new ArrayList<Node>(files); roots.addAll(librarySources);
        roots.addAll(classTemplateInstances); roots.addAll(templateInstances);
        for (Node root : roots) {
            if (root instanceof SourceFile file) facts.add(com.kniazkov.cpg.analysis.TypeSemantics.profile(file).stableIdentity());
            Node syntax = root instanceof ClassTemplateInstance instance ? instance.declaration()
                    : root instanceof TemplateInstance instance ? instance.declaration() : root;
            try { facts.add(com.kniazkov.cpg.generation.Source.render(syntax,
                    root instanceof SourceFile file ? file.language() : Language.CPP)); }
            catch (IllegalArgumentException unsupported) { facts.add("unrenderable"); }
            root.walk(new NodeVisitor() { @Override public void visit(Node node) {
                facts.add(node.editIdentity); facts.add(node.provenance());
                facts.add(node.references().stream().map(target -> target.editIdentity).toList());
                if (node instanceof TypeNode type) {
                    facts.add(type.canonicalIdentity()); facts.add(type.semanticType());
                }
            } });
        }
        return List.copyOf(facts);
    }
    private List<String> dynamicArraySyntax;
    /** Discard derived semantics if their written operands were detached by an edit. */
    void validateDynamicArraySemantics() {
        if (dynamicArrayTypes.isEmpty() && dynamicArraySemantics.isEmpty()) return;
        List<String> syntax;
        try { syntax = files.stream().map(file -> com.kniazkov.cpg.generation.Source.render(file, file.language())).toList(); }
        catch (IllegalArgumentException exception) { syntax = null; }
        var bindings = dynamicArrayBindings();
        if (syntax == null || dynamicArraySyntax != null && !dynamicArraySyntax.equals(syntax)
                || dynamicArrayBindings != null && !dynamicArrayBindings.equals(bindings)) {
            dynamicArrayTypes = List.of(); dynamicArraySemantics = List.of(); dynamicArraySyntax = null; return;
        }
        boolean fresh = dynamicArraySyntax == null;
        dynamicArrayBindings = bindings;
        dynamicArraySyntax = syntax;
        var owned = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        files.forEach(file -> file.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        librarySources.forEach(file -> file.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        classTemplateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        templateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        if (fresh && dynamicArrayTypes.stream().anyMatch(type -> !type.references().stream().allMatch(owned::contains)))
            throw new IllegalStateException("Dynamic array type references foreign syntax");
        dynamicArrayTypes = dynamicArrayTypes.stream().filter(type -> type.references().stream().allMatch(owned::contains)).toList();
        owned.addAll(dynamicArrayTypes);
        // Temporal handle effects may reference other owned operations, never foreign graphs.
        owned.addAll(dynamicArraySemantics);
        if (fresh && dynamicArraySemantics.stream().anyMatch(operation -> !operation.references().stream().allMatch(owned::contains)))
            throw new IllegalStateException("Dynamic array operation references foreign syntax");
        boolean removed;
        do {
            var retained = dynamicArraySemantics.stream()
                    .filter(operation -> operation.references().stream().allMatch(owned::contains)).toList();
            removed = retained.size() != dynamicArraySemantics.size();
            if (removed) {
                owned.removeAll(dynamicArraySemantics);
                owned.addAll(retained);
                dynamicArraySemantics = retained;
            }
        } while (removed);
    }
    private List<DynamicArrayType> dynamicArrayTypes = List.of();
    public List<DynamicArrayType> dynamicArrayTypes() { return dynamicArrayTypes; }
    private List<DynamicArrayOperation> dynamicArraySemantics = List.of();
    public List<DynamicArrayOperation> dynamicArraySemantics() { return dynamicArraySemantics; }
    private List<Object> containerBindings;
    private List<Object> containerBindings() {
        var facts = new ArrayList<Object>();
        var roots = new ArrayList<Node>(files); roots.addAll(librarySources);
        roots.addAll(classTemplateInstances); roots.addAll(templateInstances);
        for (Node root : roots) {
            if (root instanceof SourceFile file) facts.add(com.kniazkov.cpg.analysis.TypeSemantics.profile(file).stableIdentity());
            Node syntax = root instanceof ClassTemplateInstance instance ? instance.declaration()
                    : root instanceof TemplateInstance instance ? instance.declaration() : root;
            try { facts.add(com.kniazkov.cpg.generation.Source.render(syntax,
                    root instanceof SourceFile file ? file.language() : Language.CPP)); }
            catch (IllegalArgumentException unsupported) { facts.add("unrenderable"); }
            root.walk(new NodeVisitor() { @Override public void visit(Node node) {
                facts.add(node.editIdentity); facts.add(node.provenance());
                facts.add(node.references().stream().map(target -> target.editIdentity).toList());
                if (node instanceof TypeNode type) {
                    facts.add(type.canonicalIdentity()); facts.add(type.semanticType());
                }
            } });
        }
        return List.copyOf(facts);
    }
    private List<String> containerSyntax;
    /** Discard derived semantics if their written operands were detached by an edit. */
    void validateContainerSemantics() {
        if (containerTypes.isEmpty() && containerSemantics.isEmpty()) return;
        List<String> syntax;
        try { syntax = files.stream().map(file -> com.kniazkov.cpg.generation.Source.render(file, file.language())).toList(); }
        catch (IllegalArgumentException exception) { syntax = null; }
        var bindings = containerBindings();
        if (syntax == null || containerSyntax != null && !containerSyntax.equals(syntax)
                || containerBindings != null && !containerBindings.equals(bindings)) {
            containerTypes = List.of(); containerSemantics = List.of(); containerSyntax = null; return;
        }
        boolean fresh = containerSyntax == null;
        containerBindings = bindings;
        containerSyntax = syntax;
        var owned = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        files.forEach(file -> file.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        librarySources.forEach(file -> file.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        classTemplateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        templateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        if (fresh && containerTypes.stream().anyMatch(type -> !type.references().stream().allMatch(owned::contains)))
            throw new IllegalStateException("Container type references foreign syntax");
        containerTypes = containerTypes.stream().filter(type -> type.references().stream().allMatch(owned::contains)).toList();
        owned.addAll(containerTypes);
        // Temporal handle effects may reference other owned operations, never foreign graphs.
        owned.addAll(containerSemantics);
        if (fresh && containerSemantics.stream().anyMatch(operation -> !operation.references().stream().allMatch(owned::contains)))
            throw new IllegalStateException("Container operation references foreign syntax");
        boolean removed;
        do {
            var retained = containerSemantics.stream()
                    .filter(operation -> operation.references().stream().allMatch(owned::contains)).toList();
            removed = retained.size() != containerSemantics.size();
            if (removed) {
                owned.removeAll(containerSemantics);
                owned.addAll(retained);
                containerSemantics = retained;
            }
        } while (removed);
    }
    private List<ContainerType> containerTypes = List.of();
    public List<ContainerType> containerTypes() { return containerTypes; }
    private List<ContainerOperation> containerSemantics = List.of();
    public List<ContainerOperation> containerSemantics() { return containerSemantics; }
    private List<Object> sequenceBindings;
    private List<Object> sequenceBindings() { return containerBindings(); }
    private List<String> sequenceSyntax;
    /** Discard derived semantics if their written operands were detached by an edit. */
    void validateSequenceSemantics() {
        if (characterSequenceTypes.isEmpty() && sequenceSemantics.isEmpty()) return;
        List<String> syntax;
        try { syntax = files.stream().map(file -> com.kniazkov.cpg.generation.Source.render(file, file.language())).toList(); }
        catch (IllegalArgumentException exception) { syntax = null; }
        var bindings = sequenceBindings();
        if (syntax == null || sequenceSyntax != null && !sequenceSyntax.equals(syntax)
                || sequenceBindings != null && !sequenceBindings.equals(bindings)) {
            characterSequenceTypes = List.of(); sequenceSemantics = List.of(); sequenceSyntax = null; return;
        }
        boolean fresh = sequenceSyntax == null;
        sequenceBindings = bindings;
        sequenceSyntax = syntax;
        var owned = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        files.forEach(file -> file.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        librarySources.forEach(file -> file.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        classTemplateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        templateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } }));
        if (fresh && characterSequenceTypes.stream().anyMatch(type -> !type.references().stream().allMatch(owned::contains)))
            throw new IllegalStateException("Character sequence type references foreign syntax");
        characterSequenceTypes = characterSequenceTypes.stream().filter(type -> type.references().stream().allMatch(owned::contains)).toList();
        owned.addAll(characterSequenceTypes);
        // Temporal handle effects may reference other owned operations, never foreign graphs.
        owned.addAll(sequenceSemantics);
        if (fresh && sequenceSemantics.stream().anyMatch(operation -> !operation.references().stream().allMatch(owned::contains)))
            throw new IllegalStateException("Sequence operation references foreign syntax");
        boolean removed;
        do {
            var retained = sequenceSemantics.stream()
                    .filter(operation -> operation.references().stream().allMatch(owned::contains)).toList();
            removed = retained.size() != sequenceSemantics.size();
            if (removed) {
                owned.removeAll(sequenceSemantics);
                owned.addAll(retained);
                sequenceSemantics = retained;
            }
        } while (removed);
    }
    private List<CharacterSequenceType> characterSequenceTypes = List.of();
    public List<CharacterSequenceType> characterSequenceTypes() { return characterSequenceTypes; }
    private List<SequenceOperation> sequenceSemantics = List.of();
    public List<SequenceOperation> sequenceSemantics() { return sequenceSemantics; }
    private List<SourceFile> files = List.of();
    private List<SourceFile> librarySources = List.of();
    private StandardLibrary standardLibrary;
    public java.util.Optional<StandardLibrary> standardLibrary() { return java.util.Optional.ofNullable(standardLibrary); }
    private List<TemplateInstance> templateInstances = List.of();
    private List<String> instanceSyntax;
    /** Removes derived instances when a low-level builder changes written source. */
    java.util.Set<Node> invalidateTemplateInstances() {
        if (templateInstances.isEmpty() && classTemplateInstances.isEmpty()) { instanceSyntax = null; return java.util.Set.of(); }
        List<String> syntax;
        try { syntax = files.stream().map(file -> com.kniazkov.cpg.generation.Source.render(file, file.language())).toList(); }
        catch (IllegalArgumentException exception) { syntax = null; } // Recovery/edit syntax cannot retain derived instances.
        if (instanceSyntax == null && syntax != null) { instanceSyntax = syntax; return java.util.Set.of(); }
        var written = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        files.forEach(file -> file.walk(new NodeVisitor() { @Override public void visit(Node node) { written.add(node); } }));
        librarySources.forEach(file -> file.walk(new NodeVisitor() { @Override public void visit(Node node) { written.add(node); } }));
        var allOwned = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        allOwned.addAll(written);
        templateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node node) { allOwned.add(node); } }));
        classTemplateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node node) { allOwned.add(node); } }));
        boolean owned = templateInstances.stream().allMatch(instance -> written.contains(instance.source())
                && allOwned.contains(instance.origin()) && instance.requests().stream().allMatch(allOwned::contains))
                && classTemplateInstances.stream().allMatch(instance -> written.contains(instance.source()) && written.contains(instance.origin())
                && instance.requests().stream().allMatch(allOwned::contains));
        if (syntax != null && instanceSyntax != null && instanceSyntax.equals(syntax) && owned) return java.util.Set.of();
        var discarded = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        templateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node node) { discarded.add(node); } }));
        classTemplateInstances.forEach(instance -> instance.walk(new NodeVisitor() { @Override public void visit(Node node) { discarded.add(node); } }));
        // Library typedefs (for example std::string) can bind a specialization too.
        // Clear every retained written root before removing its derived declaration.
        java.util.stream.Stream.concat(files.stream(), librarySources.stream()).forEach(file -> file.walk(new NodeVisitor() {
            @Override public void visit(CallExpression call) { call.invalidateTargets(discarded); }
            @Override public void visit(VariableDeclaration variable) { variable.invalidateLifetimeTargets(discarded); }
            @Override public void visit(NamedType type) { type.invalidateTemplateTargets(discarded); }
            @Override public void visit(NameExpression name) { name.invalidateTemplateTargets(discarded); }
            @Override public void visit(MemberExpression member) { member.invalidateTemplateTargets(discarded); }
        }));
        templateInstances = List.of(); classTemplateInstances = List.of(); instanceSyntax = null;
        return discarded;
    }
    private List<ClassTemplateInstance> classTemplateInstances = List.of();
    public List<ClassTemplateInstance> classTemplateInstances() { return classTemplateInstances; }
    public List<SourceFile> librarySources() { return librarySources; }
    public List<TemplateInstance> templateInstances() { return templateInstances; }
    /** True when any persistent parser or semantic error is present. */
    public boolean hasErrors() {
        return files.stream().anyMatch(SourceFile::hasErrors)
                || semanticDiagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }
    /** Semantic findings, including instantiation traces, independent of CFG/DFG generation. */
    public List<Diagnostic> semanticDiagnostics() {
        var result = new ArrayList<Diagnostic>();
        walk(new NodeVisitor() { @Override public void visit(Node node) { result.addAll(node.diagnostics()); } });
        return List.copyOf(result);
    }
    /** Every persistent parse-failure occurrence in stable physical/context order. */
    public List<ParseProblem> parseProblems() {
        var result = new ArrayList<ParseProblem>();
        for (SourceFile file : files) {
            var problems = new ArrayList<>(file.parseProblems());
            var known = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ParseProblem, Boolean>());
            known.addAll(problems);
            file.walk(new NodeVisitor() {
                @Override public void visit(ErrorExpression error) {
                    if (known.add(error.problem())) problems.add(error.problem());
                }
            });
            String translationUnit = file.provenance().map(p -> p.unit().translationUnit())
                    .orElseGet(() -> file.range().map(SourceRange::file).orElse(file.path()));
            var fallback = new DiagnosticContext(translationUnit, file.variantId().orElse(null), List.of(), List.of());
            for (ParseProblem problem : problems)
                result.add(problem.context() == null ? problem.withContext(fallback) : problem);
        }
        result.sort(DiagnosticGroup.OCCURRENCE_ORDER);
        return List.copyOf(result);
    }

    /**
     * Stable root-cause groups, keyed only by code, physical primary range and semantic reason.
     * Logical locations, recovery spans and invocation/include context cannot split a root cause.
     */
    public List<DiagnosticGroup> diagnosticGroups() {
        record Cause(String code, SourceRange range, String reason) {}
        var groups = new java.util.LinkedHashMap<Cause, List<ParseProblem>>();
        for (ParseProblem problem : parseProblems())
            groups.computeIfAbsent(new Cause(problem.code(), problem.primaryRange(), problem.reason()),
                    ignored -> new ArrayList<>()).add(problem);
        return groups.entrySet().stream().map(entry -> {
            Cause cause = entry.getKey();
            return new DiagnosticGroup(cause.code(), cause.range(), cause.reason(),
                    entry.getValue().getFirst().message(), entry.getValue());
        }).toList();
    }

    private List<ExternalInput> externalInputs = List.of();
    public List<ExternalInput> externalInputs() { return externalInputs; }
    /** Resolves provenance to the actual owned node in this snapshot, also after edits. */
    public java.util.Optional<ExternalInput> externalInput(String key) {
        var found = new java.util.ArrayList<ExternalInput>();
        for (var input : externalInputs) input.walk(new NodeVisitor() {
            @Override public void visit(ExternalInput node) { if (node.key().equals(key)) found.add(node); }
        });
        return found.stream().findFirst();
    }
    private com.kniazkov.cpg.build.Build build;

    public java.util.Optional<com.kniazkov.cpg.build.Build> build() { return java.util.Optional.ofNullable(build); }
    public java.util.Optional<com.kniazkov.cpg.build.Build.Composition> composition() {
        return build().map(com.kniazkov.cpg.build.Build::composition);
    }
    public List<SourceFile> variants(java.nio.file.Path path) {
        var normalized = path.toAbsolutePath().normalize();
        return files.stream().filter(f -> java.nio.file.Path.of(f.range().map(SourceRange::file)
                .orElse(f.path())).toAbsolutePath().normalize().equals(normalized)).toList();
    }
    /** Selects owned variants, clears old call edges and recomputes resolution and flow analyses. */
    public Project selectBuild(com.kniazkov.cpg.build.Build.Composition composition) {
        if (build == null) throw new IllegalStateException("Project has no compilation build");
        var selectedBuild = new com.kniazkov.cpg.build.Build(build.units(), composition);
        var selected = files.stream().filter(f -> f.variantId().map(composition.variantIds()::contains).orElse(false)).toList();
        if (selected.size() != composition.variantIds().size())
            throw new IllegalArgumentException("Requested variant is not present in this snapshot");
        var context = new EditContext();
        var root = context.edit(this, Builder.class);
        walk(new NodeVisitor() {
            @Override public void visit(CallExpression call) { context.edit(call, CallExpression.Builder.class).clearTarget(); }
            @Override public void visit(NameExpression name) { context.edit(name, NameExpression.Builder.class).clearTarget(); }
            @Override public void visit(MemberExpression member) {
                NodeBuilder<?> copied = context.copy(member);
                ((MemberExpression.Builder<?, ?>) copied).clearTarget();
            }
            @Override public void visit(Declaration declaration) {
                var builder = context.copy(declaration);
                builder.clearDefinition(); builder.clearPreviousDeclaration();
            }
        });
        root.clearTemplateInstances().setFiles(context.copyAll(selected, SourceFile.class)).setBuild(selectedBuild);
        Project snapshot = root.build();
        if (snapshot.hasErrors()) return snapshot;
        return com.kniazkov.cpg.analysis.UnreadWrites.analyze(
                com.kniazkov.cpg.analysis.NameResolver.resolve(snapshot));
    }

    private List<FunctionFlow> flows = List.of();

    /** Returns flow analyses associated with this particular syntax snapshot. */
    public List<FunctionFlow> flows() { return flows; }

    /** Shares immutable syntax and attaches complete per-callable analysis results. */
    public Project withFlows(List<FunctionFlow> results) {
        var copy = new Project();
        copy.findings = findings;
        copy.origin = origin;
        copy.provenance = provenance;
        copy.commentReferences = commentReferences;
        copy.editIdentity = editIdentity;
        copy.files = files;
        copy.classTemplateInstances = classTemplateInstances;
        copy.dynamicArrayBindings = dynamicArrayBindings; copy.dynamicArraySyntax = dynamicArraySyntax; copy.dynamicArraySemantics = dynamicArraySemantics; copy.dynamicArrayTypes = dynamicArrayTypes;
        copy.containerBindings = containerBindings; copy.containerSyntax = containerSyntax; copy.containerSemantics = containerSemantics; copy.containerTypes = containerTypes;
        copy.sequenceBindings = sequenceBindings; copy.sequenceSyntax = sequenceSyntax; copy.sequenceSemantics = sequenceSemantics; copy.characterSequenceTypes = characterSequenceTypes;
        copy.standardLibrary = standardLibrary; copy.librarySources = librarySources; copy.templateInstances = templateInstances; copy.instanceSyntax = instanceSyntax;
        copy.externalInputs = externalInputs;
        copy.build = build;
        copy.flows = List.copyOf(results);
        var owners = new java.util.HashSet<CallableDeclaration>();
        walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration callable) {
                if (callable.body().isPresent()) owners.add(callable);
            }
        });
        var nodes = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        walk(new NodeVisitor() { @Override public void visit(Node node) { nodes.add(node); } });
        for (FunctionFlow flow : copy.flows) {
            for (var edge : flow.data()) {
                for (Node endpoint : List.of(edge.from(), edge.to()))
                    if (!nodes.contains(endpoint)) throw new IllegalArgumentException("Foreign data-flow endpoint");
                if (edge.callSite() != null && !nodes.contains(edge.callSite()))
                    throw new IllegalArgumentException("Foreign data-flow call site");
            }
            for (var auxiliary : flow.auxiliaryVertices()) {
                if (auxiliary.object().filter(n -> !nodes.contains(n)).isPresent()
                        || auxiliary.destructor().or(auxiliary::constructor).filter(n -> !nodes.contains(n)).isPresent())
                    throw new IllegalArgumentException("Foreign auxiliary flow reference");
            }
            if (!owners.remove(flow.entry()))
                throw new IllegalArgumentException("Foreign or duplicate function flow");
        }
        if (!owners.isEmpty()) throw new IllegalArgumentException("Missing function flow");
        return copy;
    }

    /**
     * Replaces the findings of one rule. When annotations change, copies the snapshot
     * privately and reconnects its flow edges to the copied nodes before publication.
     * Ordinary edit/build deliberately discards findings and flow information.
     */
    public Project withDiagnostics(String rule, java.util.Map<Node, Diagnostic> findingsByNode) {
        var pending = new java.util.IdentityHashMap<>(findingsByNode);
        var annotations = new java.util.IdentityHashMap<Node, List<Diagnostic>>();
        var changed = new java.util.HashSet<Node>();
        walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (node.diagnostics().isEmpty() && !pending.containsKey(node)) return;
                var updated = new ArrayList<>(node.diagnostics());
                updated.removeIf(d -> d.code().equals(rule));
                Diagnostic finding = pending.remove(node);
                if (finding != null) {
                    if (!finding.code().equals(rule)) throw new IllegalArgumentException("Unexpected rule code");
                    updated.add(finding);
                }
                if (!updated.equals(node.diagnostics())) changed.add(node);
                if (!updated.isEmpty()) annotations.put(node, List.copyOf(updated));
            }
        });
        if (!pending.isEmpty()) throw new IllegalArgumentException("Finding outside this project");
        if (changed.isEmpty()) return this;
        var edit = new EditContext();
        var build = new BuildContext();
        Project copy = build.build(edit.edit(this, Builder.class));
        java.util.function.Function<Node, Node> copied = n -> build.copy(edit.copy(n));
        annotations.forEach((node, list) -> copied.apply(node).findings = list);
        copy.flows = flows.stream().map(flow -> {
            CallableDeclaration entry = (CallableDeclaration) copied.apply(flow.entry());
            var auxiliaries = new java.util.IdentityHashMap<FlowVertex, FlowVertex>();
            for (var auxiliary : flow.auxiliaryVertices()) auxiliaries.put(auxiliary,
                    new AuxiliaryFlowVertex(entry, auxiliary.index(), auxiliary.kind(),
                            auxiliary.object().map(copied).orElse(null),
                            auxiliary.destructor().or(auxiliary::constructor).map(d -> (CallableDeclaration) copied.apply(d)).orElse(null)));
            java.util.function.Function<FlowVertex, FlowVertex> endpoint = v -> v instanceof Node n ? copied.apply(n) : auxiliaries.get(v);
            var control = flow.control().stream().map(e -> new FunctionFlow.ControlEdge(
                    endpoint.apply(e.from()), endpoint.apply(e.to()), e.kind())).toList();
            var data = flow.data().stream().map(e -> new FunctionFlow.DataEdge(
                    copied.apply(e.from()), copied.apply(e.to()), e.kind(),
                    e.callSite() == null ? null : (CallExpression) copied.apply(e.callSite()))).toList();
            return new FunctionFlow(entry, entry.exit(), control, data, flow.complete());
        }).toList();
        return copy;
    }

    private Project() {
    }

    public List<SourceFile> files() {
        return files;
    }

    @Override
    public List<Node> children() {
        List<Node> children = new ArrayList<>();
        children.addAll(files);
        if (standardLibrary != null) children.add(standardLibrary); children.addAll(templateInstances); children.addAll(classTemplateInstances); children.addAll(dynamicArraySemantics); children.addAll(dynamicArrayTypes); children.addAll(containerSemantics); children.addAll(containerTypes); children.addAll(sequenceSemantics); children.addAll(characterSequenceTypes);
        children.addAll(externalInputs);
        return List.copyOf(children);
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }

    public static Builder create() {
        return new Builder();
    }

    @Override
    public Builder edit() {
        return new EditContext().edit(this, Builder.class);
    }

    @Override
    Builder copyBuilder(EditContext context) {
        Builder builder = new Builder();
        context.register(this, builder);
        builder.files = context.copyAll(files, SourceFile.class);
        builder.librarySources = context.copyAll(librarySources, SourceFile.class);
        builder.libraryCollection = standardLibrary == null ? null : context.edit(standardLibrary, StandardLibrary.Builder.class);
        builder.templateInstances = context.copyAll(templateInstances, TemplateInstance.class);
        builder.classTemplateInstances = context.copyAll(classTemplateInstances, ClassTemplateInstance.class);
        builder.dynamicArrayBindings = dynamicArrayBindings; builder.dynamicArraySyntax = dynamicArraySyntax;
        builder.containerBindings = containerBindings; builder.containerSyntax = containerSyntax;
        builder.sequenceBindings = sequenceBindings; builder.sequenceSyntax = sequenceSyntax;
        builder.dynamicArrayTypes = context.copyAll(dynamicArrayTypes, DynamicArrayType.class);
        builder.containerTypes = context.copyAll(containerTypes, ContainerType.class);
        builder.characterSequenceTypes = context.copyAll(characterSequenceTypes, CharacterSequenceType.class);
        builder.dynamicArraySemantics = context.copyAll(dynamicArraySemantics, DynamicArrayOperation.class);
        builder.containerSemantics = context.copyAll(containerSemantics, ContainerOperation.class);
        builder.sequenceSemantics = context.copyAll(sequenceSemantics, SequenceOperation.class);
        builder.instanceSyntax = instanceSyntax;
        builder.build = build;
        builder.externalInputs = context.copyAll(externalInputs, ExternalInput.class);
        return builder;
    }

    /** Mutable counterpart; setters may be chained before building a snapshot. */
    public static final class Builder extends NodeBuilder<Project> {
        private List<NodeBuilder<? extends SourceFile>> files = new ArrayList<>();
        private List<NodeBuilder<? extends SourceFile>> librarySources = new ArrayList<>();
        private StandardLibrary.Builder libraryCollection;
        private List<NodeBuilder<? extends TemplateInstance>> templateInstances = new ArrayList<>();
        private List<Object> dynamicArrayBindings;
        private List<Object> containerBindings;
        private List<Object> sequenceBindings;
        private List<String> dynamicArraySyntax;
        private List<String> containerSyntax;
        private List<String> sequenceSyntax;
        private List<NodeBuilder<? extends DynamicArrayType>> dynamicArrayTypes = new ArrayList<>();
        private List<NodeBuilder<? extends ContainerType>> containerTypes = new ArrayList<>();
        private List<NodeBuilder<? extends CharacterSequenceType>> characterSequenceTypes = new ArrayList<>();
        public Builder setDynamicArrayTypes(List<NodeBuilder<? extends DynamicArrayType>> value) { dynamicArrayTypes = new ArrayList<>(value); return this; }
        public Builder setContainerTypes(List<NodeBuilder<? extends ContainerType>> value) { containerTypes = new ArrayList<>(value); return this; }
        public Builder setCharacterSequenceTypes(List<NodeBuilder<? extends CharacterSequenceType>> value) { characterSequenceTypes = new ArrayList<>(value); return this; }
        private List<NodeBuilder<? extends DynamicArrayOperation>> dynamicArraySemantics = new ArrayList<>();
        private List<NodeBuilder<? extends ContainerOperation>> containerSemantics = new ArrayList<>();
        private List<NodeBuilder<? extends SequenceOperation>> sequenceSemantics = new ArrayList<>();
        public Builder setDynamicArraySemantics(List<NodeBuilder<? extends DynamicArrayOperation>> value) { dynamicArraySemantics = new ArrayList<>(value); dynamicArraySyntax = null; dynamicArrayBindings = null; return this; }
        public Builder setContainerSemantics(List<NodeBuilder<? extends ContainerOperation>> value) { containerSemantics = new ArrayList<>(value); containerSyntax = null; containerBindings = null; return this; }
        public Builder setSequenceSemantics(List<NodeBuilder<? extends SequenceOperation>> value) { sequenceSemantics = new ArrayList<>(value); sequenceSyntax = null; sequenceBindings = null; return this; }
        private List<NodeBuilder<? extends ClassTemplateInstance>> classTemplateInstances = new ArrayList<>();
        Builder addClassTemplateInstance(ClassTemplateInstance.Builder value) { classTemplateInstances.add(value); return this; }
        private List<String> instanceSyntax;
        public Builder setLibrarySources(List<? extends NodeBuilder<? extends SourceFile>> values) { librarySources = new ArrayList<>(values); return this; }
        public Builder addLibrarySource(NodeBuilder<? extends SourceFile> value) { librarySources.add(value); return this; }
        Builder clearTemplateInstances() { templateInstances.clear(); classTemplateInstances.clear(); return this; }
        Builder addTemplateInstance(TemplateInstance.Builder value) { templateInstances.add(value); return this; }
        private com.kniazkov.cpg.build.Build build;
        private List<NodeBuilder<? extends ExternalInput>> externalInputs = new ArrayList<>();
        public Builder addExternalInput(ExternalInput.Builder value) { externalInputs.add(value); return this; }
        public Builder setBuild(com.kniazkov.cpg.build.Build value) { build = value; return this; }

        private Builder() {
            super(Project.class);
        }

        public List<NodeBuilder<? extends SourceFile>> files() {
            return List.copyOf(files);
        }

        public Builder setFiles(List<? extends NodeBuilder<? extends SourceFile>> files) {
            this.files = new ArrayList<>(List.copyOf(files));
            return this;
        }

        public Builder addFile(NodeBuilder<? extends SourceFile> value) {
            files.add(Objects.requireNonNull(value));
            return this;
        }

        @Override
        Project newNode() {
            return new Project();
        }

        @Override
        void fillNode(Project node, BuildContext context) {
            node.files = context.copyAll(files);
            if (!librarySources.isEmpty()) {
                var collection = libraryCollection == null ? new StandardLibrary.Builder() : libraryCollection; collection.headers = librarySources;
                node.standardLibrary = context.copy(collection);
                node.librarySources = node.standardLibrary.headers();
            }
            node.classTemplateInstances = context.copyAll(classTemplateInstances);
            node.dynamicArrayBindings = dynamicArrayBindings; node.dynamicArraySyntax = dynamicArraySyntax;
            node.containerBindings = containerBindings; node.containerSyntax = containerSyntax;
            node.sequenceBindings = sequenceBindings; node.sequenceSyntax = sequenceSyntax;
            node.dynamicArrayTypes = context.copyAll(dynamicArrayTypes);
            node.containerTypes = context.copyAll(containerTypes);
            node.characterSequenceTypes = context.copyAll(characterSequenceTypes);
            node.dynamicArraySemantics = context.copyAll(dynamicArraySemantics);
            node.containerSemantics = context.copyAll(containerSemantics);
            node.sequenceSemantics = context.copyAll(sequenceSemantics);
            node.templateInstances = context.copyAll(templateInstances); node.instanceSyntax = instanceSyntax;
            node.build = build;
            var inputs = new java.util.LinkedHashMap<String, ExternalInput.Builder>();
            for (var input : externalInputs) {
                var builder = (ExternalInput.Builder) input;
                inputs.put(builder.inputKey(), builder);
            }
            for (var generated : ExternalInputs.create(node.files, build)) {
                var existing = inputs.get(generated.inputKey());
                if (existing == null) inputs.put(generated.inputKey(), generated);
                else existing.mergeMissing(generated);
            }
            node.externalInputs = context.copyAll(new ArrayList<>(inputs.values()));
        }
    }
}
