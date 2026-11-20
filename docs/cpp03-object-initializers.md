# Empty object/base initializers in handler outcomes (#96)

## Русский

Сводки исходов поддерживают пустые инициализаторы баз и полей-объектов:
`Base(), member(), array()`. Создание выполняется в порядке виртуальных баз,
невиртуальных баз и объявления полей, независимо от порядка списка. При сбое
очищается только завершённый префикс; тело конструктора и деструктор неуспешно
созданного объекта не выполняются. Спецификация конструктора охватывает эти эффекты.

Инициализатор виртуальной базы при вызове конструктора базового подобъекта
пропускается вместе с аргументами. Пустой инициализатор того же типа для полного
объекта выполняется. Сохраняются проверки привязки, принадлежности и повторных
инициализаторов, включая определения вне класса. Обнуление при value-initialization
не объявляется точным эффектом памяти: здесь моделируются исключения и очистка.

Непустые выполняемые инициализаторы баз/полей-объектов, ссылки, копирование,
временные объекты и параметры по умолчанию остаются консервативными. #96 открыта.

## English

Handler outcome summaries accept empty base, class-field and field-array
initializers. Construction order follows the hierarchy and field declarations,
not the written initializer list. Failed construction unwinds completed subobjects
only; constructor exception specifications cover the composed operation.

A virtual-base initializer is ignored, including argument evaluation, in a base
invocation. Complete-object invocation retains it. Binding, membership and duplicate
checks remain. Value-initialization zeroing is not claimed as an exact memory effect.
Nonempty evaluated object/base arguments, references, copies, temporaries and default
arguments remain conservative. No AST/API/JSON schema change is introduced.

The source basis remains retained N1638 §12.6.2/5–6 and §15.2/2; no fresh
original-edition verification is claimed. Full CI including Clang is required.
Base: merged PR #180. Native tests cover ordering, cleanup and ignored arguments.

Verified 2026-11-20: 191 unit tests and GCC E2E, 59 scenarios / 118 original/generated executions, all passed. Strict Javadoc, portable paths and diff checks passed.

```sh
mvn -B -ntp -DargLine=-Xmx768m -Dtest=UnexpectedHandlerObjectInitializerTest,UnexpectedHandlerInitializerTest,UnexpectedHandlerVirtualLifetimeTest,UnexpectedHandlerCompositeLifetimeTest,UnexpectedHandlerLifetimeTest,ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated verify
```

Tested JAR SHA-256: `6264496f8fa6ee9e19e6c9021e47917953ae140e75d800bfdf9f08257130e2d5`.
