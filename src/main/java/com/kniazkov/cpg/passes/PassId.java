/* Stable public pass identity. MIT License. */
package com.kniazkov.cpg.passes;

import java.util.Objects;

/** A stable, case-sensitive producer name, shared by dependencies and execution history. */
public record PassId(String value) implements Comparable<PassId> {
    public PassId {
        if (Objects.requireNonNull(value, "Pass ID").isBlank())
            throw new IllegalArgumentException("Pass ID is required");
    }
    public static PassId of(String value) { return new PassId(value); }
    @Override public int compareTo(PassId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
