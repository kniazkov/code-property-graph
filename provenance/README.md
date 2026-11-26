# Statement provenance

This directory describes how statements are linked to the private source
document without disclosing its text.

## What is stored in the public repository

Every statement has a `provenance` block:

```yaml
provenance:
  private_locator: "2.13.2/1"
  fingerprint: "sha256:15179602323662c186717753d942a86fd62c7d938007186f6832df1c918d97e4"
  extracted_at: "2026-11-25"
```

- `private_locator` — locator of the paragraph or grammar block in the private
  document. Format: `<clause>/<paragraph>`; for grammar, `<clause>/grammar`.
- `fingerprint` — SHA-256 of the normalized source paragraph text.
- `extracted_at` — extraction date.

The source text, PDF, TXT, and HTML never enter the public repository.

## Normalization before computing a fingerprint

1. Take the paragraph lines without running headers or page numbers.
2. Join the lines with a single space.
3. Replace any run of whitespace characters with a single space.
4. Trim leading and trailing whitespace.
5. Encode the result as UTF-8 and compute SHA-256.

The fingerprint proves that a statement still corresponds to the same fragment
of the private document without publishing the fragment itself.

## Pre-publication check

CI compares fingerprints against the private corpus and looks for verbatim
matches. The threshold and algorithm of that check are defined separately and
do not disclose the source text.
