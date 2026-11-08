# C++03 #92: selected offsetof builtin subset

## Verified subset and open obligation

Expanded focused verification passed 325 distinct unit tests and 15 integration
tests in 3:51, with zero failures, errors or skips. The final provenance-check
rerun passed eight units and two IT, all overlapping; those are not additional
distinct coverage. Independent code review approved the frozen production and
rechecked the final non-offset macro provenance invariants.

The offsetof inventory obligation remains open/partial: valid POD enum,
member-pointer, template and sizeof-union-dependent cases remain outside the
supported helper subset. Totals stay 97 bounded obligations/90 names and five
open obligations/five names. Selected macro availability does not count as
completion of the ISO obligation; #92 and #48 remain open.

The preceding cpp03-offset-layout-verification.md remains a historical helper
report. This packet verifies actual CPG macro materialization and ICE behavior
for the selected subset; native host offsetof remains an independent oracle.

## Historical basis

Current public C90 §7.1.6, printed page 98/PDF page 108, requires an integral
constant expression of size_t giving the designated member byte offset. It does
not authorize a runtime null dereference, fabricated object or dummy zero.
The source-reviewed C++03 18.1/5 ledger supplies the original POD structure/union
and static/function-member restrictions. No C++11 standard-layout terminology
or later conditional-support wording is imported.

