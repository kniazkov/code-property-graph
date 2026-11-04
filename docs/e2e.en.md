# Reproducible end-to-end tests

Run `mvn verify` with JDK 21, GCC (`g++`) and Clang (`clang++`) on PATH. Failsafe runs the packaged JAR, including the existing Java/C++ translation regressions. C++03 acceptance does not require expanding translation to Java.

## C++03 corpus

The inventory is `src/test/resources/cpp03/scenarios.txt`. Every entry names a directory containing ordinary files under `sources/` and a `scenario.properties` expectation. `Cpp03CorpusIT` loads those exact files through both `Cpg.read` and the CLI. `CliSandbox.directory` preserves nested relative paths and headers. Nested source discovery and preprocessing are not claimed by this infrastructure change.

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

The fourth corpus entry, [square](../src/test/resources/cpp03/square/README.md), uses explicit `source-fixture` mode while macro processing is pending. It verifies both original source (58) and a hand-written edited oracle (64) with GCC and Clang and checks its portable compilation database. It is counted and never skipped, but does not claim a successful CPG graph. Run it with `-Dcpg.cpp03.scenario=square`.
