# C++03 requirements matrix for CPG

The tracking unit is a testable rule or closely related group. Left-hand numbers
refer to ISO/IEC 14882:2003; “Plan” identifies an [implementation-plan](CPP03-PLAN.md)
stage. This initial registry spans clauses 1–27 and annexes A–E; completeness for
every normative paragraph and library overload has not been established.

This English block migrates original lines **1–118**. The
[remaining implementation evidence](CPP03-MATRIX.ru.md) is assigned, in source
order, to [#234](https://github.com/kniazkov/code-property-graph/issues/234),
[#235](https://github.com/kniazkov/code-property-graph/issues/235),
[#236](https://github.com/kniazkov/code-property-graph/issues/236) and
[#237](https://github.com/kniazkov/code-property-graph/issues/237). Keep that source
until consolidation. Historical evidence and counts below are preserved, not
revalidated by this translation; no row or chapter gains coverage status.

Final audit #48: [acceptance status](docs/cpp03-acceptance.ru.md),
[language/clauses](docs/cpp03-language-audit.ru.md),
[mandatory headers](docs/cpp03-library-audit.ru.md). Paragraph-level coverage of
the 2003 edition remains unverified; implementations and successful general E2E
runs do not satisfy that condition.

[ISO edition record](https://www.iso.org/standard/38110.html) ·
[Sources and edition differences](docs/standards/cpp/cpp03/README.md).

Rows remain open until tested through the normal entry point. Task-specific
verified portions do not close entire normative clauses. “Baseline” denotes only
an adjacent capability in the [model guide](docs/model-guide.ru.md). Examples are
planned checks. For erroneous input, check diagnostics and graph status; for UB,
do not prescribe an execution result.

| ID / clause | Rule and consequence for CPG | Plan | Check |
|---|---|---|---|
| 1a [intro.compliance] | Distinguish diagnosable violations from rules whose detection is not guaranteed. | 0,9 | Incompatible-type redeclaration in one scope diagnoses; classify cross-file ODR violations separately. |
| 1b [intro.execution] | Respect observable effects without fixing unspecified order. | 5,9 | In `f(g(),h())`, both functions execute; CFG promises no left-to-right order. |
| 2a [lex.phases] | Line splicing precedes comment/directive recognition. | 2 | A continued `//` comment hides the next physical line; verify the following token's coordinates. |
| 2b [lex.pptoken] | First recognize preprocessing tokens according to lexical rules. | 2 | `a+++b` contains postfix `++` and binary `+`. |
| 2c [lex.literal] | Preserve spelling; derive type from base, suffix and profile. | 1,5 | `10`, `012`, `0xA` all equal 10; `10UL` has type `unsigned long`. |
| 2d [lex.string] | Concatenate adjacent strings; preserve embedded nulls. | 2,5 | `sizeof("a" "b")` is 3; `sizeof("a\0b")` is 4. |
| 3a [basic.scope] | Respect declaration points and shadowing. | 4 | In `int x=1; { int x=x; }`, the initializer references the inner declaration. |
| 3b [basic.link] | Distinguish internal-linkage entities by translation unit. | 1,4 | File-scope `static int n` in two `.cpp` files gives distinct reference targets. |
| 3c [basic.def.odr] | Distinguish redeclarations and permitted repeated definitions. | 4,6 | Identical inline functions from a shared header are allowed in two units; duplicate definition within one unit diagnoses. |
| 3d [basic.life] | Track lifetime separately from storage. | 6,9 | Link placement-new construction and explicit destruction to the same storage region. |
| 4a [conv.array], [conv.func] | Model array/function-to-pointer conversions where applicable. | 4,5 | For `int a[3]`, distinguish passing `a` to `f(int*)` from `sizeof(a)`. |
| 4b [conv.prom], [conv.integral] | Account for promotions, ranges and signedness. | 1,5 | With short=16/int=32, `short + short` has type `int`. |
| 4c [conv.qual] | Check cv-qualification levels. | 4,5 | `int*` converts to `const int*`; `int**` cannot convert to `const int**`. |
| 5a [expr.log.and] | Built-in `&&` conditionally evaluates its right operand. | 5,6,9 | `false && f()` cannot reach the call; test overloaded operators separately. |
| 5b [expr.sizeof] | Do not evaluate a `sizeof` expression operand. | 5,9 | `sizeof(i++)` preserves `i`; retain the expression without executing increment. |
| 5c [expr.new], [expr.delete] | Distinguish scalar/array forms and lifetime effects. | 5,6 | `new T[3]`/`delete[]`: three constructions, reverse destruction. |
| 5d [expr.dynamic.cast] | Distinguish failed pointer/reference casts. | 5,6,8 | With a polymorphic base, failure yields a null pointer or `bad_cast`. |
| 6a [stmt.iter] | In `for`, `continue` targets the iteration expression. | 5,9 | Compare `for`/`while` CFG with `continue`. Baseline: `while` parsing. |
| 6b [stmt.switch] | `case`/`default` are entry points with possible fall-through. | 5,9 | Check fall-through, `break`, and duplicate-value `case` diagnostics. |
| 6c [stmt.jump] | Destroy constructed objects on scope exit; validate `goto` entry. | 5,6 | Jumping past local-object initialization into its scope diagnoses. |
| 7a [dcl.typedef] | Aliases preserve type identity. | 4 | Two typedefs of `int` retain chains without creating a new overload. |
| 7b [namespace.def], [namespace.udir] | Account for reopened namespaces and using effects. | 4 | Qualified access finds declarations from both blocks; test ambiguous unqualified lookup. |
| 7c [dcl.link] | Retain declaration language linkage. | 4,6 | Match header `extern "C"` with its definition; preserve linkage on output. |
| 8a [dcl.meaning] | Parentheses change declared-type structure. | 4 | Distinguish `int *a[3]` and `int (*p)[3]` after reparsing. |
| 8b [dcl.init.aggr] | Initialize remaining aggregate elements under language rules. | 4,5 | `int a[3]={7};` gives 7,0,0; excess initializers diagnose. |
| 8c [dcl.fct.default] | Select defaults from visible declarations; evaluate at calls. | 6 | With default `next()`, `log(), log(2), log()` calls `next` twice. |
| 9a [class.mem] | Distinguish instance fields, static members and bit-fields. | 4,6 | Two objects have separate fields/one static variable; bit-field address-taking diagnoses. |
| 9b [class.union] | Union fields share storage. | 4,6,9 | Check member addresses/profile-dependent size; prescribe no arbitrary inactive-member reading result. |
| 10a [class.mi] | Distinguish repeated base subobjects/shared virtual bases. | 6 | Nonvirtual diamond conversion to the top is ambiguous; virtual inheritance gives one subobject. |
| 10b [class.virtual] | Separate static declarations from virtual targets. | 6,9 | Base-reference and qualified calls have different execution targets. |
| 11a [class.access] | Check access after declaration selection. | 6 | A private exact overload beats a public converting overload, then diagnoses. |
| 11b [class.friend] | Friendship belongs to a specific entity. | 6 | A friend accesses private fields; its friend does not automatically gain access. |
| 12a [class.base.init] | Class structure determines base/member construction order. | 6,9 | Reorder mem-initializers; events still follow field declarations. |
| 12b [class.copy] | Account for copy construction, assignment and permitted elision. | 6,9 | Distinguish `T b=a`/`b=a`; do not fix one copy count for return-by-value. |
| 13a [over.match] | Build candidates, check viability and rank conversions. | 6 | `f(int)`/`f(long)` select different targets for `f(1)`/`f(1L)`, including equal-width profiles. |
| 13b [over.oper] | Overloaded operators retain call behaviour. | 5,6 | `Money + Money` references `operator+` and preserves operator syntax. |
| 14a [temp.res] | Bind nondependent names at definition; dependent names under instantiation rules. | 7 | Retain unresolved `x.size()`; bind the appropriate member for concrete `T`. |
| 14b [temp.deduct] | Substitution failure in applicable contexts removes candidates. | 7 | Missing nested `type` excludes that parameter overload; errors inside the selected template body diagnose. |
| 14c [temp.class.spec] | Select applicable class partial specializations. | 7 | `Box<T*>`/`Box<int*>` exercise partial/explicit full specialization selection. |
| 14d [temp] | Account for C++03 exported templates/separate translation. | 1,7 | Client sees exported declaration, definition is elsewhere; check targets/substitution in CPG and separately record reference-compiler capabilities. |
| 15a [except.handle] | Select the first matching handler. | 5,6,9 | Throw derived, catch by base reference; `throw;` preserves the original exception. |
| 15b [except.ctor] | Failed construction destroys completed subobjects. | 6,9 | Second member constructor throws: destroy first, never invoke the whole object's destructor. |
| 15c [except.spec] | Account for C++03 dynamic exception specifications. | 6,9 | An external `throw()` call has no throw edge to the caller's `catch`. |
| 16a [cpp.replace] | Distinguish substitution, stringification and pasting. | 2,3 | Macro arguments differ under ordinary substitution/`#`; verify rescanning after `##`. |
| 16b [cpp.cond] | Select branches per translation-unit context. | 1–3 | Shared header, `-DDEBUG=0`: `#ifdef` true, `#if DEBUG` false. Preserve inactive text; implementation/tests are in retained #19 evidence. |
| 16c [cpp.include], [cpp.line] | Track inclusions and logical coordinates. | 1–3 | Include twice with different macros plus `#line`; check provenance, physical file and logical `__LINE__`. Retained evidence: #15/#19. |
| 17 [lib.library] | Catalogue standard declarations and argument requirements. | 8 | With only a JDK, include `<vector>`; check visibility and materialize only used declarations. |
| 18 [lib.support] | Support types, limits, RTTI, memory management and language exceptions. | 1,8 | Profile-consistent `numeric_limits<int>::max()`; link `bad_alloc` to its library type. |
| 19 [lib.diagnostics] | Distinguish standard exception types/hierarchy. | 8,9 | `out_of_range` matches `logic_error`; check linkage and `what()` for the supplied message. |
| 20 [lib.utilities] | Model pairs, function objects, allocators and `auto_ptr` ownership. | 8 | Ownership transfer releases exactly once and nulls the original pointer. |
| 21 [lib.strings] | `basic_string` length can include null characters. | 8 | `string("a\0b",3)` has `size()==3`; check `substr` and last-character access. |
| 22 [lib.locales] | Formatting depends on locale/selected facet. | 8 | Custom `numpunct`: verify decimal separator through a stream and facet linkage. |
| 23a [lib.vector] | Ordinary vectors are contiguous; `vector<bool>` needs a separate model. | 8.1 | `vector<int>` with at least two elements: check `&v[1]==&v[0]+1`; `vector<bool>`: proxy writes. |
| 23b [lib.associative.reqmts] | Comparator defines key equivalence. | 8.2 | Differently represented equivalent keys produce one `map` element, two `multimap` elements. |
| 23c [lib.list], [lib.queue] | Distinguish list transfer from adapter operations. | 8.2 | `splice` preserves element references; identical input yields different `stack`/`queue` extraction orders. |
| 24 [lib.iterators] | Iterator categories determine valid operations. | 8.2 | `advance` reaches expected list/vector elements; do not give `list::iterator` an `operator+`. |
| 25 [lib.algorithms] | Distinguish rearrangement, logical end and container resizing. | 8.2 | `remove` returns a new end; `vector` size changes only after `erase`. Do not assert tail values. |
| 26 [lib.numerics] | Accumulate in the initial-value type; model custom-operation calls. | 8.2 | Fractional elements accumulated from `0`/`0.0` yield different results. |
| 27 [lib.input.output] | Track stream state, formatting and manipulator effects. | 8,9 | Failed `stringstream` input sets `failbit`; `endl` adds newline/flushes. Baseline: limited output-pattern recognition. |

## Annexes

| Clause | Project application | Check |
|---|---|---|
| A, grammar summary | Compare handwritten parser branches with productions/contextual name resolution. | `T(a);` with `T` naming a type versus a function. |
| B, implementation limits | Catalogue CPG limits/reproducible boundary tests. | Nesting/instantiation depth: valid inputs pass; exhaustion diagnoses. |
| C, compatibility | Distinguish C/C++03 for shared headers. | Positive explicit `void*` conversion; negative implicit `void*` to `int*`. |
| D, deprecated features | Separate valid C++03 constructs from forbidden extensions. | Assign a string literal to `char*` under this edition's special rule; never write into it. |
| E, universal character names in identifiers | Validate with the selected edition's tables. | Allowed/disallowed character-code examples; diagnose in physical source. |

## Header inventory for stage 8

The historical inventory records 51 required headers from 2003 tables 11–12:
33 C++ and 18 C-prefixed headers, including deprecated `<strstream>`; it recorded
50 in CPG's catalogue. This is not a new catalogue measurement.

Each header needs separate entries for public types, functions, macros and
overloads: visibility, resolution, effects, applicable greening and test IDs.
Inclusion alone establishes availability, not semantic coverage.

| Group | Headers to review |
|---|---|
| Language support/diagnostics | `<cstddef> <limits> <new> <typeinfo> <exception> <stdexcept>` |
| Utilities | `<utility> <functional> <memory>` |
| Strings/locales | `<string> <locale>` |
| Containers/adapters | `<vector> <deque> <list> <set> <map> <stack> <queue> <bitset>` |
| Iterators/algorithms | `<iterator> <algorithm>` |
| Numerics | `<complex> <valarray> <numeric>` |
| Streams | `<iosfwd> <ios> <istream> <ostream> <iostream> <streambuf> <iomanip> <fstream> <sstream>`; separately deprecated but required `<strstream>` |
| C library | `<cassert> <cctype> <cerrno> <cfloat> <ciso646> <climits> <clocale> <cmath> <csetjmp> <csignal> <cstdarg> <cstddef> <cstdio> <cstdlib> <cstring> <ctime> <cwchar> <cwctype>`; check corresponding `.h` forms/namespace rules |

## Closing a row

1. Split groups into independent normative cases. Record exact 2003 subclause/paragraph, applicability, exceptions and related clauses. For N1638 differences, retain both references and the chosen mode rule.
2. Assign permanent example IDs; store corpus sources and expected nodes/edges/diagnostics. Identify the tested stage. For complexity/library-implementation requirements, define what CPG observes and justify the boundary.
3. Record implementation path, CI scenario and result. Explicitly classify IFNDR, UB, unspecified and implementation-defined behaviour. IFNDR means an ill-formed program with no diagnostic required; compiler disagreement alone cannot establish conformance.
4. Before closing a chapter, check both directions: each applicable requirement has a row, each closed row has reproducible evidence. The source revision completed chapter inventory, not paragraph verification.

## Historical implementation evidence

### Executable corpus baseline (#11)

`src/test/resources/cpp03/scenarios.txt` registers `return-zero` (literal return),
`arithmetic` (expressions, folding, existing print normalization) and `invalid-token`
(physical-position error; #21 added diagnostic JSON while blocking source output).
Each scenario uses API/CLI; positive original/generated programs use strict-C++03
GCC and Clang. This establishes infrastructure, not full clause coverage.
Commands/limits: [English E2E guide](docs/e2e.en.md),
[retained Russian source](docs/e2e.ru.md).

### Original SQUARE fixture (#12)

The [SQUARE corpus](src/test/resources/cpp03/square/README.md) has two translation
units, a shared macro and compilation database. GCC/Clang confirm 58 before and
64 after the reference local edit; `source-fixture` claims neither preprocessing
nor graph-editing support. Import, lexing, expansion, provenance and regeneration
remained #13–#17 work in this record.

### Input configuration (#13)

[CompilationDatabaseIT](src/test/java/com/kniazkov/cpg/e2e/CompilationDatabaseIT.java),
[unit tests](src/test/java/com/kniazkov/cpg/CompilationDatabaseTest.java) and
[corpus](src/test/resources/cpp03/compilation-database/README.md) cover default/explicit
C++03, precedence over `-std`, nonexecuting `arguments`/`command` parsing, spaces
in paths, search/`-D`/`-U` order, unsupported editions/options and lambda diagnostics.
API/CLI effective graphs match; #19 configuration provenance distinguishes their
origins. Physical-file, translation-unit and inclusion records are separate in
the input API; header/macro expansion and contextual graphs remained later stages.
