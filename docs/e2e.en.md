# Reproducible end-to-end tests

Run `mvn verify` with JDK 21, GCC (`g++`), Clang (`clang++`) and Graphviz (`dot`) on PATH. Graphviz is needed during `mvn verify` for actual diagnostic SVG acceptance, not only for the later demo script. Failsafe runs the packaged JAR, including the existing Java/C++ translation regressions. C++03 acceptance does not require expanding translation to Java.

## C++03 corpus

The inventory is `src/test/resources/cpp03/scenarios.txt`. Every entry names a directory containing ordinary files under `sources/` and a `scenario.properties` expectation. `Cpp03CorpusIT` loads those exact files through the public API and the CLI. Positive cases use strict `Cpg.read`; negative C++03 cases verify its exception separately from the CLI diagnostic snapshot, with dedicated tests also checking `Cpg.readRecovering`. The compilation-database recovery fixture uses the corresponding database entry points. `CliSandbox.directory` preserves nested relative paths and headers. Translation units come from the folder or explicit compilation database; custom headers are recursively preprocessed.

| Scenario | Expectation | Failsafe test |
| --- | --- | --- |
| return-zero | exit 0, empty output, return of integer zero | Cpp03CorpusIT#corpus |
| arithmetic | exit 0, output `40` followed by newline, folded print node | Cpp03CorpusIT#corpus |
| invalid-token | CLI exit 1, `main.cpp:2:5: Unsupported character: @`, strict API exception, recovering API/CLI diagnostic JSON, no generated sources | Cpp03CorpusIT#corpus |

Repeat one scenario:

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=arithmetic verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage folded \
  --input src/test/resources/cpp03/arithmetic/sources --out target/manual-cpp --json target/manual.json
```

Use `resolved` for the other two initial scenarios. `invalid-token` intentionally fails. For positive cases, both compilers build original and generated sources with `-std=c++03 -pedantic-errors`. Every executable's exit code and exact output are checked against independent expectations. Failure messages include compiler version and command. A missing compiler is a failure, not an assumption/skip; `missingCompilerIsAnError` tests that contract.

CI runs `python3 scripts/check-cpp03-reports.py` after the full `mvn verify`. It requires one result per inventory entry plus the two harness tests and rejects failures, errors or skips. `inventoryAndNestedCopy` also rejects duplicate entries or unlisted scenario directories. Do not use the report guard after an intentionally filtered local run.

When adding language support, reuse existing nodes first. Add minimal positive, negative and interaction scenarios and update the standard matrix and RU/EN model guides in the same PR. Give each scenario explicit diagnostics or graph expectations and independent output/exit expectations. Undefined or implementation-dependent behavior is not an execution oracle. Editing scenarios must supply separate before/after expectations when introduced. New nodes need builders, visitors, ranges, exports and generation tests.


`compilation-database` is the fifth registered scenario. It uses a mixed C++03/C++11 database with an explicit C++03 override and requires full API/CLI parity and both native compilers. `CompilationDatabaseIT` also checks default-vs-explicit folder equivalence, rejection without the override, lambda rejection despite the override, unsupported-option filenames, and a manually constructed configuration with ordered spaced paths and define/undefine operations. Run `mvn -Dit.test=CompilationDatabaseIT verify`; [fixture details](../src/test/resources/cpp03/compilation-database/README.md).

`lexical-phases` and `unterminated-literal` extend the mandatory corpus to seven scenarios. `LexicalPhasesIT` inspects per-character physical mapping, trigraph ranges, alternative spelling and full API/CLI graph parity using the same source. The positive source executes through both strict compilers; the negative source reports physical 2:5, produces requested diagnostic JSON and blocks source output (#21). See each fixture README for commands. CI runs once per PR open/update/reopen, and on pushes to `master` (including merges); branch pushes no longer duplicate PR runs. Manual dispatch remains available.

`macros` and `missing-header` bring the mandatory inventory to nine scenarios. `square` now uses compilation-database mode with full API/CLI graph parity, two expanded multiplications, and native original/generated output 58. The actual graph edit and the independent hand-written oracle both produce 64 (#17). `macros` covers a macro-generated include name, guards, stringization, pasting, nested arguments, recursion suppression and repeated inclusion with changed definitions. `missing-header` requires failure with a three-file include chain, requested diagnostic JSON and no generated sources (#21); the strict API still throws. `PreprocessorIT` inspects those same ordinary sources through the public API and packaged CLI. Run `mvn -Dit.test=PreprocessorIT,Cpp03CorpusIT verify` for this acceptance.

`provenance` is the tenth mandatory corpus scenario. `ProvenanceIT` checks distinct syntax nodes with shared argument origins, nested events, partial/multiple/empty token boundaries, API/CLI parity and edit/build preservation. `scripts/demo.sh` renders real SVG with the layer on/off and validates source-coordinate links. See the fixture README for commands.

`MacroEditingIT` checks immutable captured input, one-operand replacement, preserved header/second invocation, packaged CLI reparsing and nested precedence. The square corpus now compiles the actual edited graph under both compilers; scripts/demo.sh runs examples/cpp03-square/EditSquare.java through the public API and verifies 58→64.

`build-variants` is the eleventh mandatory scenario. Its four-command database is imported through API and JSON-only CLI; server/client compositions are selected through the API, regenerated, reparsed, and compiled with both strict compilers using the matching -D flag. `BuildVariantsIT` separately verifies unknown ambiguity versus known ODR conflict. The public selection demo also runs in scripts/demo.sh.

`conditional-catalog` is the twelfth mandatory scenario. `ConditionalCatalogIT` checks unused header definitions, redefinition IDs, definition-to-expansion/presence links, a presence check for undefined versus `DEBUG=0`, external-input ownership under two compilation commands and the C++03 profile, and physical versus `#line` coordinates. Inactive invalid syntax and an unopened missing header remain in the preserved source without AST nodes or executed checks. Both branches of a partial initializer are parsed through the API; corpus original/generated sources run under strict GCC and Clang with `-DDEBUG=0` and without it (`native.also-without-flag=true`). Negative tests activate the invalid branch or omit `#endif`; the CLI emits requested diagnostic JSON and blocks source generation (#21). Run `mvn -Dit.test=ConditionalCatalogIT,Cpp03CorpusIT verify`; [fixture commands and expectations](../src/test/resources/cpp03/conditional-catalog/README.md).

