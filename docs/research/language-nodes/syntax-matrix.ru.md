<!-- CPG language syntax research: cross-language construct matrix. MIT. -->
# Матрица синтаксических конструкций

[Обзор](README.ru.md) · [Источники и версии](sources-and-versions.ru.md) · [Поля узлов](node-model.ru.md)

Сопоставление относится к базовым языкам и редакциям из реестра источников. `—` означает отсутствие прямой конструкции языка, а не невозможность эмуляции. Совпадение в строке означает пригодность общего представления, **не эквивалентность поведения**. Поля ниже — предложения. Обозначения:

- **Общее** — один узел с небольшими вариантами для перечисленных языков.
- **Семейство** — общий интерфейс/составные части, но разные варианты узлов.
- **Спец.** — оставить языковую форму; её выражения и тела всё равно используют общие узлы.

## Имена, объявления, организация

Основания: [C11] §6.7; [C23] §6.7; [CPP-AUTO, CPP-BIND, CPP-CLASS, CPP-TEMPLATE, CPP-MODULE]; [J-TYPE, J-CLASS, J-INTERFACE, J-MODULE]; [PY-SIMPLE, PY-SCOPE, PY-COMPOUND]; [JS-STMT, JS-FUNC, JS-MODULE]. Ссылки раскрыты в [реестре](sources-and-versions.ru.md).

| Сущность | C | C++ | Java | Python | JavaScript | Предложение |
|---|---|---|---|---|---|---|
| Файл / единица разбора | Translation unit | Translation/module unit | Compilation unit | Module | Script/Module | Общее `SourceUnit(profile, items, unitKind)`; не отождествлять файл с модулем |
| Имя | Identifier | Qualified name, templates | Simple/qualified name | Name | Identifier/private name | Общее `Name`, составное `QualifiedName`; прямые ссылки отдельно |
| Переменная | Declaration | Declaration | Local/field declaration | Assignment, annotated assignment | `var`/`let`/`const` | Общее объявление для четырёх; Python-присваивание сначала отдельно |
| Несколько объявляемых имён | Declarators | Declarators | Declarators | Несколько targets | Binding list | `DeclarationGroup` + отдельные bindings; порядок сохраняется |
| Тип при имени | Type specifiers/declarator | Type/declarator/`auto` | Type / local `var` | Optional annotation | — | Общий `TypeUse` только там, где он есть; отсутствие не считать placeholder |
| Деструктуризация | — | Structured binding | Patterns в специальных контекстах | Unpacking targets | Binding/assignment patterns | Семейство `BindingTarget`; match-pattern — отдельная роль |
| Функция / метод | Function | Function/member | Method | `def` | Function/method | Общее `Callable`; placement и форма тела различаются |
| Формальный параметр | Typed / variadic | Typed/default/pack | Typed/varargs | Разные виды параметров | Binding/default/rest | Общее `Parameter` с типизированными режимами |
| Анонимная функция | — | Lambda | Lambda | Lambda | Function expression/arrow | Семейство `Lambda`; общий список параметров, особые захваты и тело |
| Конструктор / деструктор | — | Оба | Constructor | `def` с особыми именами | Constructor method | `Callable.kind`; Python `__init__` не переименовывать в конструктор при парсинге |
| Структура / класс | struct/union | class/struct/union | class/interface/record/enum | class | class | Семейство `TypeDeclaration`; union/record не скрывать за обычным class |
| Поля / свойства | Members | Data members | Fields | Присваивания атрибутам | Fields/accessors | `Field` для явных объявлений; property access отдельно |
| Перечисление | enum | enum / enum class | enum | — | — | Общее `EnumDeclaration`, разные допустимые элементы |
| Псевдоним типа | typedef | typedef/using | — | `type` | — | Общее `TypeAlias`; целевой синтаксис может быть выражением |
| Параметризация | — | Templates | Generics | Type parameters | — | Общий `TypeParameter`; template-wrapper, specialization и constraints — отдельные формы |
| Метаданные | Attributes | Attributes | Annotations | Decorators | — в базовой модели | Семейство; декоратор сохранять как вычисляемое выражение, не просто строковый тег |
| Пространство имён | — | namespace | package | Организация модулей | — | Спец. оболочки над общими списками объявлений |
| Импорт / экспорт | `#include` | include/import/export | import/module directives | import/from | import/export | Общая навигационная роль `Dependency`; include остаётся препроцессором |
| Препроцессор | Directives/macros | Directives/macros | — | — | — | Спец. `PreprocessorDirective` / условные группы / token body |

