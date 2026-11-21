# Translation phases and comments (#202)

The [frozen scope](translation-scope.json) selects eight R011 locators and two
context footnotes. Eighteen new cards (000034–000051) extend four unchanged pilot
cards (000001–000004). All nine conceptual phases are addressed at their stated
boundaries; detailed referenced clauses are not exhausted. The original PDF hash
was rechecked. No later draft or DR was substituted.

The standard specifies observable phase ordering, not a mandatory pipeline
architecture. Physical/logical range preservation is a CPG engineering consequence.
Examples describe the indicated phase or fragment; complete positive source-file
fixtures must end in a newline. UB examples specify classification, never an
expected translation result. The line-comment control-character restriction is
IFNDR, not ordinary required-diagnostic behavior.

`partial` means related machinery exists, not accepted conformance. Inspected
`parser/CppLexerTest` assertions cover phase order, single-pass splicing and
physical mappings. `parser/CppPreprocessorTest.capturedHeadersUseNormalizedAbsoluteKeysWithoutFilesystemAccess`
checks included macro expansion and retained source. `CppLiteralsTest.decodesEscapesAndUsesExecutionEncoding`
checks profile-dependent literal decoding. `TemplateInstantiationSemanticsTest.selectedBodyFailureContainsConcreteTypeAndOrderedRequests`
checks a concrete failed instantiation. These files are under
`src/test/java/com/kniazkov/cpg/`. Three exact mappings are added to the shared
inspection registry; none was executed. All cards remain `not_run`.

[#427](https://github.com/kniazkov/code-property-graph/issues/427) owns the exact
remaining 42 R011 locators and ISO 10646 dependency triage. The original R011
metadata allocation is retained with this explicit suballocation. The continuation
must split further if its word budget requires it. Macro details remain with #203,
include/conditional policy with #204, literals with #336, template instantiation
with #215, and footnotes with #328. No completed-token-grammar or full-compiler
claim follows from this batch. See [delivery measurements](translation-delivery.json).
