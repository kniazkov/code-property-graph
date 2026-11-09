# C++03 #92: named integral class-template arguments

## Bounded contract

Class-template argument completion uses snapshot-owned integral-constant facts
for already resolved const integral names and enum enumerators. The same facts
normalize full-specialization values. The normalized value retains the declared
integral parameter type, including bool truth conversion. Source requests keep
their written names rather than replacing them with computed literals.

Normal lookup still determines visibility and binding. Snapshot ownership,
constant-expression restrictions and cycle guards remain mandatory. No lookup
by spelling, host integer values or JVM layout facts are introduced. Facts are
cached once per snapshot/profile during the class-template pass.

Bounded offsetof independently rechecks written arguments, transitive
initializers and relevant specialization headers. A small normalized value does
not exempt an oversized sizeof dependency. Folding and real source edits must
preserve those dependencies and repeat specialization selection. Existing enum
promotion compatibility and optional representation contracts remain in force.

## Explicit residuals

This package supports exactly representable integral conversions plus bool
truth conversion. Modulo/wrapping or other nonrepresentable conversions are
reported as unsupported, not universally invalid C++03. Independent GCC/Clang
C++03 comparison accepts examples such as an unsigned-char argument 256 and an
unsigned argument -1; this is why the unsupported/invalid distinction matters.

Uncertified visible specialization values must not silently fall through to a
primary template. Nondependent named integral constants and value-changing integral conversions
in partial-specialization patterns remain guarded limitations rather than an
expanded deduction claim. For example, a bool pattern spelled as 2 cannot be
silently ignored in favor of the primary template after an actual argument
has normalized to true. Existing pointer/reference identity bindings remain
on their separate path.
Unresolved dependent parameter types/defaults and other unproven generic instantiations
remain outside the package. Pointer/reference template arguments receive no new
support claim. Enum-typed template parameters are not newly certified.

The source-reviewed repository ledger supplies historical original-C++03
context, including 14.3.2. The private original PDF was not freshly inspected
for this package. Modern native results are comparison evidence, not an import
of later converted-constant-expression or narrowing rules.

## Verification records

Implementation: `ClassTemplateSemantics.java`.
Focused units: `ConstantTemplateArgumentsTest` and `OffsetofContextsTest`.
Native/packaged CLI: `ConstantTemplateArgumentsIT`.
Native artifacts: `target/constant-template-arguments-oracle/`.

The acceptance matrix covers const/enumerator/literal agreement, namespace and
block binding, parameter types, full-specialization selection, real edits,
copy/reanalysis, folded source, JSON and native source materialization. Negative
cases cover unknown, runtime, floating, nonstatic, foreign and cyclic facts,
unsupported conversions and specialization patterns, layout ceilings and
original/native enum-promotion disagreement.

This is the pre-publication checkpoint for the production-source SHA256
`c12dd1bcfb9e5c548d675ff33be4e5f033175318cba16dc9ed8f3936cf9fd148`.
The local packaged JAR SHA256 is
`3acbe14a43dc60fc585d9a117bb6141b8f1557dbd1ce76c5b25a4482e662f95f`.

The final production source passed 99 selected unit tests, including 18 new
regressions. The full Maven run then passed all 1457 unit tests, with zero
failures, errors or skips, and proceeded to integration tests. Commands:
`mvn -Dtest=ConstantTemplateArgumentsTest,OffsetofContextsTest,*ClassTemplate*Test,EnumStorageLayoutTest,MemberPointerLayoutTest,OffsetofRuntimeTest test`
and `mvn verify`, using JDK 21 and both native compilers.

The 53 Python validator tests (31 macro, 9 source inventory, 13 paragraph)
and strict validation of all 5250 existing ledger records passed. This is
record consistency, not a claim of complete language or library conformance.

Full integration/native results, strict JavaDoc, catalogue/demo and exact-head
CI remain separate gates at this checkpoint. The associated PR records the
validated remote commit and final gate outcomes before merge. Earlier native
runs of 160 compiler executions predate the last identity/selection fixes and
are not presented as final-source evidence. The previous enum package passed
full CI in [PR #124](https://github.com/kniazkov/code-property-graph/pull/124);
those results do not certify this new source tree.

Macro inventory counts stay unchanged: 97 bounded obligations / 90 names and
five outstanding obligations / five names. This package advances a subset of
offsetof. Issue #92 and overall acceptance #48 remain incomplete.

## Краткий статус

Пакет добавляет факты снимка для разрешённых const-имён и перечислителей в
целых аргументах шаблонов классов. Тип параметра, записанные зависимости и
повторный выбор специализации после правки сохраняются. Непредставимые
преобразования и недоказанные шаблоны специализаций явно не поддерживаются,
но не объявляются автоматически нарушениями C++03. Все 1457 unit-тестов текущего исходника прошли. Полные интеграционные
проверки и CI ещё ожидаются; их окончательные результаты фиксируются в PR.
Прежний зелёный CI enum-пакета их не заменяет.
