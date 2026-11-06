/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Root containing source files. */
public final class Project extends Node {
    private List<PassRun> passRuns = List.of();
    private PassInputs passInputs;
    private List<CallableDeclaration> passCallables;
    private java.util.Map<Object, CallableDeclaration> passCallableIndex;

    /** Completed public pass invocations in actual execution order, including empty results. */
    public List<PassRun> passRuns() { return passRuns; }

    /** Records whole-project execution without changing syntax, findings or dependencies. */
    public Project recordPass(String passId) {
        return recordPass(passId, PassRun.Scope.PROJECT, callables());
    }

    /** Records execution for one current callable, accepting an exact prior edit-lineage handle. */
    public Project recordPass(String passId, CallableDeclaration function) {
        CallableDeclaration owned = correspondingCallable(function)
                .orElseThrow(() -> new IllegalArgumentException("Function is outside this project"));
        return recordPass(passId, PassRun.Scope.FUNCTION, List.of(owned));
    }

    private Project recordPass(String passId, PassRun.Scope scope, List<CallableDeclaration> functions) {
        var records = new ArrayList<>(passRuns);
        long sequence = records.isEmpty() ? 1 : records.getLast().sequence() + 1;
        records.add(PassRun.completed(sequence, passId, scope, functions));
        Project copy = copyMetadata(flows);
        copy.passRuns = List.copyOf(records);
        return copy;
    }

    /** Latest status for this callable; empty means no completed invocation covered it. */
    public java.util.Optional<PassRun.Status> passStatus(String passId, CallableDeclaration function) {
        Objects.requireNonNull(passId);
        CallableDeclaration current = correspondingCallable(function)
                .orElseThrow(() -> new IllegalArgumentException("Function is outside this project"));
        return latestPassStatus(passId, current);
    }

    private java.util.Optional<PassRun.Status> latestPassStatus(String passId, CallableDeclaration current) {
        for (int i = passRuns.size() - 1; i >= 0; i--) {
            PassRun run = passRuns.get(i);
            if (run.passId().equals(passId)) for (PassRun.Result result : run.results())
                if (result.covers(current)) return java.util.Optional.of(result.status());
        }
        return java.util.Optional.empty();
    }

    /** Whole-project freshness, combining the latest covering result for every current callable.
     * A partial scoped run cannot make a never-analyzed second callable current. */
    public java.util.Optional<PassRun.Status> passStatus(String passId) {
        Objects.requireNonNull(passId);
        boolean executed = passRuns.stream().anyMatch(run -> run.passId().equals(passId));
        if (!executed) return java.util.Optional.empty();
        List<CallableDeclaration> functions = callables();
        if (!functions.isEmpty()) {
            var statuses = new java.util.IdentityHashMap<Object, PassRun.Status>();
            for (int i = passRuns.size() - 1; i >= 0 && statuses.size() < functions.size(); i--) {
                PassRun run = passRuns.get(i);
                if (run.passId().equals(passId)) for (PassRun.Result result : run.results())
                    if (result.function().isPresent()) statuses.putIfAbsent(result.identity(), result.status());
            }
            for (CallableDeclaration function : functions)
                if (statuses.getOrDefault(function.editIdentity, PassRun.Status.STALE) != PassRun.Status.CURRENT)
                    return java.util.Optional.of(PassRun.Status.STALE);
            return java.util.Optional.of(PassRun.Status.CURRENT);
        }
        for (int i = passRuns.size() - 1; i >= 0; i--) {
            PassRun run = passRuns.get(i);
            if (run.passId().equals(passId) && run.scope() == PassRun.Scope.PROJECT)
                return java.util.Optional.of(run.status());
        }
        return java.util.Optional.of(PassRun.Status.STALE);
    }

    /** Explicitly retires products replaced by another pass, preserving the original snapshot. */
    public Project invalidatePasses(java.util.Set<String> passIds) {
        Objects.requireNonNull(passIds);
        Project copy = copyMetadata(flows);
        var functions = callableIndex();
        copy.passRuns = passRuns.stream().map(run -> run.remap(functions, java.util.Set.of(),
                passIds.contains(run.passId()))).toList();
        return copy;
    }

