# Unterminated string / Незакрытый литерал

`main.cpp:2:5: Unterminated string literal` is the expected diagnostic. The API
throws and CLI exits 1 without JSON or generated sources. The failure is lexical,
not a claim that string expressions are already fully supported by the parser.

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=unterminated-literal verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --input src/test/resources/cpp03/unterminated-literal/sources \
  --json target/bad-literal.json --out target/bad-literal-sources
```

Ручной CLI намеренно завершается кодом 1. Ожидаемая физическая позиция: строка 2,
колонка 5. Частичные экспорты запрещены. Корпус сверяет диагностику API и CLI.
