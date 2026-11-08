# C++03 #92: conservative POD/member/layout preparation

## Scope and executed evidence

This preparatory packet adds only package-private analysis.PodLayout. Focused
verification passed 226 distinct unit tests and five integration tests, with zero
failures, errors or skips, in 26.353 seconds. Fourteen new PodLayoutTest methods
are included; earlier repeated checks are not additional distinct coverage.

This does not activate offsetof or a compiler builtin, add an expression node
or parser, or provide constant-expression integration for a macro. offsetof
remains open and unavailable. Inventory totals stay 97 bounded obligations/
90 names and five open obligations/five names; #92 and #48 remain open.
Existing production, profile settings and golden fixtures are unchanged.

## Historical basis

Fresh public C90 §7.1.6, printed page 98/PDF page 108, specifies offsetof as an
integral constant expression of size_t giving the designated structure member's
byte offset. The designator must yield an address constant in the prescribed
static-object expression; bit-field use has undefined behavior. This contract
is not a runtime null dereference or permission to invent an offset of zero.

Public [ANSI/ISO 9899-1990 PDF](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf)
SHA256: `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
Existing reviewed C++03 `cpp03:18.1:p5` supplies POD structure/union restrictions
and static/function-member boundaries. The private ISO2003 PDF is absent; no
fresh access is claimed. Original ledger hashes and coordinates remain intact.
C++11 standard-layout terminology and later conditional-support rules are not
substituted for the original edition.

## Internal query API and target policy

The package-private helper provides layout(TypeNode, PlatformProfile) and
layout(ClassDeclaration, PlatformProfile), returning Result<Layout>, plus
offset(TypeNode, List<Step>, PlatformProfile), returning Result<Offset>.
Project-context overloads use existing IntegralConstants for snapshot-owned
constant and enum array bounds. Structured steps are Member(name) and
Index(Expression). Index expressions are queried as integral constant expressions,
not executed or attached to a fabricated object.

A Result contains either a value or a typed Failure(reason, detail). Layout
contains sizeBytes, alignmentBits and fields with exact supplied declaration
identities/offsetBytes. Offset contains offsetBytes, result type and traversed
member declarations. Sizes/offsets use target bytes; alignment uses profile bits.
The helper creates no source nodes, implicit declarations or persistent caches.

POD eligibility is checked separately from generic aggregate classification.
The supported subset includes explicit scalar sizes/alignments, eligible record
and union members, recursive nesting, aliases and fixed arrays. Ordinary methods
and static data do not affect record layout; selecting a static/function member
as an offset path is separately rejected. Non-POD subobjects and user special
members are checked conservatively. Alias/array cycles and recursive records
produce failure rather than unbounded recursion.

Existing explicit sequential layout, natural or packed1 field policy,
maximum-field aggregate alignment and alignment tail-padding are required.
Unknown policies or absent size/alignment facts do not inherit host values.
Widths alone do not certify ABI layout. No new profile setting is added. Native
agreement applies only to the independently checked selected profile, not every
policy accepted by the helper.

## Target-size representability correction

Independent review found that the earlier helper could report a known layout
larger than the selected size_t range. The corrected query returns
SIZE_NOT_REPRESENTABLE when an array product, member offset or accumulation,
union/final padding or path offset exceeds the explicit profile size_t maximum.
New regressions cover 16-, 32- and 64-bit size_t boundaries and missing/invalid
scalar facts. The final 226-unit/five-IT run supersedes the earlier 224-unit run
and its JAR; those prior counts are not added to final coverage.

Representability is necessary, not proof of compiler maximum object size. An
exactly representable large char array can still exceed native compiler limits.
The helper does not certify those limits or promise every reported layout is a
compilable native object. Final production PodLayout.java SHA256:
`09f6970d87d325ab372fa3a257b5e30083709ba8f4617baf0a12dce9fead10c9`.
Final PodLayoutTest.java SHA256:
`c6dffbab4c33dae09a85bb71b84e4e12808c3216bbe365543847c4f59339d0e0`.

## Unsupported cases and macro boundary

Enums, member pointers, templates, bit-fields, unsupported/incomplete types and
unjustified array bounds do not receive fabricated sizes or offsets. These
failure reasons describe query limitations or insufficient evidence; valid
C++03 constructs outside the subset are not thereby language errors.

Direct union layout is supported, but array bounds depending on sizeof(union)
remain UNKNOWN_ARRAY_BOUND because the existing generic sizeof evaluator is
intentionally unchanged. No generic aggregate==POD assumption or unrelated
sizeof broadening is introduced. Actual ProjectEditor changes require semantic
reanalysis under the existing editing contract; queries retain identities from
the supplied snapshot rather than reusing stale members from another snapshot.

The helper does not parse a member-designator builtin or establish future
offsetof node ownership, unevaluated CFG/DFG semantics, serialization, generation
or constant-required integration. Native host offsetof is an independent layout
oracle, never evidence of CPG macro activation or full macro support.

## Focused command and native oracle

```sh
mvn -o -B -Dtest=PodLayoutTest,AggregateLayoutSemanticsTest,ClassInitializationSemanticsTest,ClassInitializationParserTest,ClassInitializationExportTest,ProfileTypeSemanticsTest,TypedConstantFoldingTest,InvalidConstantEvaluationTest,ClassTemplateSemanticsTest,TemplateInstantiationSemanticsTest,TemplateTypeIdentityTest,MemberAddressTemplateDeductionTest,ScopedResolutionTest,ObjectMemoryDataFlowTest,ReferenceBindingDataFlowTest,FlowTest,JsonExportTest,PlatformProfileTest,StandardLibraryMacrosTest,SharedSourceRequirementsTest,GeneratedNameBindingTest -Dit.test=PodLayoutIT,PackagedCliIT -DargLine=-Xmx768m verify
```

The five IT cases comprise one analysis.PodLayoutIT and four packaged CLI cases.
The layout IT performs eight strict GCC/Clang executions: original declarations,
generated copy, independently edited declarations and generated edit under each
compiler. It compares explicit scalar sizes/alignments, unions, embedded unions,
nested aliases and arrays, copied/reparsed/generated declarations and an actual
int-to-long field edit. Host offsetof supplies independent expected layout,
without substituting for CPG macro implementation.

Artifacts under target/pod-layout-oracle record 42 GCC and 50 Clang header hashes.
Original native probe SHA256:
`9937afd1927aeab9ee42800a3ad1a74a63e4f010dbe9fabead3b685a35b11f01`.
Selected profile identity:
`caccbd1e29448852e4982173029b2c231b467031bee20ffd08e83d978798e3e5`.
Final focused JAR SHA256:
`48cc3372483da69202c417a2062b7abc59ca58b529aecb56dadffbf7041e46f0`.

## Remaining verification and work

Independent review approved the corrected implementation and passed 14 units
and one native IT in isolation. These overlap the expanded results. Full Maven
verification and exact-head CI remain separate pending gates. Earlier packet verification reports remain historical and
unchanged. The single 18.1/5 ledger addendum records preparatory helper evidence
only; the broader macro contract remains unimplemented.

Remaining #92 names are offsetof, va_start/va_arg/va_end and setjmp. This packet
closes none of those macro obligations. Future offsetof work still needs its
own supported capability, operand-bearing node, parser, constant evaluation,
provenance and end-to-end source/effect validation.

Прошли 226 различных unit-тестов и пять интеграционных тестов. Пакет добавляет
только внутренний PodLayout: консервативные POD/члены/layout, включая union,
при явных фактах профиля. offsetof остаётся открытым и недоступным; числа
реестра не меняются. Нативный offsetof служит оракулом layout, а не доказательством
поддержки макроса CPG. Допустимые случаи C++03 вне выбранного набора обозначены
ограничениями помощника. Независимое ревью одобрило исправление; полный набор и CI ожидаются отдельно.
