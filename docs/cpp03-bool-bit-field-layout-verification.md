# Bounded named bool bit-field layout (#145)

## Contract and primary basis

Explicit `offsetof.bitFieldStorage="x86_64-sysv-bool-v6"` extends v5 with
**named bool bit-fields of width 1 through 8**, including resolved aliases and
named cv-qualified fields. It preserves all earlier policies and their identity
settings. The selected profile already requires 8-bit bool storage/alignment.
This adds ordinary-member offset, aggregate size and alignment facts only, not
bit-field addressing, values, promotions or general sizeof support.

[Itanium C++ ABI 1.75](https://refspecs.linuxfoundation.org/cxxabi-1.75.html)
§2.2 specifies bool size/alignment 1. §2.4 II-2(a) describes integral POD
bit-field allocation within the declared type alignment boundary when width
fits representation; the selected 8-bit bool representation yields byte-sized
units. Document SHA256:
`b8e9cb1cd0a155daff182accffbbd65959dd1afe231be1bb74de8649c4ae688e`.
Strict GCC and Clang probes independently check the concrete bounded policy;
this is not a universal ABI claim or a claim that all C++03 rules were tested.
Original C++03 §9.6 and §18.1/5 remain the language/offsetof references from the
existing audit. No immutable source-ledger classification or hash is changed.

## Explicit remaining scope

Unnamed bool fields (including zero width), bool widths above 8, enum fields,
nonpublic/cv unnamed fields and leading-positive struct padding remain outside
this policy. Valid unsupported forms are not relabelled invalid C++03.
#145 and #48 stay open; all five macro obligations retain partial/open status.
The v5 named integral oversized algorithm is still selected for its earlier
integral families, never implicitly for bool. Snapshot, type/width dependency,
cycle, pragma and object-size guards remain active. Bit-field offsetof operands
are rejected without running undefined native programs.

## Evidence and gates

`BoolBitFieldLayoutTest` checks byte storage widths 1..8, mixed packing,
unions, aliases, nested arrays, prior-policy rejection, identity round-trip,
width/type edits, reversal/reparse, copy/GREEN/FOLDED, JSON and residual guards.
`BoolBitFieldLayoutIT` compiles and runs strict GCC+Clang original, generated
and actual CPG-preprocessor materializations, with independent offset, size and
embedding-alignment facts; it also exercises CLI and rejected boundary inputs.
Artifacts and hashes are written under `target/bool-bitfield-oracle` and included
in CI uploads. Numerical CPG evaluator assertions are separate unit evidence;
native success alone is not claimed as exhaustive evaluator equivalence.

Full clean verify, exact-final-head CI and post-merge master CI are separate
required gates. Results are recorded only after execution.