Public [C90 PDF](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf)
SHA256: `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
The private ISO2003 PDF remains absent; no fresh private-source read is claimed.
Original reviewed source hash and paragraph coordinates remain unchanged:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.

## Accepted explicit capability

```json
{"offsetof":{"emission":"gcc-clang-builtin","layout":"x86_64-sysv-lp64-v1","supportedObjectBytes":"2147483647"}}
```

Missing/empty settings are unavailable. The two selectors are closed values;
the explicit positive decimal ceiling is at most 2147483647 bytes and may be
smaller. This is a software-supported subset, not a universal compiler maximum,
allocation promise or proof that all object shapes below it compile. Compiler
limits vary by compiler, version and shape. No giant allocations are needed to
check this boundary.

The selected layout requires 8-bit bytes, exact unsigned-long size_t and the
following storage/alignment bit facts: bool and char 8/8; short 16/16; int 32/32;
long 64/64; wchar_t 32/32; float 32/32; double 64/64; long double 128/128;
ordinary object/function pointers 64/64. It also requires sequential aggregate
layout, natural field alignment, maximum-field aggregate alignment and alignment
tail padding. Conflicting, packed or unknown policies are rejected rather than silently
repaired. Nondefault native flags that change layout are outside the contract.
No cRuntime or floating-format dependency is imposed; widths alone do not prove
an ABI. Character encoding/signedness is not inferred from the target name.

The selected object ceiling and size_t representability are separate bounds.
The ceiling covers relevant layout/constant dependencies, not merely the
final selected member's small offset. Native compiler limits remain a third,
independently observed constraint.

## Coherent implementation boundary

OffsetofExpression retains a TypeNode and structured
member/index path, with snapshot-owned member references. Strict selected builtin
syntax emits genuine __builtin_offsetof, not a linked helper, ordinary call,
null-derived address arithmetic or a zero-child infinity intrinsic.

The implementation joins PodLayout with snapshot-owned ICE evaluation and shared
recursion/dependency guards. Enum values, const bounds and nested offsetof
queries use coherent context; cached stale numeric AST facts or skipping
constant-required validation are not substitutes. Cyclic dependencies terminate conservatively. Unsupported valid types remain limitations, not
blanket invalid-C++03 diagnoses.

Source generation, direct reparse, copies and actual edits honor the
selected enclosing translation-unit capability. Manually constructed or moved
nodes cannot grant capability. Type/member/index dependencies are unevaluated;
no runtime read/call/exception/null dereference is introduced, and forbidden
side effects are rejected. Preserved-source guards remain intact; unsupported
structural edits fail rather than emit stale values. Java emission is rejected.

## API and dependency-preserving transformations

OffsetofModel exposes the explicit BigInteger ceiling. PlatformProfile.offsetof()
and builder.offsetof(String, String, String) carry it. OffsetofExpression owns
a TypeNode and structured Member/Index nodes, without a numeric AST cache.
Regressions are analysis/OffsetofRuntimeTest.java and e2e/OffsetofRuntimeIT.java.
Twenty OffsetofRuntimeTest cases passed within the expanded run.

Numerical ICE queries evaluate validated offsets, but ConstantFolder retains the
whole operand-bearing expression and its transitive layout dependencies,
including field bounds and const initializers. This keeps owner/member/index
and ceiling checks live after edits; it does not weaken ICE semantics or replay
guards. An early preservation mismatch and later transitive-dependency blocker
were resolved before the final accepted run. Actual edits are re-resolved under
the existing snapshot-editing contract.

For offsetof evaluation/emission under this capability, active TU pragmas other
than exact pragma once
are refused with unsupported-capability diagnostics. This conservative rule
covers pack/align directives and expanded pragma operators, including inserted
operator syntax. Inactive directives are unaffected and pragma once remains
valid. This prevents retained native pragmas from silently changing the layout
used by emitted builtin expressions; it is not a global C++ pragma ban.

A non-POD type reached only through a sizeof dependency is an unsupported subset
case, not an invalid offsetof owner. Enum/member-pointer/template and
sizeof-union-dependent limits remain explicit. Broad structural source-rewrite
support is not inferred from the accepted fixtures, and no replay guard was
weakened.

## Final focused commands and native evidence

```sh
mvn -o -B -Dtest=OffsetofRuntimeTest,PodLayoutTest,InvalidConstantEvaluationTest,TypedConstantFoldingTest,PlatformProfileTest,ProfileTypeSemanticsTest,JsonExportTest,StandardLibraryMacrosTest,MacroSourceTest,SharedSourceRequirementsTest,ClassInitializationSemanticsTest,ClassInitializationParserTest,ClassTemplateSemanticsTest,TemplateTypeIdentityTest,FlowTest,CallSiteDataFlowTest,ReferenceBindingDataFlowTest,ObjectMemoryDataFlowTest,ExpandedExpressionDataFlowTest,ContainerFamilyFlowTest,SequenceFlowTest,DynamicArrayFlowTest,DynamicArrayAccessFlowTest,LibraryUnknownEffectsTest,UnreadWritesTest,LibraryExternalObjectsTest -Dit.test=OffsetofRuntimeIT,PodLayoutIT,PackagedCliIT,MacroEditingIT,LibraryMacrosIT,PlatformDeclaratorsIT -DargLine=-Xmx768m verify
mvn -o -B -Dtest=StandardLibraryMacrosTest -Dit.test=LibraryMacrosIT -DargLine=-Xmx768m verify
```

The first command passed 325 units and 15 IT; the second passed eight units and
two IT already present in the first. Final JAR SHA256:
`46a120f05f5e2b6f91a2f7e59fd53fb07a09141505141a0641836d2fcb216e83`.
The frozen production manifest SHA256 is
`ea5840deb32f1e0ae9d554afb1871d04ba0fca89dc765aa7dffafbc4963abf63`.
Catalogue validation and strict JavaDoc passed on that source snapshot.

Fresh target/offsetof-oracle evidence records GCC 14.2.0 (x86_64-linux-gnu) and
Clang 19.1.7 (x86_64-pc-linux-gnu), with strict C++03/pedantic flags. It includes
42 GCC and 50 Clang selected-header hashes, independently observed scalar sizes
and alignments including object/function pointers, exact size_t result type and
host offset values. Probe source SHA256:
`5a0c220f84128f4c3e3f4e1427f296a6cca28391c295908ddbb5193e5382a2e6`.
Selected native profile identity:
`c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10`.
The profile's floating-format selections are not used to certify native floating
representation; this capability concerns explicitly checked storage/alignment.

Tests capture host offsetof, then undefine it and compile actual CPG-materialized
builtin tokens. Type/value/ICE checks cover supported padding, unions and nested
arrays/aliases, copies/reanalysis, generated/reparsed/GREEN/FOLDED sources and
meaningful edits. Compile-only ceiling probes at 2147483647 bytes passed both
compilers without allocations or linked giant objects. This establishes those
specific probes, not a universal maximum-object guarantee.

Independent strict `-std=c++03 -pedantic-errors -fsyntax-only` probes of
`struct Probe { char bytes[NUL]; };` observed compiler/version/shape divergence:
GCC 14.2 and Clang 19.1.7 both accepted N = 2147483647 and
2305843009213693951. GCC accepted 2305843009213693952 and
9223372036854775807, while Clang rejected them. Both rejected
18446744073709551615. These are observations for this exact declaration shape,
not universal compiler maxima. No objects were allocated or linked.

Both return-zero golden fixtures have 37 scalar changes each: 36 derived hashes
and one effective settings string adding offsetof={}; no structural changes.
Default profile identity:
`8cd4bd01654596a6678318615972eaf03c9cbfc8dedbbeebdad882f07451d5ad`.
The empty object remains unavailable.

## Remaining gates and scope

Full Maven verification is running; exact-head CI remains pending. Focused
and review success do not replace either merge gate. Previous reports and
original ledger source anchors remain unchanged. Partial implementation metadata
keeps offsetof open and inventory counts unchanged.

Прошли 325 различных unit-тестов и 15 IT. Повторная проверка восьми unit-тестов
и двух IT полностью перекрывается с ними и не увеличивает число. offsetof
остаётся открытым частичным обязательством; числа реестра не меняются.
Сохраняются все зависимости layout при folding; активные pragma/operator,
кроме точного pragma once, отклоняются при вычислении или генерации offsetof.
Неактивные директивы допустимы. supportedObjectBytes задаёт программный предел,
не универсальный максимум компилятора. Полный набор и CI проверяются отдельно.
