<!-- CPG C++03 source and language audit. MIT License. -->
# C++03 language audit: source, paragraph records and open gaps (#48)

[Русский](cpp03-language-audit.ru.md) · [Main matrix](../CPP03-MATRIX.ru.md) · [Dataset and field semantics](cpp03-paragraphs/README.md)

**Result: original-edition source review is available; full implementation acceptance remains open.**
On 2026-11-06 the user supplied a readable ISO/IEC 14882:2003 PDF. Its edition and
continuous printed pagination were checked independently by two readers. This
removes the earlier source-access blocker. It does not remove implementation gaps.

The tables below remain **rule groups**, not paragraph rows. Individual source
locators, own-word rule summaries, diagnostic categories, examples, stage status,
test citations or explicit gaps are in the [JSONL dataset](cpp03-paragraphs/README.md).
The data is in English; this report and the Russian companion explain the same
facts. `source_reviewed=true` means the source was read, not that CPG implements
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
| 08 / 3 [basic.link], [basic.def.odr] | Keep internal identities and link compatible declarations.<br>Two TUs each with `static int n`; shared inline function | Separate internal targets; shared inline definitions do not alone trigger an error. | N | [ScopedResolutionTest] / `internalFunctionsAndObjectsRemainDistinctInSelectedComposition`, `sharedInlineFunctionDefinitionsAreNotOdrConflicts` | No complete cross-TU token/lookup ODR proof; NDR is separate below. |
| 09 / 3 [basic.life], [basic.stc] | Distinguish storage, construction and lifetime boundaries.<br>Placement `new`, local static, bound temporary | Preserve allocation/initialization and lifetime events. | C,M,S | [ClassInitializationSemanticsTest] / `storageAndTemporaryBoundariesAreExplicit`; [casts-memory] | Arbitrary alias reuse, ABI and all lifetime-dependent optimizations are not proven. |
| 10 / 4 [conv.array], [conv.func], [conv.prom] | Represent contextual decay and rank-based promotion.<br>Array argument versus `sizeof(array)`; `short + short` | Conversion nodes/types; sizeof operand remains unevaluated. | T,X | [ExpressionSemanticsTest] / `arraysFunctionsNullQualificationAndReferenceBinding`, `promotionsUseTargetRanksRatherThanEqualWidths` | Equal target widths are not identical C++ types. |
| 11 / 4 [conv.qual], [conv.ptr] | Check nested qualification and null-pointer conversions.<br>`int**` to `const int**`; integer zero to pointer | Reject unsafe candidate or keep conversion unknown, never invent binding. | T,X,O | [ExpressionSemanticsTest] / `noInventedReferenceBindingOrUnsafeNestedQualification`; [OverloadResolutionTest] / `recursivePointerQualificationRequiresConstAtIntermediateLevels` | The examples do not establish all standard conversion sequences. |
| 12 / 5 [expr], [expr.cond], [expr.ass] | Retain precedence, written casts and value categories.<br>`a ? b : c`, `p->*m`, compound assignment | Owned operands and round-trip syntax; explicit and implicit conversions differ. | X | [CppOperatorExpressionTest]; [ExpressionExportTest]; [literals-expressions] | Syntax preservation alone is not complete type validity or effects. |
| 13 / 5 [expr.log.and], [expr.log.or], [expr.sizeof] | Model conditional and unevaluated operands.<br>`false && ++i; sizeof(i++);` | No executed increment on either path; preserve errors in unevaluated syntax. | X,S | [TypedConstantFoldingTest] / `sizeofAndScalarTypeidDoNotExecuteUpdatesButAllocationBoundsDo`, `foldingRetainsErrorsEvenInsideUnevaluatedBranches` | Overloaded logical operators require call semantics, not builtin short circuit. |
| 14 / 5 [expr.new], [expr.delete], [expr.sizeof] | Validate supported object operators without invented layout.<br>`sizeof(void)`, `sizeof(S)` with forward `S`, `new int&`, `delete 1` | ERR: `invalid-sizeof`, `incomplete-type`, `invalid-new`, `invalid-delete`. | M,T | [ObjectMemoryValidationTest]; [casts-memory] | Symbolic layout and partial array destruction stay conservative. |
| 15 / 5 [expr.dynamic.cast], [expr.typeid] | Keep RTTI, cast failure and operand effects distinct.<br>`dynamic_cast<Base*>(&child)`, `typeid(i++)` | Supported upcast and unevaluated scalar typeid are retained. | X,M,V | [ObjectMemoryValidationTest] / `typeidRequiresCompleteClassButAllowsPointersAndVoid`; [casts-memory] | The fixture upcast does not establish every runtime downcast/cross-cast or bad_cast path. |
| 16 / 5 [expr.const], arithmetic | Avoid assigning portable values to undefined or implementation-defined arithmetic.<br>`2147483647 + 1`, `-1 >> 1`, `-5 / 2` | KEEP: no exact folded integer in the tested target profile. | T,X | [TypedConstantFoldingTest] / `shiftsAndUndefinedOrImplementationDefinedOperationsStayConservative` | IntegerRanges is intentionally narrower than full C++ arithmetic. |
| 17 / 6 [stmt.iter], [stmt.switch] | Preserve loop targets and switch fall-through.<br>`for (...) { continue; }`; repeated `case 1` | Iteration target for continue; ERR `duplicate-case` for duplicate label. | S | [ExceptionCleanupFlowTest] / `breakContinueGotoAndCaseFallthroughRetainTheirTargets`; [Cpp03StatementScopesTest] / `switchLabelsHavePerSwitchIdentity`; [statements-exceptions] | All statement grammar productions and combinations are not enumerated. |
| 18 / 6 [stmt.jump], [stmt.dcl] | Validate jumps and clean up only live objects.<br>`goto L; int x=1; L:;` | ERR `goto-crosses-initialization`; normal scope exits retain cleanup. | S,C | [Cpp03StatementScopesTest] / `invalidJumpsAndLabelsAreDiagnosed`; [ExceptionCleanupFlowTest] | Path-sensitive cleanup for arbitrary partial arrays remains incomplete. |
| 19 / 7 [dcl.typedef], [namespace.def], [namespace.udir] | Preserve aliases, reopened namespaces and using lookup.<br>`typedef int I; namespace n {...} namespace n {...}` | Nominal target links survive copies and source generation. | N,T | [ScopeDeclarationParserTest]; [ScopedResolutionTest] / `usingDirectivesMeetAtTheirCommonEnclosingNamespace` | asm and all declaration grammar alternatives have no complete acceptance ledger. |
| 20 / 7 [dcl.link], [dcl.stc] | Retain language linkage and storage identity.<br>`extern "C" int f(int);` and incompatible redeclaration | Existing tests diagnose conflicts and preserve C linkage without infecting unrelated overloads. | N | [ScopedResolutionTest] / `externalCLanguageLinkageIsInheritedAndCannotOverload`, `conflictingStorageLinkageIsDiagnosed` | Foreign ABI execution and every storage combination are not verified. |
| 21 / 8 [dcl.meaning], [dcl.ptr], [dcl.ref] | Represent binding structure rather than a flattened type spelling.<br>`int *a[3]` versus `int (*p)[3]` | Distinct owned array/pointer types survive generation/reparse. | T | [DeclaratorTest] / `arraysPointersFunctionsReferencesAndMemberPointersBindFromTheName`; [platform-declarators] | No exhaustive declarator-production or ABI matrix. |
| 22 / 8 [dcl.fct.default] | Bind defaults at declaration, evaluate at call.<br>`void log(int=next()); log(); log(2); log();` | Two omitted-argument references share one source expression; late default gives `missing-call-argument`. | F,N | [FunctionDefaultsSemanticsTest]; [functions-defaults] | Baseline-specific restrictions have tests; full per-paragraph behavior coverage remains open. |
| 23 / 8 [dcl.init], [dcl.init.aggr] | Distinguish direct/copy/value/aggregate initialization.<br>`int a[3]={7};` | Corpus observes zero tail; C++11 direct-list case is rejected. | C,T | [ClassInitializationSemanticsTest]; [classes-initialization] | A finite aggregate fixture is not the entire initialization algorithm. |
| 24 / 9 [class.mem], [class.union], [class.bit] | Keep fields, static members, bit-fields and union state distinct.<br>Unnamed bit-fields; union first-member initializer | Identity and initialization tests; sizeof bit-field gives `invalid-sizeof`. | C,T,M | [ClassInitializationSemanticsTest] / `namespacesAndMultipleUnnamedBitFieldsRemainDistinct`, `unionConstructionInitializesOnlyTheSelectedMember`; [ObjectMemoryValidationTest] | No full union active-member validity or target ABI proof. |
| 25 / 10 [class.mi], [class.virtual] | Separate repeated base subobjects from shared virtual bases.<br>Ordinary and virtual diamonds | Ambiguous ordinary base; shared virtual base; unique final-overrider validation. | V,O | [InheritanceSemanticsTest]; [VirtualInheritanceValidationTest]; [inheritance-virtual] | Incomplete/cyclic hierarchies remain conservative; no general layout inference. |
| 26 / 11 [class.access], [class.friend] | Check accessibility after candidate selection.<br>Private `f(int)` versus public `f(long)` called with `1` | ERR `inaccessible-overload`; do not choose the worse public candidate. | C,V,O | [OverloadResolutionTest] / `selectedPrivateMemberIsDiagnosedEvenWhenPublicWorseMatchExists`, `protectedAccessRequiresDerivedReceiverInDerivedContext` | Friend transitivity and every access path need a dedicated inventory. |
| 27 / 12 [class.base.init], [class.dtor] | Build in structural order and destroy in reverse.<br>Reordered written constructor-initializer list | Corpus trace `1234` then `12345678`, independent of list order. | C,V,S | [ClassInitializationSemanticsTest] / `constructionOrderIgnoresWrittenInitializerOrderAndDestructionReversesIt`; [classes-initialization] | Plans are not a complete ABI or context-independent executable trace. |
| 28 / 12 [class.copy], [class.temporary] | Track implicit special members and temporary boundaries.<br>`T b=a; b=a; const T& r=T();` | Different initialization/assignment effects; no fixed optional-elision count. | C,S | [ClassInitializationSemanticsTest] / `implicitMembersAndAggregateRemainDerivedNotDuplicateSourceDeclarations`, `storageAndTemporaryBoundariesAreExplicit` | Every copy-elision context, accessibility rule and implicit-body effect is not covered. |
| 29 / 13 [over.match], [over.ics.rank] | Rank conversion vectors and leave ambiguities unresolved.<br>`f(int,long); f(long,int); f(1,1);` | ERR `ambiguous-overload`, no selected target. | O,T | [OverloadResolutionTest] / `pairwiseVectorsRemainAmbiguousAndNoViableIsNotBound`; [overloads-adl] | No exhaustive standard candidate set/ranking denominator. |
| 30 / 13 [over.oper], 3 [basic.lookup.koenig] | Retain operator calls and argument-dependent lookup.<br>Hidden friend with class argument; overloaded `+` | Selected declaration/effects retained; hidden friend not found for unrelated int. | O,N | [OverloadResolutionTest] / `adlFindsAssociatedNamespaceAndHiddenFriendOnlyForClassArgument`; [OperatorSemanticsTest] | N1638 changed the ADL section label; use edition-aware mapping. |
| 31 / 14 [temp.param], [temp.names] | Own template syntax, defaults and dependent forms.<br>`Box<Box<int> >`, `typename T::value_type`, `x.template get<int>()` | Round-trip supported forms; C++03-invalid nested `>>` and function-template defaults rejected. | TM,T,N | [TemplateSyntaxTest]; [templates-dependent] | Full dependent-name grammar and semantic test coverage remain open. |
| 32 / 14 [temp.res], [temp.deduct] | Separate immediate substitution failure from selected-body failure.<br>Missing `T::type` candidate; selected `x.size()` for int | Fallback remains viable; selected body yields `template-body-member` with ordered request ranges. | TM,N | [TemplateInstantiationSemanticsTest]; [TemplateInstantiationExportTest] / `nestedBodyFailureExportsPrimaryTypeAndOrderedRequestRanges` | All SFINAE and two-phase lookup contexts are not enumerated. |
| 33 / 14 [temp.class.spec], [temp.spec] | Select class specializations and retain explicit requests.<br>`Box<T*>`, full `Box<int>`, explicit instantiation | Selected concrete identity and request links survive copies/export. | TM | [ClassTemplateSemanticsTest]; [template-instantiation] | Budget failures are implementation limits, not proof that the source is ill-formed. |
| 34 / 14 [temp] export | Do not conflate export syntax support with separate translation.<br>Exported template declaration in one TU, definition in another | LIMIT at resolved stage; syntax/JSON round-trip only is tested. | TM,N | [TemplateSpecializationSyntaxTest]; [TemplateInstantiationExportTest]; [NameResolver] | Required implementation work remains; GCC/Clang cannot be used to erase the requirement. |
| 35 / 15 [except.handle], [except.throw] | Preserve ordered typed handlers and rethrow regions.<br>Nested `catch(const int&) { throw; }` | Original exception and outer handler links; legal rethrow syntax is not a compile error. | S,C | [ExceptionCleanupFlowTest] / `typedHandlersAndRethrowUseOrderedEnclosingRegions`; [statements-exceptions] | All conversion/handler matching and exception object-copy cases are not exhausted. |
| 36 / 15 [except.ctor], [except.spec], [except.terminate] | Keep cleanup and exception-specification boundaries explicit.<br>Second member constructor throws; external `void quiet() throw()` | Destroy only completed members; no normal propagation through empty specification. | S,C | [ExceptionCleanupFlowTest] / `normalReturnAndConstructorFailureCleanOnlyLiveObjects`, `exceptionSpecificationsEnforceTheirBoundary` | Partial-array cleanup is explicitly conservative; no exhaustive unexpected/terminate runtime oracle. |
| 37 / 16 [cpp.replace] | Separate prescan, stringization, token paste and rescan.<br>`#define S(x) #x`; `#define J(a,b) a##b` | Raw spelling for #/##; invalid paste is diagnosed with provenance. | PP,L | [CppPreprocessorTest] / `stringizationUsesRawSpellingAndCollapsesOnlyWhitespace`, `pasteUsesRawArgumentsThenRescansAndNeverPastesAnArgumentHashHash`; [macros] | All rescanning corner cases have not been matched to every 2003 paragraph. |
| 38 / 16 [cpp.cond], [cpp.include], [cpp.line] | Keep TU configuration, inactive text and logical locations.<br>`-DDEBUG=0`: `#ifdef DEBUG` versus `#if DEBUG`; `#line` | Different branch activity; physical and logical positions remain distinct. | PP,L | [CppPreprocessorTest]; [conditional-catalog]; [diagnostic-provenance] | Include/environment policy is implementation-specific; all 2003 directives not individually audited. |

