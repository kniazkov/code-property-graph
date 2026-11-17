# #93, step 3: printed C++03 is_open profile

## Русский

Выбран профиль исходной печатной редакции ISO/IEC 14882:2003:

| Класс | Сигнатура |
| --- | --- |
| basic_ifstream | bool is_open() |
| basic_ofstream | bool is_open() |
| basic_fstream | bool is_open() |
| basic_filebuf | bool is_open() const |

Правило применяется к char/wchar_t и обоим size-type профилям. Основание —
сохранённая сверка synopsis §27.8.1.5, §27.8.1.8, §27.8.1.11 и #93.
Исходный PDF повторно не проверялся. Дополнительного переключателя профиля нет.
Ранее допускавшиеся const-вызовы трёх файловых потоков теперь диагностируются
как no-viable-overload и блокируют генерацию. const-filebuf остаётся допустимым.

StreamIsOpenTest независимо проверяет ровно один метод с точным bool return,
нулём параметров, public access, cv и отсутствием тела. API-проверки охватывают
четыре семейства, обе ширины символа, оба профиля, mutable/const receivers,
копию и повторное разрешение графа, удаление const из параметра с устранением
диагностики, JSON, генерацию/повторный разбор допустимого
кода и блокировку ошибочного.

StreamIsOpenIT проверяет шесть отрицательных CLI-сценариев и исполняет GCC/Clang
исходный и сгенерированный положительный пример. Для отрицательных const-вызовов
native-компиляция является наблюдением: её статус и вывод сохраняются, но
не используются как критерий правильности выбранного печатного профиля.
Современная библиотека может принимать эти вызовы даже с -std=c++03; неизвестной
DR это отличие не приписывается. Положительный native-контроль обязателен.
Логи/статусы: target/stream-is-open-oracle. Ручные исходники:
src/test/resources/stream-is-open. Полный runtime файловых потоков не доказан.

## English

The fixed catalogue profile follows the printed 2003 signatures: three file
streams have non-const is_open; basic_filebuf retains const. Tests cover exact
method shape, narrow/wide and both size-type policies, receiver qualification,
copied/recomputed graphs, JSON and generation boundaries. This intentionally
rejects const file-stream calls previously accepted by the catalogue.
Modern native-library acceptance is recorded independently rather than treated
as the original-edition oracle or attributed to an unverified defect report.
The mutable-stream/const-filebuf original and generated controls must compile
and execute successfully. The synopsis records remain partial; stream runtime
and the remaining streamsize/seekdir work in #93 are not closed.

## Reproduction

```sh
mvn -DargLine=-Xmx768m -Dtest=StreamIsOpenTest,StreamLibraryResolutionTest,StandardLibraryCatalogueTest -Dit.test=StreamIsOpenIT verify
java -cp target/cpg-0.0.1-SNAPSHOT.jar docs/cpp03-paragraphs/probes/StreamInterfaceProbe.java
```

Local runs select GCC only because Clang is unavailable. Full GitHub CI and
both compiler checks remain mandatory for final acceptance.

## Local evidence, 2026-11-17

Base: `1a514be781c5cece66702b93879e157d17f2ad65`.
StandardLibraryStreams.java Git blob: `8c6bc6cff6476966e3774a9dc272c506c2df6719`.
Fresh JAR SHA-256: `6f8117a844b899e5d484754e87e458ce8a1160332399bc6c1297600a36df0a9b`.

Both local runs completed with BUILD SUCCESS:

```sh
mvn -DargLine=-Xmx768m -Dtest=StreamIsOpenTest test
mvn -DargLine=-Xmx768m -Dtest=StreamIsOpenTest#removingConstRecomputesTheAccessBoundary,StreamLibraryResolutionTest,StandardLibraryCatalogueTest -Dit.test=StreamIsOpenIT#gccOriginalAndGenerated verify
```

The first run covered the two inventory/call-matrix methods. After adding the
edit regression, the second run covered that method and 23 existing unit tests,
plus one GCC E2E (six CLI rejections and two successful native executions).
All six host const probes compiled successfully (status 0); this is the
explicitly recorded modern host-library difference, not the CPG expectation.
Full CI/Clang execution is still required. Fresh StreamInterfaceProbe output:

```text
iosfwd-istreambuf-iterator: true
iosfwd-ostreambuf-iterator: true
ios-base-copy-declaration: true
basic-ios-copy-declaration: true
file-is-open-const-declarations: 1
copy-ios-base: [inaccessible-overload]
copy-ios: [inaccessible-overload]
```
