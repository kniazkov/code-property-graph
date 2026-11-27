# Phase 4 report — checks and oracles (clause 1)

Date: 2026-11-27  
Status: clause 1 complete; clauses 2-3 and later batches remain.  
Plan: `docs/cpp03/SPEC_REFORMAT_PLAN.md`, Phase 4

## Scope

Clause 1 has 79 normative statements. All 79 now have a check.

| Check kind | Oracle | Trust | Status | Count |
| --- | --- | --- | --- | ---: |
| manual | manual | medium | active | 60 |
| manual | manual | low | pending | 19 |

The 19 pending checks cover statements that need a memory-model checker, an
object-model analysis, or a runtime execution model. Each pending check records
its reason in `inputs.reason`.

## Check design

- Every check records the private locator and the reviewer.
- Every check has a negative control: a mutation that must make the check fail.
- No check uses a compiler as its only oracle.
- No normative statement is waived.
- Active checks assert that the statement's meaning matches the source.
- Pending checks are explicit placeholders with a stated blocking reason.

## Coverage

```text
cpp03: 83/349 normative statements covered (64 active, 19 pending), 266 uncovered
```

Clause 1 is fully covered: 79 of 79. The remaining 266 uncovered statements
belong to clauses 2-3 and later batches.

## Files

- `languages/cpp03/checks/CPP03-1.*-manual.yaml` — 60 active manual checks.
- `languages/cpp03/checks/CPP03-1.*-pending.yaml` — 19 pending checks.
- `reports/cpp03/coverage.json` — now reports `active`, `pending`, and
  `uncovered` counts separately.

## Verification results

- `tools/validate.py`: passed; 489 statements, 83 checks, 4 tests.
- `tools/coverage.py`: 83/349 covered (64 active, 19 pending).
- `tools/verify.py`: 83 checks, 4 tests, 80 checks without tests.
- `tools/license_check.py --strict`: no verbatim matches.

## Remaining

- [ ] Add checks for clauses 2 and 3.
- [ ] Replace pending checks with executable oracles as the parser grows.
- [ ] Add test manifests in Phase 5.
- [ ] Review check quality in Phase 6.
