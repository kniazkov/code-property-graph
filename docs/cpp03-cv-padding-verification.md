# Original C++03 cv-qualified unnamed padding (#145)

Explicit `x86_64-sysv-cv-v10` inherits v9 storage and permits const, volatile
and const volatile unnamed bit-fields, including typedef chains and nonpublic
padding. Earlier profiles retain their rejection. Qualifiers remain written
in the graph and generated source; only storage shape ignores cv. Snapshot,
alias-cycle, object-size, leading-positive and bit-field-address guards remain.
No bit-field values, volatile accesses or promotions are introduced.

The existing original ISO2003 audit in
[unnamed layout](cpp03-unnamed-bit-field-layout-verification.md#original-language-boundaries)
records that original §9.6 does not contain the modern cv-unnamed prohibition.
[CWG2229](https://cplusplus.github.io/CWG/issues/2229.html), submitted in 2016
and accepted as a DR in March2018, adds it. This policy deliberately follows
original C++03, not that later resolution. No new original PDF review or
immutable source classification change is claimed. Allocation follows the
already documented v9 ABI algorithm; cv does not introduce a different storage
representation under this selected GCC-compatible contract.

Compiler disagreement is explicit: GCC accepts the selected strict C++03
positives; Clang19 rejects cv unnamed even in C++03 mode. We do not strip cv
for Clang or report its rejection as a positive layout result. The native
positive oracle here is GCC only. A separate Clang test requires a qualifier
diagnostic and stores the source/error. Existing prior-policy tests retain
GCC+Clang native coverage. This is a documented exception to the general
both-compilers-positive criterion, not universal compiler compatibility.

CvPaddingLayoutTest checks direct/typedef const, volatile and combined cv,
widths0/3/33/65/127, private padding, old-profile rejection, size/offset/alignment,
copy/GREEN/FOLDED/JSON and inherited edit/reparse/guard cases.
CvPaddingLayoutIT checks GCC original/generated/actual CPG-materialized macro
sources, CLI and negative guards; Clang rejection is separate. Native artifacts
under target/cv-padding-oracle retain commands, output, source/profile hashes.

Manual input and explicit profile: src/test/resources/cv-padding.

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input src/test/resources/cv-padding --cpp-standard c++03 --platform-profile src/test/resources/cv-padding/profile.json --stage folded --json target/cv-padding.json
mvn -DargLine=-Xmx768m -Dtest=CvPaddingLayoutTest,WideBitFieldLayoutTest -Dit.test=CvPaddingLayoutIT verify
```

Remaining #145: enum fields, leading-positive struct padding and widths>=128.
All five macro obligations remain partial/open. Final GitHub CI must be green;
the maintainer checks it and merges. Focused local results are not full CI.