    /** Retains existing per-function products across prerequisite-only copies when their
     * written syntax and bindings are unchanged. This restores earlier records, never
     * invents an execution. The prior history must be a prefix of this history. */
    public Project retainUnchangedPassResults(Project prior, String passId) {
        Objects.requireNonNull(prior); Objects.requireNonNull(passId);
        if (prior.passRuns.size() > passRuns.size()) throw new IllegalArgumentException("Prior history is not an ancestor");
        for (int i = 0; i < prior.passRuns.size(); i++) {
            PassRun previous = prior.passRuns.get(i), current = passRuns.get(i);
            if (previous.sequence() != current.sequence() || !previous.passId().equals(current.passId())
                    || previous.scope() != current.scope()) throw new IllegalArgumentException("Prior history is not an ancestor");
        }
        PassInputs before = prior.passInputs(), after = passInputs();
        if (!before.global.equals(after.global)) return this;
        var changed = changedPassFunctions(prior);
        var unchanged = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        before.functions.forEach((identity, facts) -> {
            if (!changed.contains(identity) && facts.equals(after.functions.get(identity))) unchanged.add(identity);
        });
        var records = new ArrayList<>(passRuns);
        var functions = callableIndex();
        for (int i = 0; i < prior.passRuns.size(); i++) if (records.get(i).passId().equals(passId))
            records.set(i, records.get(i).restoreCurrentResults(prior.passRuns.get(i), functions, unchanged));
        Project copy = copyMetadata(flows);
        copy.passRuns = List.copyOf(records);
        if (!passId.equals("UnreadWrites")) return copy;
        // withDiagnostics creates a private annotation copy before restoring findings;
        // never mutate the immutable syntax shared by copyMetadata.
        var findings = new java.util.IdentityHashMap<Node, Diagnostic>();
        walk(new NodeVisitor() { @Override public void visit(Node node) {
            node.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("unread-write"))
                    .findFirst().ifPresent(diagnostic -> findings.put(node, diagnostic));
        }});
        var destinations = new java.util.IdentityHashMap<Object, Node>();
        walk(new NodeVisitor() { @Override public void visit(Node node) { destinations.put(node.editIdentity, node); } });
        for (CallableDeclaration function : prior.callables()) {
            if (!unchanged.contains(function.editIdentity)
                    || prior.latestPassStatus(passId, function).orElse(PassRun.Status.STALE) != PassRun.Status.CURRENT) continue;
            function.walk(new NodeVisitor() { @Override public void visit(Node node) {
                Node target = destinations.get(node.editIdentity);
                if (target != null) node.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("unread-write"))
                        .findFirst().ifPresent(diagnostic -> findings.put(target, diagnostic));
            }});
        }
        return copy.withDiagnostics("unread-write", findings);
    }

    /** Resolves current or ancestor handles only by exact edit lineage, never by name or range. */
    public java.util.Optional<CallableDeclaration> correspondingCallable(CallableDeclaration function) {
        Objects.requireNonNull(function);
        return java.util.Optional.ofNullable(callableIndex().get(function.editIdentity));
    }

    private List<CallableDeclaration> callables() {
        if (passCallables != null) return passCallables;
        var result = new ArrayList<CallableDeclaration>();
        walk(new NodeVisitor() { @Override public void visit(CallableDeclaration function) {
            if (function.body().isPresent()) result.add(function);
        }});
        passCallables = List.copyOf(result);
        return passCallables;
    }

    private java.util.Map<Object, CallableDeclaration> callableIndex() {
        if (passCallableIndex != null) return passCallableIndex;
        var result = new java.util.IdentityHashMap<Object, CallableDeclaration>();
        var ambiguous = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        for (CallableDeclaration function : callables())
            if (result.putIfAbsent(function.editIdentity, function) != null) ambiguous.add(function.editIdentity);
        ambiguous.forEach(result::remove);
        passCallableIndex = java.util.Collections.unmodifiableMap(result);
        return passCallableIndex;
    }

    private PassInputs passInputs() {
        if (passInputs == null) passInputs = new PassInputs(this);
        return passInputs;
    }

    /** Called once after an unpublished builder snapshot is fully validated. */
    void inheritPassHistory(Project source) {
        if (source == null || source.passRuns.isEmpty()) return;
        var functions = callableIndex();
        PassInputs before = source.passInputs(), after = passInputs();
        boolean global = !before.global.equals(after.global);
        var changed = global ? java.util.Set.<Object>of() : changedPassFunctions(source);
        boolean sourceChanged = global || !changed.isEmpty();
        passRuns = source.passRuns.stream().map(run -> run.remap(functions, changed,
                global || run.passId().equals("ControlFlow") || run.passId().equals("DataFlow")
                || !sourceChanged && run.passId().equals("UnreadWrites"))).toList();
        if (sourceChanged && !global) restoreUnaffectedFindings(source);
    }

    private java.util.Set<Object> changedPassFunctions(Project source) {
        PassInputs before = source.passInputs(), after = passInputs();
        var changed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        before.functions.forEach((identity, facts) -> {
            if (!facts.equals(after.functions.get(identity))) changed.add(identity);
        });
        after.functions.keySet().stream().filter(identity -> !before.functions.containsKey(identity)).forEach(changed::add);
        if (changed.isEmpty()) return changed;
        // Call effects flow toward callers, while observed writes flow back toward
        // callees. Use both graphs: removed and newly introduced calls both matter.
        var candidates = new ArrayList<>(source.callables()); candidates.addAll(callables());
        var previousCalls = new java.util.IdentityHashMap<Object, Object>();
        source.walk(new NodeVisitor() { @Override public void visit(CallExpression call) {
            previousCalls.put(call.editIdentity, callShape(call));
        }});
        var graph = new java.util.IdentityHashMap<Object, java.util.Set<Object>>();
        source.callables().forEach(function -> addCallDependencies(function, candidates, graph, previousCalls, false));
        callables().forEach(function -> addCallDependencies(function, candidates, graph, previousCalls, true));
        var pending = new java.util.ArrayDeque<Object>(); pending.addAll(changed);
        while (!pending.isEmpty()) for (Object target : graph.getOrDefault(pending.removeFirst(), java.util.Set.of()))
            if (changed.add(target)) pending.add(target);
        return changed;
    }

    private static void addCallDependencies(CallableDeclaration owner, List<CallableDeclaration> candidates,
            java.util.Map<Object, java.util.Set<Object>> graph, java.util.Map<Object, Object> previousCalls,
            boolean edited) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        var pending = new java.util.ArrayDeque<Node>(); pending.add(owner);
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (!seen.add(node)) continue;
            pending.addAll(node.children());
            for (Node target : node.references()) {
                relatedCallableIdentities(target).forEach(identity -> connectCall(graph, owner.editIdentity, identity));
                // Omitted arguments remain owned by a declaration, but execute at this call.
                if (target instanceof Expression) pending.add(target);
            }
            if (node instanceof CallExpression call) {
                call.target().ifPresent(target -> relatedCallableIdentities(target)
                        .forEach(identity -> connectCall(graph, owner.editIdentity, identity)));
                if (call.target().isPresent() || !call.candidates().isEmpty()) continue;
                String name = call.callee() instanceof NameExpression named ? named.name()
                        : call.callee() instanceof MemberExpression member ? member.member() : null;
                var possible = candidates.stream().filter(candidate -> name != null
                        && simpleCallName(candidate.name()).equals(simpleCallName(name))).toList();
                possible.forEach(candidate -> connectCall(graph, owner.editIdentity, candidate.editIdentity));
                if (possible.isEmpty() && edited && !Objects.equals(previousCalls.get(call.editIdentity), callShape(call)))
                    candidates.forEach(candidate -> connectCall(graph, owner.editIdentity, candidate.editIdentity));
            }
        }
    }

    private static String simpleCallName(String name) {
        int separator = name.lastIndexOf("::");
        String result = separator < 0 ? name : name.substring(separator + 2);
        int arguments = result.indexOf('<');
        return arguments < 0 ? result : result.substring(0, arguments);
    }

    private static Object callShape(CallExpression call) {
        try { return com.kniazkov.cpg.generation.Source.render(call, Language.CPP); }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException unsupported) {
            return new Object();
        }
    }

    private static void connectCall(java.util.Map<Object, java.util.Set<Object>> graph, Object from, Object to) {
        graph.computeIfAbsent(from, ignored -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(to);
        graph.computeIfAbsent(to, ignored -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(from);
    }

    private static java.util.Set<Object> relatedCallableIdentities(Node target) {
        var identities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        if (!(target instanceof CallableDeclaration)) return identities;
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Declaration, Boolean>());
        var pending = new java.util.ArrayDeque<Declaration>();
        pending.add((Declaration) target);
        while (!pending.isEmpty()) {
            Declaration declaration = pending.removeFirst();
            if (!seen.add(declaration)) continue;
            identities.add(declaration.editIdentity);
            declaration.definition().ifPresent(pending::add);
            declaration.previousDeclaration().ifPresent(pending::add);
            declaration.templateOrigin().ifPresent(pending::add);
        }
        return identities;
    }

    /** Annotation-only copies restore all products and therefore all original freshness. */
    private void remapPassHistory(Project source) {
        var functions = callableIndex();
        passRuns = source.passRuns.stream().map(run -> run.remap(functions, java.util.Set.of(), false)).toList();
        passInputs = source.passInputs;
    }

    /** Preserve unread-write products only where the retained pass result remains current. */
    void restoreUnaffectedFindings(Project source) {
        var nodes = new java.util.IdentityHashMap<Object, Node>();
        walk(new NodeVisitor() { @Override public void visit(Node node) { nodes.put(node.editIdentity, node); } });
        for (CallableDeclaration function : source.callables()) {
            var current = correspondingCallable(function);
            if (current.isEmpty() || passStatus("UnreadWrites", current.get()).orElse(PassRun.Status.STALE) != PassRun.Status.CURRENT) continue;
            function.walk(new NodeVisitor() { @Override public void visit(Node node) {
                Node target = nodes.get(node.editIdentity);
                if (target == null) return;
                var findings = new ArrayList<>(target.findings);
                node.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("unread-write"))
                        .filter(diagnostic -> !findings.contains(diagnostic)).forEach(findings::add);
                target.findings = List.copyOf(findings);
            }});
        }
    }

    private SnapshotStage snapshotStage = SnapshotStage.PARSED;
    public SnapshotStage snapshotStage() { return snapshotStage; }
    /** Shares immutable syntax without mutating this snapshot. */
    public Project withSnapshotStage(SnapshotStage stage) {
        Project copy = copyMetadata(flows);
        copy.snapshotStage = java.util.Objects.requireNonNull(stage);
        return copy;
    }
    public record TransformationResult(Node result, String passId, String role) {}
    /** Reverse transitive query restricted to results owned by this selected snapshot.
     * Roles describe the result's first edge on each path to the requested input. */
    public List<TransformationResult> transformationResults(Node original) {
        java.util.Objects.requireNonNull(original);
        var results = new ArrayList<TransformationResult>();
        walk(new NodeVisitor() { @Override public void visit(Node node) {
            for (var edge : node.transformationProvenance()) {
                if (reachesHistory(edge.source(), original)) {
                    var result = new TransformationResult(node, edge.passId(), edge.role());
                    if (!results.contains(result)) results.add(result);
                }
            }
        }});
        return List.copyOf(results);
    }
    private static boolean reachesHistory(Node start, Node target) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        var pending = new java.util.ArrayDeque<Node>(); pending.add(start);
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (node == target) return true;
            if (seen.add(node)) for (var edge : node.transformationProvenance()) pending.add(edge.source());
        }
        return false;
    }

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
    private Project copyMetadata(List<FunctionFlow> results) {
        var copy = new Project();
        copy.findings = findings;
        copy.origin = origin;
        copy.provenance = provenance;
        copy.transformationProvenance = transformationProvenance;
        copy.passRuns = passRuns;
        copy.passInputs = passInputs;
        copy.passCallables = passCallables;
        copy.passCallableIndex = passCallableIndex;
        copy.snapshotStage = snapshotStage;
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
        return copy;
    }

    public Project withFlows(List<FunctionFlow> results) {
        Project copy = copyMetadata(results);
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
        copy.remapPassHistory(this);
        return copy;
    }

    /** Replaces a rule's findings only inside one callable, retaining all other scopes. */
    public Project withDiagnostics(String rule, java.util.Map<Node, Diagnostic> findingsByNode,
            CallableDeclaration function) {
        CallableDeclaration scope = correspondingCallable(function)
                .orElseThrow(() -> new IllegalArgumentException("Function is outside this project"));
        var members = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Node, Boolean>());
        scope.walk(new NodeVisitor() { @Override public void visit(Node node) { members.add(node); } });
        if (!members.containsAll(findingsByNode.keySet()))
            throw new IllegalArgumentException("Finding outside the selected function");
        var combined = new java.util.IdentityHashMap<Node, Diagnostic>(findingsByNode);
        walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (!members.contains(node)) node.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals(rule))
                    .findFirst().ifPresent(diagnostic -> combined.put(node, diagnostic));
        }});
        return withDiagnostics(rule, combined);
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
        builder.historySource = this;
        builder.snapshotStage = snapshotStage;
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
        Project historySource;
        private SnapshotStage snapshotStage = SnapshotStage.PARSED;
        public Builder setSnapshotStage(SnapshotStage value) { snapshotStage = java.util.Objects.requireNonNull(value); return this; }
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
            node.snapshotStage = snapshotStage;
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
