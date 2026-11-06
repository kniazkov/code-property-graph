/* Producer-owned diagnostic products. MIT License. */
package com.kniazkov.cpg.model;

import com.kniazkov.cpg.passes.PassId;
import java.util.Objects;

/** A diagnostic with its independent producer and freshness. Equal codes or messages
 * from different producers never share ownership. Stale findings are inspectable here,
 * but omitted from the current node's diagnostics. */
public final class AnalysisDiagnostic {
    private final PassId producer;
    private final Node node;
    private final Diagnostic diagnostic;
    private final PassRun.Status status;
    final Object ownerIdentity;

    AnalysisDiagnostic(PassId producer, Node node, Diagnostic diagnostic,
            PassRun.Status status, Object ownerIdentity) {
        this.producer = Objects.requireNonNull(producer);
        this.node = Objects.requireNonNull(node);
        this.diagnostic = Objects.requireNonNull(diagnostic);
        this.status = Objects.requireNonNull(status);
        this.ownerIdentity = ownerIdentity;
    }
    public PassId producer() { return producer; }
    public Node node() { return node; }
    public Diagnostic diagnostic() { return diagnostic; }
    public PassRun.Status status() { return status; }
    AnalysisDiagnostic remap(Node target, PassRun.Status freshness) {
        return new AnalysisDiagnostic(producer, target, diagnostic, freshness, ownerIdentity);
    }
}
