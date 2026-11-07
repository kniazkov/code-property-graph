# C++03 #92: library macros, stage one / макросы, первый пакет

## Inventory and source basis

[The mandatory macro inventory](cpp03-library-macros.json) contains 102
header/name obligations, 95 distinct macro names, and all 18 C++/old-C header
pairs. It is separate from the paragraph-source census. Each entry identifies
C++03 ledger coordinates, the incorporated C90/Amendment 1 clause, the kind of
macro, value/ABI boundary, implementation state, and remaining #92 work.

The existing source-reviewed ISO 2003 ledger and its hashes are retained. The
private PDF is absent from this checkout; no fresh full-source examination is
claimed. Supplemental historical evidence is limited to indexed primary C90
excerpts and the publisher's Amendment 1 preview, as recorded in the inventory.
In particular EILSEQ remains required via Amendment 1 despite its absence from
C++03's cerrno table; WEOF belongs to both cwchar and cwctype. Neither is supplied
by this first stage. C99/C++11 additions and C-style alternative-token macros
are excluded. NDEBUG is user input, not a header-provided macro.

C90 explicitly guarantees FLT_RADIX for preprocessing conditionals. The exact
interaction between its historical permission for other nonconstant float.h
values and C++03 library wording is not newly adjudicated here. This concrete
profile model deliberately supplies constants, which does not claim every
C++03 implementation must use identical expressions.

## Implemented subset and target choices

- NULL in cstddef, cstdio, cstdlib, cstring, ctime, clocale, cwchar and each .h alias.
- All 16 climits macros, including explicit MB_LEN_MAX.
- All 29 cfloat macros with explicit radix-two formats and rounding policy.
- WCHAR_MIN and WCHAR_MAX with wchar_t integral promotion, not just the rank of
  its underlying storage type.

