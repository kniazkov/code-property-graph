# Scalar member initializers in handler outcomes (#96)

## Русский

Сводки исходов unexpected_handler учитывают явные инициализаторы скалярных
полей, указателей и указателей на члены, включая определения конструктора вне
класса. Выражения вычисляются в порядке объявления полей, а не записи списка.
Исключение из инициализатора пропускает тело конструктора и деструктор всего
объекта; ранее созданные базы и поля очищаются в обратном порядке. Спецификация
конструктора охватывает исключения из инициализаторов. Виртуальные базы сохраняют
контекст полного объекта/базового подобъекта.

Инициализаторы баз и полей-объектов, ссылки, копирование, неизвестные выражения,
параметры по умолчанию и function-try-block остаются консервативными. Проверяются
привязка к полю и отсутствие повторной инициализации. Не заявляются точные эффекты
памяти или полная поддержка инициализации C++03. #96 остаётся открытой.

## English

Known-handler summaries evaluate scalar, pointer and member-pointer field
initializers in declaration order, including out-of-class constructor definitions.
Initializer exceptions skip the constructor body and whole-object destructor,
while completed bases/fields unwind. The constructor specification covers these
exceptions. Existing virtual-base invocation contexts are preserved.

Base/class-field initialization, references, copies, default arguments, unknown
expressions and function try blocks retain conservative alternatives. Binding and
duplicate-initializer guards prevent speculative precision. This changes outcomes,
not exact memory effects. No AST/API/JSON schema change. Full CI including Clang
remains required before merge.

Base: merged PR #179. The source basis remains retained N1638 §12.6.2/5 and
§15.2/2, not a new original-edition verification. Native fixtures check reordered
scalar initializers and cleanup of ordinary/virtual subobjects after failure.

Verified 2026-11-19: 182 unit tests and GCC E2E, 56 scenarios / 112 original/generated executions, all passed. Strict Javadoc, portable paths and diff checks passed.

```sh
mvn -B -ntp -DargLine=-Xmx768m -Dtest=UnexpectedHandlerInitializerTest,UnexpectedHandlerVirtualLifetimeTest,UnexpectedHandlerCompositeLifetimeTest,UnexpectedHandlerLifetimeTest,ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated verify
```

Tested JAR SHA-256: `64eccaa3b4274415d59a5e4bef7b359486eb01eea9e3fb253624887a102d1525`.
