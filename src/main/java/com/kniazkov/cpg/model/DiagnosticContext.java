/* Code Property Graph: immutable diagnostic occurrence context. MIT License. */
package com.kniazkov.cpg.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Physical translation unit and its preprocessing ancestry, ordered outermost first.
 * Include and expansion IDs are local to this translation unit/configuration, not node IDs.
 * The value records deliberately contain no syntax-node references, so edits cannot stale them.
 */
public record DiagnosticContext(String translationUnit, String variantId,
        List<Preprocessing.Include> includeChain, List<Preprocessing.Expansion> expansionChain) {
    public DiagnosticContext {
        translationUnit = Path.of(Objects.requireNonNull(translationUnit)).toAbsolutePath().normalize().toString();
        includeChain = List.copyOf(includeChain);
        expansionChain = List.copyOf(expansionChain);
    }
}
