#!/usr/bin/env python3
"""Validate audit records without confusing inventory or source review with support."""
import argparse
import collections
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_HASH = '6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a'
SOURCE_IDENTITY = dict(edition='ISO/IEC 14882:2003', source_sha256=SOURCE_HASH,
                       source_bytes=2461934, pdf_pages=786, printed_pages=757,
                       printed_to_pdf_offset=28)
STAGES = ('parser', 'model', 'resolution', 'analysis', 'generator')


def schema_errors(value, schema, location):
    """Validate the small documented schema subset, using no optional dependency."""
    errors = []
    kinds = {'object': lambda x: isinstance(x, dict), 'array': lambda x: isinstance(x, list),
             'string': lambda x: isinstance(x, str), 'integer': lambda x: type(x) is int,
             'boolean': lambda x: type(x) is bool, 'null': lambda x: x is None}
    expected = schema.get('type')
    if expected is not None:
        expected = expected if isinstance(expected, list) else [expected]
        if not any(kinds[k](value) for k in expected):
            return [f'{location}: expected {expected}']
    if 'enum' in schema and value not in schema['enum']:
        errors.append(f'{location}: invalid enum value {value!r}')
    if 'minimum' in schema and type(value) is int and value < schema['minimum']:
        errors.append(f'{location}: below minimum')
    if isinstance(value, dict):
        for key in schema.get('required', []):
            if key not in value:
                errors.append(f'{location}: missing {key}')
        properties = schema.get('properties', {})
        for key, child in value.items():
            if key in properties:
                errors.extend(schema_errors(child, properties[key], f'{location}.{key}'))
            elif schema.get('additionalProperties') is False:
                errors.append(f'{location}: unexpected property {key}')
    if isinstance(value, list) and 'items' in schema:
        for i, item in enumerate(value):
            errors.extend(schema_errors(item, schema['items'], f'{location}[{i}]'))
    return errors


def record_errors(row, location, root=ROOT):
    errors = []
    for forbidden in ('body', 'source_text', 'paragraph_text', 'raw_source', 'pdf_bytes'):
        if forbidden in row:
            errors.append(f'{location}: raw normative source must not be redistributed in field {forbidden}')
    if row.get('source_hash') != SOURCE_HASH:
        errors.append(f'{location}: source hash does not identify verified 2003 edition')
    if row.get('edition', '').replace(' ', '') != 'ISO/IEC14882:2003':
        errors.append(f'{location}: wrong edition')
    page, pdf = row.get('printed_page'), row.get('pdf_page')
    if type(page) is int and type(pdf) is int and (pdf != page + 28 or not 1 <= page <= 757):
        errors.append(f'{location}: inconsistent source pagination')
    if not row.get('id', '').startswith(f"cpp03:{row.get('section')}:"):
        errors.append(f'{location}: ID does not match section')
    paragraph = row.get('paragraph')
    if (type(paragraph) is int or isinstance(paragraph, str)) and not row.get('id', '').endswith(f':p{paragraph}'):
        errors.append(f'{location}: ID does not match numbered paragraph')
    if row.get('evidence_status') in ('gap', 'partial') and not row.get('gap', '').strip():
        errors.append(f'{location}: incomplete evidence needs an explicit gap')
    if row.get('evidence_status') == 'verified':
        if row.get('source_reviewed') is not True:
            errors.append(f'{location}: verified evidence requires source review')
        if not row.get('tests'):
            errors.append(f'{location}: verified implementation evidence requires tests')
    if row.get('validation') == 'executed' and not row.get('tests'):
        errors.append(f'{location}: execution claim requires a test reference')
    for field in (*STAGES, 'tests'):
        for citation in row.get(field, []):
            path, _, anchor = citation.partition('#')
            if path.startswith(('http:', 'https:')):
                errors.append(f'{location}.{field}: evidence must identify a repository file')
                continue
            target = (root / path).resolve()
            if not target.is_relative_to(root.resolve()) or not target.is_file():
                errors.append(f'{location}.{field}: missing/local-escape evidence {citation}')
                continue
            if anchor and re.fullmatch(r'L[0-9]+(?:-L[0-9]+)?', anchor):
                line_numbers = [int(n) for n in re.findall(r'[0-9]+', anchor)]
                if min(line_numbers) < 1 or max(line_numbers) > len(target.read_text().splitlines()) or line_numbers != sorted(line_numbers):
                    errors.append(f'{location}.{field}: line range outside file {citation}')
            elif anchor and re.fullmatch(r'[A-Za-z_$][\w$]*(?:\(\))?', anchor):
                method = anchor.removesuffix('()')
                if not re.search(r'\b' + re.escape(method) + r'\s*\(', target.read_text()):
                    errors.append(f'{location}.{field}: method not found {citation}')
            elif anchor or '#' in citation:
                errors.append(f'{location}.{field}: unsupported evidence fragment {citation}')
    return errors


