/* Immutable expression facts, separate from a computed constant value. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Objects;
import java.util.Optional;

/** Canonical type identity is a value, not a syntax or declaration reference. */
public record ExpressionInfo(Optional<String> resultType, ValueCategory category) {
    public ExpressionInfo { Objects.requireNonNull(resultType); Objects.requireNonNull(category); }
}
