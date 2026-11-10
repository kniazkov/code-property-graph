# Nested builtin integral type defaults: bounded #92 follow-up

Base: master `e5800c314a0d8a3873aa071aa85c26dba58d4f41` after PR #134.
Previous green checks certify that base only, not this package.

## Contract and boundaries

For `template<class T, class V=unsigned char, V N=258> struct Key {};`,
nested actual and partial-pattern forms `Key<T>`, `Key<T,unsigned char>` and
`Key<T,unsigned char,2>` are equivalent under an eight-bit unsigned-char
profile. Different effective types or converted values still mismatch. A pure
`class V=int` default without a following value parameter is included, as are
multiple trailing defaults within the same bounded rules.

An omitted TYPE argument must be an independent, directly written builtin
integral type-id: bool, char and signed/unsigned integer kinds, with qualifiers
accepted by existing rules. Eligibility may inspect an unqualified type, but
completion retains the original type argument: `const int`, `volatile int` and
`int` do not acquire the same type identity. Typedef defaults, even integral
ones, and class, enum, pointer, reference, array, function and template defaults
are not added by this specialized route. This is an implementation subset, not
a restriction on the C++03 language.

A following value parameter gets its concrete type from its own preceding
effective arguments, including newly completed builtin type defaults. Its
independent value expression is evaluated on the original bound node, without
substitution of dependent expressions. Existing zero/nonzero bool, unsigned
modulo and representable-signed conversions use the selected target profile,
not Java or host integer widths. One original default node can have different
converted values in different nested occurrences of the same outer use; their
contextual overlays must not contaminate one another in either order.

Completion still requires the exact bound primary header, or verified instance
origin, visible at the actual use or partial-pattern declaration. A later outer
use cannot certify an earlier dependent pattern. Matching, ordering,
redeclaration identity, repeated deduction and instance identity share an
effective list without rewriting written AST, source, JSON or bindings.
Original const-chain/sizeof/enum dependencies of selected and unselected
candidates remain visible to layout and folding guards, including object-size
ceilings and explicit enum policies.

`V=T`, `N=sizeof(T)`, `N=M`, template defaults, merged defaults across primary
redeclarations, later headers, unresolved/dependent/template-template heads,
canonical aliases without a proved pattern declaration location, signed
out-of-range conversions and arithmetic deduction remain outside the new
bounded route. Defaults in a partial specialization's own parameter list remain
invalid separately. Existing ordinary default routes are preserved. Earlier
reports excluding every omitted type default describe historical checkpoints.
No section 11/class.access, general TypeIdentity or ABI-policy expansion is
claimed. #92/#48 and offsetof, setjmp, va_start, va_arg and va_end remain open.

## Fresh original-edition review

The scope review for this package freshly read the original ISO/IEC
14882:2003(E), second edition dated 2003-10-15, including its title page:

- PDF 269–270 / printed 241–242, §14.1 p9–14: parameter defaults, visible
  declarations, trailing defaults and preceding parameter scope.
- PDF 273 / printed 245, §14.3 p4 and §14.3.1 p1–2: default omission still
  requires angle brackets; type arguments are type-ids and the original edition
  imposes restrictions on local/unnamed/no-linkage types. This package's builtin
  subset does not claim to implement every restriction in p2.
- PDF 274–275 / printed 246–247, §14.3.2 p1,p5: original non-type argument
  forms and integral promotions/conversions.
- PDF 276 / printed 248, §14.4 p1: identity of the template and effective
  type/value arguments, rather than explicit/omitted spelling.
- PDF 283–285 / printed 255–257, §14.5.4 p7–10, §14.5.4.1 p1–4 and
  §14.5.4.2 p1: partial declaration visibility, original restrictions, deduction,
  ambiguity and partial ordering.

§14.1 p10 permits merging visible defaults; excluding that route here is not a
language prohibition. §14.5.4 p10 forbids defaults in the partial's own parameter
list, not omissions in a nested primary-id. No modern defect-report wording is
substituted for the original. Existing paragraph hashes and evidence statuses
remain historical unless explicitly documented otherwise; required new
assertions alone do not imply execution. No fresh §18.1 source review or full
paragraph/conformance claim is made. Private document identifiers, links,
original PDF bytes and page assets are not published.

