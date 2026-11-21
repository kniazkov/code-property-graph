<!-- CPG: implementation requirements documentation plan. -->
# C++03 requirements documentation plan

Date: 20 November 2026. Repository review baseline: `8f3a059c9b94262e43c628b76bdcd6527303bd47`.
Status: proposed plan; production of the requirements registry has not started.

## 1. Deliverables, scope, and documentation language

Produce a comprehensive English document,
`docs/standards/cpp/cpp03/REQUIREMENTS.md`, containing testable requirements for
C++03 support in CPG. It must help developers implement behavior and reviewers
understand the expected result and its basis.

English is the language of all project documentation: requirements, plans,
decisions, model/API guides, coverage matrices, audit reports, test descriptions,
examples, source references, and generated documentation. New documentation is
written in English immediately; parallel Russian technical documentation is no
longer maintained. This aligns terminology with the language specifications and
the project source code.

The only eventual Russian documentation is the root introductory README:
`README.md` in English and `README.ru.md` with equivalent Russian content. Both
explain what the project can do and how to download/clone it and get started,
with links to the English technical documentation. Detailed technical content
currently in the root README moves into English guides.

Migrate existing Russian documentation to English, consolidate bilingual
versions without losing unique requirements or decisions, and update all links,
anchors, generators, checks, and active planning references. Preserve requirement
IDs, source locators, evidence, and acceptance criteria. Existing Russian files
are transitional inputs, not an exception to the final language policy. Remove
superseded Russian documents after their content and inbound links are migrated;
do not retain Russian redirect stubs outside the root README. This plan changes
the documentation policy; it does not claim that the migration is already done.

The requirements are CPG's own engineering specification: conditions, expected
graph properties, diagnostics, analysis limitations, and verification criteria.
They are neither a translation nor a sequential paraphrase of the standard.
Source texts, tables, and examples must not be returned to the repository;
examples are authored independently. Editorial rules alone are not a legal guarantee.

Scope: language core, preprocessing, standard library, relevant annexes, and
normative dependencies. Establish applicability to the analyzer, source generator,
and library models for every requirement. Listing requirements does not make CPG
a complete hosted C++ implementation. Deferred implementation remains in the
registry; non-applicability requires a rationale.

## 2. Establish the exact source first

The project currently has two distinct reference bases:

| Source | Established repository facts | Use |
| --- | --- | --- |
| ISO/IEC 14882:2003 | `inventory.json` identifies a 786-page PDF with SHA-256 `6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a` | Target edition; verify access to this exact source and existing references before a new substantive audit |
| WG21 N1638, 11 April 2004 | PDF removed from Git; 772 pages, SHA-256 `648c5ba99445f6df1a621ba4daec7e684d9c54f01df753610e01a329bac15096` | Auxiliary source; do not automatically treat it as the C++03 text |

Use the existing [source record](README.ru.md) and [storage rules](../README.ru.md)
as migration inputs; translate them and update these links during migration.
The reading copy remains outside Git. Check registry provenance and differences
between editions, considering editor reports and applicable defect reports.
An editor report helps locate changes but does not replace checking a disputed
rule against the target edition.

If only N1638 is available, work may begin, but affected requirements must say
“Based on N1638; correspondence to ISO 2003 has not been verified.” Do not merge
paragraph numbers, pages, or references from different documents. Identify adopted
later corrections separately as project policy.

## 3. Reuse existing material

Inventory inputs:

- The [paragraph registry](../../../cpp03-paragraphs/README.md) and its
  `inventory.json`: 5,250 locators for paragraphs, blocks, grammar productions,
  tables, and footnotes.
- The existing [requirements matrix](../../../../CPP03-MATRIX.ru.md),
  [decisions](../../../../CPP03-DECISIONS.ru.md), and
  [implementation plan](../../../../CPP03-PLAN.ru.md); migrate these to English
  and update references, including obsolete bilingual-documentation obligations.
- Existing language and library audit reports, the library macro registry,
  Java tests, and test-program sources.

5,250 locators do not mean 5,250 requirements. One fragment may contain multiple
obligations, and one requirement may depend on multiple fragments: the mapping
must be many-to-many. Do not automatically inherit `source_reviewed` or `verified`
markers; inspect their evidence and exact scope.

For every source fragment record related requirements, a justified absence of
an independent requirement, or an unresolved question. An informative example
or footnote does not create a normative obligation by itself.

## 4. Unit of description: a testable requirement

Identify independently testable obligations with their conditions and exceptions.
Do not create a requirement for every sentence or hide dozens of obligations
behind a single “support templates” entry. All authored prose is in English.

