# Bounded unsigned short/long bit-field layout (#92)

## Contract and compatibility

This follow-up selects `offsetof.bitFieldStorage="x86_64-sysv-unsigned-v2"`
under the existing `gcc-clang-builtin` emission and `x86_64-sysv-lp64-v1`
layout. In addition to v1's named positive unsigned char/int fields, v2 admits
named `unsigned short` widths 1..16 and `unsigned long` widths 1..64, including
bound aliases/cv. Selected target storage/alignment is respectively 2/2 and
8/8 bytes with eight-bit bytes. These facts come from the selected ABI, not
from the Java runtime or machine running the analysis.

V1 retains its original unsigned char/int subset and identity. Omission still leaves
bit-field allocation unavailable. The new identifier must not silently widen
v1 or alter old profile/default identities. The existing ABI prerequisites,
maximum supported object size and snapshot/visibility/dependency/pragma guards
remain mandatory. This is bounded ordinary-member `offsetof` support, not an
extension of generic `sizeof` or certification of every target ABI.

The same allocation algorithm applies: use the earliest available bits without
crossing an aligned declared-type storage unit; named fields contribute their
declared-type alignment. Ordinary members start after occupied whole bytes,
then apply their own alignment. Union members start at zero; final aggregate
size is rounded to maximum member alignment. Existing supported POD unions,
nested objects and arrays compose with the new types. Width/type dependencies
must survive folding, copies, reanalysis and real edits.

Bit-fields never become addressable members or valid `offsetof` designators.
Unnamed/zero-width, signed/plain, bool/enum, oversized and long-long fields are
not added. Unsupported does not mean the declaration is forbidden by C++03:
for example, oversized widths are permitted by the language but not this
selected contract. The prior v1 report's exclusions remain correct for v1.

All five residual macro obligations and #92/#48 remain open. Source hashes,
paragraph hashes, coordinates and classifications are preserved. This package
adds bounded evidence, not whole-paragraph or complete-library conformance.

## Original source and ABI review

The original PDFs were reread locally and their SHA-256 values reverified for
this follow-up; no original PDF or full extracted text is redistributed.

* ISO/IEC 14882:2003 original user-supplied PDF:
  `6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
  §9.6/1–3, printed p.163/PDF p.191, permits integral/enumeration bit-field
  types, specifies integral constant widths and implementation-defined
  allocation/alignment, distinguishes unnamed/zero-width fields and forbids
  taking a bit-field address. §18.1/3,5, printed pp.333–334/PDF pp.361–362,
  inherits the C `<stddef.h>` contract with the POD restriction.
* [ISO/IEC 9899:1990](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf):
  `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
  §7.1.6, printed p.98/PDF p.108, defines `offsetof` as a `size_t` integral
  constant byte offset and makes a bit-field designator undefined. No such
  negative designator may be executed as a native oracle. C90's narrower
  bit-field type rules are not substituted for C++03 §9.6/3.
* [Itanium C++ ABI 1.75 §2.2](https://refspecs.linuxfoundation.org/cxxabi-1.75.html):
  `b8e9cb1cd0a155daff182accffbbd65959dd1afe231be1bb74de8649c4ae688e`.
  POD size/alignment follow the base C ABI. This historical rule is used rather
  than a modern standard-layout reinterpretation or the non-POD algorithm.
* [AMD64 ABI Draft 0.99.6, July 2, 2012](https://refspecs.linuxfoundation.org/elf/x86_64-abi-0.99.pdf):
  `69e117a13b560b1b957c15f05a61b130ca56828f05dca951e27cb8e2ada14283`.
  §3.1.2 Figure 3.1, printed p.12/PDF p.13, specifies unsigned-short
  size/alignment 2/2 bytes and unsigned-long 8/8 bytes. Figure 3.2, printed
  p.14/PDF p.15, gives widths 1..16 and 1..64, each with unsigned range
  zero through 2^w−1. The same page requires declared-type storage-unit
  containment and permits sharing with other members. Aggregate alignment,
  earliest appropriate member offset and size rounding appear on printed
  p.13/PDF p.14.

The historical ABI's plain-field signedness statements are not used to infer
modern GCC/Clang defaults. Plain/signed fields remain excluded. The ABI's
additional scalar types do not authorize `long long` in this strict C++03
packet. No modern defect-report wording replaces the original 2003 contract.

## Required verification and current status

`ShortLongBitFieldLayoutTest` and `ShortLongBitFieldLayoutIT` are the package's
acceptance surfaces. Required checks include v1/v2/omission identity
and JSON/API behavior; 1/16/64-bit and crossing boundaries; mixed storage units;
alias/cv, unions, nested arrays; unsupported categories; real width/type edits;
copy/reanalysis, GREEN/FOLDED, generated/reparsed source and CLI paths.

Native verification must use strict GCC and Clang `-std=c++03 -pedantic-errors`
for original/generated and actual CPG-materialized replacements with the host
macro disabled. Offsets and layout facts need independently fixed expectations,
not values derived from the implementation under test. Retain exact inputs,
compiler commands/identities, output and source hashes in CI artifacts.

At the frozen focused checkpoint, `mvn verify` with selected tests passed:
52 unit tests (including all 14 new unit tests and all 12 v1 bit-field tests),
4 integration tests (2 v2 and 2 v1), zero failures/errors/skips, one minute.
The earlier new-unit-only run also passed 14 tests before final fixture/guard
refinements; final focused verification includes those refinements. No failed
execution was observed in this packet. This is not a full aggregate result.

Independent review approved production, tests, documentation and ledger scope.
Separate differential probes covered 256 v2 struct/union shapes with 1,151
numeric ordinary-field offset/size/alignment observations per compiler; both
GCC 14.2 and Clang 19.1 matched. This used two native compile/run invocations,
not 512 JUnit or native integration cases. Native probe source SHA-256:
`ea0676212ca0eb1f6179458d687a39f8cb3285f0c5ffca6fab682ffb2927702c`.
V1 rejected all 256 shapes with BIT_FIELD, and the prior 128-shape v1 positive
probe was rechecked against both prior native outputs. These independent
observations are separate from repository test counts.

Omitted-profile identity remains
`c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10`;
v1 remains `92bb62417af0d2d82a9e883b0fb79a2ca39cf8061be3f6f9b91804b41e855327`.
V2 roundtrip/settings/toBuilder identity is
`049c96de48f5b7425f207fdcd2f7176d3fb1c1d91de2b7e0ab9951f8f80aa412`.

The new native IT produced 68 records: 34 GCC 14.2.0 and 34 Clang 19.1.7,
including 34 actual CPG-materialized sources with `#undef offsetof`. All 204
source/wrapper/profile SHA-256 checks matched. Outcomes: 44 baseline/reversed,
12 width-only edits and 12 width-plus-type edits. Metadata widthEdit=24 includes
typeEdit=12; these are overlapping flags, not 36 distinct edited records.
Width 3 to 49 changes the selected long-field-adjacent offset 1 to 7; the
short-to-long type edit changes the nested tail 6 to 24 and element tail 5 to 17.
Artifact evidence is retained in `target/short-long-bit-field-layout-oracle/`.

Full clean Maven verification, supplemental workflow gates, exact-head CI and
post-merge master verification remain separate pending gates at this frozen
source checkpoint. Their final outcomes belong in the PR checklist, without
moving the reviewed source merely to record a later CI result.
