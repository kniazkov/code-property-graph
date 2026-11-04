# Unterminated string / Незакрытый литерал

`main.cpp:2:5: Unterminated string literal` is the expected diagnostic. The strict API
throws; since #21 the recovering API retains a diagnostic graph and the CLI exits 1
with requested JSON/SVG but no generated sources. The failure is lexical,
not a claim that string expressions are already fully supported by the parser.

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=unterminated-literal verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --input src/test/resources/cpp03/unterminated-literal/sources \
  --json target/bad-literal.json --out target/bad-literal-sources
```

Ручной CLI намеренно завершается кодом 1. Ожидаемая физическая позиция: строка 2,
колонка 5. С #21 запрошенные JSON/SVG сохраняются для диагностики, генерация
исходников запрещена. Корпус сверяет исключение строгого API и диагностику CLI.
