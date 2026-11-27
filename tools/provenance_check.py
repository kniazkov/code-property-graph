#!/usr/bin/env python3
"""Check statement provenance structure and, optionally, the private map.

Every statement must have a valid private locator, a SHA-256 fingerprint, and
an extraction date. With ``--private-map`` the tool also compares each statement
against the private statement-to-source map.
"""

from __future__ import annotations

import datetime
import re
import sys
from pathlib import Path

import yaml

from spec_common import language_dirs, load_statements

LOCATOR = re.compile(
    r"^(?:\d+(?:\.\d+)*|[A-E])/"
    r"(?:\d+|grammar|table:\d+|footnote:\d+|example:\d+|note:\d+)$"
)
FINGERPRINT = re.compile(r"^sha256:[0-9a-f]{64}$")


def load_private_map(path: Path) -> dict[str, dict]:
    document = yaml.safe_load(path.read_text(encoding="utf-8"))
    return {entry["id"]: entry for entry in document.get("statements", [])}


def main() -> int:
    private_map = None
    if "--private-map" in sys.argv:
        index = sys.argv.index("--private-map")
        try:
            private_map = load_private_map(Path(sys.argv[index + 1]))
        except (IndexError, OSError) as error:
            print(f"provenance_check: cannot read private map: {error}", file=sys.stderr)
            return 2

    problems: list[str] = []
    total = 0
    for language in language_dirs():
        for _, statement in load_statements(language):
            total += 1
            sid = statement.get("id", "<missing-id>")
            provenance = statement.get("provenance")
            if not isinstance(provenance, dict):
                problems.append(f"{sid}: missing provenance")
                continue
            locator = provenance.get("private_locator", "")
            fingerprint = provenance.get("fingerprint", "")
            extracted_at = provenance.get("extracted_at", "")
            if not LOCATOR.match(locator):
                problems.append(f"{sid}: invalid locator {locator!r}")
            if not FINGERPRINT.match(fingerprint):
                problems.append(f"{sid}: invalid fingerprint")
            try:
                datetime.date.fromisoformat(extracted_at)
            except ValueError:
                problems.append(f"{sid}: invalid extracted_at {extracted_at!r}")
            if private_map is not None:
                entry = private_map.get(sid)
                if entry is None:
                    problems.append(f"{sid}: missing from private map")
                else:
                    if entry.get("private_locator") != locator:
                        problems.append(f"{sid}: locator differs from private map")
                    if entry.get("fingerprint") != fingerprint:
                        problems.append(f"{sid}: fingerprint differs from private map")

    if problems:
        print(f"provenance_check: {len(problems)} problem(s)", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    suffix = " and private map" if private_map is not None else ""
    print(f"provenance_check: {total} statements verified{suffix}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
