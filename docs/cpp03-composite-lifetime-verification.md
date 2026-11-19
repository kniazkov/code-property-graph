# Composite lifetime outcomes in unexpected handlers (#96)

## Русский

Сводка исходов обработчика теперь рекурсивно учитывает невиртуальные базовые
классы, поля-объекты и одномерные массивы полей. Базы создаются в порядке списка
баз, затем поля в порядке объявления, затем выполняется тело конструктора.
При успешном создании подобъекты остаются живыми. При исключении очищается только
полностью созданная часть, в обратном порядке; деструктор неуспешно созданного
объекта не вызывается.

Разрушение начинается с тела деструктора, затем выполняется обратная очистка
полей и баз. Исключение из тела деструктора не отменяет очистку его подобъектов.
Если следующее исключение покидает деструктор подобъекта при этой раскрутке,
результат — `TERMINATE_EXIT`. Если очистка завершается нормально, первое
исключение продолжает распространение. Массив поля следует тем же правилам:
после сбоя создания элемента очищается только созданный префикс.

Спецификация исключений внешнего конструктора/деструктора проверяется против
результата всего составного действия, включая подобъекты. Если она нарушается,
сводка оставляет консервативные варианты: вложенное поведение unexpected не
подменяется обычным выходом с исключением.

Поддержаны автоматические объекты без записанного инициализатора, невиртуальные
базы и поля с аналогичным созданием по умолчанию. Массивы одномерные, 1–64 элемента.
Скалярные поля, указатели и указатели на члены не требуют пользовательской очистки.
Виртуальное наследование, union, поля-ссылки, неизвестные типы, копирование,
временные объекты, списки инициализаторов конструкторов, параметры по умолчанию,
function-try-block и неизвестные тела остаются за границей уточнения. Ограничение
восемь уровней классов дополняет существующие ограничения на функции и бюджет
2000 посещений; достижение границы сохраняет неопределённость.

Это анализ возможных исходов CFG, а не точных записей в память деструкторов.
Ветви объединяются без вычисления значений счётчиков. API/JSON не изменены.
Тесты нативного исполнения отдельно проверяют наблюдаемый порядок и число вызовов.

Повторно просмотрен сохранённый N1638 (11 апреля 2004): §12.6.2/5 (порядок
создания), §12.4/6 (разрушение тела, полей и баз), §15.2/2–3 (частичное создание
и раскрутка). Это не новая сверка оригинальной печатной редакции 2003 года.
#96 остаётся открытой: копирование исключений, виртуальные базы, оставшиеся
формы инициализации, точные эффекты памяти и нормативная сверка ещё впереди.

## English

Known-handler summaries now compose nonvirtual bases, class-valued fields and
one-dimensional field arrays. Construction follows base-list order, field declaration
order, then the constructor body. Only an escaping construction exception destroys
the completed prefix; successful construction retains subobjects. The failed
object's destructor is not run.

Destruction runs the written destructor body followed by reverse field/base cleanup.
An exception escaping that body still cleans subobjects; a second escaping destructor
exception during unwinding terminates. Field arrays follow the same completed-prefix
rule. The enclosing special member's exception specification applies to the composed
result, not just its body; violations keep conservative boundary alternatives.

The supported subset uses default initialization, nonvirtual bases and arrays of
1–64 elements. Virtual inheritance, unions, references, unknown types, copies,
temporaries, constructor initializer lists, default arguments, function try blocks
and unknown bodies retain uncertainty. Class nesting is bounded at eight levels,
in addition to the existing function-depth and 2000-visit limits. Reaching a limit
is not evidence that an operation cannot throw.

This refines CFG outcomes, not exact destructor memory effects. No AST or JSON schema
change is required. Native counters verify concrete order independently of summary
branch unions. The reviewed source is the retained April 2004 N1638 draft, sections
12.6.2/5, 12.4/6 and 15.2/2–3, not the original published 2003 edition.

## Verification

Base: `a1c2351` (merged PR #177). Java 21; native programs compiled with GCC
`-std=c++03 -pedantic-errors`. New fixtures cover construction/destruction order,
constructor-body and field-constructor failure, destructor-body failure, termination
during subobject cleanup, partial field-array creation and nested fields. Every case
runs as original and generated code in a separate process. Full CI including Clang
remains required before merge.

Verified 2026-11-19: **161 unit tests, one GCC E2E test, 45 scenarios / 90
original/generated native executions**, zero failures. Strict Javadoc, portable
paths, paragraph-ledger validation and diff checks passed.

```sh
mvn -B -ntp -DargLine=-Xmx768m -Dtest=UnexpectedHandlerCompositeLifetimeTest,UnexpectedHandlerLifetimeTest,ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated verify
```

Tested JAR SHA-256: `9485a6a9738d2af4ba9536663fbe1ebaa0873cd06919802a1de6c570eec7b66a`.
