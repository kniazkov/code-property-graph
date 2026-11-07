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
