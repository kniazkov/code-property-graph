/* Typed and producer-owned analysis result identity. MIT License. */
package com.kniazkov.cpg.passes;

import java.util.Objects;

/** A typed result name owned by one pass. Values must be immutable: records with immutable
 * components are suitable; mutable collections, arrays and mutable objects are not.
 * The JSON representation is separately frozen when a successful run is published. */
public record ResultKey<T>(PassId producer, String name, Class<T> type) {
    public ResultKey {
        Objects.requireNonNull(producer, "Result producer");
        if (Objects.requireNonNull(name, "Result name").isBlank())
            throw new IllegalArgumentException("Result name is required");
        Objects.requireNonNull(type, "Result type");
        if (type.isPrimitive()) throw new IllegalArgumentException("Use a boxed result type");
    }
    public static <T> ResultKey<T> of(PassId producer, String name, Class<T> type) {
        return new ResultKey<>(producer, name, type);
    }
}
