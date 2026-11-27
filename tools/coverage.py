#!/usr/bin/env python3
"""Compute statement coverage from checks and verification references."""

from __future__ import annotations

import json
import sys

from spec_common import (
    REPORTS_DIR,
    language_dirs,
    load_checks,
    load_statements,
    relative,
)


def main() -> int:
    strict = "--strict" in sys.argv[1:]
    problems: list[str] = []

    for language in language_dirs():
        statements = [statement for _, statement in load_statements(language)]
        checked = {
            check.get("statement_id")
            for _, check in load_checks(language)
        }
        active = {
            check.get("statement_id")
            for _, check in load_checks(language)
            if check.get("status", "active") == "active"
        }
        normative = [statement for statement in statements if statement.get("normative") is True]
        covered = [
            statement
            for statement in normative
            if statement["id"] in checked or statement.get("verification")
        ]
        active_covered = [
            statement
            for statement in normative
            if statement["id"] in active or statement.get("verification")
        ]
        pending = [
            statement
            for statement in covered
            if statement["id"] not in active and not statement.get("verification")
        ]
        uncovered = [
            statement
            for statement in normative
            if statement not in covered
        ]

        report = {
            "language": language.name,
            "statements": len(statements),
            "normative": len(normative),
            "covered": len(covered),
            "active": len(active_covered),
            "pending": len(pending),
            "uncovered": len(uncovered),
            "uncovered_ids": [statement["id"] for statement in uncovered],
            "pending_ids": [statement["id"] for statement in pending],
        }
        target = REPORTS_DIR / language.name / "coverage.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

        print(
            f"{language.name}: {report['covered']}/{report['normative']} normative "
            f"statements covered ({report['active']} active, {report['pending']} pending), "
            f"{report['uncovered']} uncovered"
        )
        print(f"  report: {relative(target)}")

        if strict:
            for statement in uncovered:
                problems.append(
                    f"{language.name}: statement {statement['id']} has no check"
                )

    if problems:
        print(f"\n{len(problems)} coverage problem(s):", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
