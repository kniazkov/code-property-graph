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
  reviewer: "reviewer-id"
```

- `private_locator` — locator of the paragraph, grammar block, table, or annex
  in the private document.
- `fingerprint` — SHA-256 of the normalized source fragment.
- `extracted_at` — extraction date.
- `reviewer` — reviewer identifier after review; optional while in `draft`.

The source text, PDF, TXT, and HTML never enter the public repository.

## Locator format

| Kind | Format | Example |
| --- | --- | --- |
| Clause paragraph | `<clause>/<paragraph>` | `2.13.2/1` |
| Clause grammar | `<clause>/grammar` | `2.13.1/grammar` |
| Clause table | `<clause>/table:<number>` | `2.13.2/table:5` |
| Annex paragraph | `<annex>/<paragraph>` | `B/2` |
| Annex grammar | `<annex>/grammar` | `A/grammar` |
| Footnote | `<clause>/footnote:<number>` | `2.13.2/footnote:12` |

Paragraph numbers are one-based and follow the source paragraph numbering, not
the line numbers of the TXT file.

## Normalization before computing a fingerprint

1. Take the paragraph lines without running headers or page numbers.
2. Join the lines with a single space.
3. Replace any run of whitespace characters with a single space.
4. Trim leading and trailing whitespace.
5. Encode the result as UTF-8 and compute SHA-256.

The fingerprint proves that a statement still corresponds to the same fragment
of the private document without publishing the fragment itself.

## Source lock

`provenance/source-lock.yaml` records the SHA-256, byte size, and line count of
the frozen private source files. It contains no source text. Any change to the
private source requires a new lock entry and a provenance review.

## Reviewer registry

`provenance/reviewers.yaml` defines reviewer roles and sign-off rules. Reviewer
identities and review dates are stored in the private map; the public repository
stores only the `reviewer` identifier on a statement.

Sign-off rules:

- `reviewed` requires technical and consistency sign-off.
- `verified` requires technical, consistency, and legal sign-off.
- While only one reviewer is registered, self-sign-off is allowed.
- Add a second reviewer before the first public release.

## Pre-publication check

`tools/license_check.py` compares public text files with the private corpus using
10-word shingles. Set `CPG_PRIVATE_CORPUS` to the private vault directory. The
check is skipped when the variable is absent, so public CI does not need the
source text.
