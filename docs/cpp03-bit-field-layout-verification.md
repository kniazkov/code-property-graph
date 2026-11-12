# Bounded ordinary-member offsets beside bit-fields (#92)

## Selected contract and limits

`offsetof.bitFieldStorage="x86_64-sysv-unsigned-v1"` is an optional closed
contract within the existing `gcc-clang-builtin` emission and
`x86_64-sysv-lp64-v1` layout. It selects named `unsigned char` (1..8 bits) and
`unsigned int` (1..32 bits) bit-fields, including resolved typedef/cv forms,
in otherwise supported POD structs/unions, nested objects and arrays. Widths
must be proven integral constant expressions in the current snapshot. Eight-bit
bytes, one-byte unsigned-char storage/alignment and four-byte unsigned-int
storage/alignment are selected target facts, not inferred host properties.

A structure carries a bit cursor. Each selected bit-field occupies the earliest
available bits without crossing an aligned storage-unit boundary of its declared
type. Named fields contribute declared-type alignment. An ordinary field starts
after the last occupied byte, with its own natural alignment; it does not have
to wait for the end of the bit-field's storage unit. Union fields begin at zero;
aggregate size is rounded for maximum member alignment. This supports ordinary
member offsets only. A bit-field never becomes an addressable layout field,
and naming one as the `offsetof` member-designator remains unsupported.

Absent capability, unnamed or zero-width fields, excessive widths, signed/plain,
bool and enum bit-field types remain outside this bounded contract. These are
implementation limits, not blanket claims that such C++03 declarations are
invalid. Existing POD, snapshot/visibility/cycle, pragma, representability,
`supportedObjectBytes` ceiling and source/dependency guards remain. Bit-field
width and type dependencies must survive folding and later edits. Generic
`sizeof` behavior is not extended. Default profile identities and omission
behavior are unchanged; explicit selection is required. The preexisting selected
offsetof profile without bit-field storage retains identity
`c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10`
(`gcc-clang-builtin`, `x86_64-sysv-lp64-v1`, ceiling `2147483647`).

This does not close `offsetof`, the other four remaining macro obligations,
#92 or #48. Historical reports excluding all bit-fields describe earlier
checkpoints. Immutable source/paragraph hashes and classifications are unchanged.

## Original standards and primary ABI evidence

The supplied ISO/IEC 14882:2003 original PDF was inspected and its SHA-256
verified:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.

* §9.6/1–3, printed p.163 / PDF p.191: nonnegative integral constant width;
  implementation-defined allocation/alignment; unnamed and zero-width rules;
  integral/enumeration types; no address-of applied to a bit-field. Wider-than-
  representation widths are permitted by the language but excluded here.
* §18.1/3,5, printed pp.333–334 / PDF pp.361–362: C `<stddef.h>` contract is
  inherited with POD structure/union restriction; static/function members are
  undefined designators.

The original [ISO/IEC 9899:1990 PDF](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf)
was independently downloaded and SHA-256 verified:
`26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
§7.1.6, printed p.98 / PDF p.108, defines the `size_t` integral constant byte
offset and address-constant condition; a bit-field designator has undefined
behavior. §6.5.2.1, printed p.60 / PDF p.70, documents nonportable allocation
choices. No undefined designator is executed as a native negative test.

[Itanium C++ ABI 1.75 §2.2](https://refspecs.linuxfoundation.org/cxxabi-1.75.html)
specifies POD size/alignment through the base C ABI. The retrieved historical
HTML has SHA-256
`b8e9cb1cd0a155daff182accffbbd65959dd1afe231be1bb74de8649c4ae688e`.
This is the POD rule, not the non-POD class algorithm or a modern standard-layout
substitution for C++03.

[System V AMD64 ABI Draft 0.99.6, July 2, 2012](https://refspecs.linuxfoundation.org/elf/x86_64-abi-0.99.pdf)
has verified SHA-256
`69e117a13b560b1b957c15f05a61b130ca56828f05dca951e27cb8e2ada14283`.
§3.1.2 Figure 3.1, printed p.12 / PDF p.13, gives scalar sizes/alignments.
Aggregates and Unions, printed p.13 / PDF p.14, supplies lowest available
appropriately aligned offsets, maximum component alignment and size rounding.
Bit-Fields/Figure 3.2, printed p.14 / PDF p.15, gives unsigned-char/int ranges,
containment within declared-type storage units and sharing with other members.
The ABI is selected evidence for this target subset, not a language guarantee.

## Verification plan and current status

Research-only positive probes compiled and executed with GCC 14.2.0 using
`-std=c++03 -pedantic-errors`. Independently expected `(tail offset, size)`
facts include unsigned-int width 1 `(1,4)`; a char prefix followed by uint width
24 `(4,8)` or width 25 `(8,12)`; uint width 31 followed by uchar width 2 `(5,8)`;
and a union's ordinary char member `(0,4)`. These probes corroborate the ABI
interpretation, not the implementation or aggregate test gates.

Implementation acceptance is separately covered by `BitFieldLayoutTest` and
`BitFieldLayoutIT`: explicit profile API/JSON and omitted/default behavior;
ordinary offsets across storage boundaries and mixed declared types; union,
nested and array layout; unsupported designators/types/widths; copy/reanalysis,
real width/type edits, source generation/reparse and FOLDED/GREEN paths.
Native acceptance requires strict GCC and Clang `-std=c++03 -pedantic-errors`
on original, generated and actual CPG-materialized macro replacements with the
host macro disabled, plus CLI and independently asserted offsets/layout facts.
Inputs, commands, compiler identities, outputs and SHA-256 metadata belong in
`target/bit-field-layout-oracle/`, included in workflow artifacts.

At the corrected focused checkpoint, 52 unit tests passed (10 new bit-field
units plus 42 existing layout units). A subsequent enum/const-width regression
brings the new class to 11 tests; that regression passed a separate direct
method invocation from the freshly compiled test class, without adding to the
52-test JUnit count. The full class is included in the clean aggregate run.
The first two native harness attempts failed test-only literal/whitespace
assumptions. Both were corrected without changing production behavior.
The final focused run passed **2 IT, zero failures/errors/skips** in 20.07 s.
It completed **68 native executions**, 34 per compiler; 34 use actual CPG
materializations with the host `offsetof` macro disabled. Width edits appear
in 24 records and type edits in 12; these categories overlap. The native oracle
also pins mixed `uint:5 / uchar:4 / char` offset/size `(2,4)` and
`uchar:7 / uint:10 / char` `(3,4)` rather than deriving expected values from CPG.

Independent production review of all four changed production files found no
blocker. A separate differential probe covered 128 deterministic struct/union
cases with mixed unsigned-char/int widths, ordinary scalar/array fields and
const/volatile qualification. Each case's tail offset, size and alignment
matched the CPG query on both GCC and Clang (384 numeric observations per
compiler). The independent native-source SHA-256 is
`692e58ec55a9aa41956866524e5cd81d738c4b6ac749a7ea8e725c9a5bbac9bb`.
This evidence is separate from, and not added to, JUnit/IT counts.

The clean aggregate subsequently completed its unit phase: **1617 unit tests,
zero failures/errors/skips**, including all 11 BitFieldLayoutTest methods. Its
integration phase is still running at this frozen checkpoint; this is not a
full aggregate PASS. Full workflow gates, exact-head published Linux/Windows CI and
post-merge master verification are separate gates, not inferred from focused
success. Their final outcomes are recorded in the associated PR checklist.
This frozen pre-publication report does not claim merge or a full aggregate
PASS. Interrupted/failed runs are never counted as successful verification.
