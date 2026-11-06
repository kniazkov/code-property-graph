# Public custom passes / Пользовательские проходы

This is an external Java client, not code inside the CPG library. It uses only the
packaged JAR and public `com.kniazkov.cpg.passes` API. JDK 21, Maven, GCC and Clang
are required. Run these commands from the repository root:

```sh
mvn verify
mkdir -p target/custom-pass-classes
javac -Xlint:all -Werror -cp target/cpg-0.0.1-SNAPSHOT.jar \
  -d target/custom-pass-classes examples/cpp03-custom-passes/RunPasses.java
java -cp target/cpg-0.0.1-SNAPSHOT.jar:target/custom-pass-classes RunPasses \
  src/test/resources/cpp03/custom-passes/sources target/custom-pass-output
for compiler in g++ clang++; do
  for version in original transformed; do
    "$compiler" -std=c++03 -pedantic-errors \
      "target/custom-pass-output/$version/main.cpp" -o "target/custom-pass-$version"
    "target/custom-pass-$version" # no output, exit status 0
  done
done
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved --cpp-standard c++03 \
  --input target/custom-pass-output/transformed --json target/custom-pass-reparsed.json
```

Choose a fresh output directory on repeated runs. On Windows use `;` instead of
`:` as the Java classpath separator. `mvn verify` also compiles this exact example
with `-Xlint:all -Werror` against the packaged JAR in a separate process, runs it,
checks its JSON using an ordinary parser, and builds/runs every source version
with both native compilers. Run only this corpus scenario with:

```sh
mvn verify -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=custom-passes
```

## What the example demonstrates

- `AnalysisPass` publishes an immutable `IntRange(1, 10)` on each function through
  `AnalysisResults.put`. It emits no warnings. A successful pass history record,
  rather than diagnostics, proves that it ran.
- `ResultKey<IntRange>` includes producer, name and Java type. The dependent pass
  declares `requires()` and reads `Optional<IntRange>` through
  `PassContext.resultOf`, without casting. The runner prepares dependencies before
  the consumer. A missing key returns `Optional.empty()`.
- Another producer publishes its own `range` as `IntRange(20, 30)` without
  overwriting the first. String, Boolean, Integer and Double results use built-in
  encoders. `ResultTypes.defaults().with(IntRange.class, ...)` registers a custom
  immutable encoder, producing a JSON object with numeric `min` and `max` fields.
- A successful function-scoped rerun replaces the selected producer's **complete**
  result set in that scope. It publishes a new label but omits `range`, so the old
  range disappears. Other functions, other producers and the ancestor snapshot
  retain their own results and freshness. A rerun on a branch with an already-stale
  result does not falsely refresh the unselected function.
- A failing analysis stages both a value and warning, then throws. A failing
  transformation queues an edit, then throws. Neither changes the input snapshot
  or records successful execution.
- `TransformationPass` receives one `ProjectEditor` owned by the runner. It queues
  replacement of `helper`'s `1 + 2` expression with constant `3`. The runner builds
  the batch after the callback returns. The original addition remains in the
  ancestor; the new literal has provenance pointing to it with the custom pass ID.
  Analysis values affected by that edit become stale and are not returned as
  current by `resultOf`.

The emitted files are `original.json`, `analyzed.json`, `rerun.json`,
`transformed.json`, `stale-rerun.json`, a byte-identical repeated export, a check summary, and
`original/` and `transformed/` source trees. JSON `analysisResults` separates
producer/name/type, node ID, status and encoded value from diagnostics and pass
history. Stale payloads remain inspectable with their explicit status.

This example deliberately uses a Java client to execute extensions. The ordinary
CLI can reparse its generated C++03 sources but does not load custom Java classes
or reconstruct analysis products from source text. Register custom passes and
encoders explicitly in the application's `PassRunner`.

## По-русски

Пример компилируется отдельно от библиотеки и использует только публичный JAR.
Проход анализа возвращает типизированный `IntRange(1, 10)` без предупреждений.
Зависимый проход объявляет `requires()` и получает значение без приведения типа.
Одинаковое имя `range` у другого производителя не вызывает конфликта. Свой
кодировщик сохраняет диапазон в JSON как объект с числовыми полями `min` и `max`.

Повторный запуск для одной функции полностью заменяет её результаты выбранного
производителя: пропущенный диапазон исчезает, другие производители и функции
сохраняются. Исключение из анализа или преобразования ничего не публикует.
Преобразование через один `ProjectEditor` заменяет `1 + 2` на `3`, сохраняя
исходный снимок и связь происхождения. Результаты анализа изменённой функции
помечаются устаревшими. Исходная и преобразованная программы проверяются GCC и
Clang в строгом режиме C++03; обе завершаются с кодом 0 без вывода.
