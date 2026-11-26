# Specification format for statements and automatic checks

Version: 0.1  
Date: 2026-11-24  
Status: draft

## 1. Purpose

This document describes the format for storing a rewritten programming language
specification as a set of independently checkable statements, the format of
auxiliary files, and the algorithms of automatic checks.

Format goals:

- represent the specification as a finite set of atomic statements;
- make every statement independently checkable;
- separate normative requirements from informative examples and notes;
- provide traceability from statement to check to result;
- avoid storing the source specification text in the public repository;
- allow different oracles instead of relying on a compiler alone.

This document is not legal advice. Questions about distributing the source
specification and derivative works must be reviewed by a lawyer.

## 2. Terms

**Statement** — the smallest fragment of the specification that has one
subject, one modality, and can be checked independently.

**Normative statement** — a statement that imposes a requirement, prohibition,
or permission, or defines mandatory behavior.

**Informative statement** — a note, example, clarification, or other element
that is not a requirement.

**Oracle** — the source of the expected result of a check. An oracle may be a
specification rule, a table, an independent grammar, an independent evaluator, a
formal model, a metamorphic relation, a compiler, or a human.

**Check** — a reproducible experiment that compares a statement with an observed
result.

**Trust** — an assessment of the independence and reliability of a check. Trust
is not the same as "the check passed"; it is a property of the oracle.

**Provenance** — information about the origin of a statement: a private locator
in the source document and a fingerprint of the fragment. Provenance does not
contain the source text in the public repository.

## 3. Legal framework and provenance

Changing the format does not automatically remove copyright protection.
Rewriting, translation, and paraphrase may be derivative works. Therefore the
public repository stores only:

- independently formulated statements;
- identifiers;
- checks and test data;
- tables obtained as facts;
- provenance metadata without the source text.

The source PDF/TXT/HTML and the statement-to-source-paragraph map are stored in
a private vault. The public repository may contain only:

- `private_locator` — for example, `p.9, 2.13.2/1`;
- `fingerprint` — a hash of the source fragment;
- `extracted_at` — extraction date;
- `reviewer` — reviewer identifier.

Before publication, CI checks for verbatim matches with the private corpus. The
threshold and algorithm of that check are defined separately and do not disclose
the source text.

## 4. Repository structure

The repository hosts several languages side by side. Shared assets live at the
root; everything language-specific lives under `languages/<lang>/`.

```text
/
  README.md
  LICENSE
  NOTICE
  CONTRIBUTING.md
  .github/
    workflows/
      verify.yml
  docs/
    SPEC_FORMAT.md
    JSON_FORMAT.md
    cpp03/
      SPEC_REFORMAT_PLAN.md
      PARSER_COVERAGE.md
      PHASE0_REPORT.md
  schema/
    statement.schema.json
    check.schema.json
    test.schema.json
  tools/
    README.md
    requirements.txt
    spec_common.py
    validate.py
    verify.py
    coverage.py
    diff.py
    license_check.py
  provenance/
    README.md
    source-lock.yaml
    reviewers.yaml
  languages/
    cpp03/
      language.yaml
      inventory.yaml
      pilot.lock.yaml
      statements/
        CPP03-2.13.yaml
        CPP03-5.1.yaml
      grammar/
        com.kniazkov.cpg.ebnf
        cpp03-negative.ebnf
      tables/
        keywords.yaml
        alternative-tokens.yaml
        escape-sequences.yaml
        execution-charset.yaml
      checks/
        CPP03-2.13.2-001.yaml
      tests/
        lex/
          char-literal-type.yaml
          char-literal-value.yaml
          char-literal-equiv.yaml
      oracles/
        char_literal_eval.py
        integer_literal_eval.py
      provenance/
        README.md
    java/
      language.yaml
      statements/
      grammar/
      tables/
      checks/
      tests/
      oracles/
      provenance/
  reports/
    cpp03/
      coverage.json
      results.json
    java/
    summary.json
  generated/
    cpp03/
      statements.md
      coverage.md
      statements.db
    java/
```

Rules:

- `language.yaml` is the single entry point for a language: `id`, `name`,
  `edition`, `id_prefix`, private source reference, status, and directory names.
- Shared assets are `schema/`, `tools/`, and the provenance policy in
  `provenance/README.md`.
- Language-specific assets are statements, grammar, tables, checks, tests,
  oracles, and provenance records.
- A language may reuse a table from another language only through an explicit
  reference; shared tables may be promoted to a top-level `shared/tables/`
  directory.
- Reports and generated files are produced per language and aggregated in
  `reports/summary.json`.
