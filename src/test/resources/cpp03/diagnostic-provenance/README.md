# diagnostic-provenance

The sixteenth mandatory C++03 scenario verifies diagnostic provenance and stable root-cause summaries through the public recovering API and packaged CLI. Four ordinary compilation databases preserve the complete occurrence context rather than testing synthetic diagnostics alone. Each CLI run exits 1, emits requested diagnostic JSON, and must not create source output. These invalid programs have diagnostic oracles, not native execution results.

Шестнадцатый обязательный сценарий C++03 проверяет происхождение диагностики и устойчивую сводку причин через публичный восстанавливающий API и упакованный CLI. Четыре обычные базы компиляции сохраняют полный контекст срабатываний, а не только искусственно созданные записи. Каждый CLI-прогон возвращает 1, выдаёт запрошенный диагностический JSON и не создаёт исходников. Для ошибочных программ задана диагностика, а не результат нативного исполнения.

## Cases / Случаи

- `assignment`: `ASSIGN(total);` has a missing right operand immediately before `;`. The `macro-invocation` note identifies the call, `macro-definition` the `=` token in `include/assign.h`. The following `int second = ;` is independent and survives as a second error/declaration. / Отсутствующий правый операнд перед `;`, пояснения вызова и `=` определения; следующая ошибка `second` остаётся независимой.
- `paste`: `JOIN(+, *)` fails at the invocation. One primary CLI error has notes for both arguments and the header's `##`; the failed `JOIN` expansion is retained. / Одна основная ошибка на вызове и три поясняющих диапазона, неудавшееся раскрытие сохранено.
- `shared`: `a.cpp` and `b.cpp` include one physical `include/common.h` with a missing initializer. One group retains two occurrences, two translation units and two variants, each with its own include chain. An additional API check imports one physical `.cpp` in two configurations and requires one translation unit but two variants. / Общая ошибка объединяется без потери обоих контекстов; варианты одного `.cpp` не увеличивают число физических единиц.
- `causes`: the common header calls `EXPAND(+, BAD)`; commands use `-DBAD=*` and `-DBAD=/`. Invalid `+*` and `+/` have the same primary physical range but different reasons, so two groups remain. / Разные причины в одной физической позиции не объединяются.

## Physical range oracle / Эталон физических диапазонов

Paths below are relative to this directory. API/JSON store normalized absolute paths. Line and column numbers are one-based; offsets are zero-based. End positions are exclusive. / Пути ниже относительные; API/JSON хранят нормализованные абсолютные пути. Строки и столбцы начинаются с 1, смещения с 0, конец диапазона не включается.

| Case / role | Physical file | Line:column start–end | Offset start–end |
|---|---|---|---|
| assignment primary, missing operand | `assignment/sources/main.cpp` | 4:18–4:18 | 77–77 |
| `macro-invocation` | `assignment/sources/main.cpp` | 4:5–4:18 | 64–77 |
| `macro-definition` (`=`) | `assignment/sources/include/assign.h` | 1:21–1:22 | 20–21 |
| paste primary, `JOIN(+, *)` | `paste/sources/main.cpp` | 2:21–2:31 | 46–56 |
| `macro-argument-left` (`+`) | `paste/sources/main.cpp` | 2:26–2:27 | 51–52 |
| `macro-argument-right` (`*`) | `paste/sources/main.cpp` | 2:29–2:30 | 54–55 |
| `macro-paste-operator` (`##`) | `paste/sources/include/join.h` | 1:21–1:23 | 20–22 |
| shared primary, missing initializer | `shared/sources/include/common.h` | 1:30–1:30 | 29–29 |
| causes primary, `EXPAND(+, BAD)` | `causes/sources/include/common.h` | 3:23–3:37 | 77–91 |

`DiagnosticProvenanceIT` and `Cpp03CorpusIT` use `DiagnosticProvenanceAcceptance`: the same public API/CLI checks, all paste/assignment role paths and coordinates, full JSON equality, primary error count, all occurrence contexts, and unchanged groups after `edit().build()` and `NameResolver.resolve`. The shared and different-cause cases reverse translation-unit order and compare group values and root `diagnosticGroups` JSON. No equality of reordered AST node IDs is implied. Context values contain no syntax-node references; include/expansion IDs are local to the owning unit/configuration. `range`/`skippedText` still describe recovery, independently of `primaryRange`.

Тесты используют общий приёмочный помощник: API/CLI, пути и координаты всех ролей ASSIGN/JOIN, полное равенство JSON, число основных ошибок, контексты срабатываний и сохранность групп после edit/build и повторного анализа. Для общего заголовка и разных причин сравниваются группы и их JSON при обратном обходе. Равенство ID переставленного AST не требуется. Контекст не содержит ссылок на узлы, ID включений/раскрытий локальны для единицы/конфигурации. `range`/`skippedText` сохраняют контракт восстановления независимо от `primaryRange`.

Beyond these four corpus fixtures, `DiagnosticProvenanceIT` creates regressions for independent errors within one macro, unsafe nested-assignment boundaries, and invalid `#line`/`#include` operands expanded from different build definitions. `DiagnosticProvenanceTest` additionally checks redefinition signatures and previous/new definition notes, conditional operands, actual repeated-include IDs and strict exception context. / Помимо четырёх фикстур корпуса, E2E создаёт примеры независимых ошибок одного макроса, небезопасной границы вложенного присваивания и ошибочных операндов `#line`/`#include` из разных настроек сборки. Фокусированные тесты дополнительно проверяют сигнатуры переопределения и прежние/новые определения, операнды условий, ID повторного включения и контекст строгого исключения.

## Reproduce / Воспроизведение

JDK 21 is required; full `mvn verify` also needs GCC, Clang and Graphviz. / Нужен JDK 21; полный `mvn verify` также требует GCC, Clang и Graphviz.

```sh
mvn -Dit.test=DiagnosticProvenanceIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=diagnostic-provenance verify

# Full regressions, then the inventory check (16 scenarios + 2 infrastructure tests):
mvn verify
python3 scripts/check-cpp03-reports.py

# Expected exit status: 1. JSON exists; target/diagnostic-paste-sources must not exist.
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --compile-commands src/test/resources/cpp03/diagnostic-provenance/paste/compile_commands.json \
  --json target/diagnostic-paste.json --out target/diagnostic-paste-sources
```

Replace `paste` with `assignment`, `shared` or `causes` to inspect another database, and use fresh output paths for each run; existing diagnostic files are not overwritten. Do not run the inventory check after a filtered corpus run. / Для другого случая замени `paste` на `assignment`, `shared` или `causes` и задай новые пути вывода: существующие диагностические файлы не перезаписываются. После фильтрованного запуска корпуса проверка полноты отчётов неприменима.

The scope is preprocessing/parsing `ParseProblem` diagnostics, not general semantic grouping or repair after editing an error. / Область реализации — `ParseProblem` препроцессора/парсера, а не все семантические диагностики или исправление ошибки после редактирования.

[Model EN](../../../../../docs/model-guide.en.md#diagnostic-provenance-and-grouping-22) · [Модель RU](../../../../../docs/model-guide.ru.md#происхождение-и-группировка-диагностики-22) · [E2E EN](../../../../../docs/e2e.en.md#diagnostic-provenance-and-grouped-summaries-22) · [E2E RU](../../../../../docs/e2e.ru.md#происхождение-диагностики-и-сводка-групп-22)
