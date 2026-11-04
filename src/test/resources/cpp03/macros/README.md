# macros

Macro/include acceptance: macro-generated header names, guards, nested arguments, raw pasting, stringization, recursion suppression and repeated unguarded inclusion with changed context. The ordinary source and generated source both exit 0 with empty stdout under strict GCC and Clang.

Приёмочный пример макросов и включений: макроимя заголовка, guards, вложенные аргументы, склейка, строкификация, подавление рекурсии и повторное включение при изменённом контексте. Исходная и сгенерированная программы завершаются с кодом 0 без вывода.

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=macros verify
```
