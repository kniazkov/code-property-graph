#!/usr/bin/env python3
"""Validate schemas, identifiers, and references."""

from __future__ import annotations

import sys
from pathlib import Path

from spec_common import (
    language_dirs,
    load_checks,
    load_statement_documents,
    load_tests,
    load_yaml,
    relative,
    validate,
)


def main() -> int:
    errors: list[str] = []
    statement_ids: dict[str, Path] = {}
    check_ids: dict[str, Path] = {}
    test_ids: dict[str, Path] = {}
    summary: list[str] = []

    for language in language_dirs():
        manifest_path = language / "language.yaml"
        if not manifest_path.is_file():
            errors.append(f"{relative(manifest_path)}: missing language manifest")
            continue
        try:
            manifest = load_yaml(manifest_path)
        except Exception as exception:  # noqa: BLE001
            errors.append(f"{relative(manifest_path)}: {exception}")
            continue
        for field in ("id", "name", "edition", "id_prefix"):
            if not manifest.get(field):
                errors.append(f"{relative(manifest_path)}: missing field {field}")

        statements: dict[str, dict] = {}
        try:
            for path, document in load_statement_documents(language):
                for message in validate(document, "statement.schema.json"):
                    errors.append(f"{relative(path)}: {message}")
                for statement in document.get("statements", []):
                    statement_id = statement.get("id")
                    if not isinstance(statement_id, str):
                        errors.append(f"{relative(path)}: statement without string id")
                        continue
                    if statement_id in statement_ids:
                        errors.append(
                            f"{relative(path)}: duplicate statement id {statement_id}"
                        )
                    else:
                        statement_ids[statement_id] = path
                    statements[statement_id] = statement
        except Exception as exception:  # noqa: BLE001
            errors.append(f"{relative(language)}: {exception}")

        for statement_id, statement in statements.items():
            for reference in statement.get("depends_on", []) + statement.get("related", []):
                if reference not in statements:
                    errors.append(
                        f"{statement_id}: unknown reference {reference}"
                    )

        try:
            for path, check in load_checks(language):
                for message in validate(check, "check.schema.json"):
                    errors.append(f"{relative(path)}: {message}")
                check_id = check.get("check_id")
                if not isinstance(check_id, str):
                    errors.append(f"{relative(path)}: check without string id")
                    continue
                if check_id in check_ids:
                    errors.append(f"{relative(path)}: duplicate check id {check_id}")
                else:
                    check_ids[check_id] = path
                if check.get("statement_id") not in statements:
                    errors.append(
                        f"{relative(path)}: unknown statement {check.get('statement_id')}"
                    )
        except Exception as exception:  # noqa: BLE001
            errors.append(f"{relative(language)}: {exception}")

        try:
            for path, test in load_tests(language):
                for message in validate(test, "test.schema.json"):
                    errors.append(f"{relative(path)}: {message}")
                test_id = test.get("test_id")
                if not isinstance(test_id, str):
                    errors.append(f"{relative(path)}: test without string id")
                    continue
                if test_id in test_ids:
                    errors.append(f"{relative(path)}: duplicate test id {test_id}")
                else:
                    test_ids[test_id] = path
                if test.get("statement_id") not in statements:
                    errors.append(
                        f"{relative(path)}: unknown statement {test.get('statement_id')}"
                    )
        except Exception as exception:  # noqa: BLE001
            errors.append(f"{relative(language)}: {exception}")

        summary.append(
            f"{manifest.get('id', language.name)}: "
            f"{len(statements)} statements, {len(check_ids)} checks, "
            f"{len(test_ids)} tests"
        )

    for line in summary:
        print(line)
    if errors:
        print(f"\n{len(errors)} validation error(s):", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print("\nvalidation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
