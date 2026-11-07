# C++03 #92: CLOCKS_PER_SEC and configurable clock_t

## Verified scope

The implemented model passed packaged acceptance (23 units and three native
cases) and final expanded verification (269 units and 28 integration tests).
The inventory now records the bounded target-fact implementation. Focused
verification is complete; full Maven and exact-head CI remain separate gates.
Previous packet reports retain their historical scope.

## Original edition contract

The public final ANSI/ISO 9899-1990 §7.12.1, printed page170/PDF page180, was
visually checked during preparation. Its CLOCKS_PER_SEC description is “which
is the number per second of the value returned by the clock function”. The
clock_t/time_t paragraph describes arithmetic types capable of representing
times. This original clause does not require the macro expression to have exact
clock_t type or to be a constant expression.

Public source: [ANSI/ISO 9899-1990 PDF](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf).
Source SHA256:
`26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
This fresh public C90 access is distinct from the private ISO2003 PDF, which
remains absent. Existing reviewed C++03 locators 18.7/2, table21 and17.4.4.2/2
retain their original coordinates and hashes; their source SHA256 is
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.

[WG14 DR281](https://www.open-std.org/jtc1/sc22/wg14/www/docs/dr_281.htm)
addresses later C99-era clock_t/constant-expression wording. It is not authority
to rewrite original C90 §7.12.1. Likewise the C89 draft's CLK_TCK spelling is
not a required macro in the final C90 inventory.

## Explicit coupled profile model

```json
{"clockScale":{"clockType":"long","expressionType":"long","value":"1000000"}}
```

The optional object contains exactly clockType, expressionType and a positive
strict-decimal value string. Missing or empty settings retain opaque clock_t
and unavailable CLOCKS_PER_SEC. Both types independently support int, unsigned
int, long or unsigned long. The value must fit expressionType; no requirement
forces it to fit clockType or makes the two types equal. No type/value is inferred
from cRuntime, sizeType, pointer width, JVM or the current host.

This is a deliberately bounded integral subset. C90 clock_t is arithmetic,
not universally integral; other arithmetic/expression forms remain unsupported.
The atomic settings object supplies the two independent type facts together.
Partial/unknown fields, wrong types, malformed values, zero/negative values and
expression-type overflow are rejected rather than repaired.

The selected macro is emitted as an exact positive literal with its type's
suffix, including L/U/UL even for values also representable by int. It supports
#if without a cast. That chosen capability does not claim every historically
permitted macro form was a preprocessing constant. The observed glibc cast form
fails #if; the CPG literal preserves selected value/type, not native spelling.

## Integration and verification scope

ClockScaleModel and PlatformProfile carry the explicit target facts. Catalogue
construction replaces only the trusted clock_t marker, retaining time_t, tm and
ordinary ctime declarations. ctime/time.h and mixed includes share the
configured canonical fundamental clock_t without broadening arbitrary typedef
merging. clock() retains its declared return type and opaque runtime behavior;
no body, constant return, timing resolution or wraparound model is introduced.

Actual supplied headers remain authoritative. Missing facts permit unused
headers and macro-presence checks, while expansion/use including #if diagnoses
unavailability. Profile serialization/copying, definition provenance, TU/profile
isolation, #undef and literal arithmetic/type behavior are covered by focused checks.

Committed regression paths:
- `src/test/java/com/kniazkov/cpg/analysis/ClockScaleRuntimeTest.java`.
- `src/test/java/com/kniazkov/cpg/e2e/ClockScaleRuntimeIT.java`.
- Existing StandardLibraryMacrosTest inventory crosscheck.

Native evidence is recorded under `target/clock-scale-oracle/`. Separate
GCC/Clang probes identify real headers/compiler/runtime, clock_t and macro
types/value. Materialized tests undefine the host macro after real includes.
Synthetic differing types are constant/type tests, not permission to call host
clock() through an incompatible ABI. Real clock() checks allow its unavailable
(clock_t)-1 result and avoid deterministic duration assertions. Actual graph
edits require semantic reanalysis under the existing editing contract.

Supporting preparation observed local glibc2.41, long clock_t and long macro
value1000000 on GCC14.2/Clang19.1.7; that is not a completed CPG acceptance test
or a universal glibc fact. Pinned implementation context:
[glibc2.41 bits/time.h](https://raw.githubusercontent.com/bminor/glibc/glibc-2.41/bits/time.h).

## Executed packaged acceptance and remaining gates

The packaged command passed 23 unit tests (nine CCompatibilityFamiliesTest,
eight StandardLibraryMacrosTest and six ClockScaleRuntimeTest) and all three
ClockScaleRuntimeIT cases, with zero failures, errors or skips in 1:16:

```sh
mvn -o -B -Dtest=ClockScaleRuntimeTest,CCompatibilityFamiliesTest,StandardLibraryMacrosTest -Dit.test=ClockScaleRuntimeIT -DargLine=-Xmx768m verify
```

Native evidence records 57 GCC and 60 Clang selected-header hashes, linked
glibc 2.41, exact long clock_t and long macro expression with value1000000.
Both actual host macros expand to ((__clock_t)1000000) and fail #if. CPG's
selected suffix literal is deliberately preprocessable. Probe source SHA256:
`199cee07a79449c9c51502f9d45a5acaf20ba061817f37d0c139d2d01751afc4`.
Synthetic boundary and unavailable/API/error cases passed; no synthetic target
is claimed ABI-compatible with the native clock function.

Final expanded verification passed 269 unit tests and 28 integration tests in
11:33, with zero failures, errors or skips. The earlier 23-unit/three-IT run is
not added to these counts because its cases are included in the expanded run.

```sh
mvn -o -B -Dtest=ClockScaleRuntimeTest,WeofRuntimeTest,StandardLibraryMacrosTest,TypedConstantFoldingTest,JsonExportTest,PlatformProfileTest,CCompatibilityFamiliesTest,StandardLibraryCatalogueTest,LibraryCatalogueFamiliesTest,RemainingLibraryResolutionTest,RemainingLibraryOverloadsTest,LazyLibraryGraphTest,LibraryForwardClassResolutionTest,StreamLibraryResolutionTest,StrstreamLibraryTest,ScopedResolutionTest,MacroSourceTest,CppPreprocessorTest,MbCurMaxRuntimeTest,ErrnoRuntimeTest,StandardStreamsRuntimeTest,LibraryExternalObjectsTest,AssertionSourceTest,SharedSourceRequirementsTest,GeneratedNameBindingTest,ProfileTypeSemanticsTest,RuntimeIntegerMacrosTest -Dit.test=ClockScaleRuntimeIT,WeofRuntimeIT,PackagedCliIT,MacroEditingIT,SharedHeaderGenerationIT,RuntimeIntegerMacrosIT,AssertionRuntimeIT,StandardStreamsRuntimeIT,StrstreamLibraryIT -DargLine=-Xmx768m verify
```

Built JAR SHA256:
`8b1556a60adfdf1b7e43a6dc200ef33f5b440a0813007c4024f5cde766f94f47`.
Both existing return-zero golden fixtures have exactly 37 scalar changes each:
36 derived profile-hash replacements and one effective settings string adding
clockScale={}. There are no structural changes. The new default identity is
`2794d5dac7ed883b7eca56c6286054b4af62c98d7c53d5e19c93bcbe42d6c33a`.
The empty object preserves default opaque clock_t/unavailable scale behavior.

Full Maven verification is running separately and is not yet complete.
Exact-head CI remains pending. Neither result is implied by focused success.

## Existing structural-folding boundary

The source-preservation guard rejects folding an enclosing comparison that
contains a macro expansion, such as `#define SCALE 1000000L` followed by
`if (SCALE != 1000000L)`, rather than silently flattening macro structure. This
is an existing intentional unsupported case, not a repaired production bug.
Production code remained unchanged during fixture correction. The accepted
fixture initializes scaleValue from the macro and compares/calculates through
that variable. Direct macro overload checks, real clock/conversion behavior,
actual edits, materialization and all-stage coverage are retained. This evidence
does not imply arbitrary structural folding around macros is supported.

The previous CCompatibilityFamiliesTest absentTargetMacroDefinitionsRemainUnresolved
method was replaced by absentClockScaleDiagnosesUseWhileProfileLimitsRemainAvailable:
missing clockScale now produces an explicit diagnostic rather than an unresolved
macro with no preprocessing diagnostic. The test retains unused-ctime and
INT_MAX/FLT_RADIX checks; ledger method locators follow the renamed test without
claiming broader historical runtime coverage.

## Inventory boundary

This bounded obligation brings the inventory to 93 implemented obligations/
86 names, with nine obligations/nine names open. Final expanded focused verification
has passed. Remaining names are
HUGE_VAL, SIG_DFL/SIG_ERR/SIG_IGN, offsetof, va_start/va_arg/va_end and setjmp.
#92 and #48 remain open.

Исходный C90 не требует точного совпадения типа CLOCKS_PER_SEC с clock_t.
Модель задаёт оба типа независимо и поддерживает ограниченный целочисленный
набор. Выбранный литерал работает в #if, но это не универсальная гарантия всех
исторических форм макроса. Расширенные проверки пакета прошли: 269 модульных
и 28 интеграционных тестов. Полный Maven-набор и CI учитываются отдельно.
