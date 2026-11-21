# Reproducible end-to-end tests

[README](../README.md) · [Model guide](model-guide.md)

Canonical opening, checked against `906db23b187cf36661749fa4b90d4231ec170abc`.
[M042](documentation-migration/M042.json) records scope and owners. Later sections
remain in [the original English guide](e2e.en.md); both originals stay until
#269–#272 finish migration and counterpart reconciliation.

On `documents`, CI is temporarily disabled. Commands below are reproducibility
instructions, not fresh test results. Final integration restores CI as recorded
in [the migration policy](documentation-migration/README.md#language-and-branch-policy).

Use JDK 21, Maven, GCC (`g++`), Clang (`clang++`), Graphviz (`dot`) and Python 3 on PATH. Failsafe runs the packaged JAR during `mvn verify`, including Java/C++ translation regressions and actual diagnostic SVG checks. Full evidence requires a clean, unfiltered run:

```sh
mvn clean verify
python3 scripts/check-cpp03-reports.py
```

## C++03 corpus

The inventory is [`scenarios.txt`](../src/test/resources/cpp03/scenarios.txt): **42 scenarios**, hence **44 corpus/harness results** at the checked revision. The three rows below are initial examples, not the whole inventory. Each entry names ordinary `sources/` files and `scenario.properties` expectations exercised by `Cpp03CorpusIT` through API and CLI. Positive cases use strict `Cpg.read`; negative cases separately check strict exceptions, recovering API and CLI diagnostics. `CliSandbox.directory` preserves nested relative paths and headers. Folder/database input recursively preprocesses configured headers.

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

Use `resolved` for the other two initial scenarios. `invalid-token` intentionally fails. For positive cases, both compilers build original and generated sources with `-std=c++03 -pedantic-errors`. Exit codes and exact output have independent expectations; failures include compiler version and command. A missing compiler is a failure, not an assumption/skip; `missingCompilerIsAnError` tests that contract.

The report guard checks result counts against inventory size plus two harness tests and rejects failures, errors or skips. `inventoryAndNestedCopy` rejects duplicates and unlisted directories. The guard does not identify each result by scenario name or establish report freshness: use it after a successful full run, never as evidence from filtered or stale reports.

Reuse existing nodes. Add positive, negative and interaction scenarios; update the standard matrix and canonical English model guide in the same PR. Specify diagnostics/graphs and independent output/exit expectations. Undefined behavior is not an execution oracle; implementation-dependent outcomes need an explicit target contract. Editing scenarios need separate before/after expectations. New nodes need builders, visitors, ranges, exports and generation tests.


Historical milestones below retain original counts; current membership comes from the inventory. `compilation-database` was fifth. It uses a mixed C++03/C++11 database with an explicit C++03 override and requires full API/CLI parity and both native compilers. `CompilationDatabaseIT` also checks default-vs-explicit folder equivalence, rejection without the override, lambda rejection despite the override, unsupported-option filenames, and a manually constructed configuration with ordered spaced paths and define/undefine operations. Run `mvn -Dit.test=CompilationDatabaseIT verify`; [fixture details](../src/test/resources/cpp03/compilation-database/README.md).

`lexical-phases` and `unterminated-literal` brought the corpus to seven scenarios. `LexicalPhasesIT` inspects per-character physical mapping, trigraph ranges, alternative spelling and full API/CLI graph parity using the same source. The positive source executes through both strict compilers; the negative source reports physical 2:5, produces requested diagnostic JSON and blocks source output (#21). See each fixture README for commands. The saved workflow triggers on PR open/update/reopen, `master` pushes and manual dispatch; it remains inactive on `documents`.

`macros` and `missing-header` brought the inventory to nine scenarios. `square` now uses compilation-database mode with full API/CLI graph parity, two expanded multiplications, and native original/generated output 58. The actual graph edit and the independent hand-written oracle both produce 64 (#17). `macros` covers a macro-generated include name, guards, stringization, pasting, nested arguments, recursion suppression and repeated inclusion with changed definitions. `missing-header` requires failure with a three-file include chain, requested diagnostic JSON and no generated sources (#21); the strict API still throws. `PreprocessorIT` inspects those same ordinary sources through the public API and packaged CLI. Run `mvn -Dit.test=PreprocessorIT,Cpp03CorpusIT verify` for this acceptance.

`provenance` was the tenth mandatory corpus scenario. `ProvenanceIT` checks distinct syntax nodes with shared argument origins, nested events, partial/multiple/empty token boundaries, API/CLI parity and edit/build preservation. `scripts/demo.sh` renders real SVG with the layer on/off and validates source-coordinate links. See the fixture README for commands.

`MacroEditingIT` checks immutable captured input, one-operand replacement, preserved header/second invocation, packaged CLI reparsing and nested precedence. The square corpus now compiles the actual edited graph under both compilers; scripts/demo.sh runs examples/cpp03-square/EditSquare.java through the public API and verifies 58→64.

`build-variants` was the eleventh mandatory scenario. Its four-command database is imported through API and JSON-only CLI; server/client compositions are selected through the API, regenerated, reparsed, and compiled with both strict compilers using the matching -D flag. `BuildVariantsIT` separately verifies unknown ambiguity versus known ODR conflict. The selection demo is in `scripts/demo.sh`.

`conditional-catalog` was the twelfth mandatory scenario. `ConditionalCatalogIT` checks unused header definitions, redefinition IDs, definition-to-expansion/presence links, a presence check for undefined versus `DEBUG=0`, external-input ownership under two compilation commands and the C++03 profile, and physical versus `#line` coordinates. Inactive invalid syntax and an unopened missing header remain in the preserved source without AST nodes or executed checks. Both branches of a partial initializer are parsed through the API; corpus original/generated sources run under strict GCC and Clang with `-DDEBUG=0` and without it (`native.also-without-flag=true`). Negative tests activate the invalid branch or omit `#endif`; the CLI emits requested diagnostic JSON and blocks source generation (#21). Run `mvn -Dit.test=ConditionalCatalogIT,Cpp03CorpusIT verify`; [fixture commands and expectations](../src/test/resources/cpp03/conditional-catalog/README.md).

Configuration provenance now distinguishes `java-api`, `cli` and compilation-database sources. `SnapshotAssertions.assertEquivalent` compares effective syntax/analyses/preprocessing while omitting only input-attribution fields where the entry routes differ; dedicated tests inspect those ownership fields. For identical database routes without differing overrides, full JSON equality is still checked. Separate API and CLI `return-zero` golden snapshots preserve the intended origin distinction. Evaluator-specific integer/character choices are tested directly rather than used as portable native execution oracles.

`shared-headers` was the thirteenth mandatory scenario. Its `common` and `conditional` fixtures each import the same physical main.cpp twice with SERVER/CLIENT settings. `SharedHeaderGenerationIT` checks that a server-only shared-line edit fails with both variants, source coordinates and required texts, without creating an export directory. Identical edits emit one changed header; an edit in an existing SERVER arm preserves the CLIENT arm byte-for-byte, including CRLF, tabs, comments and spaces. Strict GCC/Clang corpus runs compare original 1/1, common edited 2/2 and conditional edited 2/1. `SharedSourceRequirementsTest` covers reconciliation boundaries. The [public editing example](../examples/cpp03-shared-headers/README.md) and `scripts/demo.sh` reproduce all three outcomes. Run `mvn -Dit.test=SharedHeaderGenerationIT,Cpp03CorpusIT verify`.


## Parser recovery and diagnostic exports (#21)

`parser-recovery` and `inactive-extension` were the fourteenth and fifteenth scenarios. C++ source failures preserve diagnostic exports; strict Java/API failure behavior remains separate.

- [parser-recovery](../src/test/resources/cpp03/parser-recovery/README.md) supplies a compilation database with damaged `a.cpp` and valid `b.cpp`, each with its own macro setting. `ParserRecoveryIT` and `Cpp03CorpusIT` require the retained function before the error, a stop position and raw skipped fragment in `a.cpp`, all expected syntax in `b.cpp`, matching recovering API/CLI JSON, actual readable SVG and status 1. Both JSON/SVG-only and combined source-output requests are tested; neither may emit C++.
- [inactive-extension](../src/test/resources/cpp03/inactive-extension/README.md) keeps the packed attribute in `#if 0`, successfully exports and executes original/generated C++ under both strict compilers. The paired active test reports `main.cpp:1:8` at `__attribute__`, includes the C++03 reason, writes diagnostic JSON and blocks source output.
- `ParserRecoveryIT` also checks missing initializer range/text, exact nonempty skipped text, continuation to the next declaration, variadic macros, `LL`/binary/raw literals, repeated unexpected tokens, truncated functions and EOF. A damaged unit must terminate and must not hide its valid neighbor. `ParserRecoveryTest` checks copying and Visitor dispatch; `RecoveryExportTest` checks schema, DOT and generation guards.

For a C++ source failure, assert status 1, the reason in `stderr()` and `diagnosticSnapshot()`; this also checks that the source output directory does not exist. Assert requested SVG separately. `expectNoExports()` remains correct for invalid configuration/options, Java parse failures and other failures before a diagnostic graph exists. Healthy JSON omits optional error fields. The original #21 milestone excluded per-function recovery. Current `Cpg` honors the requested stage, analyzes complete bodies and excludes incomplete/invalid flows; partial input bypasses normalization/folding. Requested stage alone is not evidence of complete analysis.

```sh
mvn -Dit.test=ParserRecoveryIT,Cpp03CorpusIT verify
```

This is focused acceptance. Use the full-run commands above for complete evidence. The saved workflow installs Graphviz before Maven.

## Diagnostic provenance and grouped summaries (#22)

[diagnostic-provenance](../src/test/resources/cpp03/diagnostic-provenance/README.md) was the sixteenth mandatory scenario. `DiagnosticProvenanceIT` and the corpus invoke the same acceptance helper on four ordinary compilation databases:

- `assignment`: `ASSIGN(total);` retains the missing-expression position before `;`, a call-site note and the exact `=` token in the header. An independent missing initializer after recovery remains a second error and its declaration survives.
- `paste`: `JOIN(+, *)` produces one primary CLI error on the invocation. API/JSON must contain all three labeled ranges: the left and right arguments in `.cpp` and `##` in the header. Assertions check role, absolute physical file, start/end offsets, lines and columns, plus the failed expansion context.
- `shared`: the same error in one physical header from `a.cpp` and `b.cpp` yields one group, two translation units and two retained contexts. A separate API check uses two variants of one physical `.cpp`: one translation unit, two variants.
- `causes`: two commands define `BAD` differently, producing `+*` and `+/` at the same physical macro invocation. The distinct reasons must yield two groups rather than collapse by line/range.

Every case compares full API/CLI JSON, counts primary `error [code]` lines against groups, checks build-variant/include/expansion context, and checks unchanged groups after `edit().build()` and `NameResolver.resolve`. The shared-header and different-reason cases reverse translation-unit order and require equal group records and root `diagnosticGroups` JSON. Summary determinism does not require identical AST IDs or file ordering after reversing inputs. These diagnostic oracles require status 1 and blocked source output, without claiming native execution, semantic grouping or automatic repair.

Additional `DiagnosticProvenanceIT` regressions cover two independent missing expressions inside one macro, unsafe assignment recovery inside parentheses/conditions/calls, and distinct expanded operands (`40000`/`50000`) for invalid `#line`/`#include` directives. The directive cases compare API/CLI JSON, macro ancestry and reversed summaries; unsafe recovery must stop with one original error, not create a cascade. `DiagnosticProvenanceTest` also covers conditional operands, incompatible redefinition signatures and labeled previous/new definitions, repeated-include occurrence IDs, strict exception provenance, and the same safe assignment boundary in the lower-level Java recovering parser.

```sh
mvn -Dit.test=DiagnosticProvenanceIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=diagnostic-provenance verify
# Full regression and mandatory corpus inventory, without a scenario filter:
mvn clean verify
python3 scripts/check-cpp03-reports.py
```

Run the report check only after the full corpus. At the #22 milestone, sixteen scenarios required eighteen corpus results, including the two infrastructure checks; use the current inventory count above. Existing Java/C++ tests and strict GCC/Clang checks for valid corpus programs remain mandatory. The fixture README gives CLI commands and coordinate oracles; the [model guide](model-guide.en.md#diagnostic-provenance-and-grouping-22) defines the API and JSON fields.

## Platform profiles and declarators (#23)

[platform-declarators](../src/test/resources/cpp03/platform-declarators/README.md) was the seventeenth mandatory scenario. `PlatformDeclaratorsIT` and `Cpp03CorpusIT` share `PlatformDeclaratorsAcceptance` over the same ordinary source files. It checks the distinction between arrays of pointers, pointers to arrays and function pointers, abstract function parameters, a member pointer, cv qualifiers, references, local comma-separated declarations and a written typedef linked to its canonical type. A `PARSED` copy preserves the complete JSON snapshot, and parsed API and packaged CLI snapshots agree after accounting for their documented input-attribution difference.

The target checks compare default and `long32` profiles, target bytes of 8 and 16 bits, long integer ranges, absent versus explicit-null alignment, invalid settings and distinct per-unit variants. `GREEN` written types and `TypeRepresentation` records survive an unchanged edit; this does not require full JSON equality, because editing drops derived CFG/DFG edges. `FOLDED` returns 20 under the default profile and 16 under `long32`, with API/CLI parity. Ordinary-field layout is checked separately: `char, long, char` has default offsets 0/8/16 target bytes and size 24; changing long to 32 bits with 32-bit alignment gives size 12. Unknown layout facts must not take host values.

`PlatformEncodingIT` separately writes ISO-8859-1 source/header files, exports through the packaged CLI and verifies their exact bytes and reparsing under the same profile. It also requires no exports for unrepresentable generated text or conflicting encodings for one physical file. These add no inventory entries.

The corpus compiles and executes original and generated default-profile sources using both `g++` and `clang++` with `-std=c++03 -pedantic-errors`, expecting empty output and exit 20. The custom `long32` size/result checks are **API/CLI model oracles**, not native executions claiming the host compiler uses that arbitrary ABI. The native oracle assumes the controlled test configuration, not universal LP64. Function/member-pointer/class layout and the fuller literal, conversion and overload work remain outside #23 as described in the [model guide](model-guide.en.md#platform-profiles-and-recursive-declarators-23).

```sh
mvn -Dit.test=PlatformDeclaratorsIT,PlatformEncodingIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=platform-declarators verify
# Full acceptance, without filtering the corpus:
mvn clean verify
python3 scripts/check-cpp03-reports.py
```

At the #23 milestone, seventeen scenarios required nineteen corpus results including the two infrastructure checks; use the current inventory count above. The guard requires the complete corpus; existing Java/C++ regressions remain mandatory.

The `platform-declarators/signed-min` companion requires both compilers before/after folding. Its `int`/`long` overload oracle returns 1: generated signed minima retain their type. This oracle does not establish CPG overload resolution (#30).

## Scopes, declarations and linkage (#24)

[scopes-linkage](../src/test/resources/cpp03/scopes-linkage/README.md) was the eighteenth mandatory scenario. `ScopesLinkageIT` and `Cpp03CorpusIT` share `ScopesLinkageAcceptance`, importing the same three ordinary `.cpp` files through their compilation database. The positive contract covers:

- Two `config.h` typedef occurrences at the same physical range, with canonical types `long` and `int`. Only the `a.cpp` command defines `WIDE`; each `Width` use must reference its own translation unit's typedef.
- Separate file-scope `static helper` functions and anonymous-namespace `hidden` functions in `a.cpp` and `b.cpp`; calls must stay within their unit even after selecting the complete build composition.
- Reopened namespaces, a namespace alias, a using-declaration, sequential typedefs, and one using-directive traversed by two distinct type uses. API/JSON must preserve both per-use chains, the shared directive and its namespace target, and the absence of a using step for an explicitly qualified name.
- A named pointer type referencing the visible forward class declaration, with a forward-declaration-to-definition link and false completeness at the earlier use; C language-linkage declarations and definitions remain connected.
- Full API/packaged-CLI JSON parity for the same database, reference targets owned by the snapshot, preserved header text, generated-source reparse under the same per-unit flags, and equivalent links in copied and selected snapshots.

The native oracle separately compiles each translation unit with `g++` and `clang++`, always using `-std=c++03 -pedantic-errors`, applying `-DWIDE` **only to `a.cpp`**, then links the objects. Both original and generated programs must produce empty stdout and exit 33. Passing a global `-DWIDE` to every file would test a different program and is not an acceptable substitute. No native execution is used as an oracle for an invalid incomplete-type or ODR case.


The linkage section continues in [the retained guide](e2e.en.md#scopes-declarations-and-linkage-24), from original line 126, owned by #269.
