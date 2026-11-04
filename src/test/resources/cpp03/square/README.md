# SQUARE milestone fixture / Пример первой вехи

Two translation units share `sources/include/square.h`. The two expansions are
`SQUARE(a)` in `main.cpp` and `SQUARE(c)` in `second.cpp`. Values are deliberately
distinct: `a = 3`, `b = 5`, `c = 7`.

- Before editing: `3 * 3 + 7 * 7 = 58`, stdout `58\n`, exit 0.
- After replacing only the first expansion's right operand with `b`:
  `3 * 5 + 7 * 7 = 64`, stdout `64\n`, exit 0.
- `expected/edited-main.cpp` is a hand-written acceptance oracle, **not generated
  output**. The common header and `second.cpp` remain byte-for-byte unchanged.

Две единицы трансляции используют один заголовок и два вызова SQUARE. До правки
ожидается 58, после замены только правого операнда первого раскрытия — 64.
Файл `expected/edited-main.cpp` задаёт ожидаемый результат будущего редактора,
а не доказывает его реализацию. Импорт и препроцессор реализованы в #13–#15; происхождение
и правка графа принимаются в #16–#17.

From the repository root / Из корня репозитория:

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=square verify
cd src/test/resources/cpp03/square
for compiler in g++ clang++; do
  "$compiler" -std=c++03 -pedantic-errors -Isources/include \
    sources/main.cpp sources/second.cpp -o /tmp/cpg-square-original
  /tmp/cpg-square-original # 58
  "$compiler" -std=c++03 -pedantic-errors -Isources/include \
    expected/edited-main.cpp sources/second.cpp -o /tmp/cpg-square-edited
  /tmp/cpg-square-edited # 64
done
```

`compile_commands.json` uses relative directories for portability: resolve
`directory` from the database's parent directory and `file` from that working
directory. Its `arguments` entries compile each translation unit; link both
objects to create the executable. No external paths or installed CPG are needed.

The compilation-database corpus now verifies full CLI/API JSON parity, both macro
expansions, source generation, and strict GCC/Clang execution of original and
generated programs (58). The edited oracle (64) is still hand-written: graph
provenance and macro-aware editing remain #16–#17. No tests are skipped.

Корпус проверяет API/CLI, JSON, оба раскрытия, генерацию и строгие нативные
прогоны исходной и сгенерированной программы (58). Эталон 64 остаётся ручным;
происхождение в графе и редактирование макросов — задачи #16–#17.