### Existing positive, negative and boundary checks

This records **specific assertions present in source**, not a new run result or
clause completeness. Every clause remains **OPEN** for acceptance. “Boundary”
means the named edge case, not every standard boundary. Main-table examples
illustrate properties; only the references here establish corresponding existing
tests. There is no positive cross-TU export acceptance scenario here.

| Clause | Positive check | Negative check | Separate boundary |
|---|---|---|---|
| 1 | [TypedConstantFoldingTest] / `foldedShortCircuitNeverDropsExecutedSideEffects` | [ParserRecoveryIT] | [TypedConstantFoldingTest] / `foldingRetainsErrorsEvenInsideUnevaluatedBranches` |
| 2 | [CppLexerTest] / `replacesAllTrigraphsBeforeSplicingAndPreservesPhysicalRanges` | [CppLexerTest] / `partialTokensReportTheirPhysicalBeginningAfterEarlierTransformations` | [CppLexerTest] / `unicodeAndUcnIdentifiersUseCpp03AnnexEInsteadOfJavaOrModernUnicodeRules` |
| 3 | [ScopedResolutionTest] / `sharedInlineFunctionDefinitionsAreNotOdrConflicts` | [ScopedResolutionTest] / `conflictingReturnTypesAndMultipleDefinitionsAreDiagnosed` | [ScopedResolutionTest] / `unknownCompositionCanLinkAUniqueExternalDefinitionButNotAlternativeVariants` |
| 4 | [ExpressionSemanticsTest] / `arraysFunctionsNullQualificationAndReferenceBinding` | [OverloadResolutionTest] / `recursivePointerQualificationRequiresConstAtIntermediateLevels` | [ExpressionSemanticsTest] / `promotionsUseTargetRanksRatherThanEqualWidths` |
| 5 | [casts-memory], [literals-expressions] | [ObjectMemoryValidationTest] / `sizeofRejectsKnownInvalidOperands` | [TypedConstantFoldingTest] / `shiftsAndUndefinedOrImplementationDefinedOperationsStayConservative` |
| 6 | [statements-exceptions] | [Cpp03StatementScopesTest] / `invalidJumpsAndLabelsAreDiagnosed` | [Cpp03StatementScopesTest] / `jumpsMayBypassUninitializedPodObjects` |
| 7 | [ScopedResolutionTest] / `usingDirectivesMeetAtTheirCommonEnclosingNamespace` | [ScopedResolutionTest] / `conflictingStorageLinkageIsDiagnosed` | [ScopedResolutionTest] / `cLanguageLinkageDoesNotInfectAnUnrelatedCppOverload` |
| 8 | [platform-declarators], [functions-defaults] | [FunctionDefaultsSemanticsTest] / `declarationPointNamesAndInvalidDefaultsAreDiagnosed` | [FunctionDefaultsSemanticsTest] / `parameterScopeAndUnevaluatedRestrictionsFollowCpp03Baseline` |
| 9 | [ClassInitializationSemanticsTest] / `unionConstructionInitializesOnlyTheSelectedMember` | [ClassInitializationSemanticsTest] / `invalidInitializersAndBitWidthsAreDiagnosed` | [ClassInitializationSemanticsTest] / `namespacesAndMultipleUnnamedBitFieldsRemainDistinct` |
| 10 | [inheritance-virtual] | [VirtualInheritanceValidationTest] / `sharedVirtualBaseRequiresUniqueFinalOverriderWithoutAnyCall` | [VirtualInheritanceValidationTest] / `unknownIncompleteAndCyclicHierarchiesRemainConservative` |
| 11 | [OverloadResolutionTest] / `selectedOutOfLineDefinitionKeepsDeclaredPublicAccess` | [OverloadResolutionTest] / `selectedPrivateMemberIsDiagnosedEvenWhenPublicWorseMatchExists` | [OverloadResolutionTest] / `protectedAccessRequiresDerivedReceiverInDerivedContext` |
| 12 | [classes-initialization] | [ClassInitializationSemanticsTest] / `nonBaseAndIndirectNonvirtualInitializersAreRejectedEvenWithVirtualBases` | [ClassInitializationSemanticsTest] / `storageAndTemporaryBoundariesAreExplicit` |
| 13 | [overloads-adl] | [OverloadResolutionTest] / `pairwiseVectorsRemainAmbiguousAndNoViableIsNotBound` | [OverloadResolutionTest] / `worseAmbiguousUserConversionDoesNotDefeatExactStandardCandidate` |
| 14 | [templates-dependent], [template-instantiation] | [TemplateInstantiationExportTest] / `nestedBodyFailureExportsPrimaryTypeAndOrderedRequestRanges` | [TemplateInstantiationSemanticsTest] / `recursiveInstantiationHasAnExplicitResourceLimit`; export: no positive cross-TU implementation |
| 15 | [statements-exceptions] | No exhaustive negative source ledger | [ExceptionCleanupFlowTest] / `partialArrayConstructionIsConservativelyIncomplete`, `exceptionSpecificationsEnforceTheirBoundary` |
| 16 | [macros], [conditional-catalog] | [CppPreprocessorTest] / `rejectsMalformedAndNonCpp03MacroForms` | [CppPreprocessorTest] / `recursiveMacrosTerminateAndDisabledTokensStayDisabled` |

