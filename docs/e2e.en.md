# Reproducible end-to-end tests

Run `mvn verify` with JDK 21, GCC (`g++`) and Clang (`clang++`) on PATH. Failsafe runs the packaged JAR, including the existing Java/C++ translation regressions. C++03 acceptance does not require expanding translation to Java.

## C++03 corpus

The inventory is `src/test/resources/cpp03/scenarios.txt`. Every entry names a directory containing ordinary files under `sources/` and a `scenario.properties` expectation. `Cpp03CorpusIT` loads those exact files through both `Cpg.read` and the CLI. `CliSandbox.directory` preserves nested relative paths and headers. Translation units come from the folder or explicit compilation database; custom headers are recursively preprocessed.

| Scenario | Expectation | Failsafe test |
| --- | --- | --- |
| return-zero | exit 0, empty output, return of integer zero | Cpp03CorpusIT#corpus |
| arithmetic | exit 0, output `40` followed by newline, folded print node | Cpp03CorpusIT#corpus |
| invalid-token | CLI exit 1, `main.cpp:2:5: Unsupported character: @`, API exception, no exports | Cpp03CorpusIT#corpus |

Repeat one scenario:

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=arithmetic verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage folded \
  --input src/test/resources/cpp03/arithmetic/sources --out target/manual-cpp --json target/manual.json
```

Use `resolved` for the other two scenarios. `invalid-token` intentionally fails. For positive cases, both compilers build original and generated sources with `-std=c++03 -pedantic-errors`. Every executable's exit code and exact output are checked against independent expectations. Failure messages include compiler version and command. A missing compiler is a failure, not an assumption/skip; `missingCompilerIsAnError` tests that contract.

CI runs `python3 scripts/check-cpp03-reports.py` after the full `mvn verify`. It requires one result per inventory entry plus the two harness tests and rejects failures, errors or skips. `inventoryAndNestedCopy` also rejects duplicate entries or unlisted scenario directories. Do not use the report guard after an intentionally filtered local run.

When adding language support, reuse existing nodes first. Add minimal positive, negative and interaction scenarios and update the standard matrix and RU/EN model guides in the same PR. Give each scenario explicit diagnostics or graph expectations and independent output/exit expectations. Undefined or implementation-dependent behavior is not an execution oracle. Editing scenarios must supply separate before/after expectations when introduced. New nodes need builders, visitors, ranges, exports and generation tests.


`compilation-database` is the fifth registered scenario. It uses a mixed C++03/C++11 database with an explicit C++03 override and requires full API/CLI parity and both native compilers. `CompilationDatabaseIT` also checks default-vs-explicit folder equivalence, rejection without the override, lambda rejection despite the override, unsupported-option filenames, and a manually constructed configuration with ordered spaced paths and define/undefine operations. Run `mvn -Dit.test=CompilationDatabaseIT verify`; [fixture details](../src/test/resources/cpp03/compilation-database/README.md).

`lexical-phases` and `unterminated-literal` extend the mandatory corpus to seven scenarios. `LexicalPhasesIT` inspects per-character physical mapping, trigraph ranges, alternative spelling and full API/CLI graph parity using the same source. The positive source executes through both strict compilers; the negative source must report physical 2:5 and produce no exports. See each fixture README for commands. CI runs once per PR open/update/reopen, and on pushes to `master` (including merges); branch pushes no longer duplicate PR runs. Manual dispatch remains available.

`macros` and `missing-header` bring the mandatory inventory to nine scenarios. `square` now uses compilation-database mode with full API/CLI graph parity, two expanded multiplications, and native original/generated output 58. The actual graph edit and the independent hand-written oracle both produce 64 (#17). `macros` covers a macro-generated include name, guards, stringization, pasting, nested arguments, recursion suppression and repeated inclusion with changed definitions. `missing-header` requires failure without exports and a three-file include chain. `PreprocessorIT` inspects those same ordinary sources through the public API and packaged CLI. Run `mvn -Dit.test=PreprocessorIT,Cpp03CorpusIT verify` for this acceptance.

`provenance` is the tenth mandatory corpus scenario. `ProvenanceIT` checks distinct syntax nodes with shared argument origins, nested events, partial/multiple/empty token boundaries, API/CLI parity and edit/build preservation. `scripts/demo.sh` renders real SVG with the layer on/off and validates source-coordinate links. See the fixture README for commands.

`MacroEditingIT` checks immutable captured input, one-operand replacement, preserved header/second invocation, packaged CLI reparsing and nested precedence. The square corpus now compiles the actual edited graph under both compilers; scripts/demo.sh runs examples/cpp03-square/EditSquare.java through the public API and verifies 58→64.

`build-variants` is the eleventh mandatory scenario. Its four-command database is imported through API and JSON-only CLI; server/client compositions are selected through the API, regenerated, reparsed, and compiled with both strict compilers using the matching -D flag. `BuildVariantsIT` separately verifies unknown ambiguity versus known ODR conflict. The public selection demo also runs in scripts/demo.sh.

`conditional-catalog` is the twelfth mandatory scenario. `ConditionalCatalogIT` checks unused header definitions, redefinition IDs, definition-to-expansion/presence links, a presence check for undefined versus `DEBUG=0`, external-input ownership under two compilation commands and the C++03 profile, and physical versus `#line` coordinates. Inactive invalid syntax and an unopened missing header remain in the preserved source without AST nodes or executed checks. Both branches of a partial initializer are parsed through the API; corpus original/generated sources run under strict GCC and Clang with `-DDEBUG=0` and without it (`native.also-without-flag=true`). Negative tests activate the invalid branch or omit `#endif`; the CLI must not export partial output. Run `mvn -Dit.test=ConditionalCatalogIT,Cpp03CorpusIT verify`; [fixture commands and expectations](../src/test/resources/cpp03/conditional-catalog/README.md).

Configuration provenance now distinguishes `java-api`, `cli` and compilation-database sources. `SnapshotAssertions.assertEquivalent` compares effective syntax/analyses/preprocessing while omitting only input-attribution fields where the entry routes differ; dedicated tests inspect those ownership fields. For identical database routes without differing overrides, full JSON equality is still checked. Separate API and CLI `return-zero` golden snapshots preserve the intended origin distinction. Evaluator-specific integer/character choices are tested directly rather than used as portable native execution oracles.
