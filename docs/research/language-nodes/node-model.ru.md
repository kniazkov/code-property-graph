<!-- CPG language syntax research: proposed reusable node shapes and invariants. MIT. -->
# Предварительная модель узлов

[Обзор](README.ru.md) · [Матрица](syntax-matrix.ru.md) · [Примеры](cases-and-roadmap.ru.md)

Все структуры этого документа — **проектное предложение**, не готовое Java API. Запись `?` означает необязательное поле, `[]` — упорядоченный список, `A | B` — типизированный вариант. Это не предложение добавить динамический словарь полей вместо нормальных Java-классов.

## 1. Общие правила

У каждого синтаксического узла полезно иметь `origin?` с исходным файлом/диапазоном, а у синтетического — сведения о создавшем проходе. Профиль языка лучше хранить на `SourceUnit`, а происхождение преобразованного узла — отдельно: язык происхождения не равен множеству языков генерации.

Дети обозначают синтаксическое владение и обходятся в заданном порядке. Ссылки на объявления, функции и цели переходов — отдельные рёбра графа, не дети. Благодаря этому обычный обход AST не зацикливается на рекурсивном вызове.

Общие роли: `Expression`, `Statement`, `Declaration`, `BindingTarget`, `TypeSyntax`, `Pattern`. Роли могут пересекаться через явные оболочки: например, `ExpressionStatement` содержит expression. Не надо делать любой expression допустимым statement во всех языках.

У общих узлов должны быть типизированные поля специфики, например `CppCallableDetails`, а не список произвольных строк. Специфику добавляем по мере реализации. Неизвестная конструкция не становится валидной только потому, что её текст положили в `extras`.

## 2. Переменные и связывание

### Предлагаемые формы

| Узел / значение | Общие поля | Варианты и необязательные поля |
|---|---|---|
| `VariableDeclaration` | `binding`, `declarationKind` | `typeUse?`, `initializer?`, `modifiers[]`, `attributes[]` |
| `DeclarationGroup` | `declarations[]` | `sharedSpecifiers?`, сведения о совместной исходной записи |
| `BindingTarget` | Типизированный вариант | `NameBinding`, `SequenceBinding`, `ObjectBinding`, `RestBinding`; defaults — только в разрешённых контекстах |
| `TypeUse` | `mode` | `Explicit(TypeSyntax)`, `Placeholder(kind)`, `Annotation(Expression)`; отсутствие TypeUse — отсутствие записи типа |
| `Initializer` | `kind` | `ExpressionInit(expr)`, `DirectInit(args)`, `ListInit(elements)`, `DesignatedInit(entries)` |
| `Assignment` | `target`, `value`, `form` | `form = STATEMENT / EXPRESSION / NAMED_EXPRESSION`; compound operation — отдельный вариант |
| `AssignmentTarget` | Типизированный вариант | Имя, member, index, unpacking; это не обязательно объявление |

Почему `BindingTarget` и `AssignmentTarget` различаются: объявить имя и записать новое значение в существующий объект — разные роли. Общие составные структуры распаковки можно переиспользовать, но валидатор должен знать роль. Java pattern также может ввести имя, однако проверка образца не равна присваиванию.

`declarationKind` хранит различие `var`/`let`/`const` у JS и категорию local/field/global там, где это нужно. Не стоит отображать JS `const`, Java `final` и C `const` одним безусловным флагом `immutable`: они не обещают одинаковую неизменяемость объектов.

`TypeSyntax` описывает написанную конструкцию: именованный тип, квалификация, применение параметров, массив, указатель, ссылка, функциональная форма и т. п. Общие части переиспользуются, языковые комбинации проверяются отдельно. Это не универсальная система типов. `resolvedType?` при необходимости относится к результатам анализа, а не подменяет TypeUse.

### Минимальные инварианты

- `Explicit` требует TypeSyntax; `Placeholder` требует указания конкретного placeholder, например `CPP_AUTO`, `CPP_DECLTYPE_AUTO`, `JAVA_VAR`, `C_AUTO`.
- `Annotation` хранит собственное выражение и не утверждает, что оно является обязательным runtime-типом переменной.
- Инициализатор не обязателен вообще, но обязателен для конкретных вариантов и контекстов. Например, обычное JS `const x = ...` и Java local `var` имеют свои ограничения; binding в заголовке цикла проверяется иначе [JS-STMT, J-STMT].
- У каждого declarator — собственная структура типа. В `int *p, n;` указатель относится только к `p` [C23].
- Пустой TypeUse нельзя трактовать как «вывести тип из initializer» для всех языков.
- Python `x = e` первоначально остаётся Assignment. Озеленение может представить выбранное связывание общим узлом, только сохранив правила области видимости и повторных записей [PY-SCOPE].

