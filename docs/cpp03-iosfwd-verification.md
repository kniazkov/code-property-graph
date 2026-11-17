# #93, package 1: iosfwd / предварительные объявления

## Русский

В `iosfwd` добавлены предварительные объявления `istreambuf_iterator`,
`ostreambuf_iterator` с параметром Traits по умолчанию и явных специализаций
`char_traits<char>`/`char_traits<wchar_t>`. Параметр Traits по умолчанию в определениях iterator повторно не объявляется.
Это закрывает конкретный пробел synopsis §27.2 (`cpp03:27.2:u1`), но не весь контракт заголовка или задачу #93.

Независимые ожидания в StandardLibraryCatalogueTest проверяют параметры,
специализации и отсутствие определений/членов. IosfwdDeclarationTest проверяет
оба size-type профиля, narrow/wide, ссылки из пользовательского кода,
недоступность методов до подключения определяющих заголовков, доступность после,
повторное разрешение, редактируемую копию графа, JSON и генерацию с повторным
разбором. Исправлено объединение кандидатов в NameResolver: предварительное объявление специализации
не вытесняет первичный шаблон того же имени.
Проверка на пользовательском шаблоне защищает этот общий путь.

E2E проверяет CLI/JSON и компилирует/исполняет исходный и сгенерированный код
с GCC и Clang (`-std=c++03 -pedantic-errors`), отдельно для include-only и
подключения iterator/string. Пример: `src/test/resources/iosfwd-declarations/main.cpp`.
Команды и вывод компиляторов сохраняются в `target/iosfwd-declarations-oracle`.

Следующие пакеты #93: private copy/assignment потоковых баз, точный cv-профиль
трёх файловых is_open, категории streamsize/seekdir. Runtime потоков относится
к #103. Статус записи §27.2 остаётся partial; исходный аудит не переписывается
как доказательство новой сборки. Исходный PDF повторно не проверялся: locator
и формулировка взяты из уже сохранённой поабзацной сверки и #93.

## English

This package supplies the four missing forward declarations, without importing
iterator/string definitions through iosfwd. It also preserves primary templates
when same-scope specialization forward declarations are collapsed during lookup.
Independent inventory and API tests cover both size-type profiles and both
character widths, header boundaries, graph copies, recomputation, JSON and
source regeneration. CLI/native tests cover original and generated programs.
This is partial evidence for 27.2, not stream-runtime or complete C++03 conformance.
The remaining copy-access, is_open profile and opaque-type categories in #93
remain open; no unrelated ABI, runtime behavior or DR is asserted.

## Reproduction

```sh
mvn -DargLine=-Xmx768m -Dtest=IosfwdDeclarationTest,StandardLibraryCatalogueTest,StreamLibraryResolutionTest,LibraryForwardClassResolutionTest -Dit.test=IosfwdDeclarationIT verify
java -cp target/cpg-0.0.1-SNAPSHOT.jar docs/cpp03-paragraphs/probes/StreamInterfaceProbe.java
```

Local execution selects the GCC method only because Clang is unavailable.
Both compilers and the complete test suite remain mandatory in GitHub CI.

## Local evidence, 2026-11-17

Base revision: `f44c61445d0424426da6753c4d17a5577eec36a3`.
Source changes are identified by the Git blob IDs below; the enclosing PR
identifies the final commit. This is a fresh local build, not the audit JAR.

- `NameResolver.java`: `cd15901dfac3a84875d643270d6045470364c0ba`
- `StandardLibraryCatalogue.java`: `73b7df6057d28ec932349aad2d9c1c9998071481`
- `StandardLibraryStreams.java`: `15b7271a1ad2ee8a29bc3368302e09582ab0b8b8`

JAR SHA-256: `ce400114ac7d028a0788a7a2eb4490cf9ad57e9a17fa3018725c73ba804097c9`.

Executed command (47 unit tests, 1 GCC E2E, BUILD SUCCESS):

```sh
mvn -DargLine=-Xmx768m -Dtest=IosfwdDeclarationTest,StandardLibraryCatalogueTest,ClassTemplateSemanticsTest,StreamLibraryResolutionTest,LibraryForwardClassResolutionTest -Dit.test=IosfwdDeclarationIT#gccOriginalAndGenerated verify
```

StreamInterfaceProbe rerun on that JAR:

```text
iosfwd-istreambuf-iterator: true
iosfwd-ostreambuf-iterator: true
ios-base-copy-declaration: false
basic-ios-copy-declaration: false
file-is-open-const-declarations: 4
copy-ios-base: [no-viable-overload]
copy-ios: [no-viable-overload]
```

The false copy-declaration checks and four const is_open declarations are
expected remaining work in #93, not completed obligations.
