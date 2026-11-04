/* Code Property Graph: immutable non-syntax preprocessing provenance. MIT License. */
package com.kniazkov.cpg.model;

import java.util.List;
import java.util.Objects;

/** One translation unit's preprocessing record. IDs and token offsets are local to this record. */
public record Preprocessing(String translationUnit, List<Include> includes, List<Token> tokens,
                            List<Expansion> expansions, java.util.Map<String, String> sources,
                            com.kniazkov.cpg.build.Build.TranslationUnit configuration,
                            List<Definition> definitions, List<Branch> branches,
                            List<PresenceCheck> presenceChecks, List<Directive> directives,
                            List<LogicalLocation> logicalLocations, List<IncludeState> includeStates, java.time.Instant translationTime) {
    public Preprocessing {
        Objects.requireNonNull(translationUnit);
        definitions = List.copyOf(definitions); branches = List.copyOf(branches);
        presenceChecks = List.copyOf(presenceChecks); directives = List.copyOf(directives);
        logicalLocations = List.copyOf(logicalLocations); includeStates = List.copyOf(includeStates);
        sources = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(sources));
        includes = List.copyOf(includes); tokens = List.copyOf(tokens); expansions = List.copyOf(expansions);
    }
    public Preprocessing(String translationUnit, List<Include> includes, List<Token> tokens,
            List<Expansion> expansions, java.util.Map<String,String> sources,
            com.kniazkov.cpg.build.Build.TranslationUnit configuration) {
        this(translationUnit, includes, tokens, expansions, sources, configuration,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), java.time.Instant.EPOCH);
    }
    /** Executed definition, including definitions which never expand. */
    public record Definition(long id, String name, boolean functionLike, List<String> parameters,
            String rawReplacement, SourceRange range, long includeId, String originKind, String sourceKey) {
        public Definition { parameters = List.copyOf(parameters); }
    }
    /** A conditional arm and its original directive/body text. */
    public record Branch(long id, String directive, String rawSource, SourceRange range,
            SourceRange directiveRange, long includeId, Long parentId, boolean parentActive,
            boolean evaluated, boolean conditionResult, boolean active, int startToken, int endToken) {}
    /** A defined/ifdef/ifndef test, not a replacement-token expansion. */
    public record PresenceCheck(long id, String name, SourceRange range, long includeId,
            boolean present, boolean conditionResult, Long definitionId, long branchId) {}
    public record Directive(String kind, String rawSource, SourceRange range, long includeId, boolean active) {}
    /** Logical coordinates introduced by #line, separate from physical source ranges. */
    public record LogicalLocation(SourceRange range, long includeId, String file, int line) {}
    /** Active definition IDs after a particular include occurrence finished executing. */
    public record IncludeState(long includeId, java.util.Map<String,Long> activeDefinitions) {
        public IncludeState { activeDefinitions = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(activeDefinitions)); }
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
                            int startToken, int endToken, long definitionId) {
        public Expansion(long id, String name, SourceRange definition, List<Fragment> replacement,
                SourceRange invocation, long includeId, Long parentId, List<List<Fragment>> arguments,
                List<Operation> operations, int startToken, int endToken) {
            this(id, name, definition, replacement, invocation, includeId, parentId, arguments, operations, startToken, endToken, 0);
        }
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
