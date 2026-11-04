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
