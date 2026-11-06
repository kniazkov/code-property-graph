# Executed expression diagnostic audit probes

These are two manually executed CLI replay probes, not new automated regression tests.
They document missing diagnostics found while comparing the original ISO/IEC
14882:2003 source with the repository. No production code was changed.
The related JUnit methods cited in the JSONL shard were inspected, not rerun
as part of these probes. A recorded execution does not mean the rule is supported.

## Executable provenance

- Existing executable: `target/cpg-0.0.1-SNAPSHOT.jar`.
- SHA-256: `e5b3191500fb4d816e0060cfafe4bbddc401d75faa9a317c792b73612a75eefc`.
- Working tree HEAD when replayed: `12181d3afaaf2b7cd54cf33519a4a9049bdc74bb`.
- The existing JAR was **not rebuilt** for this audit. The HEAD is context, not
  proof that the JAR was built from that revision. The recorded hash identifies
  the executable used. Rebuild separately before claiming current-HEAD behavior.
- Both probes used `--stage resolved`, C++ input and the default target profile.
  The profile has 32-bit `int`/`unsigned int`.
- A recursive traversal collected every JSON `diagnostics` array. Both collected
  lists were empty. This is stronger than inferring acceptance from the exit code alone.

## typeid without the required header

Source locator: `cpp03:5.2.8:p6`.

Input written to `input.cpp`:

```cpp
int f(){typeid(int);return 0;}
```

Expected under the original 2003 rule: Reject: typeid occurs before the required `<typeinfo>` inclusion.

Actual command, executed 2026-11-06T11:17:05.080230+00:00:

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input /tmp/cpp03-exact-probe-typeid --stage resolved --json /tmp/cpp03-typeid-replay-1793963824555522297.json
```

Observed: exit code `0`, diagnostics `[]`, empty stderr.

Output JSON SHA-256: `e7eb0bedb17ebcb72c11fad683da709b7b6e22a77ea52dbdfe3a718301459a90`.

Inspected implementation: `NameResolver.java` checks typeid operand class completeness but has no prior-header check in that visitor. `ObjectMemoryValidationTest#typeidRequiresCompleteClassButAllowsPointersAndVoid` also expects no errors for pointer/void operands without including the header. This related test is source evidence, not a regression for the missing-header rule.

## Duplicate case values after conversion

Source locator: `cpp03:6.4.2:p2`.

Input written to `input.cpp`:

```cpp
void f(unsigned int x){switch(x){case -1: break; case 4294967295U: break;}}
```

Expected under the original 2003 rule: Reject: both case constants convert to the same unsigned-int value on the default 32-bit int profile.

Actual command, executed 2026-11-06T11:17:05.738849+00:00:

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input /tmp/cpp03-exact-probe-switch --stage resolved --json /tmp/cpp03-switch-replay-1793963825080332599.json
```

Observed: exit code `0`, diagnostics `[]`, empty stderr.

Output JSON SHA-256: `afe57bdee85002fb706837e606a9a288f7baebb3c4703e47b43d9a6671defbfb`.

On the default target, converting `-1` to `unsigned int` yields `4294967295U`, so these two labels violate the uniqueness requirement. Inspected implementation: `NameResolver.java` inserts raw `ConstantEvaluator` numbers into the switch value set without conversion to the promoted condition type. `Cpp03StatementScopesTest#switchLabelsHavePerSwitchIdentity` checks equal written values, not this converted-value collision.

## Reproduce using fresh output paths

Run from the repository root. This does not rebuild the JAR. A different JAR hash
must be reported as a new run rather than attributed to the observations above.
The output names must be new because the CLI refuses to overwrite an existing file.

```sh
sha256sum target/cpg-0.0.1-SNAPSHOT.jar
probe=$(mktemp -d)
mkdir "$probe/typeid" "$probe/switch"
printf '%s\n' 'int f(){typeid(int);return 0;}' > "$probe/typeid/input.cpp"
printf '%s\n' 'void f(unsigned int x){switch(x){case -1: break; case 4294967295U: break;}}' > "$probe/switch/input.cpp"
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input "$probe/typeid" --stage resolved --json "$probe/typeid.json"
printf 'typeid exit: %s\n' "$?"
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input "$probe/switch" --stage resolved --json "$probe/switch.json"
printf 'switch exit: %s\n' "$?"
python3 - "$probe/typeid.json" "$probe/switch.json" <<'PY_DIAGNOSTICS'
import json, sys
for path in sys.argv[1:]:
    diagnostics = []
    def visit(value):
        if isinstance(value, dict):
            diagnostics.extend(value.get("diagnostics", []))
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    with open(path) as source:
        visit(json.load(source))
    print(path, json.dumps(diagnostics))
PY_DIAGNOSTICS
```

Implementation fixes and dedicated negative regressions remain open. Neither probe establishes full support for its paragraph
or other rules in the same section.

## Subsequent regression work

The observations above are historical and unchanged. See
[DiagnosticRegressionResults.md](DiagnosticRegressionResults.md) for the later
fresh-build fixes, automated regressions, replay results, and remaining limits.
