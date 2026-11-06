# Diagnostic regressions after the original audit

This supplements, rather than replaces, the historical observations in
[ExpressionDiagnosticProbes.md](ExpressionDiagnosticProbes.md). It addresses
`cpp03:5.2.8:p6` and the converted-case-value part of `cpp03:6.4.2:p2`.
It does not claim complete support for either surrounding section, and does
not change the separate nested-access audit.

## Implemented behavior and boundaries

- `NameResolver` diagnoses `missing-typeinfo` for a `typeid` use without an
  earlier `<typeinfo>` inclusion. Opaque modeled headers use the current syntax
  order. Physical headers retain their expanded include name and final-token
  insertion point in preprocessing directive provenance, including empty headers.
  Inactive and failed includes do not record successful inclusion. Template
  copies use the original definition occurrence, not their later instantiation
  position. A different translation unit cannot supply the header. Removing or
  moving a modeled include does not reuse stale preprocessing evidence.
- `NameResolver` compares evaluated case constants after conversion to the
  promoted fundamental condition type, using the translation unit's target
  profile. This includes condition declarations, typedefs, references, bool,
  narrow integral types, and `wchar_t`. Unsigned conversion uses arbitrary
  precision modulo the target width.
- Enum underlying-type selection, class conversion functions, and bit-field
  condition promotions are not established by this change. Conditions containing
  a bit-field reference are deliberately conservative, including compound
  expressions. Equal raw values are still diagnosed, but no additional converted
  collision is inferred for these conditions. Signed out-of-range conversion
  also remains conservative: the profile does not specify that implementation-
  defined conversion. Non-evaluable constant expressions and full validation of
  condition/case admissibility remain separate work.
- Physical-header evidence belongs to captured preprocessing. Editing raw
  physical include directives requires replaying preprocessing; externally
  fabricated provenance without the new optional include metadata cannot prove
  a flattened physical header was included. Existing constructor overloads and
  existing JSON fields remain available; `includedHeader` and `startToken` are
  additive fields emitted only on successful include directives.

## Executed validation

Executed on 2026-11-06 with Java 21 and ECJ, using a freshly compiled production
artifact, not the audit's pre-existing `target` JAR:

- Artifact: `/tmp/cpp03-local.eIWY20/cpg.jar`.
- SHA-256: `349268c91716c4769b4bf02ee245b3254733352779a80de506c33009c2389b31`.
- 139 focused tests passed, zero failed or skipped, with a 512 MB Java heap.
  Suites: `TypeidHeaderValidationTest`, `SwitchCaseConversionTest`,
  `ObjectMemoryValidationTest`, `ObjectMemoryExportTest`,
  `TypedConstantFoldingTest`, `AggregateLayoutSemanticsTest`,
  `Cpp03StatementScopesTest`, `CppPreprocessorTest`,
  `DiagnosticProvenanceTest`, `MacroSourceTest`, `ConditionalCatalogModelTest`,
  `ProvenanceExportTest`, `LibraryForwardClassResolutionTest`, and
  `LazyLibraryGraphTest`.
- After adding a macro-expanded `typeid` invocation-order assertion, all 11
  `TypeidHeaderValidationTest` methods passed again against the same production
  artifact. The test class was recompiled separately.
- 15 focused integration tests passed, zero failed or skipped, with a 512 MB
  heap: `PreprocessorIT`, `ProvenanceIT`, `ConditionalCatalogIT`,
  `MacroEditingIT`, and the `casts-memory` corpus scenario. That scenario checks
  original, generated, and folded programs with both g++ and clang++ in strict
  C++03 mode. Its valid input now includes `<typeinfo>` instead of merely forward
  declaring `std::type_info`.
- This is focused validation, not a claim that the complete Maven/full suite ran.

The new regression methods cover missing/late/prior headers, unrelated headers,
forward declarations, macro include operands and invocations, inactive/failed
includes, physical/transitive/empty/repeated header occurrences, TU isolation,
function and class templates, modeled-header edits, and retained completeness
errors. Switch cases cover default 32-bit int / 64-bit long and a 16-bit int
profile, bool/short/wchar promotions, nested switches, condition declarations,
and the explicitly conservative boundaries above.

