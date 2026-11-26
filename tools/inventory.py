#!/usr/bin/env python3
"""Build the C++03 clause map from the private N1638 TXT source.

The tool reads the private source text but never writes any source text to the
public repository. It produces:

- a public clause map with titles, page ranges, structural counts, batches,
  owners, and candidate cross-clause references;
- a private map with line ranges and SHA-256 fingerprints per subclause;
- a coverage baseline report.

Run it with the private source path:

    python tools/inventory.py --source path/to/wg21-n1638.txt
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from dataclasses import dataclass, field
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
LANGUAGE_DIR = ROOT / "languages" / "cpp03"
INVENTORY = LANGUAGE_DIR / "inventory.yaml"
PUBLIC_OUT = LANGUAGE_DIR / "clause-map.yaml"
REPORT_OUT = ROOT / "reports" / "cpp03" / "inventory.json"

DASH = "\u2212\u2013\u2014-"
HEADER_MARKER = "DRAFT: 11 April 2004"
START_PAGE_RE = re.compile(rf"^\s*((?:\d+|[A-E]))[{DASH}](\d+)\b")
END_PAGE_RE = re.compile(rf"\b((?:\d+|[A-E]))[{DASH}](\d+)\s*$")
HEADING_RE = re.compile(
    r"^\s{0,6}((?:[A-E]|\d+)(?:\.\d+)*)\s+(\S.*?)\s+\[([A-Za-z0-9._]+)\]\s*$"
)
ANNEX_RE = re.compile(r"^\s*Annex\s+([A-E])\s+\((informative|normative)\)\s*$")
TAG_RE = re.compile(r"\[([A-Za-z0-9._]+)\]\s*$")
PARAGRAPH_RE = re.compile(r"^\d+\s{2,}\S")
PRODUCTION_RE = re.compile(r"^\s{2,}[a-z][a-z0-9-]*:\s*$")
TABLE_RE = re.compile(rf"^\s*Table\s+\d+[{DASH}]")
NUMBER_REF_RE = re.compile(r"\((\d+(?:\.\d+)*)\)")
CLAUSE_REF_RE = re.compile(r"\bclause\s+(\d+)\b")
ANNEX_REF_RE = re.compile(r"\bAnnex\s+([A-E])\b")

LIBRARY_CLAUSES = {str(number) for number in range(17, 28)}


@dataclass
class Subclause:
    number: str
    title: str
    tag: str
    informative: bool
    page_start: str = ""
    page_end: str = ""
    line_start: int = 0
    line_end: int = 0
    paragraphs: int = 0
    grammar: int = 0
    tables: int = 0
    examples: int = 0
    notes: int = 0
    fingerprint: str = ""
    references: list[str] = field(default_factory=list)

    @property
    def top(self) -> str:
        return self.number.split(".")[0]


def normalize(lines: list[str]) -> str:
    text = " ".join(lines)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def fingerprint(lines: list[str]) -> str:
    digest = hashlib.sha256(normalize(lines).encode("utf-8")).hexdigest()
    return f"sha256:{digest}"


def page_of(token: str) -> str:
    token = token.strip()
    return token.replace("\u2212", "-").replace("\u2013", "-").replace("\u2014", "-")


def parse(source: Path) -> list[Subclause]:
    raw = source.read_text(encoding="utf-8").splitlines()
    page_by_line: list[str] = []
    current_page = ""
    for line in raw:
        if HEADER_MARKER in line:
            match = START_PAGE_RE.search(line) or END_PAGE_RE.search(line)
            if match:
                current_page = page_of(match.group(0))
        page_by_line.append(current_page)

    headings: list[tuple[int, Subclause]] = []
    index_start = next(
        (i for i, line in enumerate(raw) if line.strip() == "Index"),
        len(raw),
    )
    index = 0
    while index < len(raw):
        line = raw[index]
        match = HEADING_RE.match(line)
        if match:
            number, title, tag = match.group(1), match.group(2).strip(), match.group(3)
            headings.append(
                (index, Subclause(number=number, title=title, tag=tag, informative=False))
            )
            index += 1
            continue
        annex = ANNEX_RE.match(line)
        if annex:
            number = annex.group(1)
            informative = annex.group(2) == "informative"
            lookahead = index + 1
            while lookahead < len(raw) and not raw[lookahead].strip():
                lookahead += 1
            title, tag = "", ""
            if lookahead < len(raw):
                candidate = raw[lookahead]
                tag_match = TAG_RE.search(candidate)
                if tag_match:
                    tag = tag_match.group(1)
                    title = candidate[: tag_match.start()].strip()
            headings.append(
                (
                    lookahead,
                    Subclause(
                        number=number,
                        title=title,
                        tag=tag,
                        informative=informative,
                    ),
                )
            )
            index = lookahead + 1
            continue
        index += 1

    for position, (line_index, subclause) in enumerate(headings):
        end = headings[position + 1][0] if position + 1 < len(headings) else len(raw)
        end = min(end, index_start)
        body: list[str] = []
        last_content = line_index
        for cursor in range(line_index + 1, end):
            line = raw[cursor]
            if HEADER_MARKER in line:
                continue
            if line.strip():
                last_content = cursor
            body.append(line.replace("\f", " ").rstrip())
        subclause.line_start = line_index + 1
        subclause.line_end = last_content + 1
        subclause.page_start = page_by_line[line_index]
        subclause.page_end = page_by_line[last_content]
        top = subclause.top
        if not subclause.page_start or subclause.page_start.split("-")[0] != top:
            subclause.page_start = f"{top}-1"
        if not subclause.page_end or subclause.page_end.split("-")[0] != top:
            subclause.page_end = subclause.page_start
        subclause.paragraphs = sum(1 for line in body if PARAGRAPH_RE.match(line))
        subclause.grammar = sum(1 for line in body if PRODUCTION_RE.match(line))
        subclause.tables = sum(1 for line in body if TABLE_RE.match(line))
        subclause.examples = sum(1 for line in body if "[Example" in line)
        subclause.notes = sum(1 for line in body if "[Note" in line)
        subclause.fingerprint = fingerprint(body)
        subclause.references = collect_references(body, subclause.top)
    return [subclause for _, subclause in headings]


VALID_TOPS = {str(number) for number in range(1, 28)} | set("ABCDE")


def sort_references(values: set[str]) -> list[str]:
    return sorted(
        values,
        key=lambda value: (
            value.isdigit() is False,
            int(value) if value.isdigit() else 0,
            value,
        ),
    )


def collect_references(lines: list[str], own_top: str) -> list[str]:
    found: set[str] = set()
    text = " ".join(lines)
    for match in NUMBER_REF_RE.finditer(text):
        top = match.group(1).split(".")[0]
        if top in VALID_TOPS:
            found.add(top)
    for match in CLAUSE_REF_RE.finditer(text):
        if match.group(1) in VALID_TOPS:
            found.add(match.group(1))
    for match in ANNEX_REF_RE.finditer(text):
        found.add(match.group(1))
    found.discard(own_top)
    return sort_references(found)


def load_inventory() -> dict:
    return yaml.safe_load(INVENTORY.read_text(encoding="utf-8"))


def build_clause_map(subclauses: list[Subclause], inventory: dict) -> dict:
    batches: dict[str, str] = {}
    for batch, members in inventory.get("batches", {}).items():
        for member in members:
            batches[str(member)] = batch

    grouped: dict[str, list[Subclause]] = {}
    for subclause in subclauses:
        grouped.setdefault(subclause.top, []).append(subclause)

    clauses = []
    for number in sorted(grouped, key=lambda value: (value.isdigit() is False, int(value) if value.isdigit() else 0, value)):
        members = grouped[number]
        clauses.append(
            {
                "number": number,
                "title": members[0].title,
                "tag": members[0].tag,
                "informative": members[0].informative,
                "library": number in LIBRARY_CLAUSES,
                "batch": batches.get(number, "H"),
                "owner": "ivan.kniazkov",
                "page_start": members[0].page_start,
                "page_end": members[-1].page_end,
                "subclauses": len(members),
                "paragraphs": sum(item.paragraphs for item in members),
                "grammar": sum(item.grammar for item in members),
                "tables": sum(item.tables for item in members),
                "examples": sum(item.examples for item in members),
                "notes": sum(item.notes for item in members),
                "references": sort_references(
                    {ref for item in members for ref in item.references}
                ),
                "subclause_list": [
                    {
                        "number": item.number,
                        "title": item.title,
                        "tag": item.tag,
                        "informative": item.informative,
                        "page_start": item.page_start,
                        "page_end": item.page_end,
                        "paragraphs": item.paragraphs,
                        "grammar": item.grammar,
                        "tables": item.tables,
                        "examples": item.examples,
                        "notes": item.notes,
                    }
                    for item in members
                ],
            }
        )
    return {
        "schema_version": 1,
        "language": "CPP03",
        "edition": "N1638",
        "status": "draft",
        "generated_by": "tools/inventory.py",
        "source": "private N1638 TXT",
        "clauses": clauses,
    }


def build_private_map(subclauses: list[Subclause]) -> dict:
    return {
        "schema_version": 1,
        "language": "CPP03",
        "edition": "N1638",
        "subclauses": [
            {
                "number": item.number,
                "title": item.title,
                "tag": item.tag,
                "page_start": item.page_start,
                "page_end": item.page_end,
                "line_start": item.line_start,
                "line_end": item.line_end,
                "fingerprint": item.fingerprint,
            }
            for item in subclauses
        ],
    }


def build_report(clause_map: dict, subclauses: list[Subclause]) -> dict:
    clauses = clause_map["clauses"]
    baseline = {
        "normative_statements": None,
        "covered": None,
        "note": "Populated by tools/coverage.py; see reports/cpp03/coverage.json.",
    }
    coverage_path = REPORT_OUT.parent / "coverage.json"
    if coverage_path.exists():
        coverage = json.loads(coverage_path.read_text(encoding="utf-8"))
        baseline = {
            "normative_statements": coverage.get("normative"),
            "covered": coverage.get("covered"),
            "uncovered": coverage.get("uncovered"),
        }
    return {
        "language": "CPP03",
        "edition": "N1638",
        "clauses": len(clauses),
        "subclauses": len(subclauses),
        "informative_subclauses": sum(1 for item in subclauses if item.informative),
        "library_clauses": sum(1 for clause in clauses if clause["library"]),
        "paragraphs": sum(item.paragraphs for item in subclauses),
        "grammar_productions": sum(item.grammar for item in subclauses),
        "tables": sum(item.tables for item in subclauses),
        "examples": sum(item.examples for item in subclauses),
        "notes": sum(item.notes for item in subclauses),
        "batches": {
            batch: [
                clause["number"]
                for clause in clauses
                if clause["batch"] == batch
            ]
            for batch in sorted({clause["batch"] for clause in clauses})
        },
        "coverage_baseline": baseline,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--private-out", type=Path, default=None)
    parser.add_argument("--public-out", type=Path, default=PUBLIC_OUT)
    parser.add_argument("--report", type=Path, default=REPORT_OUT)
    args = parser.parse_args()

    subclauses = parse(args.source)
    inventory = load_inventory()
    clause_map = build_clause_map(subclauses, inventory)
    report = build_report(clause_map, subclauses)

    args.public_out.parent.mkdir(parents=True, exist_ok=True)
    args.public_out.write_text(
        yaml.safe_dump(clause_map, sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )

    if args.private_out:
        args.private_out.parent.mkdir(parents=True, exist_ok=True)
        args.private_out.write_text(
            yaml.safe_dump(build_private_map(subclauses), sort_keys=False, allow_unicode=True),
            encoding="utf-8",
        )

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(
        f"CPP03: {report['clauses']} clauses, {report['subclauses']} subclauses, "
        f"{report['paragraphs']} paragraphs, {report['grammar_productions']} productions, "
        f"{report['tables']} tables, {report['examples']} examples, {report['notes']} notes"
    )
    print(f"  clause map: {args.public_out}")
    if args.private_out:
        print(f"  private map: {args.private_out}")
    print(f"  report: {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
