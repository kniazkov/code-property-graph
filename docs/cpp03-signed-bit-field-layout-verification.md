# Signed and plain integral bit-field layout (#145)

## Selected contract

The explicit `offsetof.bitFieldStorage="x86_64-sysv-integral-v4"` policy adds
signed and plain char/short/int/long fields to v3's unsigned layout subset.
Storage-unit widths remain 8/16/32/64 bits. Named fields require positive
widths no larger than their declared storage; unnamed public non-cv padding
may have width zero. Bound aliases and existing named-cv handling are retained.
This is an ordinary-member offset/size/alignment calculation, not a bit-field
value, signedness, promotion, or serialization contract. The omitted setting
and v1/v2/v3 settings, identities and accepted subsets remain unchanged.

The same declared-unit containment, ordinary-member rounding, aggregate
alignment and unnamed-padding rules apply. Unnamed fields do not increase
aggregate alignment. No addressable field entry is created for a bit-field.
Snapshot/dependency/cycle checks, pragma guards and object-size ceilings remain
mandatory. Generic sizeof support is not extended.

Bool/enum fields, oversized widths, nonpublic or cv unnamed fields, leading
positive struct padding and positive-padding-only structs remain outside this
selected model. They are not thereby invalid C++03. Named private members
remain non-POD. Bit-field offsetof designators are never native test inputs.
Residual work stays in #145; the five macro obligations remain partial/open.
#92's support-or-open-link coordination acceptance is complete after #148,
not the implementation of these residuals. #48 remains open.

## Source and ABI evidence

The original ISO/IEC 14882:2003 §9.6/1–3 (printed163/PDF191) was reread:
allocation/alignment and plain char/short/int/long bit-field signedness are
implementation-defined; oversized fields may contain padding; unnamed fields
are not members and only they may have width zero. No later DR wording is
silently substituted. Original §9.2/17 and §9.6/2 still motivate the explicit
leading-positive exclusion documented in the v3 report.

[AMD64 ABI 0.99.6 §3.1.2, Figure3.2](https://refspecs.linuxfoundation.org/elf/x86_64-abi-0.99.pdf)
(printed14/PDF15) lists signed/plain/unsigned char, short, int and long with
8/16/32/64-bit ceilings. Declared-unit containment, sharing and alignment rules
support the layout subset. Its plain-field value-range wording is NOT imported
as C++ value semantics. [Itanium C++ ABI1.75 §2.2](https://refspecs.linuxfoundation.org/cxxabi-1.75.html)
uses the base C ABI for POD size/alignment. These selected ABI facts do not
constitute a universal C++03 layout guarantee.

Source SHA256 values reverified for this packet:

- User-supplied 2003 PDF: `6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
- Original C90 PDF: `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
- AMD64 ABI0.99.6 PDF: `69e117a13b560b1b957c15f05a61b130ca56828f05dca951e27cb8e2ada14283`.
- Itanium ABI1.75 HTML: `b8e9cb1cd0a155daff182accffbbd65959dd1afe231be1bb74de8649c4ae688e`.

Original PDFs/full extracted text are not redistributed. C90 §7.1.6 and original
C++03 §18.1/5 offsetof/POD restrictions remain as documented in prior reports.

## Verification gates

SignedBitFieldLayoutTest and SignedBitFieldLayoutIT cover the selected policy,
legacy rejection, API/CLI/JSON, copying and GREEN/FOLDED output, width/type
edits and reversal/reparse. Native probes use independently fixed ordinary
member offsets, object sizes and alignment-through-embedding, not CPG output
as its own oracle. GCC and Clang run original/generated and actual CPG
materialized inputs using `-std=c++03 -pedantic-errors`. Bit-field values are
not read or written; layout agreement does not certify signedness/promotions.
Metadata and hashes are retained in `target/signed-bitfield-oracle/`.

Focused lifecycle passed **44 unit tests + 2 integration tests**, zero
failures/errors/skips, **56.550 seconds**. Command:

```sh
mvn -B -ntp -s "$CPG_MAVEN_SETTINGS" -Dtest=BitFieldLayoutTest,ShortLongBitFieldLayoutTest,UnnamedBitFieldLayoutTest,SignedBitFieldLayoutTest -Dit.test=SignedBitFieldLayoutIT verify
```

An earlier unit invocation exposed a new null-policy selector regression, which
was fixed before this combined pass. Two earlier IT invocations failed because
of shared target recompilation and an incorrect Surefire selection without the
packaged-JAR property; they are not counted as passes. The corrected invocation
used the normal Failsafe lifecycle without concurrent Maven processes.

Full clean verify, exact-final-head CI and post-merge verification remain
separate gates. Final outcomes are recorded in the task PR before merge;
focused results do not substitute for aggregate acceptance.

Independent review found no blocking defect in the bounded production/test contract.
Its separate strict GCC/Clang probe confirmed mixed signed/plain, padding and
cv-alias offsets/sizes/alignment. Focused native artifacts contain 68 records
and 204 verified referenced hashes (17 ordinary and 17 actual-materialized
routes for each compiler). The paragraph ledger is unchanged. Supplemental links below record only this
bounded evidence; immutable classifications/hashes and all macro support
statuses/residual links are unchanged.

## Fresh aggregate result

Full clean local `mvn -B -ntp -s "$CPG_MAVEN_SETTINGS" clean verify` passed:
**1651 unit + 169 integration tests**, zero failures/errors/skips, **54:26**.
Fresh shaded JAR SHA256:
`85cbec82f260721eb740db34a59d941d5b7a63e7efe604c6c8d918062ff4c7f3`.
This is local acceptance, not a statement that exact-head CI or post-merge gates
have passed. The PR records those separately.

## Supplemental paragraph evidence, not reclassification

To avoid rewriting large immutable-source ledgers for four supplemental links,
this small report carries the precise bounded associations. The JSONL ledgers
are byte-for-byte unchanged, not silently marked complete:

- `cpp03:9.6:p1`: `SignedBitFieldLayoutTest#signedAndPlainFamiliesHaveIndependentLayoutFactsWithoutValueClaims`,
  `#widthAndTypeEditsCopiesGreenFoldedJsonAndReparse`, `#dependenciesCyclesCeilingAndPragmasRemainConservative`.
  Selected layout widths/edits only, not oversized fields or arbitrary ABI.
- `cpp03:9.6:p2`: `SignedBitFieldLayoutTest#mixedSignedUnsignedUnitsAndUnnamedPaddingPreserveV3Rules`,
  `#unsupportedFormsDoNotExpandAlongWithSignedStorage`. Bounded padding only;
  original leading-positive and unnamed cv/access questions stay in #145.
- `cpp03:9.6:p3`: `SignedBitFieldLayoutTest#unsupportedFormsDoNotExpandAlongWithSignedStorage`
  retains bit-field-designator rejection; it does not establish full bit-field
  value, signedness, reference-binding or bool/enum semantics.
- `cpp03:18.1:p5`: `SignedBitFieldLayoutIT#ordinaryOffsetsSurviveStagesCopiesReparseWidthAndTypeEditsAndCli`
  and `#previousPoliciesAndResidualFormsAreRejectedWithoutNativeExecution`.
  Only ordinary members in the selected POD/layout subset; unsupported forms
  are not promoted to support and undefined native negatives are not executed.
