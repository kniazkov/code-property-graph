<!-- CPG C++03 source and language audit. MIT License. -->
# C++03 language audit: source, paragraph records and open gaps (#48)

[Main matrix](../CPP03-MATRIX.md) · [Dataset and field semantics](cpp03-paragraphs/README.md)

Migration note: this is the historical audit opening, preserved from the frozen inventory.
Source-review statements, counts and implementation boundaries below report that audit,
not a fresh standards review or execution. Current issue status may differ.
[M017 mapping](documentation-migration/M017.json) records scope and checks.
Both original editions remain until #245–#247 finish their allocated ranges;
Russian opening reconciliation belongs to #246.

**Result: original-edition source review is available; full implementation acceptance remains open.**
On 2026-11-06 the user supplied a readable ISO/IEC 14882:2003 PDF. Its edition and
continuous printed pagination were checked independently by two readers. This
removes the earlier source-access blocker. It does not remove implementation gaps.

The tables below remain **rule groups**, not paragraph rows. Individual source
locators, own-word rule summaries, diagnostic categories, examples, stage status,
test citations or explicit gaps are in the [JSONL dataset](cpp03-paragraphs/README.md).
The data is in English. Counterpart parity remains pending in #246–#247. `source_reviewed=true` means the source was read, not that CPG implements
or has executed a test for the requirement.

## 1. Source and edition

- Verified document: **ISO/IEC 14882:2003(E), second edition, 2003-10-15**,
  with the INCITS adoption cover recording ANSI approval on 2003-12-29.
- File size: **2,461,934 bytes**; **786 PDF pages**. Printed pages **1–757** are
  continuous at PDF pages **29–785**; use `pdf_page = printed_page + 28`.
