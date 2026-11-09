# Unsigned template argument conversions: bounded #92 follow-up

This package completes the defined unsigned-conversion family for certified
integral class-template arguments. A negative or oversized integral value is
reduced modulo 2^N using the selected target parameter width, consistently for
ordinary arguments, defaults, full-specialization values and top-level
nondependent partial patterns. Matching, partial ordering and repeated-declaration
identity consume the same normalized facts. Bool still uses zero/nonzero conversion.

## Boundaries

The shared `ClassTemplateSemantics.convertIntegralArgument` helper consumes only
already certified integral facts. It does not evaluate initializers or relax
snapshot ownership, cycles, signed-expression overflow, runtime/volatile/floating
value rejection, enum compatibility, written dependencies or layout ceilings.
Parameter types remain in substituted bodies. Pointer/reference identity is
unchanged. Signed out-of-range conversions, nested named patterns and dependent
partial parameter types remain unsupported. No target layout or ABI policy changes.

The source-reviewed original-edition ledger identifies unsigned modulo semantics
in 4.7 paragraph 2 and template conversions in 14.3.2. The private PDF was not
freshly inspected for this package. Strict C++03 GCC/Clang comparisons are evidence
for this bounded subset, not complete certification of those clauses. Earlier
verification reports describe historical representability-only checkpoints.

## Tests and artifacts

`ConstantTemplateArgumentsTest` exercises unsigned char/short/int/long, negative
values, multiple-wrap values, selected short width and plain-char signedness,
default/full/partial matching, ordering, repeated declarations, ambiguity,
parameter size, copy/reanalysis/source reparse/JSON and edits after folding.
Negative tests retain signed out-of-range rejection, unsupported nested/dependent
patterns, invalid initializers and pointer/reference identities. Dedicated unsigned
regressions retain `SUPPORTED_OBJECT_LIMIT` and `UNSUPPORTED_ENUM_SEMANTICS` after
normalization and snapshot edits; invalid source generation stays blocked.

`PartialConstantTemplateArgumentsIT#unsignedPartialConversionsSurviveStagesAndHeaderEdits`
uses both cstddef spellings, actual preprocessor expansion, RESOLVED/GREEN/FOLDED,
copies, reanalysis, edited initializer chains and the packaged CLI. A seed of 511
normalizes to unsigned char 255; editing it to 256 changes the selected value to
zero and reselects two specializations. Native observations must change from
const=8, enum=2, extent=9 to const=3, enum=8, extent=4. The wrapper also checks
size_t identity, ICE use, array extent and case labels.

Artifacts are retained in `target/partial-constant-template-arguments-oracle/`:
tested source, wrapper, observations, source SHA256, compiler version/target,
command, profile and expansion/edit flags. This existing directory is uploaded
by the workflow. The focused Maven verify run passed 117 unit tests and one native/CLI integration
test with zero failures, errors or skips. Its 160 native executions comprise 80
GCC and 80 Clang runs, 80 expanded-source checks and 72 edited variants. All 160
preserved source hashes match metadata. A subsequent 37-unit run passed after
adding the body-type review regression, for 118 unique focused unit tests. The
new regression checks unsigned-long maximum values in comparisons/sizeof for
actual/default arguments and unsigned-char overload selection inside an
instantiated body. Production code and native fixtures did not change after the
native run. All 53 Python validator unit tests passed
(31 macro, 13 paragraph, 9 source inventory), along with strict validation of all
5250 ledger records. The macro inventory remains 102 entries / 95 names, five open.

Production ClassTemplateSemantics.java SHA256:
`c09cd7dd03917a7ca547cdaa09d83a3328d1d0fe3e333b4d430fbdfecddb4d1a`.
Packaged JAR SHA256:
`cd2ce0a1663545878ffc224d433b066ea29d2b0bef30b57c7239866453720542`.

The aggregate report checker currently sees the inherited Cpp03CorpusIT report
with three failures from the preceding full local run. These are not results of
this focused run; the parent reported they reproduce on the baseline. No clean
aggregate/full-run pass is claimed for this worktree.

Commands (JDK 21, GCC 14.2, Clang 19.1):

```sh
mvn '-Dtest=ConstantTemplateArgumentsTest,OffsetofContextsTest,*ClassTemplate*Test,EnumStorageLayoutTest,MemberPointerLayoutTest,OffsetofRuntimeTest' '-Dit.test=PartialConstantTemplateArgumentsIT#unsignedPartialConversionsSurviveStagesAndHeaderEdits' verify
python3 scripts/check-cpp03-library-macros.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
python3 scripts/test-cpp03-library-macros.py
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/test-inventory-cpp03-source.py
```

Full Maven, JavaDoc, demo/catalogue and exact-head CI are separate gates. Prior
package green runs do not certify this worktree. The five open obligations remain
offsetof, setjmp, va_start, va_arg and va_end. Neither #92 nor #48 is completed.

## Краткий статус

Единое преобразование проверенных целых значений в unsigned по модулю ширины
профиля применяется к обычным/default/full аргументам и независимым частичным
шаблонам верхнего уровня. Сохраняются тип параметра, написанные зависимости,
ограничения владения/циклов/размера и семантики enum. Непредставимые знаковые
преобразования, вложенные именованные шаблоны и зависимые типы параметров
частичных специализаций остаются за границами. ABI не расширен; пять макро-
обязательств, #92 и #48 остаются открытыми. Полный Maven и CI требуют отдельных
гейтов. Целевой verify прошёл 117 unit-тестов и один native/CLI-тест:
160 выполнений GCC/Clang, 80 развёрнутых исходников, 72 изменённых варианта.
Финальный прогон 37 unit-тестов также прошёл после добавления проверки типов
в теле шаблона (118 уникальных unit-тестов). Все хеши подтверждены; 53 Python-теста и строгая проверка 5250 записей прошли.
