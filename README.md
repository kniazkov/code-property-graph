<!-- Code Property Graph command-line interface and Java API. MIT License. -->
# Code Property Graph

Рукописный парсер Java и C++ строит единый неизменяемый `Project` из исходников одной папки. Парсер, разрешение ссылок, озеленение, свёртка, генерация исходников и визуализация используют классы из `com.kniazkov.cpg.model`.

## Запуск из консоли

Собери исполняемый JAR на JDK 21 командой `mvn verify`. Класс с `main` — `com.kniazkov.cpg.Main`. Для вывода SVG установи Graphviz и проверь доступность `dot -V` из той же консоли.

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --help
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from java --input examples/java --to cpp --out target/generated-cpp
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input examples/cpp --stage resolved --svg target/resolved.svg
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input examples/cpp --to java --out target/generated-java --svg target/folded.svg
```

| Параметр | Значение |
|---|---|
| `--input` | Папка исходников. Читаются подходящие файлы непосредственно в ней, в порядке имён |
| `--from` | Язык всей папки, `java` или `cpp` |
| `--stage` | `parsed`, `resolved`, `green`, `folded`. По умолчанию `folded` |
| `--out` | Новая папка для сгенерированных исходников |
| `--to` | Язык исходников в `--out`. По умолчанию совпадает с `--from` |
| `--json` | Новый UTF-8 файл со всем графом выбранной стадии. Сочетается с выводом исходников и SVG |
| `--svg` | Путь для нового SVG. Можно указать вместе с `--out` или отдельно |

Стадии `resolved`, `green` и `folded` включают внутрипроцедурные CFG и DFG. Синие стрелки показывают ход выполнения, пурпурные — передачу значений и достигающие записи. Для Java и C++ поддержаны `if/else`, `while` и сравнения `==`, `!=`, `<`, `<=`, `>`, `>=`. [Модель рёбер и пример анализа](docs/cfg-dfg.ru.md)

SVG показывает снимок **на выбранной стадии**. Сплошные стрелки ведут к детям, пунктирные — к разрешённым объявлениям. Для прежних скриптов поддерживается позиционный вызов `JAVA FOLDED examples/java target/generated-java`.

## Поддерживаемые программы

Примеры в `examples` содержат один класс, метод main, локальные int, присваивания, арифметику, вызовы и консольный вывод. C++ использует дополнительную свободную функцию main. Лексер пропускает комментарии и сохраняет директивы include. Диагностика неподдерживаемого синтаксиса содержит файл, строку и столбец.

`resolved` связывает имена с объявлениями и однозначные квалифицированные вызовы с методами, в том числе между файлами. `green` сводит две поддерживаемые формы консольного вывода к PrintStatement. `folded` сворачивает операции над константами в диапазоне знакового 32-битного int. Деление на ноль и переполнение остаются в графе.

На исходном языке доступны все четыре стадии. Для перевода примеров с выводом выбери `green` или `folded`. При переводе Java в C++ поддерживается одна папка с одним файлом и классом, содержащим main. Генератор добавляет свободную точку входа и iostream. При переводе C++ в Java поддерживается стандартная обёртка, которая вызывает Class::main() и возвращает 0. Файл класса получает расширение целевого языка. Использование аргументов командной строки Java и произвольных C++ заголовков в этих переводах вызывает диагностику.

## Организация пакетов

В `com.kniazkov.cpg` находятся `Cpg` (API библиотеки) и `Main` (CLI). В `parser` лексер создаёт токены с координатами, а читатели объявлений, операторов и выражений работают с общей позицией в потоке. В `analysis` каждый проход можно запустить отдельно. Пакет `generation` отвечает за генерацию исходников, `visualization` — за DOT и SVG.

## Java API

```java
Project parsed = Cpg.read(Path.of("examples/java"), new Cpg.Options(Language.JAVA, Cpg.Stage.PARSED));
Project ready = ConstantFolder.fold(ConsoleNormalizer.green(NameResolver.resolve(parsed)));
Map<String, String> files = Cpg.sources(ready, Language.CPP);
DotVisualizer.renderSvg(ready, Path.of("tree.svg"));
```

`Project`, `Language` и классы узлов находятся в пакете `com.kniazkov.cpg.model`. Каждый узел поддерживает `walk(visitor)`, `edit()` и генерацию фрагмента через `source(language)`. Для изменения ссылок и замены узлов в пределах одного проекта предусмотрен `ProjectEditor`.

`mvn verify` запускает JUnit. `bash scripts/demo.sh` проверяет исполняемый JAR, компиляцию и запуск результатов на обоих языках и SVG через системный dot. Нужны JDK 21, g++, Graphviz, Python 3 и Bash. В CI результаты демонстрации доступны в артефакте `cpg-prototype`.

[Требования](docs/requirements.ru.md) · [Модель узлов](docs/node-model.ru.md) · [Иерархия и Visitor](docs/node-hierarchy.ru.md) · [Сравнение языков](docs/research/language-nodes/README.ru.md)


## Экспорт JSON

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input examples/cpp --stage resolved --json target/project.json
```

`ProjectJson.encode(project)` возвращает объект библиотеки `com.kniazkov:json:2.0.0`, а `ProjectJson.write(project, path)` создаёт файл. Корень имеет ID 0. Далее узлы нумеруются в порядке обхода детей в глубину. Именованные дети вложены в родителя, списки сохраняют порядок. Исходящие связи записаны в `edges` как вид и числовой ID цели. Порядок групп — ref, CFG, DFG, внутри группы по виду и ID цели.

Полный путь записан один раз в `SourceFile.path`. Поле `range` хранит включительное начало и исключительный конец. Строки и столбцы начинаются с 1, смещения с 0. Столбцы и смещения измеряются в единицах UTF-16, табуляция занимает одну единицу. CRLF занимает два смещения и завершает одну строку. У синтетических узлов без исходного фрагмента поле отсутствует. После озеленения и свёртки диапазон указывает на исходную конструкцию.

[Эталон JSON для тестов](src/test/resources/json/return-zero.json) использует маркер пути, который тест заменяет абсолютным именем файла во временной папке.
