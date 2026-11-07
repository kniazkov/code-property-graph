# C++03 #92: typed runtime integer macros / целочисленные факты runtime

## Scope and source boundary

This third sequential packet supplies a bounded target-fact mechanism for exactly
30 object-like macro obligations, under both C++ and old C header spellings:

- cerrno: EDOM, ERANGE, EILSEQ (3).
- clocale: LC_ALL, LC_COLLATE, LC_CTYPE, LC_MONETARY, LC_NUMERIC, LC_TIME (6).
- csignal: SIGABRT, SIGFPE, SIGILL, SIGINT, SIGSEGV, SIGTERM (6).
- cstdio: _IOFBF, _IOLBF, _IONBF, BUFSIZ, EOF, FOPEN_MAX, FILENAME_MAX,
  L_tmpnam, SEEK_CUR, SEEK_END, SEEK_SET, TMP_MAX (12).
- cstdlib: EXIT_FAILURE, EXIT_SUCCESS, RAND_MAX (3).

The [inventory](cpp03-library-macros.json) binds every obligation to the existing
reviewed original-C++03 ledger: 17.4.4.2/2; 18.3/2 and table18; 18.7/2 and table22;
19.3/2 and table26; 22.3/2 and table62; 26.5/1–2; 27.8.2/1 and table94.
Inherited C90 references are 7.1.4, 7.4, 7.7, 7.9.1–3, 7.9.4.4 and 7.10;
Amendment1 adds EILSEQ. The original private ISO2003 PDF is not available here;
this work preserves its previously reviewed source/paragraph hashes and does not
claim a fresh full source read.

Original C90 error selectors are distinct nonzero integral constant expressions.
The later positive-int wording is not silently imported. Likewise a universal
int-expression claim is not made for every integral macro. This packet explicitly
supports int facts as a bounded target choice. Signal numbers must be positive;
EXIT_SUCCESS need not numerically equal zero. The historical distinctions recorded
in the previous assert packet remain unchanged.

## Explicit profile facts

`runtimeIntegerMacros` defaults to an empty object, including in old complete
profiles. Each known macro accepts exactly `type` and `value`, with type `int`
and an exact decimal string. For example:

```json
{"inherits":"default", "runtimeIntegerMacros":{
  "EDOM":{"type":"int","value":"33"},
  "ERANGE":{"type":"int","value":"34"},
  "EOF":{"type":"int","value":"-1"}
}}
```

Those are selected example facts, not defaults for glibc, Linux, LP64 or an
arbitrary target. Production does not consult JVM values, environment headers,
compiler output or the host libc. A caller must supply facts that actually match
the target library. Synthetic test profiles establish model behavior only and
are not asserted compatible with the linked native runtime.

Validation rejects unknown names, non-int types, arbitrary C++ expressions,
non-string/fractional values, extra entry fields and values outside target int.
Standalone profile files and embedded compilation-database profiles use strict
JSON and reject duplicate decoded keys, including nested and escaped-equivalent
names. This prevents silent last-value-wins target facts. The structural key walk
runs after JSON syntax validation and does not replace the JSON parser.
Names are sorted and integer strings canonicalized for stable profile/variant
identity; copies and JSON retain the facts. Replacement tokens preserve int type,
including the minimum through `(-INT_MAX - 1)`, not an oversized positive literal.
The produced preprocessor expression uses no C++ cast or hidden native include.

Constraints are scoped to each family: error codes are distinct/nonzero, locale
selectors distinct, signal numbers distinct/positive, buffering modes and seek
origins each distinct. EOF is negative; BUFSIZ >=256, FOPEN_MAX >=8, TMP_MAX >=25,
RAND_MAX >=32767 and representable as target int. Positive FILENAME_MAX/L_tmpnam
is a supported-profile policy, not proof that a supplied capacity suffices for
an actual filesystem or library. Numeric validation does not prove exit-status
meaning or any runtime function's behavior.

Partial maps are supported. Including a built-in header records unavailable
macro definitions for omitted facts. `defined`/#ifdef sees this modeled presence;
ordinary or #if expansion reports `unavailable-library-macro`, rather than
substituting zero or an empty usable replacement. Unused includes are allowed.
Explicit supplied headers keep precedence, header aliases share facts, and
preprocessor state stays local to its translation unit. Source ownership and
profile identity distinguish the macro definitions from physical host headers.

The trusted declaration-only strstream catalogue contains EOF default arguments.
Its explicit internal parse path preserves an unavailable EOF token and its
metadata as an unresolved value instead of inventing a number. This mode is not
selected by filenames and cannot load physical user headers. Ordinary user parsing
(including catalogue-looking filenames) and directive #if expansion still report
missing facts. Configured EOF facts expand normally in the catalogue. This is a
conservative declaration boundary, not permission to evaluate an unknown default.

## Reproducible checks

The final focused run on 2026-11-07 passed **121 unit tests and 14 integration
tests**, zero failures/errors/skips, in 3:13. The fresh shaded JAR SHA-256 is
`e3bed6fecc30c3639f6ffe8415520f3dd0f0906baf3c899937e0b3a3db1ad48d`.
The published source revision and exact-head CI link are recorded in the PR
verification section; full-suite and CI results are separate merge gates.

