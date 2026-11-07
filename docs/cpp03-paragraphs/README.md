# Paragraph evidence / Свидетельства по абзацам

## English

This directory records review of the privately supplied ISO/IEC 14882:2003 text.
It does not redistribute that PDF or its extracted normative text. The verified
source hash and pagination are recorded in `inventory.json`.

- `inventory.json` is a census of source locators, grouped by kind: numbered
  paragraphs (including original letter suffixes), unnumbered blocks, grammar
  productions, tables, footnotes and explicit numbering anomalies. It is not a
  count of implemented features or an atomized count of every requirement.
- `schema.json` defines each evidence record. Shards use JSON Lines, one record
  per locator. English rule summaries are authored paraphrases; Russian summaries
  are optional. Bilingual chapter and acceptance reports provide the reading guide.
- `source_reviewed` means the source unit was actually examined. It does not mean
  the implementation satisfies it. Unreviewed rows are unfinished audit work.
- `evidence_status` and `stage_status` distinguish verified, partial, gap and
  not-applicable evidence. A missing proof is not automatically an implementation
  rejection. The `gap` field must make the distinction explicit.
- `parser`, `model`, `resolution`, `analysis`, `generator` and `tests` cite actual
  repository paths, optionally with `#method` or `#Lstart-Lend`. A path alone is
  not a claim that every case in a paragraph passes.
- `validation=source_inspected` means the cited code/test assertions were read;
  `executed` is reserved for an actual applicable execution. Planned examples
  must be identified as planned, not passed regressions.
- Diagnostic classifications distinguish mandatory diagnostic, ill-formed no
  diagnostic required, undefined, unspecified and implementation-defined behavior.
  Chosen conservative diagnostics do not turn undefined behavior into a normative
  requirement to diagnose. Informative source material is distinguished explicitly.
- `edition_delta` records known historical/profile differences. Lack of a completed
  later-edition comparison must not be described as proof that no difference exists.

Run `python3 scripts/test-cpp03-paragraphs.py` and
`python3 scripts/check-cpp03-paragraphs.py` from the repository root. The checker
validates schema, unique IDs, source locators and file/method citations, then
reports source review and implementation evidence separately by locator kind.
`--require-source-complete` additionally rejects missing or unreviewed inventoried
locators. Even that option is not a standard-conformance test: manual inventory
quality, paragraph interpretation, examples and evidence still require review.

## Русский

Здесь фиксируется сверка с предоставленным пользователем ISO/IEC 14882:2003.
Сам PDF и извлечённый нормативный текст не распространяются. Проверенные хеш
источника и пагинация указаны в `inventory.json`.

- Реестр локаторов отдельно считает нумерованные абзацы (включая буквенные суффиксы
  оригинала), ненумерованные блоки, грамматические продукции, таблицы, сноски и
  аномалии нумерации. Это не число реализованных возможностей или отдельных норм.
- `schema.json` задаёт формат JSON Lines. Краткие английские пересказы написаны
  своими словами; русский пересказ необязателен. Общие отчёты доступны на RU/EN.
- `source_reviewed` подтверждает чтение исходного фрагмента, а не поддержку его
  требований реализацией. Непрочитанные записи означают незавершённую работу.
- `evidence_status` и `stage_status` различают подтверждение, частичное подтверждение,
  пробел и неприменимость. Отсутствие доказательства не равно отказу реализации;
  поле `gap` должно объяснять разницу.
- Ссылки стадий и тестов указывают реальные файлы, методы или строки. Наличие
  ссылки на файл не означает успешной проверки всех случаев нормативного абзаца.
- `source_inspected` означает чтение кода/утверждений теста; `executed` требует
  фактического прогона. Планируемый пример нельзя выдавать за выполненную регрессию.
- Обязательная диагностика, IFNDR, UB, неуточнённое и зависящее от реализации
  поведение различаются. Полезная выбранная диагностика UB не делает её
  обязательной по стандарту. Информативные фрагменты отмечаются отдельно.
- Отличия редакций и осознанные исключения профиля указываются в `edition_delta`.
  Незавершённое сравнение с поправками не является доказательством их отсутствия.

Проверки запускаются командами `python3 scripts/test-cpp03-paragraphs.py` и
`python3 scripts/check-cpp03-paragraphs.py`. Проверяется структура, уникальность ID,
локаторы и существование ссылок; чтение источника и доказательства реализации
подсчитываются раздельно. Флаг `--require-source-complete` дополнительно запрещает
пропущенные/непрочитанные локаторы реестра, но не доказывает соответствие стандарту:
качество реестра, трактовки правил и достаточность тестов требуют ручной сверки.

## Reproducing locators / Воспроизведение локаторов

With a separately supplied copy of the exact verified PDF and Poppler installed:

```sh
python3 scripts/inventory-cpp03-source.py --pdf /path/to/original.pdf --output /tmp/cpp03-inventory.json
python3 scripts/test-inventory-cpp03-source.py
```

The extractor refuses a different file hash/size, processes the layout in memory,
and emits only locator metadata. Compare the parsed JSON with `inventory.json`.
Its regression tests use synthetic input and require no private PDF; CI runs them.
The extraction corrections were manually reviewed. Successful reproduction does
not prove that the census atomizes every normative obligation.

Для повторения нужен отдельно предоставленный экземпляр именно проверенного PDF
и установленный Poppler. Команды выше проверяют хеш и размер, обрабатывают текст
в памяти и сохраняют только метаданные локаторов. Сравнивайте разобранный JSON с
`inventory.json`. Синтетические регрессии не требуют частного PDF и запускаются
в CI. Исправления извлечения проверены вручную; воспроизводимость реестра не
доказывает выделение каждой отдельной нормативной обязанности.

## Mandatory library macro inventory (#92)

[`../cpp03-library-macros.json`](../cpp03-library-macros.json) is a separate
header/name obligation inventory (102 pairs, 95 distinct names), not a change to
the source-coordinate census in this directory. It preserves the historical C90
and Amendment 1 boundary, links existing C++03 locators and records open work per
entry. Run `python3 scripts/test-cpp03-library-macros.py` and
`python3 scripts/check-cpp03-library-macros.py` from the repository root. These
checks validate inventory consistency and evidence paths, not all macro semantics.

Отдельный инвентарь обязательных макросов не меняет знаменатель координат
источника. Состояния реализации и открытые обязательства учитываются отдельно.
