# GCC extended bit-field allocation (#145)

Explicit `x86_64-gcc-extended-v11` inherits v10 and admits widths >=128.
Width arithmetic uses BigInteger, with the existing supportedObjectBytes ceiling
applied to occupied storage and final rounded size. There is no separate fixed
bit-width cutoff and no host allocation proportional to the bit count.
Existing profiles retain their old limits. The policy does not expose __int128
as a source-language type or extend values/promotions/general sizeof.

The ABI algorithm remains [Itanium §2.4 II-2(b)](https://itanium-cxx-abi.github.io/cxx-abi/abi.html#class-types):
select the largest integral POD storage fitting the width. In this explicitly
GCC-selected x86-64 contract, widths >=128 use 16-byte alignment, including
unnamed fields. Padding occupies the full declared width. Below128 the previous
allocation is unchanged. This is not a universal SysV/GCC-version guarantee.
The original C++03 references remain those in the prior source-reviewed audit;
no new normative ledger classification is claimed.

GCC14 and Clang19 disagree on this boundary under strict C++03: after char,
unsigned long:128 gives tail32,size48 in GCC versus tail24,size32 in Clang;
width256 gives tail48,size64 versus tail40,size48. Therefore this profile is
GCC-specific. Clang is not used as a positive layout oracle for it. Prior
profiles retain their earlier GCC/Clang tests. The inherited original-cv
contract also retains the separate Clang later-DR rejection test.

| Width | GCC tail offset after char | Aggregate size | Alignment |
|---|---:|---:|---:|
|128|32|48|16|
|129|33|48|16|
|255|48|64|16|
|256|48|64|16|
|257|49|64|16|
|1024|144|160|16|

ExtendedBitFieldLayoutTest covers fixed facts across bool/char/short/int/long,
named/unnamed fields, widths beyond Java int, object ceiling, edits/reversal,
copy/GREEN/FOLDED/JSON/reparse and old-profile rejection. Huge synthetic widths
are evaluator-only checks, not promises about compiler resource limits.
ExtendedBitFieldLayoutIT covers GCC original/generated/materialized macros,
CLI and negative guards, storing native sources/commands/output/hashes in
target/extended-bitfield-oracle. Enum, leading-positive, invalid named zero,
negative widths and bit-field addresses retain their restrictions.

Manual fixture/profile: src/test/resources/extended-bitfield.

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input src/test/resources/extended-bitfield --cpp-standard c++03 --platform-profile src/test/resources/extended-bitfield/profile.json --stage folded --json target/extended.json
mvn -DargLine=-Xmx768m -Dtest=ExtendedBitFieldLayoutTest,CvPaddingLayoutTest,WideBitFieldLayoutTest -Dit.test=ExtendedBitFieldLayoutIT verify
```

Remaining #145: enum and leading-positive struct padding. Other compiler ABIs
are not implied by this selected profile. Five obligations remain partial/open.
Full final-revision GitHub CI is mandatory for acceptance; the maintainer checks
and merges it. Focused verification is not full CI.
