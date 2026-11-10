# C++03 standard-library declaration inventory

The built-in catalogue recognizes all 51 C++03 standard headers and 18 deprecated
C compatibility spellings. C++11, TR1 and vendor headers are excluded. The complete
available declaration dataset stays outside `Project`, in `StandardLibraryCatalogue`
and its C/stream helpers. Only demanded declarations, required dependencies and
structural owners enter a resolved snapshot. Including a header or considering an
overload is not a request to import every declaration.

This inventory separates **declaration interfaces** from **behavioral coverage**.
All callable entries are declarations without bodies. Their existence does not
claim that every possible template instantiation or overload expression is already
semantically supported, nor that an implementation strategy or ABI layout is known.
The #36 acceptance path exercises concrete vector types and demanded members;
[#37](https://github.com/kniazkov/code-property-graph/issues/37)–
[#41](https://github.com/kniazkov/code-property-graph/issues/41) add behavioral and
family-specific semantic acceptance. Source-level macros are identified separately.

## Namespace and target policy

- C++ headers expose the declared names in `std`; C compatibility headers expose
  their C names globally. The optional additional global names from `<c...>` and
  optional `std` names from `<...h>` are not promised. Global allocation functions
  from `<new>` remain global. Dependency and name indexes are deterministic.
- `size_t` uses `PlatformProfile.sizeType()`. The current `ptrdiff_t` policy chooses
  its signed counterpart (`unsigned int` → `int`, `unsigned long` → `long`). This
  explicit conventional policy is **not an ISO ABI guarantee**; the profile does
  not yet have an independent `ptrdiff_t` setting.
- Implementation-defined types use `OpaqueLibraryType`: arithmetic, integer,
  object or unspecified category, a stable identity, and unknown representation.
  This includes C handles/state/time types, stream offsets/flags, container
  iterators/size types, and unspecified manipulator/proxy results. These are not
  fake class layouts or guessed aliases to host scalar types. The internal parser
  placeholder syntax is accepted only in trusted catalogue fragments.
- Named aliases retain their declarations. Nested opaque identities include their
  owner and are specialized for concrete template instances. C and C++ spellings
  of the same C-library type can refer to the same abstract type identity.
- Public or protected members specified by the standard are interfaces, not a
  physical layout description. No container size, growth factor, allocation
  strategy, iterator invalidation, purity, exception outcome or green equivalence
  may be inferred from the catalogue. Standard class layout remains unknown.
- `vector<bool, Allocator>` has its own partial-specialization interface, nested
  reference proxy, bool `const_reference`, opaque pointers and flip/swap members.
  It is not silently represented as primary-template `bool&` element access.

## Header/family matrix

Every declaration row below has **no behavior summary in this catalogue**. Resolved
ordinary C++ calls retain conservative unknown effects. Existing console
normalization is a separate pass, not an effect summary inferred from declarations.
“Header only” refers to a macro-only header, not successful macro expansion.

| Headers | Declaration interfaces | Semantic/behavior follow-up |
|---|---|---|
| `vector` | Primary and bool specialization; constructors, assign/access/capacity/iteration/modifiers; comparisons and swap | #37 common construction/copy/append; access/capacity/iterators #38; proxy and allocator operations remain resolved C++ |
| `deque`, `list` | Constructors/assignment/allocator, forward/reverse iteration, access/capacity/modifiers, comparisons/swap; list splice/remove/unique/merge/sort/reverse | #39 |
| `map`, `set` | Both unique and multi variants, value/key/allocator/comparator types, construction, insertion/erasure, lookup/bounds/equal_range, observers and nonmembers | #39; unique/multi insertion results differ |
| `queue`, `stack` | queue, priority_queue, stack; typedefs/defaults, protected adapters, constructors, access/push/pop; queue/stack comparisons | #39; no priority_queue equality or C++11 adapter swap |
| `string` | char_traits and char/wchar specializations; basic_string constructors, assignment, access/iteration/capacity, append/assign/insert/erase/replace, searches/substr/compare, concatenation/comparison/swap, stream extraction/insertion/getline | #40 |
| `iterator` | Category tags, iterator/traits with pointer specializations, reverse/insertion/stream adapters, advance/distance and helper functions | #40–41; opaque istreambuf postfix result rather than a fictitious public proxy name |
| `algorithm` | C++03 search/count/mismatch/equal; copy/swap/transform; replace/fill/generate/remove/unique; reverse/rotate/shuffle; partition/sort/bounds/merge/set/heap; min/max and permutations, with predicate/comparator overloads | #40; no C++11 additions |
| `numeric` | Both overloads of accumulate, inner_product, partial_sum and adjacent_difference | #40 |
| `functional` | unary/binary_function, arithmetic/comparison/logical functors with C++03 bases, negators, binders, function-pointer and const/nonconst member-pointer adapters | #40 |
| `utility` | pair with public types/data/constructors, six comparisons and make_pair | #41; generic swap belongs to algorithm in C++03 |
| `memory` | allocator and void specialization/rebind/comparisons, auto_ptr conversions, raw_storage_iterator, temporary buffers, uninitialized algorithms | #41; no allocation/lifetime summaries |
| `exception` | exception/bad_exception, copy/assignment/what, terminate/unexpected handlers and uncaught_exception | #41 |
| `new` | bad_alloc/nothrow/new_handler; 12 global allocation/deallocation overloads, including nothrow and placement forms | #41 |
| `stdexcept` | Nine exception classes, hierarchy, string constructors and root what overrides | #41 |
| `typeinfo` | type_info comparisons/before/name and inaccessible copy operations, bad_cast/bad_typeid interfaces | #41 |
| `limits` | numeric_limits member interface, 14 C++03 scalar specializations, float_round_style/float_denorm_style | #41; target-specific constants are not invented |
| `bitset` | Reference proxy, scalar/string construction, bit/shift operators, access/tests/count/conversions and stream operators | #41; C++03 to_string template parameters remain explicit |
| `complex` | Primary plus float/double/long-double specializations, scalar/complex arithmetic and comparisons, values/polar/transcendentals and stream operators | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `valarray` | valarray, slice/gslice, four proxy families, access/subsets/assignments/reductions/shift/apply/resize, arithmetic/logical/comparison and math nonmembers | #41; expression-template implementation choices are not represented as concrete layouts |
| `iosfwd` | Forward stream templates, character aliases, fpos and abstract stream offset/position types | #41; string streams and stringbuf retain Allocator parameter |
| `ios` | ios_base flags/state/callbacks/storage/locale/Init/failure, basic_ios, manipulators | #41 |
| `iostream` | Eight standard narrow/wide stream objects | #41 |
| `istream`, `ostream` | Stream construction, sentries, formatted/unformatted operations, positioning, character/pointer/operator and manipulator overloads, basic_iostream | #41 |
| `streambuf` | Buffer public entry points and protected virtual hooks | #41 |
| `fstream` | Filebuf and file stream classes, open/close/is_open/rdbuf and protected buffer hooks | #41 |
| `sstream` | Stringbuf and string stream classes, construction/str/rdbuf and protected buffer hooks | #41 |
| `iomanip` | Flag/base/fill/precision/width manipulators with abstract result types | #41 |
| `locale` | locale/facet/id/category, facet access, classification, ctype and char specialization, codecvt, numeric/collation/time/money/message facets and byname variants | #41 |
| `cstddef`, `stddef.h` | size_t/ptrdiff_t under explicit target policy | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cstdio`, `stdio.h` | FILE/fpos_t, formatted/character/file/block I/O, positioning/status and file management, including va_list forms | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cstdlib`, `stdlib.h` | div_t/ldiv_t, allocation, process/environment, conversions, abs/div, random, search/sort and multibyte conversions | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cstring`, `string.h` | Memory/string copy/concatenation/comparison/search/tokenization/transformation, const/mutable search overloads | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cctype`, `ctype.h` | All 13 narrow classification/case functions | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cmath`, `math.h` | C++03 floating overload families, decomposition/scaling and pow | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `clocale`, `locale.h` | lconv public interface, setlocale/localeconv | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `ctime`, `time.h` | Abstract clock_t/time_t, tm fields, clock/time/difftime/mktime, text/calendar conversions and strftime | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cwchar`, `wchar.h` | Abstract mbstate_t/wint_t, wide I/O, string/memory operations, classification bridges, number/multibyte conversions and wcsftime | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cwctype`, `wctype.h` | Abstract wide types, classification/descriptors and transforms | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `csignal`, `signal.h` | Abstract sig_atomic_t, signal/raise | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `csetjmp`, `setjmp.h` | Abstract jmp_buf and longjmp | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cstdarg`, `stdarg.h` | Abstract va_list | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cassert`, `assert.h` | assert macro; no standard callable declaration. Explicit glibc binding may supply its real global helper | #92: disabled mode is runtime independent; enabled mode requires the selected runtime binding |
| `cerrno`, `errno.h` | Header only | Target macros require definitions; resolved calls retain conservative effects; see #41 evidence below |
| `cfloat`, `float.h` | No callable declarations | #92 supplies 29 macros from explicit target formats |
| `climits`, `limits.h` | No callable declarations | #92 supplies 16 macros from explicit target facts |
| `ciso646`, `iso646.h` | Header only; alternative operator tokens are a language feature | No separate C++ declaration family |

## Reproducible checks

`mvn -Dtest=StandardLibraryCatalogueTest test` parses all 69 fragments with their
dependencies for two size-type policies, verifies exact namespace type/template
indexes and declared nested indexes, checks stream arities, bool proxy/opaque
choices, global allocation overloads and representative member families, and
rejects bodies. The deliberately nonexistent source directory and
`Parser.parseLibrary` require no system C++ headers, compiler process or STL.

`bash scripts/probe-cpp03-library-catalogue.sh` independently checks representative
real-STL signatures with GCC and Clang in strict C++03 mode. Inputs live under
`src/test/resources/library-catalogue/`. They do not pretend to implement the STL
and are never read during ordinary analysis. Native signature probes and the
lazy-library round-trip E2E complement, rather than replace, declaration parsing.
Passing a fragment parse alone does not prove complete semantic support for all
expressions using that fragment.

Source basis: C++03 library synopsis, clauses 18–27 and Annex D, with applicable
library defect corrections; [repository reference](standards/cpp/cpp03/README.ru.md).

The #39 common family model distinguishes deque/list, associative uniqueness and
adapter ordering, with selected comparator links and scoped splice transfer.
See model-guide.en.md for conservative effects and iterator-category boundaries.

The #40 common sequence model covers explicit string lengths, access/append/find/
substr, iterator movement/output destinations, copy/find_if/transform/sort/remove
and accumulate. This is distinct from the broader declaration catalogue above:
other declared overloads remain resolved C++ unless their common equivalence is
proved. User traits, allocators, specializations and unsupported lifetime cases
retain conservative behavior; see model-guide.en.md and the native corpus.

## Remaining-family verification (#41)

Four kinds of evidence are kept separate:

1. `src/test/resources/library-catalogue/cpp03-remaining-overloads.tsv` records
   audited declaration groups and overload counts. `RemainingLibraryOverloadsTest`
   checks the parsed catalogue against that inventory. This is an audited matrix,
   not a claim that every possible standard-library expression is modeled.
2. `remaining-cpp-signatures.cpp` and the C compatibility signature sources are
   compiled with real GCC and Clang in strict C++03. They check signatures without
   executing allocation, process, signal, file or nonlocal-jump operations.
3. `RemainingLibraryResolutionTest`, `CCompatibilityFamiliesTest` and
   `StreamLibraryResolutionTest` check selected CPG declarations, template
   specializations and overload distinctions. Native compilation alone does not
   establish CPG resolution.
4. `LibraryUnknownEffectsTest` and the `remaining-library` native corpus check
   conservative effects and observable stream values/state. String streams stay
   ordinary C++ streams; they are not converted into console-print nodes.

The matrix covers utility/memory/functional adapters; exception/new/stdexcept/
typeinfo; numeric limits, bitset, complex and valarray; locales/facets; stream
families and I/O iterators; and all 18 C-prefixed/compatibility-header pairs.
Known library declarations have no invented bodies. Unproven behavior remains
an ordinary resolved call with possible reads/writes to accessible storage,
including backing streams and returned reference/pointer aliases. Time, locale,
I/O, process and signal behavior is not simulated. Numeric limit values and
implementation layouts are not guessed.

Macro-only header recognition is a separate boundary: the portable catalogue
does not supply target-specific definitions for EOF, errno, clock scales, signal
numbers, `setjmp` or `va_*`. Numeric limits and NULL are supplied separately by the profile-backed preprocessor described below. Without target definitions those names
remain unresolved; real-header presence checks do not imply macro evaluation in
the graph. See `src/test/resources/library-catalogue/c-compatibility-coverage.md`
for namespace and compiler-specific probe qualifications. Alternative tokens in
`ciso646` are C++ language tokens, not missing function declarations.


## Deprecated strstream declarations (#90)

The required `<strstream>` header contributes `strstreambuf`, `istrstream`,
`ostrstream`, and `strstream`, with 39 explicit callable declarations and four
`strstream` typedefs. The independent D.7 coordinate fixture checks access,
explicit/virtual/const qualifiers, defaults, callbacks, bases and types. Complete
stream fragments index their declared nested aliases; forward-only `iosfwd` does
not invent these members.

Demanded concrete classes instantiate their stream template bases. Actual member
receiver types also create this demand, including returned buffers and used
function/class-template bodies. Include-only and unused member bodies do not
materialize unused bases. The tests check instantiated targets rather than only
a successful lookup, with copy/re-resolution and both size-type profiles.

These remain declaration-only interfaces. `str()` and `freeze()` have conservative
receiver-write effects, not inferred allocation/ownership/frozen-state/lifetime
semantics. Native fixed-buffer and dynamic freeze/unfreeze examples use the
compiler's STL. They demonstrate those particular executions only.
[Verification and source decisions](cpp03-strstream-verification.md).


## Opt-in relational operators (#91)

`<utility>` declares the four C++03 `std::rel_ops` templates (`!=`, `>`, `<=`,
`>=`) in their nested namespace. Parser callable indexes expose that namespace
without importing it into `std` or global lookup. Qualified calls, namespace
aliases, using-directives and operator using-declarations are covered. Only
demanded templates enter the graph; target ownership survives copying and
resolution.

Ordinary non-member overloaded-operator templates (excluding allocation and
deallocation operators) require a class or enum operand; known
builtin-only specializations are not callable. Unknown object/unspecified library
representations remain conservative. This is distinct from the library's
EqualityComparable/LessThanComparable prerequisites, which declaration-only CPG
does not establish. Calls retain unknown effects rather than invented STL bodies
or pure-comparison folding. [Evidence and limits](cpp03-rel-ops-verification.md).

## Profile-backed library macros (#92, first stage)

The preprocessor now supplies CPG-owned `NULL` in its seven required header
families, all 16 `climits` macros, all 29 `cfloat` macros, and `WCHAR_MIN/MAX` in
`cwchar`. Both C++ and old C spellings work. These 54 header/name obligations
(48 distinct names) are separate from the declaration catalogue. An explicitly
provided physical header takes precedence over the built-in model.

Definitions use the selected target profile, not Java numeric constants or hidden
host headers. Integer suffixes preserve promoted types; floating formats are
explicit choices separate from storage widths. Unknown required floating facts
produce a contextual diagnostic rather than guessed values. Macro definitions,
expansions and profile provenance remain in the graph without emitting synthetic
headers as user source files.

The [complete inventory](cpp03-library-macros.json) records 102 mandatory
header/name obligations (95 names), source coordinates and per-entry scope.
The other 48 obligations remain open under #92: assert/NDEBUG, runtime bindings
and target/ABI macros are not supplied by this stage. #92 and general acceptance
#48 stay open. [Verification and boundaries](cpp03-library-macros-verification.md).

## Explicit assertion runtime (#92, second stage)

`assert` remains a function-like macro, not std::assert or a fabricated function.
Every cassert/assert.h inclusion samples the presence of NDEBUG, even NDEBUG=0.
Disabled mode discards the unused argument without prescan or evaluation. Enabled
mode uses the explicit `cRuntime: "glibc"` family binding and a real global
C-linkage __assert_fail declaration. An unknown runtime records an unavailable
macro definition and diagnoses only its invocation, not an unused include.

This adds one bounded runtime-binding obligation to the numeric/null packet:
55 supplied header/name obligations, 47 still open under #92. Other libc/ABI
macros are not implied by selecting glibc. Copying and unrelated graph edits are
covered; editing inside an assertion expansion is deliberately rejected because
its condition and diagnostic string must be re-derived together. Edit the source
argument and reparse instead. [Evidence and limits](cpp03-assert-verification.md).

## Explicit runtime integer facts (#92, third stage)

Thirty further object-like macros now accept typed int facts from
`runtimeIntegerMacros`: error selectors, locale categories, signal numbers,
stdio constants and EXIT_*/RAND_MAX. Both header spellings, preprocessing #if,
ordinary typed expressions and provenance use the same explicit values. An
omitted fact stays unavailable and is diagnosed on expansion; cRuntime alone
never chooses its value. This brings the bounded inventory to 85 header/name
obligations (79 names), with 17 obligations (16 names) still open under #92.
The count does not mean that every profile supplies every value, or that numeric
macros implement the related runtime functions. [Checks and remaining work](cpp03-runtime-integer-macros-verification.md).

## Mutable errno runtime binding (#92, fourth stage)

With explicit cRuntime=glibc, cerrno/errno.h defines errno as
`(*::__errno_location())` and supplies the actual global C-linkage int-pointer
accessor with throw(). The result is a mutable int lvalue, not an invented global
errno object or constant. Numeric error selectors still require their separate
explicit target facts. Unknown runtime leaves a recorded unavailable macro and
diagnoses use. The inventory now has 86 bounded obligations (80 names), with
16 obligations (15 names) still open under #92. [Verification and analysis limits](cpp03-errno-verification.md).

## Live MB_CUR_MAX query (#92, fifth stage)

The preceding follow-ups describe their respective stages. At stage five the inventory
added one bounded locale-query binding: 87 obligations/81 names implemented within
explicit limits, 15 obligations/14 names still open. MB_CUR_MAX under cstdlib and
stdlib.h requires `mbCurMaxBinding: "glibc-x86_64-lp64-mb16"`, alongside glibc,
8-bit bytes, 32-bit int, 64-bit long/pointers, exact unsigned-long size_t and
MB_LEN_MAX16. Naming glibc alone does not enable this query.

The object macro calls the genuine global C-linkage accessor with throw(), while
ordinary cstdlib declarations remain available. Its scalar rvalue is unknown to
static evaluation and observes the current locale at runtime. Missing capability
permits unused headers and diagnoses expansion. This caller-selected deployment
contract is not automatic ABI detection or complete locale semantics. Original
C90 requires a positive integer expression; exact size_t is the selected glibc
signature. [Verification and limits](cpp03-mb-cur-max-verification.md).

## Standard-stream objects and macros (#92, sixth stage)

At stage six the inventory had 90 bounded obligations/84 names and 12 open
obligations/11 names. `standardStreamsBinding: "glibc-x86_64-lp64"` enables
stdin/stdout/stderr under cstdio/stdio.h. It defaults to unavailable, independently
of cRuntime. Selection requires glibc, byte8/int32/long64/pointer64 and exact
unsigned-long size_t; there is no multibyte-bound requirement.

Exact self-referential macros resolve genuine global C-linkage mutable FILE*
objects. They reuse canonical library::FILE without a layout, initializer or
invented std alias. Compatible trusted declaration occurrences share external
identity while retaining header provenance. User/local same names are not merged.
C90 requires FILE-pointer expressions; mutable cells belong to this glibc binding.
The target ABI and deployed exports must match. Complete I/O semantics and generic
unresolved-qualified-name diagnostics remain open. [Executed checks and pending
merge gates](cpp03-standard-streams-verification.md). #92 and #48 remain open.

## Configurable wint_t and WEOF (#92, seventh stage)

The coupled `wideInteger` object supplies `wintType`, decimal-string `weof` and
`characterDomain: "nonnegative-wchar"`. Missing or empty settings retain opaque
wint_t and unavailable WEOF; actual headers remain authoritative. The supported
types are int, unsigned int, long and unsigned long. Both wide header families,
wide signatures and char_traits use the configured canonical type. No host or
libc-family fact is inferred.

The selected type must cover the entire nonnegative wchar_t range; WEOF must
fit that type and lie outside that envelope. This deliberately stronger policy
is not ISO's character-repertoire requirement. Signed -1 is permitted, including
when wchar_t can represent it; WEOF need not equal EOF. Type-preserving constant
expressions support #if. Synthetic profiles do not authorize mismatched native
wide-library calls or claim a complete repertoire/locale model.

At stage seven the bounded inventory had 92 obligations/85 names, with 10 obligations/10
names open. Focused implementation checks support the two WEOF entries; all four WEOF native cases passed. Expanded
regression, full-suite and exact-head CI results are separately tracked
in the [verification report](cpp03-weof-verification.md). Prior stage counts and
reports remain historical. #92 and #48 remain open.

## Clock-scale target facts (#92, eighth stage)

Optional `clockScale` couples independent `clockType`, `expressionType` and
positive decimal-string `value` facts. Each type supports int, unsigned int,
long or unsigned long; value must fit expressionType, not necessarily clockType.
Missing/empty settings retain opaque clock_t and unavailable CLOCKS_PER_SEC.
Actual headers remain authoritative; no libc/host/JVM facts are inferred.

Original C90 describes arithmetic clock_t and a scale giving the number per
second of clock() values. It does not impose later C99 exact-clock_t or constant
wording. The chosen typed-literal subset supports #if without making that a
universal historical guarantee. clock() remains an opaque runtime call.
At stage eight the inventory had 93 bounded obligations/86 names and nine open obligations/
nine names. Expanded focused acceptance passed 269 units and 28 integration tests; full verification
and exact-head CI remain separately tracked in the [report](cpp03-clock-scale-verification.md).
Prior stage counts remain historical. #92 and #48 remain open.

## Explicit HUGE_VAL compiler capability (#92, ninth stage)

`hugeVal: {kind: "positive_infinity", emission: "gcc-clang-builtin-huge-val"}`
selects a closed double-positive-infinity expression, with explicit binary64
double format and 64-bit storage. Missing/empty settings remain unavailable;
libc, host or width alone never enable it. Actual headers retain authority.
The selected exact zero-argument builtin also reparses directly under this
capability. Ordinary cmath declarations remain unchanged.

CompilerIntrinsicExpression carries symbolic double/rvalue semantics and JSON,
not a fabricated libc call, numeric JSON infinity or finite BigDecimal surrogate.
No general floating evaluator, finite HUGE_VAL policy or complete IEEE arithmetic
is supplied. Original C90 permits infinity without requiring constant form.
Assignment/increment and bare-name/declaration/qualified misuse are rejected;
address of the evaluated rvalue still has the existing generic CPG diagnostic
gap despite strict native rejection. Integer #if arithmetic is not promised.

Expanded verification passed 432 units and 31 IT, plus eight separate inventory
units (440 distinct units). At stage nine the inventory had 94 bounded
obligations/87 names and eight open obligations/eight names. Full/CI
results remain separate gates in the [report](cpp03-huge-val-verification.md).
Earlier counts remain historical; #92 and #48 remain open.

## Explicit signal dispositions (#92, tenth stage)

`signalDispositionBinding: "glibc-x86_64-lp64"` selects SIG_DFL/SIG_IGN/SIG_ERR
as explicit void(*)(int) casts of 0/1/-1. The unavailable default is independent
of cRuntime. Selection requires glibc and 8-bit bytes / 32-bit int / 64-bit long and pointers, without
sizeType or MB_LEN_MAX coupling. Actual headers retain authority. These pointer
sentinels are separate from positive-int signal-number facts; no fake helper,
integer pointer evaluation or #if guarantee is introduced.

Expanded verification passed 313 units and 22 integration tests, including actual
CPG materialization and safe subprocess save/change/restore with no delivery.
Host typedef-cast SIG_ERR incidentally passes the tested #if; all selected
explicit function-pointer casts reject it. No preprocessing equivalence is claimed.
FunctionType language-linkage identity and catalogue signal C++ linkage remain
open precision gaps; no complete ISO callback or asynchronous runtime model is
claimed. The inventory has 97 bounded obligations/90 names, five open obligations/
five names. [Evidence and pending merge gates](cpp03-signal-dispositions-verification.md).
Prior counts remain historical; #92 and #48 remain open.

## POD/member/layout preparation for offsetof (#92)

Package-private PodLayout now queries conservative POD eligibility, member paths
and union-aware recursive layout under existing explicit target policies. It
returns a justified result or typed failure, not a guessed host offset. The
query is separate from generic aggregate classification and does not broaden
unrelated sizeof behavior. Enum/member-pointer/template layouts and bounds
requiring sizeof(union) remain unsupported; valid C++03 cases outside the subset
are not language errors.

Results exceeding explicit size_t representability return SIZE_NOT_REPRESENTABLE.
This necessary bound does not certify compiler maximum object sizes.
Focused checks passed 226 units and five IT, including an independent native
layout oracle and meaningful field edits. Native host offsetof is not CPG macro
support. No new macro, builtin, parser or node is enabled; offsetof remains open
and unavailable. Inventory totals stay 97 bounded obligations/90 names and five
open obligations/five names. [Evidence and remaining gates](cpp03-offset-layout-verification.md).
#92 and #48 remain open; previous packet reports retain their historical scope.

## Selected offsetof builtin subset (#92, still open)

The explicit `offsetof` object selects emission gcc-clang-builtin, layout
x86_64-sysv-lp64-v1 and a positive decimal supportedObjectBytes ceiling no larger
than 2147483647. Missing/empty settings remain unavailable. Exact scalar sizes,
alignments, size_t and natural-layout policies are checked independently of libc
and floating formats. The ceiling applies to relevant dependencies and describes
a software-supported subset, not a universal compiler maximum.

OffsetofExpression owns type/member/index operands. Snapshot-owned ICE queries
compute validated values while folding preserves the whole expression and its
transitive layout bounds/const initializers for edits. Selected capability use
refuses active pragmas and expanded pragma operators except exact pragma once;
inactive directives remain allowed. Non-POD sizeof dependencies are unsupported
subset cases rather than invalid owners. No replay guard is weakened.

Focused verification passed 325 units and 15 IT; the final eight-unit/two-IT
rerun overlaps those counts. Native checks use actual CPG materialization, not
host macro presence alone. Valid POD enum/member-pointer/template and
sizeof-union cases remain excluded, so offsetof remains open/partial. Counts
stay 97 bounded obligations/90 names and five open obligations/five names.
The preceding preparation section is historical. [Evidence and remaining gates](cpp03-offsetof-builtin-verification.md).
#92/#48 remain open; full Maven and exact-head CI are separate gates.


## Bounded offsetof contexts (#92, partial)

The current packet extends the selected query to already materialized, complete
concrete POD template instances and query-local sizeof dependencies, including
unions. Primary, explicit and partial specialization evidence concerns the
actual selected concrete instances, not general template support. The existing
explicit layout capability and ceiling are unchanged; unrelated generic sizeof
and ABI claims are not broadened.

The implementation preserves written type/non-type arguments, defaults and relevant
specialization-header dependencies through folding so edits can reselect an
instance and recheck its budget. Snapshot ownership, cycle and ceiling checks,
pragma/operator restrictions and source replay remain in force. Named non-type
arguments and dependent defaults that the generic instantiator cannot
materialize remain prerequisite limitations. Non-POD sizeof dependencies remain
valid unsupported cases, not blanket language errors.

The corrected snapshot passed all 1411 unit tests and 13 selected IT in 8:50.
Independent review passed 78 affected units and two native IT; these overlap
the clean run. Full Maven and exact-head CI remain separate pending gates. Identity-preserving syntax
copy fixes template-argument typedef ownership; derived template instances
retain caller argument aliases, excluding primary-template/catalogue
placeholders and aliases with canonical OpaqueLibraryType targets. Dependent
or unresolved typedef target chains remain unsupported.
Array typedef arguments are no longer excluded.
Qualified member aliases with dependent targets and function-pointer template
arguments retain existing generic limitations; resolved namespace aliases pass. Independent checks are not added to expanded-suite counts.
offsetof stays open/partial, with 97 bounded obligations / 90 names and five
open obligations / five names. The preceding builtin section is historical.
[Current evidence and gates](cpp03-offsetof-contexts-verification.md).
#92 and #48 remain open.


## Selected member-pointer storage for offsetof (#92, partial)

The optional memberPointerStorage="itanium-x86_64-lp64-v1" setting independently
selects data-member pointer storage 8/8 bytes and member-function pointer storage
16/8 bytes (size/alignment). A pointer to a function-pointer data member uses
8/8. Omission remains unsupported; ordinary-pointer width, glibc and host
properties do not enable it. No member-pointer value, encoding or call is modeled.

Known incomplete, non-POD or oversized owner bodies and parameter/return class
bodies need not be laid out or fit the query budget. Written type/signature and
template syntax must still resolve; its sizeof/constant dependencies remain
bounded and survive folding. An edit crossing the ceiling is rejected. Owner
identity alone is not unconditional acceptance. Enum storage and bitfields
remain outside this packet; original C++03 enum semantics are unchanged.

The clean gate passed all 1422 units and 15 explicitly selected IT in 09:54,
with zero failures, errors or skips, including 44 fresh native records.
Independent 11-unit/two-IT checks overlap and are not summed. Full Maven and
exact-head CI remain separate pending gates. offsetof remains open/partial; totals stay
97 bounded obligations / 90 names and five open obligations / five names.
[Current evidence and gates](cpp03-member-pointer-layout-verification.md).
Previous sections are historical stages. #92 and #48 remain open.


## Bounded enum storage for offsetof (#92, partial)

Independent enumStorage="gcc-clang-x86_64-default-enum-v1" selects bounded
4/4 or 8/8 byte representation after original range-admissibility checks.
Omission stays unsupported; member-pointer selection does not enable enums.
Original full-underlying-range promotion and the mixed-range restriction stay
unchanged. Modern compiler behavior is comparison evidence, not a new rule.

Pure storage and actual arithmetic/type dependencies are separate: the latter
require original/native promotion agreement. Lexical initializer types and
conditional enum identity survive bounded evaluation; folding retains actual
type dependencies without evaluating runtime initializers. Generic type
semantics are unchanged. Bitfields and residual prerequisites remain outside.

The clean gate passed all 1439 units and 22 selected IT in 19:34, with zero
failures, errors or skips. Independent 79 assertions and 68 JUnit tests overlap
and are not summed. Fresh native evidence covers 82 executions and 40 CPG
materializations. Full Maven and exact-head CI remain separate pending gates.
Counts stay 97 bounded obligations / 90 names and five open obligations / five
names. offsetof remains open/partial; #92 and #48 remain open. Previous sections
are historical. [Current evidence](cpp03-enum-storage-verification.md).


## Named integral template arguments for offsetof (#92, partial)

Historical PR #125 contract; the following section updates the bounded partial-pattern subset.

Resolved const integral names and enum enumerators can supply representable
integral class-template arguments and full-specialization values. Snapshot-owned
facts preserve the declared parameter type; bool conversion remains a truth
conversion. Written arguments and initializer chains remain available for
bounded layout checks and later edits. No new ABI policy is selected.

Nonrepresentable conversions, uncertified specialization patterns and remaining
template prerequisites are explicit unsupported boundaries, not claims that
all excluded programs violate original C++03. Original enum promotion, selected
layout ceilings and optional representation policies remain unchanged. This
package leaves the five outstanding macro obligations incomplete and does not
increase the completed macro counts. [Scope and verification](cpp03-constant-template-arguments-verification.md).


## Named integral partial-specialization patterns (#92, partial)

Top-level nondependent integral constants and enumerators in partial-specialization
patterns use snapshot facts for matching, ordering and redeclaration identity.
Written headers and initializer chains remain dependencies, including unselected
candidates, so edits can reselect after copying or folding. Pointer/reference
identity matching is unchanged. Nested named patterns, dependent parameter types
and signed out-of-range conversions remain uncertified.
The follow-up bool subset maps zero to false and every nonzero integral value
to true, consistently for matching, ordering and redeclaration identity.
Written facts survive copying, folding and edits.
[Boolean conversion evidence and gates](cpp03-boolean-partial-arguments-verification.md).
No profile, ABI, macro inventory count or acceptance status is broadened.
[Current evidence and gates](cpp03-partial-constant-template-arguments-verification.md).
Earlier sections describe historical package checkpoints. #92 and #48 remain open.

### Unsigned template argument conversions (#92, bounded follow-up)

Certified integral facts now convert to unsigned parameters modulo 2^N using
the selected profile width. The same conversion serves ordinary arguments,
defaults, full specializations and top-level partial patterns, including
matching, ordering and repeated declarations. Bool retains its zero/nonzero
rule. Signed out-of-range results, nested named patterns and dependent partial
parameter types remain unsupported. Written initializer/header dependencies,
cycle/ownership checks, layout ceilings and enum compatibility are unchanged.
No ABI policy or macro completion count changes; #92 and #48 remain open.
[Current scope and gates](cpp03-unsigned-template-arguments-verification.md).

### Concrete partial-specialization parameter types (#92, bounded follow-up)

A top-level non-type pattern parameter may now use an integral type made concrete
by the specialization's own preceding type arguments, for example
`template<class T, T N, class U>` specialized as `<unsigned char, fixed, U>`.
The same snapshot conversion serves matching, ordering and redeclaration
identity. Use-site actual types never certify a still-dependent pattern.
Written facts, typed bodies, pointer/reference identity, original enum semantics
and layout/ownership/cycle guards remain unchanged. Nested named patterns,
still-dependent parameter types and signed out-of-range conversions remain
unsupported. This does not add an ABI policy or complete another macro; #92 and
#48 remain open. [Scope and gates](cpp03-concrete-partial-parameter-types-verification.md).

### Nested integral partial-pattern values (#92, bounded follow-up)

Named independent integral values are normalized inside explicitly written
nested class-template-id arguments of a partial specialization, such as
`Box<Key<T, fixed> >`. The nested head must bind to a primary class template
(or a verified instance origin); matching a spelling alone is insufficient.
A value parameter's integral type must be concrete from that nested pattern's
own preceding arguments. Actual use-site types never certify a dependent
pattern. Normalization retains declaration/parameter/typedef identities and
uses the existing bool, unsigned-modulo and representable-signed rules.
Written headers and initializer chains remain dependencies after copying,
folding and edits. Unresolved/dependent/template-template heads, omitted nested
defaults, still-dependent value parameter types, arithmetic deduction and signed
out-of-range conversions remain outside this subset. Pointer/reference identity,
original enum compatibility and layout/cycle/ownership ceilings are unchanged.
Earlier nested-pattern exclusions describe historical checkpoints. No ABI policy
or macro completion count changes; #92 and #48 remain open.
[Scope, tests and gates](cpp03-nested-partial-arguments-verification.md).

### Nested actual integral defaults (#92, bounded follow-up)

Nested actual template-ids now complete trailing independent integral value
defaults from their exact bound primary header visible before use, or verified
instance origin. Matching and outer instance identity share the same effective
arguments while written syntax and default dependencies remain intact.
Literal/const/enumerator defaults support bool, unsigned modulo and representable
signed conversions; a type may become concrete from its own explicit preceding
arguments. Partial patterns with omitted nested defaults, merged/later defaults,
dependent/type/template defaults and unsupported heads stay guarded.
Earlier omitted-default exclusions describe historical checkpoints.
No ABI policy, macro count, section 11 audit or completion status changes;
#92/#48 and the five remaining macro obligations stay open.
[Contract, assertions and separate gates](cpp03-nested-actual-defaults-verification.md).
