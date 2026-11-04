# Compilation database acceptance / Проверка базы компиляции

This directory contains two ordinary C++03 source files and a mixed-standard
`compile_commands.json`. `a.cpp` selects C++03 through `arguments`; `b.cpp`
selects C++11 through safely tokenized `command`. Include search paths contain
spaces and occur in different orders; `-D`/`-U` operations must preserve order.
The source program exits 0 without output.

The default import deliberately rejects `b.cpp` and `c++11`. An explicit C++03
override succeeds, because both sources use only C++03 syntax. Replacing `b.cpp`
with a lambda must still fail under that override. The definitions here test
configuration preservation, not macro expansion (implemented in a later task).

Из корня проекта:

```sh
mvn -Dit.test=CompilationDatabaseIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=compilation-database verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --compile-commands src/test/resources/cpp03/compilation-database/compile_commands.json \
  --cpp-standard c++03 --json target/database.json --out target/database-sources
```

Omit `--cpp-standard c++03` to reproduce the expected unsupported-standard error.
The test copies this entire directory beneath a path containing spaces and runs
the CLI from another working directory. It compares the full graph against an
explicit `Build`, including types, references, ranges and generated filenames.
Both original and generated positive programs compile with strict GCC and Clang.

База переносима: `directory` разрешается относительно файла базы, а `file` и
пути поиска — относительно `directory`. Отдельный тест сверяет импорт с явным
`Build`, порядок путей и операций макросов, типы и ссылки. Полный граф CLI
сравнивается с API. Без переопределения ожидается ошибка C++11, с C++03 — успех;
лямбда по-прежнему запрещена. Разбор команд не запускает оболочку или компилятор.