- CI runs schema and ID validation once, then a matrix job per
  `languages/*` directory.

The `provenance/` directories in the public repository contain only format
descriptions and rules, not source texts or the private map.

## 5. General format rules

- Encoding: UTF-8 without BOM.
- Line endings: LF.
- Indentation: two spaces, tabs forbidden.
- Primary format for statements and checks: YAML 1.2.
- Schemas: JSON Schema.
- Generated files in `generated/` are not edited manually.
- All identifiers are unique within an edition.
- Identifiers are not reused or renumbered.
- Changes are made as additions or `deprecated` marks, not deletions.

## 6. Identifiers

Base scheme:

```text
CPP03-<clause>-<number>
```

Examples:

```text
CPP03-2.13.2-001
CPP03-5.1-004
CPP03-A-012
CPP03-DEF-007
```

Rules:

- `CPP03` — the language edition.
- `<clause>` — clause or appendix number.
- `<number>` — sequence number within the clause, starting at `001`.
- `DEF` is used for definitions.
- `GRAM` is allowed for grammar productions.
- When statements are merged or split, the old ID receives `deprecated: true`
  and the new one receives `supersedes`.
- When a statement moves to another clause, the ID is preserved and `clause` is
  updated.

## 7. Statement model

### 7.1 Required fields

```yaml
schema_version: 1
id: CPP03-2.13.2-001
edition: N1638
clause: "2.13.2"
type: requirement
modality: shall
normative: true
title: "Type and value of an ordinary character literal"
statement: >
  An ordinary character literal containing exactly one c-char has type char,
  and its value equals the numerical value of the encoding of that c-char in
  the execution character set.
subject: "ordinary character literal with one c-char"
condition: "literal is ordinary and contains exactly one c-char"
requirement: "type is char; value equals execution character set encoding"
exceptions: []
depends_on: []
related: []
provenance:
  private_locator: "p.9, 2.13.2/1"
  fingerprint: "sha256:..."
  extracted_at: "2026-11-24"
verification: []
status: draft
```

### 7.2 Statement types

- `requirement` — a requirement, prohibition, or permission.
- `definition` — a definition of a term.
- `grammar` — a grammar production.
- `constraint` — a constraint or diagnostic rule.
- `behavior_undefined` — undefined behavior.
- `behavior_implementation_defined` — implementation-defined behavior.
- `behavior_unspecified` — unspecified behavior.
- `informative_note` — a note.
- `informative_example` — an example.
- `table_row` — a row of a requirements table.
- `cross_reference` — a reference or dependency.

### 7.3 Modalities

- `shall` — mandatory requirement.
- `shall_not` — prohibition.
- `may` — permission.
- `is` — definition or statement about a type/value.
- `is_not` — negative definition.
- `implementation_defined` — the implementation must define and document it.
- `undefined` — no requirements.
- `unspecified` — the implementation need not document it.
- `informative` — informative statement.

### 7.4 Atomization rules

One statement must contain one requirement. If a sentence contains several
independent requirements, it is split.

Example source sentence:

> An ordinary character literal with one c-char has type `char`, and its value
> equals the encoding of that c-char.

Atomization:

```text
CPP03-2.13.2-001: the type of an ordinary character literal with one c-char is char.
CPP03-2.13.2-002: the value of an ordinary character literal with one c-char equals the encoding of the c-char.
```

If a requirement depends on context, the context is recorded in `condition`
rather than hidden in the `statement` text.

### 7.5 Normativity

`normative: true` means the statement is part of conformance coverage.
`normative: false` means the statement is informative; it may be checked for
consistency but cannot be required of an implementation.

### 7.6 Statuses

- `draft` — draft, checks may be missing.
- `reviewed` — technical and legal review passed.
- `verified` — all mandatory checks pass.
- `disputed` — there is a dispute about the wording or the oracle.
- `deprecated` — the statement was replaced or removed from a new edition.
- `waived` — a check was officially deferred with a stated reason.

## 8. Check model

### 8.1 Required fields

```yaml
schema_version: 1
check_id: ast-type
statement_id: CPP03-2.13.2-001
kind: parser_ast
oracle: spec_rule
trust: high
inputs:
  source: "'a'"
expected:
  ast:
    kind: CharacterLiteral
    prefix: none
    cchars: ["a"]
    type: char
negative_control:
  mutate: "type: int"
  expected: fail
evidence:
  - parser_version
  - ast_dump
```

### 8.2 Check kinds

