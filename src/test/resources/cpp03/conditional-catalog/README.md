# Conditional branches and macro catalog / Условные ветви и каталог макросов

The compilation database imports `main.cpp` with `-DDEBUG=0` and an unused
external definition, and `aux.cpp` with a separate unused definition. The header
has unused object/function macros and the initial `SWITCH_VALUE`. Two value
expansions straddle `#undef`/`#define` and must refer to different definition IDs.

`#ifdef DEBUG` contributes only `10` or `20` to an initializer whose common
suffix is `+ 1`. The API checks `Add(10,1)` and `Add(20,1)`. An inactive nested
group neither opens `deliberately-missing.h` nor performs the nested presence
check. `#if BROKEN` retains an invalid initializer without creating its syntax.
`#line 120 "logical.cpp"` changes `__LINE__` without changing physical ranges.
Both original and generated programs exit 0 with empty output under strict GCC
and Clang, with `-DDEBUG=0` and without it. The retained source is byte-identical.

```sh
mvn -Dit.test=ConditionalCatalogIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=conditional-catalog verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --compile-commands src/test/resources/cpp03/conditional-catalog/compile_commands.json \
  --json target/conditional-catalog.json --svg target/conditional-catalog.svg \
  --out target/conditional-catalog-generated
```

Expected JSON: each SourceFile has a `preprocessing` catalog with `definitions`,
`includeStates`, `branches`, `presenceChecks`, `directives`, `logicalLocations`
and `expansions`. `RETRY_LIMIT` has replacement `3`, a header range and an empty
`expansionIds` array. The `DEBUG` presence check has `present=true`,
`conditionResult=true` and a definition link but no DEBUG value expansion.
`sourceNodeId` links external definitions to nodes reachable through the
project's `externalInputs`: database → command → argument, or profile → builtin.
Definition and check IDs are local to their source file's preprocessing record.

The E2E negative tests enable `BROKEN` (syntax failure) and remove `#endif`
(preprocessing failure without CLI exports). Unit tests separately specify the
fixed integer/character evaluation model, malformed directives and reproducible
date/time macros. This fixture does not claim multi-configuration editing (#20).

База задаёт `DEBUG=0` только для `main.cpp`; внешний неиспользованный макрос
каждой команды остаётся достижимым обычным Visitor. `RETRY_LIMIT` и функциональный
макрос заголовка сохраняются без вызовов. Два раскрытия `SWITCH_VALUE` связаны
с разными определениями. Проверка наличия `DEBUG` не является раскрытием значения.
Неактивные ветви сохраняют текст, не создавая AST и не читая отсутствующий файл.
Обе конфигурации частичного выражения проходят API и строгие нативные прогоны
исходных/сгенерированных файлов. Правка разных вариантов общего текста остаётся
отдельной задачей #20.