## Fresh CLI replay of the original two inputs

Both commands used `--from cpp --stage resolved` and the default profile. The
inputs are exactly those in the historical audit. Each JSON was recursively
inspected for all `diagnostics` arrays.

```sh
java -jar /tmp/cpp03-local.eIWY20/cpg.jar --from cpp \
  --input /tmp/cpp03-diagnostic-current.uydHll/typeid --stage resolved \
  --json /tmp/cpp03-diagnostic-current.uydHll/typeid.json
java -jar /tmp/cpp03-local.eIWY20/cpg.jar --from cpp \
  --input /tmp/cpp03-diagnostic-current.uydHll/switch --stage resolved \
  --json /tmp/cpp03-diagnostic-current.uydHll/switch.json
```

- Missing header: exit `1`; diagnostic codes exactly `[missing-typeinfo]`.
  JSON SHA-256: `1e949894803424c0d01c756526b06a554f49eddba8d2dbfff6759299404ad7f9`.
- Converted case collision: exit `1`; diagnostic codes exactly `[duplicate-case]`.
  JSON SHA-256: `76ab85249f832c26651e8ed1a90aaaf6ef2e72b3752ff443f1d659cf5e59c5ae`.

These hashes identify these executions, including their temporary source paths;
new output paths will produce different JSON hashes. Do not overwrite the old
probe observations or attribute these results to the old audit artifact.

## Independent-review corrections

The initial regression patch passed the focused tests above, but independent
review found two additional edit cases: moving a function from a physical-header
TU into a TU without the header reused its old provenance, and cloning an early
function after a modeled include overwrote the earlier occurrence's position.
The earlier measurements remain historical observations of that initial patch.

The corrected resolver now checks physical-header evidence against the exact
preprocessing record of the current owning source file. Written occurrences use
their own syntax positions. Only explicit function/class instantiation edges map
derived occurrences to their own definitions, rather than using a TU-wide table
keyed by token offsets. Template re-resolution forms its internal identity from
a detached diagnostic-free syntax copy when needed, while keeping project errors
and public invalid-source-export rejection intact.

Executed correction validation on 2026-11-06:

- Fresh artifact: `/tmp/cpp03-local.Q8ffU0/cpg.jar`.
- SHA-256: `fd56364e85ab7fcdc1f0c3686c3fdce5bd19c187731fc6476a116c8f0314d8d0`.
- 85 focused tests passed, zero failures or skips, at 512 MB: the two diagnostic
  suites plus `TemplateInstantiationSemanticsTest`,
  `TemplateInstantiationExportTest`, `ClassTemplateSemanticsTest`,
  `LazyLibraryGraphTest`, `ProvenanceExportTest`, and `MacroSourceTest`.
- New regressions cover physical/opaque cross-TU moves of both functions and
  individual expressions; moved function/class template definitions; cloned
  ordinary functions and function templates on opposite sides of an include;
  and whole-project copy, editor rebuild, and repeated resolution. Valid
  physical-header template evidence survives those supported rebuilds. Additional
  assertions preserve the original diagnostic snapshot during re-resolution, keep
  public source export blocked, and check the offending typeid's exact file, line,
  columns and offsets in both the API and JSON primary range.
- The independent review's two reproductions now diagnose both cross-TU moves,
  and diagnose only the original early occurrence in the cloned-function case.

The complete suite and remote CI remain separate validation steps.

## Selected-definition correction

A further independent review found that a function instance's `origin()` can
identify a forward declaration or the primary template rather than the body
actually copied. Function instances now retain that body as a separate owned
reference, `selected()` / JSON `selectedId`, mirroring the existing class-instance
model. The resolver uses that definition when checking a derived `typeid`.
`TemplateInstance.complete()` also applies its error check to expression nodes;
the previous specialized visitor overloads accidentally bypassed that check.
No change was made to class-template selection.

Executed on 2026-11-06:

- Fresh production artifact: `/tmp/cpp03-local.NkDAtR/cpg.jar`.
- SHA-256: `8f734ad505afe468152e6f496f7204e1a6e8e6a5bdc74a4dd2397eef25d74b5e`.
- 98 focused tests passed with zero failures or skips at 512 MB, in
  `TypeidHeaderValidationTest`, `TemplateInstanceTest`,
  `TemplateInstantiationExportTest`, `TemplateInstantiationSemanticsTest`,
  `SwitchCaseConversionTest`, `ClassTemplateSemanticsTest`,
  `LazyLibraryGraphTest`, `ProvenanceExportTest`, and `MacroSourceTest`.
  After strengthening the selected-body edit/rebuild assertions, that test was
  recompiled and passed again against the same production artifact.
- New tests distinguish forward declarations from later definitions and primary
  templates from selected full specializations. They verify early/late header
  diagnostics, instance completeness/status, API ownership, exact JSON reference
  targets, copying, repeat resolution, and invalidation after editing the selected
  body. A plain syntax copy retains `selectedId`; its diagnostic assertions run
  after resolution, as ordinary editing does not retain all semantic findings.
- Positive specialization coverage uses an ordinary call. The positive explicit-
  instantiation case has no explicit specialization: combining both forms for
  the same specialization is not a positive C++03 conformance oracle (14.7/5).
- The review reproductions now report `missing-typeinfo` in both written and
  derived bodies and `complete=false` when the selected definition precedes the
  header. Re-resolution preserves those results. The previous cross-TU and cloned
  occurrence reproductions also retain their corrected results.

A separate, pre-existing explicit-instantiation selection limitation was verified:

```cpp
template<class T> void f();
#include <typeinfo>
template<class T> void f(){typeid(T);}
template void f<int>();
```

Both the PR86 baseline artifact (SHA-256
`e5b3191500fb4d816e0060cfafe4bbddc401d75faa9a317c792b73612a75eefc`)
and this correction produce no instance and `invalid-explicit-instantiation`.
Without the first forward declaration, both produce a complete instance with no
errors. GCC and Clang accept the full source with `-std=c++03 -pedantic-errors
-fsyntax-only` (exit 0; Clang reports unused-value warnings). This valid-input
selection gap is separate from the missing-header fix and remains follow-up work.


## #88: enum, class and bit-field converted-case typing

This section describes the #88 source/test scope and the focused execution below.
Earlier commands, JAR hashes and observations above remain historical evidence
for their original sources, not this extension. Final local aggregate validation is reported
separately below; CI remains pending.