- `parser_ast` — check the AST built by the parser.
- `parser_diagnostic` — check parser diagnostics.
- `compile_pass` — the program must compile.
- `compile_fail` — the program must be rejected.
- `runtime` — the program must produce the expected result.
- `table_driven` — check against a table.
- `property` — check a property.
- `metamorphic` — check equivalence of transformations.
- `round_trip` — check `parse → print → parse`.
- `differential` — compare with another oracle.
- `formal` — check with a formal model.
- `manual` — manual check.

### 8.3 Oracles

- `spec_rule` — a rule derived directly from the statement.
- `spec_table` — a table from the specification.
- `independent_grammar` — a grammar written separately from the parser.
- `independent_evaluator` — an independent type/value evaluator.
- `formal_model` — a formal model.
- `metamorphic_relation` — an equivalence relation.
- `compiler` — clang/g++ and other compilers.
- `manual` — a human.

### 8.4 Trust levels

- `high` — a table, an invariant, an independent formal model.
- `medium` — an independent parser, an independent evaluator, a property.
- `low` — differential check by a compiler, manual check without a formal
  criterion.

Statement trust is computed as follows:

- `high` if there is at least one `high` check and no failures;
- `medium` if there is no `high` but there is a `medium` check and no failures;
- `low` if there are only `low` checks;
- `none` if there are no mandatory checks.

### 8.5 Results

- `pass` — the result matched the expected one.
- `fail` — the result did not match.
- `error` — the check could not run.
- `skipped` — the check is not applicable in this environment.
- `waived` — the check was officially deferred.
- `disputed` — the result requires manual review.

### 8.6 Evidence

For each check the following is stored:

- parser/tool version;
- compiler version and flags, if a compiler was used;
- target platform and encoding;
- input data;
- expected result;
- actual result;
- exit code;
- hash of the input data and test file;
- date and duration.

## 9. Auxiliary files

Paths in this section are relative to a language root, for example
`languages/cpp03/`. Shared schemas and tools remain at the repository root.

### 9.1 Schemas

`schema/statement.schema.json`, `schema/check.schema.json`, and
`schema/test.schema.json` describe allowed fields and types. Schemas are
versioned together with the format.

### 9.2 Independent grammar

`grammar/com.kniazkov.cpg.ebnf` — a grammar written independently of the parser.
It is used for test generation and production coverage checks. Tests must not be
generated by the same grammar from which the parser was generated.

`grammar/cpp03-negative.ebnf` — rules describing deliberately invalid constructs
for negative tests.

### 9.3 Tables

`tables/keywords.yaml`, `tables/alternative-tokens.yaml`,
`tables/escape-sequences.yaml`, and `tables/execution-charset.yaml` contain
facts extracted from the specification. Tables do not contain long quotations.

Example:

```yaml
schema_version: 1
table: execution-charset
edition: N1638
charset: ascii
entries:
  "a": 97
  "A": 65
  "0": 48
  "\\n": 10
  "\\0": 0
```

### 9.4 Tests

Tests are stored in `tests/` and described by YAML manifests. A test does not
contain expectations derived from the parser itself; expectations come from a
statement, a table, an independent grammar, or an independent evaluator.

Example:

```yaml
schema_version: 1
test_id: char-literal-type
statement_id: CPP03-2.13.2-001
cases:
  - name: ordinary-single
    source: "'a'"
    expected:
      kind: CharacterLiteral
      prefix: none
      cchars: ["a"]
      type: char
  - name: wide-single
    source: "L'a'"
    expected:
      kind: CharacterLiteral
      prefix: wide
      cchars: ["a"]
      type: wchar_t
  - name: multichar
    source: "'ab'"
    expected:
      kind: CharacterLiteral
      prefix: none
      cchars: ["a", "b"]
      type: int
```

### 9.5 Oracles

`oracles/` contains independent implementations of rules. For example,
`char_literal_eval.py` computes the value of a character literal from the
encoding table and escape-sequence rules without using the parser.

### 9.6 Tools

`tools/validate.py` validates schemas and ID uniqueness.  
`tools/verify.py` runs checks.  
`tools/coverage.py` builds a coverage report.  
`tools/diff.py` performs differential checks.  
`tools/license_check.py` looks for verbatim matches with the private corpus.

### 9.7 Reports

`reports/results.json` contains check results.  
`reports/coverage.json` contains statement and production coverage.  
`generated/statements.md` is human-readable documentation assembled from
statements.

## 10. Algorithms of automatic checks

The algorithms of the checks are described below. For each one, the purpose,
inputs, steps, oracle, example, and limitations are given.

### 10.1 Generation from an independent grammar

