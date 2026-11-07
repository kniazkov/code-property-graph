# C++03 #92: explicit assertion binding / явная привязка assert

## Source and edition boundary

This packet addresses cassert/assert.h and NDEBUG selection within #92. The
source basis is the existing reviewed C++03 ledger at 19.2 (paragraphs 1–3 and
table25), 17.4.1.2/5, 17.4.2.1/2 and 18.3/3. The original private ISO2003 PDF
is absent here; no fresh examination of it is claimed and no source hash changes.
Stringization and predefined invocation coordinates follow the existing §16
preprocessor implementation and its regressions.

Historical expression types need care. [WG14 C90 DR107](https://www9.open-std.org/JTC1/SC22/WG14/issues/c90/issue0107.html)
says an enabled C90 implementation may rely on int arguments; later scalar or
contextual-bool wording is not silently imported into original C++03. The exact
C++ adaptation remains an evidence gap. This implementation deliberately uses a
C++03 bool conversion, with concrete tests of integer expressions. No C++11
constexpr, explicit-conversion-operator, __func__ or variadic-assert guarantee is
claimed. Existing strict C++03 empty-macro-argument diagnostics remain unchanged.

The glibc binding was checked against pinned upstream-source mirror files:
[assert.h](https://raw.githubusercontent.com/bminor/glibc/glibc-2.39/assert/assert.h),
[assert.c](https://raw.githubusercontent.com/bminor/glibc/glibc-2.39/assert/assert.c)
and [sys/cdefs.h](https://raw.githubusercontent.com/bminor/glibc/glibc-2.39/misc/sys/cdefs.h).
The real helper is global, has C linkage and C++03 throw(), returns void and takes
const char*, const char*, unsigned int, const char*. Its implementation diagnoses
on standard error and aborts. The optional function-name argument may be null;
CPG passes zero rather than depending on a compiler extension. No GNU attribute
or implementation body is emitted by CPG. These sources establish a runtime
binding, not a function required by the ISO standard library.

## Explicit target selection

`cRuntime` accepts unknown (default, including older profiles) or glibc. Selection
participates in serialization, stable profile identity, build variants and
library ownership. The implemented signature binding validates 8-bit bytes/char,
32-bit int, and 32- or 64-bit pointers. These checks do not identify a whole
architecture ABI: callers must select a compatible glibc C calling convention,
pointer ABI and actual linked runtime exporting __assert_fail. LP64 or Linux is
not sufficient to select libc automatically. This setting does not implement
errno, streams, locale, setjmp, offsetof or varargs bindings.

For a concrete assertion-enabled model:

```json
{"inherits":"default", "cRuntime":"glibc"}
```

Unknown runtime still permits an unused cassert/assert.h include. The graph
records a function-like definition with `unavailableReason`, without inventing
a callable expansion. An enabled invocation produces the contextual
`unavailable-library-macro` diagnostic, preserves error JSON and blocks generated
sources. NDEBUG-disabled assertions need no runtime binding.

## Macro and source behavior

Every inclusion samples NDEBUG's presence, including NDEBUG=0, and redefines
assert accordingly. Changing NDEBUG without reinclusion leaves the current mode
unchanged. Disabled mode drops a nonempty unused argument without prescan,
C++ name/type checks or evaluation; balanced argument collection and other
preprocessing syntax still apply. Enabled mode evaluates its condition once,
independently stringizes the raw argument and passes invocation file/line to the
real selected helper. Names inside the user's namespace/class cannot capture the
globally qualified helper. Both header spellings retain the same C-linkage ABI.
Explicit supplied headers take precedence over this built-in model.

Enabled/disabled definitions have separate source keys, and reinclusions retain
separate definition/event identities. Owned external-input text includes the
function-like parameter list. Unavailable metadata is retained in parser/model
records, copies and JSON. No std::assert function or fake libc implementation is
added to the graph.

Generated source preserves assertion diagnostic coordinates. When an assertion
uses the original physical filename, its physical output file receives a precise
#line prefix so relocation does not silently change __FILE__/__LINE__. Existing
logical filename mappings are retained. Files without such expansions are not
modified for this purpose. Regeneration is idempotent. Full relocated AST
comparison is retained, not weakened by dropping the diagnostic strings.

**Graph-edit limitation:** a changed node inside a built-in assert expansion is
rejected during source generation with an explicit re-stringization-required
message. Otherwise its condition could change while its diagnostic text stayed
frozen. Edit the assertion's source argument and reparse instead. Copying and
unrelated graph edits remain supported. This packet does not claim arbitrary
assert-condition editing through the expanded graph.

The runtime failure is abort, not ordinary return, exit or C++ exception
unwinding. Native tests check the actual binding and absence of following,
destructor and atexit actions. The analysis still treats the resolved helper
conservatively: no terminal CFG fact is inferred merely from its spelling, and
complete nonreturning-call modeling is not claimed.

## Verification commands and scope

Production/test revision: `ffc06f95d34fb31b8cd1c5b014e9a59e7c9330e4`.
The final focused run passed **142 unit tests and 9 integration tests**, with
zero failures, errors or skips on 2026-11-07. Fresh executable JAR SHA-256:
`f09d4fa603e60b54bd385e6a8ac96d703c5850e4a5e4ff2617d4e7faefd9818b`.
Local tools: OpenJDK21, Maven3.9.11, GCC14.2, Clang19.1.7; cached Maven artifacts
allow offline mode. Reproduce the focused checks after building:

```sh
mvn -B -DargLine=-Xmx768m \
  -Dtest=AssertionSourceTest,AssertionMacrosTest,JsonExportTest,StandardLibraryMacrosTest,MacroSourceTest,PlatformProfileTest,CppPreprocessorTest,CCompatibilityFamiliesTest,StandardLibraryCatalogueTest,LibraryCatalogueFamiliesTest,RemainingLibraryOverloadsTest,RemainingLibraryResolutionTest,LibraryUnknownEffectsTest \
  -Dit.test=AssertionRuntimeIT,PackagedCliIT,LibraryMacrosIT verify
```

The focused suite includes AssertionMacrosTest, StandardLibraryMacrosTest,
profile/preprocessor/source tests, both reviewed JSON fixture paths and
AssertionRuntimeIT. The latter compiles and runs original, generated and
materialized CPG expansions with GCC and Clang using
`-std=c++03 -pedantic-errors`. Materialized sources contain the actual CPG
conditional and __assert_fail call, rather than relying only on the host assert
macro. API/packaged-CLI JSON, copying, unrelated edits, error JSON, explicit
condition-edit rejection, raw diagnostic text and presumed locations are checked.

Both reviewed JSON snapshots changed only in 37 scalar places each: 36 profile
hash occurrences and one effective-settings string gaining cRuntime=unknown.
No syntax, node IDs, ranges, passes or predefined values were regenerated or
weakened. Source reviewed values remain separate from implementation evidence.

Independent review reproduced the original stale-text edit and confirmed its
safe rejection after the fix. It also checked quoted physical paths, number-only
#line, logical filenames, nested wrapper macros, mixed logical/physical cases,
local-header assertions, scoped helper lookup and relocation idempotence.

The [complete inventory](cpp03-library-macros.json) now separates 54 numeric/null
profile obligations, one explicit assertion runtime binding and 47 open
obligations. Eight inventory mutation tests require explicit ABI/unknown-runtime
boundaries for a runtime-binding claim. #92 and general acceptance #48 remain
open. Full Maven, corpus completeness, native catalogue checks, paragraph
validation, strict JavaDoc, demos and exact-head CI remain separate merge gates;
focused results are not a substitute. Final commands, counts and executable JAR
identity are recorded with the PR verification results.

## По-русски

Пакет добавляет собственный функциональный макрос assert с переключением при
каждом include по наличию NDEBUG. Отключённый режим не вычисляет и не раскрывает
аргумент; включённый требует явного cRuntime=glibc и реального совместимого ABI.
Неизвестная привязка диагностируется при вызове, а не при одном включении.
Проверяются настоящие раскрытия CPG, сообщения о ложном условии, координаты,
завершение без следующих действий/деструкторов/atexit, копии и независимые правки.

Изменение внутри раскрытия assert явно отклоняется, чтобы не оставить старую
строку диагностики при новом условии. Для такой правки нужно изменить исходный
аргумент и повторить разбор. Полная модель невозвращающихся вызовов, ABI всей
архитектуры и все исторические типы аргумента не объявляются реализованными.
С учётом первого пакета охвачены 55 из 102 пар «заголовок/макрос», остальные
47 остаются в #92. Общая приёмка #48 не закрывается.
