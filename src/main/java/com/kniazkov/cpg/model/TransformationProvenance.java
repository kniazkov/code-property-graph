package com.kniazkov.cpg.model;
import java.util.Objects;
/** Immutable non-owning edge into an earlier snapshot, never a semantic reference. */
public record TransformationProvenance(String passId, String role, Node source) {
    public TransformationProvenance {
        Objects.requireNonNull(passId); Objects.requireNonNull(role); Objects.requireNonNull(source);
        if (passId.isBlank() || role.isBlank()) throw new IllegalArgumentException("Pass and role must be nonblank");
    }
}
