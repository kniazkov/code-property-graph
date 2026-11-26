# Specification tools

Python 3.10+ tools for validating and reporting on the specification data.

## Install

```text
python -m pip install -r tools/requirements.txt
```

## Commands

```text
python tools/validate.py
```

Validates every statement, check, and test against the JSON schemas, checks
identifier uniqueness, and verifies `depends_on` and `related` references.

```text
python tools/coverage.py [--strict]
```

Writes `reports/<language>/coverage.json` and prints statement coverage.
`--strict` fails when a normative statement has no check. Waivers are not used
in this project.

```text
python tools/verify.py [--strict]
```

Summarises checks by kind and trust, and reports checks without tests.
`--strict` fails on checks without tests or with `trust: low`.

```text
python tools/diff.py [--fail] OLD_DIR NEW_DIR
```

Compares two statement directories by identifier and reports added, removed, and
changed statements.

```text
python tools/license_check.py [--strict]
```

Compares public prose with the private corpus using 10-word shingles. Grammar
files, table files, and grammar/table statements are excluded. Set
`CPG_PRIVATE_CORPUS` to the private vault directory. Without the variable the
check is skipped, so public CI does not need the source text. The default mode
reports matches; `--strict` fails on any match.

## Exit codes

- `0` — success or a skipped check.
- `1` — validation, coverage, or license problem.
- `2` — invalid command-line usage.
