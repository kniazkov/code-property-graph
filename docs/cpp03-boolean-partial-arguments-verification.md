# Integral-to-bool partial-specialization patterns: bounded #92 follow-up

This package extends PR #126 only for **nondependent integral values converted
to bool in top-level partial-specialization patterns**. Zero becomes false;
every nonzero value, including negative values, becomes true. The conversion
is performed before matching, partial ordering and repeated-declaration identity,
using the same snapshot-local facts as the preceding package.

## Contract and boundaries

Written patterns, initializer chains and visible unselected candidate headers
remain dependencies. Copying, folding and editing retain them so changing a
header fact from nonzero to zero can reselect a specialization. Pointer/reference
identity matching is untouched. This does not admit floating values, volatile or
runtime initializers, unresolved constants, dependent value parameter types,
nested named patterns, or other value-changing integral conversions. In
particular, unsigned-char wrapping is still explicitly unsupported.

Original C++03 integral-to-bool conversion is a zero/nonzero conversion, rather
than the later converted-constant-expression representability restriction.
The repository's source-reviewed original-edition ledger is historical evidence;
the private original PDF was not freshly read for this package. Strict C++03
GCC/Clang probes are comparison evidence, not a claim to certify every original
language rule. No layout profile, enum representation, object-size ceiling,
pragma/operator restriction or ABI policy changes.

## Evidence and gates

Implementation: `ClassTemplateSemantics.partialArguments`. Unit tests in
`ConstantTemplateArgumentsTest` cover positive, negative and zero constants,
enumerators, redeclaration equivalence, ordering, primary fallback, ambiguity,
nonconstant guards, copying, reanalysis, generation/reparse, JSON and edits after
folding. The existing nested/dependent/layout ceiling and object identity tests
remain in place. A dedicated method in `PartialConstantTemplateArgumentsIT`
compares original, generated, folded, copied, reanalyzed and edited sources with
the packaged CLI. Both cstddef spellings and actual preprocessor expansion are
included. Native checks assert ICE contexts, size_t identity and layout values.

Native artifacts reuse `target/partial-constant-template-arguments-oracle/`,
already uploaded by the workflow. Each execution preserves tested source,
wrapper, observations, compiler/version/target, command, profile and SHA256.
The bool fixture starts with a negative header fact and edits its retained
initializer to zero. Baseline observations are const=8, enum=2, extent=9;
edited observations are const=3, enum=8, extent=4 on the selected target.

The completed focused Maven run passed **108 unit tests and one native/CLI
integration test**, with no failures, errors or skips. The integration test
passed 160 compiler executions (80 GCC, 80 Clang), including 80 preprocessor
materializations and 72 edited variants. All 160 source hashes match their
preserved metadata. The earlier 27-unit run overlaps these 108 tests.
A final 29-unit run passed after adding two review regressions, bringing the
union of focused unit coverage to 110 tests. These assert that conversion to
bool cannot erase an oversized written sizeof dependency (including after
copy/fold/edit), or incompatible original enum promotion arithmetic. Both
SUPPORTED_OBJECT_LIMIT and UNSUPPORTED_ENUM_SEMANTICS remain enforced, and
unsafe source generation remains blocked. Production code and native fixtures
were unchanged after the native run.

All 53 Python validator tests passed (31 macro, 13 paragraph, 9 source inventory),
as did strict validation of the existing 5250 ledger records. The unchanged
inventory has 102 entries / 95 names, including five open entries. These checks
establish record consistency, not complete standard conformance.

Commands used JDK 21, GCC 14.2 and Clang 19.1:

```sh
mvn -Dtest=ConstantTemplateArgumentsTest test
mvn '-Dtest=ConstantTemplateArgumentsTest,OffsetofContextsTest,*ClassTemplate*Test,EnumStorageLayoutTest,MemberPointerLayoutTest,OffsetofRuntimeTest' '-Dit.test=PartialConstantTemplateArgumentsIT#booleanPartialConversionsSurviveStagesAndHeaderEdits' verify
python3 scripts/check-cpp03-library-macros.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
python3 scripts/test-cpp03-library-macros.py
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/test-inventory-cpp03-source.py
```

Production ClassTemplateSemantics.java SHA256:
`29692d11cc638c15162eab1c10a61d30710d416c593912ecbced4cfc36cd198c`.
Packaged JAR SHA256:
`7d526ff8552f66c284ea50f5fc89e26dffb68c4ae1d348342e39d330dc9f6dd3`.

Full Maven, JavaDoc, demo/catalogue and exact-head CI remain separate gates;
prior package green runs do not certify this worktree. The inventory retains
five outstanding obligations: offsetof, setjmp, va_start, va_arg and va_end.
No completion claim is made for #92 or #48.

## Краткий статус

Добавлено преобразование независимых целых значений верхнего уровня в bool
для частичных специализаций: ноль даёт false, ненулевое значение даёт true.
Сопоставление, упорядочение и повторные объявления используют одинаковые факты
снимка. Имена и зависимости сохраняются после копирования, свёртки и правок.
Вложенные шаблоны, зависимые типы параметров и прочие меняющие значение
преобразования остаются за границей пакета. ABI и профиль не расширены;
пять обязательств, #92 и #48 остаются открытыми.

Прошли 108 выбранных unit-тестов и финальный перекрывающий прогон 29 тестов
с двумя дополнительными регрессиями (110 уникальных unit-тестов), native/CLI-тест
со 160 выполнениями GCC/Clang и 53 Python-проверки. Ограничения размера и
совместимости продвижения enum сохраняются. Полный Maven и CI требуют отдельных гейтов.
