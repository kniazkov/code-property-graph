<!-- Code Property Graph: Russian model and analysis reference. MIT License. -->
# Модель графа и анализ программ

[English version](model-guide.en.md) · [README](../README.md)

Этот справочник описывает публичный API и грамматику, реализованные на 4 ноября 2026 года. Примеры рассчитаны на Java 21. При добавлении конструкции обновляй обе версии справочника, таблицу покрытия в конце и соответствующий тест парсера или CLI.

## Получить снимок проекта

```java
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import java.nio.file.Path;
import java.util.*;

// Внутри метода, при запуске из корня репозитория
Project project = Cpg.read(Path.of("examples/unread-write/java"),
        new Cpg.Options(Language.JAVA, Cpg.Stage.RESOLVED));
SourceFile file = project.files().getFirst();
```

Пример читает папку с программой, где первое значение переменной `score` перезаписывается до чтения. Чтение папки нерекурсивное, на запуск выбирается один язык. Файлы обрабатываются в порядке имён. Ошибка разбора даёт `parser.ParseException` с именем файла, строкой и столбцом. Успешный вызов возвращает целый неизменяемый проект.

| Стадия | Какой снимок получишь |
|---|---|
| `PARSED` | Исходное синтаксическое дерево с позициями. `Exit` уже существует. Разрешение имён и анализ ещё не запускались. |
| `RESOLVED` | Разрешённые имена и вызовы, CFG, DFG и диагностика исходных конструкций. Эту стадию удобно выбирать для исследования исходника. |
| `GREEN` | После разрешения имён распознанный консольный вывод заменён на `PrintStatement`. Потоки и предупреждения описывают получившееся дерево. |
| `FOLDED` | Дополнительно свёрнута безопасная константная арифметика. Потоки и предупреждения вычислены для итогового дерева. Стадия CLI по умолчанию. |

## Структура и свойства узла

Корень `Project` владеет списком `files()`. Каждый `SourceFile` хранит путь, язык и упорядоченные элементы `items()`. Ниже находятся объявления (`Declaration`), инструкции (`Statement`), выражения (`Expression`) и записи типов (`TypeNode`). Это отдельные ветви общей иерархии `Node`.

| API | Назначение и пример |
|---|---|
| Именованные геттеры | `BinaryExpression.left()/right()` дают операнды, `VariableDeclaration.name()` — имя, `initializer()` — возможный инициализатор. `BlockStatement.items()` содержит объявления и инструкции в порядке исходника. |
| `children()` | Неизменяемый упорядоченный список детей. У листа он пуст. Для функции или метода последним ребёнком всегда идёт её собственный `exit()`. |
| `references()` | Разрешённые ссылки на другие узлы снимка. У `NameExpression` и `CallExpression` конкретную цель удобнее читать через `target()`. Для внешнего или неоднозначного имени цель может отсутствовать. |
| `range()` | `Optional<SourceRange>` даёт полный путь и полуоткрытый интервал исходника. Строки и столбцы с 1, смещения с 0. Столбцы и смещения в единицах UTF-16. У синтетического `Exit` диапазона нет. Свёрнутый литерал сохраняет диапазон исходного выражения. |
| `languages()`, `source(target)` | Допустимые языки непосредственной генерации поддерева и его текст. Для целых выходных файлов и адаптации точек входа используй `Cpg.sources(project, target)`. |
| `diagnostics()` | Неизменяемый список замечаний к конкретному узлу. Пустой список означает отсутствие прикреплённых замечаний на выбранной стадии. |

Обязательный ребёнок доступен напрямую. У необязательного ребёнка тип результата — `Optional`, у повторяющихся элементов — список. Например, выражение в `return x;` доступно через `ReturnStatement.value()`, а `return;` даёт `Optional.empty()`.

```java
project.walk(new NodeVisitor() {
    @Override public void visit(VariableDeclaration variable) {
        String type = variable.type()
                .filter(NamedType.class::isInstance)
                .map(NamedType.class::cast).map(NamedType::name).orElse("?");
        System.out.println(type + " " + variable.name());
    }
});
```

`accept(visitor)` вызывает обработчик одного узла. `walk(visitor)` проходит дерево в глубину, посещая родителя перед детьми. Обработчик семейства, например `visit(BinaryExpression)`, получает и сложение, и вычитание, если их перегрузки не переопределены. Обход дерева не следует по ссылкам на объявления или по рёбрам потоков.

## Обход CFG и DFG

