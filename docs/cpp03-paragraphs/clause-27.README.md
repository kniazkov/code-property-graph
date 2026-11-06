# Clause 27 source review / Сверка раздела 27

## English

`clause-27.jsonl` reviews 640 source locators from the privately supplied original
ISO/IEC 14882:2003: 485 numbered paragraphs, 90 unnumbered blocks, 13 tables and
52 footnotes. Each has an individually authored summary. The PDF, extracted
paragraph bodies and private construction script are not redistributed.

The evidence split is 443 gaps, 110 partial records and 87 informative records
without an independent executable obligation. There are no `verified` records.
Reading the source is not proof that the project implements the requirement.
The 52 footnotes use canonical global IDs and metadata from `inventory.json`.
Footnote 272 has a reliable number/page but no uniquely located call site.
The empty, out-of-order paragraph 27.7.1.3/9 is retained explicitly.

### What was inspected

- `StandardLibraryStreams` provides declarations with no behavior bodies.
- `StandardLibraryCatalogue`, `LibraryDeclarations` and `NameResolver` supply
  header availability, parsed candidates and resolution.
- `OpaqueLibraryType` retains abstract type categories, not an ABI or values.
- `LibraryCallEffects` conservatively models possible aliasing/writes, not
  exact I/O outcomes. `ConsoleNormalizer` records newline/flush provenance for
  its recognized console form, not a complete stream runtime.
- The cited catalogue, stream resolution, constructor lifetime, opaque effect,
  console and end-to-end test sources were read. The existing C++ fixture has
  useful narrow/wide extraction, state reset and formatting assertions, but
  these tests were not executed as part of this source review. Interface
  presence and test names are never treated as proof of all runtime cases.

The remaining contracts include stream initialization and ties; flag/state and
exception sequencing; callback storage/order; get/put pointer invariants;
sentry behavior; locale formatting/conversion; exact transfer counts; string
buffer ownership and seeking; file-mode mapping, conversion and closing.
The rows carry planned probes and explicit limits for those contracts.

### Concrete original-edition differences

1. The `iosfwd` fragment lacks the two buffer-iterator forward declarations and
   the explicit narrow/wide `char_traits` specialization declarations in the
   printed synopsis (27.2, unnumbered synopsis). A test comparing the namespace
   index with the same catalogue cannot detect this ISO-inventory omission.
2. The printed private copy declarations for `ios_base` and `basic_ios` are
   absent from the catalogue synopses (27.4.2 and 27.4.4). The probe below still
   rejects the two attempted copies with `no-viable-overload`; the omission is
   not evidence that those illegal programs are accepted.
3. The printed `ifstream`, `ofstream` and `fstream` synopses specify non-const
   `is_open()`. Their catalogue declarations are const. No particular later
   defect correction is asserted to explain the difference.
4. `streamsize` is an opaque INTEGER with no independent signedness property,
   so the signed-fundamental-type requirement is not established. `seekdir`
   likewise uses an unspecified alias rather than an enumeration category.
5. The original wording at 27.6.1.3/15 uses array-delegation arguments beneath
   the stream-buffer overload. The audit preserves this printed discrepancy
   instead of silently substituting a presumed later correction.

### Executed probe and limits

The read-only `probes/StreamInterfaceProbe.java` was compiled and run on
2026-11-06 using JDK 21 and the existing `target/cpg-0.0.1-SNAPSHOT.jar` with
SHA-256 `e5b3191500fb4d816e0060cfafe4bbddc401d75faa9a317c792b73612a75eefc`.
The inspected production stream catalogue last changed at commit
`3c4110ad352491c8eb41c3002729c01a24c286c3`.
This probe is not a fresh Maven build, full suite or generated-runtime test.
Six associated synopsis rows use `validation=executed` only for this narrow
probe; their evidence remains partial.

```sh
mkdir -p /tmp/cpp03-stream-probe
java -m jdk.compiler/com.sun.tools.javac.Main \
  -cp target/cpg-0.0.1-SNAPSHOT.jar -d /tmp/cpp03-stream-probe \
  docs/cpp03-paragraphs/probes/StreamInterfaceProbe.java
java -cp target/cpg-0.0.1-SNAPSHOT.jar:/tmp/cpp03-stream-probe StreamInterfaceProbe
```

Observed output:

```text
iosfwd-istreambuf-iterator: false
iosfwd-ostreambuf-iterator: false
ios-base-copy-declaration: false
basic-ios-copy-declaration: false
file-is-open-const-declarations: 4
copy-ios-base: [no-viable-overload]
copy-ios: [no-viable-overload]
```

The four const declarations are `filebuf` plus the three file-stream classes;
`filebuf::is_open()` is already const in the original source.

## Русский

Проверены все 640 локаторов раздела 27 предоставленного оригинала C++03:
485 нумерованных абзацев, 90 ненумерованных фрагментов, 13 таблиц и 52 сноски.
Для каждого написан отдельный пересказ. Исходный PDF и нормативный текст
не публикуются. Чтение источника не означает соответствия реализации.

Итог: 443 пробела доказательств, 110 частичных свидетельств, 87 информационных
записей без отдельного исполняемого требования. Полностью подтвержденных
записей нет. Каталог содержит объявления; связывание имен и консервативные
эффекты не доказывают поведение буферов, состояние потоков, исключения,
форматирование, локали, файловые операции и правила времени жизни.

Обнаружены отсутствующие предварительные объявления итераторов буфера и
специализаций traits в iosfwd, отсутствующие закрытые объявления копирования,
отличие const у is_open трех файловых потоков и недостаточная детализация
категорий streamsize/seekdir. Попытки копирования в отдельной пробе все же
отклоняются как no-viable-overload, поэтому отсутствие объявлений не выдается
за принятие некорректных программ. Исправления поздних редакций не предполагаются.

Отдельная проба выполнена на указанном готовом JAR. Остальные существующие
тесты и пример C++ прочитаны, но в рамках этой сверки не запускались. Шесть
строк отмечают только этот узкий выполненный эксперимент, а не полный прогон.
Сноска 272 сохраняет проверенный номер и страницу без выдуманного места ссылки;
пустой абзац 27.7.1.3/9 также сохранен.
