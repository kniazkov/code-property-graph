# Bounded oversized integral bit-field layout (#145)

## Selected contract

The explicit `offsetof.bitFieldStorage="x86_64-sysv-oversized-v5"` policy
extends v4 with named signed/plain/unsigned char, short and int bit-fields whose
width exceeds declared storage, up to 64 bits. Existing ordinary-width fields
retain v4 rules. This is ordinary-member offset/size/alignment support only,
not bit-field values, signedness, promotions or serialization.

Original ISO/IEC 14882:2003 §9.6/1 permits excess width as padding. Itanium C++
ABI 1.75 explicitly excludes such C++ POD classes from **POD for layout**;
therefore §2.2's base-C-ABI shortcut is insufficient. Section 2.4 II-2(b)
selects the largest integral POD storage not exceeding width, aligns the new
allocation to it, occupies the specified width, and raises aggregate alignment.
The selected 8/16/32/64-bit types bound this contract. No extended integer type
or width greater than 64 is inferred. Current bytes occupied by an earlier
field must not be reused by an oversized allocation.

The original source SHA256 is
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
The primary ABI is [Itanium C++ ABI 1.75](https://refspecs.linuxfoundation.org/cxxabi-1.75.html),
SHA256 `b8e9cb1cd0a155daff182accffbbd65959dd1afe231be1bb74de8649c4ae688e`.
Full source documents are not redistributed. Original §9.6 and §18.1/5
restrictions are retained; this is a selected ABI contract, not portable C++03
layout or substitution of later defect resolutions.

## Boundaries and compatibility

Default and v1–v4 identities and accepted subsets remain unchanged. Snapshot,
alias, constant-dependency, cycle, pragma and supported-object-size guards stay
mandatory; generic sizeof is not extended. An oversized allocation must not
make a bit-field addressable. Native negative tests never apply offsetof to a
bit-field or otherwise execute undefined behavior.

Unnamed oversized fields, bool/enum bit-fields, widths above 64, nonpublic/cv
unnamed fields and leading-positive struct padding remain explicitly outside
this contract. Named nonpublic fields remain non-POD. Unsupported valid forms
are not automatically invalid language constructs. These residuals remain in
[#145](https://github.com/kniazkov/code-property-graph/issues/145).
All five macro obligations remain partial/open; neither #145 nor #48 is closed
by this bounded package.

## Verification

Fresh focused, full clean verify, exact-final-head CI and post-merge gates are
separate. Results will be recorded only after actual execution. Tests cover
independent offsets, sizes and alignment through embedding; strict GCC/Clang
original/generated/actual CPG-materialized routes; edit/reversal/reparse,
copy/GREEN/FOLDED/JSON/CLI, and conservative rejected boundaries.

The immutable paragraph ledger remains unchanged. This report supplements
`cpp03:9.6:p1` (oversized padding and selected layout) and `cpp03:18.1:p5`
(ordinary POD-member offsetof); it makes no full-paragraph support claim.

## Focused result

The focused lifecycle passed 50 unit tests and 2 integration tests with zero
failures/errors/skips in 48.726 seconds:

```sh
mvn -B -ntp -s "$CPG_MAVEN_SETTINGS" -Dtest=BitFieldLayoutTest,ShortLongBitFieldLayoutTest,UnnamedBitFieldLayoutTest,SignedBitFieldLayoutTest,OversizedBitFieldLayoutTest -Dit.test=OversizedBitFieldLayoutIT verify
```

An independent review compared 400 mixed layout probes with strict GCC and
Clang and found agreement on tail offsets, size and embedding alignment.
This auxiliary check is not a substitute for full project verification.

### Named supplemental assertions

- `cpp03:9.6:p1`: `OversizedBitFieldLayoutTest#boundariesUseAllocationAlignmentNotDeclaredStorage`,
  `#mixedPackingUnionAliasesAndNestedArrays`,
  `#widthTypeChangesCrossOversizedBoundaryAndReverse`. These establish only
  the selected named integral widths through 64, not arbitrary oversized fields.
- `cpp03:18.1:p5`: `OversizedBitFieldLayoutIT#ordinaryOffsetsSurviveStagesCopiesReparseWidthAndTypeEditsAndCli`
  establishes the selected ordinary-member materialization paths.
- `OversizedBitFieldLayoutTest#oldPoliciesDoNotAcquireOversizedLayout`,
  `#residualsAndUndefinedDesignatorsAreNotRunNatively`,
  `#boundsPragmasAndCyclesRemainConservative` and
  `OversizedBitFieldLayoutIT#previousPoliciesAndResidualFormsAreRejectedWithoutNativeExecution`
  preserve bounded rejection and earlier policy identity.

None of these assertions changes immutable source locators/hashes, paragraph
classifications or the five partial/open macro statuses.

The first full clean verification attempt was interrupted during the unit suite;
it is not recorded as a pass. The fresh clean retry passed 1657 unit tests and
171 integration tests with zero failures/errors/skips in 51:27 on 2026-11-13.
Exact-head CI and post-merge CI remain separate gates. Native
materialization establishes CPG macro expansion and native behavior; numerical
CPG evaluator assertions are separate unit tests, not an exhaustive equivalence
claim for every native fixture.
