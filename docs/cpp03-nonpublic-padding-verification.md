# Nonpublic unnamed padding v8 (#145)

## Contract and basis

Explicit `offsetof.bitFieldStorage="x86_64-sysv-access-v8"` inherits the bounded
v7 storage policy and admits non-cv unnamed bit-fields under private/protected
access, including implicit private access in a class. Named nonstatic members
must still be public. Access labels do not change the selected allocation,
size, alignment, zero-width barriers or absence of addressable field entries.
Old omitted/v1–v7 profiles retain their limits and identity settings.

Original-language reasoning uses the existing source-reviewed report
[Unnamed padding: Original-language boundaries](cpp03-unnamed-bit-field-layout-verification.md#original-language-boundaries):
C++03 §9.6/2 says an unnamed bit-field is not a member; §8.5.1/1 and §9/4
restrict aggregate/POD data members. Therefore access on an unnamed field
alone is not the private/protected data-member violation. This is distinct
from a named private data member or named private bit-field, both still
rejected as NOT_POD. Class-key `class` alone is not a POD disqualification.
No fresh original ISO PDF review or immutable ledger upgrade is claimed.

The allocation basis remains the selected SysV profile and
[Itanium ABI §2.4 II-2](https://itanium-cxx-abi.github.io/cxx-abi/abi.html#class-types)
(read 2026-11-16), including possibly unnamed fields and the oversized
alignment rule. No new arithmetic/storage algorithm is introduced.
Modern GCC may warn about non-standard-layout offsetof even in C++03 mode;
this later-language diagnostic does not replace the original membership
rule. Warnings are retained in artifacts. Strict GCC and Clang positive
executions check the selected ABI; they are not evidence of universal
language/ABI conformance. Research probes also compared public member
addresses within real objects, independently of the offsetof builtin.

## Scope and residuals

For `struct S { char c; private: bool :64; public: char tail; };`, tail=16,
size=24 and alignment=8 bytes. A protected unnamed int width33 gives 9/12/4;
ordinary int width3 gives 2/3/1. A union with unnamed int width33 and char
member has size8, alignment4 and member offset0. Leading zero-width private
padding in a class is admitted; leading positive padding is not.

CV unnamed fields (also through typedef), enum bit-fields, widths above64,
leading positive struct padding and positive-padding-only structs remain
tracked by #145. Named private/protected members, bit-field offsetof operands,
packing, unresolved/cyclic widths and the object-size ceiling retain their
guards. No bit-field values/promotions, general sizeof or new language
versions are added. Five macro obligations remain partial/open.

## Verification

`NonpublicPaddingLayoutTest` checks struct/class/union, explicit private and
protected labels, default class access, named-private rejection, old-policy
rejection and identity, aliases, arrays, numerical facts and guards. It edits
an actual private unnamed field's width/type, restores it, reparses generated
code and checks independent copies, GREEN/FOLDED and JSON.
`NonpublicPaddingLayoutIT` checks original/generated/copied/edited/reparsed
sources and actual CPG-materialized offsetof macros with both GCC and Clang
in strict C++03 mode. It additionally changes protected int padding to bool
padding, exercises CLI JSON and keeps rejected forms out of native execution.

Durable input/profile and manual commands:
[nonpublic-padding](../src/test/resources/nonpublic-padding/README.md).
CI artifacts under `target/nonpublic-padding-oracle` retain sources, wrappers,
compiler versions/targets/commands/output, observations, profile identity and
SHA256 hashes. Numerical evaluator assertions are independent of the native
wrapper's fixed constants. The focused command is:

```sh
mvn -DargLine=-Xmx768m -Dtest=NonpublicPaddingLayoutTest,PaddingBitFieldLayoutTest,UnnamedBitFieldLayoutTest -Dit.test=NonpublicPaddingLayoutIT verify
```

Full final-revision GitHub CI is the acceptance gate; focused local verification
does not replace it. The maintainer checks CI and merges. #145 remains open.

Local result (2026-11-16): BUILD SUCCESS, 23 unit tests and 2 e2e tests,
92 GCC/Clang native executions; no failures or skips. Tested JAR SHA256:
`9ff5a241921205e7a6fb1f3dfbcbb9a118525853e36ab7f395757a19534a1664`.
