# C++03 specification reformatting plan

Version: 0.1  
Date: 2026-11-26  
Status: draft  
Input document: ISO/IEC 14882:2003 working draft N1638 (private copy)

## 1. Goal

Convert the entire primary C++03 specification into the statement format defined
in `docs/SPEC_FORMAT.md`, stored under `languages/cpp03/`, without publishing the
source text.

Scope:

- clauses 1 through 27;
- Annexes A through E;
- all normative requirements, definitions, grammar productions, tables,
  constraints, undefined/implementation-defined/unspecified behavior, examples,
  and notes.

Out of scope:

- distributing the source PDF, TXT, or HTML;
- legal advice;
- full parser implementation for every clause. Checks may be deferred until the
  parser supports the construct, but the statement must still be extracted.

## 2. Guiding principles

- [ ] One statement carries one requirement.
- [ ] Normative and informative statements are separated.
- [ ] No source text, long quotation, or paraphrase chain enters the repository.
- [ ] Every statement has provenance: private locator and fingerprint.
- [ ] Every normative statement has at least one check or an official waiver.
- [ ] Checks use independent oracles where possible.
- [ ] Identifiers are unique, stable, and never reused.
- [ ] The process is deterministic and reproducible.
- [ ] The 2.13 pilot is the reference implementation of the format.

## 3. Inputs and outputs

Inputs:

- [ ] private N1638 TXT as the primary text source;
- [ ] private N1638 HTML for tables, the index, and coordinates;
- [ ] private N1638 PDF for confirmation;
- [ ] `docs/SPEC_FORMAT.md`;
- [ ] `docs/JSON_FORMAT.md`;
- [ ] existing `languages/cpp03/` pilot for 2.13.1, 2.13.2, and 2.13.5.

Outputs per clause:

- [ ] `languages/cpp03/statements/CPP03-<clause>.yaml`;
- [ ] `languages/cpp03/grammar/*.ebnf` additions;
- [ ] `languages/cpp03/tables/*.yaml` additions;
- [ ] `languages/cpp03/checks/*.yaml`;
- [ ] `languages/cpp03/tests/**/*.yaml`;
- [ ] `languages/cpp03/oracles/*` additions;
- [ ] provenance records without source text;
- [ ] coverage and result reports.

## 4. Phases

### Phase 0 — Preparation

- [ ] Freeze the private source and record its hash.
- [ ] Confirm the clause and annex inventory.
- [ ] Create `schema/statement.schema.json`, `schema/check.schema.json`, and
      `schema/test.schema.json`.
- [ ] Create `tools/validate.py`, `tools/verify.py`, `tools/coverage.py`,
      `tools/diff.py`, and `tools/license_check.py`.
- [ ] Add CI jobs for schema validation, ID uniqueness, referential integrity,
      coverage, and verbatim-match detection.
- [ ] Define the private locator format per clause and annex.
- [ ] Define the fingerprint normalization and record it in
      `provenance/README.md`.
- [ ] Define the reviewer registry and sign-off rules.
- [ ] Freeze the pilot files as the format reference.

### Phase 1 — Inventory and partitioning

- [ ] Build a clause map: number, title, subclauses, annexes, page range.
- [ ] Record the page range and fingerprint of each subclause in the private map.
- [ ] Count paragraphs, grammar blocks, tables, examples, and notes per
      subclause.
- [ ] Mark clauses that are mostly library descriptions and need a different
      extraction cadence.
- [ ] Assign a batch and an owner to each clause.
- [ ] Record dependencies between clauses.
- [ ] Produce the initial coverage baseline.

### Phase 2 — Extraction

For each subclause:

- [ ] Extract definitions.
- [ ] Extract grammar productions, one production per statement.
- [ ] Extract requirements with `shall`, `shall not`, and `may`.
- [ ] Extract constraints and diagnosable rules.
- [ ] Extract undefined, implementation-defined, and unspecified behavior.
- [ ] Extract table rows as `table_row` statements or table files.
- [ ] Extract examples as `informative_example`.
- [ ] Extract notes as `informative_note`.
- [ ] Extract cross-references into `depends_on` and `related`.
- [ ] Atomize compound sentences.
- [ ] Assign IDs.
- [ ] Add provenance.
- [ ] Set `status: draft`.

