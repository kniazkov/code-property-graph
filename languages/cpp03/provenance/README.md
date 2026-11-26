# C++03 provenance

Language-specific provenance for the N1638 edition. The shared policy
(normalization, hashing, allowed fields) is in `../../../provenance/README.md`.

- `private_locator` points to a clause and paragraph of the private N1638 copy,
  for example `2.13.2/1` or `2.13.1/grammar`.
- `fingerprint` is the SHA-256 of the normalized paragraph.
- No source text, PDF, TXT, or HTML is stored here.

The statement-to-source-paragraph map stays in the private vault. This directory
is reserved for per-language extraction logs and review records that contain no
source text.
