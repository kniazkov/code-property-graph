# Implicit unexpected-handler memory effects (#96)

## Русский

DFG границы спецификации исключений теперь включает конкретные чтения и записи
из известных тел `unexpected_handler`, а также эффекты их обычных вызовов.
Запись перед нарушившим спецификацию `throw` может достигать чтения в обработчике;
запись обработчика может достигать чтения в `catch` вызывающей функции.
Обычная запись в непосредственно названный целочисленный объект больше не
сбрасывает установленный обработчик. Вызовы в правой части продолжают учитываться;
записи через ссылки и указатели сохраняют консервативную обработку.

Это **may-анализ**, а не точное исполнение обработчика. Каждый известный кандидат
анализируется из одного входного состояния. Объединяются состояния достижимых
префиксов тела, в том числе перед `terminate`; записи не становятся обязательными
и не удаляют входные определения. Общий непрозрачный эффект границы сохраняется
для неизвестных кандидатов, копирования исключений, времени жизни и внешнего кода.
Память локальных объектов после окончания жизни не объявляется доступной;
наличие определения в may-наборе не является доказательством корректного доступа.

Не создаётся фиктивный `CallExpression`. Прямые эффекты обработчика используют
существующие узлы его тела; обычные вызовы помощников сохраняют свой `callSite`.
При анализе внешнего вызова используется его контекст. Проверка принадлежности
DFG допускает тела обработчиков, перечисленных в вспомогательной операции,
и тела функций, достижимые из контекста вызова через ссылки на функции.
Произвольные посторонние тела не становятся допустимыми концами рёбер.
JSON и копирование проекта сохраняют эти ссылки без изменения схемы.

Повторный вход в активный обработчик и глубина более восьми обработчиков
останавливают раскрытие тела, сохраняя непрозрачный may-эффект. Для обычных
рекурсивных вызовов действует существующее приближение анализа памяти.
Точные эффекты деструкторов/копирования, корреляция записей с конкретным исходом
обработчика и сверка оригинальной редакции стандарта остаются в открытой #96.

## English

Exception-specification boundaries now publish concrete may-definitions and reads
from known unexpected-handler bodies and their ordinary helper calls. Input writes
can reach handler reads, and handler writes can reach a caller's catch. Assigning
a directly named integral object preserves installed-handler provenance; RHS calls
and alias writes retain their existing conservative treatment.

Each candidate starts from the same boundary input. Reachable prefix states are
joined, including effects before termination, without claiming must-writes or
killing input definitions. The opaque boundary effect remains for unknown handlers,
exception copying, lifetime operations and external code. This is not an assertion
that objects remain alive or that any access through a dangling pointer is valid.

No synthetic call AST is introduced. Handler body nodes are existing project-owned
DFG endpoints; ordinary helper calls retain call-site provenance. Project copies,
JSON export and reanalysis preserve ownership. Active handler recursion and depth
eight bound implicit expansion; the opaque effect remains at the cutoff. Exact
lifetime/copy effects and outcome-correlated memory states remain outside this
contract. No new original-edition normative verification is claimed.

## Verification

Base: `13d552f` (merged PR #175). Java 21, GCC `-std=c++03 -pedantic-errors`.
Focused command (after configuring Maven networking and JAVA_HOME):

```sh
mvn -B -ntp -Dtest=ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated -DargLine=-Xmx768m verify
```

New native fixtures check aliased helper writes, cleanup after handler throws and
writes observed by a terminating handler. Both original and generated programs run
in isolated processes. A returning unexpected handler is tested only in the model;
it is not used as a valid native oracle. Full CI, including Clang, is required before
merge.

Verified 2026-11-19: **130 unit tests, 1 GCC E2E test, 28 scenarios / 56 native
original/generated executions**, all passed. Strict Javadoc, portable-path, paragraph-ledger
and diff checks passed.
JAR SHA-256: `96f7e4079a161c81c9a9f1b6c63e626555056332a2dfe369ae7d9dcf20cac665`.