## Выражения и значения

Основания: [C11] §6.5; [CPP-INIT, CPP-LAMBDA, CPP-REQUIRES, CPP26-REFLECT]; [J-EXPR]; [PY-EXPR, PY-LEX]; [JS-EXPR, JS-LEX].

| Сущность | C | C++ | Java | Python | JavaScript | Предложение |
|---|---|---|---|---|---|---|
| Числовой / строковый литерал | Да | Да | Да | Да | Да | Общее `Literal(kind, value, spellingHints)`; тип и суффиксы не выбрасывать |
| Отсутствие значения | nullptr в C23; иные формы раньше | nullptr | null | None | null/undefined | Семейство `NullLike`; JS `undefined` синтаксически имя, не keyword-literal |
| Доступ к члену | `.` / `->` | `.` / `->` / qualified names | `.` | `.` | `.` / computed | Общее `MemberAccess`, но qualification отделить от вычисления receiver |
| Индексирование / срез | Index | Index, новые варианты | Array access | Index/slice | Computed property | Общее семейство `Subscript`; ключ, индекс и срез — варианты |
| Вызов | Function call | Call | Invocation | Call | Call | Общее `Call(callee, arguments)`; каждый argument сохраняет форму |
| Создание объекта | Compound literal | new / construction | new | Обычный call | new | Семейство `Creation`; Python-вызов не менять по одному имени |
| Литерал коллекции / агрегат | Initializer/compound literal | Initializer list | Array initializer | list/tuple/set/dict | Array/object | Семейство `Aggregate`; не превращать initializer в обычный runtime-list |
| Унарный / бинарный оператор | Да | Да, перегрузка | Да | Да, специальные методы | Да | Общие `Unary`/`Binary`; operator identity не только строка |
| Логическое короткое замыкание | &&/&#124;&#124; | &&/&#124;&#124; | &&/&#124;&#124; | and/or | &&/&#124;&#124; | Общее семейство `ShortCircuit`, отдельный режим результата |
| Условное выражение | `?:` | `?:` | `?:` | `a if c else b` | `?:` | Общее `Conditional(condition, then, else)` |
| Присваивание | Expression | Expression | Expression | Statement; `:=` отдельно | Expression | Общая структура записи, разные допустимые контексты |
| Составное присваивание | Да | Да | Да | Да | Да | `AugmentedAssignment`; не разворачивать слепо в чтение + запись |
| Инкремент / декремент | Да | Да | Да | — | Да | Общее `Update(prefix/postfix)` для четырёх языков |
| Цепочка сравнений | Бинарные операции | Бинарные операции | Не Python-цепочка | Comparison chain | Бинарные операции | Спец. `ComparisonChain` с общими operands/operators |
| Приведение / проверка типа | Cast | Cast family | Cast/instanceof | Нет прямого cast-оператора | Нет прямого cast-оператора | Семейство `TypeOperation`, тип операции обязателен |
| Размер / информация о типе | sizeof/alignof/typeof | sizeof/alignof/decltype/typeid | Некоторые специальные формы | — | typeof | Общая роль запроса; аргумент expression/type различать; JS typeof — отдельный вид |
| Interpolation | — | — в базовой форме | — в финальной базовой модели | f-/t-strings | Template/tagged template | Семейство `Interpolated`; различать вычисление строки и объект шаблона |
| Распаковка / spread | — | Pack expansion | — как общий оператор | `*` / `**` | `...` | Общий вспомогательный `Expansion`, но разные kind/context |
| Comprehension | — | — | — | Comprehensions/generator expressions | — | Спец. `Comprehension` с общими clauses, expressions, bindings |
| Ожидание / выдача значения | — | co_await/co_yield | — как корутина | await/yield/yield from | await/yield/yield* | Семейство `Suspend`; Java switch-yield сюда не входит |
| Проверка требований / reflection | _Generic | requires; reflection draft26 | — в соответствующем смысле | — | — | Спец. формы, переиспользующие TypeUse/Expression |

