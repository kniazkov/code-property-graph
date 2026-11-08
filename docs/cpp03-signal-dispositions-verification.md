# C++03 #92: explicit signal disposition sentinels

## Focused evidence

Expanded focused verification passed 313 unit tests and 22 integration tests
in 5:13, with zero failures, errors or skips. The earlier packaged 14-unit/three-IT
run and independent six-unit/three-IT review overlap this result and are not
added to the distinct test count. The three inventory entries have bounded
runtime_binding support. Full Maven and exact-head CI remain separate gates.
Earlier reports and pre-restart probes retain their historical scope.

## Original contract and source evidence

Fresh public C90 §7.7, printed page 120/PDF page 130, requires SIG_DFL, SIG_IGN and
SIG_ERR to expand to distinct constant expressions whose type is compatible with
the second argument and return value of signal, and whose values compare unequal
to the address of any declarable function. This is separate from the positive-int
signal-number requirement. The disposition macros are not integer target facts.
Section 7.7.1.1 describes previous-disposition return and SIG_ERR failure behavior.

Public [ANSI/ISO 9899-1990 PDF](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf)
was downloaded anew and matched SHA256
`26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
This does not constitute fresh access to the absent private ISO2003 source.
Existing reviewed C++03 18.7/2 and table 22 anchor the header obligation;
original source hashes, paragraph hashes and coordinates remain unchanged.
Their source SHA256 is
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.

## Explicit selected binding

`signalDispositionBinding: "glibc-x86_64-lp64"` selects a bounded target contract.
Its default is unavailable. Selection requires cRuntime=glibc, 8-bit bytes,
32-bit int and 64-bit long/pointers; it does not depend on sizeType or MB_LEN_MAX.
The capability is not enabled by cRuntime alone and does not detect a target ABI.
The caller must select matching integer-to-function-pointer semantics and runtime.

Selected expansions are:

```cpp
#define SIG_DFL ((void (*)(int)) 0)
#define SIG_IGN ((void (*)(int)) 1)
#define SIG_ERR ((void (*)(int)) -1)
```

These are selected glibc sentinel representations, not universal ISO values.
Existing cast/function-pointer nodes represent them; no new intrinsic, linked
helper, fake function, callback body or numeric pointer constant is introduced.
No integer #if guarantee is supplied. Missing capability allows unused headers
and presence checks but diagnoses actual use. Actual physical headers remain
authoritative, and ordinary csignal/signal.h declarations remain available.

## Type, state and linkage limits

The structural handler type is pointer to function(int) returning void. Existing
FunctionType does not encode language linkage, and catalogue signal retains
C++ linkage. Native selected-ABI compatibility does not close that precision gap
or prove full ISO C++03 signal/callback linkage conformance. sig_atomic_t stays
opaque. signal/raise remain conservative opaque library calls; no asynchronous
callback CFG, handler state machine, pointer-value evaluator or purity is claimed.

## Executed fresh evidence

Both spellings and mixed orders, unavailable/default/mismatched settings,
provenance, actual headers, TU/profile isolation, copies/reanalysis, exact cast
rvalue/type and API/CLI/error JSON have focused tests. sizeof is unevaluated;
function-pointer/reference dataflow remains conservative. Actual edits
between ignore/default change runtime checks, not an unused initializer.
Source replay guards and unsupported structural-fold boundaries remain intact.

Native GCC/Clang tests capture real macros before undefining them, then run
actual CPG materialized casts against independently captured values and types.
Compiler/target/sysroot/header/runtime evidence was freshly regenerated.
No pre-restart observation is counted as current CPG acceptance.

Safe subprocess tests use independently probed SIGTERM: save the old
disposition while setting ignore, set default and check the returned previous
value, then restore the original. Never install SIG_ERR, invoke a sentinel,
raise/kill/deliver a signal, depend on timing or alter parent dispositions.
Failure paths attempt restoration of saved state where possible. These checks prove a
bounded no-delivery interaction, not asynchronous behavior or a complete signal
runtime model.

## Native provenance and remaining gates

Exact packaged command:

```sh
mvn -o -B -Dtest=SignalDispositionsRuntimeTest,StandardLibraryMacrosTest -Dit.test=SignalDispositionsRuntimeIT -DargLine=-Xmx768m verify
```

The 14 units comprise six signal tests and eight macro crosschecks. Independent
isolated review passed six signal units and three native cases and approved the
bounded implementation. These are overlapping checks, not additive distinct counts.

Fresh target/signal-dispositions-oracle evidence records GCC 14.2 / Clang 19.1.7,
glibc 2.41 headers/runtime, 8-bit bytes / 32-bit int / 64-bit long and pointers, independently probed
SIGTERM 15 with exact int type, and 84/87 selected-header hashes. Probe SHA256:
`fd8e50fadb63f2c9510ad80127b45a7fec9f5565f6949b872ee3636833f3908b`.
Native pointer-type/value comparisons and actual materialized casts passed.

Host preprocessing has a specific caveat: glibc's typedef-cast SIG_ERR
((__sighandler_t) -1) incidentally passes the tested #if SIG_ERR==0 because an
unrecognized typedef identifier is treated as zero there. Host SIG_DFL/SIG_IGN
reject; all three selected explicit void(*)(int) casts reject. This is recorded
behavior, not a portable #if contract or a claim that replacements preserve host
preprocessor accidents. The first overstrong native-negative assertion was
corrected in the fixture, not repaired by changing production behavior.

Production changes are limited to PlatformProfile.java and CppPreprocessor.java.
Tests are analysis/SignalDispositionsRuntimeTest.java and
e2e/SignalDispositionsRuntimeIT.java, plus the existing macro crosscheck.
The expanded command passed 313 distinct unit tests and 22 integration tests:

```sh
mvn -o -B -Dtest=SignalDispositionsRuntimeTest,StandardLibraryMacrosTest,JsonExportTest,PlatformProfileTest,CppPreprocessorTest,MacroSourceTest,TypedConstantFoldingTest,CCompatibilityFamiliesTest,StandardLibraryCatalogueTest,LibraryCatalogueFamiliesTest,RemainingLibraryResolutionTest,RemainingLibraryOverloadsTest,LazyLibraryGraphTest,ScopedResolutionTest,LibraryUnknownEffectsTest,CallSiteDataFlowTest,ObjectMemoryDataFlowTest,ReferenceBindingDataFlowTest,FlowTest,ProfileTypeSemanticsTest,SharedSourceRequirementsTest,GeneratedNameBindingTest -Dit.test=SignalDispositionsRuntimeIT,PackagedCliIT,MacroEditingIT,SharedHeaderGenerationIT,AssertionRuntimeIT,HugeValRuntimeIT,StandardStreamsRuntimeIT -DargLine=-Xmx768m verify
```

An accessor Javadoc relocation followed this run. It changes comments only;
the final six-unit/three-IT rerun passed in 1:08 with zero failures, errors or
skips. It overlaps the expanded run completely; distinct coverage stays
313 units and 22 IT. Independent review confirmed the comment-only delta.
Final PlatformProfile.java SHA256:
`8ac08e806cac35c857916e5059a52a1195c15118547d58699bbf4bd1ed415dff`.
Final JAR SHA256:
`d2e650310d3afbd02f5f7f3f815559f9c4ce9e22905d69b66ea0c1ad9f009477`.

```sh
mvn -o -B -Dtest=SignalDispositionsRuntimeTest -Dit.test=SignalDispositionsRuntimeIT -DargLine=-Xmx768m verify
```

Both return-zero golden fixtures have exactly 37 scalar changes each: 36 derived
hash replacements and one effective settings string adding
signalDispositionBinding=unavailable. No structural change is claimed. New
default profile identity:
`c5640eb4218ea1ede66036a75a28a31b2caa37bd714a71770cc0ba060f632e7a`.
Full Maven and exact-head CI remain separate pending gates.

## Inventory outcome

The three bounded verified bounded obligations bring totals to
97 bounded obligations/90 names and five open obligations/five names. Remaining
names are offsetof, va_start/va_arg/va_end and setjmp. Packaged native acceptance passed. #92 and #48 remain open.

Три макроса обозначают различные константные выражения, совместимые с типом
обработчика, а не положительные целые номера сигналов. Выбранные glibc-приведения
не являются универсальными значениями ISO. Тесты не должны доставлять сигналы;
пробел идентичности языкового связывания остаётся открытым. Прошли 313 различных unit-тестов и 22 интеграционных теста; повторная проверка
после правки комментария прошла без прибавления тестов к общему числу.
Полный набор и CI учитываются отдельно.