Configuration provenance now distinguishes `java-api`, `cli` and compilation-database sources. `SnapshotAssertions.assertEquivalent` compares effective syntax/analyses/preprocessing while omitting only input-attribution fields where the entry routes differ; dedicated tests inspect those ownership fields. For identical database routes without differing overrides, full JSON equality is still checked. Separate API and CLI `return-zero` golden snapshots preserve the intended origin distinction. Evaluator-specific integer/character choices are tested directly rather than used as portable native execution oracles.

`shared-headers` is the thirteenth mandatory scenario. Its `common` and `conditional` fixtures each import the same physical main.cpp twice with SERVER/CLIENT settings. `SharedHeaderGenerationIT` checks that a server-only shared-line edit fails with both variants, source coordinates and required texts, without creating an export directory. Identical edits emit one changed header; an edit in an existing SERVER arm preserves the CLIENT arm byte-for-byte, including CRLF, tabs, comments and spaces. Strict GCC/Clang corpus runs compare original 1/1, common edited 2/2 and conditional edited 2/1. `SharedSourceRequirementsTest` covers reconciliation boundaries. The [public editing example](../examples/cpp03-shared-headers/README.md) and `scripts/demo.sh` reproduce all three outcomes. Run `mvn -Dit.test=SharedHeaderGenerationIT,Cpp03CorpusIT verify`.


## Parser recovery and diagnostic exports (#21)

`parser-recovery` and `inactive-extension` are the fourteenth and fifteenth mandatory scenarios. Earlier scenario counts above record the milestones at which fixtures entered the corpus; the inventory is authoritative. Source failures in valid C++03 configurations now have a diagnostic-export contract, replacing the earlier CLI no-export expectation without changing strict Java/API behavior.

- [parser-recovery](../src/test/resources/cpp03/parser-recovery/README.md) supplies a compilation database with damaged `a.cpp` and valid `b.cpp`, each with its own macro setting. `ParserRecoveryIT` and `Cpp03CorpusIT` require the retained function before the error, a stop position and raw skipped fragment in `a.cpp`, all expected syntax in `b.cpp`, matching recovering API/CLI JSON, actual readable SVG and status 1. Both JSON/SVG-only and combined source-output requests are tested; neither may emit C++.
- [inactive-extension](../src/test/resources/cpp03/inactive-extension/README.md) keeps the packed attribute in `#if 0`, successfully exports and executes original/generated C++ under both strict compilers. The paired active test reports `main.cpp:1:8` at `__attribute__`, includes the C++03 reason, writes diagnostic JSON and blocks source output.
- `ParserRecoveryIT` also checks missing initializer range/text, exact nonempty skipped text, continuation to the next declaration, variadic macros, `LL`/binary/raw literals, repeated unexpected tokens, truncated functions and EOF. A damaged unit must terminate and must not hide its valid neighbor. `ParserRecoveryTest` checks copying and Visitor dispatch; `RecoveryExportTest` checks schema, DOT and generation guards.

