# C++03 source record

The target is **ISO/IEC 14882:2003, Programming languages — C++**, second edition,
dated 15 October 2003. Its published wording governs the C++03 mode where source
documents disagree. The [ISO catalogue](https://www.iso.org/standard/38110.html)
identifies the edition; it is not verification of a particular PDF's bytes.

## Verified identity and availability

On **21 November 2026**, both reading copies below were available outside Git.
SHA-256, byte length and PDF page count were checked independently for each file.

| Source | Reading-copy identity | Observed size | Result |
| --- | --- | --- | --- |
| ISO/IEC 14882:2003 | `INCITS+ISO+IEC+14882-2003.pdf`; cover identifies the second edition and INCITS adoption, with ANSI approval dated 29 December 2003 | 2,461,934 bytes; 786 PDF pages | Exact match to the source recorded in the [paragraph inventory](../../../cpp03-paragraphs/inventory.json) |
| WG21 N1638 | Committee cover identifies `N1638=04-0078`, dated 11 April 2004 | 2,009,827 bytes; 772 PDF pages | Exact match to the previously recorded N1638 identity; a later working draft, not the published C++03 edition |

ISO reading-copy SHA-256:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.

N1638 reading-copy SHA-256:
`648c5ba99445f6df1a621ba4daec7e684d9c54f01df753610e01a329bac15096`.

The ISO copy has 757 numbered body/index pages, distinct from its total PDF
page count. Spot checks found printed page 1 at PDF page 29 and printed page 757
at PDF page 785, consistent with the inventory's offset of 28. PDF page numbers
here are one-based. These endpoint checks do not validate every stored locator.

This verifies access and file identity, not every requirement, paragraph mapping
or existing coverage claim. It does not independently certify the distribution
history of the INCITS copy. A matching hash also grants no redistribution right.
Availability is a dated observation; future audits must verify their own access
to the exact source. No PDF or extracted standard text belongs in this repository.

## Why N1638 is auxiliary

The [committee PDF](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1638.pdf)
postdates C++03. [Editors' report N1578](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1578.html)
describes N1577 as starting from ISO/IEC 14882:2003 and incorporating subsequent
changes; [N1639](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1639.html)
records further changes for N1638. Reports help locate differences; they do not
establish that a disputed N1638 rule already appeared in the 2003 edition.

Useful PDF search tags include `[lex]` (lexical conventions), `[expr]`
(expressions), `[stmt]` (statements), `[dcl.dcl]` (declarations) and `[dcl.fct]`
(function declarators). Record edition-specific clauses and paragraphs as well;
do not transfer page numbers or locators between documents.

## Source confidence and defect reports

Use the confidence distinctions from the [requirements plan](REQUIREMENTS-PLAN.md):

- **Verified against ISO 2003**: the particular claim and locator were checked
  against the target edition. File identity alone does not earn this status.
- **N1638 only**: state “Based on N1638; correspondence to ISO 2003 has not been
  verified.” This is provisional evidence if the ISO source is unavailable.
- **Unresolved**: identify missing access, an unverified locator or a source
  disagreement, and track the work needed to resolve it.

Later defect-report resolutions are not silently incorporated into the
printed-edition baseline. An adopted correction must identify its DR, affected
requirement, evidence for the edition difference and the explicit CPG decision;
keep the original edition classification separate from project policy. This
provenance task adopts no new DR and performs no complete edition/DR comparison.
It does not change existing requirement confidence or test expectations.

Use the ISO catalogue above to obtain the published edition; N1638 remains an
external WG21 reference. Acquisition and use follow the source's terms. See
[source storage and CPG evidence rules](../README.md).
