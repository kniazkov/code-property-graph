<!-- CPG language syntax research: primary sources and version boundaries. MIT. -->
# Источники и границы версий

[Обзор](README.ru.md) · [Матрица](syntax-matrix.ru.md)

Проверено 03.11.2026. Ссылки ведут на спецификации, материалы комитетов и официальную документацию. Обозначения в квадратных скобках в других документах относятся к таблицам ниже. Названия предлагаемых узлов — наши проектные решения, а не терминология стандартов.

## Текущие ориентиры

| Язык | Основа исследования | Статус и оговорки |
|---|---|---|
| C | C23, ISO/IEC 9899:2024; для сравнения C11 | Публичный N3096 — рабочий текст перед публикацией C23, не сама платная ISO-редакция. C2y рассматривается только как будущая ветвь |
| C++ | C++11, C++17, опубликованный C++23; значимые изменения C++14/20/23 | Текущий опубликованный стандарт — ISO/IEC 14882:2024 (C++23). C++26 рассматривается как рабочий проект, даже если отдельные возможности уже приняты комитетом |
| Java | JLS Java SE 27 и официальная история изменений | Java 27 опубликована в сентябре 2026. Preview не считается обычной возможностью языка |
| Python | Language Reference 3.15; история ключевых конструкций Python 3 | Не анализируем Python 2. Номер patch-релиза не фиксируется: документация ветви обновляется |
| JavaScript | ECMA-262, ECMAScript 2026, 17-я редакция | Ссылки фиксированы на `/2026/`. Живой `/ecma262/` уже может описывать следующий проект. TypeScript, JSX, DOM и Node.js API исключены |