For a C++ source failure, assert status 1, the reason in `stderr()` and `diagnosticSnapshot()`; this also checks that the source output directory does not exist. Assert requested SVG separately. `expectNoExports()` remains correct for invalid configuration/options, Java parse failures and other failures before a diagnostic graph exists. Healthy JSON golden files omit the optional error fields and remain unchanged. Broken snapshots stay at `PARSED` regardless of requested stage, without CFG/DFG or analysis claims; per-function flow recovery is not part of #21.

```sh
mvn -Dit.test=ParserRecoveryIT,Cpp03CorpusIT verify
python3 scripts/check-cpp03-reports.py
```

Run the report guard only after a complete, unfiltered corpus run. CI installs Graphviz before Maven so SVG checks execute as part of acceptance.

## Diagnostic provenance and grouped summaries (#22)

[diagnostic-provenance](../src/test/resources/cpp03/diagnostic-provenance/README.md) is the sixteenth mandatory scenario. `DiagnosticProvenanceIT` and the corpus invoke the same acceptance helper on four ordinary compilation databases:

- `assignment`: `ASSIGN(total);` retains the missing-expression position before `;`, a call-site note and the exact `=` token in the header. An independent missing initializer after recovery remains a second error and its declaration survives.
- `paste`: `JOIN(+, *)` produces one primary CLI error on the invocation. API/JSON must contain all three labeled ranges: the left and right arguments in `.cpp` and `##` in the header. Assertions check role, absolute physical file, start/end offsets, lines and columns, plus the failed expansion context.
- `shared`: the same error in one physical header from `a.cpp` and `b.cpp` yields one group, two translation units and two retained contexts. A separate API check uses two variants of one physical `.cpp`: one translation unit, two variants.
- `causes`: two commands define `BAD` differently, producing `+*` and `+/` at the same physical macro invocation. The distinct reasons must yield two groups rather than collapse by line/range.

Every case compares full API/CLI JSON, counts primary `error [code]` lines against groups, checks build-variant/include/expansion context, and checks unchanged groups after `edit().build()` and `NameResolver.resolve`. The shared-header and different-reason cases reverse translation-unit order and require equal group records and root `diagnosticGroups` JSON. This tests deterministic summaries; it does not demand identical AST IDs or file ordering after reversing the project inputs. These are invalid-source diagnostic oracles, not native execution oracles. They keep exit status 1 and source output blocked, and do not claim semantic-analysis grouping or automatic error repair.

Additional `DiagnosticProvenanceIT` regressions cover two independent missing expressions inside one macro, unsafe assignment recovery inside parentheses/conditions/calls, and distinct expanded operands (`40000`/`50000`) for invalid `#line`/`#include` directives. The directive cases compare API/CLI JSON, macro ancestry and reversed summaries; unsafe recovery must stop with one original error, not create a cascade. `DiagnosticProvenanceTest` also covers conditional operands, incompatible redefinition signatures and labeled previous/new definitions, repeated-include occurrence IDs, strict exception provenance, and the same safe assignment boundary in the lower-level Java recovering parser.

```sh
mvn -Dit.test=DiagnosticProvenanceIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=diagnostic-provenance verify
# Full regression and mandatory corpus inventory, without a scenario filter:
mvn verify
python3 scripts/check-cpp03-reports.py
```

Run the report check only after the full corpus. At the #22 milestone, sixteen scenarios required eighteen corpus results, including the two infrastructure checks; the current inventory is described below. Existing Java/C++ tests and strict GCC/Clang checks for valid corpus programs remain mandatory. The fixture README gives manual packaged-CLI commands and the exact physical coordinate oracle; the [model guide](model-guide.en.md#diagnostic-provenance-and-grouping-22) defines the API and JSON fields.

## Platform profiles and declarators (#23)

[platform-declarators](../src/test/resources/cpp03/platform-declarators/README.md) is the seventeenth mandatory scenario. `PlatformDeclaratorsIT` and `Cpp03CorpusIT` share `PlatformDeclaratorsAcceptance` over the same ordinary source files. It checks the distinction between arrays of pointers, pointers to arrays and function pointers, abstract function parameters, a member pointer, cv qualifiers, references, local comma-separated declarations and a written typedef linked to its canonical type. A `PARSED` copy preserves the complete JSON snapshot, and parsed API and packaged CLI snapshots agree after accounting for their documented input-attribution difference.

