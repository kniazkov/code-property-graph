# Phase 4 report — checks and oracles (clauses 1-2)

Date: 2026-11-27  
Status: clauses 1-2 complete; clause 3 and later batches remain.  
Plan: `docs/cpp03/SPEC_REFORMAT_PLAN.md`, Phase 4

## Scope

Clause 1 has 79 normative statements and clause 2 has 63. All 142 now have a
check.

| Clause | Checks | Active | Pending |
| --- | ---: | ---: | ---: |
| 1 General | 79 | 60 | 19 |
| 2 Lexical conventions | 63 | 13 | 50 |
| **Total** | **142** | **73** | **69** |

The four pilot checks cover statements in the separate 2.13.1, 2.13.2, and
2.13.5 files and are included in the repository totals.

## Check kinds

| Kind | Oracle | Trust | Status | Count |
| --- | --- | --- | --- | ---: |
| manual | manual | medium | active | 68 |
| manual | manual | low | pending | 69 |
| table_driven | spec_table | high | active | 5 |
| parser_ast | spec_rule | high | active | 2 (pilot) |
| table_driven | spec_table | high | active | 2 (pilot) |

The five new table checks cover trigraph replacement, the absence of other
trigraphs, alternative-token equivalence, keywords, and alternative
representations. They read the table files under `languages/cpp03/tables/`.

## Check design

- Every check records the private locator and the reviewer.
- Every check has a negative control: a mutation that must make the check fail.
- No check uses a compiler as its only oracle.
- No normative statement is waived.
- Pending checks record the blocking reason in `inputs.reason`. Clause 2 is
  mostly pending because the preprocessing pipeline, lexer, and
  floating/string literal parser are not implemented yet.

## Coverage

```text
cpp03: 146/349 normative statements covered (77 active, 69 pending), 203 uncovered
```

Clauses 1 and 2 are fully covered. The remaining 203 uncovered statements belong
to clause 3 and later batches.

## Files

- `languages/cpp03/checks/CPP03-1.*` — clause 1 checks.
- `languages/cpp03/checks/CPP03-2.*` — clause 2 checks.
- `reports/cpp03/coverage.json` — reports `active`, `pending`, and `uncovered`
  counts separately.

## Verification results

- `tools/validate.py`: passed; 489 statements, 146 checks, 4 tests.
- `tools/coverage.py`: 146/349 covered (77 active, 69 pending).
- `tools/verify.py`: 146 checks, 4 tests, 143 checks without tests.
- `tools/license_check.py --strict`: no verbatim matches.

## Remaining

- [ ] Add checks for clause 3.
- [ ] Implement the preprocessing pipeline, lexer, and floating/string literal
      parser to activate pending checks.
- [ ] Add test manifests in Phase 5.
- [ ] Review check quality in Phase 6.
