# C++03 #92: standard streams / стандартные потоки

## Scope and historical sources

This sixth packet adds three bounded stdin/stdout/stderr obligations under
cstdio/stdio.h. The inventory has 90 bounded obligations/84 names and 12 open
obligations/11 names: 54 profile definitions, 30 target facts, six runtime
bindings and 12 open entries. #92 and general acceptance #48 remain open.
Previous packet verification reports retain their historical scope.

Existing source-reviewed C++03 records are 27.8.2/1, table94 and 17.4.1.2/5,
inheriting C90 7.9.1. Original C90 promises FILE-pointer expressions for the
standard streams, not universally mutable pointer lvalues. Original source and
paragraph hashes are retained. The private ISO2003 PDF is absent; no fresh
full-source read or replacement paragraph hash is claimed. The ledger source
SHA256 remains
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.

Selected implementation sources:
- [glibc 2.41 stdio.h](https://raw.githubusercontent.com/bminor/glibc/glibc-2.41/libio/stdio.h)
  declares global FILE* objects inside C linkage and defines self-referential macros.
- [glibc 2.41 stdio.c](https://raw.githubusercontent.com/bminor/glibc/glibc-2.41/libio/stdio.c)
  defines the real pointer cells. Those definitions/internal stream objects are
  not copied into the CPG catalogue.
- [GNU standard-stream documentation](https://sourceware.org/glibc/manual/2.24/html_node/Standard-Streams.html)
  documents assignable GNU variables and contrasts nonassignable implementations.
  Its later-edition details are not imported as original C90 requirements.

## Explicit deployment and type contract

`standardStreamsBinding` defaults to `unavailable`; `cRuntime=glibc` alone does
not enable it. The supported opt-in `glibc-x86_64-lp64` requires glibc, 8-bit
bytes, 32-bit int, 64-bit long/pointers and exact unsigned-long sizeType. There
is no MB_LEN_MAX or multibyte-bound coupling. The setting participates in profile
serialization/copying and stable identity. Matching widths do not establish
architecture, data-pointer/C ABI or runtime exports: the caller must select a
matching deployed target.

Exact self-referential macros name genuine global extern-C mutable FILE*
objects. The pointee is the existing canonical opaque library::FILE used by
stdio functions, under both header spellings. No FILE layout, initializer,
synthetic storage, accessor or std stream alias is invented. Actual headers
retain precedence. Missing capability permits unused includes and macro-presence
checks but diagnoses use.

LibraryExternalObjects groups only trusted global declaration-only C-linkage
scalar objects by full stable profile, external name, canonical type and linkage.
Lookup, previous-declaration chains, demand peers and CppMemoryFlow storage
roots/accessibility agree. Header declaration occurrences keep their provenance.
User/local/static/namespaced/incompatible/profile-distinct objects are not merged
indiscriminately. This is bounded external identity, not general linker emulation.

## Confirmed focused evidence

Executed evidence available when this report was written:
- `src/test/java/com/kniazkov/cpg/analysis/LibraryExternalObjectsTest.java`:
  10 tests passed.
- `src/test/java/com/kniazkov/cpg/analysis/StandardStreamsRuntimeTest.java`:
  eight tests passed.
- `src/test/java/com/kniazkov/cpg/e2e/StandardStreamsRuntimeIT.java`:
  all three cases passed, zero failures/errors/skips. The focused unit/native command completed
  in 1:04; its recorded result is a focused run, not a full-suite result.
- Independent review freshly compiled production, passed the same 18 focused
  tests and eight additional cross-include pointer/reference/opaque-call probes,
  and reviewed native source safety. Those additional probes are supplementary
  review evidence, not eight extra committed regression tests.

The native cases cover four header arrangements and original, copied, generated,
edited, reparsed, GREEN and FOLDED sources under GCC 14.2 and Clang 19.1.7.
Actual CPG macro expansions are materialized; host stream macros are undefined
after materialization includes, so native host replacement cannot conceal a
missing CPG expansion. Subprocesses capture stdin/stdout/stderr separately.
Pointer redirection restores stdout before closing temporary files; original
streams are never closed. Edited payloads are checked through native output.

Local evidence identifies glibc headers/runtime 2.41, x86_64 LP64,
byte8/int32/long64/pointer64, unsigned-long size_t and mutable FILE*& overloads.
Real data-object exports are stdin/stdout/stderr@@GLIBC_2.2.5 in the hashed libc.
Selected-header provenance records 41 GCC and 44 Clang header hashes. These are
local deployment observations, not a claim for every glibc architecture/version.
Artifacts are produced under `target/standard-streams-oracle/` by the tests.
Native probe SHA256:
`65aaa262aebde3407b698829189e3c5efaf213b101a51aee52fd120e0ea7153f`.

## Fixture and identity changes

Each existing return-zero JSON fixture has exactly 37 justified scalar changes:
36 derived-hash replacements and one effective-settings string adding
standardStreamsBinding=unavailable. There are no structural fixture changes.
The resulting default profile identity is
`749d7539776f36e777adb42b486d69d2c5ae99b77f736a3dd6ffb11dab84736a`.
Adding the default setting does not silently enable a runtime binding.

## Expanded focused verification and remaining merge gates

On 2026-11-07, using Java 21, Maven 3.9.11 and cached offline dependencies,
the expanded focused command passed
350 unit tests and 18 integration tests with zero failures, errors or skips in
6:42. A separate final inventory crosscheck passed eight unit tests. Thus 358
focused unit tests passed across two commands, not in one run. These results do
not add the earlier repeated focused checks to the count. Production hashes
remain unchanged from the independent review.

Exact commands:

```sh
mvn -o -B -Dtest=StandardStreamsRuntimeTest,LibraryExternalObjectsTest,JsonExportTest,PlatformProfileTest,CCompatibilityFamiliesTest,StandardLibraryCatalogueTest,LibraryCatalogueFamiliesTest,RemainingLibraryResolutionTest,RemainingLibraryOverloadsTest,LazyLibraryGraphTest,LibraryForwardClassResolutionTest,StreamLibraryResolutionTest,StrstreamLibraryTest,LibraryUnknownEffectsTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,ObjectMemoryValidationTest,ReferenceBindingDataFlowTest,CallSiteDataFlowTest,UnreadWritesTest,ContainerFamilyFlowTest,SequenceFlowTest,ScopedResolutionTest,MacroSourceTest,CppPreprocessorTest,MbCurMaxRuntimeTest,ErrnoRuntimeTest -Dit.test=StandardStreamsRuntimeIT,PackagedCliIT,MbCurMaxRuntimeIT,ErrnoRuntimeIT,StrstreamLibraryIT,MacroEditingIT -DargLine=-Xmx768m verify
mvn -o -B -Dtest=StandardLibraryMacrosTest -DargLine=-Xmx768m test
```

Fresh local JAR SHA256:
`d0c0ec2f756785cd63149d6019a86342e8f46097a57f50ddb24bf07aab462707`.

Full Maven verification and exact-head CI remain separate merge gates. Their
final outcomes and the published revision belong to the PR verification section;
the focused results above must not be substituted for either result.

Расширенная проверка прошла: 350 unit-тестов и 18 интеграционных тестов;
отдельная проверка реестра добавила восемь unit-тестов. Итого 358 unit-тестов
в двух командах, без ошибок, провалов или пропусков. Полный Maven-набор ещё
выполняется; CI точного коммита также остаётся ожидающей проверкой.

## Remaining limits

Selected GNU pointer cells are mutable lvalues. Pointer values are statically
unknown; no constant addresses or file-descriptor numbers are promised. Canonical
FILE identity does not provide FILE layout or complete buffering, position,
error, close or external-file semantics. Opaque library effects remain bounded
and conservative, not a simulation of I/O state or a complete alias proof.

std::stdout correctly has no target, but generic unresolved ScopeMemberExpression
diagnosis is incomplete. The strict native negative case does not establish a
CPG diagnostic. No invented std alias is added to hide that limitation.

Remaining macro obligations are HUGE_VAL, CLOCKS_PER_SEC, WEOF in two headers,
SIG_DFL/SIG_ERR/SIG_IGN, offsetof, va_start/va_arg/va_end and setjmp. Neither this
packet nor the local ABI evidence closes #92 or #48.
