/* Immutable metadata storage for custom-pass products. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

final class AnalysisProducts {
    static final AnalysisProducts EMPTY = new AnalysisProducts(List.of(), List.of());
    final List<AnalysisResult<?>> results;
    final List<AnalysisDiagnostic> diagnostics;

    AnalysisProducts(List<AnalysisResult<?>> results, List<AnalysisDiagnostic> diagnostics) {
        this.results = List.copyOf(results);
        this.diagnostics = List.copyOf(diagnostics);
    }

    AnalysisProducts remap(Map<Object, Node> nodes, BiPredicate<String, Object> stale) {
        var values = new ArrayList<AnalysisResult<?>>();
        var findings = new ArrayList<AnalysisDiagnostic>();
        for (var result : results) {
            Node target = nodes.get(result.node().editIdentity);
            if (target != null) values.add(result.remap(target,
                    stale.test(result.key().producer().value(), result.ownerIdentity)
                            ? PassRun.Status.STALE : result.status()));
        }
        for (var finding : diagnostics) {
            Node target = nodes.get(finding.node().editIdentity);
            if (target != null) findings.add(finding.remap(target,
                    stale.test(finding.producer().value(), finding.ownerIdentity)
                            ? PassRun.Status.STALE : finding.status()));
        }
        return new AnalysisProducts(values, findings);
    }

    AnalysisProducts replace(String producer, Set<Object> selected,
            List<AnalysisResult<?>> values, List<AnalysisDiagnostic> findings) {
        var remainingValues = new ArrayList<AnalysisResult<?>>();
        var remainingFindings = new ArrayList<AnalysisDiagnostic>();
        for (var result : results) if (!result.key().producer().value().equals(producer)
                || selected != null && !selected.contains(result.node().editIdentity)) remainingValues.add(result);
        for (var finding : diagnostics) if (!finding.producer().value().equals(producer)
                || selected != null && !selected.contains(finding.node().editIdentity)) remainingFindings.add(finding);
        remainingValues.addAll(values);
        remainingFindings.addAll(findings);
        return new AnalysisProducts(remainingValues, remainingFindings);
    }

    AnalysisProducts remapRoot(Node oldRoot, Node newRoot) {
        return new AnalysisProducts(results.stream().map(value -> value.node() == oldRoot
                ? value.remap(newRoot, value.status()) : value).toList(),
                diagnostics.stream().map(value -> value.node() == oldRoot
                ? value.remap(newRoot, value.status()) : value).toList());
    }

    boolean sameCurrentDiagnostics(AnalysisProducts other) {
        record Finding(String producer, Object identity, Diagnostic value) {}
        var first = diagnostics.stream().filter(d -> d.status() == PassRun.Status.CURRENT)
                .map(d -> new Finding(d.producer().value(), d.node().editIdentity, d.diagnostic())).toList();
        var second = other.diagnostics.stream().filter(d -> d.status() == PassRun.Status.CURRENT)
                .map(d -> new Finding(d.producer().value(), d.node().editIdentity, d.diagnostic())).toList();
        return first.equals(second);
    }
}