### Phase 3 — Provenance

- [ ] Every statement has `private_locator`.
- [ ] Every statement has `fingerprint`.
- [ ] Every statement has `extracted_at`.
- [ ] Locators use the agreed `<clause>/<paragraph>` or `<clause>/grammar` form.
- [ ] Fingerprints are recomputed after any extraction fix.
- [ ] The private statement-to-source map is updated.
- [ ] No source text is present in the public files.

### Phase 4 — Checks and oracles

- [ ] Map each statement type to a check kind.
- [ ] Prefer `spec_table`, `independent_grammar`, and `independent_evaluator`.
- [ ] Use `compiler` only as a secondary oracle.
- [ ] Add a negative control for every check.
- [ ] Record trust level.
- [ ] Mark checks that need parser support as `pending` with a reason.
- [ ] Add an official waiver for statements that cannot be checked yet.
- [ ] Verify that no statement is checked only by a compiler.

### Phase 5 — Tests

- [ ] Create a test manifest per check.
- [ ] Include positive cases.
- [ ] Include negative cases.
- [ ] Include boundary cases.
- [ ] Include determinism cases.
- [ ] Link tests to statements.
- [ ] Add regression cases for fixed defects.
- [ ] Verify that test data contains no source text.

### Phase 6 — Review

- [ ] Technical review: statement matches the source meaning.
- [ ] Legal review: no verbatim or near-verbatim text.
- [ ] Consistency review: terminology and modality are uniform.
- [ ] Cross-reference review: `depends_on` and `related` are valid.
- [ ] Oracle review: the check really proves the statement.
- [ ] Negative-control review: the check can fail.
- [ ] Sign-off recorded in the private map.

### Phase 7 — Integration

- [ ] Regenerate `generated/cpp03/statements.md`.
- [ ] Regenerate `generated/cpp03/statements.db`.
- [ ] Regenerate `reports/cpp03/coverage.json`.
- [ ] Regenerate `reports/cpp03/results.json`.
- [ ] Update `reports/summary.json`.
- [ ] Update `docs/cpp03/PARSER_COVERAGE.md`.
- [ ] Verify that CI is green.

### Phase 8 — Release

- [ ] All normative statements have checks or waivers.
- [ ] Coverage targets are met.
- [ ] No verbatim matches.
- [ ] No orphan checks.
- [ ] No duplicate IDs.
- [ ] All schemas validate.
- [ ] Release notes list the covered clauses and known gaps.
- [ ] Tag the release.

## 5. Clause inventory and batches

| Batch | Clauses | Focus |
| --- | --- | --- |
| A | 1–3 | general, lexical conventions, basic concepts |
| B | 4–6 | standard conversions, expressions, statements |
| C | 7–10 | declarations, declarators, classes, derived classes |
| D | 11–16 | access control, special members, overloading, templates, exceptions, preprocessing |
| E | 17–20 | library introduction, language support, diagnostics, general utilities |
| F | 21–23 | strings, localization, containers |
| G | 24–27 | iterators, algorithms, numerics, input/output |
| H | Annexes A–E | grammar summary, implementation quantities, compatibility, UCN |

Clause titles:

1. General
2. Lexical conventions
3. Basic concepts
4. Standard conversions
5. Expressions
6. Statements
7. Declarations
8. Declarators
9. Classes
10. Derived classes
11. Member access control
12. Special member functions
13. Overloading
14. Templates
15. Exception handling
16. Preprocessing directives
17. Library introduction
18. Language support library
19. Diagnostics library
20. General utilities library
21. Strings library
22. Localization library
23. Containers library
24. Iterators library
25. Algorithms library
26. Numerics library
27. Input/output library
A. Grammar summary
B. Implementation quantities
C. Compatibility
D. Compatibility features
E. Universal character names for identifiers

Batch rules:

- [ ] Finish one batch before starting the next.
- [ ] Do not mix extraction and review in the same batch.
- [ ] Re-run the full CI after every batch.
- [ ] Record the batch baseline and the batch result.
- [ ] Keep the pilot clause 2.13 as the format regression test.

## 6. Per-clause checklist

Copy this checklist into the private tracking record for every subclause.