The profile choices and printed-edition limits are documented in the
[English model guide](../../model-guide.en.md#switch-converted-case-diagnostics-88)
and [Russian model guide](../../model-guide.ru.md).
The implementation uses `EnumSemantics`, `BitFieldSemantics`, `ConstantEvaluator`,
`IntegralConstants`, `StandardConversions`, expression typing and `NameResolver` for duplicate-case
proof, without asserting runtime class-conversion calls or full admissibility.

New regression assertions in
[`SwitchConversionCompletenessTest`](../../../src/test/java/com/kniazkov/cpg/SwitchConversionCompletenessTest.java)
cover:

- `enumUnderlyingChoicePrecedesFullRangePromotion`: explicit enum choices,
  representability boundaries, aliases/references, condition declarations and
  enum bit-fields. `enumAndCertifiedConstCasesAreEvaluatedContextually` adds
  enumerator dependencies and certified const/static-const cases.
- `unknownSignedConversionAndOptInWrapAreDistinctFromOverflow`: default unknown
  versus explicit two's-complement conversion, width variation, explicit casts,
  raw equal-value fallback and refusal to infer defined signed overflow.
- `bitfieldPromotionsUseValueWidthNotStorageWidth`,
  `plainBitfieldSignednessIsAnIndependentTargetChoice`,
  `bitfieldDescendantsDoNotTaintUnambiguousResultTypes` and
  `preCwg324ResultProvenanceStaysUnknown`: direct width/padding, plain versus
  explicit signedness, expression promotions and the printed pre-CWG324 boundary.
- `uniqueClassConversionsIncludeReferencesDeclarationsAndInheritance` and
  `ambiguousInaccessibleAndUncallableClassConversionsAreNotGuessed`: exactly one
  eligible conversion function, inheritance/access/cv/reference cases, no modern
  unique-target relaxation or guessed conversion chains.
- `foldableButForbiddenCaseSyntaxAndNonconstantsStayUnevaluated`: 5.19 syntax in
  all arms, volatile/reference/nonstatic-member exclusions and cycle guards.
- `nestedLabelsCopyJsonRangesAndEditedRebuildRemainExact`: per-switch identity,
  diagnostic locations, copying/export and rebuilding after an edit.

These scoped assertions were exercised in the focused run recorded below.
Ledger updates are limited to 6.4.2/2 and the relevant portions of 4.5/2, 4.5/3,
4.7/3, 7.2/5 and 9.6/3. In particular enum sizeof, bit-field layout/address/reference
restrictions and complete switch/ICE admissibility are not established here.


### #88 review-driven scope additions

The following review regressions are included in the focused run below:

- `enumPromotionsShareTheOverloadPolicy` checks shared enum promotion policy.
- `contextualConstantsReachNestedPromotionExpressions` checks enum/const names in
  field widths and enum initializers within nested unary/arithmetic promotions.
- `referenceConditionsAndProtectedReceiversUseActualObjectTypes` checks reference
  declarations and protected conversion access against the receiver's actual type.
- `lvalueWrappersCannotEraseUnknownHistoricalFieldProvenance` retains conservative
  pre-CWG324 provenance through implicit lvalue-to-rvalue wrappers.
- `ownershipRatherThanAccessMetadataCertifiesStaticConstants` checks that actual
  declaration ownership, not access metadata alone, certifies static constants.
- `repeatedCrossEnumDependenciesAreMemoizedAndCyclesRemainUnknown` checks repeated
  enum dependencies under a time bound; existing cycle examples remain in
  `foldableButForbiddenCaseSyntaxAndNonconstantsStayUnevaluated`.
- `integralEnumCastsUseOnlyProvenValuesAndFloatingPolicyStaysUnknown` checks the
  deliberately narrow enum-cast range and floating-zero/nonzero boundary.

`IntegralConstants` owns snapshot/profile-scoped successful-value memoization,
cycle guards and enum/member ownership certification, shared by `TypeSemantics`
and `StandardConversions`. Enum casts prove only values between minimum/maximum
enumerators (zero for an empty enum), not the entire printed 7.2/6 range. The
normative wording/footnote 81 discrepancy and later CWG58 are not silently treated
as equivalent. Direct floating zero can be converted to integer; nonzero floating
literals remain unknown because storage sizes alone do not specify the target
floating representation or rounding. They are not thereby diagnosed invalid.
No broad C++ conformance conclusion is inferred from a C defect-report reading.

[`SwitchCaseConversionsIT`](../../../src/test/java/com/kniazkov/cpg/e2e/SwitchCaseConversionsIT.java)
adds `convertedCollisionsReachCliJsonAtTheConflictingLabel`,
`explicitProfilesControlCliConversionsIndependentlyOfHost` and
`validOriginalAndGeneratedSourcesRunOnBothStrictCompilers`. These assert API/CLI
JSON diagnostics and exact label locations, custom-profile behavior, refusal to
emit invalid examples, compiler-only rejection of invalid inputs and execution
of valid original/generated sources with GCC and Clang in strict C++03 mode.
Native compiler agreement is scoped to the supplied ordinary cases, not all
implementation-defined profiles. These assertions passed in the focused run below.


### #88 confirmed validation, production revision f190ae4

Production revision: `f190ae4127570c057bf34d0dc1bfd23c930be84b`.
A fresh local build at `/tmp/cpp03-local.Orwme5/cpg.jar`, SHA-256
`9561c93b74a8e8f69345621a9f4d6bb656e32d671f927df96b0d7ef1d59667ff`,
passed **91/91** focused tests with no failures. Log:
`/tmp/cpp03-switch-focused-final.log`. Suites:
`SwitchCaseConversionTest`, `SwitchConversionCompletenessTest`,
`PlatformProfileTest`, `OverloadResolutionTest`, `TypedConstantFoldingTest`,
`ProfileTypeSemanticsTest`, `Cpp03StatementScopesTest`, and
`e2e.SwitchCaseConversionsIT`.

Independent review reran the original reproductions and all 19 tests in
`SwitchConversionCompletenessTest`, reporting no blocker. JavaDoc also passed.
The ledger checker reported zero errors; its own tests passed 13/13. These checks
establish only their stated assertions and do not establish whole-language
conformance or complete coverage of the cited paragraphs.

A separate aggregate run on the same production revision used the `EExidt`
build, SHA-256
`99b010e85aa31e6b2c4635e616003404209b7c5c7c6c4fc3e1dc20508a37da11`.
It passed **1303/1305**, with two failures:
`PackagedCliIT.completeCppSnapshotAndSource` and
`JsonExportTest.completeSnapshotMatchesReviewedFixture`. Log:
`/tmp/cpp03-tools/full88-reviewed.log`. The failures were stale return-zero JSON
fixtures after adding explicit profile fields and changing the corresponding
stable-identity hash. Fixture-only revision
`f30303b18fd57b960bec9ae1e7111f0dbf3f0880` corrects exactly 37 JSON values per
fixture: 36 default-profile identity references and one profile text value adding
`bitFieldSignedness=signed`, `enumUnderlyingType=int-first`, and
`signedIntegerConversion=unknown`. Review found no other graph changes.
`JsonExportTest` and `e2e.PackagedCliIT` then passed **7/7** using the same `EExidt`
production JAR and hash above. Log: `/tmp/cpp03-switch-snapshots-final.log`.
The final local aggregate rerun on revision
`19ea97650b26685446ebf97ae9b6cd81d5284ee7` passed **1305/1305**, with zero
skipped, aborted or failed tests and all 150 containers successful, in
1,333,701 ms. Artifact: `/tmp/cpp03-local.MgnWrb/cpg.jar`, verified SHA-256
`2cd7c22e307e70e07c93eda6ddcb337755f63c311fbd5bf683c8c7d06e30e24a`.
Log: `/tmp/cpp03-tools/full88-final.log`. JavaDoc passed. This final run includes
the fixture corrections; the earlier 1303/1305 observation and 7/7 follow-up
remain recorded above. CI remains pending. Passing the local suite does not
expand the implementation scope or prove complete C++03 conformance.

Reproduction invocations for the recorded local artifacts (from repository root;
these temporary paths require the corresponding build and JUnit/JSON dependencies):

```sh
export PATH=/tmp/cpp03-clang-bin:$PATH
export CPATH=/usr/lib/gcc/x86_64-linux-gnu/14/include
java -Xmx512m -Dcpg.e2e.jar=/tmp/cpp03-local.Orwme5/cpg.jar \
  -jar /tmp/cpp03-tools/junit.jar execute \
  --class-path /tmp/cpp03-local.Orwme5/classes:/tmp/cpp03-local.Orwme5/test-classes:/tmp/cpp03-tools/json.jar:src/test/resources \
  --select-class com.kniazkov.cpg.SwitchCaseConversionTest \
  --select-class com.kniazkov.cpg.SwitchConversionCompletenessTest \
  --select-class com.kniazkov.cpg.PlatformProfileTest \
  --select-class com.kniazkov.cpg.OverloadResolutionTest \
  --select-class com.kniazkov.cpg.TypedConstantFoldingTest \
  --select-class com.kniazkov.cpg.ProfileTypeSemanticsTest \
  --select-class com.kniazkov.cpg.Cpp03StatementScopesTest \
  --select-class com.kniazkov.cpg.e2e.SwitchCaseConversionsIT \
  --details summary --disable-banner

java -Xmx512m -Dcpg.e2e.jar=/tmp/cpp03-local.EExidt/cpg.jar \
  -jar /tmp/cpp03-tools/junit.jar execute \
  --class-path /tmp/cpp03-local.EExidt/classes:/tmp/cpp03-local.EExidt/test-classes:/tmp/cpp03-tools/json.jar:src/test/resources \
  --select-class com.kniazkov.cpg.JsonExportTest \
  --select-class com.kniazkov.cpg.e2e.PackagedCliIT \
  --details summary --disable-banner
```