**Purpose.** Verify that the parser accepts every string derived from the
independent grammar and builds the expected structure.

**Inputs.** Independent grammar, string generator, parser, AST matching rule.

**Steps.**

1. Load the independent grammar.
2. Generate a string with a given derivation depth.
3. Save the grammar derivation: production, derivation tree, expected structure.
4. Pass the string to the parser.
5. Verify that the parser returned no error.
6. Match the AST against the expected structure.
7. Repeat for all productions and depths.

**Oracle.** The independent grammar and its derivation tree.

**Example.** For the production `nonzero-digit: one of 1 2 3 4 5 6 7 8 9`, the
generator creates the string `7`. The parser must build a `nonzero-digit` node
with value `7`, not an `integer-literal` with a different parse.

**Limitation.** If the generator and the parser use the same grammar, the check
is circular. The grammar must be written separately.

### 10.2 Negative generation

**Purpose.** Verify that the parser rejects strings that are not derived from the
grammar.

**Inputs.** Independent negative grammar or mutator, parser, expected diagnostic.

**Steps.**

1. Take a valid string.
2. Apply a mutation that violates a specific production.
3. Ensure that the mutation really makes the string invalid according to the
   independent model.
4. Pass the string to the parser.
5. Verify that the parser returned an error.
6. Verify the diagnostic category and position.
7. Ensure that the valid string without the mutation is accepted.

**Oracle.** The independent negative grammar or mutation model.

**Example.** For `integer-literal`, replace `0x` with `0z`. The parser must
reject the string as an invalid hexadecimal-literal, not accept it as an
identifier.

**Limitation.** Not every mutation makes a string invalid; such cases must be
filtered out by the independent model.

### 10.3 Production coverage

**Purpose.** Ensure that every production of the independent grammar is covered
by at least one test.

**Inputs.** List of productions, tests, `production → tests` map.

**Steps.**

1. Extract all productions from the grammar.
2. For each test, determine which productions it covers.
3. Build a report: covered, uncovered, partially covered.
4. Generate tests for uncovered productions.
5. Fix the threshold: 100% of productions for normative rules.

**Oracle.** The list of productions and the coverage map.

**Example.** The production `hex-quad` is considered covered if there is a test
for `\u0041` and a test for `\U00000041` where `hex-quad` appears in the parse.

**Limitation.** Production coverage does not guarantee semantic correctness.

### 10.4 Error coverage

**Purpose.** Ensure that every expected diagnostic is reachable and checked.

**Inputs.** List of diagnostics from statements, tests, `diagnostic → tests` map.

**Steps.**

1. Collect all statements with `kind: constraint` or `parser_diagnostic`.
2. Create a minimal negative example for each diagnostic.
3. Verify that the parser emits a diagnostic of the required category.
4. Verify that the position points to the expected token.
5. Verify that the diagnostic is not emitted without the violation.

**Oracle.** The statement and the expected diagnostic category.

**Example.** For an `integer-suffix` violation after `0x`, the parser must emit
an "invalid hexadecimal-literal" diagnostic, not "unknown identifier".

**Limitation.** The exact message text must not be checked; only the category
and position are checked.

### 10.5 Maximal munch and tokenization invariants

**Purpose.** Verify that the lexer chooses the longest valid token and handles
overlapping sequences correctly.

**Inputs.** Set of strings, token table, translation-phase rules.

**Steps.**

1. Compose strings where different splits are possible: `a+++++b`, `a+++b`,
   `>>=`, `>=`, `...`, `##`.
2. For each string, enumerate all valid splits.
3. Choose the split by the maximal-munch rule.
4. Compare with the parser tokens.
5. Verify that alternative tokens (`and`, `&&`) produce the same structure.
6. Verify digraph/trigraph and line-continuation escaping.

**Oracle.** The token table and the maximal-munch rule.

**Example.** The string `a+++++b` must be parsed as `a ++ ++ + b`, not
`a + ++ ++ b` or otherwise. If the parser chooses a different split, the test
fails.

**Limitation.** Some sequences depend on context; they must be moved into
separate statements.

### 10.6 Round-trip

**Purpose.** Verify that an AST can be printed back to text and parsed again
without losing structure.

**Inputs.** Parser, canonical printer, AST comparison function.

**Steps.**

1. Parse the source string into an AST.
2. Print the AST with the canonical printer.
3. Parse the printed string again.
4. Compare the ASTs up to normalization.
5. Repeat for a corpus of valid strings.

**Oracle.** The structural AST equivalence function.

