# Bounded bool and unnamed oversized layout v7 (#145)

## Contract and primary basis

Explicit `offsetof.bitFieldStorage="x86_64-sysv-padding-v7"` extends v6 with
named bool widths 1..64, unnamed bool widths 0..64, and unnamed oversized
char/short/int families through 64 bits. Existing integral families, aliases,
named cv-qualified fields and zero-width padding behavior are inherited.
Omission and v1–v6 retain their earlier boundaries and profile identities.
This is ordinary-member offset and selected aggregate size/alignment support;
it does not add bit-field values, promotions, addresses or general sizeof.

The primary ABI reference is [Itanium C++ ABI §2.4 II-2](https://itanium-cxx-abi.github.io/cxx-abi/abi.html#class-types),
read on 2026-11-16. II-2 explicitly includes possibly unnamed fields. Part (b)
allocates an oversized field using the largest integral POD type whose size
fits its width and updates aggregate alignment. Thus ordinary unnamed padding
and oversized unnamed padding have different alignment effects. Bool has
one-byte size/alignment (§2.2). The selected x86-64 SysV LP64 profile fixes the
remaining scalar and allocation assumptions; GCC and Clang check this bounded
implementation, not universal C++ ABI behavior.

Original C++03 §9.6/1–2 and §18.1/5 remain the language and offsetof references
from the existing source-reviewed ledger. This packet does not claim a fresh
review of the original ISO text, equate the current ABI page to ABI 1.75, or
change immutable paragraph classifications/hashes. ABI POD-for-layout and
language POD are distinct concepts. The pre-existing language-POD guards stay
in force; no non-POD offsetof operand is admitted by the ABI allocation rule.

For `struct S { char c; bool :N; char tail; };`, selected independent facts are:

| N | tail offset (bytes) | sizeof S | alignment (bytes) |
|---|---:|---:|---:|
| 0 | 1 | 2 | 1 |
| 1 or 8 | 2 | 3 | 1 |
| 9 | 3 | 4 | 1 |
| 16 | 4 | 6 | 2 |
| 17 | 5 | 6 | 2 |
| 31 | 6 | 8 | 2 |
| 32 | 8 | 12 | 4 |
| 33 | 9 | 12 | 4 |
| 63 | 12 | 16 | 4 |
| 64 | 16 | 24 | 8 |

A named bool field has the same positive-width facts in this example.
An unnamed `int :3` does not raise aggregate alignment; `int :33` raises it
to four bytes. Bit-fields never become entries in the addressable field list.

## Residuals and guards

Issue #145 remains open for widths above 64, enum bit-fields, cv/nonpublic
unnamed fields and leading positive struct padding before the first named
object. These unsupported forms are not universally classified as invalid
C++03. Existing no-profile, cycle, dependency, packing/pragma, object-size
ceiling and bit-field-designator guards remain. Negative widths and named
zero-width declarations remain rejected. No invalid/undefined native oracle
is executed. All five partial/open macro obligations retain their statuses.

## Evidence and reproducibility

`PaddingBitFieldLayoutTest` checks the fixed table, all unnamed oversized
char/short/int families, unions, nested arrays, aliases, prior-policy limits,
profile round-trips, copy/GREEN/FOLDED/JSON, edits/reversal/reparse and guards.
`PaddingBitFieldLayoutIT` checks original, generated and actual CPG-materialized
macro sources with independent constants in GCC and Clang, using
`-std=c++03 -pedantic-errors`, plus CLI and rejected boundaries. Expected
oversized-field warnings are retained in artifacts; they are not suppressed.
The native entry point checks x86-64/LP64 assumptions. Compiler execution is
required, not silently skipped. Native comparisons complement separate CPG
numerical assertions rather than proving all evaluator states equivalent.

The durable fixture and explicit profile are in
[`src/test/resources/padding-bitfield`](../src/test/resources/padding-bitfield/README.md).
Artifacts under `target/padding-bitfield-oracle/` include compiled sources,
wrappers, fixed observations, compiler version/target/command, output, profile
identity and source/profile hashes; CI uploads this directory.

Focused local verification: 59 unit tests across seven layout suites and two
new e2e tests passed; the latter made 80 native runs across GCC and Clang.
Commands:

```sh
mvn -DargLine=-Xmx768m -Dtest=PaddingBitFieldLayoutTest,BoolBitFieldLayoutTest,UnnamedBitFieldLayoutTest,OversizedBitFieldLayoutTest,SignedBitFieldLayoutTest,ShortLongBitFieldLayoutTest,BitFieldLayoutTest -Dit.test=PaddingBitFieldLayoutIT verify
```

Full final-revision GitHub CI remains the acceptance gate. It is started on
publication and checked by the maintainer; this report does not claim that
local focused verification is a full suite or a completed CI run.
