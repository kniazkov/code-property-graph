# Cross-topic pilot (#198)

[15 cards](records/pilot.jsonl) reserve IDs 000001–000015. The
[frozen scope](pilot-scope.json) identifies the original ISO 2003 PDF and checked
repository revision. Seventeen selected locators were reviewed, including two
context-only destructor paragraphs (12.4/5 and /7). No DR reconciliation was
needed for these narrowly stated obligations. Extracted source pages stay outside
the repository; the cards and examples are independently authored.

## Editorial decisions

- One paragraph can support several obligations; one obligation can need several
  paragraphs. The reverse dispositions cover authored links, not whole-paragraph
  completion. No context-only paragraph is labelled as having no requirement.
- Conditions deliberately exclude adjacent undefined, unspecified and
  implementation-defined cases. The sizeof card does not specify an ABI size;
  the vector card excludes bool, empty-vector dereference and one-past subscripting.
- The diagnostic cards describe ill-formed inputs. They prescribe no exact error
  wording. Their CPG location/retention expectations are engineering consequences,
  not claims that ISO specifies our graph API.
- `reviewed` concerns the bounded text. `partial` records related implementation
  inspected at the frozen revision, without accepting complete behavior. All
  cards remain `not_run` with empty evidence. Source inspection and successful
  registry validation are not conformance execution.
- No schema defect was demonstrated. Existing tests belong in a separate review
  map until execution and assertion coverage justify actual evidence. Dependencies
  are empty because no pilot card requires another registered card as a premise;
  contextual source references are not fabricated requirement dependencies.

## Existing checks versus planned checks

Each card's `example` check is newly proposed, not an executed result. The table
links existing inspected tests; none is asserted to cover every condition. Paths
are relative to the repository root, and method names are exact.

| Card suffix | Existing test path under `src/test/java/com/kniazkov/cpg/` | Method |
|---|---|---|
| 000001 | `parser/CppLexerTest.java` | `replacesAllTrigraphsBeforeSplicingAndPreservesPhysicalRanges` |
| 000002, 000004 | `parser/CppLexerTest.java` | `commentsAreSpacesWithoutMergingTokensOrEndingDirectives` |
| 000003 | `parser/CppLexerTest.java` | `partialTokensReportTheirPhysicalBeginningAfterEarlierTransformations` |
| 000005 | `parser/CppPreprocessorTest.java` | `argumentPrescanDoesNotUseFollowingTokensAndRescanDoes` |
| 000006 | `parser/CppPreprocessorTest.java` | `stringizationUsesRawSpellingAndCollapsesOnlyWhitespace` |
| 000007 | `parser/CppPreprocessorTest.java` | `recursiveMacrosTerminateAndDisabledTokensStayDisabled` |
| 000008, 000009 | `TypedConstantFoldingTest.java` | `foldedShortCircuitNeverDropsExecutedSideEffects` |
| 000010 | `TypedConstantFoldingTest.java` | `sizeofAndScalarTypeidDoNotExecuteUpdatesButAllocationBoundsDo` |
| 000011, 000012 | `ClassInitializationSemanticsTest.java` | `constructionOrderIgnoresWrittenInitializerOrderAndDestructionReversesIt` |
| 000013, 000014 | `TemplateSyntaxTest.java` | `parameterKindsDefaultsAndCopiesAreOwned` |
| 000015 | `VectorAccessNormalizationTest.java` | `accessQueriesAndBoundariesRetainWrittenTargets` |

The non-nesting example, virtual-base destruction context, typename equivalence,
wrong-kind template argument and pointer identity need dedicated assertions;
those table entries are related checks, not proofs. `ClassSemantics` already
separates complete-object/base-subobject plans, and vector tests inspect reference
results; neither establishes the stronger proposed runtime assertions here.

## Remaining ownership and delivery

The pilot does not transfer or complete any research batch. Reconcile these IDs
before authoring overlapping cards: lexing #202; macros #203; sizeof #346;
logical operators #348; lifetime #212; templates #214; vector #385.
Those owners retain unrepresented obligations, exceptions and implementation-gap
assessment. #199 owns rendering/reverse-index generation; #200 owns evidence
execution/publication. All are already in [#186](https://github.com/kniazkov/code-property-graph/issues/186).

See [delivery measurements](pilot-delivery.json). Keep the current batch limits;
15 cards fit, but this sample does not justify increasing future batch sizes.
No parser/runtime code or CI configuration changes are included.