The target checks compare default and `long32` profiles, target bytes of 8 and 16 bits, long integer ranges, absent versus explicit-null alignment, invalid settings and distinct per-unit variants. `GREEN` written types and `TypeRepresentation` records survive an unchanged edit; this does not require full JSON equality, because editing drops derived CFG/DFG edges. `FOLDED` returns 20 under the default profile and 16 under `long32`, with API/CLI parity. Ordinary-field layout is checked separately: `char, long, char` has default offsets 0/8/16 target bytes and size 24; changing long to 32 bits with 32-bit alignment gives size 12. Unknown layout facts must remain unknown rather than taking host values.

`PlatformEncodingIT` separately writes ISO-8859-1 source/header files, exports through the packaged CLI and verifies their exact bytes and reparsing under the same profile. It also requires no exports for unrepresentable generated text or conflicting encodings for one physical file. These checks do not add corpus inventory entries.

The corpus compiles and executes original and generated default-profile sources using both `g++` and `clang++` with `-std=c++03 -pedantic-errors`, expecting empty output and exit 20. The custom `long32` size/result checks are **API/CLI model oracles**, not native executions claiming the host compiler uses that arbitrary ABI. The default native oracle is the repository’s controlled test configuration, not a declaration that all C++03 targets are LP64. Function/member-pointer/class layout and the fuller literal, conversion and overload work remain outside #23 as described in the [model guide](model-guide.en.md#platform-profiles-and-recursive-declarators-23).

```sh
mvn -Dit.test=PlatformDeclaratorsIT,PlatformEncodingIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=platform-declarators verify
# Full acceptance, without filtering the corpus:
mvn verify
python3 scripts/check-cpp03-reports.py
```

At the #23 milestone, seventeen scenarios required nineteen corpus results including the two infrastructure checks; the current inventory is described below. Run it only after the complete corpus, not a focused invocation. JDK 21, both strict C++ compilers and Graphviz remain required, as do the existing Java/C++ regression and translation tests.

