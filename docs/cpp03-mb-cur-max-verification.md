# C++03 #92: live MB_CUR_MAX / текущий многобайтовый предел

## Scope and historical sources

This fifth sequential packet supplies one MB_CUR_MAX macro obligation under
cstdlib/stdlib.h. It does not implement standard streams, wide/time/math scalar
macros, varargs, setjmp or complete locale conversion semantics. #92 and general
acceptance #48 remain open.

The existing reviewed C++03 ledger supplies 21.4/2, table49 and 17.4.1.2/5,
inheriting C90 7.10. Original ISO2003 source and paragraph hashes are retained;
the private original PDF is absent and no fresh full standards read is claimed.
Original C90 requires a positive integer expression for the current LC_CTYPE
limit, no greater than MB_LEN_MAX. It does not universally require size_t.
[WG14 approved minutes N872](https://www.open-std.org/jtc1/sc22/wg14/www/docs/n872.htm),
items [8l.] and 37, record the C9X change to size_t. The inventory corrects that
historical distinction rather than importing later wording.

Pinned glibc2.41 source mirrors establish the selected implementation contract:
- [stdlib.h](https://raw.githubusercontent.com/bminor/glibc/glibc-2.41/stdlib/stdlib.h)
  defines MB_CUR_MAX through the global size_t-returning accessor.
- [mb_cur_max.c](https://raw.githubusercontent.com/bminor/glibc/glibc-2.41/locale/mb_cur_max.c)
  reads current locale data, not a permanent maximum or compile-time constant.
- [limits.h](https://raw.githubusercontent.com/bminor/glibc/glibc-2.41/include/limits.h)
  supplies MB_LEN_MAX16 for this implementation.

## Explicit deployment contract

The new profile field mbCurMaxBinding defaults to unavailable. Its sole supported
value is glibc-x86_64-lp64-mb16. Selection requires cRuntime=glibc, 8-bit bytes,
32-bit int, 64-bit long/pointers, exact sizeType=unsigned long and
maxMultibyteLength=16. Contradictory explicit selections are rejected, not repaired.
Equal widths are insufficient to equate unsigned int and unsigned long.

This is a caller-selected deployment contract. The settings validate these
stated facts; they do not detect architecture or prove calling convention,
libc export/version compatibility, or every target ABI property. The actual
program must run against a matching target. Other glibc ABIs are not certified
by naming the family. Existing errno/assert profiles remain valid and unchanged.
Defaults/builder/inherits=default already select MB_LEN_MAX16; inherits=none
without the optional bound retains its legacy fallback1.

The capability participates in profile serialization, copies and canonical
identity. Both existing return-zero JSON fixtures change only the effective
settings string and its derived hashes; the new default field does not enable
the runtime binding.

## Macro, declaration and analysis behavior

The CPG-owned object macro expands to `(::__ctype_get_mb_cur_max())`.
The genuine global declaration has C linkage, an unsigned-long return type equal
to the selected canonical size_t, and throw(). It is appended outside ordinary
cstdlib namespace wrapping; ordinary functions and cstddef dependencies remain.
The helper is lazily owned as a library declaration, not a generated libc body.
Global qualification avoids capture by local names.

The query is a scalar rvalue and remains unknown to constant evaluation before
and after setlocale. sizeof does not execute it. No locale result is guessed,
cached, clamped or replaced with MB_LEN_MAX. Conservative opaque library-call
effects remain; no precise locale-state engine is introduced.

Missing capability allows unused includes and records an unavailable definition;
ordinary or #if use is diagnosed. defined(MB_CUR_MAX) tests modeled definition
presence, not binding availability. Even a selected live query is not a required
preprocessor integer constant. Explicit physical headers keep precedence, and
translation units/profiles retain their own macro state and provenance.

Pre-existing generic limitation: CPG identifies scalar-call rvalues correctly
but does not itself reject all assignment, increment or address-taking misuses.
This packet does not add a spelling-specific exception. Strict native rejection
of these expressions is separate evidence, not a CPG diagnostic claim.

## Verification

The final focused run on 2026-11-07 passed **120 unit tests and 14 integration
tests**, with zero failures/errors/skips, in 1:15. Fresh shaded JAR SHA-256:
`6c9d89c029dcd94bbd69ab6af19d58d76199455a79856cc60ea973cfe46186ed`.
The published revision and final CI link belong to the PR verification section.
Full Maven verify and required CI on that exact revision remain separate merge
gates; these focused results alone are not represented as a complete pass.

```sh
mvn -B -DargLine=-Xmx768m \
  -Dtest=MbCurMaxRuntimeTest,JsonExportTest,PlatformProfileTest,StandardLibraryMacrosTest,CCompatibilityFamiliesTest,StandardLibraryCatalogueTest,LibraryCatalogueFamiliesTest,RemainingLibraryOverloadsTest,MacroSourceTest,CppPreprocessorTest,ErrnoRuntimeTest,RuntimeIntegerMacrosTest \
  -Dit.test=MbCurMaxRuntimeIT,PackagedCliIT,ErrnoRuntimeIT,AssertionRuntimeIT,RuntimeIntegerMacrosIT verify
python3 scripts/test-cpp03-library-macros.py
python3 scripts/check-cpp03-library-macros.py
python3 scripts/test-inventory-cpp03-source.py
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
```

Local Maven used cached offline dependencies, OpenJDK21/Maven3.9.11. GCC14.2.0
and Clang19.1.7 compiled native sources using `-std=c++03 -pedantic-errors`.
Independent header/runtime observations established glibc headers and loaded
runtime2.41, unsigned-long size_t/query overload, MB_LEN_MAX16 and an installed
C.utf8 locale on this target. Live C -> C.utf8 -> C queries returned 1 -> 6 -> 1.
Six is an observed locale result, not a universal UTF-8 limit or production
constant. Missing usable UTF-8 coverage is a test failure, not silently skipped.

Original, copied, generated, edited and actual CPG-materialized sources execute
under both compilers. GREEN/FOLDED sources preserve the live result. The host
MB_CUR_MAX is undefined after includes, so it cannot supply the tested expansion.
The included native headers provide declarations, not the query macro. Actual
edits alter the written locale string beneath its array-to-pointer conversion;
the graph, reparsed graph and native output verify the change. Separate native
negative probes reject assignment, increment and address-taking of the actual
materialized scalar query. They do not assert that CPG issues those diagnostics.

API and packaged CLI snapshots agree; capability, helper ownership, C linkage,
throw(), exact type and macro provenance survive copying. Unknown capability
produces error JSON and blocks generation while unused includes remain usable.
Both return-zero fixtures have exactly 37 scalar differences: 36 derived hash
replacements and one effective-settings string adding mbCurMaxBinding=unavailable.
Their JSON structure is unchanged. New default profile identity is
`d6e518b7698a65a92001cfe0c547557c64c72c048d0765c0a8b5a8fe0d954e21`.

`target/mb-cur-max-oracle/` records compiler/target/locale facts, selected-header
hashes (76 GCC, 80 Clang in this run), dependency/runtime library hashes and
source/binary hashes. Native probe source SHA-256:
`83b07452530a3d0a3e5826d75b1c1c4c91eae181931a0c46cd870dc2544182cd`.
CI uploads its own independently observed evidence; its sysroot is not assumed
identical to this local target.

Independent review compiled production fresh and passed all six focused unit
tests, then compiled actual CPG tokens and the actual catalogue helper declaration
with both native compilers, observing the same 1 -> 6 -> 1 transition. It found no
blocking implementation issue and explicitly retained the generic diagnostic gap.
Three existing paragraph records gain bounded evidence without changing original
source hashes, coordinates or paragraph hashes. Broader inherited C contracts
remain partial, not proven by one successful macro binding.

## Remaining scope

The inventory now has 54 profile-definition,
30 integer-target-fact and three runtime-binding obligations: 87 obligations/81
names with bounded implementations, 15 obligations/14 names still open. A counted
binding is not available under every profile and is not complete library or ABI
conformance. Historical packet verification reports retain their then-current
remainder lists.

## Кратко по-русски

MB_CUR_MAX остаётся объектным макросом и читает текущий предел локали через
реальную глобальную функцию glibc. Привязка включается отдельно и требует
совместимого узкого контракта профиля; одного имени glibc недостаточно. Значение
не подменяется MB_LEN_MAX и не вычисляется статически. Исходный C90 требует
положительное целочисленное выражение, а точный size_t относится к выбранной
реализации. Общая приёмка и оставшиеся макросы не закрываются этим пакетом.
