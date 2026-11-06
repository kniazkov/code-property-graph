# Printed-2003 nested access probe

`NestedAccessProbe.java` is a read-only diagnostic probe for ISO/IEC 14882:2003
11.8/1. It changes no production code and makes no claim of full clause coverage.

After building the project, run with JDK 21:

```sh
mkdir -p /tmp/cpp03-nested-probe
java -m jdk.compiler/com.sun.tools.javac.Main \
  -cp target/cpg-0.0.1-SNAPSHOT.jar -d /tmp/cpp03-nested-probe \
  docs/cpp03-paragraphs/probes/NestedAccessProbe.java
java -cp target/cpg-0.0.1-SNAPSHOT.jar:/tmp/cpp03-nested-probe NestedAccessProbe
```

Observed on 2026-11-06 against the existing build artifact with SHA-256
`e5b3191500fb4d816e0060cfafe4bbddc401d75faa9a317c792b73612a75eefc`
(checkout HEAD `98de89020d236e8faabe0fef68c3ab282ef501ac`):

```text
nested-method: [inaccessible-overload]
nested-field: []
enclosing-method: [inaccessible-overload]
enclosing-field: []
nested-type: []
```

The printed paragraph denies special private access in either direction. The
method cases are diagnosed; both field cases and the nested use of an enclosing
private typedef lack a diagnostic. `NameResolver.checkSelectedAccess` validates
selected callable access, which does not establish equivalent field/type checks.
This is incomplete enforcement, not evidence of a deliberate later-defect-report
profile. No particular later correction has been verified here. The probe's
observations are not a full Maven run or a generation/round-trip test.


## Subsequent work

The output above remains the historical observation of that exact artifact.
The separate [#89 regression report](NestedAccessRegressionResults.md) records
the original-2003 access fix and its validation gates; it does not attribute new
behavior to the old JAR or claim complete clause coverage.