## Управление и области выполнения

Основания: [C11] §6.8; [CPP-STMT]; [J-STMT]; [PY-COMPOUND, PY-SIMPLE]; [JS-STMT].

| Сущность | C | C++ | Java | Python | JavaScript | Предложение |
|---|---|---|---|---|---|---|
| Последовательность | Compound statement | Compound statement | Block | Suite | Block | Общее `Block(items)`; не обещать одинаковую область видимости |
| Пустое действие | `;` | `;` | `;` | pass | `;` | Общее `EmptyStatement`; Python pass — способ вывода пустого тела |
| Выражение как оператор | Expression statement | Expression statement | Ограниченный набор | Expression statement | Expression statement | Общее `ExpressionStatement`, валидатор ограничивает виды |
| Ветвление | if/else | if/else, constexpr/consteval | if/else | if/elif/else | if/else | Общее `If`; режим compile-time — отдельный вариант |
| Предусловие цикла | while | while | while | while + else | while | Общее `While`; optional normal-exit body ограничивает цели |
| Постусловие цикла | do/while | do/while | do/while | — | do/while | Общее `DoWhile` для четырёх |
| Цикл с тремя частями | for | for | basic for | — | for | Общее `ForControl(init, condition, update, body)` |
| Обход элементов | — | range-for | enhanced for | for | for-of | Общее `ForEach`, сохранять kind iteration и binding |
| Обход ключей | — | — | — | Не отдельная синтаксическая форма | for-in | Вариант JS enumeration, не обычный for-each |
| Множественный выбор | switch | switch | switch statement/expression | match | switch | Семейство `Selection` с разными branch labels/patterns |
| Образец | — | — в этом смысле | Type/record patterns | Pattern family | —; destructuring не match | `Pattern` отдельно от expression и assignment target |
| Выход / переход | return/break/continue/goto | Те же | return/break/continue, labels | return/break/continue | return/break/continue, labels | Семейство `Transfer`; target и контекст обязательны |
| Исключение | — | throw/try/catch | throw/try/catch/finally | raise/try/except/else/finally | throw/try/catch/finally | Общее семейство `Try` + `Throw`; разные handlers |
| Ресурсная область | — | RAII, не отдельный try-resource синтаксис | try-with-resources | with/async with | Не включено в фиксированную модель | `ResourceScope` с отдельными языковыми режимами |
| Синхронизация | — | — как общий оператор | synchronized | — | — | Спец. `Synchronized`, общие lock expression и body |
| Assert | _Static_assert/static_assert | static_assert | assert | assert | — | Общая роль `Assertion`, различать compile-time/runtime |
| Управление binding | — | — | — | global/nonlocal/del | delete expression | Спец. формы; delete JS и del Python не общий «удалить узел» |

## Что из этого действительно озеленять

Первыми кандидатами являются простые литералы, ссылки на локальные имена, арифметические выражения в ограниченной модели, `Block`, `If`, `While`, присваивания и простые объявления. Их зелёность всё равно зависит от детей и режима генерации. Например, имя с неподдерживаемым binding или literal с языковым суффиксом не становится универсальным автоматически.

Функции и классы удобно объединить для обходов раньше, чем для генерации на всех языках: C не имеет классов, Java не имеет обычных свободных функций в смысле C. Снятие таких оболочек или создание новых — отдельное преобразование целого фрагмента, не работа метода печати одного узла.

Препроцессор, специализации C++, Python-comprehensions, JS optional chains и Java-specific module directives разумно сначала оставить красными. Общими остаются их вложенные выражения, имена, тела и связи. Это даёт переиспользование без принудительного понижения всех конструкций в примитивный язык.
