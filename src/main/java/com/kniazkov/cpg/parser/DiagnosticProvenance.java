/* Code Property Graph: physical provenance for parser diagnostic occurrences. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Converts only immutable preprocessing records, never syntax-node identities. */
final class DiagnosticProvenance {
    private DiagnosticProvenance() {}
    static DiagnosticContext context(Preprocessing unit, int index) {
        if (unit == null || index < 0 || index >= unit.tokens().size()) return null;
        var token = unit.tokens().get(index);
        var expansions = new ArrayList<Preprocessing.Expansion>();
        var ids = new LinkedHashSet<Long>();
        for (long id : token.expansionIds()) addExpansion(unit, id, ids, expansions);
        long include = expansions.isEmpty() ? token.includeId() : expansions.getLast().includeId();
        return new DiagnosticContext(unit.translationUnit(), unit.configuration() == null ? null : unit.configuration().variantId(),
                includes(unit, include), expansions);
    }
    private static void addExpansion(Preprocessing unit, long id, LinkedHashSet<Long> ids, List<Preprocessing.Expansion> result) {
        if (ids.contains(id)) return;
        var event = unit.expansions().stream().filter(e -> e.id() == id).findFirst().orElse(null);
        if (event == null) return;
        if (event.parentId() != null) addExpansion(unit, event.parentId(), ids, result);
        if (ids.add(id)) result.add(event);
    }
    private static List<Preprocessing.Include> includes(Preprocessing unit, long id) {
        var result = new ArrayList<Preprocessing.Include>();
        Long current = id;
        while (current != null) {
            long wanted = current;
            var include = unit.includes().stream().filter(i -> i.id() == wanted).findFirst().orElse(null);
            if (include == null) break;
            result.add(include); current = include.parentId();
        }
        Collections.reverse(result); return result;
    }
    static String causeOrigin(Preprocessing unit, int index) {
        if (unit == null || index < 0 || index >= unit.tokens().size()) return "";
        var token = unit.tokens().get(index);
        if (token.expansionIds().isEmpty()) return "";
        var origin = token.spellingRange();
        return " [origin: " + origin.file() + ":" + origin.start().offset() + ".." + origin.end().offset() + "]";
    }
    static List<DiagnosticRelatedRange> related(Preprocessing unit, int index) {
        DiagnosticContext context = context(unit, index);
        if (context == null || context.expansionChain().isEmpty()) return List.of();
        var result = new ArrayList<DiagnosticRelatedRange>();
        for (var expansion : context.expansionChain())
            result.add(new DiagnosticRelatedRange("macro-invocation", "Expansion of macro " + expansion.name(), expansion.invocation()));
        var token = unit.tokens().get(index);
        result.add(new DiagnosticRelatedRange("macro-definition", "Expanded token '" + token.spelling() + "' originates here", token.spellingRange()));
        return List.copyOf(result);
    }
}
