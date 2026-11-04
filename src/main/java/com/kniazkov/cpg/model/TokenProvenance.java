/* Code Property Graph: token-based links outside syntax ownership. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;
import java.util.LinkedHashSet;

/** A node's half-open slice in its translation unit's final preprocessing stream. */
public record TokenProvenance(Preprocessing unit, int startToken, int endToken) {
    public TokenProvenance {
        Objects.requireNonNull(unit);
        if (startToken < 0 || endToken < startToken || endToken > unit.tokens().size())
            throw new IllegalArgumentException("Invalid preprocessing token interval");
    }
    /** Tokens contributing to this syntax span, including interior whitespace. */
    public List<Preprocessing.Token> tokens() { return unit.tokens().subList(startToken, endToken); }
    /** Distinct events contributing to tokens, in event order; not syntax children. */
    public List<Preprocessing.Expansion> expansions() {
        var ids = new LinkedHashSet<Long>();
        tokens().forEach(t -> ids.addAll(t.expansionIds()));
        return unit.expansions().stream().filter(e -> ids.contains(e.id())).toList();
    }
}