- SHA-256: `6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
- The supplied PDF and extracted normative text are kept outside the repository.
  They are not published, attached to the audit or uploaded to another service.
  The existing N1638 PDF entry was not changed.
- [ISO catalogue](https://www.iso.org/standard/38110.html) corroborates edition 2
  and 757 printed pages. Earlier third-party full-copy URLs returned 403 and the
  accessible preview had only cover material. Those historical failures are no
  longer the source blocker because the user supplied the complete document.

The [safe inventory](cpp03-paragraphs/inventory.json) currently contains **5,250
source locators**: **3,923 numbered paragraphs**, 500 unnumbered section blocks,
403 grammar productions, 105 tables, 318 globally numbered footnotes, and one
numbering anomaly. These sets overlap. They are **not 5,250 atomic normative
requirements**: individual bullets, table cells and signatures inside paragraphs
are not separately counted. The manifest also records 33 rejected extraction
artifacts instead of counting prose fragments as grammar.

This audit owns these source-reviewed shards:

| Shard | Numbered paragraphs | Other locators | Implementation evidence |
|---|---:|---:|---|
| [Clauses 1–4](cpp03-paragraphs/clauses-01-04.jsonl) | 329 | 126 | 59 partial, 293 gaps, 103 not applicable |
| [Annexes A–E](cpp03-paragraphs/annexes-A-E.jsonl) | 98 | 259 | 2 partial, 93 gaps, 262 not applicable |

All **812** records above were source-reviewed. None claims complete implementation
verification; the cited unit tests were inspected, not newly run by this audit.
Other chapter shards and aggregate execution results are tracked separately.

### Edition and extraction cautions

- [N1638](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1638.pdf)
  is a **2004-04-11 working draft**, not the authoritative source used here.
  [N1578](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1578.html)
  and [N1639](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1639.html)
  document later editorial/defect changes; no complete edition-wide diff is claimed.
- Original 2003 clause 1.2/1 lists ISO/IEC 9899:1999 and ISO 10646-1:2000,
  whereas N1638 lists earlier 1990/1993 references. The original 1.2/2 separately
  defines the C Standard Library through 1990 plus the 1995 amendment. Preserve
  that distinction rather than silently importing later library facilities.
- Original ADL tag `[basic.lookup.koenig]` becomes `[basic.lookup.argdep]` later.
  Alphabetic labels such as 3.4.2/2a and 3.4.3.1/1a are genuine source locators.
- The 1.3 introduction is **unnumbered**, followed by numbered paragraph 2.
  No invented 1.3/1 appears in the manifest. Original paragraph 27.7.1.3/9 is an
  overprinted/standalone label without an inferred second body. The detached
  number at 22.2.1.4 is recorded as an anomaly, not an invented requirement.
- Footnotes have global IDs and separate `call_sites`. Notes 246 (intentionally
  empty) and 272 have no uniquely recovered call-site marker; their note number
  and printed/PDF page remain reliable. A page's last heading is not used as a
  false normative owner.
- The implementation's Annex E comment cites N1638 transcription. A direct
  comparison now proves **all 173 original-2003 intervals match in order**.
  Provenance is preserved; table identity is verified, not inferred. This does
  not prove every lexer context or boundary combination.

## 2. Reading the evidence

- **P**: parsing; **M**: model/ownership; **R**: binding and semantic validation;
  **A**: analysis/transformation; **G**: C++ generation/reparse.
- A component link means its code was found and mapped to a test, not that the
  stage is complete. Every listed component has **PARTIAL** status.
- `—` means no separate stage applies to that group.
- **ERR**: an existing diagnostic assertion; **LIMIT**: unsupported-feature refusal;
  **KEEP**: conservatively preserve the expression/effects.
- Where no diagnostic code is given, tests check the described property; no
  dedicated diagnostic is claimed. Examples are abbreviated. Full reproducible
  inputs reside in the linked tests/fixture directories.
- This audit inspects code and test assertions. It does not report a new Maven
  execution. Final aggregate run results belong to the separate #48 report.

### P/M/R/A/G links

| ID | P | M | R | A | G | Existing tests |
|---|---|---|---|---|---|---|
| L | [CppLexer] | [PreprocessingToken] | — | — | [PreservedSource] | [CppLexerTest], [LexicalPhasesIT] |
| PP | [CppPreprocessor] | [Preprocessing] | — | — | [PreservedSource] | [CppPreprocessorTest], [PreprocessorIT] |
| N | [Parser] | [NameExpression] | [NameResolver] | [DataFlow] | [NameBindings] | [ScopedResolutionTest], [ScopesLinkageIT] |
| T | [TypeParser] | [TypeNode] | [TypeSemantics] | [ConstantEvaluator] | [Source] | [DeclaratorTest], [ProfileTypeSemanticsTest] |
| X | [ExpressionParser] | [ExpressionInfo] | [StandardConversions] | [ConstantFolder] | [Source] | [ExpressionSemanticsTest], [TypedConstantFoldingTest] |
| S | [StatementParser] | [FunctionFlow] | [NameResolver] | [ControlFlow] | [Source] | [Cpp03StatementScopesTest], [ExceptionCleanupFlowTest] |
| F | [Parser] | [ParameterDeclaration] | [NameResolver] | [DataFlow] | [Source] | [FunctionDefaultsSemanticsTest], [FunctionsDefaultsIT] |
| C | [Parser] | [ClassDeclaration] | [ClassSemantics] | [ControlFlow] | [Source] | [ClassInitializationSemanticsTest], [ClassesInitializationIT] |
| V | [Parser] | [BaseSpecifier] | [InheritanceSemantics] | [VirtualInheritanceValidation] | [Source] | [InheritanceSemanticsTest], [VirtualInheritanceValidationTest] |
| O | [ExpressionParser] | [CallExpression] | [OverloadResolution] | [OperatorSemantics] | [Source] | [OverloadResolutionTest], [OperatorSemanticsTest] |
| TM | [Parser] | [TemplateInstance], [ClassTemplateInstance] | [TemplateDeduction], [ClassTemplateSemantics] | [TemplateOrdering] | [Source] | [TemplateInstantiationSemanticsTest], [ClassTemplateSemanticsTest] |
| M | [ExpressionParser] | [NewExpression], [DeleteExpression] | [TypeSemantics] | [CppMemoryFlow] | [Source] | [ObjectMemoryValidationTest], [ObjectMemoryDataFlowTest] |

## 3. Inspected groups in clauses 1–16

Every group remains **PARTIAL** implementation evidence. Exact source-review
status belongs to individual JSONL records, not these grouped rows. P/M/R/A/G
references expand via the IDs above; no row claims a complete clause.

| ID / clause | Rule and example | Expected property / diagnostic | P/M/R/A/G | Test / fixture | Open boundary |
|---|---|---|---|---|---|
| 01 / 1 [intro.compliance] | Classify diagnostics separately from unsupported implementation features.<br>`export template<class T> T f(T);` | LIMIT: `unsupported-export-template`; this is not proof of an invalid C++03 program. | TM | [TemplateInstantiationExportTest] | Exported-callable resolution is explicitly unsupported. |
| 02 / 1 [intro.execution] | Do not fold away observable effects.<br>`true && f(); f() + 0;` | KEEP: call and possible effects remain. | X,S | [TypedConstantFoldingTest] / `foldedShortCircuitNeverDropsExecutedSideEffects` | No exhaustive sequencing, volatile or whole-program equivalence proof. |
| 03 / 2 [lex.phases] | Track physical locations through trigraphs and line splicing.<br>`??=define N 1` and a continued `//` comment | Tokens and original physical ranges agree; inactive text is preserved. | L,PP | [CppLexerTest] / `replacesAllTrigraphsBeforeSplicingAndPreservesPhysicalRanges`; [lexical-phases] | Phase coverage is case-based, not a paragraph census. |
| 04 / 2 [lex.pptoken], [lex.digraph] | Maximal tokenization and original alternative spelling.<br>`a+++b`, stringization of alternative tokens | Token boundaries and raw spelling, not Java lexer conventions. | L,PP | [CppLexerTest] / `maximalMunchUsesCpp03PreprocessingNumberAndOperatorGrammar`, `alternativesKeepSpellingForStringization` | All lexical productions and negative combinations are not enumerated. |
| 05 / 2 [lex.literal] | Separate written literals, exact values and target types.<br>`10UL`, `"a\0b"`, adjacent strings | Profile-aware integer type; embedded NUL retained. | L,T,X | [CppLiteralsTest]; [literals-expressions] | Decimal lexical values do not establish target floating rounding. |
| 06 / 2 [lex.name], E [extendid] | Use historical identifier tables.<br>UCN / non-ASCII identifier boundaries | Boundary tests in lexer; table source explicitly N1638. | L | [CppLexerTest] / `unicodeAndUcnIdentifiersUseCpp03AnnexEInsteadOfJavaOrModernUnicodeRules`; [CppIdentifierCharacters] | All 173 original-2003 intervals match; full lexer-context coverage remains open. |
| 07 / 3 [basic.scope] | Bind at the declaration point and preserve shadowing.<br>`int x=1; { int x=x; }` | Inner initializer refers to inner declaration; no invented outer binding. | N | [ScopedResolutionTest] / `lexicalScopesHonorDeclarationPointsAndInitializerShadowing`; [scopes-linkage] | Every class/local/template scope interaction remains unenumerated. |
| 08 / 3 [basic.link], [basic.def.odr] | Keep internal identities and link compatible declarations.<br>Two TUs each with `static int n`; shared inline function | Separate internal targets; shared inline definitions do not alone trigger an error. | N | [ScopedResolutionTest] / `internalFunctionsAndObjectsRemainDistinctInSelectedComposition`, `sharedInlineFunctionDefinitionsAreNotOdrConflicts` | No complete cross-TU token/lookup ODR proof; NDR is covered in the retained report. |
| 09 / 3 [basic.life], [basic.stc] | Distinguish storage, construction and lifetime boundaries.<br>Placement `new`, local static, bound temporary | Preserve allocation/initialization and lifetime events. | C,M,S | [ClassInitializationSemanticsTest] / `storageAndTemporaryBoundariesAreExplicit`; [casts-memory] | Arbitrary alias reuse, ABI and all lifetime-dependent optimizations are not proven. |
| 10 / 4 [conv.array], [conv.func], [conv.prom] | Represent contextual decay and rank-based promotion.<br>Array argument versus `sizeof(array)`; `short + short` | Conversion nodes/types; sizeof operand remains unevaluated. | T,X | [ExpressionSemanticsTest] / `arraysFunctionsNullQualificationAndReferenceBinding`, `promotionsUseTargetRanksRatherThanEqualWidths` | Equal target widths are not identical C++ types. |
| 11 / 4 [conv.qual], [conv.ptr] | Check nested qualification and null-pointer conversions.<br>`int**` to `const int**`; integer zero to pointer | Reject unsafe candidate or keep conversion unknown, never invent binding. | T,X,O | [ExpressionSemanticsTest] / `noInventedReferenceBindingOrUnsafeNestedQualification`; [OverloadResolutionTest] / `recursivePointerQualificationRequiresConstAtIntermediateLevels` | The examples do not establish all standard conversion sequences. |
| 12 / 5 [expr], [expr.cond], [expr.ass] | Retain precedence, written casts and value categories.<br>`a ? b : c`, `p->*m`, compound assignment | Owned operands and round-trip syntax; explicit and implicit conversions differ. | X | [CppOperatorExpressionTest]; [ExpressionExportTest]; [literals-expressions] | Syntax preservation alone is not complete type validity or effects. |
| 13 / 5 [expr.log.and], [expr.log.or], [expr.sizeof] | Model conditional and unevaluated operands.<br>`false && ++i; sizeof(i++);` | No executed increment on either path; preserve errors in unevaluated syntax. | X,S | [TypedConstantFoldingTest] / `sizeofAndScalarTypeidDoNotExecuteUpdatesButAllocationBoundsDo`, `foldingRetainsErrorsEvenInsideUnevaluatedBranches` | Overloaded logical operators require call semantics, not builtin short circuit. |
| 14 / 5 [expr.new], [expr.delete], [expr.sizeof] | Validate supported object operators without invented layout.<br>`sizeof(void)`, `sizeof(S)` with forward `S`, `new int&`, `delete 1` | ERR: `invalid-sizeof`, `incomplete-type`, `invalid-new`, `invalid-delete`. | M,T | [ObjectMemoryValidationTest]; [casts-memory] | Symbolic layout and partial array destruction stay conservative. |
| 15 / 5 [expr.dynamic.cast], [expr.typeid] | Keep RTTI, cast failure and operand effects distinct.<br>`dynamic_cast<Base*>(&child)`, `typeid(i++)` | Supported upcast and unevaluated scalar typeid are retained. | X,M,V | [ObjectMemoryValidationTest] / `typeidRequiresCompleteClassButAllowsPointersAndVoid`; [casts-memory] | The fixture upcast does not establish every runtime downcast/cross-cast or bad_cast path. |
| 16 / 5 [expr.const], arithmetic | Avoid assigning portable values to undefined or implementation-defined arithmetic.<br>`2147483647 + 1`, `-1 >> 1`, `-5 / 2` | KEEP: no exact folded integer in the tested target profile. | T,X | [TypedConstantFoldingTest] / `shiftsAndUndefinedOrImplementationDefinedOperationsStayConservative` | IntegerRanges is intentionally narrower than full C++ arithmetic. |
| 17 / 6 [stmt.iter], [stmt.switch] | Preserve loop targets and switch fall-through.<br>`for (...) { continue; }`; repeated `case 1` | Iteration target for continue; ERR `duplicate-case` for duplicate label. | S | [ExceptionCleanupFlowTest] / `breakContinueGotoAndCaseFallthroughRetainTheirTargets`; [Cpp03StatementScopesTest] / `switchLabelsHavePerSwitchIdentity`; [statements-exceptions] | All statement grammar productions and combinations are not enumerated. |