```sh
mvn -B -DargLine=-Xmx768m \
  -Dtest=RuntimeIntegerMacrosTest,StandardLibraryMacrosTest,CCompatibilityFamiliesTest,JsonExportTest,PlatformProfileTest,CompilationDatabaseTest,CppPreprocessorTest,MacroSourceTest,StandardLibraryCatalogueTest,LibraryCatalogueFamiliesTest,RemainingLibraryOverloadsTest,StrstreamLibraryTest \
  -Dit.test=RuntimeIntegerMacrosIT,PackagedCliIT,LibraryMacrosIT,AssertionRuntimeIT,StrstreamLibraryIT verify
python3 scripts/test-cpp03-library-macros.py
python3 scripts/check-cpp03-library-macros.py
python3 scripts/test-inventory-cpp03-source.py
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
```

Local Maven used offline cached dependencies, OpenJDK21 and Maven3.9.11. The
native oracle used GCC14.2.0 (x86_64-linux-gnu) and Clang19.1.7
(x86_64-pc-linux-gnu), 8-bit bytes and 32/64/64-bit int/long/pointers. Both observed
glibc headers 2.41 and the actually loaded runtime 2.41. It independently compiles
all 30 real header macros and checks overload-visible exact int types, then
supplies those observed facts to CPG and compiles actual materialized token
expansions with both compilers using `-std=c++03 -pedantic-errors`. Merely running
a regenerated file that includes native headers is not the only oracle.

The native manifest SHA-256 is
`427c6385971181f90e8d5d4c7f11187f830a3044b025feb9e2b82ae8a20ec455`.
The independent probe source SHA-256 is
`8cbbde239a2ef143f635f9ec96354d9b6234f4a37c0c2f9cb39d73c7131b3234`.
Test artifacts under `target/runtime-integer-oracle/` record each compiler version,
target, include search path, probe/profile hashes and all selected header hashes
(115 for GCC, 119 for Clang in this local run). CI uploads its own oracle artifacts
beside the JAR and demo; a different CI sysroot is observed independently, not
misrepresented as the same local runtime.

An intentionally different synthetic profile exercises negative error codes,
nonzero EXIT_SUCCESS and INT_MIN. Its materialized constant-only expressions also
run int-versus-long overload checks under the compatible native integer model;
this does not assert that synthetic runtime numbers match the host library.
Both aliases pass API/CLI JSON equivalence, copied graphs, explicit edited-
initializer and reparsed-value assertions, as well as provenance ownership.
Unavailable ordinary/#if uses produce contextual error JSON and block generation.

Both API/CLI golden fixtures were inspected structurally: exactly 37 changed
scalar values each, comprising 36 profile-identity occurrences and one effective
settings string gaining runtimeIntegerMacros={}. No graph shape, source range,
node ID, pass result or unrelated value was regenerated. Thirteen bounded ledger
records add narrow macro evidence while retaining original source hashes and
remaining runtime gaps. Thirteen macro-inventory mutation tests, nine source-
inventory tests, thirteen paragraph-checker tests and all 5,250 records pass.

Independent review reproduced and verified the duplicate-key fix, including
500 valid escaped/nested documents and nested/Unicode-equivalent duplicate
rejection. Fresh-source probes also verified unavailable catalogue metadata,
spoofed-filename user diagnostics, directive rejection and default-profile
strstream resolution. Full Maven verify and required exact-head CI must still
pass before merging; focused results alone are not presented as complete.

## Remaining work

The current inventory separates 54 numeric/null profile definitions, one bounded
assert runtime binding, 30 typed integer target-fact obligations and 17 still-open
obligations (16 names):

- HUGE_VAL, CLOCKS_PER_SEC and WEOF under both wide headers: expression type,
  representation, frequency and wide-character domain facts remain necessary.
- SIG_DFL, SIG_ERR, SIG_IGN: real handler pointer representation/call ABI.
- errno, stdin, stdout, stderr and MB_CUR_MAX: live runtime state and bindings.
- offsetof, va_start, va_arg, va_end and setjmp: layout, calling convention and
  control-transfer semantics, not fake declarations or guessed constants.

Thus 85 obligations/79 names have bounded implementations, but not every profile
provides every value. The packet does not implement locale state, signal delivery,
I/O, errno storage, random generation, exit semantics or complete C-library ABI.
Issue #92 and general acceptance #48 remain open.

## Кратко по-русски

Добавлены явные типизированные факты для 30 объектных целочисленных макросов.
Значения не угадываются по машине запуска или имени libc. Без факта раскрытие
макроса диагностируется, включая #if; наличие определения через defined не
означает известного значения. Ограничение типа int выбрано реализацией, а не
приписано всему исходному C90. Числа не подменяют состояние errno, сигналы,
потоки, locale или другие эффекты runtime. Оставшиеся 17 обязательств сохранены
в открытой #92, общая приёмка #48 также не закрывается.