## Required assertions and artifacts

The implementation adds focused assertions to `ConstantTemplateArgumentsTest`
and native acceptance to `PartialConstantTemplateArgumentsIT`. Required coverage
includes explicit/omitted type/value permutations, pure and multiple defaults,
matching/mismatching types and values, cv identity, bool/unsigned/representable
signed conversion, two target profiles, pointer ordering, ambiguity, renamed
redeclarations, namespace heads, repeated deduction and instance deduplication.
Shared original default nodes are exercised with different preceding effective
types in one outer use and in reversed order. Type-default and const-chain edits
must change selected layouts while predecessor snapshots/source/JSON remain
unchanged. Existing negative guards and selected/unselected hidden layout/enum
dependencies remain required.

`PartialConstantTemplateArgumentsIT#nestedIntegralTypeDefaultsSurviveLifecycleAndNativeOracle`
requires fifteen fixture aliases covering explicit/omitted permutations, mismatches,
pointer ordering, pure and cv type defaults, bool/signed/unsigned conversions,
multiple defaults, repeated identity, one shared default node consumed under
different types in both orders, and renamed partial declarations. Const-chain
258→259 and type-default unsigned-char→unsigned-short edits must change selected
layouts while retaining immutable predecessors.

`ConstantTemplateArgumentsTest` adds these bounded assertions (their presence
is not an execution claim):

- `builtinTypeDefaultsShareActualAndPatternIdentityWithoutRewritingSyntax`:
  argument permutations, lifecycle stages and instance deduplication.
- `builtinTypeDefaultsPreserveCvAndAllIntegralKinds`: pure integral type
  defaults, including preservation of qualifier identity.
- `builtinTypeDefaultsConvertValuesAndRespectTargetWidths`: bool, modulo,
  representable signed and cv value types; LP64 versus a symbolic 32-bit-long
  profile checks selection, not a second native ABI.
- `builtinTypeAndValueDefaultsKeepSharedNodesIndependentInOneUse`: distinct
  conversions of shared original defaults in one use and reversed order.
- `builtinTypeDefaultEditsAndConstChainEditsReselectWithoutMutatingSnapshots`:
  changed selected layouts with immutable predecessor snapshots.
- `unsupportedTypeDefaultsRemainGuardedInActualAndPartialHeads`: the bounded
  type-default exclusions on both sides of matching.
- `builtinTypeDefaultsShareOrderingRedeclarationsAndAmbiguity`: pointer
  ordering, renamed equivalent declarations and ambiguity.
- `builtinTypeDefaultsRespectNamespaceHeadsAndRepeatedDeductions`: namespace
  head identity and repeated constraints.
- `builtinTypeDefaultsKeepOwnHeaderVisibility`: exact own-header visibility.
- `builtinTypeDefaultsRetainSelectedAndUnselectedHiddenLayoutDependencies`
  and `builtinTypeDefaultsRetainSelectedAndUnselectedEnumPolicyGuards`:
  selected/unselected hidden dependencies retain ceilings and enum policies.

Native acceptance must cover RESOLVED/GREEN/FOLDED, copy/reanalysis, reparsing,
JSON, packaged CLI, real cstddef/stddef.h macros and CPG materialization. Original,
API-generated and materialized source run with GCC and Clang under
`-std=c++03 -pedantic-errors`; observed offsets and exact source hashes are kept
as `type-defaults-*` artifacts under
`target/partial-constant-template-arguments-oracle`. Native observations
certify only a matching host layout profile. A second symbolic target profile is
not presented as a native host ABI observation.

## Separate execution gates

Required fresh gates are full `mvn clean verify`, catalogue probes, corpus
discovery, macro/source/paragraph validators, portable-path tests, strict private
JavaDoc and demo. Independent final review follows the final source edits.
Exact-head Linux and Windows CI must pass before Ready/merge, followed by
post-merge CI. The implementation PR body records actual counts, logs, source
freshness and CI links without a checklist-only follow-up source commit.
A focused pass, historical baseline or compiler-only fixture does not certify
all gates. Failures, timeouts and skipped checks are not waived.
