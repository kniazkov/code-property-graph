# Bounded unnamed unsigned padding and zero-width barriers (#92)

## Selected contract

`offsetof.bitFieldStorage="x86_64-sysv-unsigned-v3"` preserves v2's named
unsigned fields and adds **public, non-cv unnamed** unsigned char/short/int/long
fields with proven integral widths from zero through their declared storage
width (8/16/32/64). Bound non-cv aliases may identify these types. Cv qualifiers
through aliases are not silently discarded. Existing x86_64 SysV LP64 target
facts, natural alignment and bounded `offsetof` capability remain prerequisites.
V1/v2/default identities and their previous subsets are unchanged.

Unnamed positive fields consume storage under the existing declared-unit
containment rule but do not raise aggregate alignment. A zero-width field rounds
a struct's current bit position to the selected declared-type unit boundary;
unions start each field at zero, with zero-width fields contributing no extent.
Unnamed fields never acquire addressable entries. Ordinary-member offsets,
size rounding, object ceilings and snapshot/dependency/pragma guards still apply.
Generic `sizeof` is not extended.

One coherent contract includes interior/trailing padding, consecutive barriers,
leading zero-width fields, unions, structs containing only zero-width fields,
nested objects and arrays. Positive leading padding in a struct before its
first named nonstatic field/bit-field, and structs containing only positive
unnamed padding, remain unsupported pending reconciliation of the original
initial-member-pointer guarantee with unnamed fields not being members.

Nonpublic or cv-qualified unnamed fields, including cv through typedefs, remain
unsupported, **not thereby invalid C++03**. Named private fields remain non-POD.
Signed/plain, bool/enum, oversized and long-long fields are not added. Bit-field
`offsetof` designators remain unsupported and must not be executed as native
negative oracles. Five macro obligations and #92/#48 remain open.

## Original-language boundaries

ISO/IEC 14882:2003 §9.6/1–3, printed p.163/PDF p.191, states that allocation
and alignment are implementation-defined; unnamed fields are not members;
zero width specifies the next bit-field's allocation-unit boundary; addresses
of bit-fields are forbidden. The original text does not contain the modern
cv-qualified-unnamed prohibition. Excluding such forms here is a selected
implementation boundary, not importing a later defect resolution into 2003.

§9.2/17, printed p.157/PDF p.185, guarantees the initial member/unit relationship
for a POD-struct pointer. Its note excludes initial unnamed alignment padding.
Together with §9.6/2, this raises a concrete concern for positive unnamed fields
that displace the first named member. This packet does not resolve that issue
or claim all leading forms invalid. All-positive-padding-only structs are
conservatively excluded with that unresolved family. Leading zero width has no
such displacement at cursor zero. Bounded negative tests are not a proof of
§9.2/17, and its ledger classifications are not upgraded.

§8.5.1/1, printed p.147/PDF p.175, and §9/4, printed p.153/PDF p.181,
define aggregate/POD requirements. Because unnamed fields are not members,
a nonpublic unnamed field is not automatically a private nonstatic data-member
violation. Modern compiler warnings about standard-layout must not replace
this original-language reasoning. This packet excludes those fields explicitly
rather than returning a fabricated non-POD diagnosis.

§18.1/3,5, printed pp.333–334/PDF pp.361–362, inherits C `<stddef.h>` with
POD restrictions. C90 §7.1.6, printed p.98/PDF p.108, supplies the `size_t`
constant-byte-offset contract and bit-field-designator undefined behavior.

## Primary ABI evidence and verified source identities

All source hashes were reverified locally for this packet. Original PDFs/full
extracted text are not redistributed.

* User-supplied ISO/IEC 14882:2003 PDF:
  `6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
* [Original ISO/IEC 9899:1990 PDF](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf):
  `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
