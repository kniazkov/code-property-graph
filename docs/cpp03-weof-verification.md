# C++03 #92: WEOF and configurable wint_t

## Verified packet scope

This report records the implemented bounded model and verified WEOF acceptance.
Focused implementation and independent boundary evidence support the two
bounded inventory entries. All four WEOF native cases and the expanded focused regressions passed;
full-suite and exact-head CI completion are not claimed. Previous packet verification reports remain historical.

## Historical contract and source boundaries

WEOF is a constant expression of wint_t type, distinct from every member of the
extended character set. wint_t is an integral type unchanged by default argument
promotions and able to represent extended-character values and a noncharacter
value. WEOF may differ from EOF and need not be negative; a signed -1 is permitted.
Neither universal unsigned-int typing nor equality to EOF is implied.

The public [Amendment 1 preview](https://cdn.standards.iteh.ai/samples/23909/3713b553291741f7aa531e8de6b28bfe/ISO-IEC-9899-1990-Amd-1-1995.pdf)
provides section 7.15.1, printed pages 7–8 (zero-indexed PDF pages 10–11),
including footnotes 11–12. Its preview ends before 7.16.1. Both header obligations
are recorded in the existing source-reviewed C++03 ledger: 21.4/2, tables46/48.
Reviewed 17.4.4.2/2 supplies the integral object-macro #if requirement. The private
original ISO2003 PDF is absent; no fresh private-source read is claimed.
Original source hashes, paragraph hashes and coordinates are unchanged.
The existing source SHA256 is
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.

## Explicit target model

The optional profile object is:

```json
{"wideInteger":{"wintType":"unsigned int","weof":"4294967295","characterDomain":"nonnegative-wchar"}}
```

A missing model or empty object keeps WEOF unavailable and wint_t opaque.
No facts are inferred from cRuntime, wcharType, JVM properties or host headers.
The bounded supported type set is int, unsigned int, long and unsigned long;
all are unchanged by default argument promotions. Type and sentinel are checked
independently of wchar_t. Decimal strings carry exact values without arbitrary
replacement source code.

The nonnegative-wchar policy requires wint_t to represent the entire nonnegative
range of target wchar_t and WEOF to lie outside that envelope. The caller asserts
that the actual character repertoire is nonnegative. The envelope is deliberately
stronger than ISO's exclusion from valid extended characters: it does not claim
that every nonnegative wchar_t value is a character, and rejects some otherwise
conforming targets. Signed -1 remains permitted even when wchar_t can represent
-1. Unsupported types/domains, malformed or partial models, out-of-range values,
insufficient wint_t range and sentinel overlap are rejected.

The implementation uses build/WideIntegerModel.java, profile serialization
and copying, catalogue wint_t substitution and preprocessor WEOF definitions.
Both wide header families retain ordinary declarations and share the
configured canonical wint_t, including char_traits<wchar_t>::int_type. Actual
supplied headers remain authoritative. Missing facts do not become a guessed
constant, and unavailable use/#if diagnostics are separate from unused includes
or macro-presence checks.

## Executed focused and native evidence

Committed regressions:
- `src/test/java/com/kniazkov/cpg/analysis/WeofRuntimeTest.java`.
- `src/test/java/com/kniazkov/cpg/e2e/WeofRuntimeIT.java`.
- Existing `StandardLibraryMacrosTest.java` inventory crosscheck.

Verified WEOF coverage includes all four header spellings and mixed include orders,
exact type/value and #if behavior, independent signed/unsigned/long boundaries,
canonical wide signatures and char_traits, unavailable diagnostics, provenance,
profile/TU isolation and physical-header precedence. Copying, edits/reparsing,
API/CLI and GREEN/FOLDED are checked against actual materialized expressions.
Host WEOF is undefined in materialized native probes.

Native deployment evidence is recorded under `target/weof-runtime-oracle/`.
The independent native probe records glibc headers/runtime 2.41, exact
unsigned-int wint_t/WEOF/char_traits int_type, WEOF=4294967295 and distinct
32-bit signed wchar_t. Selected-header hashes cover 100 GCC and 104 Clang files.
Probe source SHA256:
`d434f8ef51a50d4e3a14dc4a5121fccbca980dcb18b73d28e9cfe535cd2cd009`.
These local facts do not certify every glibc target. Synthetic profiles exercise
constants/types only, not host wide-library calls through mismatched types.

All four WeofRuntimeIT cases passed in 277.2 seconds. They cover eight header
arrangements, actual edits/reparsing, GREEN/FOLDED, materialized CPG expansions
and seven independent boundary forms under GCC and Clang. The expanded unit
phase passed 255 tests; the complete focused result is recorded below.

## Confirmed independent review and generic prerequisite

Independent review passed six WEOF and ten typed-folding unit tests, 40 extra
alias/profile/header probes and 14 native boundary checks. The latter are seven
cases under two compilers, not 14 additional committed tests. Full runtime
fixture GREEN/FOLDED source emission was also reviewed; expanded acceptance
subsequently passed.

The generic Source.integerLiteral prerequisite fixes a C++ nonminimum negative
rendering mismatch: emitted -1 did not replay identically to canonical (-(1)).
The fix emits canonical unary form without weakening the preservation guard;
signed-minimum and Java rendering paths remain unchanged. Permanent regression
TypedConstantFoldingTest.synthesizedNegativeLiteralsReplayThroughPreservedSourceWithoutWeakeningEquivalence
checks int -1, -7, int minimum, long -1 and long minimum, each with and
without a macro, including unchanged copies and reparsed exact type/value.
WeofRuntimeIT.nativeBoundaryOraclesCheckAllSupportedSuffixesAndSignedMinima
uses independent hexadecimal/bounded oracles for int -1 and -2147483648,
unsigned-int maximum, long -1 and -9223372036854775808, long 2147483648 and
unsigned-long maximum. These cover exact types as well as numeric values.

Compatible trusted library typedef handling is narrowed to avoid merging
unrelated user names or differing profiles. Actual ProjectEditor changes
invalidate existing template caches; the runtime test explicitly re-resolves
the edited graph with NameResolver.resolve before checking canonical types and
emitting it. This follows the existing editing contract rather than claiming
that raw editing recomputes lookup. Unchanged graph copies retain types.

## Aggregate result and merge gates

The expanded focused Maven verify run passed 255 unit tests and 25 integration
tests in 10:42, with zero failures, errors or skips. A separate final crosscheck
passed 14 unit tests in 15.559 seconds: six repeated WEOF tests and eight macro
inventory tests. This establishes 263 distinct focused unit tests across two
commands, not 269 distinct tests. The earlier review/repeated runs are not added
to that count.

With the documented Java/Maven and native C++03 toolchains available, the two
focused commands were:

```sh
mvn -o -B -Dtest=WeofRuntimeTest,TypedConstantFoldingTest,JsonExportTest,PlatformProfileTest,CCompatibilityFamiliesTest,StandardLibraryCatalogueTest,LibraryCatalogueFamiliesTest,RemainingLibraryResolutionTest,RemainingLibraryOverloadsTest,LazyLibraryGraphTest,LibraryForwardClassResolutionTest,StreamLibraryResolutionTest,StrstreamLibraryTest,ScopedResolutionTest,MacroSourceTest,CppPreprocessorTest,MbCurMaxRuntimeTest,ErrnoRuntimeTest,StandardStreamsRuntimeTest,LibraryExternalObjectsTest,AssertionSourceTest,SharedSourceRequirementsTest,GeneratedNameBindingTest,ProfileTypeSemanticsTest,RuntimeIntegerMacrosTest -Dit.test=WeofRuntimeIT,PackagedCliIT,MacroEditingIT,SharedHeaderGenerationIT,RuntimeIntegerMacrosIT,AssertionRuntimeIT,StandardStreamsRuntimeIT,StrstreamLibraryIT -DargLine=-Xmx768m verify
mvn -o -B -Dtest=WeofRuntimeTest,StandardLibraryMacrosTest -DargLine=-Xmx768m test
```

The inventory validator and its 17 mutation tests pass, including exact WEOF
metadata keys and continued rejection of unsupported integer-fact names/types.

Built JAR SHA256:
`3a25f05867e8fa3e735a6f2f2212006148fe494ad698052bf922c5ca328ad81b`.
Each existing return-zero JSON fixture has exactly 37 scalar changes: 36 derived
profile-hash replacements and one effective-settings string adding wideInteger={}.
No structural or unrelated value changes occur. The default profile identity is
`607b46cc721db9ae4f95a233fcc1b37f4651893f1869127e10c0a3fd5aa652cc`.
The empty model keeps the default opaque/unavailable behavior.

Full Maven verification is running separately and is not yet complete. Exact-head
CI is pending. These remain independent merge gates, not implied by focused
success or the JAR hash.

## Bounded inventory outcome and remaining gaps

The inventory contains 92 bounded obligations/85 names and 10 open
obligations/10 names, with focused unit and native acceptance confirmed.
Remaining names are HUGE_VAL, CLOCKS_PER_SEC, SIG_DFL/SIG_ERR/SIG_IGN,
offsetof, va_start/va_arg/va_end and setjmp. No complete character repertoire,
locale simulation, wide-library runtime implementation or general ABI
certification is provided. #92 and #48 remain open.

Модель и границы согласованы; целевые и независимые проверки выполнены,
расширенная целевая проверка прошла. Политика nonnegative-wchar намеренно строже требования ISO
об исключении действительных символов. Отрицательный WEOF=-1 допустим;
равенство EOF не требуется. Обе записи WEOF имеют ограниченную реализацию; полный Maven-набор
и CI проверяются отдельно.
