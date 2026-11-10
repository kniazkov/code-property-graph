# Nested integral partial-pattern arguments: bounded #92 follow-up

Base: GitHub master `1289d0c002538ac748c25874ffb14151b6273091` after PR #129.
Its post-merge [CI 42770780915](https://github.com/kniazkov/code-property-graph/actions/runs/42770780915)
passed. That historical result does not certify this package.

## Scope and boundaries

Named independent integral values inside explicitly written nested
class-template-id arguments of a partial specialization use snapshot-scoped
constant facts. For example, `Box<Key<T, fixed> >` can match `Box<Key<int, 2> >`
when the bound independent constant `fixed` has value 2. The nested head must
resolve through its bound primary declaration or a verified instance origin,
never by spelling alone. Integral value parameter types must be concrete from
the nested pattern's own preceding arguments, without using actual arguments
at the use site to certify otherwise dependent patterns.

The same normalized facts serve matching, partial ordering and alpha-equivalent
redeclaration identity. An identity-keyed read-only overlay retains the written
argument nodes rather than rebuilding headers with edit/build. A contextual
TypeIdentity expression override supplies normalized value identities without
changing ordinary identity queries. This preserves parameter, head and typedef
identities. Original headers and initializer chains remain written
layout/folding dependencies, including initially unselected candidates whose
headers are changed later. Existing bool, unsigned modulo and representable
signed conversions apply. Pointer/reference identity, original enum promotion
and compatibility, snapshot ownership, cycle checks and layout ceilings remain
unchanged. No ABI policy or macro inventory count is added.

Unresolved/dependent/template-template heads, omitted nested defaults,
still-dependent value parameter types, unsupported arithmetic deduction and
signed out-of-range conversions remain outside this certified subset. This is
not general template deduction or complete C++03 clause 14 conformance. The
five macro obligations offsetof, setjmp, va_start, va_arg and va_end remain open;
issues #92 and #48 are not closed. Section 11/class.access implementation and
its audit matrix are outside this change.

## Normative evidence

The supplied original `INCITS+ISO+IEC+14882-2003.pdf` was freshly inspected
through its parsed PDF text: PDF pages 274–275 (printed 246–247) and 283–285
(printed 255–257). Paragraph 14.3.2p1 requires integral/enumeration constant
expressions; p5 permits the category's integral promotions and conversions.
Paragraphs 14.5.4p8–p9 distinguish a simple parameter name from a specialized
argument and restrict dependent non-type expressions and parameter types.
The bounded independent `fixed` value does not involve the partial's parameters;
`Key<unsigned char, fixed, T>` supplies its concrete integral type itself.
A specialized value whose type remains dependent on a partial parameter is not
certified from an actual use-site type. Arithmetic deduction involving those
parameters is not added. Matching/fallback/ambiguity in 14.5.4.1p1–p4 and the
ordering transformation in 14.5.4.2p1 were also inspected.

The paragraph ledger's existing source/paragraph hashes remain historical;
this inspection did not independently recompute the original PDF byte hash.
No later draft or defect-report text was used as a replacement for the original
wording. Strict GCC/Clang C++03 comparisons remain differential tests, not proof
that every rule in these paragraphs is implemented.

## Tests and assertions

`ConstantTemplateArgumentsTest#namedNestedPatternsSelectAndOversizedHeaderDependenciesRemainGuarded`
updates the former unsupported-boundary assertion to require selection of the
nested named pattern, while retaining the oversized written-dependency guard.
The same class includes these focused assertions (names identify coverage,
not an execution result):

- `nestedNamedValuesMatchAliasesQualifiedChainsAndPrimaryFallback`,
  `nestedValuesOrderAndDeduplicateBoundRenamedParameters` and
  `nestedValuesRetainNamespaceHeadIdentityAndDeeperWrappers` cover selection,
  fallback, ordering, alpha-equivalent declarations, bound namespace heads,
  aliases and deeper type wrappers.
- `repeatedNestedTypeDeductionsCompareOriginalNormalizedBindings` compares
  the original bound types for repeated deduction. Equal named/literal
  integral arguments share normalized values without merging unequal values
  or different bound template heads. The assertions cover original, copied,
  reanalyzed, folded and reparsed snapshots.
  `repeatedNestedTypesRetainCanonicalCvAndFunctionParameterIdentities` retains
  canonical cv and function-parameter identities during those comparisons.
- `nestedValuesUseBoolModuloAndOwnConcretePrecedingTypes` and
  `nestedDeducedValuesRetainTheirDeclaredParameterType` cover conversions and
  the declared integral type of a deduced value in the instantiated body.
- `nestedHeaderEditsActivateAndDeactivateUnselectedPatterns` checks actual
  edits, dependency invalidation and predecessor preservation;
  `nestedAmbiguityRemainsDiagnosed` retains ambiguity diagnostics.
- `uncertifiedNestedValuesNeverSelectPrimary`,
  `omittedNestedActualDefaultsDoNotSilentlySelectThePrimary` and
  `nestedTemplateTemplateHeadsRemainOutsideNamedValueSubset` keep unsupported
  inputs guarded rather than silently selecting a primary layout. The omitted
  actual-default guard applies to families with nested named partial patterns;
  unrelated pre-existing default argument routes are not broadened or removed.
- `nestedPointerAndReferenceValuesKeepObjectIdentity`,
  `nestedValuesPreserveOversizedAndEnumWrittenDependencies` and
  `actualNamedArrayBoundsRemainOutsidePatternNormalization` cover object
  identity, original enum/size dependencies and unrelated array-bound behavior.

`TemplateTypeIdentityTest#contextualExpressionIdentitiesAreExactAndDoNotChangeSyntax`
checks that contextual overrides apply only to exact expression objects and do
not alter ordinary type identity queries or written syntax. The overlay's
identity preservation is scoped to these snapshot-owned bindings; it is not a
claim of general canonical equality or unrestricted template deduction.

`PartialConstantTemplateArgumentsIT#nestedNamedPatternsSurviveStagesAndHeaderEdits`
uses an initializer chain (`seed` then `fixed`) and a qualified enumerator.
It changes seed 1 to 2 and enum `second` 2 to 4, invalidating previously selected
headers and activating a previously unselected header.
`#nestedConversionsUseTheirOwnConcretePrecedingTypes` covers bool (seed -7 to 0),
unsigned char (511 to 256) and an integral parameter made concrete by its own
preceding unsigned-char argument. Every variant uses cstddef and stddef.h,
RESOLVED/GREEN/FOLDED, copied/reanalyzed snapshots, JSON, real edits, packaged
CLI and actual CPG expansion. The old snapshot's source and JSON must remain
unchanged. Expected observations change from const=8, enum=2, extent=9 to
const=3, enum=8, extent=4. Existing oracle assertions check size_t, integral
constant expressions, array and case contexts in strict GCC/Clang execution.
Oracle metadata preserves source hashes, versions, commands and observations
under the existing CI-uploaded partial-constant-template-arguments directory.
These describe assertions, not a successful execution claim; final gate results
are recorded separately.

## Verification gates

Focused unit and integration checks, a fresh complete Maven verify, catalogue
probes, corpus discovery, macro/paragraph validators, strict private JavaDoc and
the generated demo are separate gates. No prior package count or unfinished
run certifies this source. Final results and exact-head CI links belong in the
implementation PR description to avoid a checklist-only documentation rerun.
The bounded checklist entry is part of this implementation PR; it does not mark
any remaining macro obligation complete. No timeout or assertion is waived.

```sh
mvn -B '-Dtest=ConstantTemplateArgumentsTest,OffsetofContextsTest,*ClassTemplate*Test,EnumStorageLayoutTest,MemberPointerLayoutTest,OffsetofRuntimeTest' '-Dit.test=PartialConstantTemplateArgumentsIT' verify
mvn -B verify
bash scripts/probe-cpp03-library-catalogue.sh
python3 scripts/check-cpp03-reports.py
python3 scripts/test-cpp03-library-macros.py
python3 scripts/check-cpp03-library-macros.py
python3 scripts/test-inventory-cpp03-source.py
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
mvn -B dependency:build-classpath -Dmdep.includeScope=compile -Dmdep.outputFile=target/javadoc-classpath
javadoc -classpath "$(cat target/javadoc-classpath)" -quiet -Werror -private -Xdoclint:all,-missing -sourcepath src/main/java -subpackages com.kniazkov.cpg -d target/apidocs
bash scripts/demo.sh
```

## Краткий статус

Пакет ограничен независимыми целыми значениями в явно записанных аргументах
вложенных class-template-id частичных специализаций. Связи объявлений и
записанные зависимости сохраняются; типы из места использования не снимают
зависимость шаблона. Предыдущие результаты исторические, новые гейты
фиксируются отдельно. Пять обязательств макросов, #92 и #48 остаются открытыми.