* [Historical Itanium C++ ABI1.75 §2.2](https://refspecs.linuxfoundation.org/cxxabi-1.75.html),
  POD size/alignment through the base C ABI:
  `b8e9cb1cd0a155daff182accffbbd65959dd1afe231be1bb74de8649c4ae688e`.
* [AMD64 ABI0.99.6, July2 2012](https://refspecs.linuxfoundation.org/elf/x86_64-abi-0.99.pdf):
  `69e117a13b560b1b957c15f05a61b130ca56828f05dca951e27cb8e2ada14283`.
  §3.1.2 Figure3.1, printed p.12/PDF p.13, supplies storage/alignment;
  printed p.13/PDF p.14 supplies aggregate rounding; Bit-Fields/Figure3.2,
  printed p.14/PDF p.15, specifies storage containment/sharing and explicitly
  says unnamed field types do not affect struct/union alignment.

The original next-bit-field rule alone does not determine zero-width effects
on trailing size or a following ordinary member. These are selected ABI/compiler
behaviors corroborated by native probes, not universal 2003 guarantees.
Research-only strict GCC14/Clang19 probes agreed: `uchar a:1; ulong :0` size8;
`uchar a:1; uint :0; char tail` offset4,size5; consecutive uint/ulong barriers
before `uchar b:1; char tail` offset9,size10; union `ulong :63; char tail`
offset0,size8; union zero-width plus char offset0,size1. These observations are
not implementation acceptance or full conformance results.

## Verification gates

Acceptance surfaces are `UnnamedBitFieldLayoutTest` and
`UnnamedBitFieldLayoutIT`. Cover API/JSON capability identities, ordinary offsets
and size/alignment composition, widths and mixed storage units, zero barriers,
unsupported placement/access/cv boundaries, aliases, unions and nested arrays;
real named/unnamed width/type edits; copies/reanalysis; GREEN/FOLDED;
generated/reparsed source and CLI. Negative fixtures must not execute undefined
bit-field-designator or uncertain non-POD native operations.

Strict GCC/Clang `-std=c++03 -pedantic-errors` execution must include original,
generated and actual CPG-materialized replacements with the host macro disabled,
independent expected facts and retained inputs/compiler identities/commands/
outputs/source hashes in CI artifacts.

Focused verification passed 63 unit tests and 6 integration tests (new unnamed
suite: 11 units and 2 IT; legacy v1/v2 native suites also ran), zero failures,
errors or skips, 1:26. A final unit-only regression then added real non-cv to cv
typedef-chain edits and reversal across copies/GREEN/FOLDED. The final focused
unit run passed 64 tests, including all 12 new units, zero failures/errors/skips,
15.743 seconds. Production and native IT sources did not change between these
runs. Full aggregate verification remains a separate gate.

The new native IT produced 68 records: 34 GCC 14.2 and 34 Clang 19.1.7,
including 34 actual CPG-materialized sources disabling the host offsetof macro.
All 204 source/wrapper/profile hashes matched. Outcomes: 44 baseline/reversed,
12 width-only and 12 width-plus-type. Metadata widthEdit=24 includes typeEdit=12.
Changing unnamed width 3 to zero moves the selected offset 2 to 4; changing an
unnamed zero barrier from unsigned short to unsigned long moves nested tail
7 to 19 and element tail 6 to 18. Evidence is retained in
`target/unnamed-bit-field-layout-oracle/`.

Independent production review and differential probes passed: 256 v3 shapes,
1,255 numeric ordinary-field offset/size/alignment observations per compiler,
matching strict GCC14.2 and Clang19.1.7. This is two native compiler runs, not
512 test cases. Probe source SHA-256:
`358b0f2ac44ea5df11ddf49d6cb18f95eceae6a749a7049baa0f4724de6991f8`.
Legacy v2 (256 shapes/1,151 observations) and v1 (128/384) were rechecked using
the current production code against both native outputs. Independent probes
are separate from repository test totals.

An auxiliary JShell identity inspection printed the v3 identity but then hung
while flushing unavailable Java user preferences; it was interrupted. It is
not counted as a passing verification gate. The JUnit/API identity checks pass;
no test failure is hidden by that tooling result.

Full clean Maven, remaining workflow gates, final exact-head CI and post-merge
master verification are pending at this source checkpoint. Their later outcomes
belong in the PR checklist without changing the frozen reviewed source. Mutable
ledger additions preserve source/paragraph hashes, coordinates and
classifications; they do not certify complete coverage of any paragraph.
