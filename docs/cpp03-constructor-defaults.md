# Constructor default-argument outcomes (#96)

## Русский

Сводки исходов поддерживают скалярные аргументы конструкторов по умолчанию,
включая указатели и указатели на члены. Это вычисления вызывающей стороны:
они происходят перед созданием подобъектов и вне спецификации исключений самого
конструктора. Поэтому исключение из аргумента может выйти из вызова конструктора
с throw(), не вызывая unexpected из-за этой спецификации.

Для конструктора поля или базы исключение из аргумента всё же входит в действие
внешнего конструктора и проверяется его спецификацией. При сбое аргумента не
создаются подобъекты не начатого объекта и не вызывается его деструктор; ранее
созданные объекты очищаются. В массиве аргументы вычисляются заново для каждого
элемента, а при сбое очищается созданный префикс.

Порядок аргументов в C++03 не выбирается произвольно: возможные исключения
объединяются, а тело конструктора достижимо только при возможном нормальном
завершении всех аргументов. Значения параметров не используются для сужения ветвей.
Ссылки, параметры-объекты, неизвестные вызовы, неоднозначные конструкторы и
копирование остаются консервативными. Точная память не моделируется. #96 открыта.

## English

Scalar/pointer/member-pointer constructor defaults are evaluated on the caller
side, before subobject construction and outside that constructor's exception
specification. A default argument throwing through a throw() constructor call is
therefore not a violation of that callee specification. For a base/field call,
the enclosing constructor specification still covers argument evaluation.

Failed arguments skip the unstarted object's construction and destruction while
completed surrounding objects unwind. Arrays reevaluate defaults per element.
Unspecified argument order is represented by a union of exceptional outcomes;
invocation is possible only if every argument can complete. Parameter values do
not prune branches. References, object parameters, unknown calls, ambiguous
constructors and copies remain conservative. No exact memory or schema claim.

Base: merged PR #181. Full CI including Clang is required before merge.

## Verification — 2026-11-20

Temurin 21.0.9, Maven 3.9.9, GCC: 200 focused unit tests and 62 native
scenarios (124 original/generated executions), all passed. Strict private
JavaDoc with `-Werror -Xdoclint:all,-missing` and portable path checks passed.
Full CI, including Clang, remains required before merge.

```sh
mvn -B -ntp -Dtest=UnexpectedHandlerConstructorDefaultsTest,UnexpectedHandlerObjectInitializerTest,UnexpectedHandlerInitializerTest,UnexpectedHandlerVirtualLifetimeTest,UnexpectedHandlerCompositeLifetimeTest,UnexpectedHandlerLifetimeTest,ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated -DargLine=-Xmx768m verify
```

Verified shaded JAR SHA-256: `4c7730a1bae76ec80fe79532343d79c9f8e23047baa8e4035e75f6baa4e98a24`.
