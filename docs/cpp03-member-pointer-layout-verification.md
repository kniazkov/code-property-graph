# C++03 #92: selected member-pointer storage for offsetof

## Packet status

This packet adds independently selected member-pointer representation facts to
the bounded offsetof query. The corrected clean gate passed all 1422 unit tests
and 18 explicitly selected IT in 11:15, with zero failures, errors or skips.
Strstream passed all three IT cases in 128.8 seconds. Independent 11-unit/two-IT checks
overlap this coverage and are not added to its totals. offsetof remains
open/partial; inventory totals remain 97 bounded obligations / 90 names and five
open obligations / five names. #92 and #48 remain open.

The [contexts report](cpp03-offsetof-contexts-verification.md) and preceding
stage reports remain historical and unchanged. Full local and exact-head CI
are separate gates for the new snapshot.

## Selected representation boundary

The optional offsetof field is
memberPointerStorage="itanium-x86_64-lp64-v1". Omission keeps member-pointer
storage unsupported. The policy selects these byte size/alignment facts:

- Data-member pointer: 8 / 8.
- Member-function pointer: 16 / 8.
- Pointer to a function-pointer data member: 8 / 8; the member has an ordinary
  function-pointer type, not a member-function type.

These facts are explicit selected representation rules. They are not inferred
from host properties, ordinary-pointer width, glibc or a compiler executable.
No pointer value, null encoding, adjustment thunk, member call or runtime
behavior is modeled by this layout extension.

A known incomplete or non-POD class owner can identify a member-pointer type
without supplying that owner's layout. The owner's body must not become a
by-value layout dependency merely because the member pointer names it.
Unresolved or dependent owner/category cases remain guarded. Known oversized owner bodies and parameter/return class bodies also require
no layout budget. Written type/signature/template syntax must nevertheless
resolve; its sizeof and constant dependencies remain bounded and survive
folding. A ceiling-crossing signature edit is rejected. Known owner identity
is not unconditional acceptance.

Existing explicit layout facts, supportedObjectBytes ceiling, size_t bounds,
snapshot/cycle guards, folding dependencies, source replay and pragma/operator
restrictions remain applicable. Enum storage and bitfields are outside this
packet. Generic sizeof, template matching and member-call semantics are not
claimed to change.

OffsetofModel gains a nullable String memberPointerStorage and preserves the
three-argument constructor/parse/Builder.offsetof APIs, with four-argument
overloads for explicit selection. Absent policy is omitted from settings.
Tests pin the previous omitted-policy profile identity
`c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10`.
No profile resource or golden fixture changed. The opt-in profile identity is
`62a0fae7a740ee50f76a6eabf1ac439f0ed7f112f07db78a9c4d99e0db15f60e`.

## Original edition and enum separation

Original C++03 18.1/5 source-reviewed ledger evidence remains the POD
structure/union basis for offsetof. The private ISO2003 PDF remains absent;
there has been no fresh private-source read. Original source hash and paragraph coordinates are unchanged.

