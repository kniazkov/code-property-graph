# Explicit constructor argument outcomes (#96)

## Русский

Сводки исходов обработчиков unexpected теперь поддерживают прямую инициализацию
локального объекта `S s(args)` и непустые инициализаторы баз/полей-объектов.
Конструктор выбирается существующим механизмом разрешения перегрузок, по типам
аргументов. Для выбранного конструктора учитываются его тело, инициализаторы,
спецификация исключений и оставшиеся аргументы по умолчанию из объявления.
Переданный аргумент заменяет соответствующее значение по умолчанию.

Поддерживаются скалярные параметры, указатели и указатели на члены со скалярными
аргументами и стандартными преобразованиями. Вычисления аргументов происходят
до создания подобъектов, вне спецификации вызываемого конструктора. Для вызова
конструктора базы или поля действует спецификация внешнего конструктора.
Значения параметров не используются для сужения условных ветвей.

Возможные исключения из аргументов объединяются без выбора одного порядка
вычисления; вызов тела возможен только если все аргументы могут завершиться.
Базы и поля создаются в порядке языка, независимо от порядка записи
инициализаторов. Аргументы виртуальной базы вычисляются только в полном объекте.
При исключении очищаются уже созданные подобъекты/локальные объекты/элементы
массива; деструктор не начатого объекта не вызывается. Массивы используют этот
же механизм для инициализаторов подобъектов каждого элемента.

Копирующая и агрегатная инициализация, параметры-ссылки и параметры-объекты,
пользовательские преобразования, шаблонные конструкторы, неоднозначный выбор,
неизвестные тела и function-try-block остаются консервативными. Сохраняются
прежние ограничения глубины и размера массивов. Изменение уточняет сводки
исходов, но не добавляет точную модель памяти или публичные узлы CFG. #96 открыта.

## English

Unexpected-handler outcome summaries support automatic direct initialization
`S s(args)` and nonempty base/object-member initializers. Existing overload
resolution selects the constructor by argument types. Its body, subobject
initializers, exception specification and remaining declaration defaults are
used; supplied arguments replace defaults.

Scalar/pointer/member-pointer parameters with scalar arguments and standard
conversions are supported. Argument evaluation precedes subobject construction
and is outside the callee specification; enclosing constructor specifications
still govern base/member argument evaluation. Parameter values do not prune
branches. Argument exceptions are unioned without imposing evaluation order,
and body invocation requires possible completion of every argument.

Base/member construction follows language order. Ignored virtual-base
initializers do not evaluate arguments. Failures unwind the completed prefix,
including surrounding locals and array elements, without destroying the failed
object. Arrays reuse the same subobject-initialization summaries per element.

Copies, aggregates, reference/object parameters, user-defined conversions,
constructor templates, ambiguous selection, unknown bodies and function try
blocks remain conservative. Existing depth/array bounds remain. This refines
outcomes, not exact memory effects or public CFG structure. Issue #96 stays open.

Base: merged PR #182. Full CI including Clang is required before merge.

## Verification — 2026-11-20

Temurin 21.0.9 / Maven 3.9.9: 223 focused unit tests passed, including
23 new explicit-construction tests. All 69 GCC C++03 scenarios passed for
both original and generated code (138 native executions). Strict private
JavaDoc with `-Werror -Xdoclint:all,-missing` and portable path checks passed.

```sh
mvn -B -ntp -Dtest=UnexpectedHandlerExplicitConstructionTest,UnexpectedHandlerConstructorDefaultsTest,UnexpectedHandlerObjectInitializerTest,UnexpectedHandlerInitializerTest,UnexpectedHandlerVirtualLifetimeTest,UnexpectedHandlerCompositeLifetimeTest,UnexpectedHandlerLifetimeTest,ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated -DargLine=-Xmx768m verify
```

Verified shaded JAR SHA-256: `aed55d2fd327332252ac5dd6600a756288b1de329b0ebee3f9147092810528dd`.

Full CI including Clang is still required before merge.
