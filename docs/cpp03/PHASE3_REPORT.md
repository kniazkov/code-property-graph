# Phase 3 report — provenance

Date: 2026-11-27  
Status: complete  
Plan: `docs/cpp03/SPEC_REFORMAT_PLAN.md`, Phase 3

## Completed items

- [x] Every statement has `private_locator`.
- [x] Every statement has `fingerprint`.
- [x] Every statement has `extracted_at`.
- [x] Locators use the agreed `<subclause>/<paragraph>`, `<subclause>/grammar`,
      `<subclause>/example:<n>`, and `<subclause>/note:<n>` forms.
- [x] Fingerprints were recomputed for every statement.
- [x] The private statement-to-source map was rebuilt.
- [x] No source text is present in the public files.

## Fingerprint recomputation

The first verification pass found 58 mismatches, all in the frozen pilot files
for 2.13.1, 2.13.2, and 2.13.5. Those fingerprints had been produced before the
normalization rules in `provenance/README.md` were fixed. They were recomputed
with the documented method, and `languages/cpp03/pilot.lock.yaml` was updated.

After the fix:

```text
statements: 489
missing fragments: 0
fingerprint mismatches: 0
```

## Private map

`statement-source-map.yaml` in the private vault records, for every statement:

- statement ID;
- subclause;
- private locator;
- fingerprint;
- extraction date;
- public source file.

The map contains no source text.

## New tool

`tools/provenance_check.py` validates the provenance block of every statement:

- locator syntax;
- SHA-256 fingerprint syntax;
- ISO extraction date;
- presence of all required fields;
- optional comparison with the private map through `--private-map`.

The tool runs in CI without the private map, so public CI needs no source text.

## Verification results

- `tools/provenance_check.py`: 489 statements verified.
- `tools/provenance_check.py --private-map ...`: 489 statements verified against
  the private map.
- `tools/validate.py`: passed; 489 statements, 4 checks, 4 tests.
- `tools/coverage.py`: 4/349 normative statements covered.
- `tools/verify.py`: 4 checks, 4 tests, 1 check without a test.
- `tools/license_check.py --strict`: no verbatim matches.