**Example.** `'a'` → AST → `'a'` → AST. Both ASTs must be equivalent, including
the type `char` and the value.

**Limitation.** Round-trip verifies representability, not semantic correctness.

### 10.7 Metamorphic checks

**Purpose.** Verify that transformations that do not change program meaning do
not change the AST or the result.

**Inputs.** Set of transformations, parser, comparison function.

**Steps.**

1. Take a valid program.
2. Apply a transformation: add spaces, comments, line breaks, replace `and` with
   `&&`, replace a digraph with an ordinary character.
3. Parse the original and the transformed program.
4. Compare the AST or the result.
5. For transformations that must change meaning, verify the expected change.

**Oracle.** The equivalence relation defined by the statement.

**Example.** `a and b` and `a && b` must produce the same AST. `'a'` and
`'\x61'` on ASCII must produce the same value.

**Limitation.** Not all transformations are legal; the list of transformations
must be explicit.

### 10.8 Grammar fuzzing

**Purpose.** Find parser crashes, hangs, and nondeterminism.

**Inputs.** String generator, parser, timeout, invariants.

**Steps.**

1. Generate a string from the grammar or mutate the corpus.
2. Run the parser with a timeout.
3. Verify that the process terminated.
4. Verify that there is no crash or leak.
5. Verify that a repeated run produces the same result.
6. Save the minimal reproducing example.

**Oracle.** Invariants: termination, no crash, determinism.

**Example.** Generate deeply nested parentheses and verify that the parser does
not crash with a stack overflow but emits an error or handles the input.

**Limitation.** Fuzzing does not prove correctness; it finds defects.

### 10.9 Mutation fuzzing

**Purpose.** Verify that small changes to a valid program produce a predictable
change in the AST or diagnostic.

**Inputs.** Corpus of valid programs, mutation model, parser, expected changes.

**Steps.**

1. Take a valid program.
2. Apply a mutation: replace an operator, literal, or identifier.
3. Determine the expected effect: error, different type, different value.
4. Parse the mutated program.
5. Compare with the expected effect.
6. Save mutants that were not detected.

**Oracle.** The mutation model and the statement.

**Example.** Replace `'a'` with `'b'`; the type remains `char`, the value
changes to the code of `'b'`. Replace `'a'` with `L'a'`; the type changes to
`wchar_t`.

**Limitation.** Equivalent mutants must be excluded.

### 10.10 Independent evaluator

**Purpose.** Check types and values computed by the parser with a separate
implementation of the rules.

**Inputs.** AST, independent evaluator, tables, statements.

**Steps.**

1. Pass the AST to the independent evaluator.
2. The evaluator applies type and value rules.
3. Compare the result with the parser result.
4. On a mismatch, save both results and a minimal example.
5. Verify the negative control: the evaluator must distinguish a correct value
   from an incorrect one.

**Oracle.** The independent evaluator and tables.

**Example.** For `'a'`, the evaluator takes the execution charset table and
returns `97` for ASCII; the parser must return the same value.

**Limitation.** The evaluator and the parser may share a defect if written from
the same model. An independent implementation is required.

### 10.11 Property-based testing

**Purpose.** Check properties that must hold for all inputs from a given class.

**Inputs.** Generators, properties, parser.

**Steps.**

1. State a property: for example, `'0' + 1 == '1'`.
2. Generate inputs satisfying the precondition.
3. Check the property on each input.
4. On violation, save a minimal counterexample.
5. Repeat with different seeds.

**Oracle.** The property derived from the statement.

**Example.** For all digits `d` from `0` to `8`, `'d' + 1 == 'd+1'` must hold.
For all ordinary character literals with one c-char, the type must be `char`.

**Limitation.** The property must be proven or justified; otherwise a false
statement may be checked.

### 10.12 Table-driven checks

**Purpose.** Check behavior against tables extracted from the specification.

**Inputs.** Table, parser, matching rule.

**Steps.**

1. Load the table.
2. Build an input for each table row.
3. Obtain the parser result.
4. Compare with the expected value from the table.
5. Verify that all table rows are covered.
6. Verify the negative control: change the expectation and ensure the test
   fails.

**Oracle.** The table.

**Example.** Escape-sequence table: `\n` → 10, `\t` → 9, `\0` → 0. The parser
must produce these values.

**Limitation.** The table must be extracted without errors; an error in the
table makes the check false.

### 10.13 Type and constraint table checks

**Purpose.** Check invariants of the symbol table, scopes, and type conversions.

**Inputs.** AST, symbol table, type model.

**Steps.**

