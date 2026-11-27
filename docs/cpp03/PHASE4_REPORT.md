# Phase 4 report — checks and oracles (batch A)

Date: 2026-11-27  
Status: batch A complete; 209 checks remain pending until the parser and
analysis layers grow.  
Plan: `docs/cpp03/SPEC_REFORMAT_PLAN.md`, Phase 4

## Scope

Batch A covers clauses 1-3 and the literal pilot. All 349 normative statements
now have a check.

| Group | Normative | Checks | Active | Pending |
| --- | ---: | ---: | ---: | ---: |
| 1 General | 79 | 79 | 60 | 19 |
| 2 Lexical conventions | 63 | 63 | 13 | 50 |
| 3 Basic concepts | 147 | 147 | 14 | 133 |
| Literal pilot (`2.13.x`) | 60 | 60 | 53 | 7 |
| **Total** | **349** | **349** | **140** | **209** |

## Check kinds

| Kind | Oracle | Trust | Status | Count |
| --- | --- | --- | --- | ---: |
| manual | manual | medium | active | 82 |
| manual | manual | low | pending | 209 |
| parser_ast | spec_rule | high | active | 50 |
| table_driven | spec_table | high | active | 8 |

The eight table checks cover trigraph replacement, the absence of other
trigraphs, alternative-token equivalence, keywords, alternative
representations, and the cv-qualifier ordering. The 50 parser checks cover
integer, character, and boolean literals.

## Check design

- Every check records the private locator and the reviewer.
- Every check has a negative control: a mutation that must make the check fail.
- No check uses a compiler as its only oracle.
- No normative statement is waived.
- Pending checks record the blocking reason in `inputs.reason`. They cover
  preprocessing, lexing, semantic analysis, the object model, and the runtime
  model.

## Coverage

```text
cpp03: 349/349 normative statements covered (140 active, 209 pending), 0 uncovered
```

## Files

- `languages/cpp03/checks/CPP03-1.*` — clause 1 checks.
- `languages/cpp03/checks/CPP03-2.*` — clause 2 checks.
- `languages/cpp03/checks/CPP03-3.*` — clause 3 checks.
- `languages/cpp03/checks/CPP03-2.13.*` — literal pilot checks.
- `reports/cpp03/coverage.json` — reports `active`, `pending`, and `uncovered`
  counts separately.

## Verification results

- `tools/validate.py`: passed; 489 statements, 349 checks, 4 tests.
- `tools/coverage.py`: 349/349 covered (140 active, 209 pending).
- `tools/verify.py`: 349 checks, 4 tests, 345 checks without tests.
- `tools/license_check.py --strict`: no verbatim matches.

## Remaining

- [ ] Implement the preprocessing pipeline, lexer, semantic analysis, and
      runtime model to activate the 209 pending checks.
- [ ] Add test manifests in Phase 5.
- [ ] Review check quality in Phase 6.