## 4. Annexes A–E

This is likewise a group navigation table. Individual annex locators are in
[annexes-A-E.jsonl](cpp03-paragraphs/annexes-A-E.jsonl), including D.7 library contracts.

| Annex | Subject | P/M/R/A/G | Evidence | Limit |
|---|---|---|---|---|
| A | Syntax summary | L,PP,N,T,X,S,C,V,O,TM | [Parser], [TypeParser], [ExpressionParser], [StatementParser] | 204 informative grammar-production locators; syntax alone does not establish semantic validity. |
| B | Implementation quantities | TM | [TemplateInstantiationSemanticsTest] / `recursiveInstantiationHasAnExplicitResourceLimit`, `breadthBudgetStopsBeforePublishingTooManyInstances` | Only template budgets checked; no complete recommended-limit ledger. |
| C | Compatibility | T,N | [DeclaratorTest], [ScopedResolutionTest] | Not a complete C/C++ difference audit. |
| D | Compatibility features | N,X; library audit | [ScopedResolutionTest]; [library-catalogue.en.md](library-catalogue.en.md) | D is normative: <strstream> is a confirmed catalogue gap; deprecated does not mean optional. Optional D.6 overloads are marked separately. |
| E | Universal-character names | L | [CppIdentifierCharacters], [CppLexerTest] | Transcription cites N1638; all 173 original-2003 intervals were independently matched in order. |

