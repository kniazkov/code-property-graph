# Evidence publication conventions

[PROOF.md](../../../../PROOF.md) is generated from the same requirement records
and reciprocal source dispositions as the requirements document. The only extra
input, `proof-inspections.json`, maps a small sample of existing assertions.
It cannot store an execution result. Every mapping names a requirement/check,
repository path, method, exact assertion excerpt, full commit, precise property
and coverage limitations. This pilot maps three lexer assertions; it does not
claim their new proposed examples have run.

```sh
python3 scripts/render-proof.py
python3 scripts/render-proof.py --check
python3 scripts/test-render-proof.py
```

Run from the repository root. Generation validates before writing; `--check`
compares exact UTF-8/LF bytes without rewriting stale output. It neither executes
recorded commands nor reads standards. A missing file, revision, method string,
assertion excerpt or relationship fails validation. Method/excerpt presence in
one file does not prove that the assertion belongs to that method: review that
association and helper behavior explicitly. Source review and fixture tests
cannot manufacture a C++ conformance result.

## Three distinct relationships

- **Proposed:** intended inputs and expected behavior in a requirement card.
  No recorded execution is shown as a gap, even if a related test exists.
- **Inspected:** an existing exact assertion at a pinned revision. Inspection
  records have no pass/fail field and cannot alter the card's verification state.
- **Recorded execution:** the card's actual evidence, including check ID, path,
  assertion, command, revision and result. These are historical pinned records;
  report generation never reruns them or certifies the current head. Verify the
  original execution and coverage before adding evidence. The existing validator
  rejects incompatible states and merely planned checks marked passed.

An execution may cover only part of a proposed check. Keep verification partial
until all conditions and relevant exclusions are covered; do not infer coverage
from a test name, a passing unrelated assertion or a host compiler result.
Inspected assertions are not moved into evidence without execution. The current
pilot has no recorded execution evidence and retains `not_run` throughout.

## English test and scenario descriptions

When adding or revising relevant tests, write comments in English. Identify the
requirement ID, the input condition, the exact observable property and any
excluded cases. Put the comment beside the assertion or helper invocation whose
behavior it describes. A whole-method reference alone is insufficient.

For file scenarios, describe the input path, invocation/profile, expected output
or diagnostic, asserted property and limitations in English. Preserve the exact
revision and command when recording a result. Explain helper assertions and
negative cases; do not translate fixtures merely to populate this report.

The proof provides edition/locator-to-requirement navigation, links to pinned
assertions, and a test-file-to-requirement reverse table. Unprocessed source
coverage remains in the source index. Follow-up assertion execution/coverage
belongs to the existing topic owners (#202 for this lexer sample); #228 owns CI
integration and #229 independent traceability review. No new implementation
completion is implied by publishing this report.