- [ ] Source page range recorded.
- [ ] Subclause fingerprint computed.
- [ ] Paragraph inventory complete.
- [ ] Grammar productions extracted.
- [ ] Definitions extracted.
- [ ] Requirements extracted.
- [ ] Constraints extracted.
- [ ] Undefined behavior extracted.
- [ ] Implementation-defined behavior extracted.
- [ ] Unspecified behavior extracted.
- [ ] Tables extracted.
- [ ] Examples extracted as informative.
- [ ] Notes extracted as informative.
- [ ] Statements atomized.
- [ ] IDs assigned and unique.
- [ ] Provenance complete.
- [ ] `depends_on` and `related` complete.
- [ ] Checks assigned.
- [ ] Negative controls added.
- [ ] Tests added.
- [ ] Technical review passed.
- [ ] Legal review passed.
- [ ] Consistency review passed.
- [ ] Coverage report updated.
- [ ] Status set to `reviewed` or `verified`.

## 7. Extraction rules

- [ ] Split "and" and "or" lists when they carry independent requirements.
- [ ] Keep context in `condition`, not in the `statement` text.
- [ ] Keep exceptions in `exceptions`.
- [ ] Use `shall_not` for prohibitions.
- [ ] Use `is_not` for negative definitions.
- [ ] Use `implementation_defined` for behavior the implementation must document.
- [ ] Use `undefined` for behavior with no requirements.
- [ ] Use `unspecified` for behavior the implementation need not document.
- [ ] Use `informative` only for examples and notes.
- [ ] Treat each grammar production as one statement.
- [ ] Treat each table row as one `table_row` statement or one table entry.
- [ ] Attach footnotes to the statement they qualify.
- [ ] Do not merge requirements from different subclauses.
- [ ] Do not infer requirements that are not in the source.
- [ ] Paraphrase independently; never copy sentences.

## 8. Identifier rules

- [ ] Use `CPP03-<clause>-<number>`.
- [ ] Use `CPP03-DEF-<number>` for definitions without a clause.
- [ ] Use `CPP03-GRAM-<number>` for grammar-only statements if needed.
- [ ] Start numbering at `001` in each clause.
- [ ] Never reuse an ID.
- [ ] Mark replaced statements with `deprecated: true`.
- [ ] Record `supersedes` when a statement is split or merged.
- [ ] Keep the ID when a statement moves between clauses; update `clause`.

## 9. Provenance checklist

- [ ] `private_locator` present.
- [ ] `fingerprint` present.
- [ ] `extracted_at` present.
- [ ] `reviewer` present after review.
- [ ] Fingerprint normalization matches `provenance/README.md`.
- [ ] Private map updated.
- [ ] No source text in the public repository.
- [ ] No long quotation in commit messages, issues, or reports.

## 10. Check and oracle plan

| Statement type | Preferred check kinds | Preferred oracles |
| --- | --- | --- |
| `grammar` | `parser_ast`, `round_trip`, `property` | `independent_grammar` |
| `definition` | `parser_ast`, `property` | `spec_rule`, `independent_evaluator` |
| `requirement` | `parser_ast`, `compile_pass`, `runtime`, `property` | `spec_rule`, `spec_table`, `independent_evaluator` |
| `constraint` | `compile_fail`, `parser_diagnostic` | `spec_rule` |
| `behavior_undefined` | `compile_fail`, `runtime`, `manual` | `spec_rule`, `manual` |
| `behavior_implementation_defined` | `property`, `manual` | `spec_rule`, `manual` |
| `behavior_unspecified` | `property`, `manual` | `spec_rule`, `manual` |
| `table_row` | `table_driven` | `spec_table` |
| `informative_example` | `runtime`, `parser_ast` | `spec_rule` |
| `informative_note` | optional | `manual` |

Check rules:

- [ ] Every check has `statement_id`.
- [ ] Every check has a negative control.
- [ ] Every check records evidence.
- [ ] No statement relies only on `compiler`.
- [ ] `trust: high` is reserved for tables, invariants, and formal models.
- [ ] `trust: low` requires review or a waiver.

## 11. Test plan