| Field | Content |
| --- | --- |
| ID | Stable identifier such as `CPG-CPP03-REQ-000001`; never reuse numbers |
| Title | Concise, testable property |
| Basis | Edition/draft, clause, label, paragraph or table; external reference and existing locator ID |
| Source confidence | Verified against ISO 2003 / N1638 only / unresolved; known differences and DRs |
| Conditions | Context, preconditions, exceptions, dependencies on other requirements |
| Rule | Independently authored description of required implementation behavior |
| Classification | Well-formed program, required diagnostic, IFNDR, UB, unspecified, implementation-defined; separate cases where necessary |
| CPG consequence | What to recognize, bind, preserve, or reject; required graph and diagnostic facts |
| Profile | Target platform, type sizes, environment, and selected CPG policy dependencies |
| Verification | Original source/scenario, expected results and exact assertions; positive, boundary, and negative cases where applicable |
| Evidence | Actual test/method paths, command and result at a specific revision, or an explicit “planned” marker |
| Status | Separate text readiness, implementation coverage, and test evidence; link open questions/issues |

CPG policy must not replace the language rule. For example, a useful UB diagnostic
may be analyzer policy without being a required compiler diagnostic. Do not assign
reference output to undefined behavior or make one permitted unspecified outcome
the only acceptable result. GCC/Clang behavior is supplementary experimental
evidence, not the source of the normative rule.

## 5. Main document structure

Organize by implementation tasks, with a reverse index from source clauses to
requirements. Proposed parts:

1. Target edition, CPG boundaries, behavior classification, and platform profile.
2. Translation phases, lexing, literals, preprocessing, and translation units.
3. Types, objects, storage and lifetime; declarations, scopes, and linkage.
4. Name lookup, conversions, expressions, and constant evaluation.
5. Statements, control flow, functions, and overloading.
6. Classes, inheritance, access, special member functions, and RTTI.
7. Templates, specialization, instantiation, and dependent names.
8. Exceptions, stack unwinding, and dynamic exception specifications.
9. General library contracts, infrastructure, strings, and localization.
10. Containers, iterators, algorithms, and numerics.
11. I/O, streams, C header families, and their normative dependencies.
12. Annexes, implementation limits, deprecated features, edition differences,
    and unresolved questions.

Library coverage must include applicable preconditions, effects, return values,
exceptions, invalidation, guarantees, and complexity, not just names/signatures.
State what CPG models, what is checked separately, and what is inapplicable to the
chosen analyzer scope.

## 6. Single data source and evidence document

The large Markdown file is a single readable deliverable. For stable IDs, link
validation, and reproducible generation, store authored records in thematic JSONL
files under `docs/standards/cpp/cpp03/requirements/` and generate `REQUIREMENTS.md`.
Validate the precise format in the pilot. The generator only formats our records;
it does not download or extract the standard.

The future English `docs/PROOF.md` uses the same relationships:
“edition and clause → requirement ID → test/method → exact verified property.”
Both root README versions link directly to both English documents. Do not present
planned tests as existing evidence or create empty directories to imply coverage.

File-based scenarios get a directory with an English description, target-language
sources, and expected results. Java unit tests contain the requirement ID, exact
source reference, and a short original English description of the checked rule.
Do not copy the standard's full text into comments.

Keep the existing paragraph registry as a source-address index. When adding new
records, establish a single owner for each status; derive older summaries rather
than maintaining independent accounts. Restructuring all tests and filling
implementation gaps are subsequent stages, not prerequisites for the initial
requirements document.

## 7. Work sequence and review checkpoints

| Stage | Work | Reviewable result |
| --- | --- | --- |
| 0. Documentation migration | Inventory Russian and bilingual documents, consolidate into English, move detailed README content to guides, update paths/anchors and active planning references | English technical documentation and plans; only the root introductory README has Russian and English versions; no broken references or lost content |
| 1. Sources and scope | Verify editions, source availability, existing data, and applicability boundaries | Source provenance, differences, and unverified bases |
| 2. Format and pilot | Author 25–40 requirements across lexing/preprocessing, expressions, lifetime, templates, and the library | English sample, record schema, forward/reverse links; fix the format before bulk authoring |
| 3. Language foundations | Cover parts 1–5; map existing tests | Substantial coherent requirements block and gap list |
| 4. Complex semantics | Cover parts 6–8 and cross-links to lookup, lifetime, and conversions | Classes, templates, and exceptions with checked conditions and exceptions |
| 5. Library | Cover parts 9–11, including dependencies on other normative documents | Library contracts, profiles, and explicit CPG model limitations |
| 6. Completeness and editions | Cover part 12; traverse the source index in reverse to find omissions and duplicates | Accounting for every locator, open question, and edition difference |
| 7. Publication | Generate documents/indexes, check links, and add links to both root README versions | PR with English documents, maintenance rules, and an accurate readiness summary |

