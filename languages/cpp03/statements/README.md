# Statements

One file per clause or subclause. Each file contains a `statements` list.

```yaml
schema_version: 1
edition: N1638
clause: "2.13.1"
title: "Integer literals"
statements:
  - id: CPP03-2.13.1-001
    type: grammar
    modality: is
    normative: true
    title: "..."
    statement: >
      ...
    subject: "..."
    condition: "..."
    requirement: "..."
    exceptions: []
    depends_on: []
    related: []
    provenance:
      private_locator: "2.13.1/grammar"
      fingerprint: "sha256:..."
      extracted_at: "2026-11-25"
    verification: []
    status: draft
```

## Current coverage

- `CPP03-2.13.1.yaml` — integer literals: grammar, form, base, type, suffixes,
  the ill-formed case.
- `CPP03-2.13.2.yaml` — character literals: grammar, ordinary/wide,
  multicharacter, escape sequences, universal-character-name.
- `CPP03-2.13.5.yaml` — boolean literals.

Related files (relative to the language root `languages/cpp03/`):

- `grammar/cpp03-literals.ebnf` — independent grammar for the subset;
- `tables/escape-sequences.yaml` — escape-sequence table;
- `tables/execution-charset.yaml` — example character set table;
- `checks/` — checks bound to statements;
- `tests/lex/` — test manifests.

## Rules

- One statement carries one requirement.
- Identifiers are never reused.
- `normative: false` only for examples and notes.
- Checks refer to a statement through `statement_id`.
