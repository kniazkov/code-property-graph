/* Producer-owned typed analysis products. MIT License. */
package com.kniazkov.cpg.model;

import com.kniazkov.cpg.passes.ResultKey;
import java.util.Objects;

/** An immutable result binding. Custom values must themselves be immutable, as required
 * by {@link ResultKey}; the encoded value is a defensively frozen JSON-compatible tree.
 * Stale products remain inspectable but are not returned by {@link Project#resultOf}. */
public final class AnalysisResult<T> {
    private final ResultKey<T> key;
    private final Node node;
    private final T value;
    private final Object encodedValue;
    private final PassRun.Status status;
    final Object ownerIdentity;

    AnalysisResult(ResultKey<T> key, Node node, T value, Object encodedValue,
            PassRun.Status status, Object ownerIdentity) {
        this.key = Objects.requireNonNull(key);
        this.node = Objects.requireNonNull(node);
        this.value = Objects.requireNonNull(value);
        this.encodedValue = encodedValue;
        this.status = Objects.requireNonNull(status);
        this.ownerIdentity = ownerIdentity;
    }
    public ResultKey<T> key() { return key; }
    public Node node() { return node; }
    public T value() { return value; }
    public Object encodedValue() { return encodedValue; }
    public PassRun.Status status() { return status; }
    AnalysisResult<T> remap(Node target, PassRun.Status freshness) {
        return new AnalysisResult<>(key, target, value, encodedValue, freshness, ownerIdentity);
    }
}
