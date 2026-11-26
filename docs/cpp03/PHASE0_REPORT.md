# Phase 0 report — preparation

Date: 2026-11-26  
Status: complete  
Plan: `docs/cpp03/SPEC_REFORMAT_PLAN.md`, Phase 0

## Completed items

### Source freeze

- [x] Private source frozen and hashed.
- [x] `provenance/source-lock.yaml` records SHA-256, byte size, and line count
      for the TXT, HTML, and PDF copies.
- [x] No source text is stored in the public repository.

### Clause inventory

- [x] `languages/cpp03/inventory.yaml` lists clauses 1–27 and Annexes A–E.
- [x] Batches A–H are assigned.
- [x] Clause 2 is marked `pilot` because 2.13.1, 2.13.2, and 2.13.5 are already
      processed.

### Schemas

- [x] `schema/statement.schema.json`
- [x] `schema/check.schema.json`
- [x] `schema/test.schema.json`

The schemas cover the fields used by the pilot and the rules in
`docs/SPEC_FORMAT.md`.

### Tools

- [x] `tools/validate.py` — schema validation, ID uniqueness, reference
      integrity.
- [x] `tools/coverage.py` — statement coverage report.
- [x] `tools/verify.py` — check and test wiring summary.
- [x] `tools/diff.py` — statement diff by ID.
- [x] `tools/license_check.py` — 10-word shingle check against the private
      corpus.
- [x] `tools/spec_common.py` — shared helpers.
- [x] `tools/requirements.txt` — PyYAML and jsonschema.
- [x] `tools/README.md` — usage.

### CI

- [x] `.github/workflows/verify.yml` with two jobs:
  - `build`: `mvn -B clean verify`;
  - `spec`: schema validation, coverage report, check wiring, license check.

### Provenance policy

- [x] Locator format defined for clauses, grammar, tables, annexes, and
      footnotes.
- [x] Fingerprint normalization recorded.
- [x] Source lock and reviewer registry defined.
- [x] Sign-off rules recorded in `provenance/reviewers.yaml`.

### Pilot freeze

- [x] `languages/cpp03/pilot.lock.yaml` records the SHA-256 of every pilot file.
- [x] The pilot is the format reference for later batches.

## How to run

```text
python -m pip install -r tools/requirements.txt
python tools/validate.py
python tools/coverage.py
python tools/verify.py
```

Optional strict gates, to be enabled after the pilot is fully checked:

```text
python tools/coverage.py --strict
python tools/verify.py --strict
```

License check:

```text
set CPG_PRIVATE_CORPUS=D:\Projects\cpg
python tools/license_check.py
```

## Verification results

- `tools/validate.py`: passed; 62 statements, 4 checks, 4 tests.
- `tools/coverage.py`: 4 of 60 normative statements covered, 56 uncovered.
- `tools/verify.py`: 4 checks, 4 tests, 1 check without a test.
- `tools/license_check.py --strict`: passed; no 10-word matches in public prose.

## Current baseline

- 62 pilot statements.
- 4 checks.
- 4 tests.
- Many normative pilot statements do not have checks yet; `coverage.py`
  reports them without failing.
- Strict coverage and strict check wiring are intentionally disabled in CI
  until the pilot checks are complete.
- Strict license checking is enabled in CI; the local check passes with the
  private corpus available.

## Remaining work before Phase 1

- [ ] Install the Python dependencies in the working environment.
- [ ] Run `tools/validate.py` and fix any schema mismatch.
- [ ] Decide whether `verification` references use `check:<id>` or plain IDs.
- [ ] Enable `coverage.py --strict` and `verify.py --strict` after the pilot
      checks are complete.
- [ ] Add the private corpus path to the local environment for license checks.
