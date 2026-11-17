# Leading unnamed padding: selected GCC behavior (#145)

Explicit `x86_64-gcc-leading-v13` inherits v12 and admits positive unnamed
bit-fields before the first named object/bit-field, including classes containing
only unnamed padding. Earlier profiles retain their guard. Allocation starts
at cursor zero and uses the existing integral/enum/cv/oversized rules. Unnamed
padding never becomes an addressable member. Named private fields and invalid
bit-field designators remain rejected; object ceilings and dependencies remain.

## Normative boundary — deliberately not erased

The original source-reviewed audit records C++03 §9.2/17's initial-member
pointer guarantee and §9.6/2's statement that unnamed bit-fields are not members.
A leading positive field followed by an ordinary member exposes a tension:
GCC places the ordinary member at a positive byte offset. We have NOT proved
that this behavior satisfies the original pointer guarantee. This profile
selects observable GCC layout, not a resolution of the language wording.

Primary follow-up: [CWG2254](https://cplusplus.github.io/CWG/issues/2254.html),
read 2026-11-17, addresses later standard-layout/bit-field pointer wording.
It is not a resolution of this original C++03 leading-unnamed problem and must
not be presented as one. The original ISO PDF was not freshly reviewed here;
prior source identities and immutable ledger classifications stay unchanged.
The normative follow-up belongs to the existing open
[#99](https://github.com/kniazkov/code-property-graph/issues/99) language evidence
audit and final #48 acceptance. This handoff is not a standard-conformance claim.

Clients selecting v13 may use ordinary-member offsets for this compiler
contract, but must not infer object/first-named-member pointer equivalence or
fold reinterpret_cast from them. No such pointer optimization is added here.
There is no attempt to take the address of a bit-field in native tests.

## Fixed facts and acceptance

| Declaration | Ordinary tail offset | Size |
|---|---:|---:|
| unsigned int :3; char tail; | 1 | 2 |
| unsigned int :3; unsigned int :0; char tail; | 4 | 5 |
| bool :128; char tail; | 16 | 32 |
| unsigned int :3; (no named field) | not applicable | 1 |

A char followed by two objects of the last type has its following char at
byte3. An enum/cv/private unnamed field uses the inherited selected contracts.

LeadingPaddingLayoutTest checks fixed offsets, padding-only nesting, barriers,
first named bit-fields, aliases/cv/enum, previous-policy rejection, profile
round-trips, copy/GREEN/FOLDED/JSON, edits/reversal/reparse and guards.
LeadingPaddingLayoutIT checks GCC original/generated/CPG-materialized macros,
CLI and rejected operands. Inherited Clang disagreement/DR tests stay in CI.
Native artifacts: target/leading-padding-oracle.

```sh
mvn -DargLine=-Xmx768m -Dtest=LeadingPaddingLayoutTest,EnumBitFieldLayoutTest,UnnamedBitFieldLayoutTest -Dit.test=LeadingPaddingLayoutIT verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input src/test/resources/leading-padding --cpp-standard c++03 --platform-profile src/test/resources/leading-padding/profile.json --stage folded --json target/leading.json
```

This completes the selected implementation families listed in #145, subject
to final green CI and maintainer acceptance. It does not close #99/#48 or turn
the five macro obligations from partial/open into universal support. The #145
checklist remains pending until CI acceptance; no issue is auto-closed here.
