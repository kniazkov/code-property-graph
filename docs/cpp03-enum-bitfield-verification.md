# Enum bit-field allocation (#145)

Explicit x86_64-gcc-enum-v12 inherits v11 and adds bit-fields whose resolved
base type is an enum. It also requires the independently selected
`offsetof.enumStorage=gcc-clang-x86_64-default-enum-v1`. Missing storage
capability, uncertified enumerators and recursive dependencies remain unknown
with diagnostics. No inferred host representation is used.

The existing enumFacts path chooses 32/64-bit signed or unsigned storage from
certified enumerator values. Allocation then uses the same integral bit-field
rules, including unnamed barriers and GCC oversized alignment. Typedef/cv
syntax remains in the graph. The representation policy does not change enum
values, promotions or bit-field addressing. Existing policies still reject enum
bit-fields. This is GCC-selected allocation, including inherited compiler
limitations; it is not a universal ABI contract.

Basis: original C++03 §9.6/1 allows integral/enumeration bit-field types and
makes allocation implementation-defined; §7.2 governs enum representation.
Existing source-reviewed enum and unnamed-layout reports retain their original
source identities. This packet reuses their bounded enum representation
contract and the Itanium §2.4 II-2 allocation rule; it does not claim a new ISO
PDF review or upgrade immutable paragraph classifications.

Tests: EnumBitFieldLayoutTest covers signed/unsigned and wider ranges,
explicit capability, alias/cv, unnamed access, oversized fields, old-profile
rejection, copy/GREEN/FOLDED/JSON, edits/reparse and invalid operands.
EnumBitFieldLayoutIT adds GCC original/generated/actual materialized offsetof
checks for enum widths3/128 to the inherited native corpus and CLI guards.
Artifacts: target/enum-bitfield-oracle. Remaining #145: leading-positive struct
padding. Five macro obligations remain partial/open.

```sh
mvn -DargLine=-Xmx768m -Dtest=EnumBitFieldLayoutTest,ExtendedBitFieldLayoutTest -Dit.test=EnumBitFieldLayoutIT verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input src/test/resources/enum-bitfield --cpp-standard c++03 --platform-profile src/test/resources/enum-bitfield/profile.json --stage folded --json target/enum-bitfield.json
```

Full final-revision GitHub CI is required for acceptance, checked by the
maintainer. Local verification limitations are recorded in the PR.
