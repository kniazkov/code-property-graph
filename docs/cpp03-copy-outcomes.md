# Named-lvalue copy and reference argument outcomes (#96)

## Русский

Сводки исходов обработчиков unexpected поддерживают пользовательский копирующий
конструктор при копировании существующего именованного объекта того же класса:
`S b = a` и `S b(a)`. Учитываются выбор перегрузки для изменяемого/константного
источника, тело выбранного конструктора, его подобъекты, спецификация исключений
и дополнительные параметры с аргументами по умолчанию. Для формы `S b = a`
конструктор не должен быть помечен `explicit`. Неявно объявленные компилятором
копирующие конструкторы пока остаются неизвестными.

Ссылочные параметры конструкторов и прямых функций поддержаны при привязке к
имени переменной или параметра того же типа, без временного объекта, срезки или
пользовательского преобразования. Привязка сама по себе не читает значение и
не вызывает конструктор/деструктор. Правила cv и допустимость перегрузки проверяет
существующее разрешение имён/перегрузок. Значения параметров не специализируют тело.

Копирование баз и полей использует общий порядок создания и очистки. Ошибка
копирования не уничтожает не созданную копию, но запускает очистку ранее
созданных локальных объектов/подобъектов/элементов массива. Ошибка деструктора
во время этой очистки даёт terminate. Игнорируемая инициализация виртуальной
базы не выполняет копирование. Аргументы по умолчанию вычисляются вне
спецификации вызываемого конструктора или функции.

Для обычных прямых вызовов объединяются исключения из всех явных и разрешённых
аргументов по умолчанию. Тело достижимо только при возможном нормальном завершении
каждого аргумента; порядок аргументов не фиксируется. Это также позволяет
суммировать вспомогательные функции, принимающие источник копии по ссылке.

Открыты неявное поэлементное копирование, временные объекты и copy elision,
копирование исключений/параметров по значению, пользовательские преобразования,
ссылки на подобъекты и результаты вызовов, производные типы при привязке ссылок,
вариадические аргументы и точные эффекты памяти. Неизвестное тело, неоднозначный
выбор и неподдерживаемые конструкции сохраняют консервативные альтернативы.
Изменение уточняет исходы; публичная структура CFG не расширяется. #96 открыта.

## English

Outcome summaries support user-declared copy construction from a named same-class
lvalue, both `S b = a` (non-explicit copy constructor) and `S b(a)`. Existing overload
selection distinguishes mutable/const sources. The selected body, subobjects,
exception specification and remaining defaults participate in the lifetime summary.
Implicit compiler-declared copies remain unknown.

Constructor/direct-function reference parameters may bind named variables or
parameters of the same unqualified type. Binding creates no temporary and performs
no read or lifetime operation. Existing resolution checks cv/viability; parameter
values do not specialize bodies. Member/index/call arguments, derived-to-base
binding and user conversions remain unsupported.

Failed copies skip the failed object's destruction and unwind the completed prefix,
including surrounding locals and array elements. Cleanup throws during unwinding
terminate. Ignored virtual-base initializers do not copy. Defaults remain outside
the called constructor/function specification.

Direct calls now union explicit/resolved-default argument exceptions and invoke the
body only if every argument may complete. No argument order is invented. Reference
helpers can therefore participate in copy-constructor summaries.

Implicit memberwise copies, temporary/elision choices, exception/by-value parameter
copies, conversions, variadic arguments and exact memory effects remain open. Unknown
bodies and unsupported forms retain conservative outcomes. No public CFG schema
extension; #96 stays open.

Base: merged PR #183. Full CI including Clang is required before merge.

## Verification — 2026-11-20

Temurin 21.0.9 / Maven 3.9.9: the focused verify run passed 244 unit
tests and 76 GCC C++03 scenarios for original/generated code (152 native
executions). A subsequent run of the final 26-test copy suite, including
five added boundary tests, passed: 249 distinct unit tests checked overall.
Production sources did not change between these runs. Strict private JavaDoc
with `-Werror -Xdoclint:all,-missing` and portable path checks passed.

```sh
mvn -B -ntp -Dtest=UnexpectedHandlerCopyConstructionTest,UnexpectedHandlerExplicitConstructionTest,UnexpectedHandlerConstructorDefaultsTest,UnexpectedHandlerObjectInitializerTest,UnexpectedHandlerInitializerTest,UnexpectedHandlerVirtualLifetimeTest,UnexpectedHandlerCompositeLifetimeTest,UnexpectedHandlerLifetimeTest,ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated -DargLine=-Xmx768m verify
mvn -B -ntp -Dtest=UnexpectedHandlerCopyConstructionTest -DargLine=-Xmx768m test
```

Verified shaded JAR SHA-256: `8dca5e9c70eab6a5b4b0fb3884e9248428473a05c27cf5bf8b42e3d69f76163e`.

Full CI including Clang remains required before merge.