Migration can proceed in substantial thematic batches alongside source validation;
new technical documents must be English from the outset. Complete migration before
claiming the final repository-wide language policy is fulfilled.

Review each block independently: first the wording and original scenarios, then
reverse-check against the source, focusing on exceptions and dependent rules.
Use substantial thematic PRs rather than a PR for each requirement. Estimate final
size and effort after the pilot; neither PDF pages nor locator counts measure effort.

## 8. Acceptance criteria

- Every requirement has a unique ID, basis, conditions, expected CPG behavior,
  and verification method or justified non-applicability.
- All 5,250 existing locators are accounted for as source addresses; also inspect
  obligations not yet extracted from lists/tables and gaps in the inventory itself.
- No unverified import of N1638 or modern rules into C++03 mode. Requirements
  with unverified bases remain explicitly provisional.
- Text readiness, implementation presence, and successful test execution remain
  separate. A broad test does not prove every condition of a requirement.
- Checks cover schema, IDs, links, completeness of inventory mappings, valid
  statuses, and reproducibility of generated documents.
- Substantive review separately checks rule accuracy, exceptions, independent
  examples, and sufficient acceptance criteria.
- CI runs without source standards. PDFs, extracted source texts, and third-party
  examples do not enter Git or build artifacts.
- New and generated technical documentation is English. The completed migration
  leaves only `README.ru.md` as Russian documentation, paired with the equivalent
  English root `README.md`; both explain capabilities and download/getting started.
- Migration preserves unique content, requirement IDs, source references, and
  evidence; links, anchors, scripts, and active plan requirements use the new
  destinations and no longer require parallel Russian technical documentation.

An intermediate release may explicitly list unresolved questions. Do not claim
requirements completeness against ISO 2003 until source and interpretation gaps
are resolved. Documentation readiness does not imply full C++03 implementation
conformance.


## 9. GitHub execution checklist

The execution checklist is [#186](https://github.com/kniazkov/code-property-graph/issues/186).
It contains 46 initial one-PR tasks (#187–#232), grouped into migration, source/schema/pilot,
language requirements, library requirements, verification, and publication.
Start with #187 (migration inventory), followed by #188 (source provenance).

Each task is limited to 2,500 words of new or substantively revised prose,
60 newly researched source locators, and three difficult edition/DR questions.
Migration batches compare at most 3,000 source prose words. Requirement batches
normally contain 15–25 cards, fewer for complex rules. Deterministic generated
output and metadata-only inventories are reported separately and are not evidence
of substantive review. Freeze exact file/section or locator ranges before writing.

These are initial batches, not a fixed estimate of the full work. Issues #187 and
#196 must allocate the remaining migration sections and source ranges to bounded
continuations. Create and link continuation issues before closing a batch that
cannot cover its entire topic within the limits. Independent review ranges also
need explicit owners. Keep #186 open until all required continuations are complete.

## 10. Integration branch and temporary CI suspension

Decision: 21 November 2026. All documentation-program PRs (checklist #186,
initial tasks #187–#232 and their continuations) target `documents`, not `master`.
Create each task branch from the latest `documents` and merge the reviewed PR
back into `documents`. Keep one bounded task per PR.

Commit `03b933126ac4f6af2ac0c1f57d8a0a9691cd151a` disables CI only on
this integration branch by renaming `.github/workflows/build.yml` to
`build.yml.disabled`. It changes no workflow content. Do not squash this setup
commit into documentation work. The workflow on `master` remains active.

During this phase, individual PRs do not require GitHub CI. Perform applicable
local link, schema and generation checks; tooling changes still require targeted
local tests. When developing CI integration, edit the inactive workflow file
without restoring its active extension prematurely. This decision supersedes
per-PR GitHub CI requirements in the documentation task descriptions.

Before final integration:

1. Finish and review the documentation PRs into `documents`, then reconcile any
   intervening `master` changes without losing documentation or workflow updates.
2. Run `git revert 03b933126ac4f6af2ac0c1f57d8a0a9691cd151a` on `documents`.
   Inspect the result, preserve intended CI additions, and restore active YAML.
3. Push the restoration commit and run the restored workflow on the final
   `documents` revision using `workflow_dispatch` (the current push trigger is
   limited to `master`). Fix failures and rerun after any further changes.
4. Open the integration PR from `documents` to `master`, require the restored CI
   to pass on its final revision, then merge. Do not integrate disabled CI.

The final verification/publication tasks #228 and #232 must enforce this sequence.
