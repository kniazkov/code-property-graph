# C++03 #92: bounded offsetof contexts

## Packet status

This packet extends the selected offsetof subset to already materialized,
complete concrete POD template instances and bounded context-local sizeof
dependencies, including unions. Independent review approved the frozen production
with 71 unit tests and two native IT, including the unchanged 14 SequenceFlow
tests. Strict JavaDoc passed on the same manifest. Clean expanded verification passed 444 distinct unit tests and 17 IT in 5:11,
with zero failures, errors or skips. Full Maven and exact-head CI remain
separate pending gates.

The inventory obligation remains open/partial. Totals remain 97 bounded
obligations / 90 names and five open obligations / five names. #92 and #48 remain
open. Unsupported valid cases must remain explicit limitations or receive a
concrete follow-up before any issue closure; linking a residual only to the
issue being closed does not resolve it.

The previous [builtin report](cpp03-offsetof-builtin-verification.md) and
[layout preparation report](cpp03-offset-layout-verification.md) are historical
stage reports and are unchanged.

## Unchanged capability and evidence requirements

No profile schema or golden fixture changes are introduced. The existing
optional offsetof capability
selects emission gcc-clang-builtin, layout x86_64-sysv-lp64-v1 and an explicit
positive decimal supportedObjectBytes ceiling no greater than 2147483647.
Missing/empty settings remain unavailable. The selected ceiling describes a
software-supported subset, not a universal compiler maximum.

Concrete template support is tied to the actually selected, materialized
instance. Bounded sizeof uses snapshot-owned cycle, ceiling and cache guards;
unrelated generic sizeof is unchanged. Folding retains the full expression and
transitive layout syntax. Existing pragma/operator restrictions and source
replay checks remain in force.

## Concrete scope and remaining limitations

The query admits already materialized, complete concrete POD instances selected
from primary, explicit and partial specializations. Bounded query-local sizeof
handles supported unions, maximum-member alignment/tail padding, arrays and
aliases under the existing explicit layout policy. Named constants and enum
values are reevaluated within the query budget; lazy enum dependencies include
necessary implicit predecessors without letting unrelated enumerators poison
the query. Unrelated generic sizeof behavior is unchanged.

Written type/non-type arguments and defaults, including relevant visible
unselected specialization headers in the selected family, are validated and
preserved through folding. Unselected bodies and unrelated template families
do not expand that dependency set. This preserves specialization reselection,
cycle/ceiling checks and transitive layout dependencies after real edits.

Identity-preserving parser syntax copying fixes scalar and array typedef
arguments, including Shape and Shape*. Alias preservation is restricted to
caller argument aliases
in derived ClassTemplateInstance syntax, excluding primary-template and
catalogue placeholders. Generic matching
is unchanged. The regression distinguishes outer int offset 4 from inner long
offset 8 across copies, reanalysis and FOLDED output; editing the inner long to
short rebuilds offset 2 while the outer offset remains 4. This is a narrow
prerequisite correction, not universal alias or template support.

Remaining limitations include unresolved/dependent/failed/incomplete instances,
named non-type arguments and dependent defaults such as N=sizeof(T) that the
generic instantiator cannot materialize, qualified member aliases whose target
remains dependent, and the existing generic function-pointer-argument ownership
failure. Fully resolved namespace aliases are tested positive.
Enum/member-pointer object layouts and non-POD sizeof dependencies also remain
outside the bounded query. These valid unsupported cases are not reclassified
as invalid offsetof owners. A new bounded guard rejects template arguments
whose typedef target chain remains dependent or unresolved, such as
Box<Container<long>::Value>, instead of reporting an unjustified offset. The
generic qualified matcher is not broadened. The selected ceiling is not a
compiler maximum.

Final tests include analysis/OffsetofContextsTest.java,
model/ParserSyntaxCopyTest.java and analysis/OffsetofAliasScopeTest.java,
plus e2e/OffsetofContextsIT.java, all under src/test/java/com/kniazkov/cpg.
Frozen production manifest SHA256:
`65f8f3476bfd17fc9f319e9593ffcf44ad1c97b0500caeb2a8b04e19965f20d8`.

## Historical basis

Public C90 §7.1.6, printed page 98 / PDF page 108, defines the size_t integral
constant-expression member-offset contract. The source-reviewed C++03 18.1/5
ledger adds the original POD structure/union and static/function-member
restrictions. No later standard-layout terminology is imported.

