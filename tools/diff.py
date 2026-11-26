#!/usr/bin/env python3
"""Compare two statement directories by statement identifier."""

from __future__ import annotations

import sys
from pathlib import Path

from spec_common import load_yaml


def load_directory(path: Path) -> dict[str, dict]:
    statements: dict[str, dict] = {}
    for file in sorted(path.glob("*.yaml")):
        document = load_yaml(file)
        for statement in document.get("statements", []):
            statements[statement["id"]] = statement
    return statements


def main() -> int:
    arguments = [argument for argument in sys.argv[1:] if not argument.startswith("--")]
    fail = "--fail" in sys.argv[1:]
    if len(arguments) != 2:
        print("usage: diff.py [--fail] OLD_DIR NEW_DIR", file=sys.stderr)
        return 2

    old = load_directory(Path(arguments[0]))
    new = load_directory(Path(arguments[1]))
    added = sorted(new.keys() - old.keys())
    removed = sorted(old.keys() - new.keys())
    changed = sorted(
        key for key in old.keys() & new.keys() if old[key] != new[key]
    )

    print(f"added: {len(added)}")
    for key in added:
        print(f"  + {key}")
    print(f"removed: {len(removed)}")
    for key in removed:
        print(f"  - {key}")
    print(f"changed: {len(changed)}")
    for key in changed:
        print(f"  ~ {key}")

    if fail and (added or removed or changed):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
