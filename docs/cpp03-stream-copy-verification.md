# #93, step 2: private stream-base copying

## Русский

Добавлены закрытые, без определений, copy constructor и operator= с аргументом
const-reference для ios_base/basic_ios. Присваивание возвращает reference на
соответствующий класс. Основание — сохранённая сверка synopsis §27.4.2 и §27.4.4
(cpp03:27.4.2:u1, cpp03:27.4.4:u1). Исходный PDF повторно не проверялся.

Раньше проверенные копирования уже отвергались как no-viable-overload; этот
пакет устраняет пробел объявлений и проверяет точную причину отказа, не заявляя
об исправлении установленного принятия неправильной программы.

StreamCopyAccessTest проверяет точные private-сигнатуры без тел, ios_base/ios/wios,
копирование, присваивание и явный operator=, оба size-type профиля, диагностический
JSON, копирование/повторное разрешение графа и блокировку генерации. Положительный
copyfmt проверяется после редактирования аргумента width и повторного разбора.
StreamCopyAccessIT проверяет шесть отрицательных CLI/native случаев и исполнение
исходного/сгенерированного положительного примера GCC/Clang в строгом C++03.
Исходники для ручной проверки: src/test/resources/stream-copy.
Артефакты компиляторов: target/stream-copy-oracle.

Открыты остальные пункты #93: cv-профиль is_open и категории streamsize/seekdir.
Runtime потоков и весь synopsis не доказаны; поабзацные статусы остаются partial.

## English

The catalogue now declares private undefined copy constructors and assignments
for ios_base/basic_ios. Tests cover exact access/signatures, narrow/wide aliases,
both size-type profiles, copied/recomputed graphs, diagnostic JSON and blocked
emission. A valid copyfmt control survives editing and regeneration. Native
oracles reject all six invalid programs and execute original/generated controls.
Previously observed copying already failed overload resolution; this is an
interface/access-contract correction, not evidence of a previously accepted
invalid copy. Remaining issue93 type/cv requirements and stream-runtime coverage
are not closed by this package.

## Reproduction

```sh
mvn -DargLine=-Xmx768m -Dtest=StreamCopyAccessTest,StreamLibraryResolutionTest,StandardLibraryCatalogueTest -Dit.test=StreamCopyAccessIT verify
java -cp target/cpg-0.0.1-SNAPSHOT.jar docs/cpp03-paragraphs/probes/StreamInterfaceProbe.java
```

Local verification selects GCC only; Clang is unavailable locally. The full
GitHub CI and both compilers remain mandatory for acceptance of the final PR.

## Local execution, 2026-11-17

Base: `759515efb5a8c8aeff982ae3fdd03ac7b4eee0fe`.
StandardLibraryStreams.java Git blob: `5f60a3edec612c601ce65d8f3dc684f3f7dbfa72`.
Fresh JAR SHA-256: `56e6980db85a0a888ef821ce9a60571814a45c01595f0e8878694a793aff55f2`.

The 25 behavior/catalogue/stream unit tests passed in the broader local run.
The remaining inventory assertion initially inspected the wrong access field
and then assumed prefix-const spelling. It now checks the typed reference and
qualifier nodes; its final rerun and the GCC E2E completed with BUILD SUCCESS:

```sh
mvn -DargLine=-Xmx768m -Dtest=StreamCopyAccessTest#catalogueContainsExactPrivateDeclarationsWithoutBodies -Dit.test=StreamCopyAccessIT#gccOriginalAndGenerated verify
```

E2E: six rejected CLI/native programs, two successful native executions
(original and generated copyfmt control). Full CI is still required.
Fresh StreamInterfaceProbe output:

```text
iosfwd-istreambuf-iterator: true
iosfwd-ostreambuf-iterator: true
ios-base-copy-declaration: true
basic-ios-copy-declaration: true
file-is-open-const-declarations: 4
copy-ios-base: [inaccessible-overload]
copy-ios: [inaccessible-overload]
```
