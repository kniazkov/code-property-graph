# Compliance contracts (#201)

[Scope](conformance-scope.json): R009 sections 1.1–1.4, 28 locators, plus three
context footnotes. Eighteen cards reserve IDs 000016–000033. The original PDF
hash was rechecked; no later draft or DR was substituted. Definitions receive
contextual dispositions where detailed operational rules belong elsewhere.

CPG analyzes and generates programs; these cards do not declare it a complete
C++ implementation. ISO obligations remain in `rule`; CPG engineering responses
remain in `cpg_consequence`. Extra diagnostics are policy. IFNDR, UB and
unspecified behavior are distinct; a required diagnostic need not mean rejection.
Detailed ODR, library and freestanding inventories are dependencies, not reviewed
here. Classification labels describe the stated cases, not every related input.

`partial` reflects related infrastructure, not accepted compliance. Inspected
existing checks include `DiagnosticGroupingTest.reversingInputOrderPreservesApiJsonSummariesAndCliOutput`
(diagnostic output), `PlatformProfileTest.documentsDefaultWidthsRangesAndKnownLayout`
(explicit widths), and `StandardLibraryCatalogueTest.everyFragmentParsesWithoutHostHeadersOnBothSizeTypePolicies`
(declarations without bodies). Paths are under `src/test/java/com/kniazkov/cpg/`.
None was executed; all new checks remain proposed. These assertions cannot
establish complete diagnostics, library execution or environmental conformance.

R009 retains its original metadata allocation; [#425](https://github.com/kniazkov/code-property-graph/issues/425)
owns its exact remaining 22 locators and vocabulary-dependency triage. This is an
explicit suballocation, not completed review of all R009. Footnotes remain with
#328. C-library edition reconciliation belongs to #225, character-set dependency
to #202, library contracts to #217, ODR to #206, overloads to #213 and DR policy
to #227. Implementation gaps retain existing implementation owners; these cards
create no completion claim. [Delivery](conformance-delivery.json) separates authored
prose from regenerated mirrors.
