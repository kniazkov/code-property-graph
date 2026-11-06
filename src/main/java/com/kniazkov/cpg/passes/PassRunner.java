/* Deterministic custom pass scheduling and atomic publication. MIT License. */
package com.kniazkov.cpg.passes;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/** Registers extension passes and prepares their dependency DAG before invoking them.
 * Registration captures IDs and requirements; forward references are allowed until validate/run.
 * Each requested target runs even if current, while current prerequisites are reused.
 * Registration is not thread-safe. Each run uses a frozen registration map and local staging.
 * Neither callback failures nor validation/encoding failures change the caller's snapshot. */
public final class PassRunner {
    private record Registered(PassId id, Set<PassId> requirements, boolean wholeProject,
            AnalysisPass analysis, TransformationPass transformation,
            BiFunction<Project, CallableDeclaration, Project> builtin) {}
    private final Map<PassId, Registered> registrations = new TreeMap<>();
    private final ResultTypes resultTypes;

    public PassRunner() { this(ResultTypes.defaults()); }
    public PassRunner(ResultTypes resultTypes) {
        this.resultTypes = Objects.requireNonNull(resultTypes);
        builtins();
    }
    public PassRunner register(AnalysisPass pass) {
        Objects.requireNonNull(pass);
        add(new Registered(Objects.requireNonNull(pass.id()), requirements(pass.requires()), false, pass, null, null));
        return this;
    }
    public PassRunner register(TransformationPass pass) {
        Objects.requireNonNull(pass);
        add(new Registered(Objects.requireNonNull(pass.id()), requirements(pass.requires()), false, null, pass, null));
        return this;
    }
    private static Set<PassId> requirements(Set<PassId> values) {
        return Collections.unmodifiableSet(new TreeSet<>(Objects.requireNonNull(values)));
    }
    private void add(Registered pass) {
        if (registrations.putIfAbsent(pass.id(), pass) != null)
            throw new IllegalArgumentException("Pass already registered: " + pass.id());
    }
    /** Checks the complete registration DAG without executing any callback. */
    public PassRunner validate() {
        validate(registrations, registrations.keySet());
        return this;
    }
    public Set<PassId> registeredPasses() {
        return Collections.unmodifiableSet(new TreeSet<>(registrations.keySet()));
    }
    public Project run(Project project, PassId id) { return runScope(project, id, null); }
    /** Custom passes run only on this callable; built-in graph prerequisites may need the
     * whole project. Scope is followed by exact edit lineage through those snapshots. */
    public Project run(Project project, PassId id, CallableDeclaration function) {
        Objects.requireNonNull(function, "Function scope");
        return runScope(project, id, function);
    }
    private Project runScope(Project project, PassId id, CallableDeclaration function) {
        Objects.requireNonNull(project); Objects.requireNonNull(id);
        if (function != null) function = currentFunction(project, function);
        Map<PassId, Registered> frozen = Map.copyOf(registrations);
        validate(frozen, Set.of(id));
        return execute(frozen, project, id, function, true);
    }
    private Project execute(Map<PassId, Registered> registry, Project project, PassId id,
            CallableDeclaration requested, boolean force) {
        Registered entry = registry.get(id);
        CallableDeclaration scope = entry.wholeProject() ? null : requested;
        List<PassId> required = entry.requirements().stream().sorted().toList();
        final Project initial = project;
        final CallableDeclaration initialScope = scope;
        // Do not recursively revisit every shared ancestor of a current dependency DAG.
        // Direct freshness is checked as well: transformations can stale their own inputs.
        if (!force && current(project, id, scope) && required.stream().allMatch(dependency ->
                current(initial, dependency, registry.get(dependency).wholeProject() ? null : initialScope)))
            return project;
        // Preparing one dependency can invalidate another (for example a transformation).
        // Recheck the entire set rather than ever invoking a callback with stale inputs.
        boolean prepared = false;
        for (int round = 0; round <= registry.size(); round++) {
            for (PassId dependency : required)
                project = execute(registry, project, dependency, scope, false);
            final Project snapshot = project;
            final CallableDeclaration handle = scope;
            if (required.stream().allMatch(dependency -> current(snapshot, dependency,
                    registry.get(dependency).wholeProject() ? null : handle))) { prepared = true; break; }
        }
        if (!prepared) throw new IllegalStateException("Dependencies cannot remain current for pass " + id);
        scope = scope == null ? null : currentFunction(project, scope);
        if (!force && current(project, id, scope)) return project;
        if (entry.builtin() != null) return entry.builtin().apply(project, scope);
        PassContext context = new PassContext(project, scope, entry.requirements());
        if (entry.analysis() != null) {
            AnalysisResults results = new AnalysisResults(id, context);
            try { entry.analysis().analyze(context, results); }
            finally { results.close(); }
            return project.publishAnalysis(id, entry.requirements(), scope,
                    results.values(), results.diagnostics(), resultTypes);
        }
        Project transformed = ProjectEditor.applyPass(project, id.value(), scope,
                editor -> entry.transformation().transform(context, editor));
        CallableDeclaration changedScope = scope == null ? null : currentFunction(transformed, scope);
        return transformed.recordCustomPass(id, entry.requirements(), changedScope);
    }
    private static boolean current(Project project, PassId id, CallableDeclaration scope) {
        return (scope == null ? project.passStatus(id.value())
                : project.passStatus(id.value(), currentFunction(project, scope))).orElse(null) == PassRun.Status.CURRENT;
    }
    private static CallableDeclaration currentFunction(Project project, CallableDeclaration function) {
        return project.correspondingCallable(function).orElseThrow(() ->
                new IllegalArgumentException("Function is outside this project or was removed by a dependency"));
    }
    private static void validate(Map<PassId, Registered> registry, Collection<PassId> targets) {
        var complete = new HashSet<PassId>();
        var active = new LinkedHashSet<PassId>();
        targets.stream().sorted().forEach(id -> validateOne(registry, id, complete, active));
    }
    private static void validateOne(Map<PassId, Registered> registry, PassId id,
            Set<PassId> complete, LinkedHashSet<PassId> active) {
        if (complete.contains(id)) return;
        Registered entry = registry.get(id);
        if (entry == null) throw new IllegalArgumentException("Missing pass registration: " + id);
        if (!active.add(id)) throw new IllegalArgumentException("Cyclic pass dependencies: " + active + " -> " + id);
        entry.requirements().stream().sorted().forEach(dependency -> validateOne(registry, dependency, complete, active));
        active.remove(id); complete.add(id);
    }
    private void builtin(PassId id, Set<PassId> requires, UnaryOperator<Project> operation) {
        add(new Registered(id, requirements(requires), true, null, null, (project, ignored) -> operation.apply(project)));
    }
    private void builtins() {
        builtin(BuiltinPasses.NAME_RESOLVER, Set.of(), NameResolver::resolve);
        builtin(BuiltinPasses.CONTROL_FLOW, Set.of(BuiltinPasses.NAME_RESOLVER), ControlFlow::analyze);
        builtin(BuiltinPasses.DATA_FLOW, Set.of(BuiltinPasses.CONTROL_FLOW), DataFlow::analyze);
        add(new Registered(BuiltinPasses.UNREAD_WRITES, Set.of(BuiltinPasses.DATA_FLOW), false, null, null,
                (project, scope) -> scope == null ? UnreadWrites.analyze(project) : UnreadWrites.analyze(project, scope)));
        builtin(BuiltinPasses.DOCUMENTATION, Set.of(), Documentation::analyze);
        builtin(BuiltinPasses.CONSTANT_FOLDER, Set.of(BuiltinPasses.NAME_RESOLVER), ConstantFolder::fold);
        builtin(BuiltinPasses.CONSOLE_NORMALIZER, Set.of(BuiltinPasses.NAME_RESOLVER), ConsoleNormalizer::green);
        builtin(BuiltinPasses.TYPE_NORMALIZER, Set.of(), TypeNormalizer::green);
        builtin(BuiltinPasses.STANDARD_CONVERSIONS, Set.of(BuiltinPasses.NAME_RESOLVER), StandardConversions::analyze);
        builtin(BuiltinPasses.WRITTEN_CONVERSIONS, Set.of(), StandardConversions::written);
        builtin(BuiltinPasses.CLASS_TEMPLATE_SEMANTICS, Set.of(), ClassTemplateSemantics::analyze);
        builtin(BuiltinPasses.CLASS_TEMPLATE_DIAGNOSTICS, Set.of(), ClassTemplateSemantics::diagnose);
        builtin(BuiltinPasses.LIBRARY_DECLARATIONS, Set.of(), LibraryDeclarations::install);
        builtin(BuiltinPasses.DYNAMIC_ARRAY_SEMANTICS, Set.of(BuiltinPasses.NAME_RESOLVER), DynamicArraySemantics::green);
        builtin(BuiltinPasses.CONTAINER_SEMANTICS, Set.of(BuiltinPasses.NAME_RESOLVER), ContainerSemantics::green);
        builtin(BuiltinPasses.SEQUENCE_SEMANTICS, Set.of(BuiltinPasses.NAME_RESOLVER), SequenceSemantics::green);
    }
}