The `platform-declarators/signed-min` companion is also compiled and executed
before and after folding by both mandatory compilers. Its `int`/`long` overload
oracle must return 1: generated signed-minimum expressions must retain their
original type. This native oracle does not claim CPG overload resolution (#30).

## Scopes, declarations and linkage (#24)

[scopes-linkage](../src/test/resources/cpp03/scopes-linkage/README.md) is the eighteenth mandatory scenario. `ScopesLinkageIT` and `Cpp03CorpusIT` share `ScopesLinkageAcceptance`, importing the same three ordinary `.cpp` files through their compilation database. The positive contract covers:

- Two `config.h` typedef occurrences at the same physical range, with canonical types `long` and `int`. Only the `a.cpp` command defines `WIDE`; each `Width` use must reference its own translation unit's typedef.
- Separate file-scope `static helper` functions and anonymous-namespace `hidden` functions in `a.cpp` and `b.cpp`; calls must stay within their unit even after selecting the complete build composition.
- Reopened namespaces, a namespace alias, a using-declaration, sequential typedefs, and one using-directive traversed by two distinct type uses. API/JSON must preserve both per-use chains, the shared directive and its namespace target, and the absence of a using step for an explicitly qualified name.
- A named pointer type referencing the visible forward class declaration, with a forward-declaration-to-definition link and false completeness at the earlier use; C language-linkage declarations and definitions remain connected.
- Full API/packaged-CLI JSON parity for the same database, reference targets owned by the snapshot, preserved header text, generated-source reparse under the same per-unit flags, and equivalent links in copied and selected snapshots.

The native oracle separately compiles each translation unit with `g++` and `clang++`, always using `-std=c++03 -pedantic-errors`, applying `-DWIDE` **only to `a.cpp`**, then links the objects. Both original and generated programs must produce empty stdout and exit 33. Passing a global `-DWIDE` to every file would test a different program and is not an acceptable substitute. No native execution is used as an oracle for an invalid incomplete-type or ODR case.

`ScopesLinkageIT` also checks `sizeof` before a class definition as a negative semantic case. A pointer to the forward declaration is valid, but a later definition does not make the earlier `sizeof` valid. The analyzed API graph contains `incomplete-type` at ERROR severity, with no parser error; the CLI must return 1, write requested diagnostic JSON/SVG and leave the source-output directory absent. The same negative source must be rejected by both native compilers with `-fsyntax-only`. Parsing alone (`PARSED`) does not run this semantic rule; the diagnostic graph is requested at `RESOLVED`.

`ScopeDeclarationParserTest`, `ScopedResolutionTest` and `ScopedExportTest` regressions cover block/function/class scopes, shadowing and declaration points, nested types, enums, storage specifiers, `T(a);` and `T x(U());` disambiguation, repeated declarations, linkage conflicts, semantic source-output guards, and edit/reanalysis ownership. Export checks traverse the JSON `resolutionSteps` IDs and ordinary reference edges, check that two uses share one directive, preserve forward/definition references in copied snapshots, and reject unsupported Java translation. Shared-header edit checks keep the #20 reconciliation guard active for namespace declarations rather than flattening conditional source text.

```sh
mvn -Dit.test=ScopesLinkageIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=scopes-linkage verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --compile-commands src/test/resources/cpp03/scopes-linkage/compile_commands.json \
  --out target/scopes-linkage-cpp --json target/scopes-linkage.json
# Full acceptance, without a corpus filter:
mvn verify
python3 scripts/check-cpp03-reports.py
```

At the #24 milestone the inventory contained eighteen scenarios, requiring **twenty** corpus/harness results with no failures, errors or skips. It must run only after the complete corpus. JDK 21, GCC, Clang and Graphviz remain required; a focused local run is not a full acceptance result. The [model guide](model-guide.en.md#scopes-declarations-and-linkage-24) defines the exact fields and remaining limits: full overload selection/ADL, class ABI and complete C++03 declaration/ODR semantics are not claimed by this scenario.

## Literals, expressions and conversions (#25)

[literals-expressions](../src/test/resources/cpp03/literals-expressions/README.md) is the nineteenth mandatory scenario. `LiteralsExpressionsIT` and `Cpp03CorpusIT` share `LiteralsExpressionsAcceptance` and the ordinary `sources/main.cpp`, rather than embedding a separate positive program. Acceptance checks:

- Exact large unsigned integer value and target type, the checked legacy `int` accessor, three floating types, decoded ordinary/wide characters and adjacent strings, and booleans.
- Distinct operator nodes and precedence, canonical expression result type/category, and owned implicit conversion chains. The two calls to a unique `accept(long)` compare `int`→`long` implicit conversion with a ranged explicit `static_cast<long>`; generation must contain exactly one written cast.
- Public resolved API/packaged CLI JSON parity, readable SVG, snapshot copy/reference ownership, generated-source reparsing and rejection of unsupported Java output.
- Original, resolved-generated and folded-generated programs compiled and executed by **both** GCC and Clang with `-std=c++03 -pedantic-errors`, empty output and exit 0. Floating target rounding and implementation-defined multicharacter values are not native oracles.
- A binary-literal negative input rejected as outside C++03, recovering API/CLI diagnostic exports and no generated sources; strict native syntax checks also reject it.

```sh
mvn -Dit.test=LiteralsExpressionsIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=literals-expressions verify
mvn verify
python3 scripts/check-cpp03-reports.py
```

Run the report guard only after the complete corpus: the #25 inventory had **nineteen scenarios and twenty-one corpus/harness results**, without failures, errors or skips. Existing Java and C++ regressions remain mandatory. The [model guide](model-guide.en.md#literals-expressions-and-standard-conversions-25) defines the conservative built-in conversion contract and the #26/#28/#30 boundaries; this fixture does not claim full C++03 overload resolution, target floating-point arithmetic or constant folding.

## Functions and default arguments (#26)

[functions-defaults](../src/test/resources/cpp03/functions-defaults/README.md) is the twentieth mandatory scenario. `FunctionsDefaultsIT` and the corpus share `FunctionsDefaultsAcceptance` and ordinary fixture files. The positive graph distinguishes `log(); log(2); log();`: two omitted calls reference the same declaration-owned `next()` expression, while the middle call keeps its written argument. Checks cover the supplying prototype separately from the later definition, JSON reference IDs, API/CLI parity, DOT/SVG, copied snapshot ownership, editing and generated-source reparsing.

Original and generated programs run under **both** GCC and Clang with `-std=c++03 -pedantic-errors`, empty output and exit 0; the fixture checks that `next()` executes twice. Source generation leaves both `log()` calls omitted. The negative fixture calls a function before its later default is visible: native compilers reject it, the diagnostic API/CLI export the error and source output stays blocked. `FunctionDefaultsExportTest` additionally checks reference identity, source-preserving edits with comments/macros, and Java guards for defaults and unsupported signatures.

```sh
mvn -Dit.test=FunctionsDefaultsIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=functions-defaults verify
mvn verify
python3 scripts/check-cpp03-reports.py
```

At milestone #26 the inventory required **twenty scenarios and twenty-two corpus/harness results**, with no failures, errors or skips. Run the report guard only after the unfiltered corpus. Existing Java tests, SQUARE editing, `scripts/demo.sh`, Maven/Javadoc and both strict compilers remain required. The [model guide](model-guide.en.md#functions-and-default-arguments-26) defines the non-owning defaults contract and remaining overload/virtual-dispatch limits.

## Classes and initialization (#27)

The mandatory [classes-initialization](../src/test/resources/cpp03/classes-initialization/README.md)
scenario runs API, packaged CLI, JSON/SVG, copy/rebuild and regeneration checks.
GCC and Clang compile original, regenerated and folded sources in strict C++03;
all three programs must exit 0. The trace checks base/field construction despite
reordered written initializers and reverse destruction, observable copy/assignment,
aggregate zero-fill, union storage, static construction once and temporary lifetime
extension. A C++11 direct-list initializer is rejected by CLI and both compilers.

`ClassInitializationParserTest`, `ClassInitializationSemanticsTest`,
`ClassInitializationExportTest` and `ClassesInitializationIT` additionally check
mixed access, nested classes, bit-fields, friendship, qualified definitions,
implicit-special-member descriptors, lexical lifetime plans and ownership-safe
class-parameter signatures. Run `mvn verify`; the focused native command is
`mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=classes-initialization verify`.
No native check is skipped when a compiler is unavailable.

At milestone #27 the full inventory requires **21 scenarios and 23 corpus/harness results**, without failures, errors or skips.

## Object sizes, casts and allocation (#28)

`ObjectMemoryExportTest` checks source generation/reparse for C-style, functional
and all four named casts, type/expression `typeid`, scalar/array allocation and
deletion, placement allocation, global allocation/deletion, parenthesized types
and the distinction between omitted and empty initialization. It also checks
copy/JSON stability, graph labels and Java rejection. A parsed cast is not a
claim that the cast is valid for every class hierarchy: native acceptance uses
well-formed source and the model retains unsupported runtime semantics.

Profile tests compare aligned and packed `char`/`int` aggregate layout (8/5
bytes), unknown-layout symbolic bounds and immutable profile reanalysis, plus
64/32-bit `long` size (8/4 bytes) without losing nominal overload identities.
Native compiler checks use the host-compatible profile, not a packed profile
without corresponding compiler options. See the [model contract](model-guide.en.md#object-sizes-casts-and-allocation-28).

Mandatory `casts-memory` runs original, regenerated and folded C++03 under both
GCC and Clang: aggregate sizeof and symbolic array bounds, value-initialized
scalar/array allocation, evaluated allocation bounds, placement new, scalar and
array deletion, valid cast forms, unsigned wrap and retained executed effects.
Scalar typeid and sizeof operands must remain unevaluated. CLI/API/JSON/SVG,
copy/reference ownership and reparse are checked; sizeof an incomplete class is
rejected by CLI and both native compilers. Run:
`mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=casts-memory verify`.
At #28 the unfiltered corpus contains **22 scenarios and 24 results including
infrastructure tests**, with no skipped compiler checks.

## Inheritance and virtual methods (#29)

`VirtualInheritanceExportTest` checks pure virtual source/reparse, out-of-class
definitions, no-call bidirectional override exports, JSON copy stability,
project-owned references, distinct static/default/virtual-target fields and DOT
labels. It also checks that omitted arguments stay omitted and Java generation
rejects pure virtual syntax even without a repeated `virtual` keyword.
Run `mvn -Dtest=VirtualInheritanceExportTest test`.
The acceptance scenarios exercise single, multiple and virtual inheritance,
shared virtual-base diamonds, two independent overridden bases and static
selection of different default arguments. Native execution verifies original
and generated C++03 under GCC and Clang; graph target sets remain conservative
analysis, not a native-runtime prediction or full overload ranking.
See the [model contract](model-guide.en.md#inheritance-and-virtual-methods-29).

Mandatory `inheritance-virtual` checks defaults 1/2 selected through the base
reference/direct derived call, one implementation overriding two independent
base declarations, a pure interface, and a shared virtual-base diamond with
construction/destruction trace `12344321`. Repeated non-virtual base ambiguity
is a separate negative case. Run:
`mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=inheritance-virtual verify`.
At #29 the unfiltered corpus requires **23 scenarios and 25 results including
infrastructure tests**, with no failed or skipped compiler checks.
Construction-order API tests distinguish complete-object and base-subobject
contexts; callable CFGs deliberately remain incomplete where the invocation
context in a virtual hierarchy is unknown.

## Overloads, ADL and nominal type identity (#30)

`OverloadExportTest` checks source/reparse and copy stability for a `Money`
addition, project-owned non-owning implementation references, distinct JSON
operator binding fields and DOT implementation labels. It checks conversion
functions without a written return type and rejects Java generation. An explicit
equal-width platform verifies different `f(int)` / `f(long)` targets for `f(1)` /
`f(1L)`, with the `long` spelling and `L` suffix preserved after regeneration.
Run `mvn -Dtest=OverloadExportTest test`.

Native acceptance compares original, generated and folded C++03 under GCC and
Clang, including overloaded `&&` evaluating both operands and the built-in
short-circuit control. Graph implementation references and source syntax are
checked separately from runtime results; unknown implementation bodies are not
assigned invented arithmetic semantics.
See the [model contract](model-guide.en.md#overloads-adl-and-nominal-type-identity-30).

Mandatory `overloads-adl` uses an explicit int32/long32 semantic profile for
API/CLI green snapshots while retaining written int/long C++ types. It checks
`f(1)`, `f(1L)` and folded long arithmetic, namespace ADL, Money addition,
class-returning operands of overloaded &&, observable conversion-function and
converting-constructor effects, and explicit-constructor rejection. Native
compilers use their host ABI: the test checks preserved overload selection, not
a claim that their long width was changed. Run
`mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=overloads-adl verify`.
At #30 the complete corpus has **24 scenarios and 26 results including
infrastructure tests**, with no skipped native compilers.

### Statement and exception export checks (#31)

`StatementExceptionExportTest` checks generated-source reparsing for empty statements,
for initializers, do/while, switch fall-through, labels and goto; exception specifications,
typed/catch-all handlers and rethrow; and constructor function-try-block initializer order.
It also checks JSON edit/copy stability and DOT references for non-syntax cleanup vertices.
A recovered incomplete body must remain visible in JSON/DOT while source generation fails.

Native cleanup scenarios must compare event order **and counts** with reachable CFG paths:
normal exit destroys `b, a`, an exception in work destroys `b, a`, and a failure to construct
`b` destroys only successfully constructed `a`. The generated source must retain ordinary
local declarations, not explicit destructor calls. Pair an unknown `void work();` declaration
with `void work() throw();` inside try/catch to distinguish possible exceptional cleanup from
a nonthrowing call. A failure elsewhere in a file must not erase a complete function's flow;
an interrupted function must not be presented as fully analyzed.

Mandatory `statements-exceptions` runs original, generated and folded sources with
GCC and Clang under strict C++03. `StatementsExceptionsAcceptance` compares
API/CLI JSON and copied snapshots, resolves cleanup targets, and checks exceptional
edges independently of the native result. Its event traces are `1221` for normal
reverse destruction, `12219` for work failure then an outer handler, `119` for
failure of the second constructor, and `1189` for constructor function-try cleanup.
Destructor function-try cleanup produces `122189`: both members are destroyed
before its handler and the outer handler. Every path also checks destruction counts.
Return, goto, continue/break, nested switch, condition declarations and rethrow have
runtime controls. Separate invalid and truncated inputs must export diagnostics and
flow completeness without generated source.
Run `mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=statements-exceptions verify`.
The #31 corpus contains **25 scenarios and 27 results including infrastructure tests**.

## Comments and opt-in cleanup (#32)

The `comments-cleanup` corpus fixture exercises ordinary preservation and the explicit
`Comments.remove(Project)` library operation. The ordinary export keeps comments;
the cleaned project has no comment nodes/links or lexical comments, including in
inactive branches, while string/character literal contents remain unchanged. Native
GCC and Clang checks use the same strict C++03 flags and runtime expectations.
`CppCommentGraphTest`, `CommentExportTest` and `CommentsTest` cover physical ranges,
association boundaries, copy identity, JSON/DOT ownership, canonical fragment output,
validated literal/structural edits, and lexical cleanup. Cleanup is opt-in, not a new
CLI switch or an automatic analysis stage. Java parsing/translation regressions keep
their existing contract. See the model guide for canonical-versus-physical rendering.

Run just this scenario with
`mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=comments-cleanup verify`.
The full inventory now has 26 scenarios and 28 corpus results including the two
harness checks. The fixture runs original, ordinary generated and explicitly cleaned
programs under GCC and Clang for both FEATURE configurations; assertions cover
headers/inactive branches, macro line continuations, physical `__LINE__`, literal
markers, copy/association identity and API/CLI JSON/SVG exports.

## Linked Doxygen declarations (#33)

The `doxygen-parameters` fixture retains prototype/definition comments and both
briefs, checks parameter-position queries across different names, repeated-header
and include-context bindings, unknown tags, and minimal template type parameters.
Independent header and definition renames must preserve all free prose exactly.
`DocumentationRenameTest` additionally checks backslash tags, dependent type uses,
JSON/DOT links, copy ownership, macro rejection and conflicting physical contexts.
Run `mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=doxygen-parameters verify`.
Original and generated programs are compiled under strict C++03 with GCC and Clang.
The mandatory scenario also compiles the explicitly cleaned snapshot and two
independently renamed snapshots. Header `count` changes only its declaration and
bound tag name; definition `n` changes its declaration/body uses while the header
remains byte-exact. It checks complete raw unknown commands, ordered T/U entries,
consecutive line prose, both include-context parameter types, the exact warning
span for unmatched `size`, and absence of documentation after cleanup. Body macros
do not prevent a physical signature from being documented. The full corpus has
**27 scenarios and 29 results including infrastructure tests**.

## Basic templates and dependent names (#34)

The template scenario uses the compiler's genuine system `<vector>`: its generic
`count<T>(const T&)` remains written once, and calls for `std::vector<int>` exercise
basic type substitution and const `size()` lookup. Original, generated and
reparsed programs must agree under the corpus's native C++03 compiler matrix.
No user-defined replacement vector or synthetic implementation is compiled.
API/export tests separately verify structured parameters/defaults/arguments,
`typename`/`template` markers, unresolved dependent generic calls, owned instance
links, copy identity and include-scoped declaration-only catalogue visibility.
The catalogue and instance bodies must never enter physical generated files.
Full specialization, SFINAE and standard-library modeling remain outside #34.

Run `mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=templates-dependent verify`.
The full corpus now has **28 scenarios and 30 results including infrastructure
checks**. This scenario checks API/CLI equivalence, snapshot copying, JSON/SVG,
original/generated/folded native programs, and three invalid C++03 template forms.
Public `Cpg.read` regression tests also verify instance CFG/DFG through resolved,
green and folded stages, including target-profile-dependent branches and folding.

## Specialization exports and the C++03 export investigation (#35)

Run focused syntax/export checks with:

```sh
mvn -Dtest=TemplateInstantiationExportTest,TemplateExportTest test
scripts/probe-cpp03-export.sh g++ clang++
```

The unit tests check full/partial specialization syntax, explicit instantiation,
`export` preservation, separately owned generic and concrete syntax, typed call
and request edges, reverse requests, copies and the absence of dangling references.
The compiler probe prints versions and diagnostics and leaves no files behind.
Unavailable compilers are explicitly marked SKIPPED. Its exit status is not a
conformance verdict: inspect both reported compile/link outcomes. With GCC 14.2
and Clang 19.1.7 the separate exported definition fails linking, whereas the
explicit-instantiation control builds and runs. Neither an ignored keyword nor a
passing explicit-instantiation control closes C++03 `export` support. Compatible
shared-header instantiation across TUs remains distinct from this investigation.

`TemplateInstantiationExportTest` also exercises TYPE/VALUE/TEMPLATE callable
arguments, unique owned JSON IDs and compatibility type views in copied snapshots.

The mandatory `template-instantiation` corpus runs two physical translation units
sharing one header. It checks shared outer/inner instances and reverse request
links, retained generic bodies, immediate-context SFINAE, function ordering,
full and ordered partial class specializations, explicit function instantiation,
array-bound/value/template-template arguments, copied graphs and API/CLI JSON/SVG
parity. Original, generated and folded programs use genuine system `<vector>` and
must agree under GCC and Clang. Separate invalid fixtures cover selected nested
body errors with ordered primary/request ranges, partial function specialization
and ambiguous class partials. The full corpus has **29 scenarios and 31 results
including infrastructure checks**. Run just this gate with
`mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=template-instantiation verify`.

The mandatory `vector-semantics` corpus checks API/CLI JSON parity, typed semantic
links in SVG, snapshot copies, GREEN/FOLDED source regeneration and genuine GCC /
Clang C++03 execution. It distinguishes count/fill/range creation and assignment,
container reference identity, independent copied sizes, shared pointer elements,
const Record copy-constructor selection, bool proxies and custom allocator effects.
The nested copy-overloads program checks observable const-copy selection without
requiring an implementation-specific number of copies. Original, GREEN and FOLDED
programs are compiled and run for both fixtures and both compilers.

`vector-access` executes real C++03 access, checked out-of-range exception, reserve
and resize, all insertion/erase forms, returned iterators, forward/reverse/const
iteration, clear and swap. It verifies reference preservation before insertion with
sufficient reserved capacity and the distinction between container and element
references after swap. A possibly invalidated reference is inspected only in the
graph, never dereferenced in the executable. API/JSON/SVG, copied snapshots and
original/GREEN/FOLDED source runs are checked under both GCC and Clang.

`container-families` uses genuine system deque/list/set/map/multi-containers and
adapters. It checks deque end insertion/indexing; list single/all splice with a
preserved iterator/reference and unchanged source-object alias; lookup versus
map insertion; duplicate multimap/multiset keys; and extraction order 231/132/321
for stack/queue/priority_queue on identical inputs. A user comparator has a real
linked body and observable comparison count, without asserting its exact value.
API/CLI JSON, full DOT, compact splice SVG, copied snapshots and native original /
GREEN/FOLDED sources are checked separately.
