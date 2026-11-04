# Alternative translation-unit configurations / Варианты единиц трансляции

Four commands produce two main.cpp and two engine.cpp syntax versions. SERVER
uses marker/return 11, CLIENT 22. Both selected two-unit programs return 0 with
empty stdout under strict GCC and Clang, before and after generation.

`BuildVariantsIT` and this mandatory corpus scenario verify API/CLI JSON parity,
stable identities, two variants per path, explicit composition selection,
local Active::main binding, unknown run() ambiguity, isolated CFG/DFG, invalid
selection, and generated-source reparsing. A separate test distinguishes a known
build's conflicting free-function definitions from unknown-build alternatives.

[Manual public-API selection demo](../../../../../examples/cpp03-builds/README.md).

Четыре команды дают по две версии main.cpp и engine.cpp. SERVER возвращает 11,
CLIENT 22; каждая выбранная программа завершается с кодом 0 без вывода.
Проверяются отдельные графы, выбор состава, цели вызовов, изоляция потоков,
неоднозначность неизвестной сборки и диагностика конфликтующих определений.

```sh
mvn -Dit.test=BuildVariantsIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=build-variants verify
```
