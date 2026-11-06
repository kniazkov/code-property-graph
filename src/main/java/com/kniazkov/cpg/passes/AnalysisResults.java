/* Transaction-local result and diagnostic sink. MIT License. */
package com.kniazkov.cpg.passes;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Staging sink for one invocation. Values must be immutable (see ResultKey); a sink cannot
 * publish for another producer or outside the selected scope, and cannot outlive its callback.
 * Repeated publication of the same key on one node replaces the earlier staged value. */
public final class AnalysisResults {
    private final PassId producer;
    private final Set<Node> members = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Node, Map<ResultKey<?>, Object>> values = new IdentityHashMap<>();
    private final Map<Node, List<Diagnostic>> diagnostics = new IdentityHashMap<>();
    private final Map<String, Class<?>> names = new HashMap<>();
    private boolean open = true;

    AnalysisResults(PassId producer, PassContext context) {
        this.producer = producer;
        members.addAll(context.nodes());
    }
    /** Stages an immutable typed value. Null is not a result; omit it instead. */
    public <T> void put(Node node, ResultKey<T> key, T value) {
        requireMember(node);
        Objects.requireNonNull(key, "Result key");
        if (!producer.equals(key.producer())) throw new IllegalArgumentException("Result key belongs to another producer");
        if (!key.type().isInstance(Objects.requireNonNull(value, "Result value")))
            throw new IllegalArgumentException("Value does not match result type " + key.type().getName());
        Class<?> previous = names.putIfAbsent(key.name(), key.type());
        if (previous != null && previous != key.type()) throw new IllegalArgumentException("Conflicting types for result name " + key.name());
        values.computeIfAbsent(node, ignored -> new LinkedHashMap<>()).put(key, value);
    }
    public <T> void publish(Node node, ResultKey<T> key, T value) { put(node, key, value); }
    public void diagnose(Node node, Diagnostic diagnostic) {
        requireMember(node);
        diagnostics.computeIfAbsent(node, ignored -> new ArrayList<>()).add(Objects.requireNonNull(diagnostic));
    }
    private void requireMember(Node node) {
        if (!open) throw new IllegalStateException("Analysis invocation has finished");
        if (!members.contains(node)) throw new IllegalArgumentException("Node is outside the selected analysis scope");
    }
    void close() { open = false; }
    Map<Node, Map<ResultKey<?>, Object>> values() {
        var copy = new IdentityHashMap<Node, Map<ResultKey<?>, Object>>();
        values.forEach((node, entries) -> copy.put(node, Map.copyOf(entries)));
        return Collections.unmodifiableMap(copy);
    }
    Map<Node, List<Diagnostic>> diagnostics() {
        var copy = new IdentityHashMap<Node, List<Diagnostic>>();
        diagnostics.forEach((node, entries) -> copy.put(node, List.copyOf(entries)));
        return Collections.unmodifiableMap(copy);
    }
}
