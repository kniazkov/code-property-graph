"""Shared helpers for the specification tools."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable

import yaml
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parent.parent
SCHEMA_DIR = ROOT / "schema"
LANGUAGES_DIR = ROOT / "languages"
REPORTS_DIR = ROOT / "reports"


def load_yaml(path: Path) -> Any:
    with path.open(encoding="utf-8") as stream:
        return yaml.safe_load(stream)


def load_schema(name: str) -> dict[str, Any]:
    with (SCHEMA_DIR / name).open(encoding="utf-8") as stream:
        return json.load(stream)


def validate(instance: Any, schema_name: str) -> list[str]:
    validator = Draft202012Validator(load_schema(schema_name))
    errors = sorted(validator.iter_errors(instance), key=lambda error: list(error.path))
    return [
        f"{'/'.join(str(part) for part in error.path) or '<root>'}: {error.message}"
        for error in errors
    ]


def language_dirs() -> list[Path]:
    if not LANGUAGES_DIR.is_dir():
        return []
    return sorted(path for path in LANGUAGES_DIR.iterdir() if path.is_dir())


def statement_files(language: Path) -> list[Path]:
    directory = language / "statements"
    return sorted(directory.glob("*.yaml")) if directory.is_dir() else []


def check_files(language: Path) -> list[Path]:
    directory = language / "checks"
    return sorted(directory.glob("*.yaml")) if directory.is_dir() else []


def test_files(language: Path) -> list[Path]:
    directory = language / "tests"
    return sorted(directory.rglob("*.yaml")) if directory.is_dir() else []


def load_statement_documents(language: Path) -> Iterable[tuple[Path, dict[str, Any]]]:
    for path in statement_files(language):
        document = load_yaml(path)
        if isinstance(document, dict):
            yield path, document


def load_statements(language: Path) -> Iterable[tuple[Path, dict[str, Any]]]:
    for path, document in load_statement_documents(language):
        for statement in document.get("statements", []):
            yield path, statement


def load_checks(language: Path) -> Iterable[tuple[Path, dict[str, Any]]]:
    for path in check_files(language):
        document = load_yaml(path)
        if isinstance(document, dict):
            yield path, document


def load_tests(language: Path) -> Iterable[tuple[Path, dict[str, Any]]]:
    for path in test_files(language):
        document = load_yaml(path)
        if isinstance(document, dict):
            yield path, document


def relative(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)