1. Build the symbol table.
2. Verify that each name has exactly one declaration in its scope.
3. Verify that references resolve to valid declarations.
4. Verify that type conversions follow the rules.
5. Verify that forbidden conversions are rejected.

**Oracle.** The independent type and constraint model.

**Example.** For `int x; x = 'a';`, the `char` to `int` conversion is allowed;
for `int* p; p = 'a';`, it must be rejected.

**Limitation.** Applicable only to a formalizable subset.

### 10.14 Abstract interpretation

**Purpose.** Check constant expressions, overflows, promotions, and narrowing
without executing the program.

**Inputs.** AST, abstract semantics, value domains.

**Steps.**

1. Build the abstract state.
2. Evaluate the expression in the abstract domain.
3. Verify that the result matches the statement.
4. Verify that undefined operations yield `undefined`.
5. Compare with the independent evaluator.

**Oracle.** Abstract semantics.

**Example.** For `2147483647 + 1` in a 32-bit `int`, the result must be
`undefined` or `long` depending on the rules; abstract interpretation must show
this.

**Limitation.** Difficult for the whole language; effective for a subset.

### 10.15 SMT and bounded model checking

**Purpose.** Formally check constraint satisfiability for a small subset.

**Inputs.** Formal model, constraints, SMT solver.

**Steps.**

1. Encode the statement and rules into logic.
2. State the property to check.
3. Run the solver.
4. If the property does not hold, obtain a counterexample.
5. Check the counterexample on the parser.
6. Record the model bounds.

**Oracle.** The formal model and the solver.

**Example.** Verify that for all `c-char` from a given set, the literal type is
`char` and the value lies in the `char` range.

**Limitation.** Does not scale to all of C++; applicable to individual rules.

### 10.16 Differential interpreter

**Purpose.** Compare two independent semantics evaluators.

**Inputs.** Two interpreters, program corpus.

**Steps.**

1. Execute the program in the first interpreter.
2. Execute the same program in the second.
3. Compare results, types, and diagnostics.
4. On a mismatch, save a minimal example.
5. Classify the mismatch: bug, undefined behavior, different model.

**Oracle.** The second interpreter.

**Example.** One interpreter treats `'a'` as ASCII 97, the other as EBCDIC; the
mismatch must be explained by the environment, not treated as a bug.

**Limitation.** Both interpreters may be wrong in the same way.

### 10.17 Differential check of two parsers

**Purpose.** Compare two independent parsers on the same corpus.

**Inputs.** Two parsers, corpus, AST comparison function.

**Steps.**

1. Parse the corpus with the first parser.
2. Parse it with the second.
3. Compare accept/reject.
4. Compare ASTs up to normalization.
5. Classify mismatches.
6. Save minimal examples.

**Oracle.** The second parser.

**Example.** A hand-written recursive descent parser and a parser generator must
parse `a+++++b` identically.

**Limitation.** Truly independent implementations are required.

### 10.18 Differential check with clang/g++

**Purpose.** Use public compilers as an additional oracle.

**Inputs.** Parser, clang/g++, corpus, version matrix.

**Steps.**

1. Record compiler versions and flags.
2. Compile the corpus.
3. Compare accept/reject and diagnostics by category.
4. Compare behavior where it is defined.
5. Classify mismatches: parser bug, compiler extension, compiler bug, undefined
   behavior.
6. Do not make the compiler the only oracle.

**Oracle.** The compiler.

**Example.** The parser accepts `'a'` as `char`; clang and g++ do the same. If
one compiler disagrees, that is a reason for review, not an automatic verdict.

**Limitation.** Compilers are not a reference; they may support extensions and
have bugs.

### 10.19 Differential check across versions

**Purpose.** Ensure that behavior does not depend on the compiler version or
platform.

**Inputs.** Version matrix, corpus, parser.

**Steps.**

1. Run the checks on each version.
2. Compare the results.
3. Identify implementation-related differences.
4. Update the environment tables.
5. Record the supported versions.

**Oracle.** The version matrix and the statement.

**Example.** `sizeof(int)` differs across platforms; a statement about the range
of an integer literal must be parameterized by the data model.

**Limitation.** Expensive; used for key statements.

### 10.20 Differential check with a conformance suite

**Purpose.** Compare the parser with an open conformance test suite.

**Inputs.** Conformance suite, parser, suite license.

**Steps.**

1. Verify license compatibility.
2. Run the suite.
3. Map tests to statements.
4. Compare the results.
5. Record mismatches.

**Oracle.** The external suite.

**Example.** An open test suite for C++03 lexical rules.