Continue with [the remaining audit groups](cpp03-language-audit.en.md#3-inspected-groups-in-clauses-116), starting at original line 135 (#245).

[BaseSpecifier]: ../src/main/java/com/kniazkov/cpg/model/BaseSpecifier.java
[CallExpression]: ../src/main/java/com/kniazkov/cpg/model/CallExpression.java
[ClassDeclaration]: ../src/main/java/com/kniazkov/cpg/model/ClassDeclaration.java
[ClassInitializationSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ClassInitializationSemanticsTest.java
[ClassSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/ClassSemantics.java
[ClassTemplateInstance]: ../src/main/java/com/kniazkov/cpg/model/ClassTemplateInstance.java
[ClassTemplateSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/ClassTemplateSemantics.java
[ClassTemplateSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ClassTemplateSemanticsTest.java
[ClassesInitializationIT]: ../src/test/java/com/kniazkov/cpg/e2e/ClassesInitializationIT.java
[ConstantEvaluator]: ../src/main/java/com/kniazkov/cpg/analysis/ConstantEvaluator.java
[ConstantFolder]: ../src/main/java/com/kniazkov/cpg/analysis/ConstantFolder.java
[ControlFlow]: ../src/main/java/com/kniazkov/cpg/analysis/ControlFlow.java
[Cpp03StatementScopesTest]: ../src/test/java/com/kniazkov/cpg/Cpp03StatementScopesTest.java
[CppIdentifierCharacters]: ../src/main/java/com/kniazkov/cpg/parser/CppIdentifierCharacters.java
[CppLexer]: ../src/main/java/com/kniazkov/cpg/parser/CppLexer.java
[CppLexerTest]: ../src/test/java/com/kniazkov/cpg/parser/CppLexerTest.java
[CppLiteralsTest]: ../src/test/java/com/kniazkov/cpg/CppLiteralsTest.java
[CppMemoryFlow]: ../src/main/java/com/kniazkov/cpg/analysis/CppMemoryFlow.java
[CppOperatorExpressionTest]: ../src/test/java/com/kniazkov/cpg/parser/CppOperatorExpressionTest.java
[CppPreprocessor]: ../src/main/java/com/kniazkov/cpg/parser/CppPreprocessor.java
[CppPreprocessorTest]: ../src/test/java/com/kniazkov/cpg/parser/CppPreprocessorTest.java
[DataFlow]: ../src/main/java/com/kniazkov/cpg/analysis/DataFlow.java
[DeclaratorTest]: ../src/test/java/com/kniazkov/cpg/parser/DeclaratorTest.java
[DeleteExpression]: ../src/main/java/com/kniazkov/cpg/model/DeleteExpression.java
[ExceptionCleanupFlowTest]: ../src/test/java/com/kniazkov/cpg/ExceptionCleanupFlowTest.java
[ExpressionExportTest]: ../src/test/java/com/kniazkov/cpg/ExpressionExportTest.java
[ExpressionInfo]: ../src/main/java/com/kniazkov/cpg/model/ExpressionInfo.java
[ExpressionParser]: ../src/main/java/com/kniazkov/cpg/parser/ExpressionParser.java
[ExpressionSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ExpressionSemanticsTest.java
[FunctionDefaultsSemanticsTest]: ../src/test/java/com/kniazkov/cpg/FunctionDefaultsSemanticsTest.java
[FunctionFlow]: ../src/main/java/com/kniazkov/cpg/model/FunctionFlow.java
[FunctionsDefaultsIT]: ../src/test/java/com/kniazkov/cpg/e2e/FunctionsDefaultsIT.java
[InheritanceSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/InheritanceSemantics.java
[InheritanceSemanticsTest]: ../src/test/java/com/kniazkov/cpg/InheritanceSemanticsTest.java
[LexicalPhasesIT]: ../src/test/java/com/kniazkov/cpg/e2e/LexicalPhasesIT.java
[NameBindings]: ../src/main/java/com/kniazkov/cpg/generation/NameBindings.java
[NameExpression]: ../src/main/java/com/kniazkov/cpg/model/NameExpression.java
[NameResolver]: ../src/main/java/com/kniazkov/cpg/analysis/NameResolver.java
[NewExpression]: ../src/main/java/com/kniazkov/cpg/model/NewExpression.java
[ObjectMemoryDataFlowTest]: ../src/test/java/com/kniazkov/cpg/ObjectMemoryDataFlowTest.java
[ObjectMemoryValidationTest]: ../src/test/java/com/kniazkov/cpg/ObjectMemoryValidationTest.java
[OperatorSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/OperatorSemantics.java
[OperatorSemanticsTest]: ../src/test/java/com/kniazkov/cpg/OperatorSemanticsTest.java
[OverloadResolution]: ../src/main/java/com/kniazkov/cpg/analysis/OverloadResolution.java
[OverloadResolutionTest]: ../src/test/java/com/kniazkov/cpg/OverloadResolutionTest.java
[ParameterDeclaration]: ../src/main/java/com/kniazkov/cpg/model/ParameterDeclaration.java
[Parser]: ../src/main/java/com/kniazkov/cpg/parser/Parser.java
[Preprocessing]: ../src/main/java/com/kniazkov/cpg/model/Preprocessing.java
[PreprocessingToken]: ../src/main/java/com/kniazkov/cpg/parser/PreprocessingToken.java
[PreprocessorIT]: ../src/test/java/com/kniazkov/cpg/e2e/PreprocessorIT.java
[PreservedSource]: ../src/main/java/com/kniazkov/cpg/generation/PreservedSource.java
[ProfileTypeSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ProfileTypeSemanticsTest.java
[ScopedResolutionTest]: ../src/test/java/com/kniazkov/cpg/ScopedResolutionTest.java
[ScopesLinkageIT]: ../src/test/java/com/kniazkov/cpg/e2e/ScopesLinkageIT.java
[Source]: ../src/main/java/com/kniazkov/cpg/generation/Source.java
[StandardConversions]: ../src/main/java/com/kniazkov/cpg/analysis/StandardConversions.java
[StatementParser]: ../src/main/java/com/kniazkov/cpg/parser/StatementParser.java
[TemplateDeduction]: ../src/main/java/com/kniazkov/cpg/analysis/TemplateDeduction.java
[TemplateInstance]: ../src/main/java/com/kniazkov/cpg/model/TemplateInstance.java
[TemplateInstantiationExportTest]: ../src/test/java/com/kniazkov/cpg/TemplateInstantiationExportTest.java
[TemplateInstantiationSemanticsTest]: ../src/test/java/com/kniazkov/cpg/TemplateInstantiationSemanticsTest.java
[TemplateOrdering]: ../src/main/java/com/kniazkov/cpg/analysis/TemplateOrdering.java
[TypeNode]: ../src/main/java/com/kniazkov/cpg/model/TypeNode.java
[TypeParser]: ../src/main/java/com/kniazkov/cpg/parser/TypeParser.java
[TypeSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/TypeSemantics.java
[TypedConstantFoldingTest]: ../src/test/java/com/kniazkov/cpg/TypedConstantFoldingTest.java
[VirtualInheritanceValidation]: ../src/main/java/com/kniazkov/cpg/analysis/VirtualInheritanceValidation.java
[VirtualInheritanceValidationTest]: ../src/test/java/com/kniazkov/cpg/VirtualInheritanceValidationTest.java
[casts-memory]: ../src/test/resources/cpp03/casts-memory/sources
[lexical-phases]: ../src/test/resources/cpp03/lexical-phases/sources
[literals-expressions]: ../src/test/resources/cpp03/literals-expressions/sources
[scopes-linkage]: ../src/test/resources/cpp03/scopes-linkage/sources
[statements-exceptions]: ../src/test/resources/cpp03/statements-exceptions/sources