- [ ] One manifest per check.
- [ ] Positive cases cover the normal path.
- [ ] Negative cases cover each rejection rule.
- [ ] Boundary cases cover limits and overflow.
- [ ] Determinism cases repeat the same input.
- [ ] Metamorphic cases cover equivalent spellings.
- [ ] Regression cases cover every fixed defect.
- [ ] Tests are independent of the parser implementation.
- [ ] Tests contain no source text.

## 12. Review gates

- [ ] Gate 1: schema and ID validation.
- [ ] Gate 2: provenance and legal check.
- [ ] Gate 3: technical review.
- [ ] Gate 4: check and negative-control review.
- [ ] Gate 5: coverage and CI.
- [ ] Gate 6: release sign-off.

A batch cannot move to the next gate until the previous gate is green.

## 13. CI and automation

- [ ] Validate YAML against schemas.
- [ ] Check ID uniqueness.
- [ ] Check `depends_on` and `related` integrity.
- [ ] Check that every normative statement has a check or waiver.
- [ ] Check oracle diversity.
- [ ] Run all checks.
- [ ] Build coverage and result reports.
- [ ] Check for verbatim matches with the private corpus.
- [ ] Regenerate documentation and databases.
- [ ] Fail on schema errors, duplicate IDs, missing checks, failed mandatory
      checks, orphan checks, and verbatim matches.

## 14. Metrics and targets

- [ ] 100% of normative statements have at least one check or waiver.
- [ ] 100% of grammar productions are covered.
- [ ] 100% of tables are covered.
- [ ] 0 verbatim matches.
- [ ] 0 orphan checks.
- [ ] 0 duplicate IDs.
- [ ] 0 statements without provenance.
- [ ] At least one non-compiler oracle per normative statement.
- [ ] Per-clause coverage recorded.
- [ ] Per-batch coverage recorded.

## 15. Definition of Done for a clause

A clause is done when:

- [ ] every subclause is inventoried;
- [ ] every normative statement is extracted and atomized;
- [ ] every statement has provenance;
- [ ] every normative statement has a check or waiver;
- [ ] every check has a negative control;
- [ ] every check has a test;
- [ ] technical, legal, and consistency reviews passed;
- [ ] coverage report is green;
- [ ] no verbatim match is detected;
- [ ] the clause status is `verified`.

## 16. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Legal risk from paraphrasing | private review, verbatim-match check, legal sign-off |
| Volume of clauses 17–27 | batch processing, library-specific cadence, partial definitions |
| Ambiguous source wording | record ambiguity in `disputed`, require reviewer decision |
| Implementation-defined behavior | model as `implementation_defined`, never as a fixed value |
| Undefined behavior | model as `undefined`, do not invent requirements |
| Parser gaps | mark checks `pending`, add waivers, keep extraction independent |
| Reviewer capacity | batch size limits, reviewer registry, rotation |
| Tooling gaps | build schemas and tools in Phase 0 |
| ID churn | freeze IDs early, never reuse, use `supersedes` |
| Stale coverage data | always run `mvn clean verify` and CI from a clean tree |

## 17. Deliverables checklist

- [ ] `languages/cpp03/statements/` covers clauses 1–27 and Annexes A–E.
- [ ] `languages/cpp03/grammar/` covers every grammar production.
- [ ] `languages/cpp03/tables/` covers every normative table.
- [ ] `languages/cpp03/checks/` covers every normative statement.
- [ ] `languages/cpp03/tests/` covers every check.
- [ ] `languages/cpp03/oracles/` contains independent evaluators.
- [ ] `languages/cpp03/provenance/` contains no source text.
- [ ] `reports/cpp03/` contains coverage and results.
- [ ] `generated/cpp03/` contains regenerated documentation.
- [ ] `docs/cpp03/PARSER_COVERAGE.md` is up to date.

## 18. Open decisions

- [ ] How to treat library clauses that specify partial class definitions.
- [ ] Whether Annex A is a separate grammar source or a cross-check.
- [ ] How to model implementation quantities from Annex B.
- [ ] How to model compatibility notes from Annex C.
- [ ] How to model deprecated features from Annex D.
- [ ] Whether universal-character-name ranges from Annex E are a table.
- [ ] Which external conformance suites are license-compatible.
- [ ] Which n-gram threshold to use for the verbatim-match check.
- [ ] Whether to generate a per-clause dashboard.
