# parser-recovery

The fourteenth mandatory C++03 scenario uses the ordinary `compile_commands.json` and two source files. `a.cpp` preserves `retained()` before an unrecoverable signature error. `b.cpp` is independently parsed with `-DVALUE=7` and must retain `usable()` returning 7; its `#error` guard detects loss of the per-unit configuration. `a.cpp` has its own `-DSTOP=3` setting.

The strict API throws. `Cpg.readCompilationDatabaseRecovering` returns both files with root/file error flags, a physical `stoppedAt` position and the raw skipped fragment for `a.cpp`. The requested `resolved` stage does not run analyses on this damaged project. CLI/API JSON must match. CLI returns 1 and emits readable JSON and a real SVG containing the error, stopped position, retained function and valid neighbor. A joint `--out` request must not create source output. This is a negative diagnostic oracle, not a native execution oracle.

Четырнадцатый обязательный сценарий читает обычную базу компиляции и два файла. В `a.cpp` сохраняется `retained()` перед невосстановимой ошибкой сигнатуры. `b.cpp` независимо использует `-DVALUE=7`, сохраняет `usable()` с возвратом 7 и проверяет наличие настройки через `#error`; для `a.cpp` задан отдельный `-DSTOP=3`. Строгий API выбрасывает исключение, восстанавливающий возвращает оба файла, отметки ошибки, физическую точку остановки и исходный пропущенный фрагмент. Анализы повреждённого проекта пропущены. JSON API/CLI совпадает, настоящий SVG показывает ошибку и доступные узлы. CLI возвращает 1; совместный запрос исходников не создаёт каталог вывода.

Requires JDK 21 and Graphviz (`dot`); the full corpus also requires GCC and Clang. / Нужны JDK 21 и Graphviz (`dot`); для полного корпуса также нужны GCC и Clang.

```sh
mvn -Dit.test=ParserRecoveryIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=parser-recovery verify

# Expected CLI status: 1. JSON/SVG exist; target/recovery-sources must not exist.
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --compile-commands src/test/resources/cpp03/parser-recovery/compile_commands.json \
  --json target/recovery.json --svg target/recovery.svg --out target/recovery-sources
```

Use fresh output paths when repeating the manual command; existing diagnostic files are not overwritten. `ParserRecoveryIT` also covers missing/nonempty initializers, the active attribute paired with `inactive-extension`, later-standard literals/macros and finite termination at truncated input/EOF. See [RU](../../../../../docs/model-guide.ru.md#восстановление-парсера-и-диагностический-экспорт-21) / [EN](../../../../../docs/model-guide.en.md#parser-recovery-and-diagnostic-exports-21) for the deliberately narrow recovery boundary.
