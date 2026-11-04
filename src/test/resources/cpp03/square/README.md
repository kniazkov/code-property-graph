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
а не доказывает его реализацию. Поддержка импорта, препроцессора, происхождения
и правки графа принимается в #13–#17; #12 проверяет воспроизводимость исходников.

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

Current CPG CLI cannot yet process this macro project. Do not present a partial
graph as success. The full CLI/API acceptance will replace `source-fixture` mode
when #13–#17 are implemented. The corpus counts this fixture and requires both
compilers now; there is no disabled or skipped test.
