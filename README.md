# Code Property Graph

An immutable syntax tree and code property graph model, plus a rewritten
specification of programming languages as independently checkable statements.

## Layout

```text
docs/                 shared format specifications
docs/cpp03/           C++03-specific documentation
schema/               shared JSON schemas
tools/                shared validation and verification tools
provenance/           shared provenance policy
languages/<lang>/     per-language statements, grammar, tables, checks, tests
reports/              per-language reports and an aggregate summary
generated/            generated documentation and databases
src/                  parser implementation
```

## Languages

| ID | Language | Edition | Root |
| --- | --- | --- | --- |
| CPP03 | C++03 | N1638 | `languages/cpp03/` |

Each language root has a `language.yaml` manifest and its own `statements/`,
`grammar/`, `tables/`, `checks/`, `tests/`, `oracles/`, and `provenance/`
directories.

## Parser

`com.kniazkov.cpg.parser.cpp.LiteralParser` parses C++03 integer, character, and
boolean literals. The statement-to-test map is in
`docs/cpp03/PARSER_COVERAGE.md`.

## Build

```text
mvn verify
```

The build runs unit tests, Checkstyle, and the JaCoCo coverage gate.

## Documentation

Shared:

- `docs/SPEC_FORMAT.md` — statement and check format, automatic-check algorithms.
- `docs/JSON_FORMAT.md` — JSON format for the AST and CPG.

C++03:

- `docs/cpp03/SPEC_REFORMAT_PLAN.md` — checklist-plan for reformatting the full
  C++03 specification.
- `docs/cpp03/PARSER_COVERAGE.md` — statement-to-test map for the literal parser.
