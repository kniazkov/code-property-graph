/* Immutable execution history, separate from node transformation provenance. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** One completed pass invocation, in execution order, as observed by a snapshot.
 * Status describes whether its result still applies, not whether it found anything.
 * A later snapshot may carry a stale copy; the original record never changes. */
public final class PassRun {
    public enum Scope { PROJECT, FUNCTION }
    public enum Status { CURRENT, STALE }

    /** Frozen callable coverage. Removed callables keep their description but no node edge. */
    public static final class Result {
        private final Object identity;
        private final CallableDeclaration function;
        private final String name;
        private final SourceRange range;
        private final Status status;

        private Result(Object identity, CallableDeclaration function, String name, SourceRange range, Status status) {
            this.identity = identity;
            this.function = function;
            this.name = name;
            this.range = range;
            this.status = status;
        }
        private Result(CallableDeclaration function) {
            this(function.editIdentity, function, function.name(), function.range().orElse(null), Status.CURRENT);
        }
        public Optional<CallableDeclaration> function() { return Optional.ofNullable(function); }
        public String name() { return name; }
        public Optional<SourceRange> range() { return Optional.ofNullable(range); }
        public Status status() { return status; }
        Object identity() { return identity; }
        boolean covers(CallableDeclaration callable) { return identity == callable.editIdentity; }
        private Result remap(Map<Object, CallableDeclaration> functions, Set<Object> changed, boolean invalidate) {
            CallableDeclaration current = functions.get(identity);
            return new Result(identity, current, name, range,
                    invalidate || current == null || changed.contains(identity) ? Status.STALE : status);
        }
    }

    private final long sequence;
    private final String passId;
    private final Scope scope;
    private final List<Result> results;
    private final Status status;
    private final Set<String> requirements;
    private final boolean custom;

    private PassRun(long sequence, String passId, Scope scope, List<Result> results, Status status, Set<String> requirements, boolean custom) {
        this.sequence = sequence;
        this.passId = passId;
        this.scope = scope;
        this.results = List.copyOf(results);
        this.status = status;
        this.requirements = Set.copyOf(requirements);
        this.custom = custom;
    }
    static PassRun completed(long sequence, String passId, Scope scope, List<CallableDeclaration> functions) {
        return completed(sequence, passId, scope, functions, Set.of(), false);
    }
    static PassRun completed(long sequence, String passId, Scope scope, List<CallableDeclaration> functions, Set<String> requirements, boolean custom) {
        if (passId == null || passId.isBlank()) throw new IllegalArgumentException("Pass ID is required");
        Objects.requireNonNull(scope);
        return new PassRun(sequence, passId, scope, functions.stream().map(Result::new).toList(), Status.CURRENT, requirements, custom);
    }
    public long sequence() { return sequence; }
    public String passId() { return passId; }
    public Scope scope() { return scope; }
    public List<Result> results() { return results; }
    public Status status() { return status; }
    /** Declared prerequisite pass IDs, empty for legacy built-in records. */
    public Set<String> requirements() { return requirements; }
    /** Custom callbacks may inspect the whole snapshot, so edits invalidate their entire coverage. */
    public boolean custom() { return custom; }

    PassRun remap(Map<Object, CallableDeclaration> functions, Set<Object> changed, boolean invalidate) {
        var remapped = results.stream().map(result -> result.remap(functions, changed, invalidate)).toList();
        Status current = invalidate || remapped.stream().anyMatch(result -> result.status() == Status.STALE)
                ? Status.STALE : status;
        return new PassRun(sequence, passId, scope, remapped, current, requirements, custom);
    }

    PassRun restoreCurrentResults(PassRun prior, Map<Object, CallableDeclaration> functions, Set<Object> unchanged) {
        var restored = results.stream().map(result -> {
            if (!unchanged.contains(result.identity)) return result;
            boolean current = prior.results.stream().anyMatch(previous -> previous.identity == result.identity
                    && previous.status == Status.CURRENT);
            return current ? new Result(result.identity, functions.get(result.identity), result.name, result.range, Status.CURRENT) : result;
        }).toList();
        Status current = !restored.isEmpty() && restored.stream().allMatch(result -> result.status == Status.CURRENT)
                ? Status.CURRENT : status;
        return new PassRun(sequence, passId, scope, restored, current, requirements, custom);
    }
}