Public [C90 PDF](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf)
SHA256: `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
The private ISO2003 PDF remains absent; no fresh private-source read is claimed.
Original reviewed source hash:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
Original paragraph hashes and coordinates remain unchanged.

## Focused execution and native evidence

The clean expanded command passed 444 distinct units and 17 IT in 5:11, with
zero failures, errors or skips. Independent review passed 71 units and two
native IT on the same frozen production, including the unchanged 14 SequenceFlow
tests. Independent and earlier overlapping checks are not added to the expanded
counts. Strict JavaDoc also passed on this manifest.

Run from the repository with the configured Java/Maven and GCC/Clang toolchain:

```sh
mvn -o -B clean -Dtest=OffsetofAliasScopeTest,DeclaratorTest,ParserSyntaxCopyTest,OffsetofContextsTest,OffsetofRuntimeTest,PodLayoutTest,AggregateLayoutSemanticsTest,InvalidConstantEvaluationTest,TypedConstantFoldingTest,PlatformProfileTest,ProfileTypeSemanticsTest,JsonExportTest,StandardLibraryMacrosTest,MacroSourceTest,SharedSourceRequirementsTest,ClassInitializationSemanticsTest,ClassInitializationParserTest,ClassTemplateSemanticsTest,TemplateTypeIdentityTest,TemplateInstanceTest,TemplateInstantiationExportTest,TemplateInstantiationSemanticsTest,TemplateSyntaxTest,TemplateSpecializationSyntaxTest,TemplateExportTest,TemplateOrderingTest,MemberAddressTemplateDeductionTest,FunctionTemplateDeclarationTest,FlowTest,CallSiteDataFlowTest,ReferenceBindingDataFlowTest,ObjectMemoryDataFlowTest,ExpandedExpressionDataFlowTest,ContainerFamilyFlowTest,SequenceFlowTest,DynamicArrayFlowTest,DynamicArrayAccessFlowTest,LibraryUnknownEffectsTest,UnreadWritesTest,LibraryExternalObjectsTest -Dit.test=OffsetofContextsIT,OffsetofRuntimeIT,PodLayoutIT,PackagedCliIT,MacroEditingIT,LibraryMacrosIT,PlatformDeclaratorsIT -DargLine=-Xmx768m verify
```

Packaged JAR SHA256:
`d2fc7f2a7e37114f2b69a8f8c61e1a66cd2f992494ea789d6030d616991490f3`.

Fresh artifacts under target/offsetof-context-oracle contain 60 records:
44 matrix records and 16 direct/Shape-alias specialization-reselection records.
GCC 14.2.0 targets x86_64-linux-gnu; Clang 19.1.7 targets
x86_64-pc-linux-gnu. Both use -std=c++03 -pedantic-errors. Selected profile SHA256:
`c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10`.

The matrix observes baseline values 4/32/15/33 in 32 runs and edited values
8/48/23/49 in 12 runs. Direct and Shape-alias reselection observes 8 before
editing and 1 afterward, with eight runs each. Checks assert exact size_t,
constant-expression use and zero calls to sizeof operands. Actual CPG
materialization, copies, reanalysis, generated/reparsed and GREEN/FOLDED paths
are exercised with real edits; host offsetof is an independent oracle rather
than the sole evidence. No giant objects are allocated. This IT records no new
selected-header hashes; historical header provenance is not claimed as fresh.

## Remaining gates

Full Maven verification is running; exact-head CI remains pending. Focused
acceptance does not replace these gates. offsetof remains open/partial with
unchanged support counts and explicit residual implementation work. Original
source anchors and previous verification reports remain unchanged.

Прошли 444 различных unit-теста и 17 IT за 5:11 без ошибок и пропусков.
Независимые 71 unit-тест и два IT перекрывают этот набор и не увеличивают число.
Проверены конкретные POD-инстанцирования, локальный sizeof объединений,
материализация CPG и реальные правки. Общая поддержка шаблонов и ABI не
заявляется. offsetof остаётся open/partial: 97 обязательств / 90 имён с
ограниченной поддержкой и пять открытых обязательств / пять имён. Полный Maven
и CI остаются отдельными незавершёнными этапами; #92 и #48 открыты.