**Limitation.** The suite license must allow use; the suite may be incomplete.

### 10.21 Compile-fail tests

**Purpose.** Verify that an invalid program is rejected with a diagnostic of the
required category.

**Inputs.** Negative example, compiler or parser, expected category.

**Steps.**

1. Prepare a minimal example.
2. Run the compiler/parser.
3. Verify that an error is produced.
4. Verify the category and position.
5. Verify that the example compiles without the error.
6. Save the evidence.

**Oracle.** The statement and the expected category.

**Example.** `int asm;` must be rejected because `asm` is a keyword.

**Limitation.** Do not check the exact message text.

### 10.22 Negative control

**Purpose.** Ensure that a check really distinguishes a correct result from an
incorrect one.

**Inputs.** Check, mutation of the expected result.

**Steps.**

1. Take a check that passes.
2. Change the expected result to a deliberately wrong one.
3. Run the check.
4. Verify that it fails.
5. Restore the original expectation.

**Oracle.** Mutation of the expectation.

**Example.** For `'a' == 97`, change the expectation to `98`; the test must
fail. If it passes, the check is useless.

**Limitation.** The mutation must be semantically significant.

### 10.23 Mutation testing

**Purpose.** Verify that the tests catch defects in the parser.

**Inputs.** Parser, set of mutants, tests.

**Steps.**

1. Introduce a mutation into the parser: change a rule, condition, or boundary.
2. Run the tests.
3. If the tests do not fail, the mutant survived.
4. Analyze the survivors.
5. Add tests or declare the mutant equivalent.

**Oracle.** The test set and the mutants.

**Example.** Change the parser's character literal type from `char` to `int`;
the type test must fail.

**Limitation.** Equivalent mutants are not detected.

### 10.24 Determinism check

**Purpose.** Ensure that a repeated run produces the same result.

**Inputs.** Parser, corpus, several runs.

**Steps.**

1. Run the parser on the corpus.
2. Repeat the run.
3. Compare AST, diagnostics, and exit code.
4. Verify that traversal order is stable.
5. Verify that there is no dependence on time, network, or randomness.

**Oracle.** Idempotence.

**Example.** Two runs on `'a'` must produce the same AST and value.

**Limitation.** Does not catch logic errors, only nondeterminism.

### 10.25 Statement coverage

**Purpose.** Ensure that every normative statement has a check.

**Inputs.** List of statements, list of checks.

**Steps.**

1. Load all statements.
2. For each one, find checks by `statement_id`.
3. Build a report: covered, uncovered, waived.
4. For uncovered normative statements — CI error.
5. For informative ones — warning.

**Oracle.** The statement and check model.

**Example.** `CPP03-2.13.2-001` has the checks `ast-type` and `value-table`;
coverage is 100%.

**Limitation.** The presence of a check does not guarantee its quality; oracle
review is needed.

### 10.26 Check coverage

**Purpose.** Ensure that every check is bound to a statement.

**Inputs.** Checks, statements.

**Steps.**

1. Load all checks.
2. Verify that `statement_id` is present.
3. Verify that the statement exists.
4. Find "orphan" checks.
5. Delete or bind them.

**Oracle.** Referential integrity.

**Example.** The check `char-literal-diff` without a `statement_id` is an error.

### 10.27 Oracle diversity

**Purpose.** Ensure that a statement is not checked only by a compiler.

**Inputs.** Checks, oracles, trust levels.

**Steps.**

1. For each statement, collect the set of oracles.
2. Verify that there is at least one oracle other than `compiler`.
3. If not — mark `trust: low` and require manual review.
4. Build a report on low-trust statements.

**Oracle.** The trust policy.

**Example.** A statement with the only check `compiler-diff` receives
`trust: low`.

**Limitation.** Not all statements can be checked without a compiler; they need
a waiver.

### 10.28 Dependency closure

**Purpose.** Ensure that a check does not require undeclared statements or
resources.

**Inputs.** `depends_on` graph, checks, resources.

**Steps.**

1. Build the statement dependency graph.
2. For each check, find the statements and files it uses.
3. Verify that all dependencies are declared.
4. Verify that there are no cycles.
5. Verify that execution order does not affect the result.

**Oracle.** The dependency graph.

**Example.** The check of the value `'a'` depends on the `execution-charset`
table; the table must be declared.

**Limitation.** Does not check the semantics of dependencies.

### 10.29 Regression matching

**Purpose.** Ensure that a statement change triggers exactly the right set of
checks.

**Inputs.** Statement diff, `statement → checks` map, CI.

**Steps.**

