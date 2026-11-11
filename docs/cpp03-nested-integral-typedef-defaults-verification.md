# Bound integral typedef defaults: bounded #92 follow-up

Base: master `dfe5b50b7e5137e22ba21cdf6b492c56329bdf2c` after PR #135.
Prior green checks certify that base only. This package supports the next
bounded type-default prerequisite for `offsetof`/POD layout, not unrestricted
class-template semantics or completion of #92/#48.

## Contract

For `namespace cfg { typedef unsigned char Byte; }` and
`template<class T,class V=cfg::Byte,V N=258> struct Key {};`, nested actual and
partial-pattern `Key<T>`, `Key<T,cfg::Byte>` and `Key<T,unsigned char,2>` have
consistent effective identity under an eight-bit unsigned-char target.
Pure type defaults and multiple trailing defaults follow the same rules.

Eligibility follows already bound alias links, never a new lookup at the use.
Each link must be a global/namespace-scope typedef owned by this snapshot;
a finite chain must terminate in a builtin integral NamedType. Qualified types
are inspected without removing cv from effective type identity. An alias to a
reference is not accepted merely because the existing integer-type helper can
inspect references. One ownership index is built per analysis, not per argument.
The original TemplateArgument, alias links, source spelling and JSON remain
intact. Canonical targets prove eligibility but do not replace written arguments.

The exact bound primary header must still be visible at the actual use or the
partial-pattern declaration point. A following independent VALUE default uses
its own preceding effective arguments. A shared original node can therefore
convert differently in two occurrences of one outer use, in either order.
Bool zero/nonzero, unsigned modulo and representable signed conversion use the
selected target. Matching, ordering, renamed redeclaration identity, repeated
deduction and instance deduplication share this effective identity.

## Exclusions and unchanged surfaces

Member/local aliases, chains through them, foreign-snapshot links, cycles,
unresolved/dependent qualified aliases and non-integral terminal types stay
uncertified. Enum, class, floating, pointer, reference, array, function,
member-pointer and template defaults are not newly supported. Neither `V=T`,
`N=M`, `N=sizeof(T)` nor `N=sizeof(V)` is added. Defaults are not merged across
redeclarations; late headers and unsupported/dependent/template-template heads
remain guarded. A canonical nested-head alias without a proved pattern location
is not certified. Signed out-of-range conversion and arithmetic deduction remain
unsupported. Ordinary legacy routes are unchanged; an unsupported candidate
cannot silently justify primary layout.

Only ClassTemplateSemantics production code changes. NameResolver, Parser,
TypeParser, TypeIdentity, PodLayout and ConstantFolder are regression surfaces,
not expanded implementations. Section 11 paragraph records and access matrix
remain byte-identical. No new ABI policy, macro count or macro completion claim
is made. `offsetof`, `setjmp`, `va_start`, `va_arg` and `va_end` remain open,
as do #92 and final acceptance #48. Earlier blanket typedef-default exclusions
in checkpoint reports describe their historical boundaries.

## Fresh original-edition review

The scope review freshly inspected the original ISO/IEC 14882:2003(E), second
edition dated 2003-10-15, rather than N1638 or modern defect-report wording:

- PDF 135 / printed 107, §7.1.3 p1: typedef names are synonyms, not new types.
  The executed narrow alias/builtin identity assertion does not certify every
  typedef rule or declaration context.
- PDF 83 / printed 55, §3.9.3 p1: cv type distinctions remain relevant to
  template argument identity; eligibility must not erase them.
- PDF 269–270 / printed 241–242, §14.1 p9–14: defaults, visible declarations,
  trailing parameters and preceding parameter scope. Excluding merged defaults
  is an implementation boundary, not a C++03 prohibition.
- PDF 273–276 / printed 245–248, §14.3 p4, §14.3.1 p1–2, §14.3.2 p1,p5 and
  §14.4 p1: type-ids, original restrictions, integral conversions and effective
  specialization identity.
- PDF 283–285 / printed 255–257, §14.5.4 p7–10, §14.5.4.1 p1–4 and
  §14.5.4.2 p1: visibility, partial restrictions, deduction, ambiguity and
  ordering. A nested primary-id omission is distinct from an illegal default
  in the partial specialization's own parameter list.
- PDF 298 / printed 270, §14.6.3 p1: nondependent bindings belong to the
  definition context. This package follows existing bindings, not new lookup.
- PDF 361–362 / printed 333–334, §18.1 p3,p5: the bounded POD `offsetof`
  prerequisite does not establish the complete macro/library contract.

Paragraph annotations preserve prior hashes, validation and stage/status
classifications. Added named assertions are bounded evidence, not whole-paragraph
conformance claims. Private document identifiers, links, PDF bytes and page
assets are not published.

## Assertions and reproducible artifacts

`IntegralTypedefTemplateDefaultsTest` adds fifteen focused tests:

- actual/partial permutations and alias/builtin identity without syntax rewrites;
- pure defaults, global/namespace chains, every builtin integral kind and cv;
- bool/modulo/signed conversion and two target profiles;
- multiple defaults sharing original nodes under different preceding types in
  one outer use and reversed order;
- alias target and const-chain value edits, including selection restoration;
- ordering, ambiguity and renamed equivalent redeclarations;
- namespace head identity, repeated deduction and instance deduplication;
- exact-header visibility, late aliases and dependent/default residual guards;
- selected/unselected hidden sizeof ceilings and enum policies through copy/fold;
- definition-time alias binding despite later use-site shadowing;
- non-integral/member aliases and chains through forbidden scopes;
- cycle, detached/foreign-snapshot and local/member ownership rejection;
- cv-chain edits and restoration without collapsing cv identity.

The former integral `Alias` case in
`ConstantTemplateArgumentsTest#unsupportedTypeDefaultsRemainGuardedInActualAndPartialHeads`
is now covered positively; all other existing exclusions remain negative.
The direct proof test also verifies that Project construction rejects foreign
references; it does not use malformed external references as a valid snapshot.

`PartialConstantTemplateArgumentsIT#nestedIntegralTypedefDefaultsSurviveLifecycleAndNativeOracle`
reuses the fifteen alias observations of the builtin-default fixture with bound
typedef chains. It checks RESOLVED/GREEN/FOLDED, copy/reanalysis, generated source,
reparse, JSON, packaged CLI, real `cstddef`/`stddef.h` and materialized CPG macro
expansions. Alias target unsigned-char→unsigned-short→unsigned-char and
const-chain seed 258→259 edits change observed selection/offsets while original
source/JSON remain immutable. Original/generated/materialized sources are
compiled and run with both GCC and Clang using `-std=c++03 -pedantic-errors`.
Exact tested source, wrapper, compiler/version, SHA-256 and observed offsets are
retained in `target/partial-constant-template-arguments-oracle/type-defaults-*`.
The typedef source entries can be identified by `cfg::Count` and their metadata.
The symbolic 32-bit-long profile checks selection only, not a second native ABI.

## Separate execution gates

Focused checks are not full verification. Required fresh checks are full
`mvn clean verify`, library catalogue probes, corpus discovery, macro/source/
paragraph validators, portable-path tests, strict private JavaDoc and demo.
The final source is frozen before the full run; logs record tree, stage and exit
before/after. An interrupted or lost run is not a pass.

Independent review follows final production/test edits. Exact published-head
Linux/Windows CI must pass before sequential Ready/readback/merge, then postmerge
CI must pass. Actual counts, logs and CI links belong in the implementation PR
body so no checklist-only follow-up source commit is needed.
