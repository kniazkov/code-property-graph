# C++03 library acceptance audit: clauses 17–27

[Русская версия](cpp03-library-audit.ru.md)

## Verdict and scope

This is an evidence audit for issue #48, not a claim of full ISO C++03 conformance.
The initial inspected baseline is `9420887` (`cpp03-base48`). The subsequently
supplied original 2003 text corrects the initial catalogue-based count: Table 11
requires **33 C++ headers**, including deprecated `<strstream>`, and Table 12 adds
18 C-prefixed headers, for **51 required headers**. The catalogue recognizes only
50 of those plus 18 deprecated C-header spellings (68 of 69 spellings). `<strstream>`
is missing. Header recognition alone would not prove complete overload resolution,
macros, behavior, or normative compliance even after this omission is fixed.

The catalogue is substantially broader than the behavior model. Every callable in
it is a declaration without a body. Selected vector/container/string/iterator/
algorithm operations acquire common graph semantics; other resolved library calls
remain conservative. A successfully parsed declaration, a resolved call, a complete
CFG/DFG, and a native program with the expected result are four different claims.
In particular, `FunctionFlow.complete()` does not mean complete STL semantics.

**Known gaps:** mandatory target macro definitions are not supplied; many declared
overloads have no independent CPG call test; most library families have no behavior
summary; file-stream and stream-buffer operations lack dedicated CPG acceptance;
source inspection does not fill the implementation and regression gaps recorded below.
Unknown ABI representations and conservative effects are intentional safeguards,
not proof that a missing facility is implemented. These gaps prevent a claim of
full library support but do not prevent completion of this audit.

## Edition and source discipline