1. Compare the old and new versions of statements.
2. Determine the changed IDs.
3. Find the checks bound to them.
4. Run those checks and dependencies.
5. Verify that unrelated checks are not affected.
6. Save the report.

**Oracle.** The coverage map and the diff.

**Example.** A change to `CPP03-2.13.2-001` triggers `ast-type`, `value-table`,
and `metamorphic`, but not integer literal checks.

**Limitation.** Requires stable IDs and a correct map.

## 11. Example of a complete statement

```yaml
schema_version: 1
id: CPP03-2.13.2-001
edition: N1638
clause: "2.13.2"
type: requirement
modality: shall
normative: true
title: "Type of an ordinary character literal with one c-char"
statement: >
  An ordinary character literal containing exactly one c-char has type char.
subject: "ordinary character literal with one c-char"
condition: "literal is ordinary and contains exactly one c-char"
requirement: "the type is char"
exceptions: []
depends_on: []
related:
  - CPP03-2.13.2-002
provenance:
  private_locator: "p.9, 2.13.2/1"
  fingerprint: "sha256:..."
  extracted_at: "2026-11-24"
verification:
  - id: ast-type
    kind: parser_ast
    oracle: spec_rule
    trust: high
    test: tests/lex/char-literal-type.yaml
  - id: metamorphic
    kind: metamorphic
    oracle: metamorphic_relation
    trust: medium
    test: tests/lex/char-literal-equiv.yaml
  - id: compiler-diff
    kind: differential
    oracle: compiler
    trust: low
    optional: true
    test: tests/lex/char-literal-diff.yaml
status: draft
```

## 12. Example of a check

```yaml
schema_version: 1
check_id: ast-type
statement_id: CPP03-2.13.2-001
kind: parser_ast
oracle: spec_rule
trust: high
inputs:
  source: "'a'"
expected:
  ast:
    kind: CharacterLiteral
    prefix: none
    cchars: ["a"]
    type: char
negative_control:
  mutate: "type: int"
  expected: fail
evidence:
  - parser_version
  - ast_dump
```

## 13. Example of an independent oracle

```python
def eval_char_literal(literal, charset):
    prefix, body = split_prefix(literal)
    if prefix == "L":
        raise NotApplicable("wide literal is a different statement")
    chars = parse_cchars(body)
    if len(chars) != 1:
        raise NotApplicable("multichar literal is a different statement")
    return charset[chars[0]]
```

The oracle does not call the parser. It uses the encoding table and
escape-sequence rules.

## 14. CI pipeline

1. Validate YAML against the schemas.
2. Check ID uniqueness.
3. Check referential integrity of `depends_on` and `related`.
4. Check that every normative statement has a check.
5. Check oracle diversity.
6. Run the checks.
7. Build `reports/results.json`.
8. Build `reports/coverage.json`.
9. Check for verbatim matches with the private corpus.
10. Generate `generated/statements.md` and `generated/statements.db`.
11. Publish the report.

CI must fail on:

- an invalid schema;
- a duplicate ID;
- a normative statement without a check;
- a failed mandatory check;
- a check without `statement_id`;
- a verbatim match with the private corpus.

CI may warn on:

- an informative statement without a check;
- a statement with `trust: low`;
- a waived check;
- a missing optional differential check.

## 15. Satisfactory-parser criterion

A parser is considered satisfactory if:

- every normative statement has at least one check;
- every normative statement has at least one check with an oracle other than a
  compiler, or an official waiver;
- all mandatory checks pass;
- all checks are deterministic and reproducible;
- statement and production coverage is recorded in the report;
- there are no "orphan" checks;
- there are no verbatim matches with the private corpus;
- check results are saved with evidence.

## 16. Pilot

For the first iteration the following is proposed:

- one clause, for example `2.13.2`;
- 30–50 statements;
- one independent grammar;
- three tables: keywords, escape sequences, execution charset;
- 10–15 checks;
- one independent evaluator;
- CI with coverage and negative control.

After the pilot, the following questions must be answered:

- where atomization breaks down;
- how many statements a clause really contains;
- which checks produce false positives;
- which statements cannot be checked without a compiler;
- how long review takes.

## 17. Open questions

- How should statement granularity be defined formally?
- How should requirements spread across a table and a footnote be handled?
- How can `undefined behavior` be checked without imposing false requirements?
- How should checks be parameterized by data models and encodings?
- Which external conformance suites are license-compatible?
- What n-gram threshold should be used for the verbatim-match check?
- Is a separate format needed for grammar productions, or is `type: grammar`
  enough?
