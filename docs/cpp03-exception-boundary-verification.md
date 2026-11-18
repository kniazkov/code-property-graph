# Dynamic exception boundary summaries (#96)

## Русский

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
Replacement object references retain the permitted type; unknown handler
side effects and installation provenance are not modeled. These remaining
obligations keep #96 open. The source reference consulted is the checked-in
N1638 draft, not a fresh verification of the original printed edition.

## Verification

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
