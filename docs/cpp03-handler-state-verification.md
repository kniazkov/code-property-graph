# Saved unexpected handlers and direct-call state (#96)

## Русский

Продолжение PR #171. Состояние установки обработчика теперь включает значения
локальных указателей: инициализацию, копирование, присваивание и объединение
ветвей/циклов. `set_unexpected(p)` устанавливает значение `p` и возвращает
предыдущее состояние. Поэтому работают сохранение, временная смена и восстановление
обработчика, включая указатели, возвращённые обычной функцией.

Анализ прямого вызова использует CFG выбранного определения, состояние в точке
вызова и значения аргументов. Передаются параметры по значению, возвращаемые
указатели, нормальное состояние установки и состояние при выходе с исключением.
Поддержаны вложенные вызовы и связывание объявления/определения в разных единицах
трансляции. Каждый вызов имеет собственную среду; локальные переменные вызываемой
функции не переносятся в вызывающую. Анализ начинается после построения всех CFG.

При `throw` фиксируется обработчик, действовавший после вычисления операнда.
Деструктор во время раскрутки может изменить текущую установку, но не эту
зафиксированную цель. Это состояние проходит и через границу прямого вызова.
Основание: рабочий проект N1638 §18.6.2.3–4, особенно §18.6.2.4/2. Это не новая
сверка исходного печатного ISO/IEC 14882:2003.

### Границы

- Самостоятельный CFG функции по-прежнему имеет неизвестное внешнее состояние
  на входе. Вызовы специализируются внутри анализа вызывающей функции; одна
  глобальная установка не приписывается всем возможным вызовам функции.
- Прямые вызовы с полным CFG, обычными аргументами без побочных эффектов и
  параметрами по значению. Виртуальные/косвенные вызовы, аргументы по умолчанию,
  ссылки, внешние функции и неполные CFG остаются консервативными.
- Для указателей отслеживаются только локальные переменные без static/extern и
  без доступа через адрес или ссылку. Глобальные, членовые, массивные и алиасные
  хранилища указателей не получают выдуманных точных значений.
- Рекурсивный цикл, глубина более восьми активных функций или исчерпание бюджета
  2000 анализов функций на корневой CFG дают неизвестный результат. Возврат из
  функции без доказанного нормального выхода также не создаёт точного состояния.
- Тело самого вызванного `unexpected_handler` пока не исполняется точно. Его
  возможные результаты и эффекты памяти остаются консервативными. После вызова
  handler нельзя переносить прежнюю точную установку на новое исключение.

Ссылки `unexpectedHandlers`/`unexpectedHandlerIds` и флаг неизвестности сохраняют
прежний контракт API/JSON/DOT. Копирование переназначает ссылки; правка тела или
аргумента установщика пересчитывает результаты. #96 остаётся открытой: нужны
точные эффекты/исходы handler и оставшаяся нормативная матрица.

## English

A forward environment tracks private local handler pointers and the old value
returned by `set_unexpected`. Direct-call CFG interpretation transfers by-value
parameters, returned handler values, normal installation effects and captured
exception state. Calls are context-specific, including cross-translation-unit
resolved definitions; standalone function graphs retain unknown external entry.
Exception paths preserve the handler captured after throw evaluation, before
unwinding. Handler invocation itself invalidates precise installation state.

Opaque calls, alias-exposed storage, unsupported arguments, recursion and bounded
analysis exhaustion retain unknown alternatives. This does not implement precise
handler-body execution or whole-program entry-context specialization. Existing
opaque memory effects and boundary may-outcomes remain; #96 is not closed.

## Verification scope

Unit tests cover save/restore/copy/overwrite, branch/loop joins, installer
parameters and return values, nested independent call contexts, propagation from
throwing helpers, cleanup ordering, unknown external/indirect/recursive calls,
alias/static storage, qualified designators, depth bounds, multiple translation
units, immutable snapshot remapping and edits followed by reanalysis.

Native corpus: 12 single-file fixtures plus one two-translation-unit fixture,
each as original and generated strict C++03. It covers replacement/termination,
local handler mutation, saved pointers, helper exchange/restore, loops and cleanup.
GCC is checked in the cloud; Clang and the full suite remain required in CI.
Logs are retained under `target/exception-boundary-oracle` by the existing workflow.

### Executed on 2026-11-18

```sh
mvn -B -ntp -DargLine=-Xmx768m \
 -Dtest=UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest \
 '-Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated' verify
```

Result: 96 unit tests and one GCC E2E (13 scenarios, 26 native original/generated
executions), zero failures/errors/skips; BUILD SUCCESS in 45.856 seconds.
Strict Javadoc (`-Werror -Xdoclint:all,-missing`), paragraph-ledger validation,
portable paths and diff whitespace checks passed. Full CI is still required.
Base revision: `1db82bacfacc90bf6d6c9b47036871f161939d18`.
Fresh tested JAR SHA-256:
`b21d2620f36d7435f0f94308680800c1c332bff099b98e2039699bb6df38e5b6`.


Follow-up after PR #172: [known handler outcomes](cpp03-handler-outcomes-verification.md)
adds bounded outcome narrowing. Exact memory and lifetime effects remain outside
that proof; the historical results above retain their original scope.
