# C++03 #90: strstream verification / проверка

This report records the recovered implementation after a workspace rollback.
Earlier interrupted full-test results are not counted as successful checks.
Local recovered production and test revision: `bdf8f24b3856ae2bf24711e1e793959053201709`.

## Scope / границы

- Independent ISO 17.4.1.2 Tables 11/12 and D.5 Table 100 inventory: 51 core
  headers / 69 spellings, including `<strstream>`. All fragments parse without
  host headers on default and unsigned-int size-type profiles.
- Independent D.7 coordinate fixture: four classes, 39 callable declarations,
  four `strstream` typedefs, base classes, callbacks, defaults, access and
  explicit/virtual/const modifiers. This is declaration evidence, not a complete
  implementation of D.7 behavior.
- All constructor families and public members, protected hooks/access rejection,
  copied/re-resolved ownership, include-only laziness and demand removal after
  editing are checked. Concrete library bases are instantiated on demand.
- Returned receivers and used function/class-template bodies demand their
  inherited `basic_streambuf` methods. Tests check target ownership in the
  instantiated base, not merely target presence. Chained insertion/extraction
  retain all four operator targets. Unused method bodies do not demand bases.
- Same-domain opaque bitmasks keep their guaranteed result type without invented
  integer values. Equal guaranteed-viable opaque constructor conversions can tie
  while another known argument chooses the overload; unrelated opaque targets
  remain unresolved.
- `str`/`freeze` remain declaration-only calls with conservative receiver-write
  effects. Allocation, ownership, frozen state and lifetime are not modeled.

## Source decisions / решения по оригиналу

The four class synopses were independently checked against the original local
ISO/IEC 14882:2003 extraction before the rollback. The recovered coordinate
fixture has SHA-256
`babd32615c69c247705ee0be7eb7770491f5531a16c76e53153c0e7df7e1dcde`.
Only interface declarations and coordinates are included, not the private PDF or
full normative text.

D.7.1 synopsis prints `pcount()` while D.7.1.2 prints `pcount() const`; the detailed
signature is modeled. D.7.1.3 prints invalid `streambuf<char>*` for setbuf even
though streambuf is a non-template alias; the valid synopsis `streambuf*` is used.
The synopsis's mutable-char-pointer-plus-length istrstream overload is retained.

## Fresh checks / новые проверки

The recovered implementation passed 47 focused Maven tests, followed by an
extended Maven verify run of 87 unit tests and all three StrstreamLibraryIT
integration tests, with no failures, errors or skips (2026-11-07).
The latter run built the executable JAR before the API/CLI/native checks. Its
SHA-256 is `9fccbb837d33d4e1a5039129fabc7eab4b9abf6d18f2aa30b22d1759448a202d`.
Strict JavaDoc with `-Werror -private
-Xdoclint:all,-missing` passed. The paragraph checker covered all 5,250 recorded
source locators with zero errors; this is not a conformance proof.
Independent probes checked deeper returned-buffer chains and copied/re-resolved
operator targets. The safe runtime fixture compiled and ran with both strict
GCC and Clang.

```sh
mvn -B -Dtest=StandardLibraryCatalogueTest,StrstreamLibraryTest,StrstreamInheritanceTest,OverloadResolutionTest test
mvn -B -DargLine=-Xmx768m -Dtest=StandardLibraryCatalogueTest,StrstreamLibraryTest,StrstreamInheritanceTest,OverloadResolutionTest,StreamLibraryResolutionTest,StreamConstructorLifetimeTest,LibraryUnknownEffectsTest,LazyLibraryGraphTest -Dit.test=StrstreamLibraryIT verify
mvn -B verify
bash scripts/probe-cpp03-strstream.sh g++ clang++
python3 -B scripts/check-cpp03-paragraphs.py --require-source-complete
```

`StrstreamLibraryIT` contains API/CLI/JSON equivalence, invalid-call JSON/export
refusal, and original/generated native executions. Its lifecycle scenario uses
GREEN and FOLDED output (six compiler executions); the returned-buffer/template
scenario uses RESOLVED output (four). Dynamic buffers are inspected only while
alive and unfrozen before destruction; no manual deletion or post-destruction
access is used.

The separate complete Maven run and exact-head GitHub CI are required before
merge. Their final run links and results are recorded in the pull request.
The focused local results do not replace either gate.

## Remaining boundary / оставшаяся граница

Review reproduced a pre-existing eager diagnostic in an unused instantiated
class-template method containing `s.rdbuf()->sputc('a')`. That limitation existed
before the receiver-demand fix. The unused-body demand regression uses `sgetc()`
to separate laziness from this unsolved diagnostic behavior. It remains language
coverage work under #99, not a claim of complete deferred-body validation.

Восстановленная реализация проверяется заново. 39 объявлений и успешные нативные
сценарии не доказывают все контракты D.7; владение, freeze-состояние и lifetime
остаются неизвестными для CPG. Реестр сохраняет отдельные поведенческие пробелы.
Общая приёмка #48 остаётся открытой.
