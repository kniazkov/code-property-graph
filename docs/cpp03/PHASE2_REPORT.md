# Phase 2 report — extraction (batch A)

Date: 2026-11-27  
Status: batch A normative extraction complete; examples, notes, table rows, and
cross-reference links remain for a follow-up pass.  
Plan: `docs/cpp03/SPEC_REFORMAT_PLAN.md`, Phase 2

## Completed work

Batch A covers clauses 1, 2, and 3. Extraction produced three statement files:

| File | Clause | Statements | Normative |
| --- | --- | ---: | ---: |
| `languages/cpp03/statements/CPP03-1.yaml` | 1 General | 92 | 79 |
| `languages/cpp03/statements/CPP03-2.yaml` | 2 Lexical conventions | 68 | 63 |
| `languages/cpp03/statements/CPP03-3.yaml` | 3 Basic concepts | 148 | 147 |
| **Total new** | | **308** | **289** |

With the 62 pilot statements from 2.13.1, 2.13.2, and 2.13.5, the repository now
contains 370 statements, of which 349 are normative.

### Statement kinds

| Kind | Clause 1 | Clause 2 | Clause 3 |
| --- | ---: | ---: | ---: |
| definition | 44 | 14 | 63 |
| requirement | 29 | 28 | 73 |
| constraint | 2 | 3 | 3 |
| grammar | 0 | 9 | 0 |
| behavior_undefined | 1 | 5 | 7 |
| behavior_implementation_defined | 0 | 4 | 1 |
| behavior_unspecified | 1 | 0 | 0 |
| cross_reference | 2 | 0 | 0 |
| informative_note | 13 | 2 | 1 |
| informative_example | 0 | 3 | 0 |

## Provenance

Every statement has a `provenance` block with:

- `private_locator` in the `<subclause>/<paragraph>` or `<subclause>/grammar`
  form;
- `fingerprint` computed as SHA-256 of the normalized private fragment;
- `extracted_at: "2026-11-27"`.

Fingerprints come from the private paragraph map generated from the frozen
N1638 TXT. The public repository contains no source text.

## License check

The first extraction pass produced 241 ten-word matches against the private
corpus. The affected statements were rephrased. The strict check now reports:

```text
license_check: no verbatim matches
```

## Coverage baseline

`tools/coverage.py` reports 4 of 349 normative statements covered, 345
uncovered. This is expected: Phase 2 assigns statements and provenance, while
checks and tests belong to Phases 4 and 5. No waivers are used; the uncovered
statements are recorded honestly.

## Verification results

- `tools/validate.py`: passed; 370 statements, 4 checks, 4 tests.
- `tools/coverage.py`: 4/349 normative statements covered.
- `tools/verify.py`: 4 checks, 4 tests, 1 check without a test.
- `tools/license_check.py --strict`: no verbatim matches.

## Remaining for batch A

- [ ] Extract table rows as `table_row` statements or table files.
- [ ] Extract every example as an `informative_example` statement.
- [ ] Extract every note as an `informative_note` statement.
- [ ] Populate `depends_on` and `related` from the candidate references in
      `clause-map.yaml`.
- [ ] Add checks and tests in Phases 4 and 5.

## Notes on granularity

- Clause 1 and clause 2 were extracted close to paragraph granularity.
- Clause 3 was extracted at subclause granularity for closely related
  paragraphs, while keeping distinct requirements and behaviors separate.
- Grammar productions in clause 2 are one statement per production group.
- Examples and notes were captured selectively where they clarify a rule; an
  exhaustive pass remains.
