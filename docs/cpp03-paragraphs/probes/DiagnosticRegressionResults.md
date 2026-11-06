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
