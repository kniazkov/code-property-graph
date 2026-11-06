# C++03 acceptance status

This report separates reproducible project tests from a claim of complete
ISO/IEC 14882:2003 conformance. The latter is **not established**.

## Normative audit gate

Task #48 requests an exact paragraph-by-paragraph audit against the published
2003 edition. The existing matrix is a rule-group inventory, not that audit.
The user supplied the original 2003 edition on 2026-11-06. Its cover identifies
ISO/IEC 14882:2003(E), second edition, 2003-10-15, with INCITS adoption. Independent
checks found 786 PDF pages and consecutive printed pages 1–757. The private source
is not redistributed in this repository. Source SHA-256:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
The source review now covers all 5,250 inventoried locators: 3,923 numbered
paragraphs, 500 unnumbered blocks, 403 grammar productions, 105 tables, 318
footnotes and one numbering anomaly. Strict locator/review validation passes.
Each record identifies scoped evidence or an explicit gap. This completes the
source census review, not every implementation acceptance condition. A passing
compiler or demonstration cannot replace missing paragraph-specific evidence.

The repository's N1638 working draft is dated April 2004 and incorporates changes
after C++03. It is useful historical material but is not interchangeable with the
published 2003 edition. See the [source record](standards/cpp/cpp03/README.ru.md)
and the edition notes in the language audit. No undocumented later correction is
claimed to be an intentionally adopted C++03 rule.

The machine-checkable [paragraph audit](cpp03-paragraphs/README.md) records each source locator separately from implementation evidence. Its checker validates IDs, pages and citations, not semantic conformance. A planned example is not an executed regression, and a declaration-only catalogue does not establish a runtime contract.

## Evidence and limitations

- [Language rule groups and chapter evidence](cpp03-language-audit.en.md).
- [Mandatory library headers, signatures and effects](cpp03-library-audit.en.md).
- [Detailed implementation matrix](../CPP03-MATRIX.ru.md).
- [Reproducible end-to-end harness](e2e.en.md).

A recognized header or declaration does not establish complete overload coverage,
runtime library semantics, or macro support. A green syntax node does not by itself
prove standard conformance. Unsupported inputs and effects must be distinguished
from diagnosed violations and from rules for which no diagnostic is required.
Undefined behavior has no prescribed execution output; unspecified behavior must
not be narrowed to one compiler's observation. Implementation-defined behavior
must be tied to an explicit platform profile.

## Completion boundary

The language/library audit records observed support and remaining gaps. It must
not be read as a certificate for every normative paragraph. Task #48 stays open
while the chapter-level positive/negative/boundary coverage and correction-specific
regression evidence still have documented gaps. The exact-edition source census
has been reviewed, but planned examples are not substitutes for these tests. Earlier checklist tasks and passing focused tests remain useful
evidence for their documented scope, not a substitute for this final gate.

## Integrated program evidence

The `final-acceptance` corpus scenario combines two translation units and a shared
user header, macro expansion, an instantiated function template, `std::vector`,
virtual dispatch and a caught exception. API and independently followed JSON IDs
verify cross-unit call/definition links, template and virtual targets, macro
provenance, vector effects, CFG exception edges and call-site DFG dependencies.
Original, generated and CLI-reparsed/generated trees compile and run under both
strict C++03 compilers, producing `10 3 2` followed by a newline.

This scenario exposed a real reference-binding DFG defect: evaluated uses followed
the referent but omitted the immutable local binding. The fix records both where
appropriate, retains warnings for unused/unevaluated bindings, and does not make
overwrites of the referred object look read. Dedicated regressions and API/JSON
assertions protect the actual dependencies, not just the absence of a warning.

These checks close the combined-program scenario only. The normative and chapter
audit gates above remain open.

## Findings already confirmed by the source audit

These findings describe the original audit; subsequent corrections are identified separately:

- The original audit CLI accepted `typeid(int)` without a preceding `<typeinfo>`
  inclusion and two switch labels equal after unsigned condition conversion.
  The subsequent [diagnostic regressions](cpp03-paragraphs/probes/DiagnosticRegressionResults.md)
  now cover prior-header checks and promoted fundamental switch types. Enum/class/bit-field
  conditions and signed out-of-range conversion remain outside that conversion proof. [Reproduction commands and results](cpp03-paragraphs/probes/ExpressionDiagnosticProbes.md)
  identify the exact executable used.
- Nested/enclosing-class private access is inconsistent: the focused probe
  diagnoses private methods but misses corresponding field/type cases.
  [Probe and scope](cpp03-paragraphs/probes/README.md).
- The original edition requires 51 standard headers. The catalogue has 50 of
  them; `<strstream>` and its deprecated facilities are missing. The generic
  `std::rel_ops` operators are also missing.
- Many numerical, locale and stream records have only declaration or selected
  resolution evidence. They do not establish runtime values, exception behavior,
  complexity, buffering or locale state. Planned examples in the dataset are
  explicitly not executed acceptance tests.

Corrected library declarations/effects are distinguished from the printed 2003
wording, including list-splice handle preservation (LWG 250), vector-erase
invalidation at the erased position (LWG 414), const valarray subscripting (LWG
389) and proxy copy accessibility (LWG 253). A recorded correction is not proof
that every affected operation has a dedicated regression.
