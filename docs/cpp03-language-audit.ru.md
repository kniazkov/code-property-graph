<!-- CPG C++03 source and language audit. MIT License. -->
# Аудит языка C++03: источники, свидетельства и открытые пробелы (#48)

[English](cpp03-language-audit.en.md) · [Основная матрица](../CPP03-MATRIX.ru.md)

**Результат: частичный аудит, не полная приёмка ISO/IEC 14882:2003.**
На 2026-11-06 проверены исходники и утверждения существующих тестов относительно
базы `9420887d09afeab438f85b4984b1c65080a07407` (`cpp03-base48`).
Полный нормативный текст редакции 2003 не получен. Число её нормативных абзацев
не установлено; индивидуально сверенных с этим текстом абзацев в данном документе
**0**. Процент нормативного покрытия не вычисляется. Это не означает отсутствие
реализации: ниже отдельно приведены проверяемые свидетельства репозитория.

Строки ниже являются **группами правил**, а не абзацами стандарта. Все номера
разделов и метки служат навигацией; поле «точный абзац издания 2003» для каждой
строки остаётся **UNVERIFIED**. Название теста или зелёный прогон не закрывают это поле.

## 1. Источник и редакция

| Источник | Результат проверки 2026-11-06 | Что он доказывает |
|---|---|---|
| [Карточка ISO](https://www.iso.org/standard/38110.html) | Доступна; edition 2, 2003-10, 757 страниц | Идентичность издания, не содержание всех абзацев |
| [Предпросмотр 2003](https://cdn.standards.iteh.ai/samples/38110/2a6a554bd4ab43aca352dd5de166956a/ISO-IEC-14882-2003.pdf) | Только 5 страниц обложек и сведений о CD-ROM | Не нормативный корпус |
| [CiteseerX, ссылка из матрицы](https://citeseerx.ist.psu.edu/document?doi=99ef5807a860d1ae879c40fbb6fb58e892808a8f&repid=rep1&type=pdf) | HTTP 403 при прямом чтении | Текст не проверен; поисковый фрагмент не заменяет чтение |
| [USPTO, ссылка из матрицы](https://ptacts.uspto.gov/ptacts/public-informations/petitions/1558958/download-documents?artifactId=SRz9QsFyV54Nrszv6SwHEwmXBh6qAERkm3gtrRJ1kfXt1j79o7ohzcQ) | HTTP 403 при прямом чтении | Даже предполагаемая выдержка главы 23 не подтверждает главы 1–16 |
| [WG21 N1638](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1638.pdf) | Онлайн доступен, 772 страницы, 2004-04-11 | Более поздний рабочий проект; не замена редакции 2003 |
| [Локальное описание источника](standards/cpp/cpp03/README.ru.md) | Прочитано; PDF отсутствует в рабочем дереве, помечен skip-worktree | Файл PDF не изменялся; его хеш здесь не перепроверен |

[N1578](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1578.html)
сообщает о поправках Oxford/Kona поверх указанной базовой редакции 2003.
Среди конкретных редакторских вмешательств: Core 160 для 3.7.3.1/3 и 15.5.1/1.
[N1639](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1639.html)
фиксирует последующие Sydney-поправки, изменение метки
`[basic.lookup.koenig]` на `[basic.lookup.argdep]` и отдельный абзац для Core 424.
Следовательно, совпадение названия темы не гарантирует совпадение номера или текста
абзаца. Полного diff «2003 → N1638» здесь нет. Известные отличия библиотек ведутся
отдельно; более поздние DR нельзя незаметно принять за исходный текст 2003.

## 2. Как читать свидетельства

- **P**: разбор; **M**: модель/владение; **R**: связывание и семантические проверки;
  **A**: анализ/преобразование; **G**: выдача и повторный разбор C++.
- Ссылка на компонент означает «код найден и сопоставлен тесту», а не доказанную
  полноту стадии. Все перечисленные компоненты имеют статус **PARTIAL**.
- `—` означает, что для данной группы отдельная стадия не применяется.
- **ERR**: конкретное утверждение теста о диагностике; **LIMIT**: отказ реализации
  на неподдержанной возможности; **KEEP**: консервативно сохранить выражение/эффекты.
- Если код диагностики не указан, тест проверяет описанное свойство, но документ
  не утверждает существование специальной диагностики. Примеры ниже сокращены;
  воспроизводимый полный вход находится в указанном тесте/каталоге.
- Этот аудит проверяет код и тестовые утверждения. Нового запуска Maven здесь нет;
  результат финального полного прогона учитывается в итоговом отчёте #48 отдельно.

### Связи P/M/R/A/G

| ID | P | M | R | A | G | Существующие тесты |
|---|---|---|---|---|---|---|
| L | [CppLexer] | [PreprocessingToken] | — | — | [PreservedSource] | [CppLexerTest], [LexicalPhasesIT] |
| PP | [CppPreprocessor] | [Preprocessing] | — | — | [PreservedSource] | [CppPreprocessorTest], [PreprocessorIT] |
| N | [Parser] | [NameExpression] | [NameResolver] | [DataFlow] | [NameBindings] | [ScopedResolutionTest], [ScopesLinkageIT] |
| T | [TypeParser] | [TypeNode] | [TypeSemantics] | [ConstantEvaluator] | [Source] | [DeclaratorTest], [ProfileTypeSemanticsTest] |
| X | [ExpressionParser] | [ExpressionInfo] | [StandardConversions] | [ConstantFolder] | [Source] | [ExpressionSemanticsTest], [TypedConstantFoldingTest] |
| S | [StatementParser] | [FunctionFlow] | [NameResolver] | [ControlFlow] | [Source] | [Cpp03StatementScopesTest], [ExceptionCleanupFlowTest] |
| F | [Parser] | [ParameterDeclaration] | [NameResolver] | [DataFlow] | [Source] | [FunctionDefaultsSemanticsTest], [FunctionsDefaultsIT] |
| C | [Parser] | [ClassDeclaration] | [ClassSemantics] | [ControlFlow] | [Source] | [ClassInitializationSemanticsTest], [ClassesInitializationIT] |
| V | [Parser] | [BaseSpecifier] | [InheritanceSemantics] | [VirtualInheritanceValidation] | [Source] | [InheritanceSemanticsTest], [VirtualInheritanceValidationTest] |
| O | [ExpressionParser] | [CallExpression] | [OverloadResolution] | [OperatorSemantics] | [Source] | [OverloadResolutionTest], [OperatorSemanticsTest] |
| TM | [Parser] | [TemplateInstance], [ClassTemplateInstance] | [TemplateDeduction], [ClassTemplateSemantics] | [TemplateOrdering] | [Source] | [TemplateInstantiationSemanticsTest], [ClassTemplateSemanticsTest] |
| M | [ExpressionParser] | [NewExpression], [DeleteExpression] | [TypeSemantics] | [CppMemoryFlow] | [Source] | [ObjectMemoryValidationTest], [ObjectMemoryDataFlowTest] |

## 3. Проверенные группы глав 1–16

Все группы: **PARTIAL**, точный абзац 2003: **UNVERIFIED**. Ссылки P/M/R/A/G
раскрываются по ID таблицы выше. Ни одна строка не утверждает полноту раздела.

| ID / раздел | Правило и пример | Ожидаемое свойство / диагностика | P/M/R/A/G | Тест / сценарий | Открытая граница |
|---|---|---|---|---|---|
| 01 / 1 [intro.compliance] | Отделять диагностику нарушений от неподдержанных возможностей реализации.<br>`export template<class T> T f(T);` | LIMIT: `unsupported-export-template`; это не доказательство некорректной программы C++03. | TM | [TemplateInstantiationExportTest] | Разрешение экспортированных функций-шаблонов явно не поддержано. |
| 02 / 1 [intro.execution] | Не удалять наблюдаемые эффекты при свёртке.<br>`true && f(); f() + 0;` | KEEP: вызов и возможные эффекты сохраняются. | X,S | [TypedConstantFoldingTest] / `foldedShortCircuitNeverDropsExecutedSideEffects` | Нет исчерпывающей проверки порядка вычислений, volatile и эквивалентности программы. |
| 03 / 2 [lex.phases] | Сохранять физические координаты после триграфов и склейки строк.<br>`??=define N 1` and a continued `//` comment | Токены связаны с исходными физическими диапазонами; неактивный текст сохранён. | L,PP | [CppLexerTest] / `replacesAllTrigraphsBeforeSplicingAndPreservesPhysicalRanges`; [lexical-phases] | Проверены отдельные случаи фаз, а не полный перечень абзацев. |
| 04 / 2 [lex.pptoken], [lex.digraph] | Максимальное поглощение токена и написание альтернативных токенов.<br>`a+++b`, stringization of alternative tokens | Границы и исходное написание токенов, без правил лексера Java. | L,PP | [CppLexerTest] / `maximalMunchUsesCpp03PreprocessingNumberAndOperatorGrammar`, `alternativesKeepSpellingForStringization` | Не перечислены все лексические продукции и отрицательные комбинации. |
| 05 / 2 [lex.literal] | Разделять написание, точное значение и целевой тип литерала.<br>`10UL`, `"a\0b"`, adjacent strings | Тип целого зависит от профиля; встроенный NUL сохраняется. | L,T,X | [CppLiteralsTest]; [literals-expressions] | Лексическое десятичное значение не доказывает округление целевого float. |
| 06 / 2 [lex.name], E [extendid] | Использовать историческую таблицу идентификаторов.<br>UCN / non-ASCII identifier boundaries | Граничные тесты лексера; источник таблицы явно N1638. | L | [CppLexerTest] / `unicodeAndUcnIdentifiersUseCpp03AnnexEInsteadOfJavaOrModernUnicodeRules`; [CppIdentifierCharacters] | Совпадение с таблицей 2003 не проверено; транскрипция N1638 не становится текстом 2003. |
| 07 / 3 [basic.scope] | Связывать имя в точке объявления и сохранять затенение.<br>`int x=1; { int x=x; }` | Инициализатор ссылается на внутреннее объявление. | N | [ScopedResolutionTest] / `lexicalScopesHonorDeclarationPointsAndInitializerShadowing`; [scopes-linkage] | Не перечислены все сочетания областей класса, блока и шаблона. |
| 08 / 3 [basic.link], [basic.def.odr] | Различать внутренние сущности и связывать совместимые объявления.<br>Two TUs each with `static int n`; shared inline function | Разные внутренние цели; одинаковые inline-определения сами по себе не вызывают ошибку. | N | [ScopedResolutionTest] / `internalFunctionsAndObjectsRemainDistinctInSelectedComposition`, `sharedInlineFunctionDefinitionsAreNotOdrConflicts` | Нет полной межфайловой проверки токенов и поиска ODR; NDR выделен ниже. |
| 09 / 3 [basic.life], [basic.stc] | Различать память, создание и границы времени жизни.<br>Placement `new`, local static, bound temporary | Сохранять выделение/инициализацию и события времени жизни. | C,M,S | [ClassInitializationSemanticsTest] / `storageAndTemporaryBoundariesAreExplicit`; [casts-memory] | Не доказаны произвольное повторное использование через алиасы, ABI и все оптимизации времени жизни. |
| 10 / 4 [conv.array], [conv.func], [conv.prom] | Представлять контекстное decay и продвижение по рангу типа.<br>Array argument versus `sizeof(array)`; `short + short` | Узлы/типы преобразования; операнд sizeof не вычисляется. | T,X | [ExpressionSemanticsTest] / `arraysFunctionsNullQualificationAndReferenceBinding`, `promotionsUseTargetRanksRatherThanEqualWidths` | Одинаковая ширина целевых типов не означает идентичность типов C++. |
| 11 / 4 [conv.qual], [conv.ptr] | Проверять вложенную квалификацию и нулевые указатели.<br>`int**` to `const int**`; integer zero to pointer | Небезопасный кандидат отвергнуть либо оставить неизвестным, не выдумывать связь. | T,X,O | [ExpressionSemanticsTest] / `noInventedReferenceBindingOrUnsafeNestedQualification`; [OverloadResolutionTest] / `recursivePointerQualificationRequiresConstAtIntermediateLevels` | Примеры не доказывают полноту стандартных последовательностей преобразования. |
| 12 / 5 [expr], [expr.cond], [expr.ass] | Сохранять приоритеты, написанные приведения и категории значений.<br>`a ? b : c`, `p->*m`, compound assignment | Владение операндами и повторный разбор; явные и неявные приведения различаются. | X | [CppOperatorExpressionTest]; [ExpressionExportTest]; [literals-expressions] | Сохранение синтаксиса не доказывает полную корректность типов и эффектов. |
| 13 / 5 [expr.log.and], [expr.log.or], [expr.sizeof] | Моделировать условные и невычисляемые операнды.<br>`false && ++i; sizeof(i++);` | Нет исполняемого инкремента; ошибки внутри невычисляемого синтаксиса сохраняются. | X,S | [TypedConstantFoldingTest] / `sizeofAndScalarTypeidDoNotExecuteUpdatesButAllocationBoundsDo`, `foldingRetainsErrorsEvenInsideUnevaluatedBranches` | Перегруженным логическим операторам нужна семантика вызова, не встроенное короткое замыкание. |
| 14 / 5 [expr.new], [expr.delete], [expr.sizeof] | Проверять поддержанные операции объектов без выдуманной раскладки.<br>`sizeof(void)`, `sizeof(S)` with forward `S`, `new int&`, `delete 1` | ERR: `invalid-sizeof`, `incomplete-type`, `invalid-new`, `invalid-delete`. | M,T | [ObjectMemoryValidationTest]; [casts-memory] | Символьная раскладка и частичное разрушение массива остаются консервативными. |
| 15 / 5 [expr.dynamic.cast], [expr.typeid] | Различать RTTI, неудачу приведения и эффекты операнда.<br>`dynamic_cast<Base*>(&child)`, `typeid(i++)` | Поддержанное приведение к базе и невычисляемый scalar typeid сохранены. | X,M,V | [ObjectMemoryValidationTest] / `typeidRequiresCompleteClassButAllowsPointersAndVoid`; [casts-memory] | Upcast в сценарии не доказывает все runtime downcast/cross-cast и пути bad_cast. |
| 16 / 5 [expr.const], arithmetic | Не приписывать переносимое значение неопределённой или зависящей от реализации арифметике.<br>`2147483647 + 1`, `-1 >> 1`, `-5 / 2` | KEEP: нет точного свёрнутого целого для проверенного профиля. | T,X | [TypedConstantFoldingTest] / `shiftsAndUndefinedOrImplementationDefinedOperationsStayConservative` | IntegerRanges намеренно уже полной арифметики C++. |
| 17 / 6 [stmt.iter], [stmt.switch] | Сохранять цели циклов и сквозное исполнение switch.<br>`for (...) { continue; }`; repeated `case 1` | Continue ведёт к итерации; повторная метка: ERR `duplicate-case`. | S | [ExceptionCleanupFlowTest] / `breakContinueGotoAndCaseFallthroughRetainTheirTargets`; [Cpp03StatementScopesTest] / `switchLabelsHavePerSwitchIdentity`; [statements-exceptions] | Не перечислены все продукции и сочетания инструкций. |
| 18 / 6 [stmt.jump], [stmt.dcl] | Проверять переходы и разрушать только живые объекты.<br>`goto L; int x=1; L:;` | ERR `goto-crosses-initialization`; обычные выходы сохраняют разрушение. | S,C | [Cpp03StatementScopesTest] / `invalidJumpsAndLabelsAreDiagnosed`; [ExceptionCleanupFlowTest] | Полное зависимое от пути разрушение частично созданных массивов не завершено. |
| 19 / 7 [dcl.typedef], [namespace.def], [namespace.udir] | Сохранять псевдонимы, повторное открытие namespace и поиск using.<br>`typedef int I; namespace n {...} namespace n {...}` | Связи целей сохраняются при копировании и генерации. | N,T | [ScopeDeclarationParserTest]; [ScopedResolutionTest] / `usingDirectivesMeetAtTheirCommonEnclosingNamespace` | Для asm и всех альтернатив грамматики объявлений нет полного реестра приёмки. |
| 20 / 7 [dcl.link], [dcl.stc] | Сохранять языковое связывание и идентичность хранения.<br>`extern "C" int f(int);` and incompatible redeclaration | Тесты диагностируют конфликты и не переносят C linkage на постороннюю перегрузку. | N | [ScopedResolutionTest] / `externalCLanguageLinkageIsInheritedAndCannotOverload`, `conflictingStorageLinkageIsDiagnosed` | Внешний ABI и все сочетания спецификаторов хранения не проверены. |
| 21 / 8 [dcl.meaning], [dcl.ptr], [dcl.ref] | Представлять структуру декларатора вместо плоской строки типа.<br>`int *a[3]` versus `int (*p)[3]` | Различные узлы массивов/указателей сохраняются после генерации/разбора. | T | [DeclaratorTest] / `arraysPointersFunctionsReferencesAndMemberPointersBindFromTheName`; [platform-declarators] | Нет исчерпывающей матрицы продукций декларатора и ABI. |
| 22 / 8 [dcl.fct.default] | Связывать умолчания в объявлении, вычислять при вызове.<br>`void log(int=next()); log(); log(2); log();` | Два пропуска ссылаются на одно исходное выражение; позднее умолчание даёт `missing-call-argument`. | F,N | [FunctionDefaultsSemanticsTest]; [functions-defaults] | Ограничения базовой редакции проверяются тестами, но точные абзацы 2003 ещё не подтверждены. |
| 23 / 8 [dcl.init], [dcl.init.aggr] | Различать direct/copy/value/aggregate инициализацию.<br>`int a[3]={7};` | Корпус наблюдает нулевой хвост; direct-list форма C++11 отвергается. | C,T | [ClassInitializationSemanticsTest]; [classes-initialization] | Конечный набор агрегатов не доказывает весь алгоритм инициализации. |
| 24 / 9 [class.mem], [class.union], [class.bit] | Различать поля, static-члены, битовые поля и состояние union.<br>Unnamed bit-fields; union first-member initializer | Тесты идентичности/инициализации; sizeof битового поля даёт `invalid-sizeof`. | C,T,M | [ClassInitializationSemanticsTest] / `namespacesAndMultipleUnnamedBitFieldsRemainDistinct`, `unionConstructionInitializesOnlyTheSelectedMember`; [ObjectMemoryValidationTest] | Нет полной проверки активного члена union и целевого ABI. |
| 25 / 10 [class.mi], [class.virtual] | Различать повторные базовые подобъекты и общую виртуальную базу.<br>Ordinary and virtual diamonds | Неоднозначная обычная база; общая виртуальная база; проверка единственного final overrider. | V,O | [InheritanceSemanticsTest]; [VirtualInheritanceValidationTest]; [inheritance-virtual] | Неполные/циклические иерархии остаются консервативными; общая раскладка не выводится. |
| 26 / 11 [class.access], [class.friend] | Проверять доступ после выбора кандидата.<br>Private `f(int)` versus public `f(long)` called with `1` | ERR `inaccessible-overload`; нельзя выбрать худший public-кандидат. | C,V,O | [OverloadResolutionTest] / `selectedPrivateMemberIsDiagnosedEvenWhenPublicWorseMatchExists`, `protectedAccessRequiresDerivedReceiverInDerivedContext` | Для транзитивности friend и всех путей доступа нужен отдельный полный перечень. |
| 27 / 12 [class.base.init], [class.dtor] | Создавать в структурном порядке, разрушать в обратном.<br>Reordered written constructor-initializer list | Трасса корпуса `1234`, затем `12345678`, независимо от порядка списка. | C,V,S | [ClassInitializationSemanticsTest] / `constructionOrderIgnoresWrittenInitializerOrderAndDestructionReversesIt`; [classes-initialization] | Планы не являются полным ABI или универсальной исполняемой трассой. |
| 28 / 12 [class.copy], [class.temporary] | Отслеживать неявные специальные функции и границы временных объектов.<br>`T b=a; b=a; const T& r=T();` | Разные эффекты инициализации/присваивания; без фиксированного числа разрешённых устранений копий. | C,S | [ClassInitializationSemanticsTest] / `implicitMembersAndAggregateRemainDerivedNotDuplicateSourceDeclarations`, `storageAndTemporaryBoundariesAreExplicit` | Не охвачены все случаи устранения копии, доступа и эффектов неявных тел. |
| 29 / 13 [over.match], [over.ics.rank] | Ранжировать векторы преобразований и сохранять неоднозначность.<br>`f(int,long); f(long,int); f(1,1);` | ERR `ambiguous-overload`, выбранная цель отсутствует. | O,T | [OverloadResolutionTest] / `pairwiseVectorsRemainAmbiguousAndNoViableIsNotBound`; [overloads-adl] | Нет полного знаменателя наборов кандидатов и правил ранжирования. |
| 30 / 13 [over.oper], 3 [basic.lookup.koenig] | Сохранять вызовы операторов и поиск по аргументам.<br>Hidden friend with class argument; overloaded `+` | Выбранное объявление/эффекты сохраняются; hidden friend не находится для постороннего int. | O,N | [OverloadResolutionTest] / `adlFindsAssociatedNamespaceAndHiddenFriendOnlyForClassArgument`; [OperatorSemanticsTest] | В N1638 изменена метка раздела ADL; нужна привязка к редакции. |
| 31 / 14 [temp.param], [temp.names] | Сохранять владение синтаксисом шаблона, умолчаниями и зависимыми формами.<br>`Box<Box<int> >`, `typename T::value_type`, `x.template get<int>()` | Поддержанные формы проходят повторный разбор; вложенный `>>` и умолчания шаблона функции отвергаются. | TM,T,N | [TemplateSyntaxTest]; [templates-dependent] | Полная грамматика зависимых имён и идентичность абзацев базовой редакции открыты. |
| 32 / 14 [temp.res], [temp.deduct] | Отделять отказ непосредственной подстановки от ошибки выбранного тела.<br>Missing `T::type` candidate; selected `x.size()` for int | Fallback остаётся применимым; выбранное тело даёт `template-body-member` и упорядоченные диапазоны запросов. | TM,N | [TemplateInstantiationSemanticsTest]; [TemplateInstantiationExportTest] / `nestedBodyFailureExportsPrimaryTypeAndOrderedRequestRanges` | Не перечислены все контексты SFINAE и двухфазного поиска. |
| 33 / 14 [temp.class.spec], [temp.spec] | Выбирать специализации классов и сохранять явные запросы.<br>`Box<T*>`, full `Box<int>`, explicit instantiation | Конкретная идентичность и связи запросов сохраняются в копии/экспорте. | TM | [ClassTemplateSemanticsTest]; [template-instantiation] | Отказ по бюджету является ограничением реализации, не доказательством некорректности исходника. |
| 34 / 14 [temp] export | Не путать поддержку синтаксиса export с раздельной трансляцией.<br>Exported template declaration in one TU, definition in another | LIMIT на resolved; проверены только синтаксис/JSON и повторный разбор. | TM,N | [TemplateSpecializationSyntaxTest]; [TemplateInstantiationExportTest]; [NameResolver] | Остаётся работа реализации; ограничения GCC/Clang не отменяют требование. |
| 35 / 15 [except.handle], [except.throw] | Сохранять порядок типизированных обработчиков и области повторного throw.<br>Nested `catch(const int&) { throw; }` | Исходное исключение и внешние обработчики; допустимый синтаксис rethrow не является ошибкой компиляции. | S,C | [ExceptionCleanupFlowTest] / `typedHandlersAndRethrowUseOrderedEnclosingRegions`; [statements-exceptions] | Не исчерпаны все преобразования/сопоставления обработчиков и копии объектов исключения. |
| 36 / 15 [except.ctor], [except.spec], [except.terminate] | Явно сохранять разрушение и границы спецификации исключений.<br>Second member constructor throws; external `void quiet() throw()` | Разрушать только завершённые члены; нет обычного распространения через пустую спецификацию. | S,C | [ExceptionCleanupFlowTest] / `normalReturnAndConstructorFailureCleanOnlyLiveObjects`, `exceptionSpecificationsEnforceTheirBoundary` | Разрушение частичного массива явно консервативно; нет полного runtime-эталона unexpected/terminate. |
| 37 / 16 [cpp.replace] | Различать prescan, строкификацию, склейку и повторное сканирование.<br>`#define S(x) #x`; `#define J(a,b) a##b` | Исходное написание для #/##; неверная склейка диагностируется с происхождением. | PP,L | [CppPreprocessorTest] / `stringizationUsesRawSpellingAndCollapsesOnlyWhitespace`, `pasteUsesRawArgumentsThenRescansAndNeverPastesAnArgumentHashHash`; [macros] | Все пограничные случаи повторного сканирования не сопоставлены каждому абзацу 2003. |
| 38 / 16 [cpp.cond], [cpp.include], [cpp.line] | Сохранять конфигурацию TU, неактивный текст и логические координаты.<br>`-DDEBUG=0`: `#ifdef DEBUG` versus `#if DEBUG`; `#line` | Разная активность ветвей; физические/логические позиции различаются. | PP,L | [CppPreprocessorTest]; [conditional-catalog]; [diagnostic-provenance] | Политика включений/окружения зависит от реализации; все директивы 2003 не проверены по отдельности. |

### Наличие положительных, отрицательных и граничных проверок

Это **наличие конкретных утверждений в исходниках**, не результат нового запуска
и не полнота главы. Во всех строках приёмка главы остаётся **OPEN**. «Граничный»
здесь означает указанный крайний случай, а не все границы стандарта. Примеры из
основной таблицы иллюстрируют свойства; лишь ссылки в этой таблице удостоверяют
существование соответствующего теста. Для export положительного межфайлового
приёмочного сценария здесь нет.

| Глава | Положительная проверка | Отрицательная проверка | Отдельная граница |
|---|---|---|---|
| 1 | [TypedConstantFoldingTest] / `foldedShortCircuitNeverDropsExecutedSideEffects` | [ParserRecoveryIT] | [TypedConstantFoldingTest] / `foldingRetainsErrorsEvenInsideUnevaluatedBranches` |
| 2 | [CppLexerTest] / `replacesAllTrigraphsBeforeSplicingAndPreservesPhysicalRanges` | [CppLexerTest] / `partialTokensReportTheirPhysicalBeginningAfterEarlierTransformations` | [CppLexerTest] / `unicodeAndUcnIdentifiersUseCpp03AnnexEInsteadOfJavaOrModernUnicodeRules` |
| 3 | [ScopedResolutionTest] / `sharedInlineFunctionDefinitionsAreNotOdrConflicts` | [ScopedResolutionTest] / `conflictingReturnTypesAndMultipleDefinitionsAreDiagnosed` | [ScopedResolutionTest] / `unknownCompositionCanLinkAUniqueExternalDefinitionButNotAlternativeVariants` |
| 4 | [ExpressionSemanticsTest] / `arraysFunctionsNullQualificationAndReferenceBinding` | [OverloadResolutionTest] / `recursivePointerQualificationRequiresConstAtIntermediateLevels` | [ExpressionSemanticsTest] / `promotionsUseTargetRanksRatherThanEqualWidths` |
| 5 | [casts-memory], [literals-expressions] | [ObjectMemoryValidationTest] / `sizeofRejectsKnownInvalidOperands` | [TypedConstantFoldingTest] / `shiftsAndUndefinedOrImplementationDefinedOperationsStayConservative` |
| 6 | [statements-exceptions] | [Cpp03StatementScopesTest] / `invalidJumpsAndLabelsAreDiagnosed` | [Cpp03StatementScopesTest] / `jumpsMayBypassUninitializedPodObjects` |
| 7 | [ScopedResolutionTest] / `usingDirectivesMeetAtTheirCommonEnclosingNamespace` | [ScopedResolutionTest] / `conflictingStorageLinkageIsDiagnosed` | [ScopedResolutionTest] / `cLanguageLinkageDoesNotInfectAnUnrelatedCppOverload` |
| 8 | [platform-declarators], [functions-defaults] | [FunctionDefaultsSemanticsTest] / `declarationPointNamesAndInvalidDefaultsAreDiagnosed` | [FunctionDefaultsSemanticsTest] / `parameterScopeAndUnevaluatedRestrictionsFollowCpp03Baseline` |
| 9 | [ClassInitializationSemanticsTest] / `unionConstructionInitializesOnlyTheSelectedMember` | [ClassInitializationSemanticsTest] / `invalidInitializersAndBitWidthsAreDiagnosed` | [ClassInitializationSemanticsTest] / `namespacesAndMultipleUnnamedBitFieldsRemainDistinct` |
| 10 | [inheritance-virtual] | [VirtualInheritanceValidationTest] / `sharedVirtualBaseRequiresUniqueFinalOverriderWithoutAnyCall` | [VirtualInheritanceValidationTest] / `unknownIncompleteAndCyclicHierarchiesRemainConservative` |
| 11 | [OverloadResolutionTest] / `selectedOutOfLineDefinitionKeepsDeclaredPublicAccess` | [OverloadResolutionTest] / `selectedPrivateMemberIsDiagnosedEvenWhenPublicWorseMatchExists` | [OverloadResolutionTest] / `protectedAccessRequiresDerivedReceiverInDerivedContext` |
| 12 | [classes-initialization] | [ClassInitializationSemanticsTest] / `nonBaseAndIndirectNonvirtualInitializersAreRejectedEvenWithVirtualBases` | [ClassInitializationSemanticsTest] / `storageAndTemporaryBoundariesAreExplicit` |
| 13 | [overloads-adl] | [OverloadResolutionTest] / `pairwiseVectorsRemainAmbiguousAndNoViableIsNotBound` | [OverloadResolutionTest] / `worseAmbiguousUserConversionDoesNotDefeatExactStandardCandidate` |
| 14 | [templates-dependent], [template-instantiation] | [TemplateInstantiationExportTest] / `nestedBodyFailureExportsPrimaryTypeAndOrderedRequestRanges` | [TemplateInstantiationSemanticsTest] / `recursiveInstantiationHasAnExplicitResourceLimit`; export: нет положительной межфайловой реализации |
| 15 | [statements-exceptions] | Нет полного реестра отрицательных исходников | [ExceptionCleanupFlowTest] / `partialArrayConstructionIsConservativelyIncomplete`, `exceptionSpecificationsEnforceTheirBoundary` |
| 16 | [macros], [conditional-catalog] | [CppPreprocessorTest] / `rejectsMalformedAndNonCpp03MacroForms` | [CppPreprocessorTest] / `recursiveMacrosTerminateAndDisabledTokensStayDisabled` |

## 4. Приложения A–E

Следующая таблица также является навигацией по группам, не реестром абзацев.
Библиотечная часть D относится к отдельному аудиту библиотеки.

| Annex | Тема | P/M/R/A/G | Свидетельства | Ограничение |
|---|---|---|---|---|
| A | Сводка грамматики | L,PP,N,T,X,S,C,V,O,TM | [Parser], [TypeParser], [ExpressionParser], [StatementParser] | Нет перечня по каждой продукции. |
| B | Количественные пределы | TM | [TemplateInstantiationSemanticsTest] / `recursiveInstantiationHasAnExplicitResourceLimit`, `breadthBudgetStopsBeforePublishingTooManyInstances` | Проверены лишь бюджеты шаблонов; нет полного реестра рекомендуемых пределов. |
| C | Совместимость | T,N | [DeclaratorTest], [ScopedResolutionTest] | Это не полный аудит различий C/C++. |
| D | Устаревающие возможности | N,X; аудит библиотеки | [ScopedResolutionTest]; [library-catalogue.ru.md](library-catalogue.ru.md) | Нет полного реестра устаревающих языковых возможностей; bool++ и преобразование строкового литерала здесь не подтверждены. |
| E | Универсальные имена символов | L | [CppIdentifierCharacters], [CppLexerTest] | Таблица явно переписана из N1638, не сверена с 2003. |

## 5. Диагностика не требуется: отдельный статус

Нельзя превращать любое несовпадение с желаемым поведением в обязательную ошибку
компилятора. Требуется отдельно учитывать диагностируемое нарушение, IFNDR,
неопределённое поведение, неустановленное поведение и документированное решение
профиля. Для `UNVERIFIED` нельзя автоматически выбрать ни один из этих статусов.

В доступном **N1638**, не в подтверждённом тексте 2003, обнаружены следующие
ориентиры явного отсутствия обязательной диагностики:

| Ориентир N1638 | Как использовать при следующей сверке |
|---|---|
| 2.10/2 `[lex.name]` | Зарезервированные идентификаторы |
| 3.2/3 `[basic.def.odr]` | Требования определения используемой сущности |
| 3.3.6/1 `[basic.scope.class]` | Повторный поиск в завершённой области класса |
| 7.1.2/4 `[dcl.fct.spec]` | Согласованность inline между единицами |
| 14/7–9 `[temp]` | Межфайловые требования к шаблонам/export |
| 14.6/7 `[temp.res]` | Неинстанцированный шаблон без допустимой специализации |

Это **не полный перечень NDR и не удостоверенные номера 2003**. При получении
исходной редакции каждый ориентир нужно сверить заново. Требование диагностики
не следует из того, что GCC/Clang выдал сообщение. Статические предупреждения CPG
можно добавлять сверх стандарта, но необходимо обозначить их как собственный анализ.
Отрицательные runtime-примеры с UB не должны иметь эталона вывода или результата.
Тесты [TypedConstantFoldingTest] проверяют консервативность для ряда опасных
выражений; они не доказывают отсутствие всех форм UB в исходнике.

## 6. Явные пробелы и условия закрытия

1. **Источник:** получить разрешённый для чтения полный текст ISO/IEC 14882:2003,
   проверить титул/редакцию/полноту и зафиксировать устойчивую ссылку и хеш.
   Нынешний блокер: прямые чтения указанных полных копий вернули 403; доступный
   preview не содержит нормативных глав. Обход ограничения не выполнялся.
2. **Знаменатель:** составить перечень каждого нормативного абзаца, подпункта,
   таблицы и грамматической продукции глав 1–16 и применимых приложений. Отдельно
   отметить справочные заметки/примеры; не превращать их автоматически в требования.
3. **Построчная запись:** издание; раздел/метка/абзац; источник/страница;
   собственное краткое описание правила; положительный/отрицательный вход;
   классификация диагностики; P/M/R/A/G; точный тест/утверждение; команда/результат;
   отличие N1638/DR; реализовано/частично/нет/не применимо с причиной.
4. **Реальные ограничения:** отдельная трансляция экспортированных шаблонов
   не поддержана; таблица UCN имеет происхождение N1638; полного реестра asm,
   volatile-эффектов, всех количественных пределов и совместимости нет. «Нет
   свидетельства в этом аудите» не равно «доказано отсутствие реализации».
5. **Анализ:** полная арифметика, раскладка/ABI, произвольное alias/lifetime,
   частичное создание массивов, все неявные вызовы и полная двухфазная семантика
   не становятся точными от успешного сохранения исходника. Неизвестность и
   неподдержанные случаи должны оставаться видимыми.
6. **Приёмка:** после устранения пробелов повторить штатный API/CLI, JSON/SVG,
   копирование, изменение/пересчёт, генерацию/повторный разбор, строгие GCC/Clang.
   Современный `-std=c++03` является полезной проверкой совместимости, но не
   независимым доказательством текста 2003, особенно для ретроактивных DR/export.

## 7. Воспроизведение

Исходники тестов являются первичным свидетельством этих строк. Ключевые методы
названы в таблице; в файле можно найти их по точному имени. [Корпус][manifest] и
[Cpp03CorpusIT] задают действующие сценарии. [Инструкция E2E](e2e.ru.md) описывает
JDK 21, оба нативных компилятора и Graphviz.

```sh
# Полная приёмка текущего репозитория, а не подтверждение каждого абзаца ISO:
mvn verify
python3 scripts/check-cpp03-reports.py
# Одна группа; после такого фильтра нельзя выдавать отчёт за полный корпус:
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=statements-exceptions verify
# Точечный семантический тест:
mvn -Dtest=OverloadResolutionTest test
```

Успех этих команд закрывает только реально выполненные утверждения. Для закрытия
#48 как полного аудита стандарта дополнительно необходимы пункты 1–6 выше.

[BaseSpecifier]: ../src/main/java/com/kniazkov/cpg/model/BaseSpecifier.java
[CallExpression]: ../src/main/java/com/kniazkov/cpg/model/CallExpression.java
[ClassDeclaration]: ../src/main/java/com/kniazkov/cpg/model/ClassDeclaration.java
[ClassInitializationSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ClassInitializationSemanticsTest.java
[ClassSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/ClassSemantics.java
[ClassTemplateInstance]: ../src/main/java/com/kniazkov/cpg/model/ClassTemplateInstance.java
[ClassTemplateSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/ClassTemplateSemantics.java
[ClassTemplateSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ClassTemplateSemanticsTest.java
[ClassesInitializationIT]: ../src/test/java/com/kniazkov/cpg/e2e/ClassesInitializationIT.java
[ConstantEvaluator]: ../src/main/java/com/kniazkov/cpg/analysis/ConstantEvaluator.java
[ConstantFolder]: ../src/main/java/com/kniazkov/cpg/analysis/ConstantFolder.java
[ControlFlow]: ../src/main/java/com/kniazkov/cpg/analysis/ControlFlow.java
[Cpp03CorpusIT]: ../src/test/java/com/kniazkov/cpg/e2e/Cpp03CorpusIT.java
[Cpp03StatementParserTest]: ../src/test/java/com/kniazkov/cpg/parser/Cpp03StatementParserTest.java
[Cpp03StatementScopesTest]: ../src/test/java/com/kniazkov/cpg/Cpp03StatementScopesTest.java
[CppIdentifierCharacters]: ../src/main/java/com/kniazkov/cpg/parser/CppIdentifierCharacters.java
[CppLexer]: ../src/main/java/com/kniazkov/cpg/parser/CppLexer.java
[CppLexerTest]: ../src/test/java/com/kniazkov/cpg/parser/CppLexerTest.java
[CppLiteralsTest]: ../src/test/java/com/kniazkov/cpg/CppLiteralsTest.java
[CppMemoryFlow]: ../src/main/java/com/kniazkov/cpg/analysis/CppMemoryFlow.java
[CppOperatorExpressionTest]: ../src/test/java/com/kniazkov/cpg/parser/CppOperatorExpressionTest.java
[CppPreprocessor]: ../src/main/java/com/kniazkov/cpg/parser/CppPreprocessor.java
[CppPreprocessorTest]: ../src/test/java/com/kniazkov/cpg/parser/CppPreprocessorTest.java
[DataFlow]: ../src/main/java/com/kniazkov/cpg/analysis/DataFlow.java
[DeclaratorTest]: ../src/test/java/com/kniazkov/cpg/parser/DeclaratorTest.java
[DeleteExpression]: ../src/main/java/com/kniazkov/cpg/model/DeleteExpression.java
[ExceptionCleanupFlowTest]: ../src/test/java/com/kniazkov/cpg/ExceptionCleanupFlowTest.java
[ExpressionExportTest]: ../src/test/java/com/kniazkov/cpg/ExpressionExportTest.java
[ExpressionInfo]: ../src/main/java/com/kniazkov/cpg/model/ExpressionInfo.java
[ExpressionParser]: ../src/main/java/com/kniazkov/cpg/parser/ExpressionParser.java
[ExpressionSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ExpressionSemanticsTest.java
[FunctionDefaultsSemanticsTest]: ../src/test/java/com/kniazkov/cpg/FunctionDefaultsSemanticsTest.java
[FunctionFlow]: ../src/main/java/com/kniazkov/cpg/model/FunctionFlow.java
[FunctionsDefaultsIT]: ../src/test/java/com/kniazkov/cpg/e2e/FunctionsDefaultsIT.java
[InheritanceSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/InheritanceSemantics.java
[InheritanceSemanticsTest]: ../src/test/java/com/kniazkov/cpg/InheritanceSemanticsTest.java
[LexicalPhasesIT]: ../src/test/java/com/kniazkov/cpg/e2e/LexicalPhasesIT.java
[NameBindings]: ../src/main/java/com/kniazkov/cpg/generation/NameBindings.java
[NameExpression]: ../src/main/java/com/kniazkov/cpg/model/NameExpression.java
[NameResolver]: ../src/main/java/com/kniazkov/cpg/analysis/NameResolver.java
[NewExpression]: ../src/main/java/com/kniazkov/cpg/model/NewExpression.java
[ObjectMemoryDataFlowTest]: ../src/test/java/com/kniazkov/cpg/ObjectMemoryDataFlowTest.java
[ObjectMemoryValidationTest]: ../src/test/java/com/kniazkov/cpg/ObjectMemoryValidationTest.java
[OperatorSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/OperatorSemantics.java
[OperatorSemanticsTest]: ../src/test/java/com/kniazkov/cpg/OperatorSemanticsTest.java
[OverloadResolution]: ../src/main/java/com/kniazkov/cpg/analysis/OverloadResolution.java
[OverloadResolutionTest]: ../src/test/java/com/kniazkov/cpg/OverloadResolutionTest.java
[ParameterDeclaration]: ../src/main/java/com/kniazkov/cpg/model/ParameterDeclaration.java
[Parser]: ../src/main/java/com/kniazkov/cpg/parser/Parser.java
[ParserRecoveryIT]: ../src/test/java/com/kniazkov/cpg/e2e/ParserRecoveryIT.java
[Preprocessing]: ../src/main/java/com/kniazkov/cpg/model/Preprocessing.java
[PreprocessingToken]: ../src/main/java/com/kniazkov/cpg/parser/PreprocessingToken.java
[PreprocessorIT]: ../src/test/java/com/kniazkov/cpg/e2e/PreprocessorIT.java
[PreservedSource]: ../src/main/java/com/kniazkov/cpg/generation/PreservedSource.java
[ProfileTypeSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ProfileTypeSemanticsTest.java
[ScopeDeclarationParserTest]: ../src/test/java/com/kniazkov/cpg/parser/ScopeDeclarationParserTest.java
[ScopedResolutionTest]: ../src/test/java/com/kniazkov/cpg/ScopedResolutionTest.java
[ScopesLinkageIT]: ../src/test/java/com/kniazkov/cpg/e2e/ScopesLinkageIT.java
[Source]: ../src/main/java/com/kniazkov/cpg/generation/Source.java
[StandardConversions]: ../src/main/java/com/kniazkov/cpg/analysis/StandardConversions.java
[StatementParser]: ../src/main/java/com/kniazkov/cpg/parser/StatementParser.java
[TemplateDeduction]: ../src/main/java/com/kniazkov/cpg/analysis/TemplateDeduction.java
[TemplateInstance]: ../src/main/java/com/kniazkov/cpg/model/TemplateInstance.java
[TemplateInstantiationExportTest]: ../src/test/java/com/kniazkov/cpg/TemplateInstantiationExportTest.java
[TemplateInstantiationSemanticsTest]: ../src/test/java/com/kniazkov/cpg/TemplateInstantiationSemanticsTest.java
[TemplateOrdering]: ../src/main/java/com/kniazkov/cpg/analysis/TemplateOrdering.java
[TemplateSpecializationSyntaxTest]: ../src/test/java/com/kniazkov/cpg/TemplateSpecializationSyntaxTest.java
[TemplateSyntaxTest]: ../src/test/java/com/kniazkov/cpg/TemplateSyntaxTest.java
[TypeNode]: ../src/main/java/com/kniazkov/cpg/model/TypeNode.java
[TypeParser]: ../src/main/java/com/kniazkov/cpg/parser/TypeParser.java
[TypeSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/TypeSemantics.java
[TypedConstantFoldingTest]: ../src/test/java/com/kniazkov/cpg/TypedConstantFoldingTest.java
[VirtualInheritanceValidation]: ../src/main/java/com/kniazkov/cpg/analysis/VirtualInheritanceValidation.java
[VirtualInheritanceValidationTest]: ../src/test/java/com/kniazkov/cpg/VirtualInheritanceValidationTest.java
[casts-memory]: ../src/test/resources/cpp03/casts-memory/sources
[classes-initialization]: ../src/test/resources/cpp03/classes-initialization/sources
[conditional-catalog]: ../src/test/resources/cpp03/conditional-catalog/sources
[diagnostic-provenance]: ../src/test/resources/cpp03/diagnostic-provenance
[functions-defaults]: ../src/test/resources/cpp03/functions-defaults/sources
[inheritance-virtual]: ../src/test/resources/cpp03/inheritance-virtual/sources
[lexical-phases]: ../src/test/resources/cpp03/lexical-phases/sources
[literals-expressions]: ../src/test/resources/cpp03/literals-expressions/sources
[macros]: ../src/test/resources/cpp03/macros/sources
[manifest]: ../src/test/resources/cpp03/scenarios.txt
[overloads-adl]: ../src/test/resources/cpp03/overloads-adl/sources
[platform-declarators]: ../src/test/resources/cpp03/platform-declarators/sources
[scopes-linkage]: ../src/test/resources/cpp03/scopes-linkage/sources
[statements-exceptions]: ../src/test/resources/cpp03/statements-exceptions/sources
[template-instantiation]: ../src/test/resources/cpp03/template-instantiation/sources
[templates-dependent]: ../src/test/resources/cpp03/templates-dependent/sources