## 5. No diagnostic required: separate categories

Diagnostic requirements are taken from the original source, not inferred from
whether GCC/Clang emits a message. IFNDR, undefined behavior, unspecified choices
and documented implementation choices remain distinct in the dataset.

Examples directly checked in the original clauses 1–4:

| 2003 locator | Classification and scope |
|---|---|
| 2.7/1 | IFNDR for the specified vertical-tab/form-feed line-comment condition; not every comment violation |
| 2.10/2 | IFNDR for use of reserved identifiers |
| 3.2/3 | IFNDR for the stated definition requirements of used entities |
| 3.3.6/1 | Mixed: the stated completed-class lookup consistency condition is IFNDR |
| 3.5/10 | IFNDR for inconsistent adjusted types of declarations denoting one entity |
| 3.2/5 | The stated cross-TU multiple-definition failure is **undefined behavior**, not silently relabeled IFNDR |
| 3.8/5–6 | Lifetime-related pointer/lvalue misuse is **undefined behavior**; no fixed runtime oracle |
| 4.7/3 | Out-of-range conversion to a signed integer is **implementation-defined**, not unspecified |

This is not an exhaustive NDR list. The paragraph shards retain more detailed
conditions and the other chapters. Extra CPG warnings may be useful, but must not
be presented as mandatory standard diagnostics. A UB probe must not be given a
portable expected output or exit code. Existing conservative-folding tests prove
only their actual assertions, not the absence of all UB in arbitrary programs.

