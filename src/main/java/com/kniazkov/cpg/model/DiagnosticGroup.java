/* Code Property Graph: stable root-cause diagnostic summaries. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** A root cause and every occurrence, including repeated includes and build variants. */
public record DiagnosticGroup(String code, SourceRange primaryRange, String reason,
        String message, List<ParseProblem> occurrences) {
    private static final Comparator<SourcePosition> POSITIONS = Comparator.comparingInt(SourcePosition::offset)
            .thenComparingInt(SourcePosition::line).thenComparingInt(SourcePosition::column);
    static final Comparator<SourceRange> RANGES = Comparator.comparing(SourceRange::file)
            .thenComparing(SourceRange::start, POSITIONS).thenComparing(SourceRange::end, POSITIONS);
    private static final Comparator<Long> OPTIONAL_IDS = Comparator.nullsFirst(Comparator.naturalOrder());
    private static final Comparator<Preprocessing.Include> INCLUDES = Comparator.comparingLong(Preprocessing.Include::id)
            .thenComparing(Preprocessing.Include::file).thenComparing(Preprocessing.Include::parentId, OPTIONAL_IDS);
    private static final Comparator<Preprocessing.Fragment> FRAGMENTS = Comparator
            .comparing(Preprocessing.Fragment::range, RANGES).thenComparingLong(Preprocessing.Fragment::includeId)
            .thenComparing(Preprocessing.Fragment::spelling);
    private static final Comparator<Preprocessing.Operation> OPERATIONS = Comparator.comparing(Preprocessing.Operation::kind)
            .thenComparing(Preprocessing.Operation::operator, RANGES)
            .thenComparing(Preprocessing.Operation::fragments, listOrder(FRAGMENTS))
            .thenComparing(Preprocessing.Operation::spelling);
    private static final Comparator<Preprocessing.Expansion> EXPANSIONS = Comparator.comparingLong(Preprocessing.Expansion::id)
            .thenComparing(Preprocessing.Expansion::name).thenComparing(Preprocessing.Expansion::definition, RANGES)
            .thenComparing(Preprocessing.Expansion::invocation, RANGES).thenComparingLong(Preprocessing.Expansion::includeId)
            .thenComparing(Preprocessing.Expansion::parentId, OPTIONAL_IDS)
            .thenComparing(Preprocessing.Expansion::replacement, listOrder(FRAGMENTS))
            .thenComparing(Preprocessing.Expansion::arguments, listOrder(listOrder(FRAGMENTS)))
            .thenComparing(Preprocessing.Expansion::operations, listOrder(OPERATIONS))
            .thenComparingInt(Preprocessing.Expansion::startToken).thenComparingInt(Preprocessing.Expansion::endToken)
            .thenComparingLong(Preprocessing.Expansion::definitionId);
    private static final Comparator<DiagnosticRelatedRange> RELATED = Comparator.comparing(DiagnosticRelatedRange::role)
            .thenComparing(DiagnosticRelatedRange::label).thenComparing(DiagnosticRelatedRange::range, RANGES);
    private static final Comparator<DiagnosticContext> CONTEXTS = Comparator.nullsFirst(
            Comparator.comparing(DiagnosticContext::translationUnit)
                    .thenComparing(DiagnosticContext::variantId, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(DiagnosticContext::includeChain, listOrder(INCLUDES))
                    .thenComparing(DiagnosticContext::expansionChain, listOrder(EXPANSIONS)));
    /** Physical position and code first, followed by cause and complete occurrence context. */
    public static final Comparator<ParseProblem> OCCURRENCE_ORDER = Comparator
            .comparing(ParseProblem::primaryRange, RANGES).thenComparing(ParseProblem::code)
            .thenComparing(ParseProblem::reason).thenComparing(ParseProblem::context, CONTEXTS)
            .thenComparing(ParseProblem::range, RANGES).thenComparing(ParseProblem::message)
            .thenComparing(ParseProblem::relatedRanges, listOrder(RELATED)).thenComparing(ParseProblem::skippedText)
            .thenComparing(ParseProblem::recovered);

    public DiagnosticGroup {
        Objects.requireNonNull(code);
        Objects.requireNonNull(primaryRange);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(message);
        occurrences = occurrences.stream().sorted(OCCURRENCE_ORDER).toList();
        if (occurrences.isEmpty()) throw new IllegalArgumentException("Diagnostic group needs an occurrence");
        for (ParseProblem occurrence : occurrences) {
            if (!code.equals(occurrence.code()) || !primaryRange.equals(occurrence.primaryRange())
                    || !reason.equals(occurrence.reason()))
                throw new IllegalArgumentException("Occurrence belongs to a different diagnostic cause");
        }
    }

    /** Distinct physical TU paths; repeated includes and variants of one .cpp count once. */
    public int translationUnitCount() {
        return (int) occurrences.stream().map(DiagnosticGroup::translationUnit).distinct().count();
    }

    /** Distinct (physical TU, variant) pairs; an unconfigured TU is one variant. */
    public int variantCount() {
        return (int) occurrences.stream().map(p -> new Variant(translationUnit(p),
                p.context() == null ? null : p.context().variantId())).distinct().count();
    }

    public int occurrenceCount() { return occurrences.size(); }

    private static <T> Comparator<List<T>> listOrder(Comparator<T> elements) {
        return (left, right) -> {
            for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                int order = elements.compare(left.get(index), right.get(index));
                if (order != 0) return order;
            }
            return Integer.compare(left.size(), right.size());
        };
    }

    private static String translationUnit(ParseProblem problem) {
        return problem.context() == null ? problem.range().file() : problem.context().translationUnit();
    }
    private record Variant(String translationUnit, String variantId) {}
}
