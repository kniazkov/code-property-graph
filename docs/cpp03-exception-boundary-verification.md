# Dynamic exception boundary summaries (#96)

## Исходный пакет PR #170 (история)

CFG явно различает проверку спецификации, вызов unexpected, допустимую замену
исключения, замену на стандартный bad_exception и terminate. Обработчик не
угадывается: UNEXPECTED_CALL — недетерминированная сводка возможных исходов,
не разрешённый вызов конкретной функции из set_unexpected. Ссылкой object у
события является функция со спецификацией; у replacement — соответствующий
тип из её списка. JSON/DOT и копирование сохраняют эти ссылки.

Известное разрешённое исключение сразу выходит через exceptional exit.
Известное запрещённое проходит через unexpected после очистки live-объектов.
Неизвестный тип проходит EXCEPTION_SPEC_CHECK с двумя возможными исходами.
Для throw() разрешённых замен нет. Стандартный bad_exception распознаётся по
объявлению каталога, а не одному имени пользовательского класса. Терминальная
ветвь остаётся возможной даже при разрешённых заменах: неизвестный handler
может вызвать terminate. Нарушение спецификации само по себе не делает исходник
некорректным и не блокирует генерацию.

Сверены §15.4/7–9 и §15.5.2/1–3 сохранённого N1638 (11 апреля 2004).
Это рабочий проект после C++03, не доказательство полного совпадения с печатной
редакцией 2003. Исходные обязательства реестра остаются partial.

Остаток #96: связывание с конкретным установленным обработчиком, его изменениями
и межпроцедурными эффектами; полная нормативная сверка с исходной редакцией и
расширенная матрица наследования/преобразования. Этот пакет не закрывает #96.
Native fixtures показывают допустимые исходы, но не доказывают статическое
разрешение установленного handler. Полный CI финального PR обязателен.

## English

The boundary is a conservative may-flow summary, not a prediction of the
installed unexpected handler. It records matching/violating unknown exceptions,
unexpected invocation after cleanup, permitted replacements, standard
bad_exception fallback and termination. No normal-return edge is introduced.
Replacement object references retain the permitted type. The follow-up below adds local installation provenance and conservative storage effects; precise contextual handler execution remains open. The source reference consulted is the checked-in
N1638 draft, not a fresh verification of the original printed edition.

## Verification of PR #170

Focused command (Java 21):

```sh
mvn -B -ntp -DargLine=-Xmx768m \
 -Dtest=ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest \
 '-Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated' verify
```

Four isolated native fixtures test permitted replacement, bad_exception,
forbidden replacement and throw(). Terminate handler exits with status 42;
normal handling exits with 0. Both original and generated sources are compiled
in strict C++03. GCC is checked locally; Clang is mandatory in CI. Logs are
saved in target/exception-boundary-oracle.

Result 2026-11-18: 41 unit tests + 1 GCC E2E (four fixtures, each original/generated), zero failures/errors/skips; BUILD SUCCESS, 21.907 seconds.
Base revision: `cd5bb833c1f4a42163f6b56a347f0ffa593f78b3`.
Fresh JAR SHA-256: `552bfbcb3e5629fe4da67b248f0f44ae807579feb3c2a1f398849308b6b44d87`.


## Local handler provenance / Локальные установки обработчиков

Пакет после PR #170 связывает `UNEXPECTED_CALL` с возможными обработчиками,
установленными прямым вызовом стандартного `set_unexpected` внутри функции.
Анализ идёт по CFG до неподвижной точки: последовательная установка заменяет
предыдущую, слияние ветвей объединяет цели, циклы учитывают нулевое число итераций.
На входе обработчик неизвестен. Неизвестные вызовы, неявные конструкторы и
деструкторы снимают точность: они сами могут переустановить обработчик. При броске состояние фиксируется до очистки: согласно N1638 §18.6.2.4
автоматический unexpected вызывает handler, действовавший сразу после throw.
Переустановка деструктором во время раскрутки не меняет эту зафиксированную цель. `sizeof` не исполняет вызов.
Пользовательский одноимённый `set_unexpected` не считается стандартным.

API: `unexpectedHandlers()` и `unknownUnexpectedHandler()`; JSON:
`unexpectedHandlerIds` и `unknownUnexpectedHandler`. Копия снимка переназначает
ссылки; DOT показывает возможные цели. Это ссылки на функции, не AST-дети.
И вызов известного обработчика, и неизвестного сохраняет консервативные эффекты
чтения/записи доступной нелокальной и раскрытой памяти; тело обработчика здесь
не исполняется контекстно и варианты исхода границы не сужаются.

The forward analysis tracks direct local installations, joins branch targets and
computes loop fixed points. Unknown entry state and opaque calls/lifetime events
retain uncertainty during normal execution. Exception edges capture the handler
before unwinding, as specified by N1638 §18.6.2.4; cleanup changes cannot alter
that captured identity. Candidate identities never narrow the existing may-outcomes.
Both known and unknown invocations retain opaque storage effects. Snapshot copies,
JSON and DOT preserve candidate links. Installation in callers, indirect handler
pointers, restoration of a saved handler and precise handler-body execution are
not resolved by this bounded analysis. In particular, no project-wide "last setter"
is guessed. These limitations and the original-edition/conversion matrix keep #96
open; this is not full interprocedural exception semantics.

Additional native fixtures: local installation with a global write, conditional
replacement, and a destructor changing the handler during unwinding. Each runs
as original and generated strict C++03, alongside the four boundary fixtures.


### Follow-up verification, 2026-11-18

```sh
mvn -B -ntp -DargLine=-Xmx768m \
 -Dtest=UnexpectedHandlerFlowTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest \
 '-Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated' verify
```

85 unit tests and one GCC E2E covering seven original/generated scenarios
(14 native executions) passed with zero failures/errors/skips, in 30.829 seconds.
Javadoc with `-Werror -Xdoclint:all,-missing`, portable paths, paragraph-ledger
validation and `git diff --check` passed. Clang and the full suite await CI.
Base revision: `f04e3cfd314580402418d17cafeaf1b32b979a98`.
Fresh tested JAR SHA-256:
`4642b25cf338befe5cb98cc96945fbe42f52a3189838b8917a606847bd5776f2`.
The cleanup oracle expects the handler captured at throw evaluation, even when
a destructor installs another one before unexpected is invoked (N1638 §18.6.2.4).
