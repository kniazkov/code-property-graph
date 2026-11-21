# Macro replacement (#203)

[Scope](macro-scope.json): all 26 R013 locators and two footnotes and the lparen grammar dependency.
Twenty-four new cards (000052–000075) reuse pilot cards 000005–000007 and the
phase-4 UCN rule 000041. Informative examples receive contextual dispositions.
The original PDF hash was rechecked; no later-edition empty-argument behavior
was imported. No continuation is needed for this allocation.

Macro provenance is CPG policy. The standard describes replacement behavior,
not our expansion-event graph. UB refusals do not establish required ISO
diagnostics. Empty source arguments differ from nonempty arguments subsequently
expanded away and from zero-parameter calls. Example fragments assume complete
newline-terminated fixtures; malformed-input checks isolate the named condition.

Inspected `parser/CppPreprocessorTest` covers argument counts, raw pasting,
redefinition, rescanning and CPG UB-refusal policy. Its `failure` helper checks
ParseException and a reason substring. Two exact assertions enter the shared
inspection registry as not-run. Existing provenance tests are related support,
not ISO obligations. All new cards remain partial/not_run; no full implementation
or executed-conformance claim is made.

Includes/conditionals and protected predefined names remain #204; lexical token
formation remains #427; footnotes remain #330; later DR policy remains #227.
No standards examples were copied. [Delivery](macro-delivery.json) records sizes
and checks separately from generated mirrors.
