# Token-level macro provenance / Происхождение макросов

The header defines SQUARE, nested NEST, multi-statement TWO, partial-expression
TAIL, and empty EMPTY. SQUARE(a) creates two distinct NameExpression nodes from
one raw argument. NEST(f()) creates two distinct CallExpression nodes from one
raw argument and records NEST as the nested SQUARE expansion's parent.
Original and generated programs exit 0 with empty output under strict C++03.

```sh
mvn verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --input src/test/resources/cpp03/provenance/sources \
  --json target/provenance.json --svg target/provenance.svg
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --input src/test/resources/cpp03/provenance/sources \
  --svg target/provenance-plain.svg --provenance off
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=provenance verify
```

Expected JSON: SourceFile.preprocessing.expansions has two SQUARE events, one
NEST parent, TWO, EMPTY and TAIL. The MultiplyExpression.provenance expansionIds
refer to those events within sourceFileId. Each SQUARE event has one arguments
entry; its definition/replacement fragments point to include/macros.h:3 while
invocation points to main.cpp:5 or :6. Final token spellingRange for '*' points
to the header; duplicated argument tokens point to the same source fragment,
but their token indices and syntax node IDs are distinct. EMPTY has equal
startToken/endToken; TWO covers both declarations; TAIL covers only '+ 2'.

SVG purple dashed origin edges lead from the multiplication to its macro event,
then invocation and definition/replacement operator edges lead to the .cpp/.h
coordinates. The layer is absent with --provenance off; syntax and flow stay.

Заголовок содержит SQUARE, вложенный NEST, две инструкции TWO, часть выражения
TAIL и пустой EMPTY. Два чтения/вызова имеют разные синтаксические узлы, но один
исходный аргумент. JSON хранит события и токены отдельно от дерева; sourceFileId
задаёт область локальных ID. В SVG фиолетовые пунктирные рёбра ведут от умножения
к событию, вызову в .cpp и оператору в .h. Опция --provenance off отключает слой.
Обычный Visitor не посещает записи происхождения. Проверяются API/CLI, JSON,
нативная генерация, пустые границы и настоящий SVG в scripts/demo.sh.