Оба набора рёбер находятся в `project.flows()`. Каждый `FunctionFlow` описывает одну функцию или метод с телом. `entry()` возвращает объявление, а `exit()` — тот же объект, что и `entry().exit()`. Границы функций не пересекаются. Рекурсивный вызов может ссылаться на объявление функции через `CallExpression.target()`.

**CFG. Какие инструкции могут исполниться следующими?** У ребра `control()` есть `from()`, `to()` и `kind()`. Виды — `ENTRY`, `NEXT`, `TRUE`, `FALSE`, `BACK`, `RETURN`. Для ветвления вершиной служит выражение условия. Из `return` переход ведёт к общему выходу. Недостижимая инструкция остаётся в дереве, но в CFG к ней нет пути от входа.

```java
for (FunctionFlow flow : project.flows()) {
    Map<FlowVertex, List<FunctionFlow.ControlEdge>> outgoing = new IdentityHashMap<>();
    for (var edge : flow.control())
        outgoing.computeIfAbsent(edge.from(), v -> new ArrayList<>()).add(edge);

    Set<FlowVertex> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<FlowVertex> pending = new ArrayDeque<>();
    pending.add(flow.entry());
    while (!pending.isEmpty()) {
        FlowVertex current = pending.removeFirst();
        if (!seen.add(current)) continue;
        for (var edge : outgoing.getOrDefault(current, List.of())) {
            System.out.println(current.getClass().getSimpleName() + " --" + edge.kind()
                    + "--> " + edge.to().getClass().getSimpleName());
            pending.addLast(edge.to());
        }
    }
}
```

Множество посещённых вершин позволяет закончить обход даже при обратном ребре цикла. Для обратного обхода индексируй по `to()`, начни с `exit()` и переходи к `from()`.

**DFG. Что даёт значение этому чтению?** Ребро `VALUE` направлено от значения операнда к потребителю, например от литерала к инициализируемой переменной. Ребро `DEFINITION` идёт от конкретной записи к чтению имени. В примере с `score` у чтения при выводе `target()` указывает на объявление `int score = 1;`, а входящее `DEFINITION` — от присваивания `score = 2`.

```java
for (FunctionFlow flow : project.flows()) {
    Map<Node, List<Node>> writesForRead = new IdentityHashMap<>();
    for (var edge : flow.data()) {
        if (edge.kind() == FunctionFlow.Dependency.DEFINITION)
            writesForRead.computeIfAbsent(edge.to(), n -> new ArrayList<>()).add(edge.from());
    }
    flow.entry().walk(new NodeVisitor() {
        @Override public void visit(NameExpression use) {
            for (Node write : writesForRead.getOrDefault(use, List.of()))
                System.out.println(use.name() + " <- " + write.getClass().getSimpleName()
                        + " " + write.range());
        }
    });
}
```

После слияния ветвей возможны несколько записей для одного чтения. Обратное ребро цикла может принести запись с предыдущей итерации. Чтобы найти все зависимые значения, используй тот же обход с очередью, что и для CFG, но построй индекс по `from()` из `flow.data()` и начни с интересующего узла. Для поиска всех источников результата разверни направление. Сравнивай узлы по идентичности внутри одного снимка.

Следующий фрагмент для каждого присваивания считает другие узлы, достижимые по исходящим рёбрам DFG обоих видов. В программе со `score` путь от второй записи проходит через чтение имени к вызову вывода.

```java
for (FunctionFlow flow : project.flows()) {
    Map<Node, List<Node>> consumers = new IdentityHashMap<>();
    for (var edge : flow.data())
        consumers.computeIfAbsent(edge.from(), n -> new ArrayList<>()).add(edge.to());

    flow.entry().walk(new NodeVisitor() {
        @Override public void visit(AssignmentExpression write) {
            Set<Node> reached = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<Node> queue = new ArrayDeque<>();
            queue.add(write);
            while (!queue.isEmpty()) {
                Node producer = queue.removeFirst();
                if (!reached.add(producer)) continue;
                for (Node consumer : consumers.getOrDefault(producer, List.of()))
                    queue.addLast(consumer);
            }
            System.out.println(write.range() + " -> " + (reached.size() - 1));
        }
    });
}
```

## Результаты проходов и редактирование

Данные проходов доступны через типизированные методы. Разрешение имён заполняет `target()`, анализ потоков — `Project.flows()`, диагностический проход — `Node.diagnostics()`. Нормализация и свёртка меняют само дерево: например, вместо вызова вывода появляется `PrintStatement`.

