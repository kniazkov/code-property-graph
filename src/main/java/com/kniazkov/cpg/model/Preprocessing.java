/* Code Property Graph: immutable non-syntax preprocessing provenance. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;

/** One translation unit's preprocessing record. IDs and token offsets are local to this record. */
public record Preprocessing(String translationUnit, List<Include> includes, List<Token> tokens,
                            List<Expansion> expansions, java.util.Map<String, String> sources,
                            com.kniazkov.cpg.build.Build.TranslationUnit configuration) {
    public Preprocessing {
        Objects.requireNonNull(translationUnit);
        sources = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(sources));
        includes = List.copyOf(includes); tokens = List.copyOf(tokens); expansions = List.copyOf(expansions);
    }
    /** Compatibility constructor for externally supplied provenance without captured source text. */
    public Preprocessing(String translationUnit, List<Include> includes, List<Token> tokens, List<Expansion> expansions) {
        this(translationUnit, includes, tokens, expansions, java.util.Map.of(), null);
    }
    /** Physical include occurrence, distinct even when the same header is read again. */
    public record Include(long id, String file, Long parentId) {}
    /** An original token fragment, before argument substitution. */
    public record Fragment(SourceRange range, long includeId, String spelling) {}
    /** Stringization or token pasting and the source fragments that contributed. */
    public record Operation(String kind, SourceRange operator, List<Fragment> fragments, String spelling) {
        public Operation { fragments = List.copyOf(fragments); }
    }
    /** One expansion event; replacement boundaries are half-open final-token indices. */
    public record Expansion(long id, String name, SourceRange definition, List<Fragment> replacement,
                            SourceRange invocation, long includeId, Long parentId,
                            List<List<Fragment>> arguments, List<Operation> operations,
                            int startToken, int endToken) {
        public Expansion {
            replacement = List.copyOf(replacement);
            arguments = arguments.stream().map(List::copyOf).toList(); operations = List.copyOf(operations);
        }
    }
    /** A final preprocessing token with physical spelling and expansion ancestry. */
    public record Token(int index, String spelling, SourceRange spellingRange, SourceRange expansionRange,
                        long includeId, List<Long> expansionIds) {
        public Token { expansionIds = List.copyOf(expansionIds); }
    }
}
