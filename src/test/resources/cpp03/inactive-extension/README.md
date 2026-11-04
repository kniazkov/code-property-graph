# inactive-extension

The fifteenth mandatory C++03 scenario places `struct __attribute__((packed)) Hidden ...` inside `#if 0`. It must produce no active syntax node or parse error for that declaration, preserve the inactive source text on regeneration, and retain the valid `main()` returning 0. The public API and packaged CLI succeed. Original and generated sources execute with empty output and status 0 under both GCC and Clang using `-std=c++03 -pedantic-errors`.

`ParserRecoveryIT` supplies the negative pair: the same attribute in active code reports `main.cpp:1:8` at `__attribute__`, marks the diagnostic graph, returns CLI status 1 and blocks source output. Accepting inactive text does not enable GCC/Clang dialects.

Пятнадцатый обязательный сценарий помещает packed-атрибут структуры в `#if 0`. Объявление не создаёт активного узла или ошибки разбора; генерация сохраняет неактивный текст и корректный `main()` с возвратом 0. API/CLI успешны. Исходник и результат исполняются с пустым выводом и кодом 0 под обоими строгими компиляторами. Парный активный случай в `ParserRecoveryIT` диагностируется на `__attribute__` в позиции 1:8, даёт граф с ошибкой и код CLI 1 без генерации исходников. Поддержка диалектов GCC/Clang этим не вводится.

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=inactive-extension verify
mvn -Dit.test=ParserRecoveryIT verify

g++ -std=c++03 -pedantic-errors src/test/resources/cpp03/inactive-extension/sources/main.cpp -o /tmp/cpg-inactive-gcc
/tmp/cpg-inactive-gcc
clang++ -std=c++03 -pedantic-errors src/test/resources/cpp03/inactive-extension/sources/main.cpp -o /tmp/cpg-inactive-clang
/tmp/cpg-inactive-clang
```

See [E2E RU](../../../../../docs/e2e.ru.md#восстановление-парсера-и-диагностический-экспорт-21) / [EN](../../../../../docs/e2e.en.md#parser-recovery-and-diagnostic-exports-21) for the full acceptance workflow.
