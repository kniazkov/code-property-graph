# C++03 #92: mutable errno binding / привязка изменяемого errno

## Scope and sources

This fourth sequential packet supplies the errno object macro under cerrno/errno.h
when cRuntime=glibc is explicitly selected. EDOM, ERANGE and EILSEQ values remain
separate target facts from the previous packet. No ordinary global int errno,
std::errno callable or libc implementation is fabricated.

The historical source basis is the existing reviewed C++03 ledger at 19.3/1–2,
table26 and 17.4.1.2/5, inheriting C90 7.1.4 and Amendment1. Original ISO2003
source and paragraph hashes remain unchanged; the private original PDF is absent
and no fresh full standards read is claimed.

Pinned upstream-source mirrors corroborate the actual glibc binding:
- [glibc2.41 errno.h](https://raw.githubusercontent.com/bminor/glibc/glibc-2.41/stdlib/errno.h):
  errno dereferences a global C-linkage int-pointer accessor with __THROW.
- [glibc2.41 errno-loc.c](https://raw.githubusercontent.com/bminor/glibc/glibc-2.41/csu/errno-loc.c):
  the accessor returns actual runtime storage, not a copied error value.

The selected C++03 signature is `extern "C" int* __errno_location() throw();`.
Compiler optimizer attributes are not imported as analysis promises. In particular,
a const accessor does not make its pointed-to errno value immutable. The generic
source file alone does not establish every target's TLS mechanism.

## Explicit binding and macro behavior

The CPG-owned replacement is `(*::__errno_location())`, so local identifiers do
not capture the real global helper. It is a modifiable int lvalue: reads, stores,
compound assignment, increments, references and addresses use the selected
runtime cell. sizeof remains unevaluated. The helper remains a lazily owned
library declaration with C linkage and an empty exception specification, not a
user declaration or generated body.

Existing glibc profile selection validates 8-bit bytes/char, 32-bit int and
32- or 64-bit pointers. Callers must select a compatible actual C ABI, calling
convention and runtime exporting the accessor. Neither Linux nor LP64 alone
selects a runtime; no target is certified merely by naming glibc.

Unknown runtime records an unavailable errno definition, allows an unused include
and diagnoses expansion with unavailable-library-macro. defined(errno) checks
modeled macro presence, not availability of a runtime value. The macro is not
required to be usable in #if: even the selected live binding is not a preprocessor
integer constant. Both header spellings retain include/TU isolation and source
provenance. Explicit actual headers retain precedence.

Successful library calls may change errno; no unchanged-value inference follows
from success. No reset is inserted before reads or calls. Numeric error codes
are not inferred from the accessor or from the runtime-family name.

## Conservative memory analysis

Rootless opaque pointer results exposed an existing analysis gap: a store through
an errno reference could be falsely reported unread because no named AST object
represented its external storage. The fix is generic, not a match on errno or the
helper's spelling.

CppMemoryFlow has one analysis-only unknown nonlocal location with no declaration
root and a wildcard path. It is never an AST variable or an invented extern
object. Opaque pointer/reference results include it alongside possible named
aliases. Opaque calls may read and weakly write it. Mixed known/opaque virtual
returns retain the unknown alternative; state copies and joins preserve it.
Wildcard updates do not kill earlier possible definitions. Thus different opaque
accessors may alias the same unknown cell conservatively; this is not an exact
errno storage identity, TLS model or context-sensitive error-state summary.

Terminal writes through references are considered possibly externally observable
unless the reference is proved to bind directly to known local nonstatic, non-extern scalar
storage. This exception applies to writes through the reference, not to its local
binding initialization: an unused reference binding still receives its normal
unread diagnostic. Direct local reference chains remain diagnosable; pointer-backed,
call-derived, cast or conditional bindings may lose unread-write precision even
when they happen to be local. This explicit conservative boundary avoids false
claims that external stores are dead. The memory analysis separately retains
observed write-to-read/call dependencies. Mutable dereferences remain unknown to
constant evaluation; the packet does not add a constant value for errno.

## Verification

The final focused run on 2026-11-07 passed **227 unit tests and 11 integration
tests**, with zero failures/errors/skips, in 2:38. Fresh shaded JAR SHA-256:
`370a52515844628c3e85aed358dbf8cf3371ec587d5112a3056517e61a2aa3d5`.
The published revision and final CI link belong to the PR verification section.

```sh
mvn -B -DargLine=-Xmx768m \
  -Dtest=ErrnoRuntimeTest,ContainerFamilyFlowTest,SequenceFlowTest,ObjectMemoryDataFlowTest,LibraryUnknownEffectsTest,UnreadWritesTest,ReferenceBindingDataFlowTest,CallSiteDataFlowTest,ObjectMemoryExportTest,ObjectMemoryValidationTest,StandardLibraryMacrosTest,CCompatibilityFamiliesTest,StandardLibraryCatalogueTest,JsonExportTest,PlatformProfileTest,MacroSourceTest,ExpandedExpressionDataFlowTest,FlowTest \
  -Dit.test=ErrnoRuntimeIT,PackagedCliIT,AssertionRuntimeIT,RuntimeIntegerMacrosIT verify
python3 scripts/test-cpp03-library-macros.py
python3 scripts/check-cpp03-library-macros.py
python3 scripts/test-inventory-cpp03-source.py
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
```

Local checks used cached offline Maven dependencies, OpenJDK21/Maven3.9.11,
GCC14.2.0 and Clang19.1.7. The independently compiled header/runtime oracle observed
8-bit bytes, 32/64/64-bit int/long/pointers, glibc headers and loaded runtime2.41,
and ERANGE34 on this particular target. That number is inserted into the test
fixture from its oracle, not guessed by production or inferred from cRuntime.

Native tests compile original, copied, generated, edited and materialized CPG
sources using both compilers with `-std=c++03 -pedantic-errors`. Materialization
contains the actual CPG accessor expression, verifies ten errno expansions and
undefines host errno/ERANGE after includes. Host headers supply ABI declarations,
not the tested macro expansion. Assignments through errno, a saved reference and
a saved pointer observe the same runtime cell. Real strtol overflow updates all
three views to the independently observed error number. Editing RHS11 to19 is
checked in the graph, reparsed graph and native output. GREEN and FOLDED generated
and materialized sources retain the mutable-state behavior.

API and packaged CLI snapshots agree. Copied graphs retain helper ownership,
C linkage, int-pointer return, throw(), macro provenance and lvalue typing.
Unknown runtime produces contextual error JSON, preserves unavailable metadata
through copies and blocks generation. No profile schema/identity change was
needed, so neither existing golden fixture changed.

`target/errno-runtime-oracle/` stores compiler versions, targets, include paths,
header/runtime observations and selected-header hashes (49 GCC, 52 Clang in this
run). The native probe source SHA-256 is
`15f953bd0e954f8499418fbc1e1ac18a1f6022f5c88f1103f028c931b4b34fe6`.
CI uploads its own evidence with the JAR/demo; its selected sysroot is observed
independently rather than assumed identical to this local runtime.

Independent fresh-source compilation and isolated tests verified rootless
pointer/reference stores, repeated opaque calls, reference chains, pointer
reassignment, indirect pointer/reference-returning calls, pointer members, branch
joins, mixed known/opaque virtual returns and block-scope extern bindings.
Direct local-reference dead-store diagnostics remain available. The unknown
location has no fabricated declaration, is null-root-safe, and always uses weak
updates. Four bounded ledger records are updated without changing original source
hashes. All 5,250 records and macro/source/paragraph validator tests pass.

The first full run exposed two existing container/sequence tests whose unused
reference-binding warnings had been hidden. The corrected target-write exception
keeps those tests unchanged and adds separate unused/used errno-reference checks.
Independent fresh-source review then passed all 68 tests in the two affected
families, object-memory flow and errno tests. Full Maven verify and required CI on
the exact final PR head remain separate merge gates; focused results alone are
not represented as a complete pass.

## Remaining scope

The inventory now separates 54 numeric/null profile definitions, 30 integer target
facts and two runtime bindings (assert and errno): 86 obligations/80 names with
bounded implementations, 16 obligations/15 names still open. Those include
HUGE_VAL, CLOCKS_PER_SEC, WEOF in two headers, handler sentinels, standard streams,
MB_CUR_MAX, offsetof, varargs and setjmp. General acceptance #48 and #92 remain
open. Full C-library semantics, precise external alias identity and C++03 thread
support are not claimed.

## Кратко по-русски

errno реализован как объектный макрос CPG с изменяемым int lvalue через реальную
glibc-функцию доступа. Числовые коды ошибок выбираются отдельно, неизвестный
runtime диагностируется при использовании. Вместо фиктивного глобального объекта
анализ использует консервативное неизвестное внешнее хранилище. Это сохраняет
возможные зависимости записей и чтений и исключает ложные выводы о мёртвых
записях через внешние ссылки, но не доказывает точную идентичность TLS или все
эффекты библиотечных функций. Остальные 16 обязательств и общая приёмка открыты.
