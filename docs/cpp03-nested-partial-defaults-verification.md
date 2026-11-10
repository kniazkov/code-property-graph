# Nested partial-pattern integral defaults: bounded #92 follow-up

Base: master `22c951f46145a968b9db615c5b0847418bb651f0` after PR #133.
Previous green checks certify that base only, not this package.

## Contract and boundaries

`template<class T,int N=2> struct Key {};` permits a nested pattern such as
`Box<Key<T> >` to match both `Key<int>` and `Key<int,2>`, but not `Key<int,3>`.
Only independent trailing integral defaults belonging to the exact bound primary
header, or verified instance origin, are completed. That header must be visible
at the partial-pattern declaration, not merely at a later outer use. Dependent
pattern syntax is not converted into a concrete instantiation request.

Literal, original bound const-chain and enumerator facts use the existing bool
zero/nonzero, unsigned-modulo and representable-signed conversions. A value
parameter type may become concrete from its own preceding explicitly written
pattern arguments: `Key<T,unsigned char>` for parameters `class T,class V,V N=258`
is within the subset; a still-dependent `Key<T>` for `class T,T N=2` is not
certified by substituting an eventual actual type.

A read-only contextual effective argument list is shared by matching, ordering,
redeclaration identity, repeated deduction and instance identity. Default nodes
may serve different target types within the same outer use without one
conversion contaminating another. Original AST, source, JSON and bindings are
preserved; bound headers are not rebuilt. Explicit and omitted equivalent forms
must not produce duplicate instances or duplicate equivalent specializations.

PodLayout and ConstantFolder retain original default dependencies of every
candidate partial header, selected or unselected. A hidden `sizeof(Giant)` or
enumerator dependency cannot evade the object ceiling or original/native enum
compatibility by normalizing to bool or a small unsigned value. Existing actual,
full-specialization and unrelated legacy type-default routes remain compatible.
Unsupported specialization-selection routes must diagnose their boundary rather
than silently choose a primary layout.

Dependent expressions, type/template defaults, default merging across separate
redeclarations, later headers, dependent/template-template/unresolved heads,
arithmetic deduction and signed out-of-range conversions remain outside this
bounded route. Default merging is permitted by the language but not certified
here. Legacy complete symbolic `H<T>` matching is preserved; nested symbolic
`H<Key<T,2> >` matching is not newly certified by this package. Defaults in the
parameter list of the partial specialization itself remain
forbidden separately. Earlier omitted-pattern exclusions in earlier reports and
summaries describe historical packages. No section 11/class.access or ABI-policy
change is included. Issues #92/#48 and offsetof, setjmp, va_start, va_arg and
va_end obligations remain open.

## Fresh original-edition review

The original ISO/IEC 14882:2003(E), second edition dated 2003-10-15, was freshly
read for this package, including its title page and these parsed PDF pages:

- PDF 269–270 / printed 241–242, §14.1 p9–14: class-template defaults,
  defaults available from declarations in scope, trailing-default requirements,
  no duplicate default in one scope, and preceding parameter scope.
- PDF 273 / printed 245, §14.3 p4: defaults may leave an empty argument list,
  but its `<>` remains required.
- PDF 274–275 / printed 246–247, §14.3.2 p1,p5: integral/enum constant
  expressions and original integral promotions/conversions for non-type arguments.
- PDF 276 / printed 248, §14.4 p1: template identity plus type/value argument
  identity, rather than the spelling of explicit versus omitted defaults.
- PDF 283–285 / printed 255–257, §14.5.4 p7–10, §14.5.4.1 p1–4 and
  §14.5.4.2 p1: previously declared partials, nondependent specialized value
  expressions/types, deduction, ambiguity and partial ordering.

§14.5.4 p10 forbids defaults in the specialization's own template parameter
list; it does not forbid omitting defaulted arguments of a nested primary-id.
§14.1 p10 allows merging visible defaults; excluding it here is an implementation
limit, not a new language prohibition. No later defect-report wording is used.
Existing byte/paragraph hashes and evidence-status classifications are historical
and unchanged. This is not a fresh review of §18.1 or a full conformance claim.
No private document identifiers, links, PDF bytes or page images are published.

## Required assertions

The following named assertions describe the intended coverage, not completed
execution. `ConstantTemplateArgumentsTest` adds:

- `nestedDefaultsCompleteOmittedPartialPatternsWithoutChangingWrittenSyntax`:
  explicit/omitted actual and pattern combinations, equality and mismatch.
- `omittedPartialPatternDefaultsConvertOriginalLiteralNamedAndEnumFacts`:
  independent facts and multiple trailing defaults.
- `omittedPartialPatternsShareOrderingAndAlphaEquivalentRedeclarations`:
  pointer ordering, ambiguity and renamed explicit/omitted redeclarations.
- `omittedPartialPatternsKeepNamespaceDeepNestingAndRepeatedDeduction`:
  scoped head identity, deeper nesting and repeated constraints.
- `omittedPartialDefaultsUseOwnExplicitTypesWithoutCrossOccurrenceContamination`:
  int/unsigned-char conversions of one default node with different target types
  in one outer use and reverse order, plus independent bool defaults.
- `omittedPartialDefaultsUsePatternDeclarationVisibilityNotLaterActualUse` and
  `unsupportedOmittedPartialDefaultsCannotSilentlySelectPrimary`: declaration
  visibility, late/redecl defaults and unsupported boundaries.
- `omittedTemplateTemplatePatternDefaultsRemainGuarded` and
  `symbolicTemplateHeadsCannotHideOmittedNestedPatternDefaults`: shortened
  template-template heads and omitted arguments hidden below symbolic heads
  fail closed rather than silently selecting a primary.
- `omittedPartialDefaultEditsReselectAndPreserveImmutableSnapshots`: 2→3→2
  edits with 8→1→8 reselection, source/JSON immutability, copying and reanalysis.
- `omittedPartialDefaultsRetainSelectedAndUnselectedHiddenLayoutDependencies`
  and `omittedPartialDefaultsRetainSelectedAndUnselectedEnumPolicyGuards`:
  hidden dependencies of selected and unselected candidate headers.

`PartialConstantTemplateArgumentsIT#nestedPartialDefaultsSurviveStagesEditsAndPackagedCli`
requires both cstddef/stddef.h, RESOLVED/GREEN/FOLDED, copies/reanalysis,
explicit/omitted identity, concrete preceding types, namespace and pointer
ordering, real const-chain edits 2→3→2 and offsets 8→1→8, immutable predecessor
source/JSON, reparsing, packaged CLI and actual CPG macro expansion. Original,
generated and materialized sources run under strict GCC and Clang C++03.
Exact source hashes and observations are recorded as `partial-defaults-*`
artifacts in `target/partial-constant-template-arguments-oracle`.

## Separate execution gates

Fresh full Maven verify, catalogue probes, corpus discovery, paragraph/macro/source
validators, portable-path tests, strict private JavaDoc and demo are required.
Exact-head Linux and Windows CI must pass before Ready/merge; post-merge CI must
then pass. Final executed counts, logs, source freshness and CI links belong in
the implementation PR body. No checklist-only follow-up commit is needed.
Historical results, a focused pass or compiler-only fixture checks do not certify
this complete package. Failed assertions, timeouts and skipped gates are not
waived.
