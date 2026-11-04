# Lexical phase and coordinate fixture / Фазы лексики и координаты

The ordinary C++03 source uses trigraphs before line splicing, a comment between
`int` and `main`, identifiers spanning physical lines, a continued line comment,
`not_eq`, and digraph braces. The original and generated programs exit 0 without
output under both strict GCC and Clang.

`main` spans physical 1:8–2:3. The return expression's `value` spans 8:12–9:3.
The final trigraph `??>` occupies 10:1–10:4. The `@` on physical line 6 belongs
to a line comment continued by the previous line's `??/` and must not be parsed.
`not_eq` retains its lexical spelling while its parser form is `!=`.

```sh
mvn -Dit.test=LexicalPhasesIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=lexical-phases verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --input src/test/resources/cpp03/lexical-phases/sources \
  --json target/lexical.json --out target/lexical-sources
```

Исходник и результат генерации дают код 0 и пустой вывод. `LexicalPhasesIT`
проверяет отображение каждого логического символа имени `main` в физические
координаты, границы триграфа, альтернативное написание и равенство API/CLI.
Корпус проверяет диапазон чтения `value`, ссылку на объявление и оба компилятора.
