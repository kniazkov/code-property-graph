# C++03 #92: bounded enum storage for offsetof

## Verification status

The clean gate passed all 1439 unit tests and 22 explicitly selected IT in
19:34, with zero failures, errors or skips. Independent review passed 79
assertions and 68 overlapping JUnit tests; these are not added to gate totals.
Strict JavaDoc and GCC/Clang catalogue probes passed on the current source.
Full Maven verification is running; exact-head CI remains pending.

offsetof remains open/partial: 97 bounded obligations / 90 names and five open
obligations / five names. #92 and #48 remain open. Prior verification reports
are historical and unchanged.

## Independent optional representation

The existing offsetof capability gains enumStorage="gcc-clang-x86_64-default-enum-v1".
It is independent of memberPointerStorage. Omission remains unsupported and
omitted from canonical settings. The fifth constructor/parse/builder argument
follows memberPointerStorage; older overloads retain their meaning. Explicit JSON null and unknown values are rejected. Nullable constructor/API
arguments intentionally encode omission. Tests preserve the existing omitted-policy identities: offsetof-only
`c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10`
and member-pointer-only
`62a0fae7a740ee50f76a6eabf1ac439f0ed7f112f07db78a9c4d99e0db15f60e`.
Default profile resources and golden fixtures are unchanged.

Under the exact existing layout contract, admissible ranges use selected
4-byte/alignment-4 or 8-byte/alignment-8 storage. Raw initializer values and
original underlying-type admissibility are checked before representation is
returned. Pure storage can be justified when the generic underlying choice is
unknown; this does not invent a generic underlying or promoted type. Original
{-1, UINT_MAX} range restrictions remain in force. No host/glibc inference,
-fshort-enums mode, complete ABI certification or bitfield support is claimed.

## Original semantics and emitted compatibility

Original C++03 full-underlying-range promotion is unchanged. Actual enum
arithmetic and type-selection dependencies must prove agreement between the
original promoted type and the selected native behavior, including signedness
and rank. Compatible int-first cases remain supported; mismatched or unknown
promotion dependencies are explicitly unsupported, not declared invalid C++03.
Equal storage widths or equal final integers do not establish compatibility.
Unproven complex expression forms remain conservative, for example
sizeof(+*&object); no blanket expression-support claim is made.

Direct sizeof of the enum or same-enum object/conditional can use storage
without promotion. Outer arithmetic, different-enum and enum/integer
conditionals retain identity and require the relevant proof. Lexical previous
enumerators retain their original initializer types, including equal narrow
conditional branch types and subsequent implicit initializers. Generic
TypeSemantics and generic enum promotion are not rewritten.

Snapshot-owned cycle, ceiling and cache guards remain shared. Folding retains
actual enum source/type dependencies through objects, members, indices, calls,
parameters, member-pointer projections and callable results, without following
runtime initializers. Meaningful edits recompute representation and preserve
the original snapshot. Unrelated enums do not poison supported queries. Source
replay, pragma restrictions and existing partial-template boundaries remain.

Reviewed checks cover narrow conditional typing, nonconstant type dependencies
and nested conditional provenance. Logical-not and conditional conditions need
known scalar or lexical-integer type proof without evaluating operand values.
Known floating/pointer conditions and resolved overloaded operator! return
types remain supported; unresolved/nonconvertible conditions remain unsupported.
Omitted enumStorage does not activate the lexical fallback.

## Historical evidence

Official [WG21 N1936](https://open-std.org/JTC1/SC22/WG21/docs/papers/2006/n1936.html)
(24 February 2006), issue 172, quotes original C++03 promotion using the entire
underlying-type range and lists the proposed resolution as WP, not TC1. Its
introduction distinguishes TC1 incorporation from other proposals. Modern
native promotion and mixed-range acceptance are comparison evidence, not a
replacement for original published semantics or proof of a project defect.

[N1639](https://www.open-std.org/jtc1/sc22/WG21/docs/papers/2004/n1639.html)
identifies Sydney 2004 changes in N1638; later drafts are not interchangeable
with original ISO2003. The private ISO2003 PDF remains absent; no fresh private
read is claimed. Original ledger source hashes and coordinates are unchanged.

## Verified checkpoint and native evidence

Tests: src/test/java/com/kniazkov/cpg/analysis/EnumStorageLayoutTest.java and
src/test/java/com/kniazkov/cpg/e2e/EnumStorageLayoutIT.java. Production manifest
SHA256: `0ea355983d3bfa6e63ae12b7ef242f3b1e13d7ebab9a858c32a0d68e6910a24c`.
Tests manifest SHA256:
`b6f96538cf4ad085772a5a53894a45c1be8607351b6ee99d9de6129a4707979a`.

Run from the repository with the configured Java/Maven and native toolchain:

```sh
mvn -o -B clean -Dit.test=EnumStorageLayoutIT,StrstreamLibraryIT,MemberPointerLayoutIT,OffsetofRuntimeIT,OffsetofContextsIT,MacroEditingIT,PackagedCliIT -DargLine=-Xmx768m verify
```

This passed all 1439 units and 22 selected IT in 19:34. JAR SHA256:
`74db6e1c928728c7850b93f0017c037a9a359e6a9721d26b3025676652a5de00`.

Fresh artifacts originate at target/enum-storage-layout-oracle; their preserved
manifest SHA256 is
`4a319d4c1acb3d39330e06b706b1a8d20b5e39138f98b60113ed3073b81caa5e`.
There are 82 execution records: 56 main, 24 lexical/alias and two modern
comparison-only records, with 41 per compiler and 40 actual CPG materializations.
The 20 edited=true flags label widened variants only, not all editing operations;
reverse/restored variants are also executed. Source, compiled-wrapper and
profile file hashes were checked against metadata. Categories overlap.

GCC Debian 14.2.0-19 targets x86_64-linux-gnu; Clang Debian 19.1.7 (3+b1)
targets x86_64-pc-linux-gnu. Strict flags are -std=c++03 -pedantic-errors.
Selected profile file SHA256:
`d6ef439f2918db4eee81bd6f5d8c00d75a29b96fc34fe20a97aee62957bbb87a`.
The separate PlatformProfile.identityHash() is
`0d4daeaab8124181f446bb1e754d978fbd2bf444b0e169c973f6c9880bd4aa93`.

Main observations cover 44 baseline/restored executions with tail=8, size=4,
index=2, extent=9 and 12 widened executions with 16/8/2/17 respectively.
Lexical and alias edits additionally check retained initializer/type syntax and
reverse changes. The two comparison probes describe modern representation and
later-DR promotion only; they do not certify original C++03 semantics or add
supported syntax. No fresh individual header hashes or libc version were
collected. No broad ABI certification is claimed.

The all-unit/selected-IT checkpoint is not the full integration suite. Full
Maven and exact-head CI remain separate pending gates. offsetof remains
open/partial; no completion mark is assigned to #92 or #48.

Чистый прогон прошёл все 1439 unit-тестов и 22 явно выбранных IT за 19:34
без ошибок и пропусков. Независимые 79 проверок и 68 JUnit-тестов перекрываются
с ним и не прибавляются к общему числу. Проверены 82 native-записи, включая
40 материализаций CPG; 20 флагов edited обозначают только расширенные варианты,
а не все правки. Исходная семантика C++03 сохранена. offsetof остаётся
open/partial; полный Maven и CI ещё ожидаются, #92 и #48 открыты.