def check(directory, root=ROOT):
    schema = json.loads((directory / 'schema.json').read_text())
    errors, rows, seen = [], [], set()
    for path in sorted(directory.glob('*.jsonl')):
        for line_number, line in enumerate(path.read_text().splitlines(), 1):
            if not line.strip():
                continue
            location = f'{path.name}:{line_number}'
            try:
                row = json.loads(line)
            except json.JSONDecodeError as failure:
                errors.append(f'{location}: {failure}')
                continue
            issues = schema_errors(row, schema, location)
            errors.extend(issues)
            if issues:
                continue
            if row['id'] in seen:
                errors.append(f"{location}: duplicate ID {row['id']}")
            seen.add(row['id'])
            errors.extend(record_errors(row, location, root))
            rows.append(row)
    if not rows:
        errors.append('No valid paragraph records were found')
    return rows, errors


def inventory_coverage(rows, directory):
    manifest = json.loads((directory / 'inventory.json').read_text())
    units = manifest['units']
    errors = []
    for field, value in SOURCE_IDENTITY.items():
        if manifest.get(field) != value:
            errors.append(f'inventory: incorrect source identity field {field}')
    index = {}
    for unit in units:
        if unit['id'] in index:
            errors.append(f"inventory: duplicate ID {unit['id']}")
        index[unit['id']] = unit
    for unit in units:
        for call_site in unit.get('call_sites', []):
            if call_site not in index:
                errors.append(f"{unit['id']}: unknown footnote call site {call_site}")
    declared = dict(collections.Counter(u['unit_kind'] for u in units))
    if declared != manifest['counts']:
        errors.append('inventory: declared kind counts differ from actual units')
    reviewed = collections.Counter()
    present = collections.Counter()
    for row in rows:
        unit = index.get(row['id'])
        if unit is None:
            errors.append(f"{row['id']}: absent from inventory")
            continue
        for field in ('section', 'tag', 'printed_page', 'pdf_page', 'paragraph', 'footnote_number', 'call_sites', 'call_site_status'):
            if field in unit and row.get(field) != unit[field]:
                errors.append(f"{row['id']}: locator differs from inventory field {field}")
        present[unit['unit_kind']] += 1
        if row['source_reviewed']:
            reviewed[unit['unit_kind']] += 1
    metrics = {kind: {'inventory': total, 'records': present[kind], 'source_reviewed': reviewed[kind]}
               for kind, total in sorted(declared.items())}
    return metrics, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=ROOT / 'docs/cpp03-paragraphs')
    parser.add_argument('--require-source-complete', action='store_true',
                        help='Fail if any inventoried source locator remains unreviewed; not a conformance claim')
    args = parser.parse_args()
    rows, errors = check(args.directory)
    coverage, inventory_errors = inventory_coverage(rows, args.directory)
    errors.extend(inventory_errors)
    if args.require_source_complete and any(v['source_reviewed'] != v['inventory'] for v in coverage.values()):
        errors.append('Source locator review is incomplete')
    print(json.dumps({'locator_coverage': coverage, 'records': len(rows), 'source_reviewed': sum(r['source_reviewed'] for r in rows),
                      'evidence_status': dict(collections.Counter(r['evidence_status'] for r in rows)),
                      'executed_records': sum(r['validation'] == 'executed' for r in rows),
                      'errors': len(errors)}, sort_keys=True))
    print('Record validation is not proof of exhaustive inventory or standard conformance.')
    for error in errors:
        print(error, file=sys.stderr)
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
