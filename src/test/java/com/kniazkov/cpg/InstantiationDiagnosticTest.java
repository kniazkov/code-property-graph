/* Structured semantic errors remain useful without flow construction. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstantiationDiagnosticTest {
    @Test void structuredTraceIsImmutableAndAvailableWithoutFlows() {
        var plain = Project.create().addFile(Parser.parse("trace.cpp", "int f() { return 0; }", Language.CPP)).build();
        var function = (CallableDeclaration) plain.files().getFirst().items().getFirst();
        var primary = function.range().orElseThrow();
        var trace = new ArrayList<DiagnosticRelatedRange>();
        trace.add(new DiagnosticRelatedRange("instantiation-request", "requested inner<int> from outer<int>", primary));
        trace.add(new DiagnosticRelatedRange("instantiation-request", "requested outer<int> from main", primary));
        var finding = new Diagnostic("template-instantiation-body", Diagnostic.Severity.ERROR,
                "Member size is unavailable on int", primary, trace);
        trace.clear();
        var project = plain.withDiagnostics(finding.code(), Map.of(function, finding));
        assertTrue(project.flows().isEmpty());
        assertFalse(plain.hasErrors());
        assertTrue(project.hasErrors());
        assertEquals(List.of(finding), project.semanticDiagnostics());
        assertEquals(2, finding.relatedRanges().size());
        assertThrows(UnsupportedOperationException.class, () -> finding.relatedRanges().clear());
        var preserved = project.withDiagnostics("other-rule", Map.of());
        assertEquals(project.semanticDiagnostics(), preserved.semanticDiagnostics());
        String json = ProjectJson.encode(project).toText("");
        assertTrue(json.contains("primaryRange"));
        assertTrue(json.contains("relatedRanges"));
        assertTrue(json.indexOf("requested inner") < json.indexOf("requested outer"));
        assertFalse(project.withDiagnostics(finding.code(), Map.of()).hasErrors());
    }
    @Test void flowAnalysisRetainsProjectLevelResourceErrors() {
        var plain = Project.create().build();
        var finding = new Diagnostic("template-instantiation-depth", Diagnostic.Severity.ERROR, "Template resource limit reached");
        var diagnosed = plain.withDiagnostics(finding.code(), Map.of(plain, finding));
        for (var snapshot : List.of(diagnosed, diagnosed.withFlows(List.of()),
                com.kniazkov.cpg.analysis.ControlFlow.analyze(diagnosed),
                com.kniazkov.cpg.analysis.DataFlow.analyze(diagnosed),
                com.kniazkov.cpg.analysis.UnreadWrites.analyze(diagnosed))) {
            assertTrue(snapshot.hasErrors());
            assertEquals(List.of(finding), snapshot.semanticDiagnostics());
        }
    }
    @Test void legacyFindingsKeepTheirConstructorAndDoNotImplyAnError() {
        var warning = new Diagnostic("note", Diagnostic.Severity.WARNING, "Ordinary finding");
        assertNull(warning.primaryRange());
        assertTrue(warning.relatedRanges().isEmpty());
    }
}
