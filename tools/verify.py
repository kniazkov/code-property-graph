#!/usr/bin/env python3
"""Summarise checks and verify their wiring.

The script does not execute parser checks yet. It verifies that checks are
linked to statements and tests, and reports the oracle and trust distribution.
"""

from __future__ import annotations

import sys
from collections import Counter

from spec_common import language_dirs, load_checks, load_statements, load_tests


def main() -> int:
    strict = "--strict" in sys.argv[1:]
    problems: list[str] = []

    for language in language_dirs():
        statements = {statement["id"]: statement for _, statement in load_statements(language)}
        checks = [check for _, check in load_checks(language)]
        tests = [test for _, test in load_tests(language)]
        tested_statements = {test.get("statement_id") for test in tests}

        by_kind = Counter(check.get("kind") for check in checks)
        by_trust = Counter(check.get("trust") for check in checks)
        without_tests = [
            check
            for check in checks
            if check.get("statement_id") not in tested_statements
        ]
        low_trust = [check for check in checks if check.get("trust") == "low"]

        print(f"{language.name}: {len(checks)} checks, {len(tests)} tests")
        print(f"  kinds: {dict(sorted(by_kind.items()))}")
        print(f"  trust: {dict(sorted(by_trust.items()))}")
        print(f"  checks without tests: {len(without_tests)}")
        print(f"  low-trust checks: {len(low_trust)}")

        if strict:
            for check in without_tests:
                problems.append(
                    f"{language.name}: check {check.get('check_id')} has no test"
                )
            for check in low_trust:
                problems.append(
                    f"{language.name}: check {check.get('check_id')} has trust low"
                )

    if problems:
        print(f"\n{len(problems)} problem(s):", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    print("\nverification wiring passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
