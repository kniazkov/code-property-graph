# Nested actual integral defaults: bounded #92 follow-up

Base: master `ff6e8aba4c0d59f95f4aace5d8feb530d28946e9` after PR #131.
Its green CI is historical and does not certify this package.

## Contract

A nested actual class-template-id may omit trailing integral value arguments
when their independent defaults belong to its exact bound primary header,
visible before the use, or to its verified instance origin. For example,
`Key<int>` with `template<class T,int N=2> struct Key` can match the partial
pattern `Box<Key<T,2> >`. Explicit `Key<int,3>` still selects the primary.
Defaults are not merged from different redeclarations in this bounded route.
A concrete value parameter type may depend on its own preceding explicitly
written type arguments. Literal, const-initializer-chain and enumerator facts
use the snapshot's IntegralConstants, with bool zero/nonzero, unsigned modulo
and representable signed conversions.

A read-only per-NamedType effective argument overlay serves matching, repeated
type deduction, partial ordering, redeclaration identity and outer instance
keys. It retains original syntax and bindings. The narrow contextual
TypeIdentity overload does not change ordinary identity queries. Explicit and
omitted equivalent arguments must not create distinct outer instances.
PodLayout follows omitted defaults and their transitive written const/sizeof/
enum dependencies; normalization to a small literal cannot bypass a target
layout ceiling or enum compatibility policy.

Unresolved/dependent/template-template heads, dependent default expressions,
type/template defaults, omitted nested arguments in partial patterns,
merged or later defaults, signed out-of-range values and arithmetic deduction
remain outside this subset. Pre-existing default routes in families without any
full/partial specialization remain compatible; they are not newly certified
for specialization selection. Full-specialization headers retain omitted-default
dependencies even when the candidate is not selected. Unsupported cases must not silently select a
primary layout. No class.access implementation or section 11 audit changes.
The five macro obligations offsetof, setjmp, va_start, va_arg and va_end,
issues #92 and #48 remain open.

## Original-edition evidence

Scope review inspected the original ISO/IEC 14882:2003 parsed PDF at PDF270 /
printed242 §14.1p10–14, PDF273 / printed245 §14.3p4, PDF274–275 /
printed246–247 §14.3.2p1,p5, PDF276 / printed248 §14.4p1 and PDF283–285 /
printed255–257 §14.5.4p8–10, §14.5.4.1p1–4 and §14.5.4.2p1.
These establish default argument availability, integral constant expressions
and conversions, template-plus-argument identity, matching and ordering.
§14.5.4p10 prohibits defaults in a partial specialization's parameter list;
it is not a prohibition on omitted primary arguments in an actual template-id.
The implementation intentionally does not cover the whole default-merging rule.
Existing PDF/paragraph hashes remain historical; this package does not claim
new byte-hash verification or substitute later defect-report wording.

## Assertions and verification

ConstantTemplateArgumentsTest covers omitted/explicit identity and selection,
conversions, repeated equal/unequal/namespace deduction, visibility and
unsupported boundaries, immutable source/JSON and seed→fixed edits 2→3→2.
The latter change offsets 8→1→8. Written sizeof and enum dependencies remain
bounded through copies, reanalysis and folding. TemplateTypeIdentityTest checks
that the contextual completion affects only the supplied objects and leaves
ordinary identity and original syntax unchanged.

PartialConstantTemplateArgumentsIT adds
`nestedActualIntegralDefaultsSurviveStagesEditsIdentityAndPackagedCli`:
cstddef and stddef.h, RESOLVED/GREEN/FOLDED, copied/reanalyzed snapshots,
real 2→3→2 edits, reparsing, immutable predecessor source and JSON, packaged
CLI and actual CPG macro expansion. Native and generated/materialized programs
run in strict GCC/Clang C++03 mode, with observable changed offsets and exact
source hashes recorded in `target/partial-constant-template-arguments-oracle`.
These are descriptions of assertions, not claims of completed execution.

Fresh full Maven verify, catalogue probes, corpus discovery, macro/paragraph
validators, portable-path tests, strict private JavaDoc and demo are required
separately. Exact-head Linux and Windows CI must pass before Ready/merge.
Final executed counts, logs and CI links are recorded in the implementation
PR body, without a checklist-only follow-up commit. Previous tests or CI runs
do not certify this source. No timeout or failed assertion is waived.
