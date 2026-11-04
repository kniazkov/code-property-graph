/* Code Property Graph: a finding attached to one syntax node. MIT License. */
package com.kniazkov.cpg.model;

import java.util.Objects;

/** Stable rule code and severity for tools, plus a message for source-oriented reports. */
public record Diagnostic(String code, Severity severity, String message) {
    public enum Severity { INFO, WARNING, ERROR }

    public Diagnostic {
        Objects.requireNonNull(severity);
        if (Objects.requireNonNull(code).isBlank() || Objects.requireNonNull(message).isBlank())
            throw new IllegalArgumentException("Diagnostic code and message must be present");
    }
}
