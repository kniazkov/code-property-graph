# C++03 #92: explicit HUGE_VAL positive-infinity capability

## Evidence status

The expanded focused run passed 432 unit tests and 31 integration tests in 13:53.
A separate eight-test inventory crosscheck passed, giving 440 distinct focused
unit tests across two commands. All results have zero failures, errors or skips.
Earlier 58-unit and dedicated seven-unit/three-IT runs are overlapping evidence,
not additional distinct tests. HUGE_VAL has bounded compiler_intrinsic support;
full Maven and exact-head CI remain separate gates. Earlier reports are unchanged.

## Original contract and source boundaries

Fresh visual inspection of the public final ANSI/ISO 9899-1990 §7.5, printed
page111/PDF page121, found the original requirement: HUGE_VAL expands to a
positive double expression, not necessarily representable as float. Footnote104
permits positive infinity in implementations supporting infinities. Original
wording does not require a constant expression. The selected compiler intrinsic
is constant as an implementation choice, not a universal C90 promise.

Public [C90 source PDF](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf)
SHA256: `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
This access is separate from the absent private ISO2003 PDF. Existing reviewed
C++03 26.5/1–2 supplies the edition anchor; no fresh private-source read is
claimed. Original ledger source hash and paragraph coordinates are retained:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.

## Explicit capability

```json
{"hugeVal":{"kind":"positive_infinity","emission":"gcc-clang-builtin-huge-val"}}
```

The narrow capability requires explicitly selected double binary64 format and
64-bit double storage. Missing or empty settings keep HUGE_VAL unavailable.
No libc-family, width-only, host or JVM inference enables it. The capability
selects compiler semantics independently of cRuntime; matching a native libc
HUGE_VAL is separate deployment evidence. Unknown kinds, emission identities
or incompatible formats are rejected, not replaced by DBL_MAX.

This is the positive-infinity branch only. Finite HUGE_VAL policies, other
formats, NaNs and full IEEE arithmetic are outside scope. The result has exact
double type and rvalue category, but no fabricated finite BigDecimal value or
nonfinite JSON number. HUGE_VAL has no integer preprocessing-arithmetic promise.
Ordinary cmath declarations and actual supplied-header authority remain intact.

## Intrinsic and replay boundary

CompilerIntrinsicExpression.Kind.DOUBLE_POSITIVE_INFINITY represents the
compiler-provided value. It does
not masquerade as a libc CallExpression, external declaration, link symbol,
division-by-zero expression or overflowing decimal literal. Emission is the
genuine zero-argument __builtin_huge_val() spelling. The selected profile also
permits that exact direct syntax so materialized/generated source can reparse;
ordinary profiles and unrelated builtins remain unsupported. Qualification,
wrong arity and bare-name/declaration misuse are rejected. The evaluated-rvalue
address diagnostic gap is documented below.

The node exports intrinsic=double_positive_infinity, cppType=double and the
fixed emission identity, with no numeric value field. Symbolic type/copy/visitor/
JSON/source-generation paths are covered by focused checks. Unsupported floating
operations remain conservative:
no invented integer result, inf-inf simplification, inf/inf identity or broad
floating evaluator is claimed. Unevaluated sizeof can use the double type.
The intrinsic adds no opaque-call memory invalidation and preserves actual
surrounding effects. Unsupported cross-language output is rejected
rather than emit a C++ builtin into another language.

## Executed focused acceptance

Both cmath/math.h spellings and mixed orders, unavailable/default/empty profiles,
physical-header precedence, macro provenance and TU/profile isolation have focused
coverage. Exact double overload selection, symbolic JSON, direct builtin grammar
and unsupported-use diagnostics are separate from native acceptance.

Native GCC/Clang tests undefine host HUGE_VAL after real headers and execute
actual CPG materialized expansions. Independently captured host value, exact
type and positive infinity establish agreement; absence of a huge_val external
symbol distinguishes the genuine compiler intrinsic from an invented helper.
Original/generated/copied/edited/reparsed/GREEN/FOLDED sources and an actual
edit to a finite value preserve observable changes. Existing guards still reject
unsupported enclosing structural folds around macro-bearing expressions; this
packet does not repair or relax that boundary.
A strtod overflow probe may use independently established errno facts but does
not implement libc math or establish a complete floating environment model.

## Recorded native evidence and pending gates

The dedicated command passed seven units and three integration cases:

```sh
mvn -o -B -Dtest=HugeValRuntimeTest -Dit.test=HugeValRuntimeIT -DargLine=-Xmx768m verify
```

Native evidence under target/huge-val-oracle records 90 GCC and 94 Clang header
hashes, linked libc2.41 and binary64 radix/precision/minimum/maximum exponent
2/53/-1021/1024. Probe source SHA256:
`7ccd519c1243ce062156fd17675c6131fca862152051b1cf86bd415b65bc6c33`.
Exact type/value agreement and absence of an external huge_val symbol establish
the selected compiler mechanism, not a universal libc contract.

Assignment/increment and bare function-name address/declaration/qualification
are rejected. However &__builtin_huge_val(), addressing the evaluated rvalue,
has no CPG diagnostic under the existing generic gap. Both strict native
compilers reject it; this native result is not counted as a CPG diagnostic.

Final expanded and separate crosscheck commands:

```sh
mvn -o -B -Dtest=HugeValRuntimeTest,ClockScaleRuntimeTest,WeofRuntimeTest,TypedConstantFoldingTest,JsonExportTest,PlatformProfileTest,CCompatibilityFamiliesTest,StandardLibraryCatalogueTest,LibraryCatalogueFamiliesTest,RemainingLibraryResolutionTest,RemainingLibraryOverloadsTest,LazyLibraryGraphTest,LibraryForwardClassResolutionTest,StreamLibraryResolutionTest,StrstreamLibraryTest,ScopedResolutionTest,MacroSourceTest,CppPreprocessorTest,MbCurMaxRuntimeTest,ErrnoRuntimeTest,StandardStreamsRuntimeTest,LibraryExternalObjectsTest,AssertionSourceTest,SharedSourceRequirementsTest,GeneratedNameBindingTest,ProfileTypeSemanticsTest,RuntimeIntegerMacrosTest,CppLiteralsTest,ExpandedExpressionDataFlowTest,ExceptionCleanupFlowTest,StatementExceptionExportTest,LibraryUnknownEffectsTest,CallSiteDataFlowTest,ObjectMemoryDataFlowTest,ReferenceBindingDataFlowTest,FlowTest,ObjectMemoryExportTest,ObjectMemoryValidationTest -Dit.test=HugeValRuntimeIT,ClockScaleRuntimeIT,WeofRuntimeIT,PackagedCliIT,MacroEditingIT,SharedHeaderGenerationIT,RuntimeIntegerMacrosIT,AssertionRuntimeIT,StandardStreamsRuntimeIT,StrstreamLibraryIT -DargLine=-Xmx768m verify
mvn -o -B -Dtest=StandardLibraryMacrosTest -DargLine=-Xmx768m test
```

The expanded command passed 432 units and 31 IT; the second command passed eight
additional units in 7.968 seconds, for 440 distinct focused units. Built JAR SHA256:
`452ba74c6a1de4422a86980ea8351c97748660c42885603388191828e06b128e`.

Independent frozen-source review approved all 14 production-file hashes,
passed seven new units, 41 regressions and three native integration cases, and
performed additional arithmetic/effect/capability probes. These repeated review
runs are not added to the distinct focused test count. Production remained
unchanged from the reviewed manifest, whose SHA256 is
`1af691f252ca3407d2d7542002434f6b665bba915efef6dabffb7310e7d2089d`.

Both existing return-zero fixtures have exactly 37 scalar changes each:
36 derived profile-hash replacements and one effective settings string adding
hugeVal={}. No structural or unrelated value changes are claimed. Default
profile identity:
`1e5bcf2de9a6e585fb2f4ac777dd216d9f57805557e382101b7bc505e553ca24`.
The empty capability remains unavailable.

Full Maven verification and exact-head CI remain pending, separately from the
completed focused commands and independent review.

## Inventory boundary

Verified bounded support brings totals to 94 bounded obligations/
87 names and eight open obligations/eight names. The remaining names are
SIG_DFL/SIG_ERR/SIG_IGN, offsetof, va_start/va_arg/va_end and setjmp.
#92 and #48 remain open. No complete compiler-intrinsic or C library
conformance follows from one explicit positive-infinity capability.

Исходный C90 допускает положительную бесконечность HUGE_VAL, но не требует
константной формы. Пакет выбирает узкую явную возможность компилятора для double,
без выдуманной функции libc, конечного значения BigDecimal или обещания полной
IEEE-арифметики. Прошли 432 unit-теста и 31 интеграционный тест; отдельная
проверка добавила восемь unit-тестов, итого 440 различных. Полный набор и CI
остаются отдельными ожидающими проверками.
