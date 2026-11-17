# #93: streamsize, seekdir and integrated interface verification

## Русский

Завершена выбранная интерфейсная часть #93: после iosfwd, private copy/assignment
и печатного cv-профиля is_open добавлены свойства streamsize/seekdir. Основание —
сохранённая сверка §27.4.1/2 и §27.4.2.1.5; новый просмотр исходного PDF не заявляется.

| Тип | Категория | Известные свойства | Неизвестно |
| --- | --- | --- | --- |
| streamsize | SIGNED_INTEGER | signed=true, fundamental=true, arithmetic | конкретный fundamental type, размер, выравнивание, диапазон, rank |
| ios_base::seekdir | ENUMERATION | fundamental=false, enum | underlying type, signedness, размер, выравнивание, значения beg/cur/end, promotion rank |

OpaqueLibraryType публикует `isArithmetic()`, `isEnumeration()`, optional
`fundamental()`/`signed()`. JSON сохраняет category и только известные свойства.
Пустое значение означает неизвестность, а не false. Новые категории проходят
каталог, парсер, копирование, нормализацию, свёртку и экспорт. Версия JSON остаётся 1.

Число не преобразуется неявно в seekdir; seekdir не преобразуется неявно
в другое перечисление. Обычный enum допустим как streamsize. Enum→арифметический тип допустим,
но rank неизвестен; наличие гарантированного преобразования не оправдывает
угадывание лучшей перегрузки. Тесты сохраняют unresolved-overload при выборе
между int/long для streamsize. sizeof, alignment, integerRange и promoted type
остаются неизвестными; профиль size_t не превращается в ABI streamsize.

API-проверки покрывают оба size-type профиля, копии/пересчёт, GREEN/FOLDED,
JSON и генерацию/повторный разбор. E2E проверяет narrow/wide потоковые вызовы,
отрицательный numeric→seekdir, компиляцию и исполнение original/generated.
Отдельный native-only oracle использует numeric_limits и compiler __is_enum
для проверки категорий без задания размеров. Он не передаётся CPG.

Все четыре части #93 включены в общую промежуточную проверку до PR. Полное
соответствие synopsis/главы 27 и runtime потоков этим не объявляются: остаются
отдельные #103/#48 и поабзацные partial-обязательства. Объём #93 — исправление
конкретных интерфейсных пробелов; после зелёного CI его можно принять целиком.

## English

The opaque model now records a signed fundamental integer for streamsize and
an enumeration for seekdir, with unknown representation, rank and values.
Optional properties distinguish unknown from false. Directional conversions
reject numeric-to-enum, allow guaranteed enum-to-arithmetic conversion without
inventing promotion rank, and retain unresolved overloads where the underlying
integer identity matters. Copying, common passes, JSON v1 and source generation
preserve the contract. Native property probes do not prescribe a host ABI.

The integrated pre-PR matrix covers all four issue93 interface packages.
The is_open test retains all 32 receiver/profile cases but batches them into
positive/negative translation units per profile. Every call is still inspected;
copy/recompute, JSON and generation checks remain, without repeatedly resolving
the entire catalogue for each isolated call.
It is partial validation, not a replacement for full CI or chapter-27 runtime
conformance. Broader behavior and remaining paragraph obligations stay open.

## Intermediate checks / промежуточные проверки

Execute from an isolated checkout on a cloud runner or Foundry 19 with Java 21,
Maven and the selected native compilers. No GitHub workflow is triggered by:

```sh
python3 scripts/check-stream-interfaces.py quick
python3 scripts/check-stream-interfaces.py matrix
```

`quick` checks new properties plus overloads and catalogue inventory. `matrix`
checks all issue93 unit suites and original/generated compiler tests with
GCC and Clang. `--compiler gcc` explicitly narrows native coverage. For a Maven
wrapper or environment-specific launcher use `--maven-command 'COMMAND ARGS'`;
it is parsed into argv, never executed through a shell. `--dry-run` prints argv.
Logs and result metadata are written under `target/stream-checks/<stage>`.
The source hash covers tracked and non-ignored untracked files at run start;
logs are excluded by gitignore. Commit/diff/source hashes identify the checked
snapshot, but a dirty run is not evidence for subsequent edits.

In this chat Foundry 19 was not exposed as a connected execution surface.
Intermediate verification used the existing cloud execution workspace. GCC
was available; Clang execution remains a mandatory part of the unchanged full
GitHub CI. CI is requested only once the combined package is ready, and its
completion is left to the user.

## Recorded result / зафиксированный результат — 2026-11-17

Base revision: `57015a3c1ff8062325d76faaf496d75df18c00a1`.
The final intermediate matrix completed successfully: 72 unit tests and four
GCC integration tests, zero failures/errors/skips, 331.64 seconds. The source
snapshot hash was independently recomputed after restoring the interrupted
session and matched `425c2044f36d9bad89cb44a7541315e363ce10822a0cef9da336736a5e76a5d6`.
This hash precedes this result-only documentation addition; implementation and
tests have not changed since the successful run.

Command (the temporary launcher supplies Java 21 and Maven proxy settings):

```sh
python3 scripts/check-stream-interfaces.py matrix --compiler gcc --maven-command 'python /tmp/runmvn.py'
```

JAR SHA-256: `2f2be4e9e8392bf058c0233854b008f1a8408ec6c2b71ea9227a8402843aa156`.
The fresh-JAR probe was executed with:

```sh
java -cp target/cpg-0.0.1-SNAPSHOT.jar docs/cpp03-paragraphs/probes/StreamInterfaceProbe.java
```

It reports both iosfwd iterator declarations, both private copy declarations,
exactly one const is_open declaration, SIGNED_INTEGER/fundamental/signed for
streamsize, ENUMERATION/non-fundamental/unknown signedness for seekdir, unknown
sizes for both, and inaccessible-overload for both copy probes.
Portable paths, paragraph ledger (5250 records) and git diff whitespace checks
passed. These checks do not establish full standard conformance. Full Linux
verify, Windows unit tests and Clang remain for the final PR's GitHub CI.
