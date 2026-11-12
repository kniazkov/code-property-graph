# Mandatory library macros: #92 convergence acceptance

## Scope and decision rule

Baseline: master `d2dfb88a6556393e08f2b1b4b1604901826c2d02` after PR #144.
Current focused tests, full clean Maven verification and independent review passed
as recorded below. Exact-head mandatory CI and post-merge verification remain pending.
The inventory has **102 header/name obligations, 95 names: 54 profile definitions,
33 target facts, nine runtime bindings, one compiler intrinsic and five partial/open**.
These classifications are unchanged. Definition presence is not semantic proof;
97 classified implementations do not mean 97 universally proved contracts.

[Issue #92](https://github.com/kniazkov/code-property-graph/issues/92) requires
support **or an open remaining-work link** for every inventoried macro, CPG-owned
expansions, explicit target/ABI boundaries and reproducible acceptance. It does
not require a complete C library, every ABI or complete C++03. #48 stays open.
The five partial obligations remain partial even if this coordination issue is
later closed with separately tracked residual work.

## Criterion-by-criterion evidence

| #92 criterion | Current concrete evidence | Limit / new gate |
|---|---|---|
| Exact original-edition inventory | 102 rows below; inventory validators and per-header actual definition comparison P | Name/locator accounting is not behavior proof; no C99/C++11 additions |
| Explicit target NULL/integer/floating limits | I/F/N/W; two configured profiles and unknown-format rejection | ALL54 independent values and NUM48 actual replacements passed focused acceptance; no universal numeric ABI claim |
| assert and NDEBUG reinclusion, disabled non-evaluation | A; `AssertionMacrosTest#reinclusionSelectsPresenceAndDoesNotReactUntilNextInclude`; native A and failure test | ASSERT18 passed focused acceptance, including actual replacements; not inferred from host assert |
| Functional/target macros retain macro nature and explicit ABI | R/E/M/S/D/H/C/WE plus partial O/V/J | No fabricated function substitution for setjmp; no universal runtime/ABI claim |
| Every obligation supported or separately tracked | 97 classified implementations, five OPEN rows | Open residuals #145 offsetof, #146 cstdarg and #147 setjmp; five obligations stay partial |
| #if INT_MAX and ordinary expressions, two profiles, old headers | I/N/W/R and `LibraryMacrosIT#profilesProvenanceApiCliCopyAndEditedGeneration` | Historical original/generated paths retain host headers; new NUM48 replacement oracle passed |
| TU boundaries and physical-header authority | `StandardLibraryMacrosTest#headerVisibilityRepeatedInclusionAndTranslationUnitsAreIndependent`, `#actualSuppliedHeadersTakePrecedenceOverTheBuiltinMacroModel`; R partial-facts isolation | Per-family provenance/visibility tests supplement these; not one global runtime proof |
| API/CLI/JSON, copy/edit/reanalysis/generation | Native method map below; error cases block generated output | New limits/assert artifact counts and hashes audited; independent review passed |
| Fresh executable evidence and exact assertions | This method/102-row map; packet reports are historical evidence | Fresh JAR SHA and full verify recorded below; exact-head CI/post-merge pending |
| RU/EN, matrix and mutable ledger | This report and Russian counterpart; coordination packet owns other updates | No immutable hash/classification upgrades from test presence alone |

## Evidence keys: exact current methods and assertions

Every method below was inspected or its named assertion checked in current source.
This table establishes what a test checks, not that it passed in this acceptance run.
P applies to available classified definitions globally; partial V/J have separate
capability tests and must not be inferred solely from P.

### P

[`StandardLibraryMacrosTest#implementedInventoryObligationsMatchActualHeaderDefinitions`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): Actual available/unavailable definition-name sets, header aliases and selected capabilities; NOT a semantic value oracle.

### I

[`StandardLibraryMacrosTest#integerLimitsWorkInDirectivesAndOrdinaryExpressionsForTwoProfiles`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): Exact printed INT_MAX/LONG_MAX/ULONG_MAX/CHAR_MIN/CHAR_MAX/MB_LEN_MAX values across two profiles; INT_MIN<0 and UINT_MAX>INT_MAX in #if. Other integer names are presence-only here.

### F

[`StandardLibraryMacrosTest#floatMacrosUseExplicitFormatsNotStorageWidthsOrJavaFloatingPoint`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): FLT_RADIX=2; printed mantissas24/53/113 or64 and rounding1 or0; FLT_EPSILON token1.1920928955078125e-7F. All29 names checked, but no independent expected values for the other constants. Unknown required format rejected.

### N

[`StandardLibraryMacrosTest#headerVisibilityRepeatedInclusionAndTranslationUnitsAreIndependent`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): All seven NULL providers and old spellings expand to selected0L; before-include absence and undef/reinclude restoration.

### W

[`StandardLibraryMacrosTest#wcharLimitsUseWcharIntegralPromotionRatherThanUnderlyingIntegerRank`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): Signed long32 wchar gives (-2147483647-1),2147483647; unsigned long32 gives0U,4294967295U, testing promotion rather than storage rank.

### R

[`RuntimeIntegerMacrosTest#exactThirtyNamesBothAliasesAndTargetValuesInConditionalsAndExpressions`](../src/test/java/com/kniazkov/cpg/parser/RuntimeIntegerMacrosTest.java): All30 explicitly configured int facts checked in #if against selected manifest values, ordinary expansion and header aliases; values are configuration, not universal libc constants.

### RV

[`RuntimeIntegerMacrosTest#validationSeparatesHistoricalConstraintsFromBoundedIntPolicy`](../src/test/java/com/kniazkov/cpg/parser/RuntimeIntegerMacrosTest.java): Reject zero errno codes, nonpositive signals, nonnegativeEOF, undersized BUFSIZ/FOPEN_MAX/TMP_MAX/RAND_MAX, malformed/out-of-range facts and forbidden duplicates.

### A

[`AssertionMacrosTest#disabledArgumentsAreNeitherPrescannedNorSemanticallyRetained`](../src/test/java/com/kniazkov/cpg/parser/AssertionMacrosTest.java): Disabled assert argument is not prescanned or retained; reinclusion method separately checks NDEBUG presence changes only at next include.

### E

[`ErrnoRuntimeTest#bothHeadersExposeWritableIntLvalueThroughRealGlobalLibraryHelper`](../src/test/java/com/kniazkov/cpg/analysis/ErrnoRuntimeTest.java): Both headers bind __errno_location; dereferences have int type and unknown value; assignment/increment/reference/address uses resolve, no invented extern int errno.

### M

[`MbCurMaxRuntimeTest#queryIsUnknownRvalueAndSizeofDoesNotCall`](../src/test/java/com/kniazkov/cpg/analysis/MbCurMaxRuntimeTest.java): Locale-dependent helper results stay unknown rvalues; sizeof has a value but contributes no evaluated helper data-flow effects.

### S

[`StandardStreamsRuntimeTest#bothHeadersExposeGlobalMutableOpaquePointersAndPreserveOrdinaryFunctions`](../src/test/java/com/kniazkov/cpg/analysis/StandardStreamsRuntimeTest.java): Both headers expose mutable global opaque FILE pointers; ordinary functions remain resolvable; no fabricated FILE layout.

### D

[`SignalDispositionsRuntimeTest#castsHaveExactHandlerTypeWithoutFabricatedCalls`](../src/test/java/com/kniazkov/cpg/analysis/SignalDispositionsRuntimeTest.java): All3 sentinels have pointer(function(int)->void) rvalue type and unknown pointer value; only written signal/raise calls exist.

### H

[`HugeValRuntimeTest#floatingArithmeticAndConversionsStayConservativelyUnknown`](../src/test/java/com/kniazkov/cpg/analysis/HugeValRuntimeTest.java): HUGE_VAL arithmetic/conversion/comparison remains unknown, sizeof gives8 under selected profile; no fabricated ordinary call.

### C

[`ClockScaleRuntimeTest#exactTypesAndRenderFormsUseIndependentBoundaryOracles`](../src/test/java/com/kniazkov/cpg/analysis/ClockScaleRuntimeTest.java): Independent explicit integral type/value/rendering cases, including suffix and representability boundaries; clock_t and macro expression type are separate.

### WE

[`WeofRuntimeTest#exactTypesAndRenderFormsUseIndependentBoundaryOracles`](../src/test/java/com/kniazkov/cpg/analysis/WeofRuntimeTest.java): Signed minima and unsigned maxima have exact renderings/types; #if and ordinary unsigned wrapping are checked; explicit nonnegative-character envelope remains bounded.

### O

[`OffsetofRuntimeTest#structuredOperandsHaveExactSizeTypeBindingsAndTrueIntegralConstantContexts`](../src/test/java/com/kniazkov/cpg/analysis/OffsetofRuntimeTest.java): Structured type/designator operands, exact selected size_t and integral-constant contexts; bounded layout, not universal POD coverage.

### V

[`VarargsRuntimeTest#boundOperandsTypedResultsAndUnknownVaListLayoutAcrossHeadersAndCopies`](../src/test/java/com/kniazkov/cpg/analysis/VarargsRuntimeTest.java): Bound list/last/type operands and typed results across aliases/copies; opaque va_list layout and runtime result remain unknown.

### J

[`SetjmpRuntimeTest#c90PermittedControllingAndDiscardedContextsRemainUnknown`](../src/test/java/com/kniazkov/cpg/analysis/SetjmpRuntimeTest.java): Accepted original C90 controlling/discarded contexts retain unknown repeated-return result; no invented saved-state constant.

## Native/API/CLI method map

- **A**: [`AssertionRuntimeIT#successfulAssertionsResolveCopyEditAndRunThroughCliAndMaterializedExpansion`](../src/test/java/com/kniazkov/cpg/e2e/AssertionRuntimeIT.java).
- **E**: [`ErrnoRuntimeIT#sharedRuntimeStorageSurvivesApiCliCopyMaterializationAndRhsEditing`](../src/test/java/com/kniazkov/cpg/e2e/ErrnoRuntimeIT.java).
- **M**: [`MbCurMaxRuntimeIT#liveLocaleQuerySurvivesApiCliCopyMaterializationAndStringEditing`](../src/test/java/com/kniazkov/cpg/e2e/MbCurMaxRuntimeIT.java).
- **S**: [`StandardStreamsRuntimeIT#liveStreamsSurviveApiCliCopyMaterializationAndPayloadEditing`](../src/test/java/com/kniazkov/cpg/e2e/StandardStreamsRuntimeIT.java).
- **D**: [`SignalDispositionsRuntimeIT#nativeDispositionsSurviveApiCliCopyEditsReparsingAndTransforms`](../src/test/java/com/kniazkov/cpg/e2e/SignalDispositionsRuntimeIT.java).
- **H**: [`HugeValRuntimeIT#nativeInfinitySurvivesApiCliCopyEditsReparsingAndTransforms`](../src/test/java/com/kniazkov/cpg/e2e/HugeValRuntimeIT.java).
- **C**: [`ClockScaleRuntimeIT#independentTypesAndAllPositiveSuffixBoundariesUseActualCpgConstants`](../src/test/java/com/kniazkov/cpg/e2e/ClockScaleRuntimeIT.java).
- **WE**: [`WeofRuntimeIT#independentSignedAndUnsignedConstantsUseActualCpgReplacementsWithoutNativeAbiClaims`](../src/test/java/com/kniazkov/cpg/e2e/WeofRuntimeIT.java).
- **R**: [`RuntimeIntegerMacrosIT#actualTargetAndIndependentSyntheticFactsThroughApiCliCopyEditsAndNativeOracles`](../src/test/java/com/kniazkov/cpg/e2e/RuntimeIntegerMacrosIT.java).
- **O**: [`OffsetofRuntimeIT#realBuiltinSurvivesApiCliCopyEditsReparseGreenAndFolded`](../src/test/java/com/kniazkov/cpg/e2e/OffsetofRuntimeIT.java).
- **V**: [`VarargsRuntimeIT#apiCliCopiesTransformsEditsAndMaterializedMacrosMatchBothCompilers`](../src/test/java/com/kniazkov/cpg/e2e/VarargsRuntimeIT.java).
- **J**: [`SetjmpRuntimeIT#apiCliNativeReplaysAndRealEditsExecuteReturnsTwiceAtOptimization`](../src/test/java/com/kniazkov/cpg/e2e/SetjmpRuntimeIT.java).

These methods exercise bounded original/generated/materialized cases with GCC/Clang. Their existence is not a fresh PASS. The new LibraryMacrosIT numeric materialization and durable assert/limits artifacts passed the focused run recorded below. Other listed historical family methods were not all rerun by that focused selection. Synthetic integer-value tests do not certify ILP32 native object layout or host libc numbers.

## New acceptance additions: focused PASS

**ALL54**: [`StandardLibraryMacrosTest#allIntegerAndFloatingDefinitionsMatchIndependentTwoProfileFacts`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java)
checks all 16 integer and 29 floating names, WCHAR_MIN/MAX and all seven NULL
obligations against independent two-profile expectations. Integer replacements
are exact strings. Floating MAX/EPSILON/MIN use exact binary BigDecimal values
or exactly the specified 36-significant-digit HALF_EVEN rounding; other floating
facts have fixed expected integers. This is not native binary128 conformance.
ALL54 selects default LP64/binary128 and a 32-bit-long, unsigned-char, binary80, rounds=0, NULL=0L numeric profile; the latter retains default pointer width and is not a full ILP32 layout profile. NUM48 separately selects 32-bit pointers and long for its synthetic ILP32 numeric-expression route.
Every PD row below explicitly maps to ALL54 in addition to its historical key.

**NUM48**: [`LibraryMacrosIT#actualMaterializedIntegerLimitsHaveIndependentLp64AndIlp32NumericOracles`](../src/test/java/com/kniazkov/cpg/e2e/LibraryMacrosIT.java)
passed 48 native executions using independent profile expectations. This tests
constant expressions, not native ILP32 object layout. **ASSERT18**: the existing
AssertionRuntimeIT success/failure methods passed 18 durable native executions,
including observed counts 1/1/2 and edited 5/5/6, plus six abort cases. Focused
success does not replace full verification, independent review or exact-head CI.

The original assert contract's adaptation before DR107 and nonreturning CFG
remain separate gaps. Do not close them from successful runtime assertions;
the library-contract evidence framework in #100 is relevant, but its body excludes
macro implementation and does not explicitly assign DR107. #98 enumerates other
specific DRs, not DR107; #99 covers language/appendices, not this library-source
question. Retain the source ambiguity under overall #48 without declaring a new
production defect or claiming an existing issue already resolves it. Existing refusal
to replay an unsafe condition edit remains a deliberate boundary.

## All 102 obligations

PD=profile_definition, TF=target_fact, RB=runtime_binding, CI=compiler_intrinsic. OPEN remains partial/open. The assertion column distinguishes numerical checks from presence. Source locators are exact inventory references, not an assertion that every referenced paragraph was freshly reread here.

| # | Header / old C spelling | Macro | State | Evidence / assertion boundary | Original locators |
|---:|---|---|---|---|---|
| 1 | `cassert` / `assert.h` | `assert` | RB | A: bounded semantics / ограниченная семантика | cpp03:19.2:p2, cpp03:19.2:p3, cpp03:17.4.2.1:p2; C90 7.2, 7.2.1.1 |
| 2 | `cerrno` / `errno.h` | `EDOM` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:19.3:table26, cpp03:19.3:p2; C90 7.1.4 |
| 3 | `cerrno` / `errno.h` | `ERANGE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:19.3:table26, cpp03:19.3:p2; C90 7.1.4 |
| 4 | `cerrno` / `errno.h` | `EILSEQ` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:19.3:p2, cpp03:17.4.1.2:p4; C90 Amendment1 7.1.4 |
| 5 | `cerrno` / `errno.h` | `errno` | RB | E: bounded semantics / ограниченная семантика | cpp03:19.3:table26, cpp03:19.3:p2; C90 7.1.4 |
| 6 | `climits` / `limits.h` | `CHAR_BIT` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 7 | `climits` / `limits.h` | `SCHAR_MIN` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 8 | `climits` / `limits.h` | `SCHAR_MAX` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 9 | `climits` / `limits.h` | `UCHAR_MAX` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 10 | `climits` / `limits.h` | `CHAR_MIN` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 11 | `climits` / `limits.h` | `CHAR_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 12 | `climits` / `limits.h` | `MB_LEN_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 13 | `climits` / `limits.h` | `SHRT_MIN` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 14 | `climits` / `limits.h` | `SHRT_MAX` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 15 | `climits` / `limits.h` | `USHRT_MAX` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 16 | `climits` / `limits.h` | `INT_MIN` | PD | [ALL54 focused PASS][all54] + I: predicate / условие | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 17 | `climits` / `limits.h` | `INT_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 18 | `climits` / `limits.h` | `UINT_MAX` | PD | [ALL54 focused PASS][all54] + I: predicate / условие | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 19 | `climits` / `limits.h` | `LONG_MIN` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 20 | `climits` / `limits.h` | `LONG_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 21 | `climits` / `limits.h` | `ULONG_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 22 | `cfloat` / `float.h` | `FLT_RADIX` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 23 | `cfloat` / `float.h` | `FLT_ROUNDS` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 24 | `cfloat` / `float.h` | `FLT_MANT_DIG` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 25 | `cfloat` / `float.h` | `FLT_DIG` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 26 | `cfloat` / `float.h` | `FLT_MIN_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 27 | `cfloat` / `float.h` | `FLT_MIN_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 28 | `cfloat` / `float.h` | `FLT_MAX_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 29 | `cfloat` / `float.h` | `FLT_MAX_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 30 | `cfloat` / `float.h` | `FLT_MAX` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 31 | `cfloat` / `float.h` | `FLT_EPSILON` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 32 | `cfloat` / `float.h` | `FLT_MIN` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 33 | `cfloat` / `float.h` | `DBL_MANT_DIG` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 34 | `cfloat` / `float.h` | `DBL_DIG` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 35 | `cfloat` / `float.h` | `DBL_MIN_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 36 | `cfloat` / `float.h` | `DBL_MIN_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 37 | `cfloat` / `float.h` | `DBL_MAX_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 38 | `cfloat` / `float.h` | `DBL_MAX_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 39 | `cfloat` / `float.h` | `DBL_MAX` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 40 | `cfloat` / `float.h` | `DBL_EPSILON` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 41 | `cfloat` / `float.h` | `DBL_MIN` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 42 | `cfloat` / `float.h` | `LDBL_MANT_DIG` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 43 | `cfloat` / `float.h` | `LDBL_DIG` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 44 | `cfloat` / `float.h` | `LDBL_MIN_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 45 | `cfloat` / `float.h` | `LDBL_MIN_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 46 | `cfloat` / `float.h` | `LDBL_MAX_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 47 | `cfloat` / `float.h` | `LDBL_MAX_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 48 | `cfloat` / `float.h` | `LDBL_MAX` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 49 | `cfloat` / `float.h` | `LDBL_EPSILON` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 50 | `cfloat` / `float.h` | `LDBL_MIN` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 51 | `clocale` / `locale.h` | `LC_ALL` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 52 | `clocale` / `locale.h` | `LC_COLLATE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 53 | `clocale` / `locale.h` | `LC_CTYPE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 54 | `clocale` / `locale.h` | `LC_MONETARY` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 55 | `clocale` / `locale.h` | `LC_NUMERIC` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 56 | `clocale` / `locale.h` | `LC_TIME` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 57 | `cmath` / `math.h` | `HUGE_VAL` | CI | H: bounded semantics / ограниченная семантика | cpp03:26.5:p1, cpp03:26.5:p2; C90 7.5 |
| 58 | `csetjmp` / `setjmp.h` | `setjmp` | OPEN | J: partial; residual SETJMP | cpp03:18.7:table20, cpp03:18.7:p2, cpp03:18.7:p4; C90 7.6, 7.6.1.1 |
| 59 | `csignal` / `signal.h` | `SIGABRT` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 60 | `csignal` / `signal.h` | `SIGFPE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 61 | `csignal` / `signal.h` | `SIGILL` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 62 | `csignal` / `signal.h` | `SIGINT` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 63 | `csignal` / `signal.h` | `SIGSEGV` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 64 | `csignal` / `signal.h` | `SIGTERM` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 65 | `csignal` / `signal.h` | `SIG_DFL` | RB | D: bounded semantics / ограниченная семантика | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 66 | `csignal` / `signal.h` | `SIG_ERR` | RB | D: bounded semantics / ограниченная семантика | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 67 | `csignal` / `signal.h` | `SIG_IGN` | RB | D: bounded semantics / ограниченная семантика | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 68 | `cstdarg` / `stdarg.h` | `va_start` | OPEN | V: partial; residual VARARGS | cpp03:18.7:table19, cpp03:18.7:p2, cpp03:18.7:p3; C90 7.8, 7.8.1 |
| 69 | `cstdarg` / `stdarg.h` | `va_arg` | OPEN | V: partial; residual VARARGS | cpp03:18.7:table19, cpp03:18.7:p2, cpp03:18.7:p3; C90 7.8, 7.8.1 |
| 70 | `cstdarg` / `stdarg.h` | `va_end` | OPEN | V: partial; residual VARARGS | cpp03:18.7:table19, cpp03:18.7:p2, cpp03:18.7:p3; C90 7.8, 7.8.1 |
| 71 | `cstddef` / `stddef.h` | `offsetof` | OPEN | O: partial; residual OFFSET | cpp03:18.1:table15, cpp03:18.1:p3, cpp03:18.1:p5; C90 7.1.6 |
| 72 | `cstdio` / `stdio.h` | `_IOFBF` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 73 | `cstdio` / `stdio.h` | `_IOLBF` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 74 | `cstdio` / `stdio.h` | `_IONBF` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 75 | `cstdio` / `stdio.h` | `BUFSIZ` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 76 | `cstdio` / `stdio.h` | `EOF` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 77 | `cstdio` / `stdio.h` | `FOPEN_MAX` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 78 | `cstdio` / `stdio.h` | `FILENAME_MAX` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 79 | `cstdio` / `stdio.h` | `L_tmpnam` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 80 | `cstdio` / `stdio.h` | `SEEK_CUR` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 81 | `cstdio` / `stdio.h` | `SEEK_END` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 82 | `cstdio` / `stdio.h` | `SEEK_SET` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 83 | `cstdio` / `stdio.h` | `TMP_MAX` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 84 | `cstdio` / `stdio.h` | `stdin` | RB | S: bounded semantics / ограниченная семантика | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 85 | `cstdio` / `stdio.h` | `stdout` | RB | S: bounded semantics / ограниченная семантика | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 86 | `cstdio` / `stdio.h` | `stderr` | RB | S: bounded semantics / ограниченная семантика | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 87 | `cstdlib` / `stdlib.h` | `EXIT_FAILURE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.3:table18, cpp03:18.3:p2; C90 7.10 |
| 88 | `cstdlib` / `stdlib.h` | `EXIT_SUCCESS` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.3:table18, cpp03:18.3:p2; C90 7.10 |
| 89 | `cstdlib` / `stdlib.h` | `RAND_MAX` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:26.5:p1, cpp03:26.5:p2; C90 7.10, 7.10.2.1 |
| 90 | `cstdlib` / `stdlib.h` | `MB_CUR_MAX` | RB | M: bounded semantics / ограниченная семантика | cpp03:21.4:table49, cpp03:21.4:p2; C90 7.10 |
| 91 | `ctime` / `time.h` | `CLOCKS_PER_SEC` | TF | C: bounded semantics / ограниченная семантика | cpp03:18.7:table21, cpp03:18.7:p2; C90 7.12.1 |
| 92 | `cwchar` / `wchar.h` | `WCHAR_MIN` | PD | [ALL54 focused PASS][all54] + W: exact promoted tokens / точные продвинутые токены | cpp03:21.4:table48, cpp03:21.4:p2; C90 Amendment1 7.16.1 |
| 93 | `cwchar` / `wchar.h` | `WCHAR_MAX` | PD | [ALL54 focused PASS][all54] + W: exact promoted tokens / точные продвинутые токены | cpp03:21.4:table48, cpp03:21.4:p2; C90 Amendment1 7.16.1 |
| 94 | `cwchar` / `wchar.h` | `WEOF` | TF | WE: bounded semantics / ограниченная семантика | cpp03:21.4:table48, cpp03:21.4:p2; C90 Amendment1 7.16.1 |
| 95 | `cwctype` / `wctype.h` | `WEOF` | TF | WE: bounded semantics / ограниченная семантика | cpp03:21.4:table46, cpp03:21.4:p2; C90 Amendment1 7.15.1 |
| 96 | `cstddef` / `stddef.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:18.1:table15, cpp03:18.1:p4; C90 7.1.6 |
| 97 | `cstdio` / `stdio.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:27.8.2:table94, cpp03:18.1:p4; C90 7.9.1 |
| 98 | `cstdlib` / `stdlib.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:18.3:p2, cpp03:18.1:p4; C90 7.10 |
| 99 | `cstring` / `string.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:21.4:table47, cpp03:18.1:p4; C90 7.11.1 |
| 100 | `ctime` / `time.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:20.5:p1, cpp03:18.1:p4; C90 7.12.1 |
| 101 | `clocale` / `locale.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:22.3:table62, cpp03:18.1:p4; C90 7.4 |
| 102 | `cwchar` / `wchar.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:21.4:table48, cpp03:18.1:p4; C90 Amendment1 7.16.1 |

## Assertion precision: contract versus analysis boundary

Original C90 §7.2.1.1, printed101/PDF111, has the `assert(int expression)`
synopsis and zero-comparison behavior. Original C++03 §19.2/3,
printed358/PDF386, inherits that header; §4.12/1, printed63/PDF91, makes
conversion of an integer zero false and every nonzero integer true. Consequently
the selected `static_cast<bool>(expression)` replacement has the required truth
behavior for the tested integer operands, including `assert(++count)`, without
silently importing DR107. The broader C++ operand domain remains a source/
adaptation uncertainty, not a reproduced mandatory-contract failure.

Nonreturning CFG modeling is a known conservative precision limit, not evidence
of incorrect native termination. The selected real `::__assert_fail` binding
aborts. `AssertionRuntimeIT#failureDiagnosesRawExpressionPresumedFileLineAndTerminates`
checks nonzero termination, raw `ZERO`, presumed `assertion-input.cpp:73`, and
absence of `AFTER_ASSERT`, destructor `UNWOUND` and `ATEXIT`. CPG does not infer
a terminal CFG fact from the helper; no precise abort-flow proof is claimed.

Condition-only graph edits are explicitly refused when re-stringization would
be required. The success/failure integration methods assert this generation
refusal, preventing stale diagnostic text. Source-argument editing/reparse and
unrelated graph edits remain supported. This is a declared editing boundary,
not silent misgeneration or an asserted production defect.

## Residuals and source integrity

- **OFFSET — [#145](https://github.com/kniazkov/code-property-graph/issues/145):** actual selected-layout/type/designator limits; not a demand for every ABI. Existing v1/v2/v3 and template prerequisites remain bounded. Link exact separate template defects rather than duplicate #94/#104.
- **VARARGS — [#146](https://github.com/kniazkov/code-property-graph/issues/146):** one family for va_start/va_arg/va_end: direct-local storage and supported retrieval forms, forwarding/lifecycle boundaries. Not universal argument-count/UB proof.
- **SETJMP — [#147](https://github.com/kniazkov/code-property-graph/issues/147):** selected direct-local buffer, escaping/nonlocal storage and conservative nonlocal-flow boundaries. Not ordinary exception specification work (#96).
- #100 explicitly excludes macro implementation; #99 is language-evidence coverage; #97 is specific IFNDR/UB oracle correction. None silently replaces these residual families. #48 stays open.

Original user-supplied ISO/IEC14882:2003 SHA256:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
Original C90 PDF SHA256:
`26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
Freshly available originals supersede historical reports saying the PDF was absent,
but do not retroactively turn their partial evidence into complete source review.
Original §18.1/3,5 (printed333–334/PDF361–362), §9.6/1–3
(printed163/PDF191) and C90 §7.1.6 (printed98/PDF108) were reviewed in recent
bounded packets. Fresh original reads for this packet additionally covered §18.2.2 tables16/17,
printed341/PDF369; §19.2/2–3, printed358/PDF386; and C90 §7.2/7.2.1.1,
printed101/PDF111. Other row locators retain the source-reviewed ledger basis.
Amendment1 obligations EILSEQ/WEOF must not be removed because absent from C90;
full Amendment1 rereview is not claimed. Existing publisher-preview boundaries
are documented in the WEOF/runtime-integer reports. No private normative text or
source hashes/classifications are rewritten here.

## Execution status

Focused command (run 2 PASS; run 1 harness failure recorded below):

```sh
mvn -Dtest=StandardLibraryMacrosTest,AssertionMacrosTest -Dit.test=LibraryMacrosIT,AssertionRuntimeIT verify
```

The 15 focused unit tests include the new ALL54 method. The second focused run
passed **15 unit tests and six integration tests** in **36.640 seconds**, with
zero failures/errors/skips.

Full clean verification subsequently **PASSED: 1,645 unit tests and 167 integration
tests**, zero failures/errors/skips, in **54 minutes 34 seconds**:

```sh
mvn -B -ntp -s "$CPG_MAVEN_SETTINGS" clean verify
```

Fresh local JAR SHA-256:
`47a1483bea93d8042e1f3d0de95fbe5ce02273b321266bc27e57495dc44eb196`.
Independent review approved source tree `102e95d25a3842bb52f6264235be8b0cdbce897d`. GCC and Clang catalogue checks, corpus-report checking (42 scenarios plus two harness checks), strict private JavaDoc and the full demo also passed. Exact-head mandatory CI and post-merge master verification remain pending; local results do not replace those gates.

The first focused acceptance attempt failed after **32.207 seconds** because the
new test harness required16 macro expansions even for transformed source where
safe edits may legitimately replace some macros. The harness now applies that
count to original input only and records the actual expansion names in metadata.
This is a test-harness correction, not a production fix. The corrected second attempt passed as recorded above; the first remains a
failed harness run, not a production fix or an aggregate PASS.

Artifact audit: `target/library-macros-oracle/` has 48 execution records,
24 per compiler, and 240 verified referenced hashes. There are 32 materialized
routes, including 16 synthetic-profile materialized routes; eight original-input
proof cases retain the exact 16-expansion requirement. Transformed routes record
their actual remaining macro names rather than imposing a stale count.
`target/assertion-runtime-oracle/` has 18 records, nine per compiler, and 36
verified referenced hashes: eight count outputs 1/1/2, four edited 5/5/6 and six
abort outcomes. Both preserved focused artifacts and freshly regenerated full-suite artifacts were independently audited: 66 records and 276 referenced hashes each. These native record counts are distinct from the 1645 unit and 167 integration test counts.

[all54]: ../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java