```java
project.walk(new NodeVisitor() {
    @Override public void visit(Node n) {
        for (Diagnostic d : n.diagnostics()) {
            String location = n.range().map(r -> r.file() + ":" + r.start().line()
                    + ":" + r.start().column()).orElse("?");
            System.out.println(location + " " + d.severity() + " [" + d.code() + "] " + d.message());
        }
    }
});
```

Правило `unread-write` ставит `WARNING` на конкретную инициализацию или присваивание, если значение не достигает чтения. В примере предупреждение относится к первой записи `score`. Для автоматизации используй стабильный `code()`, для человека — `message()`. В JSON диагностике соответствуют `code` и `severity`, а исходящие рёбра в `edges` содержат вид и числовой ID цели. ID назначаются при экспорте конкретного дерева; в Java API используются прямые ссылки.

Узел создаётся через вложенный строитель: `IntegerLiteral.create().setValue(2).build()`. Для правки вызывай `edit()`, меняй строитель и получай новый снимок через `build()`. После правки всего проекта прежние потоки и предупреждения сбрасываются. Если изменения затронули имена, снова разреши их; затем вычисли анализ для нового снимка. Например:

```java
Project edited = project.edit().build();
Project resolved = com.kniazkov.cpg.analysis.NameResolver.resolve(edited);
Project analyzed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(resolved);
```

Для отдельных задач есть `ControlFlow.analyze` и `DataFlow.analyze`; перед DFG нужны разрешённые имена. `UnreadWrites.analyze` строит необходимый DFG. Все эти проходы возвращают результат; дальнейшие запросы делай к возвращённому проекту.

## Какие конструкции читаются из исходника

Таблица описывает штатный вход через `Cpg.read`. Наличие Java-класса в модели само по себе не означает, что соответствующий токен уже распознаётся парсером.

| Конструкция | Java (`.java`) | C++ (`.cpp`) |
|---|---|---|
| Верхний уровень | `class C { … }`, `public class C { … }` | `class C { public: … };`, `int main() { … }` |
| Метод класса | `public static void main(String[] args)` | `static void main()` |
| Локальные объявления | `int x = expression;` | `int x = expression;` |
| Литералы и арифметика | Десятичные целые в диапазоне signed 32-bit, скобки, унарные и бинарные `+`, `-`, бинарные `*`, `/` | То же, плюс `<<` |
| Запись и сравнение | `=` (включая цепочки), `== != < <= > >=` | То же |
| Доступ и вызов | `obj.member`, `f(a, b)`, `C.main(args)` | `obj.member`, `f(a, b)`, `C::main()` |
| Управление | Блоки, `if`, `if/else`, `while`, `return;`, `return expr;` | То же |
| Комментарии и директивы | `//`, `/* … */` | Оба вида комментариев, сохранение `#include <…>`, `#include "…"` |
| Озеленение вывода | `System.out.println(x)` | `std::cout << x << std::endl` |

Имя параметра Java `main` произвольное. Таблица описывает разбор синтаксиса; проверка типов и семантики языка компилятору не эквивалентна. Заголовки C++ не раскрываются. Произвольные сигнатуры функций, конструкторы, шаблоны, массивы общего вида, строки, `for`, `break` и `continue` пока вне входной грамматики.

**Есть классы узлов, но ещё нет разбора из текста:** остаток `%`, правый сдвиг `>>`, логическое отрицание `!`, взятие адреса `&x`, разыменование `*p`, доступ `p->field`. Для C, Python, JavaScript и Ascend C входных парсеров пока нет. Межъязыковая генерация проверена для небольших Java/C++ программ с поддерживаемыми формами `main` и консольного вывода; границы перевода описаны в README.

[Реализация грамматики](../src/main/java/com/kniazkov/cpg/parser) · [Проходы анализа](../src/main/java/com/kniazkov/cpg/analysis) · [Классы модели](../src/main/java/com/kniazkov/cpg/model)

[Файловый корпус C++03](e2e.ru.md) проверяет API и упакованный CLI на одинаковых обычных исходниках, с исполнением через строгие GCC и Clang. Начальное покрытие: возврат литерала, арифметика/нормализация печати и диагностика недопустимого символа. Набор поддерживаемых конструкций этим изменением не расширяется.

[Пример SQUARE](../src/test/resources/cpp03/square/README.md) добавляет две единицы трансляции, общий макрос и базу компиляции. Строгие нативные прогоны проверяют 58 до правки и 64 для ручного эталона результата. Это подготовка примера; препроцессор, происхождение и редактирование графа остаются в #13–#17.
