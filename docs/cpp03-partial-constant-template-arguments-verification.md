# Named integral partial-specialization patterns: bounded #92 package

This package extends the named class-template argument work from PR #125 to
**top-level nondependent integral values in partial-specialization patterns**.
It does not complete offsetof, #92 or final acceptance #48.

This report records the PR #126 checkpoint. Its bool-conversion exclusion is
extended by the separate [integral-to-bool follow-up](cpp03-boolean-partial-arguments-verification.md);
other exclusions and historical test totals below are unchanged.

## Contract and limits

A snapshot-local IntegralConstants query resolves const names, enumerators and
nondependent constant expressions in a partial-specialization argument. The
value must fit the nondependent integral primary parameter type without a
value-changing conversion. The normalized values are shared by candidate
matching, partial ordering and repeated-declaration identity. Dependent type
parameters retain their declaration identity; spelling changes in equivalent
headers do not create competing candidates. Pointer/reference patterns continue
to compare object identities rather than initializer values.

Written headers are never replaced with normalized literals. Their initializer
chains and all visible candidate headers remain bounded-layout dependencies,
including unselected candidates. Copying, folding and editing must preserve
those dependencies so a new snapshot can select a different specialization.

Nested named values within type patterns, dependent value parameter types,
value-changing partial-pattern conversions (including bool 2), unresolved or
nonconstant facts and unproven deduction forms remain outside this package.
Representability is not a general implementation of all original C++03 integral
conversions. No enum representation, profile setting, ABI or generic sizeof
contract is expanded. Existing pragma, source replay, ownership, cycle and
object-size ceiling guards remain in force.

The repository's source-reviewed original-edition ledger is historical evidence;
the private original PDF was not freshly read for this package. Modern native
compilers provide comparison evidence, not permission to import later rules.

## Evidence and gates

Implementation: ClassTemplateSemantics.java. Unit regressions are in
ConstantTemplateArgumentsTest; the strict native/packaged CLI regression is
PartialConstantTemplateArgumentsIT. Native artifacts are written to
`target/partial-constant-template-arguments-oracle/`.

Tests cover named const/enumerator/literal agreement, unmatched primary fallback,
partial ordering, equivalent redeclarations, ambiguity, edits to header facts,
copy/reanalysis/folding/source/JSON, and guarded unsupported patterns. Native
checks use both cstddef spellings and actual preprocessor materialization,
C++03 ICE and size_t checks, selected-layout values and changed requests.

The expanded focused run passed 105 unit tests with no failures, errors or skips.
The 53 Python validator tests (31 macro, 9 source inventory, 13 paragraph) and
strict validation of all 5250 existing ledger records passed. These validate
record consistency, not complete language/library support. Independent read-only
probes additionally checked named arithmetic, short/bool targets, negative-pattern
ordering, type/value parameter alpha-equivalence and the explicit nested guard.
They are not added to the suite totals.

The native/CLI test passed 160 GCC/Clang compiler executions. After strengthening
the nested-pattern unit and adding real partial-header const/enum edits, the final
24-unit rerun and second native test passed another eight compiler executions.
The 24 units overlap the 105-unit total; production code was unchanged between
these runs. All 168 preserved source hashes match their metadata: 84 executions
per compiler, 84 actual preprocessor materializations and 80 edited variants.
Observed baselines are const=8, enum=2, extent=9; edited variants are 3, 8, 4.
These are bounded target observations, not a universal ABI claim.

Commands (JDK 21, GCC 14.2 and Clang 19.1):

```sh
mvn -Dtest=ConstantTemplateArgumentsTest,OffsetofContextsTest,*ClassTemplate*Test,EnumStorageLayoutTest,MemberPointerLayoutTest,OffsetofRuntimeTest -Dit.test=PartialConstantTemplateArgumentsIT verify
mvn -Dtest=ConstantTemplateArgumentsTest '-Dit.test=PartialConstantTemplateArgumentsIT#editsToPatternFactsReselectFoldedSource' verify
```

The first command ran before the second native method was added. Both completed
with no failures, errors or skips. Production ClassTemplateSemantics.java SHA256:
`a2ea405bf462d5e9ea0a1938d87fbc78794d42125c19caff6721f5852ea45f8d`.
The worktree's packaged JAR SHA256 is
`b96e135ea91ecf0b2e8b57fa8e4b48c97eb94abc17b4dad8aabb03cd11673705`.


Full Maven,
strict JavaDoc, demo/catalogue and exact-head CI are separate final gates.
Prior package evidence does not certify this source tree. PR #124's historical
1439-unit/22-selected-IT checkpoint was followed by
[full CI42602393679](https://github.com/kniazkov/code-property-graph/actions/runs/42602393679).
PR #125 is the completed preceding package; its verification file records its
pre-publication checkpoint and its PR records subsequent final gates.

Inventory totals remain 97 bounded obligations / 90 names, with five outstanding
obligations / five names. offsetof remains open/partial, as do setjmp, va_start,
va_arg and va_end. No completion claim is made for #92 or #48.

## Краткий статус

Добавлены независимые целые значения верхнего уровня в частичных специализациях.
Одни и те же факты снимка используются при выборе, упорядочении и объединении
повторных объявлений. Записанные имена и зависимости сохраняются после свёртки
и правок. Вложенные именованные шаблоны, зависимые типы параметров и меняющие
значение преобразования не сертифицируются. ABI и профиль не расширены.
Прошли 105 выбранных unit-тестов и перекрывающий их финальный прогон 24 тестов,
два native/CLI-теста со 168 выполнениями GCC/Clang и 53 Python-проверки.
Полный Maven и CI остаются отдельными гейтами. Исторические результаты
#124/#125 не подменяют текущие проверки; #92 и #48 открыты.
