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
| `csetjmp` / `setjmp.h`; 18 | Opaque jmp_buf; `void longjmp(jmp_buf,int)` declaration. | C both-spelling longjmp resolution; N function pointer and host setjmp presence. Opaque. | setjmp macro absent; no matching-environment/nonlocal-transfer/destructor semantics or runtime jump acceptance. |
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
| 18, language support | R all 12 allocation signatures, handlers/typeinfo/limits; C size types. | C `c99AndLaterApisAreNotPresentedAsCpp03` excludes later APIs. D allocation namespace assertions are positive placement evidence, not wrong-namespace rejection; general ill-formed-program diagnostics are unproved. | C opaque state/types and missing va_*/setjmp; O unknown limit constants. No full runtime handler/nonlocal jump acceptance. |
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