## 6. Explicit gaps and closure conditions

1. **Source access is closed:** the original edition, hash and continuous pagination
   are verified. The PDF remains private and is not part of the repository.
2. **Inventory is bounded:** the accepted locator census includes numbered and
   unnumbered text, productions, tables and footnotes. It is not an atomized count
   of every sub-bullet, table cell or embedded signature. Extraction replay is not
   independent proof that no normative unit was missed.
3. **Source review and implementation coverage are separate:** every owned record
   has its own paraphrase, example, expected classification, stage status and
   evidence or specific gap. A manually proposed example is not an executed test.
   Chapters with incomplete negative/boundary coverage remain open.
4. **Concrete implementation gaps:** exported-template separate translation is
   unsupported, and required deprecated `<strstream>` is missing from the catalogue.
   Original Table 11 contains 33 C++ headers, not 32; with 18 c-prefixed headers
   the core list is 51, before the 18 `.h` compatibility headers. The Annex E range
   table now matches 2003 exactly, but full lexical context coverage is not closed.
5. **Semantic limits:** full arithmetic, ABI/layout, arbitrary alias/lifetime
   reasoning, partial array construction, all implicit calls, volatile execution
   and two-phase semantics require stronger evidence. A source-preserving graph
   is not a conforming executing implementation. Missing audit evidence is not
   automatically proof that the feature is absent.