Статус C проверен по [плану WG14](https://open-std.org/jtc1/sc22/wg14/www/projects.html); статус C++ — по [странице стандарта](https://isocpp.org/std/the-standard) и [статусу работ](https://isocpp.org/std/status). Java — по [каталогу спецификаций](https://docs.oracle.com/javase/specs/) и [release notes 27](https://www.oracle.com/java/technologies/javase/27-relnote-issues.html). Python — по [What's New 3.15](https://docs.python.org/3.15/whatsnew/3.15.html). ECMAScript — по [каталогу ECMA-262](https://ecma-international.org/publications-and-standards/standards/ecma-262/).

## Реестр источников

### C и C++

| ID | Источник | Используемые части |
|---|---|---|
| C11 | [WG14 N1570, 2011](https://www.open-std.org/jtc1/sc22/wg14/www/docs/n1570.pdf) | §6.5 выражения; §6.7 объявления; §6.8 операторы; §6.10 препроцессор |
| C23 | [WG14 N3096, 2023](https://www.open-std.org/jtc1/sc22/wg14/www/docs/n3096.pdf) | §6.7.6 declarators; §6.7.9 вывод типа; §6.7.10 инициализация; §6.7.12 атрибуты; приложение A — грамматика |
| CPP11 | [WG21 N3337, 2012](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2012/n3337.pdf) | Публичный рабочий текст после C++11; `[expr.prim.lambda]`, `[dcl.spec.auto]`, `[stmt.ranged]` |
| CPP17 | [WG21 N4659, 2017](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2017/n4659.pdf) | Рабочий текст C++17; `[dcl.struct.bind]`, `[stmt.if]`, `[expr.prim.fold]` |
| CPP14-FEATURES | [WG21 P0096R5](https://www.open-std.org/JTC1/SC22/WG21/docs/papers/2017/p0096r5.html) | Таблицы возможностей C++11/14/17 и ссылки на принятые papers |
| CPP20-CHANGES | [WG21 P2131R0](https://open-std.org/JTC1/SC22/WG21/docs/papers/2020/p2131r0.html) | Обзор изменений между C++17 и C++20 DIS; core language отдельно от библиотек |
| CPP23-THIS | [P0847R7](https://open-std.org/JTC1/SC22/WG21/docs/papers/2021/p0847r7.html) и [N4945](https://open-std.org/JTC1/SC22/WG21/docs/papers/2023/n4945.html) | Explicit object parameter и редакторский отчёт о включении |
| CPP23-STATUS | [N4951, отчёт редакторов](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2023/n4951.html) | Отношение рабочего текста N4950 к C++23; это отчёт, не полная спецификация |
| CPP-AUTO | [Текущий draft: placeholder types](https://eel.is/c++draft/dcl.spec.auto) | `auto`, `decltype(auto)`, контексты вывода типа |
| CPP-BIND | [Текущий draft: structured bindings](https://eel.is/c++draft/dcl.struct.bind) | Структура связывания; новые детали нельзя автоматически приписывать C++17 |
| CPP-INIT | [Текущий draft: initialization](https://eel.is/c++draft/dcl.init) | Варианты инициализаторов, списки, designated initializers |
| CPP-STMT | [Текущий draft: statements](https://eel.is/c++draft/stmt) | Циклы, условные операторы, переходы |
| CPP-LAMBDA | [Текущий draft: lambdas](https://eel.is/c++draft/expr.prim.lambda) | Захваты и параметры |
| CPP-CLASS | [Текущий draft: classes](https://eel.is/c++draft/class) | Члены, конструкторы, деструкторы |
| CPP-TEMPLATE | [Текущий draft: templates](https://eel.is/c++draft/temp) | Параметры, специализации, ограничения |
| CPP-REQUIRES | [Текущий draft: requires expressions](https://eel.is/c++draft/expr.prim.req) | Виды требований |
| CPP-MODULE | [Текущий draft: modules](https://eel.is/c++draft/module) | Module units, import, export |
| CPP26-REFLECT | [WG21 P2996R13](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2025/p2996r13.html) | Синтаксис reflection/splicing; [сообщение о принятии в draft C++26](https://isocpp.org/blog/2025/06/reflection-voted-into-cpp26-whole-new-language-herb-sutter) |
| CPP26-CONTRACT | [Текущий draft: function contracts](https://eel.is/c++draft/dcl.contract.func) | Контрактные спецификаторы функций |

`eel.is/c++draft` — HTML-представление текущего редакторского рабочего текста, а не фиксированная опубликованная редакция ISO. Ссылки на именованные разделы удобны для исследования, но точные ограничения старого режима надо сверять с его текстом. Это особенно важно для лямбд и structured bindings.

Публичные PDF N4140, N4861 и N4950 не удалось надёжно прочитать используемым средством извлечения. Поэтому здесь **нет заявления о полном построчном сравнении C++14/20/23**: промежуточные версии отражены на уровне существенных возможностей, текущей грамматики и редакторских материалов. Перед реализацией потребуется фиксировать конкретный draft и проверять соответствующую feature-paper. N3220 также не используется как точная копия опубликованного C23: WG14 относит его к ранним текстам C2y.

### Java

| ID | Источник JLS 27 / официальный справочник | Используемые части |
|---|---|---|
| JAVA | [JLS 27](https://docs.oracle.com/en/java/javase/27/docs/specs/jls/index.html) | Базовая редакция |
| J-TYPE | [Глава 4](https://docs.oracle.com/en/java/javase/27/docs/specs/jls/jls-4.html) | Синтаксические категории типов и переменных |
| J-MODULE | [Глава 7](https://docs.oracle.com/en/java/javase/27/docs/specs/jls/jls-7.html) | Compilation units, package, imports, modules |
| J-CLASS | [Глава 8](https://docs.oracle.com/en/java/javase/27/docs/specs/jls/jls-8.html) | Классы, поля, методы, конструкторы, records, неявные классы |
| J-INTERFACE | [Глава 9](https://docs.oracle.com/en/java/javase/27/docs/specs/jls/jls-9.html) | Interfaces и annotations |
| J-STMT | [Глава 14](https://docs.oracle.com/en/java/javase/27/docs/specs/jls/jls-14.html) | Локальные объявления, циклы, switch, try, patterns |
| J-EXPR | [Глава 15](https://docs.oracle.com/en/java/javase/27/docs/specs/jls/jls-15.html) | Выражения, вызовы, lambda, method references |
| J-VERSION | [Language Changes Summary](https://docs.oracle.com/en/java/javase/27/language/java-language-changes-summary.html) | Даты финализации и preview; не приравнивать первое preview к постоянной поддержке |

### Python

| ID | Источник Python 3.15 | Используемые части |
|---|---|---|
| PY-LEX | [Lexical analysis](https://docs.python.org/3.15/reference/lexical_analysis.html) | Отступы, литералы, f-strings/t-strings |
| PY-SCOPE | [Execution model](https://docs.python.org/3.15/reference/executionmodel.html) | Связывание имён и области видимости |
| PY-EXPR | [Expressions](https://docs.python.org/3.15/reference/expressions.html) | Вызовы, сравнения, comprehensions, lambda, yield, await |
| PY-SIMPLE | [Simple statements](https://docs.python.org/3.15/reference/simple_stmts.html) | Присваивание, аннотации, import, global/nonlocal, type |
| PY-COMPOUND | [Compound statements](https://docs.python.org/3.15/reference/compound_stmts.html) | Циклы, try/with, match, функции, классы |
| PY14 | [What's New 3.14](https://docs.python.org/3.14/whatsnew/3.14.html) | Template strings и отложенное вычисление аннотаций |
| PY15 | [What's New 3.15](https://docs.python.org/3.15/whatsnew/3.15.html) | Lazy imports и unpacking в comprehensions |

### JavaScript

| ID | Источник ECMA-262, 2026 | Используемые части |
|---|---|---|
| JS | [Оглавление](https://tc39.es/ecma262/2026/multipage/) | Фиксированная редакция |
| JS-LEX | [Lexical grammar](https://tc39.es/ecma262/2026/multipage/ecmascript-language-lexical-grammar.html) | Литералы и автоматическая вставка `;` |
| JS-EXPR | [Expressions](https://tc39.es/ecma262/2026/multipage/ecmascript-language-expressions.html) | Операторы, property access, вызовы, optional chains |
| JS-STMT | [Statements and declarations](https://tc39.es/ecma262/2026/multipage/ecmascript-language-statements-and-declarations.html) | Объявления, binding patterns, циклы, switch, try |
| JS-FUNC | [Functions and classes](https://tc39.es/ecma262/2026/multipage/ecmascript-language-functions-and-classes.html) | Функции, стрелки, generators/async, элементы классов |
| JS-MODULE | [Scripts and modules](https://tc39.es/ecma262/2026/multipage/ecmascript-language-scripts-and-modules.html) | Import/export, различие Script и Module |

## Вехи, влияющие на модель

Это ориентир для будущих feature-gates, а не обещанный список поддерживаемого синтаксиса. Базовые конструкции более старых версий сохраняют значение; C++11 не является началом языка.

| Язык / версия | Что учитывать в форме узлов |
|---|---|
| C99 | Смешение объявлений и операторов, объявление в `for`, compound literals, designated initializers, VLA |
| C11 | `_Generic`, `_Atomic`, `_Static_assert`, средства выравнивания [C11] |
| C17 | В этом исследовании отдельной новой семьи синтаксических узлов не требует |
| C23 | `auto` для вывода типа, `typeof`, `nullptr`, атрибуты `[[...]]`, `constexpr`; не считать все эти возможности эквивалентами одноимённых C++-возможностей [C23] |
| C++11 | Лямбды, `auto`, range-for, rvalue references, variadic templates, `constexpr`, `nullptr`, атрибуты [CPP11] |
| C++14 | Generic lambdas и init-capture: расширения параметров/захватов, а не новый корень дерева [CPP14-FEATURES] |
| C++17 | Structured bindings, `if constexpr`, init-statement в `if`/`switch`, fold expressions [CPP17] |
| C++20 | Concepts/requires, модули, корутины, designated initialization: новые структурные формы [CPP20-CHANGES, CPP-REQUIRES, CPP-MODULE, CPP-INIT] |
| C++23 | Explicit object parameter, `if consteval`, многомерный subscript: расширяются параметры, условные формы и индексирование [CPP23-THIS, CPP-CLASS, CPP-STMT] |
| C++26 draft | Reflection/splicing и контракты: зарезервировать отдельные формы, не реализовывать сейчас [CPP26-REFLECT, CPP26-CONTRACT] |
| Java 8 | Lambda и ссылки на методы [J-EXPR] |
| Java 9–17 | 9: modules; 10: local `var`; 11: `var` в параметрах lambda; 14: switch expressions; 15: text blocks; 16: records и instanceof patterns; 17: sealed [J-VERSION] |
| Java 21–27 | 21: record patterns/pattern switch; 22: unnamed variables/patterns; 25: module imports, compact source files, flexible constructor bodies. В 27 primitive patterns всё ещё preview [J-VERSION] |
| Python 3.5–3.8 | 3.5: async/await; 3.6: variable annotations и f-strings; 3.8: `:=`, positional-only параметры [PY-LEX, PY-SIMPLE, PY-COMPOUND] |
| Python 3.10–3.13 | 3.10: match; 3.11: except*; 3.12: type parameters и `type` alias; 3.13: defaults type parameters [PY-COMPOUND, PY-SIMPLE] |
| Python 3.14–3.15 | 3.14: t-strings и отложенные аннотации; 3.15: lazy imports и unpacking в comprehensions [PY14, PY15] |
| ECMAScript 2015 | `let`/`const`, binding patterns, стрелки, классы, generators, modules |
| ECMAScript 2017–2022 | 2017: async functions; 2018: async generators/iteration; 2020: optional chaining, `??`, BigInt, dynamic import; 2022: class fields/private elements/static blocks, top-level await |
| ECMAScript 2026 | Верхняя фиксированная редакция сравнения. Возможности живого следующего draft не включать автоматически |

Новые C++ нельзя считать только косметикой: concepts, modules и coroutines C++20, а также reflection в проекте C++26 меняют структуру программы. Для этого проекта это повод оставить место под отдельные узлы, а не требование немедленно покрыть весь язык.

## Как продолжать точечное исследование

Для каждой реализуемой возможности сохранять: язык и редакцию; раздел спецификации; 2–3 собственных положительных примера; отрицательный пример; поля узла; ограничения исходного языка; допустимые цели генерации; условия озеленения. Даты в этой таблице служат навигацией, но не заменяют проверку конкретного правила грамматики.
