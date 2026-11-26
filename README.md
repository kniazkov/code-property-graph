# Code Property Graph

An immutable syntax tree and code property graph model, plus a rewritten
specification of programming languages as independently checkable statements.

## Layout

```text
docs/                 format specifications
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

## Build

```text
mvn verify
```

The build runs unit tests, Checkstyle, and the JaCoCo coverage gate.

## Documentation

- `docs/SPEC_FORMAT.md` — statement and check format, automatic-check algorithms.
- `docs/JSON_FORMAT.md` — JSON format for the AST and CPG.