6. **Acceptance:** complete the remaining behavior tests and fixes, then rerun API/CLI,
   JSON/SVG, copy, edit/reanalysis, generation/reparse and strict compiler checks.
   Modern `-std=c++03` remains supporting evidence, especially where compilers apply
   retroactive defect resolutions or do not implement export.

## 7. Reproduction

The extractor requires a separately supplied PDF with the exact hash above and
Poppler `pdftotext`. It keeps extracted text in memory and emits only safe metadata.
The replay was executed on 2026-11-06 and its JSON content exactly matched the
published inventory. Nine synthetic extraction regression tests passed. These are
source-tool checks, not CPG language-conformance executions.

```sh
python3 scripts/test-inventory-cpp03-source.py
python3 scripts/inventory-cpp03-source.py --pdf "$CPP03_ORIGINAL_PDF" --output /tmp/cpp03-inventory.json \
  --annex-e-java src/main/java/com/kniazkov/cpg/parser/CppIdentifierCharacters.java
python3 scripts/check-cpp03-paragraphs.py
# Require every inventoried locator to have a source-reviewed record:
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
```


Test source is the primary evidence for these rows. Key methods are named in the
table and can be found by their exact names. The [manifest] and [Cpp03CorpusIT]
define active scenarios. [E2E instructions](e2e.en.md) cover JDK 21, both native
compilers and Graphviz.

