# Requirement records and validation

This format implements #197 under [checklist #186](https://github.com/kniazkov/code-property-graph/issues/186).
The production registry starts empty. Synthetic examples exist only in
`scripts/test-requirements.py`; they are not C++ requirements or evidence.
The pilot is #198, document/reverse-index generation #199, evidence publication
#200, and CI integration #228. Do not bulk-import historical audit statuses.

## Files and identity

- `records/*.jsonl`: one requirement object per line, using `record.schema.json`.
- `ids.json`: permanent ID ledger. Each ID maps to `semantic_key` and `status`
  (`active` or `retired`). Active entries have exactly one record; retired entries
  have none. Never delete ledger entries, change their meaning or reactivate them.
- `sources.json`: edition-specific source catalogue. The initial ISO 2003 source
  uses the existing source inventory and verified PDF hash. Adding another draft,
  DR or external dependency requires a separate source ID and explicit inventory;
  do not relabel original-2003 locators as N1638. No standard text is copied.
- `dispositions.jsonl`: reverse source links and justified non-requirement/question
  dispositions, using `disposition.schema.json`.

IDs have the form `CPG-CPP03-REQ-000001`. Reserve the next unused number in the
ledger in the same PR that creates its record; coordinate parallel allocations
before merge. `semantic_key` describes a stable obligation, not a source line or
mutable title. Splitting/merging obligations retires old IDs and allocates new
ones. Synthetic tests use an isolated ledger with numbers beginning at 900001;
those numbers are not reserved in production.

Validate from the repository root:

```sh
python3 scripts/check-requirements.py
python3 scripts/test-requirements.py
```

For every subsequent registry PR also run
`python3 scripts/check-requirements.py --baseline-ref origin/documents` against a
fetched integration revision containing the ledger. The initial schema PR has no
prior ledger. A missing requested baseline is an error, never an empty-history
fallback. The baseline check rejects deleted IDs, changed semantic keys and
reactivated tombstones. Current-tree validation alone cannot detect historical
reuse. CI wiring remains owned by #228; do not enable CI on `documents` now.

## Record contract

Every field in the schema is explicit; unknown fields and duplicate JSON keys
are rejected. An empty array means no entries, never an omitted required field.
Write prose in English. A validator cannot establish linguistic quality or
independent normative interpretation; those remain review responsibilities.

| Fields | Meaning |
|---|---|
| `id`, `semantic_key`, `title` | Stable identity and concise independently testable obligation |
| `owner_issue`, `status_owners` | Accountable issue for the card and separately for readiness, implementation and verification; issue existence is reviewed in #186 |
| `basis` | `kind`, distinct `source_id`, one or more locators, confidence and review note |
| `conditions`, `exceptions`, `dependencies` | Context, exclusions and links to other registered requirement IDs |
| `rule`, `classification` | Own-word contract; distinguish well-formed, diagnostic-required, IFNDR, undefined, unspecified, implementation-defined and policy cases |
| `cpg_consequence`, `platform_policy` | Required graph/diagnostic behavior and explicit target/environment assumptions |
| `applicability` | Applicable or justified not applicable to CPG; absence of implementation is not non-applicability |
| `proposed_checks` | Unique local check IDs and descriptions of intended assertions, inputs and expectations |
| `evidence` | Actual result records referencing proposed checks, repository paths, exact assertion excerpts, full Git revisions, commands and pass/fail outcomes |
| `questions` | Explicit unresolved questions, each with a positive issue number |
| `states` | Independent readiness, implementation and verification values |

Confidence is `original_reviewed` only for an ISO-2003 basis, `draft_only` for a
draft, `dr_reviewed` for an explicitly identified DR, or `policy` for CPG policy.
`unresolved` is allowed for any basis, requires an owned question and prevents
reviewed readiness. File identity and inventory membership never populate source
confidence automatically. A policy basis must use policy classification; it cannot
masquerade as a standard-required diagnostic. Different outcomes or conditions
may need separate cards rather than one ambiguous rule.

## States and evidence

Readiness is `draft`, `reviewed` or `blocked`. Reviewed text has resolved source
confidence and no open questions; blocked text requires an owned question.
Implementation is `missing`, `partial`, `implemented` or `not_applicable`.
The last value must agree with justified non-applicability and has no execution
claim. Source review does not set implementation state.

Verification is `not_run`, `failed`, `partial` or `passed`. `not_run` has no actual
evidence; proposed checks alone do not count. Failed verification needs a failure,
and partial verification needs at least one passing result. Passed verification
requires reviewed text, implemented behavior, a nonempty proposed-check set and
passing evidence for every proposed check, with no failing evidence in that record.
Mixed outcomes cannot be called passed. For retries, curate the evidence for the
stated verification scope; Git retains prior records. Do not silently erase a
known failing condition by removing it from the proposed-check set.

Evidence must identify an existing repository file and an assertion excerpt in
that file at the cited commit. Paths cannot escape the repository; unknown revisions
or missing assertions fail validation. This checks traceability, not whether a
command really ran or whether assertions prove the entire requirement. Review
execution logs and coverage of conditions separately. Historical results at one
revision do not establish current-head conformance. #200 owns evidence publication.

## Many-to-many links and unresolved work

Multiple records may cite one locator, and one record may cite multiple locators.
Each authored link must appear in a matching reverse disposition; asymmetric,
dangling or duplicate dispositions fail. Unauthored locators need not receive
placeholder dispositions: all are already owned by the research allocation.
Validation of this partial registry makes no inventory-completeness claim.

A disposition is `requirements` with reciprocal requirement IDs,
`no_independent_requirement` with justification and no links, or `unresolved` with
justification and a question owner. Do not invent an obligation for an informative
example, footnote or numbering anomaly merely to fill a row. External normative
sources absent from the catalogue need bounded research tasks registered in #186;
link existing owners before adding duplicate work.

Both schemas use the documented JSON Schema subset (types, required properties,
closed objects, enums, patterns, lengths, minima and unique arrays). The standard-
library-only checker adds cross-file and state/evidence rules. It does not rewrite
records, run tests cited as evidence, invoke Maven or read standards PDFs.
