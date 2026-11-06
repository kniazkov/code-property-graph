# Original-2003 nested-access regressions (#89)

This follow-up addresses [issue #89](https://github.com/kniazkov/code-property-graph/issues/89),
principally `cpp03:11.8:p1`, selected-declaration checking in `cpp03:11:p4`,
and the informative example `cpp03:11.8:p2`. The historical
[probe report](README.md) retains its original artifact hash and observations.
Its JAR is not evidence for the updated branch. No source PDF or full normative
text is redistributed here.

## Edition and bounded behavior

The reference is the supplied printed ISO/IEC 14882:2003, printed page 188
(PDF page 216). The original wording denies special access in both directions
between nested and enclosing members, including friendship inherited merely
through an enclosing class. This fix deliberately uses that profile; a modern
compiler accepting a later correction is not an oracle for the old negative
cases. No later defect-report profile is claimed.

- Access is checked after selection, using the actual declaration and nearest
  class context. Failed access does not discard the selected binding or choose
  an accessible overload/namesake instead. Ambiguity is not access failure.
- Selected callable access retains `inaccessible-overload`. Field/variable uses
  use `inaccessible-member`; written named types and aliases use
  `inaccessible-type`. A public alias is not rejected merely because the alias
  definition refers to a private type.
- Positive cases distinguish public access, a class's own members and explicitly
  befriended classes/functions. A nested class does not inherit its enclosing
  class's friendship; matching a friend's spelling alone is insufficient.
- The §11.8/2 note distinguishes a nested declaration's base-clause from its
  members. A private enclosing base name is allowed in the base-clause;
  unqualified injected names of the base/current class remain usable. Explicit
  enclosing qualification of a private name does not get that exemption.
- Qualification is not globally forbidden: a public derived qualifier can expose
  an inherited injected base name. Private inheritance still blocks the
  corresponding outside use. Distinct inherited declarations remain ambiguous;
  repeated paths to the same declaration retain that identity.
- Diagnostic ranges are the current graph node's source span: field/member or
  call expression, or written named type. Existing parser spans for typedef
  declarations include the `typedef` prefix. These tests do not imply a global
  redesign of parser token ranges.
- Rebuilding/copying and resolving recomputes access diagnostics. Changing access
  to public removes the affected stale diagnostic without changing the original
  snapshot. This does not establish arbitrary incremental-edit equivalence.

## Assertion map

`src/test/java/com/kniazkov/cpg/NestedAccessSemanticsTest.java` covers:

- The original five probe cases, individually: `nestedMethodHasNoEnclosingPrivatePrivilege`,
  `enclosingMethodHasNoNestedPrivatePrivilege`, `nestedFieldHasNoEnclosingPrivatePrivilege`,
  `enclosingFieldHasNoNestedPrivatePrivilege`,
  `privateTypedefInNestedDeclaratorIsDiagnosedAndStillBound`.
- Written qualified/unqualified types in ordinary, pointer, array and typedef
  declarators; selected declaration/alias bindings, private static field names,
  shadowing and the inaccessible best overload rather than a worse public one.
- Self/public access, direct friend classes and inline/out-of-line friend
  functions; nested friendship and same-spelled namespace/nested-class negatives.
- `original2003BaseClauseAndInjectedNamesRemainAccessible` and
  `qualifiedEnclosingNamesDoNotGetInjectedNameAccessExemption` for the note.
- Qualified public/private inheritance checks and distinct-declaration ambiguity,
  plus `commonInjectedBaseIdentitySurvivesCopyAndRebuildWithoutOldGraphEdges`
  assert binding identity and snapshot-owned resolution-step edges.
- `copiesRebuildAndAccessEditsRemoveStaleDiagnostics` for method, field and alias
  access edits and immutable original snapshots.

`src/test/java/com/kniazkov/cpg/e2e/NestedClassAccessIT.java` covers:

- `allFiveAuditProbesReachCliJsonAndBlockExport`: the original five inputs through
  API and CLI JSON, expected codes/ranges, and refused generation of invalid input.
- `qualifiedAndUnqualifiedTypesAndFieldsRetainExactMultilineRanges`: multiline
  qualified/unqualified type and field cases, exact file/offset/line/column spans.
- `validPublicSelfFriendAndInjectedNamesGenerateAndRunOnBothCompilers`: valid
  public/self/friend/injected-name programs, API/CLI generation equality and strict native
  compilation/execution. Native tests are positive portable cases only.

An assertion map identifies test intent, not evidence that a test has run.
Execution records below must identify the artifact and result separately.

## Reproduction

Use JDK 21 and the repository's Maven prerequisites. The normal focused commands
are:

```sh
mvn -Dtest=NestedAccessSemanticsTest test
mvn -DskipTests package
mvn -Dit.test=NestedClassAccessIT failsafe:integration-test failsafe:verify
```

The integration tests require both `g++` and `clang++` and use
`-std=c++03 -pedantic-errors`. They exercise the freshly packaged
`target/cpg-0.0.1-SNAPSHOT.jar`; do not substitute the historical audit JAR.
The full acceptance command remains `mvn verify`.

For a direct replay of the original probe against the newly packaged artifact:

```sh
mkdir -p /tmp/cpp03-nested-regression
java -m jdk.compiler/com.sun.tools.javac.Main \
  -cp target/cpg-0.0.1-SNAPSHOT.jar -d /tmp/cpp03-nested-regression \
  docs/cpp03-paragraphs/probes/NestedAccessProbe.java
sha256sum target/cpg-0.0.1-SNAPSHOT.jar
java -cp target/cpg-0.0.1-SNAPSHOT.jar:/tmp/cpp03-nested-regression NestedAccessProbe
git rev-parse HEAD
python3 -B scripts/check-cpp03-paragraphs.py
python3 -B scripts/test-cpp03-paragraphs.py
```

The corrected five-case output must be:

```text
nested-method: [inaccessible-overload]
nested-field: [inaccessible-member]
enclosing-method: [inaccessible-overload]
enclosing-field: [inaccessible-member]
nested-type: [inaccessible-type]
```

## Execution evidence and outstanding gates

- Documentation validation on 2026-11-06: `python3 -B scripts/check-cpp03-paragraphs.py`
  passed with 5,250 records and zero errors;
  `python3 -B scripts/test-cpp03-paragraphs.py` passed all 13 tests. These check
  record/schema/inventory/evidence-link consistency, not implementation behavior.
- Implementation/API regression revision:
  `318c1edeb390cb948103b534c201858a56bdf436`; CLI regression revision:
  `c628aa23c79ae8fd97f0a6359540ecd6f53d389e` (included by the implementation commit).
- Fresh production/test compilation used JDK 21 and ECJ, with the repository JSON
  dependency and JUnit console launcher. The local helper
  `/tmp/cpp03-tools/compile.sh` compiles all `src/main/java` and `src/test/java`
  sources, and packages the main classes plus JSON dependency into an executable
  JAR. This is an alternative local harness, not a Maven lifecycle run.
- Artifact: `/tmp/cpp03-local.c6tuAx/cpg.jar`; SHA-256:
  `39c60179a2edfc4cbb682a5990064c00149633d8db5cacceee054b882ac0194d`.
- Initial implementation focused run (before the review fixes below): **250/250 passed**, zero skipped, aborted or failed; 23/23
  containers successful, 17,672 ms. This includes all 22 nested-access API tests,
  the parser package, class initialization, inheritance, overload resolution,
  edited diagnostics and all three `NestedClassAccessIT` methods. Local log:
  `/tmp/cpp03-tools/focused89-final-corrected.log`.
- The first combined console invocation used `cpg.jar` instead of `cpg.e2e.jar`:
  all 247 unit tests passed, but all three integration methods failed at harness setup with
  `Run via mvn verify`. This is not recorded as a product-behavior pass.
- Earlier separately compiled integration run passed all three methods (16
  invalid cases and 16 strict native compile/runs) on
  `/tmp/cpp03-local.ZQyXSG/cpg.jar`, SHA-256
  `650759a1fc71712245857febd9ac4daeed15c339c52b7b88f2ec709c6a8ed36f`.
  This interim result does not replace the final combined artifact check.
- Literal `mvn verify` on final revision: pending; the completed alternative local
  aggregate is recorded in the final section below.
- Required CI on exact final PR head: pending; no CI pass or URL is invented.

An equivalent focused replay with the recorded local harness artifacts is:

```sh
build=/tmp/cpp03-local.c6tuAx
PATH=/tmp/cpp03-clang-bin:$PATH \
CPATH=/usr/lib/gcc/x86_64-linux-gnu/14/include \
java -Xmx768m -Dcpg.e2e.jar="$build/cpg.jar" \
  -jar /tmp/cpp03-tools/junit.jar execute \
  --class-path "$build/classes:$build/test-classes:/tmp/cpp03-tools/json.jar:src/test/resources" \
  --select-package com.kniazkov.cpg.parser \
  --select-class com.kniazkov.cpg.ClassInitializationSemanticsTest \
  --select-class com.kniazkov.cpg.InheritanceSemanticsTest \
  --select-class com.kniazkov.cpg.OverloadResolutionTest \
  --select-class com.kniazkov.cpg.EditedDiagnosticsTest \
  --select-class com.kniazkov.cpg.NestedAccessSemanticsTest \
  --select-class com.kniazkov.cpg.e2e.NestedClassAccessIT
```

The `/tmp` files are local evidence artifacts, not repository prerequisites or
published downloads. For another checkout, use the Maven commands above with
JDK 21 and both strict native compilers installed.

The surrounding paragraphs retain partial/not-applicable evidence status.
General access paths, every protected/multiple-inheritance case, all dependent
names/templates and every declaration form are not established by this package.
Neither existing historical counts nor focused successes imply full C++03 coverage.


## Independent review follow-up

Review fixes at `28f4a8e9402ed8df96e623c4d82f637c0f0a3ec7` address two
lookup boundaries without broadening the original-2003 profile:

- A same-named data member hides the injected class name in expression lookup.
  Public member reads retain the field binding; a private field cannot evade
  access checking by being mistaken for an accessible class name. Type contexts,
  including an elaborated class specifier, still bind to the class.
- Friend-class lookup respects declarations introduced by a using-declaration
  and inherited member-class declarations. A using-directive alone does not
  grant the similarly named namespace class friendship. The chosen declaration,
  not its spelling, controls the access result.

Four additional `NestedAccessSemanticsTest` methods assert these boundaries:
`friendClassLookupIncludesUsingDeclarationsAndInheritedClasses`,
`usingDirectiveDoesNotGrantNamespaceClassFriendship`,
`sameNamedFieldWinsExpressionLookupAndCopiesKeepOwnedBindings`, and
`sameNamedPrivateFieldCannotMasqueradeAsAccessibleInjectedClassName`.
They include copy/re-resolution assertions and snapshot-owned bindings.

Independent API rerun: **26/26 passed**. The fresh combined focused run at that
revision passed **254/254**, zero skipped, aborted or failed; 23/23 containers
successful, 20,869 ms. It includes those 26 API methods and the same three
integration methods (16 invalid cases and 16 strict native compile/runs).
This supersedes the earlier 250-test result for the updated implementation;
the earlier output and harness failure remain historical evidence above.

- Fresh artifact: `/tmp/cpp03-local.0PYHGW/cpg.jar`.
- SHA-256: `425f113b08582859a1796ec7c4be48ce0513bb06b460c94831472a8d072a8e08`.
- Executed local command:
  `/tmp/cpp03-tools/verify89-focused.sh /tmp/cpp03-local.0PYHGW`.
- Log: `/tmp/cpp03-tools/focused89-reviewed.log`.

The persisted command runs the following invocation from the repository root:

```sh
build=/tmp/cpp03-local.0PYHGW
export PATH=/tmp/cpp03-clang-bin:$PATH
export CPATH=/usr/lib/gcc/x86_64-linux-gnu/14/include
java -Xmx768m -Dcpg.e2e.jar="$build/cpg.jar" \
  -jar /tmp/cpp03-tools/junit.jar execute \
  --class-path "$build/classes:$build/test-classes:/tmp/cpp03-tools/json.jar" \
  --select-package com.kniazkov.cpg.parser \
  --select-class com.kniazkov.cpg.ClassInitializationSemanticsTest \
  --select-class com.kniazkov.cpg.InheritanceSemanticsTest \
  --select-class com.kniazkov.cpg.OverloadResolutionTest \
  --select-class com.kniazkov.cpg.EditedDiagnosticsTest \
  --select-class com.kniazkov.cpg.NestedAccessSemanticsTest \
  --select-class com.kniazkov.cpg.e2e.NestedClassAccessIT --details summary
```

At the focused-review documentation snapshot, the full local aggregate was still
running. Its completed result is recorded below. Review clearance and focused
passes do not imply exhaustive paragraph coverage or complete C++03 conformance.

## Completed local aggregate and JavaDoc

The full local aggregate for source revision
`28f4a8e9402ed8df96e623c4d82f637c0f0a3ec7` passed **1,334/1,334 tests**,
with zero skipped, aborted or failed tests. All 152 containers succeeded;
elapsed time was 1,299,218 ms.

- Log: `/tmp/cpp03-tools/full89-reviewed.log`.
- Fresh aggregate artifact: `/tmp/cpp03-local.JYVycO/cpg.jar`.
- SHA-256: `19bf157f4fef9c98e87a38be58441c60fb9b4253e8b4a2ecb82042ac143a12bb`.
- Strict JavaDoc validation passed, including private implementation classes;
  log: `/tmp/cpp03-tools/javadoc89.log`.

This is the completed local compilation/JUnit aggregate, not a claim that the
literal Maven lifecycle ran. The standard `mvn verify` and required CI for the
exact final PR head remain outstanding until separately verified. Earlier
focused artifact hashes identify different packaged artifacts and must not be
substituted for this aggregate's JAR. The passing aggregate does not expand the
bounded semantic claims or promote the paragraph ledger to full conformance.