```sh
# Full current repository acceptance, not every ISO paragraph:
mvn verify
python3 scripts/check-cpp03-reports.py
# One group; do not report this filtered execution as the full corpus:
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=statements-exceptions verify
# Focused semantic test:
mvn -Dtest=OverloadResolutionTest test
```

Successful commands close only assertions actually executed. Closing #48 as a
complete standard audit additionally requires conditions 1–6 above.

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
[Cpp03CorpusIT]: ../src/test/java/com/kniazkov/cpg/e2e/Cpp03CorpusIT.java
[Cpp03StatementParserTest]: ../src/test/java/com/kniazkov/cpg/parser/Cpp03StatementParserTest.java
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
[ParserRecoveryIT]: ../src/test/java/com/kniazkov/cpg/e2e/ParserRecoveryIT.java
[Preprocessing]: ../src/main/java/com/kniazkov/cpg/model/Preprocessing.java
[PreprocessingToken]: ../src/main/java/com/kniazkov/cpg/parser/PreprocessingToken.java
[PreprocessorIT]: ../src/test/java/com/kniazkov/cpg/e2e/PreprocessorIT.java
[PreservedSource]: ../src/main/java/com/kniazkov/cpg/generation/PreservedSource.java
[ProfileTypeSemanticsTest]: ../src/test/java/com/kniazkov/cpg/ProfileTypeSemanticsTest.java
[ScopeDeclarationParserTest]: ../src/test/java/com/kniazkov/cpg/parser/ScopeDeclarationParserTest.java
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
[TemplateSpecializationSyntaxTest]: ../src/test/java/com/kniazkov/cpg/TemplateSpecializationSyntaxTest.java
[TemplateSyntaxTest]: ../src/test/java/com/kniazkov/cpg/TemplateSyntaxTest.java
[TypeNode]: ../src/main/java/com/kniazkov/cpg/model/TypeNode.java
[TypeParser]: ../src/main/java/com/kniazkov/cpg/parser/TypeParser.java
[TypeSemantics]: ../src/main/java/com/kniazkov/cpg/analysis/TypeSemantics.java
[TypedConstantFoldingTest]: ../src/test/java/com/kniazkov/cpg/TypedConstantFoldingTest.java
[VirtualInheritanceValidation]: ../src/main/java/com/kniazkov/cpg/analysis/VirtualInheritanceValidation.java
[VirtualInheritanceValidationTest]: ../src/test/java/com/kniazkov/cpg/VirtualInheritanceValidationTest.java
[casts-memory]: ../src/test/resources/cpp03/casts-memory/sources
[classes-initialization]: ../src/test/resources/cpp03/classes-initialization/sources
[conditional-catalog]: ../src/test/resources/cpp03/conditional-catalog/sources
[diagnostic-provenance]: ../src/test/resources/cpp03/diagnostic-provenance
[functions-defaults]: ../src/test/resources/cpp03/functions-defaults/sources
[inheritance-virtual]: ../src/test/resources/cpp03/inheritance-virtual/sources
[lexical-phases]: ../src/test/resources/cpp03/lexical-phases/sources
[literals-expressions]: ../src/test/resources/cpp03/literals-expressions/sources
[macros]: ../src/test/resources/cpp03/macros/sources
[manifest]: ../src/test/resources/cpp03/scenarios.txt
[overloads-adl]: ../src/test/resources/cpp03/overloads-adl/sources
[platform-declarators]: ../src/test/resources/cpp03/platform-declarators/sources
[scopes-linkage]: ../src/test/resources/cpp03/scopes-linkage/sources
[statements-exceptions]: ../src/test/resources/cpp03/statements-exceptions/sources
[template-instantiation]: ../src/test/resources/cpp03/template-instantiation/sources
[templates-dependent]: ../src/test/resources/cpp03/templates-dependent/sources
