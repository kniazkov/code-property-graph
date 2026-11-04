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
и сохраняется как независимый эталон. Полная правка через API теперь проверяется в #17.

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

The compilation-database corpus now verifies full API/CLI graphs, original and
generated output 58, then performs the actual ProjectEditor operand replacement
and verifies generated output 64 under both strict compilers. The independent
hand-written oracle is retained as an additional check. Header and second.cpp
are compared byte-for-byte, and packaged CLI reparsing must produce a*b and c*c.

[Manual public-API demo and commands](../../../../../examples/cpp03-square/README.md).
The same demo runs in scripts/demo.sh; provenance and nested precedence tests
are in MacroEditingIT. The first vertical milestone is now implemented, not just
a source fixture. This does not imply full C++03 language/library support.

Корпус проверяет исходный результат 58, настоящую правку графа и результат 64
в GCC и Clang. Ручной эталон остаётся дополнительной независимой проверкой.
Заголовок и второй файл сравниваются побайтно; CLI повторно разбирает a*b и c*c.
Команды демонстрации приведены по ссылке выше. Первая вертикальная веха закрыта;
полная поддержка языка и библиотеки C++03 этим не заявляется.
