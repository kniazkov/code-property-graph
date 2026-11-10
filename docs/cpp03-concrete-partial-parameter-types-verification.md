# Concrete partial-specialization parameter types: bounded #92 follow-up

Base: exact GitHub master `94254da7b4b65224c813ab4a3b75946ee4794490` after PR #128.
Previous master CI [42684386337](https://github.com/kniazkov/code-property-graph/actions/runs/42684386337)
passed. It does not certify this package.

## Scope and boundaries

`ClassTemplateSemantics.partialArguments` substitutes a primary parameter's type
using preceding arguments of the partial-specialization pattern itself before
normalizing its top-level integral value. For example, `T N` in
`template<class T, T N, class U>` becomes unsigned char in
`<unsigned char, fixed, U>`. Matching, ordering and alpha-equivalent redeclaration
identity all consume the same normalized pattern.

Actual arguments at the use site never make an otherwise dependent pattern
appear certified. Snapshot-owned integral facts retain the existing bool,
unsigned-modulo and representable-signed conversions. Written headers and
initializer chains remain folding/layout dependencies and survive real edits.
Pointer/reference identity, typed substituted bodies, original enum promotion,
source preservation, cycle/ownership checks and object-size ceilings remain
unchanged. No target profile or ABI policy is added. Still-dependent types,
nested named patterns, unsupported generic deduction and signed out-of-range
conversions remain outside this subset. Five macro obligations remain open:
offsetof, setjmp, va_start, va_arg and va_end. Issues #92 and #48 are not closed.

Original-edition source-reviewed ledger records remain historical context;
the private PDF was not freshly inspected. These bounded implementation tests
and compiler comparisons do not establish complete clause 14/C++03 conformance.

## Tests and assertions

Seven added methods in `ConstantTemplateArgumentsTest` cover signed, bool and
unsigned concrete types, prefixes with more than one type parameter, aliases,
matching, ordering, redeclarations, full-specialization preference, typed bodies
and overloads, ambiguity, literal/named still-dependent rejection, signed-range
rejection, copy/fold/edit/reparse/JSON, pointer/reference identities, oversized
written dependencies and incompatible enum arithmetic. Positive layout values
are asserted before checking the edited invalid cases.

`PartialConstantTemplateArgumentsIT#concretePrecedingPatternTypesSurviveStagesAndHeaderEdits`
uses cstddef and stddef.h, RESOLVED/GREEN/FOLDED, copied/reanalyzed snapshots,
actual header-initializer edits, packaged CLI, real CPG preprocessor expansion,
and strict GCC/Clang C++03 execution. Editing the seed from 511 to 256 must change
observations from const=8, enum=2, extent=9 to const=3, enum=8, extent=4.
The existing oracle directory records source hashes, compiler version/target,
commands, profile, expansion/edit flags and observations and is uploaded by CI.

## Commands

```sh
mvn '-Dtest=ConstantTemplateArgumentsTest,OffsetofContextsTest,*ClassTemplate*Test,EnumStorageLayoutTest,MemberPointerLayoutTest,OffsetofRuntimeTest' '-Dit.test=PartialConstantTemplateArgumentsIT#concretePrecedingPatternTypesSurviveStagesAndHeaderEdits' verify
mvn -B verify
bash scripts/probe-cpp03-library-catalogue.sh
python3 scripts/check-cpp03-reports.py
python3 scripts/test-cpp03-library-macros.py
python3 scripts/check-cpp03-library-macros.py
python3 scripts/test-inventory-cpp03-source.py
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
mvn -B dependency:build-classpath -Dmdep.includeScope=compile -Dmdep.outputFile=target/javadoc-classpath
javadoc -classpath "$(cat target/javadoc-classpath)" -quiet -Werror -private -Xdoclint:all,-missing -sourcepath src/main/java -subpackages com.kniazkov.cpg -d target/apidocs
bash scripts/demo.sh
```

## Historical pre-reset checkpoint, not full acceptance

The prior execution on this production source passed 125 focused unit tests,
one native/CLI integration method, 160 strict C++03 compiler/run cases (80 GCC,
80 Clang, 80 CPG materializations, 72 edited variants), 53 Python validator tests,
strict validation of 5250 records, catalogue probes and strict JavaDoc. All 160
native source hashes were checked against metadata. Independent source review
found no blocker. GCC 14.2, Clang 19.1.7 and OpenJDK 21.0.13 were used.

The environment reset before the complete Maven result or demo completion could
be recorded. Those runs are NOT claimed as successful, and their local reports
are no longer available. The recovered implementation is saved remotely before
new long-running checks. New results must be recorded separately below.

Production SHA256 remains
`6190bae8bf7fdd7c0d90bb90f29622ce879b5899b351fb0edbf9ea79983fce98`.
Historical focused JAR SHA256 was
`d036c7976e6f3f34c5c7d868c4d37daa1b5b43a33b2062daaaddf8cfc0e4f413`;
it is not the hash of a newly rebuilt artifact.

No timeout/assertion is waived. The source checklist is updated in this same
implementation branch/PR. Its checked bounded item does not close #92, #48 or
any remaining macro obligation. Full Maven, corpus success and exact-head CI
remain separate gates.

## Краткий статус

Тип нетипового параметра может стать конкретным целым типом из предыдущих
аргументов самой частичной специализации. Типы из места использования не
сертифицируют зависимые шаблоны. После сброса среды прежний незавершённый полный
Maven не считается успешным. Пакет восстановлен; новые проверки фиксируются
отдельно. Пять обязательств макросов, #92 и #48 остаются открытыми.

## Post-recovery checkpoint

The recovered source passed the focused verify again: 125 unit tests and the
new native/CLI integration method, zero failures/errors/skips. Its 160 strict
compiler/run cases comprise 80 GCC, 80 Clang, 80 materializations and 72 edited
variants. All 160 preserved source hashes were verified again. The new JAR
SHA256 is `6ae11e81d3edf3b6fff3fec6b0b1b287a01e9f670588d943a9cb4e23bef4312b`. Production source remains byte-identical to the
historical checkpoint. Full Maven and exact-head CI are separate final gates;
final outcomes and links are recorded in the pull-request description so that
recording results does not trigger a documentation-only CI run.
