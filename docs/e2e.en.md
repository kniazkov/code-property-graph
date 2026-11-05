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

Run the report guard only after the complete corpus: the current inventory has **nineteen scenarios and twenty-one corpus/harness results**, without failures, errors or skips. Existing Java and C++ regressions remain mandatory. The [model guide](model-guide.en.md#literals-expressions-and-standard-conversions-25) defines the conservative built-in conversion contract and the #26/#28/#30 boundaries; this fixture does not claim full C++03 overload resolution, target floating-point arithmetic or constant folding.