The normative target is [ISO/IEC 14882:2003, second edition](https://www.iso.org/standard/38110.html).
The user subsequently supplied the actual 2003 PDF. Its cover identifies the
second edition, 2003-10-15, with ANSI adoption dated 2003-12-29. The PDF has 786
pages, including the printed body/index through page 757. Independently checked
SHA-256: `6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
The earlier source-unavailable/zero-reviewed-paragraph finding is superseded.
Only independently written paraphrases, locators and evidence are published;
the supplied PDF and extracted text are not repository artifacts.

The [paragraph ledger](cpp03-paragraphs/clauses-17-22.jsonl) contains source-inspected
records for all **1,382 currently inventoried locators in clauses 17–22**:
1,067 numbered paragraphs (including four letter-suffixed paragraphs), 158
unnumbered definitions/declaration blocks, 53 tables, 103 footnotes and one
detached-number anomaly. Twelve false grammar candidates were rejected as prose.
Footnotes use global identifiers and explicit call-site metadata, rather than
assuming that the last heading on their page owns the note.

The implementation-evidence outcomes are **1,226 gaps, 29 partial and 127 not
applicable**. There are no fully verified implementation rows. Each record has a
rule paraphrase, a proposed example or justified non-executable case, expected
behavior, stage states and scoped evidence or a gap. Proposed examples were not
run merely by recording them. Broad family-test references establish only the
limited declaration/resolution evidence stated in the gap, not every associated
paragraph. The 127 non-applicable outcomes cover explanatory/organizational
material, including footnotes and the detached number, not missing semantics.

A source-inspected row with a documented gap is a completed source review, not an
implemented requirement. Large tables and paragraphs can contain several
obligations, so locator coverage is not an exhaustive normative-rule denominator.
Inventory counts, source review, exact test evidence and implementation coverage
remain separate. No full-conformance percentage is inferred from these counts.
Other clause ledgers are coordinated separately in the [language audit](cpp03-language-audit.en.md).

[WG21 N1638, 11 April 2004](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1638.pdf)
is a later working draft. The repository's [reference note](standards/cpp/cpp03/README.ru.md)
links a tracked PDF whose working-tree file is **not materialized here**
(`git ls-files -v` marks it skip-worktree). This is a checkout/restoration boundary,
not a missing repository artifact. Its recorded hash is not a local-byte
verification here. [N1578](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1578.html)
and [N1639](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1639.html)
document changes after the 2003 edition. In particular N1639 discusses LWG 44
(character/EOF comparison), 389 (declaration correction), and 395 (a note made
normative). They must not be silently counted as original-2003 requirements.
There is no complete declaration-to-defect-report provenance map in this audit.
A confirmed deliberate difference is `list::splice`: printed §23.2.2.4 paragraphs
4, 7 and 12 invalidate moved-element iterators/references, whereas the implemented
model preserves them and transfers ownership under the corrected
[LWG 250](https://cplusplus.github.io/LWG/issue250) equal-allocator contract.
The tested corrected profile is retained; it is not literal printed-2003 behavior.
Likewise, vector erase invalidates at-or-after the erased point in
[DynamicArraySemantics](../src/main/java/com/kniazkov/cpg/analysis/DynamicArraySemantics.java),
whereas printed §23.2.4.3/3 says after the point. This follows the correction in
[LWG 414](https://cplusplus.github.io/LWG/issue414), not the literal original boundary.

The ledger also retains original-source inconsistencies rather than silently
repairing them. For example, §21.3.5.6/12 visibly prints `begin()-i1` as the prefix
length (printed page 402), and several string bounds have both a Requires clause
and an opposing Throws clause. These are recorded as source/profile issues, not
fabricated mandatory compiler diagnostics. The locale synopses contain additional
signature inconsistencies, explicitly identified in their ledger entries.

Clause numbers below organize library families, including C facilities used in
more than one clause. They are not a verified list of every normative paragraph.
Modern GCC/Clang in `-std=c++03 -pedantic-errors` mode are useful independent
implementation checks, but incorporate defect resolutions and cannot substitute
for the original edition. TR1, C99-only and C++11 additions are not acceptance
requirements. Exact language-rule evidence is recorded separately in the language
audit; this file does not reclassify language support as library coverage.

## Evidence keys and what they establish

All Java test links below are under `src/test/java/com/kniazkov/cpg/`.
A method name following a key identifies the exact test, not merely a header include.

| Key | Source and scope |
|---|---|
| D | [StandardLibraryCatalogueTest](../src/test/java/com/kniazkov/cpg/StandardLibraryCatalogueTest.java): `catalogueRecognizesExactlyCpp03HeadersAndCompatibilityNames`, `everyFragmentParsesWithoutHostHeadersOnBothSizeTypePolicies`, `parserNameIndexExactlyMatchesDeclaredNamespaceTypes`. All 68 fragments/dependency sets parse, all callables have no bodies; not all expressions resolve. |
| O | [RemainingLibraryOverloadsTest](../src/test/java/com/kniazkov/cpg/RemainingLibraryOverloadsTest.java), with [explicit overload-count inventory](../src/test/resources/library-catalogue/cpp03-remaining-overloads.tsv). Counts selected owner/name groups, not every signature or instantiation. |
| R | [RemainingLibraryResolutionTest](../src/test/java/com/kniazkov/cpg/RemainingLibraryResolutionTest.java) and [actual CPG inputs](../src/test/resources/library-catalogue/resolved-remaining/). Tests selected declarations, parameters and operators; no callable bodies. |
| C | [CCompatibilityFamiliesTest](../src/test/java/com/kniazkov/cpg/CCompatibilityFamiliesTest.java). Namespace pairs, exact math overloads, selected calls, opaque types, missing macros and exclusion of later APIs. |
| F | [LibraryCatalogueFamiliesTest](../src/test/java/com/kniazkov/cpg/LibraryCatalogueFamiliesTest.java). Selected declaration families and opaque masks; `stdio.h` does not invent `std::printf`. |
| S | [StreamLibraryResolutionTest](../src/test/java/com/kniazkov/cpg/StreamLibraryResolutionTest.java). Narrow/wide string-stream inheritance, I/O iterators, selected manipulators/operators and deduction boundaries. |
| U | [LibraryUnknownEffectsTest](../src/test/java/com/kniazkov/cpg/LibraryUnknownEffectsTest.java). Possible writes through receiver/reference/pointer/exposed/global storage; stream aliases and unsupported temporary lifetime. |
| L | [LazyLibraryGraphTest](../src/test/java/com/kniazkov/cpg/LazyLibraryGraphTest.java). Demanded declaration closure, origins, ownership, source/profile invalidation and per-translation-unit include scope. |
| V | [VectorResolutionTest](../src/test/java/com/kniazkov/cpg/VectorResolutionTest.java), [VectorIteratorResolutionTest](../src/test/java/com/kniazkov/cpg/VectorIteratorResolutionTest.java), [VectorAccessNormalizationTest](../src/test/java/com/kniazkov/cpg/VectorAccessNormalizationTest.java). Selected concrete vector calls, iterators, capacity/invalidation facts; proxy/nontrivial boundaries. |
| K | [ContainerIteratorResolutionTest](../src/test/java/com/kniazkov/cpg/ContainerIteratorResolutionTest.java), [ContainerNormalizationTest](../src/test/java/com/kniazkov/cpg/ContainerNormalizationTest.java), [ContainerFamilyFlowTest](../src/test/java/com/kniazkov/cpg/ContainerFamilyFlowTest.java). Deque/list/associative/adaptor operations and storage effects. |
| Q | [StringAlgorithmResolutionTest](../src/test/java/com/kniazkov/cpg/StringAlgorithmResolutionTest.java), [SequenceNormalizationTest](../src/test/java/com/kniazkov/cpg/SequenceNormalizationTest.java), [SequenceFlowTest](../src/test/java/com/kniazkov/cpg/SequenceFlowTest.java), [SequenceModelTest](../src/test/java/com/kniazkov/cpg/SequenceModelTest.java). Selected string/range algorithms, callback/output effects, graph copy/invalidation. |
| N | [Native signature script](../scripts/probe-cpp03-library-catalogue.sh): [containers](../src/test/resources/library-catalogue/containers.cpp), [remaining C++ signatures](../src/test/resources/library-catalogue/remaining-cpp-signatures.cpp), [C signatures](../src/test/resources/library-catalogue/c-compatibility-signatures.cpp), [legacy C signatures](../src/test/resources/library-catalogue/c-legacy-signatures.cpp), and R's input files. Syntax-only except two benign programs; host-STL checks, not CPG resolution. |
| B | Native-only [benign C program](../src/test/resources/library-catalogue/c-benign-runtime.cpp) and [stream formatting program](../src/test/resources/library-catalogue/c-stream-signatures.cpp). Actual output/value checks. In particular B's `assert`/`va_*` use host macros, not built-in CPG macros. |
| E | [Cpp03CorpusIT](../src/test/java/com/kniazkov/cpg/e2e/Cpp03CorpusIT.java): `lazy-library`, `vector-semantics`, `vector-access`, `container-families`, `strings-algorithms`, `remaining-library` scenarios. Original/generated/folded sources, graph/JSON/DOT/SVG checks and strict GCC/Clang native checks for these inputs. Not one scenario per header. |

Declaration implementations: [StandardLibraryCatalogue](../src/main/java/com/kniazkov/cpg/analysis/StandardLibraryCatalogue.java),
[StandardLibraryC](../src/main/java/com/kniazkov/cpg/analysis/StandardLibraryC.java),
[StandardLibraryStreams](../src/main/java/com/kniazkov/cpg/analysis/StandardLibraryStreams.java).
The exact declarations there remain the source of truth; signatures below are
**audited selections**, not an exhaustive replacement synopsis. Type/template
indexes are checked separately from declarations and from resolution.

Effects: **opaque** means [LibraryCallEffects](../src/main/java/com/kniazkov/cpg/analysis/LibraryCallEffects.java)
retains possible accesses, not “pure,” “no throw,” or “has known implementation.”
**common** means a supported case in [ContainerSemantics](../src/main/java/com/kniazkov/cpg/analysis/ContainerSemantics.java)
or [SequenceSemantics](../src/main/java/com/kniazkov/cpg/analysis/SequenceSemantics.java),
with the named V/K/Q evidence. Other cases fall back to ordinary C++.
Generation retains source calls/includes rather than synthesizing an STL
implementation; E checks only its selected programs.

## The 33 required C++-specific headers (32 catalogued)

Every catalogued row has D's fragment-parse evidence; the missing strstream row does not. A D-only entry explicitly lacks a
header-specific resolution/behavior test. Template parameter names are abbreviated;
`C`/`T` mean character/value type, not a claim that arbitrary user types work.

| Header; clause family | Audited declarations / selected signatures | CPG, effects and generator evidence | Explicit remaining boundary |
|---|---|---|---|
| `algorithm`; 25 | C++03 `void rotate`, `void fill_n`, `void generate_n`; `copy`, `find_if`, unary `transform`, `sort`, `remove`; generic `swap(T&,T&)` belongs here. | D.`cxx03AlgorithmReturnTypesDoNotBorrowCxx11Signatures`; Q.`iteratorArgumentsAndUserFunctorsSurviveOwnedInstantiation`, `algorithmsRetainRangesOutputsCallbacksAndAccumulator`, `removeAndSortMutateRangeWithoutSizeChange`; U.`opaqueReferenceCallMayWriteArgumentsAndKeepIncomingDefinitions`; E `strings-algorithms`. Common selected ranges/callbacks; other algorithms opaque. | No per-overload CPG proof for full search/partition/merge/set/heap/permutation catalogue. Invalid categories/const outputs stay raw (Q), not fully diagnosed. No complexity proof or general ordering/precondition checker. |
| `bitset`; 23 | Constructors (3 primary groups), `set/reset/flip` (2 each), mutable proxy/const `operator[]`, explicit `to_string<C,Traits,Alloc>()`, `to_ulong`, count/tests and bit operators. | O; R.`bitsetOverloadsAndExplicitStringTemplate` (14 calls), `bitsetOperatorTargets` (13); N `bits`. Effects opaque. | No bit-value/overflow/position-exception model, no dedicated generated bitset runtime scenario. Counts do not prove all template arguments. |
| `complex`; 26 | Primary plus float/double/long-double specializations; `pow(complex,int)`, complex/scalar, complex/complex, scalar/complex; arithmetic, real/imag and transcendental functions. | O; R.`complexScalarAndComplexPowerOverloads` (16 calls), `complexOperatorTargets` (28); N `complex_specializations`. Opaque. | No numeric accuracy/domain/branch-cut model or complete specialization conversion/stream runtime proof. |
| `deque`; 23 | `push_front/back`, `pop_front/back`, index/at/front/back, concrete random-access iterators; constructors/modifier/relational declarations. | K.`sequencesSelectConcreteOperationsAndRespectIteratorCategories`, `dequePushPreservesReferencesButNotIteratorProofs`; E `container-families`. Common selected mutations/access. | No exhaustive constructor/range/allocator/exception-guarantee coverage. Deque reference and iterator invalidation deliberately differ. |
| `exception`; 18 | `exception`, `bad_exception`, copy/assignment/what; handler typedefs, `set_terminate`, `set_unexpected`, `terminate`, `unexpected`, `uncaught_exception`. | O; R.`exceptionHandlersAndTypeInfo` (8 calls across exception/new/typeinfo); N function pointers/`exceptions`. Opaque. | Handler installation/termination is not executed; no library handler runtime or all throw-specification outcome model. |
| `fstream`; 27 | Catalogue `basic_filebuf`, `basic_ifstream/ofstream/fstream`, narrow/wide aliases; `open`, `close`, `is_open`, `rdbuf`, protected buffer hooks. | D only for this interface. B includes the header but does not exercise file-stream operations. Opaque. | Dedicated CPG file-open/seek/close overload tests and original/generated file-I/O scenarios are absent. Header inclusion is not operation acceptance. |
| `functional`; 20 | Arithmetic/comparison/logical functors; negators; bind1st/bind2nd; ptr_fun (2), mem_fun (4), mem_fun_ref (4); const/nonconst adapters. | O; R.`functionalAdapters` (14 calls); N `functional`; Q callback links retain possible effects. Opaque outside proven common callback use. | No blanket purity or every adapter instantiation proof; stateful/custom callbacks require conservative effects. |
| `iomanip`; 27 | `setiosflags/resetiosflags`, `setbase`, `setfill<C>`, `setprecision`, `setw`; unspecified result types. | S.`parameterizedManipulatorsResolveWithoutRepresentationAssumptions`, `setfillRetainsItsCharacterTypeAndDoesNotInventExtraction`; B formatting; E `remaining-library`. Opaque. | Wrong-character/inapplicable setfill is not normalized; unspecified proxy representation is not ABI knowledge. No general formatting simulator. |
| `ios`; 27 | `ios_base` flags/state/locale/callback/storage, `basic_ios`; `clear`, `rdstate`, `exceptions`, `fill`, `width`, `precision`, `flags`. | S.`narrowAndWideStringStreamAliasesExposeInheritedStateAndPosition`; U.`streamStateMutatorIsAnOpaqueReceiverWrite`; F.`bitmaskTypesDoNotPretendToBeIntegralAbiTypes`; E `remaining-library`. Opaque. | No full callback/xalloc/iword/pword/Init/failure behavior or numeric flag encoding; native checks only selected states. |
| `iosfwd`; 27 | Stream template forwards and char/wchar aliases, `fpos<State>`, opaque stream offsets; string streams keep allocator parameter. | D.`stringStreamsRetainTheirThirdAllocatorParameter`; S.`defaultsFromForwardDeclarationsSubstituteTheirOwnParameters`; B type declarations. Type evidence; dependent stream calls opaque. | Forward declaration visibility is not complete fpos arithmetic/state or stream-family resolution. |
| `iostream`; 27 | Eight narrow/wide standard stream objects. Selected `cout << value << endl`, `cin >> value`. | [LibraryConsoleTest](../src/test/java/com/kniazkov/cpg/LibraryConsoleTest.java).`catalogueBoundCoutAndEndlNormalize` / `userNamespaceWithSameSpellingDoesNotNormalize`; U.`opaqueLibraryOperatorMayChangeItsReceiverReferenceArgumentAndAccessibleData`. Narrow proved console form is common; other calls opaque. | No full eight-object initialization/tie/sync behavior. User lookalikes never acquire console facts. |
| `istream`; 27 | `basic_istream`, sentry, formatted/unformatted input and positioning, basic_iostream, character/pointer/manipulator extraction. | S inherited input/iterator cases; U.`chainedStringStreamExtractionRetainsBothOutputAndReceiverEffects`; E `remaining-library` reads values, EOF/fail, clear and reread. Opaque. | Exhaustive get/getline/read/putback/ignore/sentry overload and exception behavior not tested; post-2003 LWG changes need provenance. |
| `iterator`; 24 | Traits/tags, pointer specializations, reverse/insert/I/O adapters; `advance(I&,D)`, `distance(I,I)`; begin/end-derived concrete container iterators. | Q pointer/reverse distance and saved output adapters; S.`inputAndOutputIteratorFamiliesResolveTheirOperators`; U saved/copied/reassigned stream-iterator alias tests; E strings/streams. Common ordinary ranges; I/O opaque. | Invalid categories and unrelated iterator types do not obtain proved operations. No universal iterator-law checker; istreambuf postfix result remains opaque. |
| `limits`; 18 | `numeric_limits<T>` plus 14 scalar specializations, eight value functions and static properties; round/denorm enums. | O.`numericLimitsPreserveUnknownTargetConstantsAndAllScalarSpecializations`; R.`numericLimitsSpecializationTargets` (5 calls); N `scalars`. Opaque functions; constants uninitialized. | Target min/max/NaN/epsilon/radix values are not provided or folded; no ABI/format inference or all member evaluation proof. |
| `list`; 23 | Bidirectional iteration; insert/erase/front/back; three splice forms; remove/unique/merge/sort/reverse declarations. | K.`listSpliceTransfersStorageWithoutCopying`, `allAndRangeSpliceRetainTheirWrittenBounds`, `recordSplicePreservesStorageWithoutCopyOrDestruction`, conditional/empty/end cases; E `container-families`. Common selected transfers/mutations. | General range membership/allocator compatibility/merge/sort/exception behavior unproved; iterator arithmetic stays unsupported. |
| `locale`; 22 | locale/facet/id; `use_facet<T>`, `has_facet<T>`; ctype, codecvt, numeric/collation/time/money/messages and byname families. | O counts, including num_get 9 and num_put 6; R.`localeFacetsAndCharacterOverloads` (23 calls); F facet family checks; N `facets`, `numeric_time_money`. Opaque. | No runtime locale mutation/catalogue/facet lifetime/collation or conversion-state semantics; no dedicated ill-formed facet negative test. |
| `map`; 23 | map/multimap; `operator[]` only for map; find/bounds, insert unique pair versus multi iterator, erase/observers/comparison declarations. | K.`associativeIteratorsPreserveConstKeysAndMappedReferences`, `mapLookupAndSubscriptHaveDifferentEffects`, `mapScalarPairInsertProducesValueResult`, mapped-reference DFG; E `container-families`. Common selected operations. | No exhaustive comparator/allocator/hint/range/exception proof; lookup possible-end is not asserted to be a valid element. |
| `memory`; 20 | allocator address const/nonconst, allocate/deallocate/construct/destroy; allocator<void>/rebind; auto_ptr get/release/reset/conversions; temporary buffers and uninitialized algorithms. | O; R.`allocatorOwnershipAndUninitializedAlgorithms` (17 calls, address parameter constness); U.`allocatorReturnedPointerKeepsPossiblePointeeWrites`; N `utilities`. Opaque. | No full ownership transfer, allocation failure, construction rollback, raw-storage/uninitialized lifetime summary. Successful resolution is not memory safety. |
| `new`; 18 | 12 global new/new[]/delete/delete[] overloads: ordinary, nothrow, placement; bad_alloc, nothrow_t, new_handler. | D.`allocationOverloadsStayInTheGlobalNamespace`; O; R.`allocationOverloadTargets` selects all 12; N exact function-pointer types. Opaque. | Catalogue acceptance is not library allocation execution, failure behavior, placement lifetime, or exception guarantee proof. |
| `numeric`; 26 | Two overloads each of accumulate/inner_product/partial_sum/adjacent_difference. Selected `accumulate(first,last,0L)`. | D; Q.`iteratorArgumentsAndUserFunctorsSurviveOwnedInstantiation`, `algorithmsRetainRangesOutputsCallbacksAndAccumulator`; E `strings-algorithms`. Common selected accumulation. | No independent CPG proof for all binary-op overloads or the other three algorithm families, no general overflow/ordering/complexity proof. |
| `ostream`; 27 | `basic_ostream`, sentry, value/character/pointer insertion, put/write/flush/seekp/tellp; endl/ends/flush. | S.`characterInsertionUsesSpecializedOperatorTemplates`, inherited positioning; U operators; B exact const-void-pointer member signature; E `remaining-library`. Opaque except narrow console normalization. | No exhaustive sentry/unformatted/failure/flush semantics. String streams do not become console print nodes. |
| `queue`; 23 | queue and priority_queue; front/back versus const top, push/pop/size/empty; queue comparisons, no priority_queue equality. | K.`adaptersAndCustomComparatorsRetainSelectedTargets`, `adapterOrdersAndPriorityMutabilityAreDistinct`, `adapterReferencesDifferFromCopiesAndPopWrites`; E `container-families`. Common selected ordering/access. | No full custom underlying-container/comparator exception or all constructor proof; no later adaptor swap. |
| `set`; 23 | set/multiset, immutable keys; unique pair versus multi iterator insertion; find/bounds/equal_range and observers. | K associative iterator tests, `customComparatorHasSelectedCallAndNoInventedComparisonCount`, `possibleEndResultsAreNotDefinitelyInvalidatedByClear`; E `container-families`. Common selected operations. | No universal comparator ordering proof, exhaustive equal_range/hint/range/allocator or exception model. |
| `sstream`; 27 | basic_stringbuf and basic_i/o/stringstream with three template parameters; constructors/str/rdbuf and inherited stream state. | S narrow/wide state/position; U stream extraction/materialization/aliases; E `remaining-library` original/generated/folded value and state checks. Opaque. | Discarded/unmodeled record-return lifetime remains explicitly incomplete (U); no exhaustive buffer virtual hook/constructor/locale behavior. |
| `stack`; 23 | stack<T,Container>, top const/nonconst, push/pop/size/empty, comparisons. | K adaptor resolution/order/reference tests; E `container-families`. Common selected LIFO access/mutation. | No every underlying-container/constructor/comparison/exception instantiation proof. |
| `stdexcept`; 19 | Nine exception classes; string constructors, logic_error/runtime_error hierarchy and what overrides. | O; R.`standardExceptionHierarchy` (9 calls); N `exceptions`. Opaque. | No dedicated generated throw/catch/message/lifetime runtime test for all nine, nor exact what contents. |
| `streambuf`; 27 | basic_streambuf public get/put/seek/locale entry points and protected virtual hooks. | D interface parsing; S I/O iterators depend on streambuf types but do not verify every hook. Opaque. | No dedicated CPG public/protected hook selection, custom buffer override or generated buffering/position runtime acceptance. |
| `strstream`; 17 Table 11 / Annex D.7 | Required deprecated header with strstreambuf/istrstream/ostrstream/strstream interfaces. | **Absent from StandardLibraryCatalogue**. The existing test asserts only 50 canonical names and therefore does not establish the original Table 11 inventory. | Entire required declaration family missing; no parser/resolution/effect/generator acceptance for this header. |
| `string`; 21 | char_traits specializations; basic_string/ string/wstring; counted/NUL/fill constructors, append/assign/find/substr/access/iteration; full broader member declaration families. | Q.`stringAliasesDemandConcreteMembersAndIteratorTypes`, `explicitCharacterTraitsUseTheSelectedCharacterSpecialization`, counted-NUL/substring/callback/storage tests; E `strings-algorithms`. Common selected strings. | Custom traits/allocators/specializations, many insert/erase/replace/search/comparison overloads and nontrivial temporary lifetimes lack blanket behavior proof. |
| `typeinfo`; 18 | type_info equality/inequality/before/name with inaccessible copy; bad_cast/bad_typeid. | O; R exception/type-info input tests `before/name`; N `exceptions` checks equality/inequality too. Opaque. | Native type_info name strings/order/identity/RTTI failure behavior not simulated; no dedicated CPG resolution assertion for every operator/copy-access failure. |
| `utility`; 20 | pair constructors/types/fields, make_pair(T1,T2), six relational operators. | O; R.`utilityTemplates` (1 call), `pairComparisonTargets` (6); K unique insertion pair flow; N `utilities`. Opaque except separately proved container result structure. | Confirmed declaration gap: original §20.2/1 and §20.2.1 require four generic operators in std::rel_ops, absent from the catalogue. Pair runtime/constructor coverage is also partial; generic swap is not declared here. |
| `valarray`; 26 | valarray, slice/gslice and four proxy families; 10 index groups, 6 assignments, apply by-value/by-const-reference, arithmetic/comparison/math. | O; R.`valarrayApplyAndReductions` (10 calls), `valarrayOperatorTargets` (54), `valarrayMathOverloads` (20); N `arrays`, `array_math`. Opaque. | No numeric/proxy-alias/shape/bounds/lifetime/exception behavior summary or generated runtime proof; implementation expression templates are not layout facts. |
| `vector`; 23 | Primary plus bool specialization; concrete constructors/assign/push/access/capacity/iteration/modifiers, bool reference proxy. | L demand closure; V selected ranges, capacity/invalidation, const elements and bool proxy identities; E `lazy-library`, `vector-semantics`, `vector-access`. Common supported default-allocator cases. | V.`boolRemainsRaw`, `nontrivialAssignmentShiftsRemainRaw`, conditional capacity and user specialization boundaries: no false bool&, shift-lifetime, growth-factor or universal allocator guarantee. |

## The 18 C-prefixed headers and 18 deprecated spellings

Each row separately covers both spellings shown. D and C test both namespaces:
`<c...>` provides the promised `std` names; `<...h>` provides global names.
Optional additional namespace imports are not promised. In these rows **opaque**
means conservative CPG call effects; even B's native arithmetic values are not
CPG constant-evaluation proofs. N's 180 signature selections per spelling are
specified in the source files, not 180 complete family semantics.

| Header / deprecated spelling; clause family | Audited declarations or macro boundary | Exact evidence and effects | Explicit gap / qualification |
|---|---|---|---|
| `cassert` / `assert.h`; 19 | Empty declaration fragment; assert is a macro, not a function. | C.`macroOnlyHeadersDoNotInventObjectsOrFunctions`; N checks macro presence; B executes host assert. No built-in CPG macro effect. | Built-in assert expansion and NDEBUG-dependent behavior absent. Native success does not close this gap. |
| `cctype` / `ctype.h`; 21/22 | 11 int classification functions plus `int tolower(int)`, `int toupper(int)`. | C.`scalarAndCharacterFamiliesResolveUnderBothSpellings`: isdigit/toupper; N all signatures; B startup-C-locale values. Opaque. | No CPG locale/value evaluation or diagnostic for an argument outside unsigned-char/EOF domain; C99 isblank excluded. |
| `cerrno` / `errno.h`; 19 | Empty declaration fragment; no invented errno object or numeric constants. | C.`macroOnlyHeadersDoNotInventObjectsOrFunctions`, `absentTargetMacroDefinitionsRemainUnresolved`; N host presence. | errno and EDOM/ERANGE definitions/value/state modeling absent, not “supported with unknown value.” |
| `cfloat` / `float.h`; 18 | Empty declaration fragment; no guessed FLT/DBL/LDBL target values. | C missing-macro test explicitly leaves FLT_RADIX unresolved; N host macro presence. | Mandatory floating limit/format macros lack target definitions/evaluation. |
| `ciso646` / `iso646.h`; 17 compatibility / 2 tokens | Empty declaration fragment is expected for C++ alternative operator tokens. | C macro-only boundary; B uses `or` and `not` as actual C++ tokens; lexer tests belong to language audit. | Not a missing callable family; no C-style macro definitions invented. Header recognition alone does not prove lexer coverage. |
| `climits` / `limits.h`; 18 | Empty declaration fragment; no guessed CHAR/INT/LONG limits. | C missing-macro test leaves INT_MAX unresolved; N macro presence. | Mandatory integer limit macros lack target definitions/evaluation. |
| `clocale` / `locale.h`; 22 | lconv public C90 fields, `char* setlocale(int,const char*)`, `lconv* localeconv()`. | C both-spelling localeconv call; N exact pointers; F broad inventory. Opaque. | LC_* target macros, actual locale mutation and lconv values/storage not modeled. No generated locale runtime test. |
| `cmath` / `math.h`; 26 | float/double/long-double families; abs/fabs/sqrt etc.; `pow(T,int)` versus `pow(T,T)`; frexp/ldexp/modf. | C.`mathAndIntegerOverloadsSelectDistinctParameterTypes`, `cppStringSearchOverloadsPreserveConstness` (also pow/modf signatures); N all listed signatures; B sqrt. Opaque. | HUGE_VAL and math error behavior/accuracy/domain/rounding not modeled; C99 hypot/round excluded. |
| `csetjmp` / `setjmp.h`; 18 | Opaque jmp_buf; `void longjmp(jmp_buf,int)` declaration; selected operand-bearing glibc setjmp backend. | Bounded API/CLI and optimized native acceptance: [separate verification status](cpp03-setjmp-verification.md). | General matching-environment/lifetime/state proof, precise nonlocal CFG/DFG and other runtimes remain open; original ANY automatic objects restriction is retained. |
| `csignal` / `signal.h`; 18 | Opaque integer sig_atomic_t; `void (*signal(int,void (*)(int)))(int)`, `int raise(int)`. | C both-spelling raise; F declarations; N pointers. Opaque. | Signal numbers/handler macros absent; no executed signal installation/delivery or asynchronous effect model. |
| `cstdarg` / `stdarg.h`; 18 | Opaque va_list only; no fake callable va_start/va_arg/va_end. | C both-spelling va_list consumer vprintf; C macro-only boundary; N signatures; B host variadic sum. | va_* expansion absent in portable CPG; no variadic-state lifetime/traversal model. va_copy is excluded as later. |
| `cstddef` / `stddef.h`; 18 | size_t = selected profile size type; ptrdiff_t = its signed counterpart policy. | C.`cstddefTypesResolveUsingTheSelectedProfile`; D both unsigned-long/unsigned-int policies; Q pointer distance. Type-only. | Independent ptrdiff_t ABI choice missing; NULL/offsetof macros absent. Signed-size pairing is a project policy, not ISO's required identity. |
| `cstdio` / `stdio.h`; 27 | Opaque FILE/fpos_t; printf/scanf/variadic forms; file/character/block I/O, seek/status/remove/rename. | C both-spelling printf; N function pointers; U.`cCompatibilityPointerOutputMayWriteExposedLocal` uses scanf; F optional std import negative. Opaque. | EOF/BUFSIZ/SEEK_* and standard stream macros absent; no full format checking/file/environment behavior or generated C I/O acceptance. gets remains a C++03 declaration, not a safety recommendation. |
| `cstdlib` / `stdlib.h`; 18/20/25/26 | div_t/ldiv_t, int/long abs/div; conversion, allocation, process, random, qsort/bsearch, multibyte functions. | C both-spelling abs/labs/atoi and exact int/long selection; N pointers; B div/abs; U distinguishes by-value from exposed storage. Opaque. | EXIT_*/RAND_MAX/MB_CUR_MAX/NULL macros and process/random/allocation/search behavior absent; no C99 long-long APIs. |
| `cstring` / `string.h`; 21 | memcpy/memmove/memset; string operations; mutable/const memchr/strchr/strpbrk/strrchr/strstr. | C both-spelling strcmp/strchr and constness declaration checks; N exact pointers; B strchr/strlen/strcmp. Opaque. | No general buffer extent/overlap/termination/tokenizer/error-text model or full per-call CPG overload coverage. |
| `ctime` / `time.h`; 20 | Opaque arithmetic time_t/clock_t, public tm fields; clock/time/difftime/mktime, conversions/strftime. | C both-spelling mktime; F.`demandedTimeTypeKeepsUnknownUnderlyingRepresentation`; N exact pointers. Opaque. | CLOCKS_PER_SEC/NULL absent; time representation, clock values, timezone and static buffers not simulated. |
| `cwchar` / `wchar.h`; 21/27 | Opaque mbstate_t/wint_t; wide I/O/string/memory/number/time and restartable multibyte functions; mutable/const searches. | C both-spelling wcslen; N exact signatures; B wide length; B stream probe mbrtowc pointer. Opaque. | WEOF/WCHAR_*/NULL absent; conversion/locale/state and full wide CPG overload effects unproved. Host Clang/glibc const-search probe exception is documented below. |
| `cwctype` / `wctype.h`; 21/22 | wint_t plus opaque wctype_t/wctrans_t; wide classifiers/case; iswctype/wctype/towctrans/wctrans. | C both-spelling wctype; N function pointers; B iswdigit. Opaque. | No descriptor/locale/runtime classifier model; WEOF definition absent; C99 iswblank excluded. |

Native qualification: [C coverage note](../src/test/resources/library-catalogue/c-compatibility-coverage.md)
explains that this Clang/glibc combination exposes legacy wide-search declarations.
The exact const `wcschr` check is enabled on GCC; the supported mutable check runs
on both. The legacy source suppresses Clang's include-next extension warning caused
by CPATH. This is not a second independent full const-overload confirmation.

## Clause-level positive / negative / boundary map

“Negative” below means an actual rejection/nonselection assertion. A deliberate
fallback or absent macro is not proof of a standard diagnostic. “None identified”
is a coverage gap, not an implicit pass. The map does not count undefined programs
as positive runtime cases.

| Clause | Positive evidence | Negative evidence | Boundary evidence and missing coverage |
|---|---|---|---|
| 17, library introduction | D all headers/dependencies and declarations; L selected lazy closure. | D rejects array/tuple/thread/unordered_map/cstdint/TR1; F no invented std import from stdio.h. | L `includeAvailabilityBeginsAtTheDirective`, `includesDoNotLeakBetweenTranslationUnits`, `incompatibleProfilesKeepSeparateLibraryIdentities`; all library-wide requirements/implementation freedoms not exhaustively checked. |
| 18, language support | R all 12 allocation signatures, handlers/typeinfo/limits; C size types. | C `c99AndLaterApisAreNotPresentedAsCpp03` excludes later APIs. D allocation namespace assertions are positive placement evidence, not wrong-namespace rejection; general ill-formed-program diagnostics are unproved. | C opaque state/types and bounded va_*/setjmp subsets, not full obligations; O unknown limit constants. No full runtime handler/nonlocal jump acceptance. |
| 19, diagnostics | R nine stdexcept constructors; O what/hierarchy declarations. | C no invented assert/errno functions/constants. No dedicated native-invalid diagnostic-library program identified. | B host assert is separate; NDEBUG and errno/EDOM/ERANGE remain missing CPG macros. |
| 20, utilities | R pair comparisons, allocator calls and functional adapters. | U `ordinaryValueArgumentIsNotAReferenceToTheLocal`, `unevaluatedAddressDoesNotExposeStorage` forbid spurious local alias effects; not language rejection. | U allocator result alias; O exact selected groups; no complete allocator/auto_ptr ownership or raw-storage behavior. |
| 21, strings | Q counted embedded-NUL strings/traits/substr/append, C narrow/wide calls; E strings. | Q `userStringSpecializationKeepsItsOwnMembers`, `lookalikeMethodsNeverAcquireLibrarySemantics` prevent false standard semantics. | Q `substringCopyOverloadsNeverBecomeBufferCopies`, `materializationDoesNotAdmitNestedOrDiscardedStringTemporary`; full string exceptional/alias cases unproved. |
| 22, localization | R 23 selected locale calls, O facet groups, N numeric/time/money signatures. | No dedicated invalid facet/locale CPG or native-negative fixture identified; C excludes later character APIs. | F opaque masks; locales/state/descriptor values not modeled; native signature checks do not execute locale mutation. |
| 23, containers | V/K concrete calls, DFG and invalidation; R bitset calls/operators; E vector/families. | V `constIteratorKeepsConstElementsAndUnrelatedContainersDoNotCompare`; K `associativeConstIteratorsCannotBindWritableReferences`; unsupported list arithmetic has no targets. | V bool/nontrivial shift/capacity fallback; K empty/end/conditional splice and comparator effects. No all-overload/precondition/exception-guarantee proof. |
| 24, iterators | Q pointer/reverse distance and saved adapters; S I/O iterator operators; E strings/streams. | S `iteratorDifferenceAliasDoesNotMergeUnrelatedBaseDeclarations`; V unrelated iterators; K category exclusions. | U saved/copied/reassigned I/O iterator state; Q unknown adapter rebinding retains possible writes. Iterator-law/invalid-range proof incomplete. |
| 25, algorithms | Q copy/find_if/transform/sort/remove; C qsort/bsearch declarations; E strings. | Q `invalidCategoriesAndUnevaluatedCallsStayRaw`, `readOnlyRangesAndOutputsNeverAcquireMutationFacts`. Fallback is not full semantic rejection. | Q remove is not physical erase; callbacks may affect globals; most algorithm overloads and complexity/exception contracts not independently covered. |
| 26, numerics | R complex/valarray/limits selections; Q accumulate; C math overloads; B benign scalar values. | C no hypot/round/long-long additions; no dedicated numeric-domain/valarray-shape negative test identified. | O unknown target constants; valarray expression/proxy representation and numerical error/accuracy are unmodeled. |
| 27, input/output | S state/manipulator/operator resolution; U aliases; E `remaining-library` values/EOF/fail/clear and round trip. | S `setfillRetainsItsCharacterTypeAndDoesNotInventExtraction`; LibraryConsoleTest rejects lookalike cout/endl normalization. | String streams never console nodes; discarded returned string lifetime incomplete; fstream/streambuf specific acceptance absent. |

## Undefined, unspecified, implementation-defined, and unimplemented

These categories must remain separate in acceptance reports:

- **Undefined behavior (UB):** dereferencing end/invalidated iterators, invalid
  ranges or buffer extents, some overlap violations, and calls outside library
  argument domains are not portable runtime oracles. Declaration-only N inputs
  intentionally contain calls such as null handler pointers, placeholder ranges,
  or uninitialized pointers solely for signature selection; the script never
  executes them. No result is claimed for them. A CPG may retain syntax or a
  target without proving preconditions. Full UB detection is not implemented.
- **Unspecified behavior/representation:** where the standard allows a choice,
  the graph must not select a host ordering, proxy layout or adaptor result
  representation without evidence. Opaque manipulator/proxy/bitmask identities
  preserve this boundary. “Opaque” can also stand for unavailable knowledge; it
  does not establish which normative category applies to every individual type.
- **Implementation-defined:** target size/difference types, numeric ranges and
  formats, time/stream representations and related documented implementation
  choices require a target profile or actual definitions. Opaque types prevent
  guessed layouts, but absence of required macro definitions is still a support
  gap. The signed-size ptrdiff_t policy is explicitly narrower than all conforming
  targets. This audit does not assume a container growth factor or ABI size.
- **Implementation limitation:** unresolved mandatory macros, an absent test,
  unmodeled record-return lifetime and conservative unknown effects are project
  limitations. They must not be relabeled “unspecified by ISO.” Conversely,
  preserving conservative effects is preferable to false purity/alias/lifetime
  guarantees and need not imply rejection of otherwise valid source.

## Reproducible checks and audit-run result

Recommended repository commands:

```sh
mvn -Dtest=StandardLibraryCatalogueTest,RemainingLibraryOverloadsTest,RemainingLibraryResolutionTest,CCompatibilityFamiliesTest,StreamLibraryResolutionTest,LibraryUnknownEffectsTest,LibraryCatalogueFamiliesTest,LazyLibraryGraphTest,LibraryConsoleTest,VectorResolutionTest,VectorIteratorResolutionTest,VectorAccessNormalizationTest,ContainerIteratorResolutionTest,ContainerNormalizationTest,ContainerFamilyFlowTest,StringAlgorithmResolutionTest,SequenceNormalizationTest,SequenceFlowTest,SequenceModelTest test
bash scripts/probe-cpp03-library-catalogue.sh
python3 scripts/check-cpp03-paragraphs.py
mvn verify
```

2026-11-06 audit run: Maven/javac were unavailable in this environment. Main and test
sources were compiled in a separate temporary output directory with Eclipse ECJ
(Java 21 target), then the listed 19 test classes were run with JUnit Console and
`-Xmx512m`. **203 tests passed, 0 failed, 0 skipped**. The native signature script **passed with GCC 14 and
Clang 19**, including its two benign runtime programs. Clang used the documented
GCC-header CPATH setup. These are focused checks, not a fresh full `mvn verify` or
all E scenarios. The final aggregate acceptance report owns the full-suite result.

## Follow-up required before any full-library claim

1. Resolve the documented source/profile differences and identify adopted corrections
   explicitly; keep source-locator review separate from implementation acceptance.
2. Implement/profile required macros and add positive, negative and boundary CPG
   tests; do not count host-header macro presence as built-in support.
3. Expand exact per-overload CPG resolution tests beyond the selected R/O/V/K/Q/S
   cases, especially file streams, streambuf hooks and currently opaque families.
4. Add behavioral and generated-runtime evidence only where semantics are actually
   modeled, including exceptional paths, iterator/range validity and lifetimes.
   Keep external effects conservative elsewhere.
5. Continue to report declaration, parser, resolution, effects, generation and test
   coverage separately. No row in this audit warrants an unqualified “complete.”


## Follow-up #90: strstream declarations

The original audit above records the catalogue at audit time. The later #90
implementation adds the missing header/four class interfaces; current inventory
is 51 core headers / 69 spellings. This does not close the documented D.7 runtime
contract gaps or general acceptance #48. See [bounded verification](cpp03-strstream-verification.md).


## Follow-up #91: std::rel_ops

The historical omission identified above is addressed by four nested, opt-in
operator templates. Scoped lookup, template selection, lazy ownership and
original/generated native truth tables have targeted regressions. The catalogue
still supplies no STL bodies or proof of user-type prerequisites; 20.2.1/10 and
general acceptance remain bounded gaps. [Verification](cpp03-rel-ops-verification.md).

## Follow-up #92: profile-backed macros, first stage

The historical macro gaps above are partially addressed: CPG now supplies NULL,
integer limits, explicit-format floating limits and WCHAR_MIN/MAX under both
header spellings. The [dedicated inventory](cpp03-library-macros.json) separates
54 supplied header/name obligations from 48 still open under #92. Native macro
presence is not counted as CPG implementation. The [verification report](cpp03-library-macros-verification.md)
records target assumptions, exact evidence and remaining ABI/runtime/assert
work. No complete-library claim or closure of #48 follows from this packet.

## Follow-up #92: assertion binding

The historical cassert gap now has a CPG-owned function-like macro, NDEBUG
reselection, an explicit glibc runtime binding, and an unavailable-binding
boundary for other profiles. This is not a portable implementation of libc or
proof of every historical expression-type case. Copying and unrelated edits
are supported; condition edits through the expanded graph are rejected pending
safe re-stringization. [Verification](cpp03-assert-verification.md).

## Issue #92 continuation: integer target facts

The historical missing-value rows above now have a bounded implementation for
30 integer object macros. Their types and values come from explicit profile
facts; missing facts remain unavailable. This does not implement errno storage,
locale changes, signal delivery, standard streams, random generation or process
termination. The updated inventory separates 54 profile definitions, one assert
runtime binding, 30 target-fact obligations and 17 still-open obligations.
[Verification and target assumptions](cpp03-runtime-integer-macros-verification.md).

## Issue #92 continuation: mutable errno

The historical absent-errno row now has an explicit glibc runtime binding through
its genuine accessor and an object-like mutable-lvalue macro. Error-number facts
remain separately selected. Unknown runtime is diagnosed on use, not replaced
with an invented global int or zero. Exact TLS identity and complete library
error-state semantics remain beyond this bounded packet. [Checks](cpp03-errno-verification.md).

## Issue #92 continuation: live multibyte limit

The historical absent-MB_CUR_MAX row now has a bounded opt-in glibc accessor
binding, separate from prior numeric/null and integer-fact packets. Both header
spellings retain an object macro and ordinary declarations. The query is an
unknown scalar rvalue, not a constant locale model. Exact size_t belongs to the
selected glibc contract, not original C90 wording. The earlier table and counts
are historical; the stage-five inventory had 15 obligations/14 names open.

Only the explicitly validated glibc-x86_64-lp64-mb16 contract is supplied; naming
glibc alone does not establish this ABI or enable the query. Complete locale
conversion semantics and generic scalar-call rvalue diagnostics remain outside
this packet. #92 and #48 remain open. [Verification](cpp03-mb-cur-max-verification.md).

## Issue #92 continuation: standard streams

The historical missing-stream-macro rows now have an explicit bounded binding
for stdin/stdout/stderr. Exact self-referential macros and genuine global
C-linkage FILE* objects share canonical opaque FILE and compatible external
identity without losing header provenance. The unavailable default and selected
LP64 contract remain separate from portable C90 expression guarantees.

At stage six the inventory had 90 bounded obligations/84 names and 12 open
obligations/11 names. Eighteen new unit tests and three GCC/Clang native cases
passed, including actual CPG materialization and copied/edited/transformed
sources. Full-suite and exact-head CI completion are separate pending gates.
No FILE layout, constant addresses/descriptors, complete buffer/position/error/
close semantics or generic qualified-name diagnostic coverage follows.
Earlier tables and verification reports retain their historical scope.
[Evidence and remaining gaps](cpp03-standard-streams-verification.md). #92 and #48 remain open.

## Configurable wint_t and WEOF (#92, seventh stage)

The coupled `wideInteger` object supplies `wintType`, decimal-string `weof` and
`characterDomain: "nonnegative-wchar"`. Missing or empty settings retain opaque
wint_t and unavailable WEOF; actual headers remain authoritative. The supported
types are int, unsigned int, long and unsigned long. Both wide header families,
wide signatures and char_traits use the configured canonical type. No host or
libc-family fact is inferred.

The selected type must cover the entire nonnegative wchar_t range; WEOF must
fit that type and lie outside that envelope. This deliberately stronger policy
is not ISO's character-repertoire requirement. Signed -1 is permitted, including
when wchar_t can represent it; WEOF need not equal EOF. Type-preserving constant
expressions support #if. Synthetic profiles do not authorize mismatched native
wide-library calls or claim a complete repertoire/locale model.

At stage seven the bounded inventory had 92 obligations/85 names, with 10 obligations/10
names open. Focused implementation checks support the two WEOF entries; all four WEOF native cases passed. Expanded
regression, full-suite and exact-head CI results are separately tracked
in the [verification report](cpp03-weof-verification.md). Prior stage counts and
reports remain historical. #92 and #48 remain open.

## Clock-scale target facts (#92, eighth stage)

Optional `clockScale` couples independent `clockType`, `expressionType` and
positive decimal-string `value` facts. Each type supports int, unsigned int,
long or unsigned long; value must fit expressionType, not necessarily clockType.
Missing/empty settings retain opaque clock_t and unavailable CLOCKS_PER_SEC.
Actual headers remain authoritative; no libc/host/JVM facts are inferred.

Original C90 describes arithmetic clock_t and a scale giving the number per
second of clock() values. It does not impose later C99 exact-clock_t or constant
wording. The chosen typed-literal subset supports #if without making that a
universal historical guarantee. clock() remains an opaque runtime call.
At stage eight the inventory had 93 bounded obligations/86 names and nine open obligations/
nine names. Expanded focused acceptance passed 269 units and 28 integration tests; full verification
and exact-head CI remain separately tracked in the [report](cpp03-clock-scale-verification.md).
Prior stage counts remain historical. #92 and #48 remain open.

## Explicit HUGE_VAL compiler capability (#92, ninth stage)

`hugeVal: {kind: "positive_infinity", emission: "gcc-clang-builtin-huge-val"}`
selects a closed double-positive-infinity expression, with explicit binary64
double format and 64-bit storage. Missing/empty settings remain unavailable;
libc, host or width alone never enable it. Actual headers retain authority.
The selected exact zero-argument builtin also reparses directly under this
capability. Ordinary cmath declarations remain unchanged.

CompilerIntrinsicExpression carries symbolic double/rvalue semantics and JSON,
not a fabricated libc call, numeric JSON infinity or finite BigDecimal surrogate.
No general floating evaluator, finite HUGE_VAL policy or complete IEEE arithmetic
is supplied. Original C90 permits infinity without requiring constant form.
Assignment/increment and bare-name/declaration/qualified misuse are rejected;
address of the evaluated rvalue still has the existing generic CPG diagnostic
gap despite strict native rejection. Integer #if arithmetic is not promised.

Expanded verification passed 432 units and 31 IT, plus eight separate inventory
units (440 distinct units). At stage nine the inventory had 94 bounded
obligations/87 names and eight open obligations/eight names. Full/CI
results remain separate gates in the [report](cpp03-huge-val-verification.md).
Earlier counts remain historical; #92 and #48 remain open.

## Explicit signal dispositions (#92, tenth stage)

`signalDispositionBinding: "glibc-x86_64-lp64"` selects SIG_DFL/SIG_IGN/SIG_ERR
as explicit void(*)(int) casts of 0/1/-1. The unavailable default is independent
of cRuntime. Selection requires glibc and 8-bit bytes / 32-bit int / 64-bit long and pointers, without
sizeType or MB_LEN_MAX coupling. Actual headers retain authority. These pointer
sentinels are separate from positive-int signal-number facts; no fake helper,
integer pointer evaluation or #if guarantee is introduced.

Expanded verification passed 313 units and 22 integration tests, including actual
CPG materialization and safe subprocess save/change/restore with no delivery.
Host typedef-cast SIG_ERR incidentally passes the tested #if; all selected
explicit function-pointer casts reject it. No preprocessing equivalence is claimed.
FunctionType language-linkage identity and catalogue signal C++ linkage remain
open precision gaps; no complete ISO callback or asynchronous runtime model is
claimed. The inventory has 97 bounded obligations/90 names, five open obligations/
five names. [Evidence and pending merge gates](cpp03-signal-dispositions-verification.md).
Prior counts remain historical; #92 and #48 remain open.

## POD/member/layout preparation for offsetof (#92)

Package-private PodLayout now queries conservative POD eligibility, member paths
and union-aware recursive layout under existing explicit target policies. It
returns a justified result or typed failure, not a guessed host offset. The
query is separate from generic aggregate classification and does not broaden
unrelated sizeof behavior. Enum/member-pointer/template layouts and bounds
requiring sizeof(union) remain unsupported; valid C++03 cases outside the subset
are not language errors.

Results exceeding explicit size_t representability return SIZE_NOT_REPRESENTABLE.
This necessary bound does not certify compiler maximum object sizes.
Focused checks passed 226 units and five IT, including an independent native
layout oracle and meaningful field edits. Native host offsetof is not CPG macro
support. No new macro, builtin, parser or node is enabled; offsetof remains open
and unavailable. Inventory totals stay 97 bounded obligations/90 names and five
open obligations/five names. [Evidence and remaining gates](cpp03-offset-layout-verification.md).
#92 and #48 remain open; previous packet reports retain their historical scope.

## Selected offsetof builtin subset (#92, still open)

The explicit `offsetof` object selects emission gcc-clang-builtin, layout
x86_64-sysv-lp64-v1 and a positive decimal supportedObjectBytes ceiling no larger
than 2147483647. Missing/empty settings remain unavailable. Exact scalar sizes,
alignments, size_t and natural-layout policies are checked independently of libc
and floating formats. The ceiling applies to relevant dependencies and describes
a software-supported subset, not a universal compiler maximum.

OffsetofExpression owns type/member/index operands. Snapshot-owned ICE queries
compute validated values while folding preserves the whole expression and its
transitive layout bounds/const initializers for edits. Selected capability use
refuses active pragmas and expanded pragma operators except exact pragma once;
inactive directives remain allowed. Non-POD sizeof dependencies are unsupported
subset cases rather than invalid owners. No replay guard is weakened.

Focused verification passed 325 units and 15 IT; the final eight-unit/two-IT
rerun overlaps those counts. Native checks use actual CPG materialization, not
host macro presence alone. Valid POD enum/member-pointer/template and
sizeof-union cases remain excluded, so offsetof remains open/partial. Counts
stay 97 bounded obligations/90 names and five open obligations/five names.
The preceding preparation section is historical. [Evidence and remaining gates](cpp03-offsetof-builtin-verification.md).
#92/#48 remain open; full Maven and exact-head CI are separate gates.


## Bounded offsetof contexts (#92, partial)

The current packet extends the selected query to already materialized, complete
concrete POD template instances and query-local sizeof dependencies, including
unions. Primary, explicit and partial specialization evidence concerns the
actual selected concrete instances, not general template support. The existing
explicit layout capability and ceiling are unchanged; unrelated generic sizeof
and ABI claims are not broadened.

The implementation preserves written type/non-type arguments, defaults and relevant
specialization-header dependencies through folding so edits can reselect an
instance and recheck its budget. Snapshot ownership, cycle and ceiling checks,
pragma/operator restrictions and source replay remain in force. Named non-type
arguments and dependent defaults that the generic instantiator cannot
materialize remain prerequisite limitations. Non-POD sizeof dependencies remain
valid unsupported cases, not blanket language errors.

The corrected snapshot passed all 1411 unit tests and 13 selected IT in 8:50.
Independent review passed 78 affected units and two native IT; these overlap
the clean run. Full Maven and exact-head CI remain separate pending gates. Identity-preserving syntax
copy fixes template-argument typedef ownership; derived template instances
retain caller argument aliases, excluding primary-template/catalogue
placeholders and aliases with canonical OpaqueLibraryType targets. Dependent
or unresolved typedef target chains remain unsupported.
Array typedef arguments are no longer excluded.
Qualified member aliases with dependent targets and function-pointer template
arguments retain existing generic limitations; resolved namespace aliases pass. Independent checks are not added to expanded-suite counts.
offsetof stays open/partial, with 97 bounded obligations / 90 names and five
open obligations / five names. The preceding builtin section is historical.
[Current evidence and gates](cpp03-offsetof-contexts-verification.md).
#92 and #48 remain open.


## Selected member-pointer storage for offsetof (#92, partial)

The optional memberPointerStorage="itanium-x86_64-lp64-v1" setting independently
selects data-member pointer storage 8/8 bytes and member-function pointer storage
16/8 bytes (size/alignment). A pointer to a function-pointer data member uses
8/8. Omission remains unsupported; ordinary-pointer width, glibc and host
properties do not enable it. No member-pointer value, encoding or call is modeled.

Known incomplete, non-POD or oversized owner bodies and parameter/return class
bodies need not be laid out or fit the query budget. Written type/signature and
template syntax must still resolve; its sizeof/constant dependencies remain
bounded and survive folding. An edit crossing the ceiling is rejected. Owner
identity alone is not unconditional acceptance. Enum storage and bitfields
remain outside this packet; original C++03 enum semantics are unchanged.

The clean gate passed all 1422 units and 15 explicitly selected IT in 09:54,
with zero failures, errors or skips, including 44 fresh native records.
Independent 11-unit/two-IT checks overlap and are not summed. Full Maven and
exact-head CI remain separate pending gates. offsetof remains open/partial; totals stay
97 bounded obligations / 90 names and five open obligations / five names.
[Current evidence and gates](cpp03-member-pointer-layout-verification.md).
Previous sections are historical stages. #92 and #48 remain open.


## Bounded enum storage for offsetof (#92, partial)

Independent enumStorage="gcc-clang-x86_64-default-enum-v1" selects bounded
4/4 or 8/8 byte representation after original range-admissibility checks.
Omission stays unsupported; member-pointer selection does not enable enums.
Original full-underlying-range promotion and the mixed-range restriction stay
unchanged. Modern compiler behavior is comparison evidence, not a new rule.

Pure storage and actual arithmetic/type dependencies are separate: the latter
require original/native promotion agreement. Lexical initializer types and
conditional enum identity survive bounded evaluation; folding retains actual
type dependencies without evaluating runtime initializers. Generic type
semantics are unchanged. Bitfields and residual prerequisites remain outside.

The clean gate passed all 1439 units and 22 selected IT in 19:34, with zero
failures, errors or skips. Independent 79 assertions and 68 JUnit tests overlap
and are not summed. Fresh native evidence covers 82 executions and 40 CPG
materializations. Full Maven and exact-head CI remain separate pending gates.
Counts stay 97 bounded obligations / 90 names and five open obligations / five
names. offsetof remains open/partial; #92 and #48 remain open. Previous sections
are historical. [Current evidence](cpp03-enum-storage-verification.md).


## Named integral template arguments for offsetof (#92, partial)

Resolved const integral names and enum enumerators can supply representable
integral class-template arguments and full-specialization values. Snapshot-owned
facts preserve the declared parameter type; bool conversion remains a truth
conversion. Written arguments and initializer chains remain available for
bounded layout checks and later edits. No new ABI policy is selected.

Nonrepresentable conversions, uncertified specialization patterns and remaining
template prerequisites are explicit unsupported boundaries, not claims that
all excluded programs violate original C++03. Original enum promotion, selected
layout ceilings and optional representation policies remain unchanged. This
package leaves the five outstanding macro obligations incomplete and does not
increase the completed macro counts. [Scope and verification](cpp03-constant-template-arguments-verification.md).


## Named integral partial-specialization patterns (#92, partial)

Top-level nondependent integral constants and enumerators in partial-specialization
patterns use snapshot facts for matching, ordering and redeclaration identity.
Written headers and initializer chains remain dependencies, including unselected
candidates, so edits can reselect after copying or folding. Pointer/reference
identity matching is unchanged. Nested named patterns, dependent parameter types
and signed out-of-range conversions remain uncertified.
The follow-up bool subset maps zero to false and every nonzero integral value
to true, consistently for matching, ordering and redeclaration identity.
Written facts survive copying, folding and edits.
[Boolean conversion evidence and gates](cpp03-boolean-partial-arguments-verification.md).
No profile, ABI, macro inventory count or acceptance status is broadened.
[Current evidence and gates](cpp03-partial-constant-template-arguments-verification.md).
Earlier sections describe historical package checkpoints. #92 and #48 remain open.

### Unsigned template argument conversions (#92, bounded follow-up)

Certified integral facts now convert to unsigned parameters modulo 2^N using
the selected profile width. The same conversion serves ordinary arguments,
defaults, full specializations and top-level partial patterns, including
matching, ordering and repeated declarations. Bool retains its zero/nonzero
rule. Signed out-of-range results, nested named patterns and dependent partial
parameter types remain unsupported. Written initializer/header dependencies,
cycle/ownership checks, layout ceilings and enum compatibility are unchanged.
No ABI policy or macro completion count changes; #92 and #48 remain open.
[Current scope and gates](cpp03-unsigned-template-arguments-verification.md).

### Concrete partial-specialization parameter types (#92, bounded follow-up)

A top-level non-type pattern parameter may now use an integral type made concrete
by the specialization's own preceding type arguments, for example
`template<class T, T N, class U>` specialized as `<unsigned char, fixed, U>`.
The same snapshot conversion serves matching, ordering and redeclaration
identity. Use-site actual types never certify a still-dependent pattern.
Written facts, typed bodies, pointer/reference identity, original enum semantics
and layout/ownership/cycle guards remain unchanged. Nested named patterns,
still-dependent parameter types and signed out-of-range conversions remain
unsupported. This does not add an ABI policy or complete another macro; #92 and
#48 remain open. [Scope and gates](cpp03-concrete-partial-parameter-types-verification.md).

### Nested integral partial-pattern values (#92, bounded follow-up)

Named independent integral values are normalized inside explicitly written
nested class-template-id arguments of a partial specialization, such as
`Box<Key<T, fixed> >`. The nested head must bind to a primary class template
(or a verified instance origin); matching a spelling alone is insufficient.
A value parameter's integral type must be concrete from that nested pattern's
own preceding arguments. Actual use-site types never certify a dependent
pattern. Normalization retains declaration/parameter/typedef identities and
uses the existing bool, unsigned-modulo and representable-signed rules.
Written headers and initializer chains remain dependencies after copying,
folding and edits. Unresolved/dependent/template-template heads, omitted nested
defaults, still-dependent value parameter types, arithmetic deduction and signed
out-of-range conversions remain outside this subset. Pointer/reference identity,
original enum compatibility and layout/cycle/ownership ceilings are unchanged.
Earlier nested-pattern exclusions describe historical checkpoints. No ABI policy
or macro completion count changes; #92 and #48 remain open.
[Scope, tests and gates](cpp03-nested-partial-arguments-verification.md).

### Nested actual integral defaults (#92, bounded follow-up)

Nested actual template-ids now complete trailing independent integral value
defaults from their exact bound primary header visible before use, or verified
instance origin. Matching and outer instance identity share the same effective
arguments while written syntax and default dependencies remain intact.
Literal/const/enumerator defaults support bool, unsigned modulo and representable
signed conversions; a type may become concrete from its own explicit preceding
arguments. Partial patterns with omitted nested defaults, merged/later defaults,
dependent/type/template defaults and unsupported heads stay guarded.
Earlier omitted-default exclusions describe historical checkpoints.
No ABI policy, macro count, section 11 audit or completion status changes;
#92/#48 and the five remaining macro obligations stay open.
[Contract, assertions and separate gates](cpp03-nested-actual-defaults-verification.md).


### Nested partial-pattern integral defaults (#92, bounded follow-up)

A nested primary template-id in a partial pattern may omit trailing independent
integral defaults, for example `Box<Key<T> >` with `template<class T, int N=2> struct Key`.
The defaults must belong to the exact bound primary header (or verified instance
origin) visible at the pattern declaration, not at a later outer use. A value
parameter type must be concrete from that nested pattern's own preceding
explicit arguments; actual-use substitution cannot certify it. Matching,
ordering, redeclaration identity and repeated deduction share effective arguments
without rewriting source syntax. Original const/sizeof/enum dependencies of all
candidate partial headers, including unselected ones, remain layout and folding
guards; conversion to bool or a small unsigned value cannot remove them.

Earlier omitted-pattern exclusions describe historical packages. Dependent,
type/template and merged/later defaults, unresolved/dependent/template-template
heads, arithmetic deduction and signed out-of-range conversions remain outside
this bounded route. Default merging is permitted by C++03 but not certified by
this implementation subset. Defaults in the specialization's own parameter list
remain forbidden. Existing unrelated default routes are preserved. #92/#48 and
all five macro obligations remain open; no section 11 or ABI-policy change.
[Fresh original-edition review, required assertions and separate execution gates](cpp03-nested-partial-defaults-verification.md).


### Nested builtin integral type defaults (#92, bounded follow-up)

Nested actual and partial-pattern class-template-ids may omit directly written,
independent builtin integral type defaults. For
`template<class T, class V=unsigned char, V N=258> struct Key`, `Key<T>`,
`Key<T,unsigned char>` and `Key<T,unsigned char,2>` are equivalent when the
selected profile gives unsigned char eight bits. Pure type defaults such as
`class V=int` are included. A following value parameter obtains its concrete
type from its own preceding effective arguments, including these type defaults;
its independent value expression is still evaluated on its original bound node.
The type argument retains cv identity: `const int` and `volatile int` are not
collapsed into `int` merely to establish integral eligibility.

The exact bound primary and its visibility at the actual use or partial-pattern
declaration remain required. Matching, ordering, redeclaration identity,
repeated deduction and instance identity share contextual effective arguments
without changing written AST/source/JSON. Original dependencies of selected and
unselected candidates remain guarded. Typedef, class, enum, pointer, reference,
array, function and template type defaults are not added by this route, nor are
dependent expressions/types, merged/later defaults, unsupported heads, signed
out-of-range conversions or arithmetic deduction. These are implementation
boundaries, not prohibitions of C++03; existing ordinary routes are preserved.
Earlier exclusions of all type defaults describe historical packages. #92/#48
and the five macro obligations remain open; no section 11 or ABI-policy change.
[Contract, fresh original-edition review and separate execution gates](cpp03-nested-integral-type-defaults-verification.md).

### Bound integral typedef defaults (#92, bounded follow-up)

Nested actual/partial class-template-ids now accept a TYPE default already bound
to a finite global/namespace typedef chain in the same snapshot, ending in a
builtin integral type. For `cfg::Byte` aliasing `unsigned char`, `Key<T>`,
`Key<T,cfg::Byte>` and `Key<T,unsigned char,2>` agree for a type default
`V=cfg::Byte` followed by independent `V N=258` on an eight-bit-char profile.
The original argument, binding chain and cv identity are retained. No new use-site
lookup occurs. Exact-header visibility and per-occurrence preceding effective
arguments still govern completion; shared defaults cannot contaminate each other.

Member/local, foreign, cyclic, unresolved/dependent and non-integral aliases,
merged/late headers, dependent defaults and unsupported heads remain outside this
route. Selected/unselected hidden sizeof/enum dependencies and ordinary legacy
routes are unchanged. Earlier blanket typedef exclusions describe historical
checkpoints. This is a bounded offsetof/POD prerequisite, not template completion.
Section 11/access and ABI policies are unchanged; #92/#48 and all five remaining
macro obligations stay open. [Contract, assertions and separate execution gates](cpp03-nested-integral-typedef-defaults-verification.md).

### Ordinary members beside selected bit-fields (#92, bounded follow-up)

The optional `offsetof.bitFieldStorage="x86_64-sysv-unsigned-v1"` contract
adds layout for named, positive-width `unsigned char` and `unsigned int`
bit-fields no wider than their declared storage, including resolved aliases/cv,
POD structs/unions and nested arrays. It does not make a bit-field an addressable
member or support `offsetof` designating one. The explicit existing x86_64 SysV
LP64 layout and natural alignment remain prerequisites; defaults retain their
previous identities. Missing capability, unnamed/zero/oversized fields and other
bit-field types remain unsupported, not necessarily invalid C++03.

Ordinary members may reuse whole bytes after a partial bit-field. Storage-unit
boundaries and declared-type alignment still matter. This is a bounded layout
query, not a generic `sizeof` extension or host-derived ABI claim. Snapshot,
representability, object-ceiling, pragma and source/dependency guards remain.
Earlier blanket bit-field exclusions describe historical checkpoints. #92/#48
and all five macro obligations remain open.
[Original evidence, contract and separate verification gates](cpp03-bit-field-layout-verification.md).

### Selected unsigned short/long bit-fields (#92, v2 follow-up)

The new explicit `offsetof.bitFieldStorage="x86_64-sysv-unsigned-v2"`
contract extends v1 with named positive `unsigned short` widths 1..16 and
`unsigned long` widths 1..64, including bound aliases/cv. Selected storage and
alignment are 2/2 and 8/8 bytes under the existing LP64 target. V1 keeps its
original unsigned char/int subset and identity; omission still selects no bit-field
layout. Storage-unit containment, ordinary-member offsets, POD unions/nesting
and arrays use the same bounded rules. Bit-field designators remain unsupported.

Unnamed/zero-width, signed/plain, bool/enum, oversized and long-long fields are
not added. Generic `sizeof`, source/dependency guards and default identities
remain unchanged. Earlier v1 exclusions remain correct for v1, not v2. This is
not a portable layout claim or closure of #92/#48 or the five macro obligations.
[Original evidence and separate verification gates](cpp03-short-long-bit-field-layout-verification.md).

### Selected unnamed unsigned padding (#92, v3 follow-up)

`offsetof.bitFieldStorage="x86_64-sysv-unsigned-v3"` preserves v2's named
fields and adds public, non-cv unnamed unsigned char/short/int/long fields with
width zero through declared storage width. Interior/trailing padding, consecutive
barriers, leading zero-width fields, unions, all-zero-field structs and nested
arrays share one selected allocation contract. Unnamed fields consume padding
but do not raise aggregate alignment or become addressable members.

Positive leading struct padding before the first named nonstatic field/bit-field
and positive-padding-only structs remain unsupported because original C++03
§9.2/17 and §9.6/2 need reconciliation there. Nonpublic or cv-qualified unnamed
fields (including cv through aliases) are also outside this contract, not thereby
invalid C++03. Named private fields remain non-POD. V1/v2/default identities,
generic `sizeof` and previous semantic guards are unchanged. Signed/plain,
bool/enum, oversized and long-long fields are not added. Zero-width effects on
ordinary members/trailing size are selected ABI/compiler facts, not universal
2003 guarantees. #92/#48 and the five macro obligations remain open.
[Original evidence and separate gates](cpp03-unnamed-bit-field-layout-verification.md).

### Macro acceptance consolidation

[The 102-obligation acceptance map](cpp03-macro-acceptance.md) distinguishes definition presence, independently checked target facts, native CPG materializations and residual scope. Five obligations remain partial/open under #145/#146/#147; neither this map nor the 97 implementation classifications means complete C++03 or universal ABI support. Exact frozen-head aggregate gates are recorded in the task PR.

### Signed and plain integral bit-field layout (#145)

Explicit `x86_64-sysv-integral-v4` adds signed/plain char/short/int/long to ordinary-member layout only, not bit-field signedness, values or promotions. Omitted/v1/v2/v3 contracts are unchanged; #145 residual forms and all five partial/open macro obligations remain open. [Contract and separate gates](cpp03-signed-bit-field-layout-verification.md). #92 acceptance after #148 completed its support-or-open-link criterion, not every residual implementation.

### Bounded oversized bit-field layout (#145)

Explicit `x86_64-sysv-oversized-v5` adds named signed/plain/unsigned char/short/int widths above declared storage through 64 bits, using Itanium §2.4 rather than simply removing v4's width cap. Old policies and guards remain unchanged. Unnamed oversized, bool/enum, widths above64 and other residuals remain in #145; all five obligations stay partial/open. [Contract and separate verification gates](cpp03-oversized-bit-field-layout-verification.md).
