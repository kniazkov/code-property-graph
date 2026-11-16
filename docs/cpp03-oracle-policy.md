# Defined-program oracles and CPG refusal policy (#97)

This changes test evidence, not production parser behavior. The basis is the
existing source-reviewed ISO/IEC 14882:2003 clauses 11–16 ledger. Later editions
and modern compiler acceptance do not change the printed-edition categories.
Only `tests` and `gap` change in five records; source hashes, normative fields,
diagnostic categories and coverage statuses remain unchanged.

| Input | Assertion contract |
| --- | --- |
| Full specialization alone | `fullSpecializationSelectsItsDefinitionWithoutExplicitInstantiation`: one instance/request, selected specialized body and call target, no errors, including copy/reanalysis. |
| Explicit instantiation alone | `explicitInstantiationSelectsPrimaryWithoutFullSpecialization`: one primary-template instance, declaration and call requests, returned parameter and call target, including copy/reanalysis. |
| Both for the same arguments, 14.7/5 | IFNDR. `ifndrSpecializationAndInstantiationOnlyChecksAnalysisRobustness` checks analysis/JSON/copy/reanalysis do not crash. No required acceptance, diagnostic, instance count, selected body or runtime result. |
| Empty argument, 16.3/10 | UB; `ubPolicyRejectsEmptyMacroArguments` pins CPG refusal. Zero-parameter calls and empty object-like replacements remain valid separate cases. |
| Invalid pasted token, 16.3.3/3 | UB; `ubPolicyRejectsInvalidPasteResults` pins refusal separately from valid pasting/rescanning. |
| Out-of-range line number, 16.4/3 | UB; `ubPolicyRejectsOutOfRangeLineNumbers` pins refusal separately from valid logical coordinates. |
| Predefined-name changes, 16.8/3 | UB; `ubPolicyRejectsPredefinedNameChanges` pins refusal. |

Template methods live in `TemplateInstantiationSemanticsTest`, preprocessing
methods in `CppPreprocessorTest`. The former combined template method is replaced
by three tests. Four UB families are extracted from the mixed malformed-macro,
valid-paste and logical-coordinate methods without removing protective assertions.

Mandatory diagnostics, UB refusal policy and IFNDR are distinct. Lack of a
required diagnostic does not forbid extra analysis. The unspecified order of
`##` is not a universal left-to-right oracle: the chained-paste provenance test
records CPG's own trace; `pre ## fix ## ed` yields the same final token in either
order. Implementation-defined facts require a profile; absence of `__STDC__`
is an assertion about the current CPG profile, not every C++03 implementation.

`Cpp03UndefinedBehaviorPolicyIT` reads four durable inputs from
[cpp03-ub-policy](../src/test/resources/cpp03-ub-policy/README.md), checks API/CLI
diagnostic JSON, copied/reanalysed error state and blocked source generation.
Neither UB nor IFNDR inputs are compiled/executed as native result oracles.
The existing `cpp03/template-instantiation` corpus independently specializes
`specialized<int>` and explicitly instantiates `identity<int>`; its graph,
API/CLI/JSON, copy, reparse and original/generated/folded execution checks run
under strict GCC and Clang.

## Reproduction and acceptance

```sh
mvn -B -ntp -Dtest=TemplateInstantiationSemanticsTest,CppPreprocessorTest \
  -Dit.test=Cpp03UndefinedBehaviorPolicyIT,Cpp03CorpusIT \
  -Dcpg.cpp03.scenario=template-instantiation verify
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
mvn -B -ntp clean verify
```

Requires JDK 21, Maven, GCC, Clang and Graphviz. Full verify and exact-final-head
GitHub CI are separate gates. Execution results, tested revision and fresh JAR
SHA256 belong in the PR after execution. This does not establish full paragraph
conformance or complete #48.