Разрешение имени может указывать на объявление либо на отдельно представленный binding-symbol. Второе полезно для Python и нескольких деклараций одной сущности в C/C++, но это отдельный этап развития: исследование не требует немедленно перестраивать ссылки прототипа.

## 3. Функции, параметры, классы

| Семейство | Предлагаемые поля | Существенные ограничения |
|---|---|---|
| `Callable` | `kind`, `name?`, `parameters[]`, `returnType?`, `body`, `modifiers[]`, `typeParameters[]` | `body = Block / Expression / Absent / Special`; отсутствие тела не равно пустому телу |
| `Parameter` | `binding`, `typeUse?`, `defaultValue?`, `passingKind` | Сохранять positional-only, keyword-only, variadic/rest/pack как разные варианты |
| `Lambda` | `parameters[]`, `body`, `returnType?`, `captureSpec?`, `flavor` | C++ explicit capture и JS arrow — самостоятельные flavors, не флаг anonymous |
| `TypeDeclaration` | `kind`, `name?`, `members[]`, `bases[]`, `typeParameters[]`, `modifiers[]` | Вид struct/union/class/interface/record/enum сохраняется |
| `BaseSpecifier` | `typeOrExpression`, `role` | C++ access/virtual и Python вычисляемая база допускаются только своими вариантами |
| `TypeParameter` | `name`, `kind`, `bounds[]`, `default?`, `variadic` | C++ type/value/template parameters не сводить к Java type parameter |
| `MetadataApplication` | `kind`, `target`, `payload` | Annotation, attribute и decorator различаются по payload и порядку применения |

У callable полезно различать функцию, метод, конструктор, деструктор, accessor и operator function. `async`, `generator`, exception specification, receiver parameter, constructor initializers добавляются типизированными деталями. `Special`-тело нужно, например, для C++ `= default` / `= delete`, а не для хранения любого непонятного текста.

Один общий контейнер members удобен для обходов. Он не доказывает одинаковую модель классов: Python class-body выполняется, а C struct не содержит методов языка C. Анализатор должен спрашивать роль/профиль, а не выводить свойства по одному имени узла [PY-COMPOUND, C11].

Шаблон C++ предлагается хранить как `TemplateDeclaration(parameters, constraints?, declaration)` и отдельные specialization-формы. Java/Python type parameters могут использовать общие элементы списка, но не обязаны получать C++-оболочку. Это максимизирует переиспользование частей без ложной идентичности механизмов [CPP-TEMPLATE].

## 4. Выражения

| Узел | Поля | Что не терять |
|---|---|---|
| `NameReference` | `name`, `target?` | Unresolved — допустимое состояние анализа, а не сломанная ссылка |
| `QualifiedName` | `segments[]` | Квалификация не обязана вычислять receiver |
| `Literal` | `kind`, `value`, `syntaxDetails?` | Числовой суффикс, строковый префикс, radix при необходимости |
| `Unary` / `Binary` | `operator`, operands | Operator — идентификатор операции с профилем, не только токен |
| `ShortCircuit` | `operator`, `operands`, `resultMode` | Boolean/coerced result и выбор значения операнда различаются |
| `Call` | `callee`, `arguments[]`, `typeArguments[]` | `Argument = Positional / Named / Spread / KeywordSpread`; порядок сохраняется |
| `MemberAccess` | `receiver`, `member`, `accessKind` | Pointer access, private name, computed property |
| `Subscript` | `receiver`, `selectors[]` | `Selector = Index / Slice`; не превращать запятую внутри expression в список индексов без проверки грамматики |
| `Conditional` | `condition`, `thenValue`, `elseValue` | Порядок полей не зависит от порядка токенов языка |
| `ComparisonChain` | `operands[]`, `operators[]` | Число operands на 1 больше числа operators |
| `Interpolated` | `flavor`, `parts[]`, `tag?` | Part = text / expression с format-spec; t-string не обычная строка |
| `Suspend` | `kind`, `value?` | await/yield/delegating-yield/co_return не смешивать |

Для JS optional chaining лучше `OptionalChain(base, steps[])`, где каждый step отмечает условный доступ/вызов. Это удерживает границу цепочки; локальный `nullable=true` на любом `MemberAccess` слишком легко теряет её при перестройке [JS-EXPR].

