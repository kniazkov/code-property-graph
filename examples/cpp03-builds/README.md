# Select SERVER and CLIENT builds / Выбор сборок

The fixture contains four compilation commands: SERVER and CLIENT variants of
both main.cpp and engine.cpp. Import once, inspect all variants, then select
explicit named compositions through the API. Run from the repository root:

```sh
mvn verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved \
  --compile-commands src/test/resources/cpp03/build-variants/compile_commands.json \
  --json target/all-variants.json
mkdir -p target/build-selection-classes
javac -cp target/cpg-0.0.1-SNAPSHOT.jar -d target/build-selection-classes examples/cpp03-builds/SelectBuilds.java
java -cp target/cpg-0.0.1-SNAPSHOT.jar:target/build-selection-classes SelectBuilds \
  src/test/resources/cpp03/build-variants/compile_commands.json target/selected-builds
for variant in server client; do
  macro=$(printf '%s' "$variant" | tr '[:lower:]' '[:upper:]')
  for compiler in g++ clang++; do
    "$compiler" -std=c++03 -pedantic-errors "-D$macro" \
      target/selected-builds/$variant/sources/main.cpp \
      target/selected-builds/$variant/sources/engine.cpp -o target/selected-builds/$variant/program
    target/selected-builds/$variant/program # exit 0, no output
  done
done
```

Use fresh output paths on repeated runs. `Project.variants(path)` returns two
units per physical file. Each `SourceFile.variantId()` identifies the normalized
configuration, not merely the path. In the all-variant JSON, each main run()
call has `resolution: "ambiguous"` and two candidateIds; Active::main in each
engine resolves to its own active class. Selected server/client JSON has a
build.composition and one resolved target per call. CFG/DFG stay within their
own syntax version. The original all-variant snapshot is unchanged.

The demo constructs `new Build.Composition(name, ids)` and calls
`all.selectBuild(composition)`. One composition cannot select incompatible
versions of one physical file. Unknown IDs are rejected. Competing free-function
definitions in one known build are `odr_conflict` with ERROR diagnostics; that
is distinct from alternatives under unknown composition. Full C++ ODR rules for
classes, templates and linkage are not claimed here.

В JSON без выбранного состава вызовы run() имеют две возможные цели, а локальные
Active::main ведут к объявлению своей версии. API выбирает server/client по ID
конфигураций, не по одному имени файла. Выбранный снимок содержит состав сборки,
однозначные цели и изолированные CFG/DFG. Исходный снимок остаётся прежним.
Одновременный выбор разных конфигураций одного файла и неизвестные ID дают
ошибку; конфликт определений свободной функции в известной сборке отличается
от неоднозначности неизвестного состава. Команды выше выполняются также в CI.
