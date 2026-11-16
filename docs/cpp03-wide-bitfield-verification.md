# Bounded wide bit-fields v9 (#145)

Explicit `x86_64-sysv-wide-v9` inherits v8 and raises the selected width
ceiling from 64 to 127 for named and unnamed admitted integral/bool fields.
Zero remains allowed only for unnamed fields. Previous policies retain their
limits and identity settings. CV unnamed, enum, leading-positive struct padding
and widths 128 or greater remain outside this contract and tracked in #145.

The algorithm remains Itanium ABI §2.4 II-2(b): allocate oversized fields at
the next byte boundary aligned to the largest integral POD type fitting the
width. See https://itanium-cxx-abi.github.io/cxx-abi/abi.html#class-types and
[the v7 primary-source review](cpp03-padding-bit-field-layout-verification.md).
For 65..127 bits the selected largest storage unit is 64 bits, so alignment
is eight bytes. The next threshold, 128, is deliberately excluded: native GCC
uses extended 128-bit allocation there even in strict C++03 mode. This package
makes no claim about that representation or arbitrary-width ABI support.
Original C++03 §9.6 and §18.1/5 references remain those of the existing audit;
no new ISO PDF review or immutable normative classification is claimed.

For `struct S { char c; unsigned long bits:N; char tail; };`, fixed facts are:

| N | tail byte offset | size | alignment |
|---|---:|---:|---:|
| 65 | 17 | 24 | 8 |
| 80 | 18 | 24 | 8 |
| 96 | 20 | 24 | 8 |
| 127 | 24 | 32 | 8 |

The same positive facts apply to admitted unnamed fields and bool/char/short/int
families. Oversized unnamed fields affect alignment but never become addressable
members. Named private fields remain NOT_POD. Size ceilings, snapshot/dependency,
cycle and pragma guards, cv-unnamed rejection and bit-field-designator rejection
remain active. No value/promotion semantics or generic sizeof extension is added.

`WideBitFieldLayoutTest` checks the fixed numerical table across six type
families, named/unnamed forms, old profiles, guard boundaries, copies,
GREEN/FOLDED/JSON and width/type edits with reversal/reparse.
`WideBitFieldLayoutIT` checks strict GCC/Clang original/generated/CPG-materialized
macros and CLI, including public named width65 and private unnamed bool width127.
Invalid or unsupported operands are never executed as native oracles.
Sources, compiler versions/commands/output and profile/source hashes are uploaded
from `target/wide-bitfield-oracle`. The durable fixture and explicit profile are
in [wide-bitfield](../src/test/resources/wide-bitfield/README.md).

```sh
mvn -DargLine=-Xmx768m -Dtest=WideBitFieldLayoutTest,NonpublicPaddingLayoutTest,PaddingBitFieldLayoutTest -Dit.test=WideBitFieldLayoutIT verify
```

Full GitHub CI on the final revision remains the mandatory acceptance gate;
the maintainer checks and merges it. Focused local tests do not substitute for
full verification. #145 and all five partial/open macro obligations remain open.
