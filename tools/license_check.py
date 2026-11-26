#!/usr/bin/env python3
"""Detect verbatim n-gram matches against the private source corpus.

Set CPG_PRIVATE_CORPUS to the private vault directory. When the variable is
absent, the check is skipped so public CI can run without the source text.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

import yaml

NGRAM = 10
TEXT_EXTENSIONS = {
    ".md",
    ".yaml",
    ".yml",
    ".json",
    ".py",
    ".java",
    ".xml",
    ".ebnf",
    ".txt",
}
EXCLUDED_DIRS = {".git", "target", "reports", "generated", "grammar", "tables"}


def tokens(text: str) -> list[str]:
    return re.findall(r"[a-z0-9]+", text.lower())


def shingles(words: list[str]) -> set[str]:
    return {
        " ".join(words[index:index + NGRAM])
        for index in range(len(words) - NGRAM + 1)
    }


def corpus_shingles(root: Path) -> set[str]:
    result: set[str] = set()
    for path in root.rglob("*"):
        if path.is_file() and path.suffix.lower() in {".txt", ".md"}:
            try:
                text = path.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            result |= shingles(tokens(text))
    return result


def public_files(root: Path):
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in EXCLUDED_DIRS for part in path.parts):
            continue
        if path.suffix.lower() in TEXT_EXTENSIONS:
            yield path


def prose_text(path: Path) -> str:
    text = path.read_text(encoding="utf-8", errors="ignore")
    if path.suffix.lower() not in {".yaml", ".yml"} or "statements" not in path.parts:
        return text
    document = yaml.safe_load(text)
    if not isinstance(document, dict):
        return text
    parts: list[str] = []
    for statement in document.get("statements", []):
        if statement.get("type") in {"grammar", "table_row"}:
            continue
        for field in ("title", "statement", "subject", "condition", "requirement"):
            value = statement.get(field)
            if isinstance(value, str):
                parts.append(value)
        for value in statement.get("exceptions", []):
            if isinstance(value, str):
                parts.append(value)
    return " ".join(parts)


def main() -> int:
    strict = "--strict" in sys.argv[1:]
    corpus_setting = os.environ.get("CPG_PRIVATE_CORPUS")
    if not corpus_setting:
        print("license_check: CPG_PRIVATE_CORPUS is not set; skipped")
        return 0
    corpus = Path(corpus_setting)
    if not corpus.is_dir():
        print(f"license_check: {corpus} is not a directory; skipped")
        return 0
    known = corpus_shingles(corpus)
    if not known:
        print("license_check: no corpus text found; skipped")
        return 0

    root = Path(__file__).resolve().parent.parent
    matches: list[tuple[Path, int, str]] = []
    for path in public_files(root):
        text = prose_text(path)
        found = shingles(tokens(text)) & known
        if found:
            matches.append((path, len(found), sorted(found)[0]))

    if matches:
        print(f"license_check: {len(matches)} file(s) contain {NGRAM}-gram matches")
        for path, count, sample in matches:
            print(f"  - {path.relative_to(root)}: {count} match(es), e.g. {sample!r}")
        if strict:
            return 1
        print("license_check: report-only mode; pass --strict to fail")
        return 0
    print("license_check: no verbatim matches")
    return 0


if __name__ == "__main__":
    sys.exit(main())