`Aggregate` предлагает общий список элементов, но с обязательным видом `ARRAY_LITERAL`, `OBJECT_LITERAL`, `TUPLE_LITERAL`, `INITIALIZER_LIST` и т. д. Entry может быть value, key/value, designated element, spread. Перед генерацией проверяются допустимые комбинации. C++ initializer не следует выдавать за обычный вызов конструктора без знания формы [CPP-INIT].

## 5. Управляющие конструкции

| Узел | Предлагаемые поля | Варианты |
|---|---|---|
| `Block` | `items[]` | Роль тела; scope определяется профилем/контекстом |
| `If` | `condition`, `thenBody`, `elseBody?` | `init?`, `conditionBinding?`, режим runtime/constexpr/consteval |
| `While` | `condition`, `body` | `normalExitBody?` для Python |
| `DoWhile` | `body`, `condition` | Отдельная форма с постусловием |
| `ForControl` | `init[]`, `condition?`, `update[]`, `body` | Допустимые init/update ограничивает язык |
| `ForEach` | `bindingOrTarget`, `source`, `body`, `iterationKind` | `await`, `normalExitBody?`; JS for-in — отдельный enumeration-kind |
| `Selection` | `subject`, `arms[]`, `form` | Statement/expression; switch/match flavor |
| `SelectionArm` | `labelsOrPatterns[]`, `guard?`, `body` | Fallthrough/arrow-rule/pattern-case; default явно |
| `Transfer` | `kind`, `value?`, `label?`, `target?` | return/break/continue/goto/switch-yield |
| `Try` | `body`, `handlers[]`, `elseBody?`, `finallyBody?` | Handler kinds: typed, catch-all, Python except/except* |
| `ResourceScope` | `resources[]`, `body`, `flavor` | Java resources, Python context managers; правила выхода различаются |

Список arms удобен для обходов, но преобразование switch в match — отдельный проход. Нельзя автоматически ставить `break` после каждого arm: это уничтожит предусмотренный fallthrough. Java `yield` относится к получению результата switch, а не к coroutine suspension [J-STMT].

Шаблоны сопоставления предлагается представить отдельным семейством: `Wildcard`, `Capture`, `LiteralPattern`, `TypePattern`, `SequencePattern`, `MappingPattern`, `DeconstructionPattern`, `OrPattern`, `AsPattern`. Допустимые виды и сочетания задаёт профиль. Повторно использовать NameBinding и списки компонентов можно; объявлять все эти формы одним выражением нельзя.

## 6. Импорты, директивы, диалекты

`Import` может иметь `source`, `items[]`, `aliases[]`, `importKind`, `languageDetails`. Java package import, Python import и JS module import остаются разными kind. C++ module declaration/export и Java module directives требуют собственных форм [J-MODULE, CPP-MODULE, JS-MODULE].

`PreprocessorDirective` хранит вид, токены аргументов и вложенные условные группы, когда это требуется. Не надо объявлять body произвольного макроса обычным AST: до раскрытия он может быть фрагментом конструкции. `#include` не следует переименовывать в Import с обещанием обычного разрешения ссылок.

Для будущего диалекта удобно переиспользовать общие выражения/блоки и добавлять минимальную специальную оболочку. В монолитной библиотеке это обычные Java-классы и ветви parser/validator/emitter. Конкретные AscendC-правила здесь не утверждаются.

## 7. Проверки и неизменяемость

Предлагаемый порядок получения снимка:

1. Проверить обязательные поля, типы детей, арность вариантов и отсутствие циклов владения.
2. Проверить принадлежность всех прямых ссылок создаваемому снимку. Неизвестная цель остаётся явной unresolved-ссылкой.
3. Проверить выбранный профиль исходного языка и реализованные контекстные ограничения. Не выдавать эту проверку за полноценную компиляцию.
4. Создать неизменяемый граф целиком. При ошибке — никакого частичного графа.

Перед генерацией дополнительно проверить целевой профиль и требуемые преобразования. Отсутствие типа или подходящего представления должно диагностироваться до вывода частичного результата. Для API прототипа это направление развития, не утверждение о наличии всех проверок сегодня.

Замена в мутабельном графе продолжает перенаправлять ссылки; удаление при оставшихся входящих ссылках запрещается. При преобразовании одного binding в несколько будущий проход обязан явно указать соответствие старых и новых целей. Автоматическая замена не должна угадывать, какое из нескольких новых имён означает старое.
