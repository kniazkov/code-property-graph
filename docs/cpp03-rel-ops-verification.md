# C++03 #91: std::rel_ops verification / проверка

## Scope and source basis

C++03 20.2 and 20.2.1 [lib.operators] supply four generic relational operators
in the opt-in nested namespace `std::rel_ops`. The independent interface fixture
`src/test/resources/library-catalogue/rel-ops-20-2-inventory.hpp` has SHA-256
`6b46b24f8797ad509cc379afe13c8ae65980ba5583357674b243affd76acd37b`.
It enumerates four `bool` function templates, each with one type parameter and
two const-reference arguments. It is not generated from the catalogue.

The normative coordinates and prerequisite/result distinctions are retained from
the previously source-reviewed ISO 2003 ledger, including the original source
and paragraph hashes. No fresh examination of the private PDF is claimed here.
The independent signatures also agree with the historical
[official libstdc++ 4.1 API](https://gcc.gnu.org/onlinedocs/libstdc%2B%2B/libstdc%2B%2B-html-USERS-4.1/namespacestd_1_1rel__ops.html).
Compiler-header agreement is supporting evidence, not a substitute for the ISO
wording or proof of complete C++03 conformance.

The catalogue supplies declarations, not STL implementation bodies. Native
checks use the compiler's real `<utility>`. CPG must preserve ordinary calls and
conservative unknown effects rather than folding the operators into invented
pure comparisons. EqualityComparable/LessThanComparable semantic prerequisites
are not enforced by a declaration-only catalogue. Their violation is not
reported as an unconditional standard requirement to diagnose.

## Independent native checks

On 2026-11-07, `rel-ops-signatures.cpp` compiled and ran with GCC and Clang using
`-std=c++03 -pedantic-errors`. Function-pointer assignments check the four exact
specialized signatures. A 3-by-3 value grid checks all four truth tables using
qualified calls and imported infix operators, including equal values and both
orderings.

Separate native probes confirmed, on both compilers:

- No import and `using namespace std` alone do not expose `std::rel_ops`.
- A using-directive inside one function does not leak into another function.
- A qualified call with conflicting deduced argument types is rejected.
- An identical user operator template competes ambiguously after import.
- A matching non-template user operator wins; an explicit using-declaration works.
- A builtin-only operator specialization is rejected by the language rule for
  overloaded operator operands.
- A class lacking the underlying equality operation fails actual native template
  instantiation. This is separate from CPG declaration viability and does not
  prove a mandatory library diagnostic.

The catalogue CI probe syntax-checks the signature fixture. The durable native fixtures under `src/test/resources/rel-ops/native/` are also
executed by the integration test; rejected cases are not syntax-checked as
positive catalogue examples.

## Verification status

Local production/test revision: `ccbb8fcf584576aa997f4dae8f9e684ec64b47d9`.
The published PR identifies the corresponding remote head and source tree.
Fresh executable JAR SHA-256:
`3ee3e2de44469fadd76cdd88538b35841a21e60cb9df00a862e76c56bfc23ad8`.

The final focused Maven verify passed 22 unit tests and three integration tests,
with no failures, errors or skips (2026-11-07). The command was:

```sh
mvn -B -DargLine=-Xmx768m -Dtest=RelOpsLibraryTest,RemainingLibraryOverloadsTest,FunctionTemplateDeclarationTest -Dit.test=RelOpsLibraryIT verify
```

- `RelOpsLibraryTest` independently compares the complete nested signatures,
  checks selected templates for qualified/infix calls and explicit template
  arguments, namespace aliases and using-declarations including leading `::`.
- Include-only and unimported namespaces introduce no operator demand. A single
  demanded declaration stays inside `std::rel_ops`; copied/re-resolved graphs
  retain owned target/origin references. Removing the requesting function clears
  the nested library demand while preserving the original snapshot. Local imports
  do not escape their scope.
- Mixed deduction and builtin-only operator specializations are rejected, valid
  enum arguments remain viable, non-template user overloads win, and equivalent
  user templates remain ambiguous. Missing library prerequisites are deliberately
  separate from these signature/language rules.
- The executable `docs/cpp03-paragraphs/probes/RelOpsEditProbe.java` removes the
  only requesting function with `ProjectEditor`, re-resolves the changed graph,
  checks that the nested library declaration and template instance disappear,
  and verifies the original snapshot remains intact. It passed against the
  fresh implementation. Reproduce after building:

  ```sh
  mkdir -p /tmp/rel-ops-edit
  javac -cp target/cpg-0.0.1-SNAPSHOT.jar -d /tmp/rel-ops-edit docs/cpp03-paragraphs/probes/RelOpsEditProbe.java
  java -cp target/cpg-0.0.1-SNAPSHOT.jar:/tmp/rel-ops-edit RelOpsEditProbe
  ```

- Unknown effects still reach accessible storage; no pure-comparison summary or
  STL runtime body is invented.
- `RelOpsLibraryIT` compares API/CLI JSON and generated text, checks both GREEN and
  FOLDED output, and runs original/generated truth tables with GCC and Clang
  (six compile-and-run executions). Invalid mixed arguments retain error JSON
  and block generation. Nine durable native oracle cases run on both compilers.
- Independent probes additionally covered class/enum aliases, reference typedefs,
  dependent class-template operands, function-reference plus class operands,
  member operator templates, pair/vector and opaque iterator types. The two
  review findings (qualified-call counting and leading-`::` operator using) were
  corrected and independently rechecked.

The first complete unit pass exposed a top-level-only namespace collector in
`RemainingLibraryOverloadsTest`; it was corrected to recurse through namespaces
without entering class scopes, preserving exact index equality. The final focused
run includes this regression. Strict JavaDoc and all 5,250 ledger records pass.

The complete `mvn -B verify` and required CI on the exact published head are
separate merge gates. Their successful completion must be checked before merge;
focused results are not a substitute. CI links and logs are attached to the PR's
Checks view. The private local toolchain used Maven offline mode with cached
artifacts, OpenJDK 21, GCC 14 and Clang 19; CI uses its configured toolchain.

A pre-existing hidden-friend-only operator-template lookup limitation was
reproduced against the previous #90 JAR. This change does not claim to resolve
that wider language-coverage gap (#99).

## Границы

Четыре шаблона должны оставаться внутри `std::rel_ops`; наличие `<utility>` или
`using namespace std` не импортирует их. Проверяются выбранные объявления,
копирование и пересчёт графа, а не только успешный разбор. Нативная STL проверяет
конкретные результаты сравнения, но не превращает CPG в собственную реализацию
STL. Предусловия пользовательских типов и общая приёмка #48 остаются отдельными
обязательствами; записи реестра не повышаются до полного соответствия по одному
успешному примеру.
