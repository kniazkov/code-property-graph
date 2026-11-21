# C++03 implementation plan

[Questions and decisions](CPP03-DECISIONS.md). Update this log and checklist at
least once per three questions.

This migration covers original lines 1–148, ending partway through section 1.
[Remaining implementation chapters](CPP03-PLAN.ru.md) belong to
[#238](https://github.com/kniazkov/code-property-graph/issues/238),
[#239](https://github.com/kniazkov/code-property-graph/issues/239),
[#240](https://github.com/kniazkov/code-property-graph/issues/240),
[#241](https://github.com/kniazkov/code-property-graph/issues/241) and
[#242](https://github.com/kniazkov/code-property-graph/issues/242), in source order.
Keep the original until consolidation.

## Current documentation policy

Technical documentation is English; only introductory root READMEs are bilingual.
[Checklist #186](https://github.com/kniazkov/code-property-graph/issues/186) tracks
this documentation program and all discovered follow-ups. Each bounded task gets
one PR into `documents`, with applicable local checks while CI is disabled.
Before integration, revert `03b933126ac4f6af2ac0c1f57d8a0a9691cd151a`, retain intended
workflow additions, run restored CI and require green final integration CI before
merging into `master`; see the [integration plan](docs/standards/cpp/cpp03/REQUIREMENTS-PLAN.md).
This exception supersedes per-PR CI requirements below for documentation work.

Historical checkboxes and evidence below are preserved, not freshly certified.
GitHub issue-state check on **21 November 2026** found these implementation issues
open, in recommended order: **#145, #96, #94, #146, #147, #98, #99, #100, #101,
#102, #103**. Their links/contracts appear below. In particular, #145 is open
although its selected implementation is checked; #48 is closed although the
historical final-acceptance box remains unchecked. Neither issue closure nor
translation establishes full conformance. Use live issues for current status.

## Original GitHub task queue

Recommended order; the first seven tasks complete SQUARE. Extend the matrix and
corpus in every implementation PR; audit coverage last. Implementation acceptance
requires green GitHub CI.

- [x] [#11 — E2E corpus and strict GCC/Clang runs](https://github.com/kniazkov/code-property-graph/issues/11)
- [x] [#12 — Reproducible SQUARE project](https://github.com/kniazkov/code-property-graph/issues/12)
- [x] [#13 — Edition selection and compilation-database import](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] [#14 — Lexical phases and physical coordinates](https://github.com/kniazkov/code-property-graph/issues/14)
- [x] [#15 — Header inclusion and macro expansion](https://github.com/kniazkov/code-property-graph/issues/15)
- [x] [#16 — Expansion provenance in API, JSON and SVG](https://github.com/kniazkov/code-property-graph/issues/16)
- [x] [#17 — Edited-macro generation and first-milestone acceptance](https://github.com/kniazkov/code-property-graph/issues/17)
- [x] [#18 — Translation-unit configurations and build membership](https://github.com/kniazkov/code-property-graph/issues/18)
- [x] [#19 — Conditional compilation and definition catalogue](https://github.com/kniazkov/code-property-graph/issues/19)
- [x] [#20 — Header generation across configurations](https://github.com/kniazkov/code-property-graph/issues/20)
- [x] [#21 — Parser recovery and damaged-project export](https://github.com/kniazkov/code-property-graph/issues/21)
- [x] [#22 — Diagnostic provenance and grouping](https://github.com/kniazkov/code-property-graph/issues/22)
- [x] [#23 — Platform profile and declarators](https://github.com/kniazkov/code-property-graph/issues/23)
- [x] [#24 — Scopes, declarations and linkage](https://github.com/kniazkov/code-property-graph/issues/24)
- [x] [#25 — Literals, expressions and conversions](https://github.com/kniazkov/code-property-graph/issues/25)
- [x] [#26 — Functions and default arguments](https://github.com/kniazkov/code-property-graph/issues/26)
- [x] [#27 — Classes, initialization and special functions](https://github.com/kniazkov/code-property-graph/issues/27)
- [x] [#28 — Object sizes, casts and folding](https://github.com/kniazkov/code-property-graph/issues/28)
- [x] [#29 — Inheritance and virtual methods](https://github.com/kniazkov/code-property-graph/issues/29)
- [x] [#30 — Overloads, ADL and original-type identity](https://github.com/kniazkov/code-property-graph/issues/30)
- [x] [#31 — Statements, exceptions and CFG](https://github.com/kniazkov/code-property-graph/issues/31)
- [x] [#32 — Comments and library cleanup](https://github.com/kniazkov/code-property-graph/issues/32)
- [x] [#33 — Doxygen, parameters and linked declarations](https://github.com/kniazkov/code-property-graph/issues/33)
- [x] [#34 — Templates and dependent names](https://github.com/kniazkov/code-property-graph/issues/34)
- [x] [#35 — Substitution, specialization and instantiation](https://github.com/kniazkov/code-property-graph/issues/35)
- [x] [#36 — Library catalogue and lazy declarations](https://github.com/kniazkov/code-property-graph/issues/36)
- [x] [#37 — Vector greening and object copying](https://github.com/kniazkov/code-property-graph/issues/37)
- [x] [#38 — Vector access, modifiers and iterators](https://github.com/kniazkov/code-property-graph/issues/38)
- [x] [#39 — Sequence/associative containers and adapters](https://github.com/kniazkov/code-property-graph/issues/39)
- [x] [#40 — Strings, iterators and algorithms](https://github.com/kniazkov/code-property-graph/issues/40)
- [x] [#41 — Streams and remaining standard-header families](https://github.com/kniazkov/code-property-graph/issues/41)
- [x] [#42 — DFG, references and interprocedural effects](https://github.com/kniazkov/code-property-graph/issues/42)
- [x] [#43 — Editing, renaming and safe generation](https://github.com/kniazkov/code-property-graph/issues/43)
- [x] [#44 — Cross-snapshot provenance and standalone JSON](https://github.com/kniazkov/code-property-graph/issues/44)
- [x] [#45 — Pass history, scope and dependency recomputation](https://github.com/kniazkov/code-property-graph/issues/45)
- [x] [#46 — Custom-pass API and typed results](https://github.com/kniazkov/code-property-graph/issues/46)
- [x] [#47 — Lattices and optional interval analysis](https://github.com/kniazkov/code-property-graph/issues/47)
- [ ] [#48 — Standards coverage audit and final acceptance](https://github.com/kniazkov/code-property-graph/issues/48)

## Remaining-work queue: historical snapshot, 17 November 2026

Base: master `a512105`. [PR #168](https://github.com/kniazkov/code-property-graph/pull/168) completed selected #93 interface fixes; #145's
selected implementation ended with PR #163, leaving leading padding to #99/#48.
PR #169 merged the selected #95 subset; #96 was next. Linux ran full `mvn verify`,
Windows `mvn test`, not full Windows verify.

The original queue records accepted stages of limited scope; later gaps remain.
Use this queue's explicit dependencies. Implementation acceptance requires green
CI on the final PR revision, contract-specific tests/documentation, then master CI.

- [x] [#92](https://github.com/kniazkov/code-property-graph/issues/92): macro coordination accepted after [PR #148](https://github.com/kniazkov/code-property-graph/pull/148) under “supported or linked open remainder”: 102 obligations/95 names, five partial/open. Closure is not complete macro support.
- [x] **Restore offsetof tracking:** reviewed master `d1b93899bc75e90dc6c0a9c71e3974329c45ad5e`; reopened [#145](https://github.com/kniazkov/code-property-graph/issues/145) with implementation links/remainders. Registry URLs, `open`/`partial_open` and classifications stayed unchanged. This completed bookkeeping, not implementation; evidence follows.
- [x] [#97](https://github.com/kniazkov/code-property-graph/issues/97): separated valid specialization/instantiation from IFNDR robustness; four UB families became API/CLI/JSON policy tests without native oracles. [Assertions/commands](docs/cpp03-oracle-policy.md). Final full CI and results in the PR are required for acceptance/closure.
- [x] [#145](https://github.com/kniazkov/code-property-graph/issues/145): selected v1–v13 merged through PR #163; leading-padding normative questions remain #99/#48.
- [x] [#104](https://github.com/kniazkov/code-property-graph/issues/104): explicit instantiation following separate template declaration/definition.
- [x] [#93](https://github.com/kniazkov/code-property-graph/issues/93): stream declarations, type properties and printed-edition profile accepted in PR #168.
- [x] [#95](https://github.com/kniazkov/code-property-graph/issues/95): partial-construction cleanup for one-dimensional arrays of known length 1–64, including members/plain `new[]`. [Contract/checks/limits](docs/cpp03-array-cleanup-verification.md). Final green CI gates acceptance; unsupported generalizations remain incomplete.
- [ ] [#96](https://github.com/kniazkov/code-property-graph/issues/96): extend dynamic exception specifications.
- [ ] [#94](https://github.com/kniazkov/code-property-graph/issues/94): semantic resolution of exported templates across units; preserve native-oracle limitations.
- [ ] [#146](https://github.com/kniazkov/code-property-graph/issues/146): remaining `va_list` passing/storage and argument types. Borrowed helper parameters v2 arrived in PR #152; forwarding/other remainders remain.
- [ ] [#147](https://github.com/kniazkov/code-property-graph/issues/147): remaining `jmp_buf` forms/nonlocal-flow boundaries. Same-unit static/global direct v2 arrived in PR #153; saving-frame lifetime remains unproven.
- [ ] [#98](https://github.com/kniazkov/code-property-graph/issues/98): selected DR regressions with exact original-edition references.
- [ ] [#99](https://github.com/kniazkov/code-property-graph/issues/99): language/annex paragraph checks after #97/#104/#95/#96/#94 fixes.
- [ ] [#100](https://github.com/kniazkov/code-property-graph/issues/100): clauses 17–22 library contract checks, including remaining macros.
- [ ] [#101](https://github.com/kniazkov/code-property-graph/issues/101): container/iterator/algorithm boundaries, accounting for #98.
- [ ] [#102](https://github.com/kniazkov/code-property-graph/issues/102): clause 26 numerical contracts, accounting for #98.
- [ ] [#103](https://github.com/kniazkov/code-property-graph/issues/103): clause 27 stream boundary acceptance after #93.
- [ ] [#48](https://github.com/kniazkov/code-property-graph/issues/48): final matrix/corpus/profile/limitations audit after those tasks, documented in English. Closing original stages does not establish conformance.

Add concrete newly discovered gaps before final acceptance. This snapshot had
11 unchecked tasks, with #95 merged in PR #169 and #96 open. Selected-implementation
checkboxes do not reclassify normative obligations.

### offsetof remainder review (#145)

Revision `d1b93899bc75e90dc6c0a9c71e3974329c45ad5e`: code/existing-test inspection,
without new Java/native runs. Normative justification of newly supported forms
remained implementation work in #145. The following findings are historical.

| Area | Recorded finding |
| --- | --- |
| Signed/plain char/short/int/long | V4 covers storage widths; `SignedBitFieldLayoutTest#signedAndPlainFamiliesHaveIndependentLayoutFactsWithoutValueClaims`, `mixedSignedUnsignedUnitsAndUnnamedPaddingPreserveV3Rules`. Do not report wholly absent. |
| Named oversized integral | V5 covers char/short/int through 64 bits; `OversizedBitFieldLayoutTest#boundariesUseAllocationAlignmentNotDeclaredStorage`. Unnamed oversized/named >64 remain bounded by `PodLayout.bitField` / `maximumWidth`. |
| Named bool, 1–8 bits | V6: `BoolBitFieldLayoutTest#namedBoolWidthsUseByteStorageWithoutChangingEarlierPolicies`. Unnamed/>8 remain excluded by `boolSelected && !unnamed` / `maximumWidth`. |
| Enum bit-fields | `PodLayout.bitField` uses fundamental spelling; enums are refused. Remainder: `BoolBitFieldLayoutTest#residualsAndUndefinedDesignatorsStayConservative`. |
| Cv-qualified unnamed | `PodLayout.memberShape(type, rejectCv)` refuses these, including typedefs; `UnnamedBitFieldLayoutTest#nonpublicAndCvPaddingRemainUnsupportedNotInvalidOrNonPod`. |
| Non-public unnamed | `PodLayout.owner` returns BIT_FIELD; previous test plus `SignedBitFieldLayoutTest#unsupportedFormsDoNotExpandAlongWithSignedStorage`. Distinct from NOT_POD for named private fields. |
| Leading-positive unnamed struct padding | Guard `!union && !namedObjectSeen && width > 0`; `UnnamedBitFieldLayoutTest#leadingPositiveAndNoNamedMemberRemainBoundedUnsupported` includes a positive-padding-only nested struct. |
| Tracking | `cstddef/offsetof.remaining_work` already references #145; `support_status=open`, `partial_implementation.completion=partial_open`. Reopening restored an open target without rewriting JSON/immutable paragraph records. |

Sources: [PodLayout](src/main/java/com/kniazkov/cpg/analysis/PodLayout.java),
[signed](src/test/java/com/kniazkov/cpg/analysis/SignedBitFieldLayoutTest.java),
[oversized](src/test/java/com/kniazkov/cpg/analysis/OversizedBitFieldLayoutTest.java),
[bool](src/test/java/com/kniazkov/cpg/analysis/BoolBitFieldLayoutTest.java),
[unnamed](src/test/java/com/kniazkov/cpg/analysis/UnnamedBitFieldLayoutTest.java),
[registry](docs/cpp03-library-macros.json). Exact reviewed-commit links remain in #145.
Negative tests mix unsupported forms with invalid/UB protection. Named width zero,
negative widths and `offsetof` on bit-fields are not positive requirements.
Earlier profiles retain earlier restrictions.

## Reading the plan: green and red nodes

**Green nodes** express shared language-independent concepts: append identifies
the modified dynamic array and inserted value, usable by language-independent analysis.
**Red nodes** retain language-specific facts, e.g. selected C++ `vector<int>::push_back`.
Red is valid in fully parsed programs; both colours can coexist.
**Greening** is a separate pass producing equivalent common nodes after resolving
constructs/combinations. It can turn `vector<int>` into a dynamic integer array and
`v.push_back(x)` into append, based on type/selected operation, never name alone.
Without a suitable transformation retain the original form; preserve the input snapshot.

Working goal: read C++03 projects, build immutable graphs, resolve language/library
entities, analyse and generate C++03. A check records API availability/verification
at that stage. Work proceeds in small changes; constructs may advance ahead of
others once dependencies are met.

## 0. Starting point and acceptance per change

- [x] Handwritten Java parser; parsed/resolved/green/folded stages; C++ generation, JSON/SVG, intraprocedural CFG/DFG, E2E infrastructure.
- [x] Recorded [coverage](docs/model-guide.md), [model rules](docs/requirements.ru.md), [E2E scenarios](docs/e2e.md), [C++03 sources](docs/standards/cpp/cpp03/README.md).
- [x] [Clauses 1–27/annexes A–E matrix](CPP03-MATRIX.md), linked to stages/scenarios.
- [ ] Complete ISO 2003 paragraph review: example, expected diagnostic, parser/model/resolution/analysis/generator status and test per rule; distinguish no-required-diagnostic rules. [#48](https://github.com/kniazkov/code-property-graph/issues/48)
- [ ] Check disputed rules against 2003; N1638 includes later corrections. Record each adopted difference and test. [#48](https://github.com/kniazkov/code-property-graph/issues/48)
- [x] Reuse existing nodes, e.g. dereference/arrow access. New nodes need builders, Visitor, ranges, export and generation. [#11](https://github.com/kniazkov/code-property-graph/issues/11)
- [x] Test positive/negative input, graph shape and generation through `Cpg.read`/CLI; never present partial graphs as success. Update English guides in the same iteration. [#11](https://github.com/kniazkov/code-property-graph/issues/11)
- [x] Run Java/C++ regressions via `mvn verify`; compile original/generated defined-behaviour C++03 and compare observations. [#11](https://github.com/kniazkov/code-property-graph/issues/11)

### E2E corpus and manual execution

- [x] Store scenarios as ordinary source/header/configuration directories shared by tests/manual CLI use. Document command, expected result and Failsafe test ID locally or centrally. [#11](https://github.com/kniazkov/code-property-graph/issues/11)
- [x] Extend corpus/matrix together: minimal positive/negative examples plus interactions with existing constructs. Document expected diagnostic errors. [#11](https://github.com/kniazkov/code-property-graph/issues/11)
- [x] Extend `CliSandbox` directory loading, preserving nested paths. Run all scenarios with the packaged JAR via `mvn verify` on PR/main-branch CI; failures fail CI. Check discovered counts against the corpus index to detect omissions. [#11](https://github.com/kniazkov/code-property-graph/issues/11)
- [x] Linux executable scenarios require original/generated GCC (`g++`) and Clang (`clang++`), `-std=c++03 -pedantic-errors`; verify exits/output, separate pre/post-edit expectations. PR/main CI requires both; missing compilers/skipped scenarios fail. Report compiler version/command on failure. [#11](https://github.com/kniazkov/code-property-graph/issues/11)
- [x] Reproduce individual scenarios via Failsafe and direct CPG invocation. Compare defined-behaviour execution; check graph/diagnostic nodes, links and positions. [#11](https://github.com/kniazkov/code-property-graph/issues/11)

### First milestone: macro invocation to graph editing

- [x] Before broader STL coverage, exercise necessary portions of stages 1–3: two `.cpp`, shared `.h`, `SQUARE(x)`, compilation database, two invocations; retain in E2E. [#12](https://github.com/kniazkov/code-property-graph/issues/12)
- [x] Read via CLI; JSON exposes two reads of one argument, SVG multiplication-to-invocation/definition-operator provenance. Publish commands/expected graph fragments. [#16](https://github.com/kniazkov/code-property-graph/issues/16)
- [x] Edit only the first expansion's right operand; regenerate preserving shared header/second invocation. Compile units together and execute; distinct operand values and precomputed pre/post-edit results. [#17](https://github.com/kniazkov/code-property-graph/issues/17)
- [x] Accept after full CI and manual reproduction from the same directory using published commands. [#17](https://github.com/kniazkov/code-property-graph/issues/17)

## 1. Input project, translation units and profile

- [x] API/CLI edition selection, e.g. `--from cpp --cpp-standard c++03`. C++03 alone/default; old calls/CLI without edition produce the same graph. Forward edition to preprocessing/parsing; test both explicit routes and unsupported-edition diagnostics. [#13](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] Retain folder input; API build descriptions list units, working directories, ordered header paths and initial macro definitions/undefinitions per unit. [#13](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] CLI/API import `compile_commands.json` into that model: `directory`, `file`, `arguments` or `command`; parse without executing shell/compiler. Resolve relative paths against entry directories. [#13](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] Edition precedence: explicit API/CLI, command `-std`, default C++03. Distinguish explicit/default choice; report filename/effective unsupported edition. [#13](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] E2E: C++03-only `a.cpp`/`b.cpp`, database editions C++03/C++11. Without override diagnose `b.cpp`/C++11; API/CLI C++03 overrides parse both. Adding a lambda to `b.cpp` then fails C++03 parsing. [#13](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] Preserve search-path and `-D`/`-U` order; support joined/separate arguments. Diagnose unsupported parsing-relevant options with filename. [#13](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] CLI E2E: import from another working directory, paths with spaces, different definitions per file; compare JSON types/references against equivalent explicit API settings. [#13](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] One `Project` retains distinct units per `.cpp` build variant; enumerate/select by path and variant. Path alone is insufficient identity. [#18](https://github.com/kniazkov/code-property-graph/issues/18)
- [x] E2E: `engine.cpp` with `-DSERVER`/`-DCLIENT`; API selects both units. Calls resolve to active declarations; CFG/DFG never cross alternatives. [#18](https://github.com/kniazkov/code-property-graph/issues/18)
- [x] Explicit build membership selects units for cross-unit definition lookup; unknown membership with multiple candidates exposes all targets and ambiguity. [#18](https://github.com/kniazkov/code-property-graph/issues/18)
- [x] E2E: server/client `main.cpp` call corresponding `engine.cpp` `run()`; explicit membership selects pairs, unknown membership returns two definitions. Distinguish known-build ODR violations. [#18](https://github.com/kniazkov/code-property-graph/issues/18)
- [x] Separate physical files, inclusions and translation units. Reuse source text, but expand per inclusion context. [#13](https://github.com/kniazkov/code-property-graph/issues/13)
- [x] Default widths: `int` 32, `long` 64 bits. Document full table/units; allow explicit per-unit parameter/file profiles, e.g. JSON. [#23](https://github.com/kniazkov/code-property-graph/issues/23)
- [x] Use effective profiles for ranges, constants, `sizeof`, greening; account for target byte width. Align char signedness, `wchar_t`, encodings/macros; diagnose incompatibilities. [#23](https://github.com/kniazkov/code-property-graph/issues/23)
- [x] Default/overridable type alignment and field-layout rules; document units, aggregate alignment/tail padding. Distinguish inherited settings from unknown effective rules. [#23](https://github.com/kniazkov/code-property-graph/issues/23)
