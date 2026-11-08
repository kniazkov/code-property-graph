# C++03 #92: selected member-pointer storage for offsetof

## Packet status

This packet adds independently selected member-pointer representation facts to
the bounded offsetof query. The clean gate passed all 1422 unit tests and 15 explicitly selected IT in
09:54, with zero failures, errors or skips. Independent 11-unit/two-IT checks
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

The clean acceptance command, run from the repository with the configured
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
layouts are used. Full Maven verification is running and exact-head CI is
pending; the selected-IT gate is not the full integration suite.

Чистый прогон прошёл все 1422 unit-теста и 15 явно выбранных IT за 09:54
без ошибок и пропусков. Независимые 11 unit-тестов и два IT перекрывают этот
набор и не увеличивают число. Тела владельцев не требуют расчёта
размещения, но записанные зависимости типов и сигнатур остаются ограниченными.
Хранение enum, битовые поля и вызовы через указатели на члены не входят в этап.
offsetof остаётся open/partial, числа поддержки не меняются; #92 и #48 открыты.
