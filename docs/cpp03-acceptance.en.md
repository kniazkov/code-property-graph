# C++03 acceptance status

This report separates reproducible project tests from a claim of complete
ISO/IEC 14882:2003 conformance. The latter is **not established**.

## Normative audit gate

Task #48 requests an exact paragraph-by-paragraph audit against the published
2003 edition. The existing matrix is a rule-group inventory, not that audit.
The complete normative paragraph inventory and the number of verified paragraphs
have not been established from an accessible authoritative 2003 text. Therefore
that acceptance gate remains open. A passing compiler, the corpus, or the combined
demonstration cannot replace the missing source comparison.

The repository's N1638 working draft is dated April 2004 and incorporates changes
after C++03. It is useful historical material but is not interchangeable with the
published 2003 edition. See the [source record](standards/cpp/cpp03/README.ru.md)
and the edition notes in the language audit. No undocumented later correction is
claimed to be an intentionally adopted C++03 rule.

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
until the exact-edition source audit and any still-missing chapter coverage are
completed. Earlier checklist tasks and passing focused tests remain useful
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