This supplies 54 header/name obligations (48 distinct names). The 48 remaining
obligations (47 names) stay open under [#92](https://github.com/kniazkov/code-property-graph/issues/92),
including assert/NDEBUG, errno, streams, runtime constants, offsetof, setjmp and
va_start/va_arg/va_end. This PR must not close #92 or general acceptance #48.
No fabricated function declaration, unresolved placeholder or compiler-host
header counts as an implemented macro.

`PlatformProfile` serializes floatingFormats, floatingRounds, nullPointerConstant
and maxMultibyteLength, incorporating them into stable identity and copies. The
default model chooses binary32/binary64/binary128, nearest rounding, NULL=0 and
MB_LEN_MAX=16. These are documented target choices, independent of Java or the
analysis machine. Known formats must fit storage and meet C90 minimum precision;
binary32 is therefore rejected for double/long double. A 128-bit storage slot
can instead select binary80, proving that storage does not determine precision.

Existing complete profiles remain readable: omitted new format choices under
inherits=none remain unknown, with rounding -1, NULL=0 and MB_LEN_MAX=1. A built-in
cfloat include with unknown required format produces a contextual preprocessor
error. No floating format is guessed. These facts do not implement general
floating arithmetic, the runtime rounding environment or arbitrary target ABI.

## Preprocessing and provenance

Definitions are installed at the include directive, per translation unit and
selected profile. Explicit physical headers found through configured include
paths take precedence. Repeated old/new header spelling restores an undefined
macro. The macro namespace remains separate from std/global declaration lookup.

Definitions and expansions retain library origin, synthetic definition range,
profile-bearing source key and owned library-macro external-input text. Synthetic
definition buffers do not become physical source files. Copying and editing keep
owned provenance and preserve the original include directives without exporting
fake system headers. API/CLI equality also checks deterministic external-input
ordering.

## Verification

Production revision: `cf92c48` (the PR records the corresponding remote head
and source tree). Fresh executable JAR SHA-256:
`06357bc884ca08ddf484f1cb42c74df1df377a877983f09d08d6cd4b6520c140`.

The final focused Maven run on 2026-11-07 passed **86 unit tests and two integration
tests**, with zero failures, errors or skips:

```sh
mvn -B -DargLine=-Xmx768m \
  -Dtest=JsonExportTest,StandardLibraryMacrosTest,PlatformProfileTest,CCompatibilityFamiliesTest,CppPreprocessorTest,MacroSourceTest,ProfileTypeSemanticsTest \
  -Dit.test=LibraryMacrosIT verify
```

The local toolchain uses OpenJDK 21, Maven 3.9.11, GCC 14.2 and Clang 19.1.7;
offline Maven mode uses already cached dependencies. The targeted evidence covers:

- positive and negative integer #if expressions, ULONG bounds and promoted types;
- both header spellings, all seven NULL providers, before-include visibility,
  repeated inclusion and isolated preprocessing units;
- supplied-header precedence without hidden host headers;
- same-size binary80/binary128 distinction, all macro-family definition counts,
  unknown-format errors, and rejection of later/ABI macro inventions;
- profile identity and serialization, API/packaged-CLI JSON and generated text,
  copied snapshots, actual initializer replacement and regenerated/reparsed C++;
- error JSON and blocked generation for an unknown required floating format;
- original, generated and edited programs compiled and executed with both
  compilers using `-std=c++03 -pedantic-errors`.

The native program checks portable predicates and source preservation. It does
not assert that the compiler's ABI matches arbitrary long32 or binary128 model
values. Java/CPG checks establish the explicit-profile values separately. Editing
one expanded initializer does not replace all header macros with host values.

Independent review reproduced and corrected two boundary errors: wchar_t backed
by 32-bit long must still promote to int, and double/long double cannot choose
binary32 precision. Regression tests cover signed/unsigned wchar backing and
invalid format selections. No unrelated language-limit claim is made.

The full unit and integration passes found two reviewed JSON fixtures still
carrying the old profile identity: return-zero.json and return-zero-cli.json.
Each structural comparison found exactly 37 scalar differences: 36 occurrences
of the profile hash and one stable-identity string gaining the four new settings.
Only those two explicit text transformations were applied to each fixture;
syntax, node IDs, ranges, pass results and predefined values did not change.
All 227 tracked test resources were checked for remaining stale profile snapshots.
The four packaged-CLI tests then passed against the corrected source fixture using
the standalone JUnit launcher; the final complete Maven run remains a merge gate.
Exact macro-name sets and every supplied inventory/header spelling are also
cross-checked against the provider, not just counted.

The inventory has seven mutation tests checking omissions, duplicate obligations,
invalid source locators, unsupported evidence claims and missing follow-up links:

```sh
python3 scripts/test-cpp03-library-macros.py
python3 scripts/check-cpp03-library-macros.py
```

Full `mvn -B verify`, corpus completeness, catalogue native probes, paragraph
validation, strict JavaDoc and required CI on the exact published head are separate
merge gates. Focused results alone do not satisfy them. Their final logs are in
the PR's Checks view. No source-review hash is changed by this implementation.

## Кратко по-русски

Первый пакет #92 реализует 54 из 102 обязательных пар «заголовок/макрос»:
NULL, целочисленные и вещественные пределы, WCHAR_MIN/MAX. Значения следуют
явному профилю цели, а не JVM или ABI машины. Сохраняются происхождение,
изоляция единиц трансляции, приоритет переданных заголовков, копирование и
редактирование графа. Целевые 86 модульных и 2 интеграционных теста прошли;
полный Maven и CI конкретной опубликованной версии остаются отдельными воротами
слияния. Нативные запуски доказывают переносимые свойства и сохранение исходника,
а не совпадение всех чисел произвольного профиля с ABI компилятора.

Остальные макросы, включая assert/NDEBUG, errno и ABI-зависимые операции,
остаются в открытой #92. Следующая задача очереди не начинается до её приёмки.
Полная приёмка C++03 #48 не закрывается.
