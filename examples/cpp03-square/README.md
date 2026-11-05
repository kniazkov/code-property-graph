# Edit one SQUARE expansion / Правка одного раскрытия SQUARE

Run from the repository root with JDK 21, GCC and Clang installed:

```sh
mvn verify
mkdir -p target/edit-square-classes
javac -cp target/cpg-0.0.1-SNAPSHOT.jar -d target/edit-square-classes examples/cpp03-square/EditSquare.java
java -cp target/cpg-0.0.1-SNAPSHOT.jar:target/edit-square-classes EditSquare \
  src/test/resources/cpp03/square/compile_commands.json target/square-edited
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --compile-commands target/square-edited/compile_commands.json --json target/square-reparsed.json
for compiler in g++ clang++; do
  "$compiler" -std=c++03 -pedantic-errors -Isrc/test/resources/cpp03/square/sources/include \
    src/test/resources/cpp03/square/sources/main.cpp src/test/resources/cpp03/square/sources/second.cpp -o target/square-before
  target/square-before # 58
  "$compiler" -std=c++03 -pedantic-errors -Itarget/square-edited/sources/include \
    target/square-edited/sources/main.cpp target/square-edited/sources/second.cpp -o target/square-after
  target/square-after # 64
 done
cmp src/test/resources/cpp03/square/sources/include/square.h target/square-edited/sources/include/square.h
cmp src/test/resources/cpp03/square/sources/second.cpp target/square-edited/sources/second.cpp
```

Choose a fresh output directory on repeated runs. The demo uses the public
ProjectEditor API, replacing only the first multiplication's right NameExpression
with a fresh `b` node. At the resolved stage, implicit lvalue-to-rvalue
ConversionExpression nodes wrap the reads. The demo unwraps only implicit
conversions to find and replace the written operand, retaining its surrounding
semantic wrapper and provenance. Explicit casts are not unwrapped.
The generated expression is `a * b` with required
parentheses. The shared header and second call remain byte-for-byte unchanged.
The original snapshot still generates the original program. Reparsed JSON has
MultiplyExpression(a,b) and MultiplyExpression(c,c). `edited.json` retains the
original expansion provenance on the replaced node.

Команды запускаются из корня проекта. Для повторного запуска выберите новый
каталог вывода. Демонстрация через публичный ProjectEditor заменяет только
правое чтение первого раскрытия на b; результат меняется с 58 на 64.
Заголовок и второй файл совпадают побайтно. JSON после повторного разбора
содержит a*b и c*c, а JSON редактированного снимка сохраняет происхождение.
Для поиска и замены написанного операнда пример проходит через неявные
ConversionExpression, сохраняя эти семантические узлы; явные приведения
типов не разворачиваются.
Тот же пример запускается в полном CI из scripts/demo.sh, без ручного оракула.