Official [WG21 N1936](https://open-std.org/JTC1/SC22/WG21/docs/papers/2006/n1936.html)
(24 February 2006), issue 172, lists the enum-promotion resolution as WP, not
TC1. Its introduction distinguishes incorporated TC1 resolutions from other
proposals. The quoted original C++03 promotion rule uses the full underlying
type's range. Modern compiler enum promotion therefore cannot by itself prove
a defect in the original-edition model. This packet changes neither those
promotion rules nor the original underlying-type restriction. Member-pointer
representation proceeds independently of enum storage.

## Focused evidence and remaining gates

The isolated independent check passed 11 MemberPointerLayoutTest units and two
MemberPointerLayoutIT cases. Review verified 44 native execution records:
22 per compiler, 22 actual CPG materializations and 12 category-edited executions.
These categories overlap. The writtenSignatureDependenciesRemainBoundedAndEditableWithoutOwnerRecursion
unit checks a ceiling-crossing written signature dependency without recursing
into irrelevant class bodies. The manual shared-NamedType regression separates
identity-cycle checks from by-value layout recursion.

Frozen production manifest SHA256:
`2cb2cf9a8b43836b8afdd1f9c0884a75595551c95b4448cd433fe10b16e855b6`.
Native artifacts: target/member-pointer-layout-oracle. Test paths:
- src/test/java/com/kniazkov/cpg/analysis/MemberPointerLayoutTest.java
- src/test/java/com/kniazkov/cpg/e2e/MemberPointerLayoutIT.java

The initial checkpoint below is historical; the corrected checkpoint follows
in the harness section. Its command, run from the repository with the configured
Java/Maven and native compiler toolchain, was:

```sh
mvn -o -B clean -Dit.test=MemberPointerLayoutIT,OffsetofRuntimeIT,OffsetofContextsIT,MacroEditingIT,PackagedCliIT -DargLine=-Xmx768m verify
```

It passed all 1422 units and 15 explicitly selected IT in 09:54, with zero
failures, errors or skips. Packaged JAR SHA256:
`5707394da01a5b74896238908e72a325970996796ea22e5bf11e7e714a8a17e2`.
Strict javadoc -Werror -private -Xdoclint:all,-missing and GCC/Clang catalogue
probes passed on the same frozen production. These checks do not increase the
unit/IT totals.

Fresh native records use GCC Debian 14.2.0-19, target x86_64-linux-gnu, and
Clang Debian 19.1.7 (3+b1), target x86_64-pc-linux-gnu. Each compiler has 22
execution records. The 32 baseline records observe selected=16, functions=56,
callbacks=16, nonpods=16 and extent=17. The 12 category-edited records observe
selected=24, functions=56, callbacks=16, nonpods=16 and extent=25. Of all 44
records, 22 compile actual CPG materializations. Tested source hashes match
record metadata. These categories overlap and must not be summed.
No fresh individual header hashes or runtime version were captured. The
representation oracle does not certify a libc ABI.

No member-pointer values or calls, giant allocations, or fabricated owner
layouts are used. The selected-IT gate is not the full integration suite.
Corrective-commit full Maven and exact-head CI gates remain pending.

## Harness prerequisite and failed full-run history

The subsequent full local run passed all 1422 units but failed one of 139 IT
in 37:22: the existing Strstream GREEN/FOLDED CLI fixture exceeded its
45-second child deadline. That run remains failed. Matched isolated FOLDED
probes completed in 28.21/27.93 seconds on the current production and
30.13/29.82 seconds on the verified PR #122 baseline, all with exit 0 and
byte-identical generated code. These probes show no slowdown in the measured
cases; they neither identify the precise timeout cause nor establish a full
suite pass.

The narrowly approved harness correction gives only the GREEN/FOLDED CLI
children in runtimeSurvivesGreenFoldedCliJsonAndNativeGeneration an explicit
90-second budget. All assertions, other 45-second child limits and native
command limits are retained. Production manifest remains unchanged. Independent
review approved this exact test-only change.

The corrected clean checkpoint passed all 1422 units and 18 explicitly selected
IT in 11:15, with zero failures, errors or skips. StrstreamLibraryIT passed all
three cases in 128.8 seconds. The command, run from the repository, was:

```sh
mvn -o -B clean -Dit.test=StrstreamLibraryIT,MemberPointerLayoutIT,OffsetofRuntimeIT,OffsetofContextsIT,MacroEditingIT,PackagedCliIT -DargLine=-Xmx768m verify
```

Corrected checkpoint JAR SHA256:
`3d6d1b6fc118b6c8e98c0cfabd3ad3db311c66b6f13bbf74265df6e6eee1b228`.
Production manifest remains
`2cb2cf9a8b43836b8afdd1f9c0884a75595551c95b4448cd433fe10b16e855b6`.
Earlier overlapping checks are not added to this checkpoint. The failed full
run remains failed; this selected-IT success does not replace a full rerun.
Full Maven and new exact-head CI remain pending.

Исправленный чистый прогон прошёл все 1422 unit-теста и 18 явно выбранных IT
за 11:15; все три Strstream IT прошли за 128,8 секунды
без ошибок и пропусков. Прежний полный прогон остаётся неуспешным; новый
полный Maven и CI ещё ожидаются. Независимые 11 unit-тестов и два IT перекрывают этот
набор и не увеличивают число. Тела владельцев не требуют расчёта
размещения, но записанные зависимости типов и сигнатур остаются ограниченными.
Хранение enum, битовые поля и вызовы через указатели на члены не входят в этап.
offsetof остаётся open/partial, числа поддержки не меняются; #92 и #48 открыты.
