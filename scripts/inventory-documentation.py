#!/usr/bin/env python3
"""Reproduce the frozen documentation migration inventory without source PDFs."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/documentation-migration'
BASE = '479ef6fc16ee6dc0e16030bde94d6712ce217ab5'
CYRILLIC = re.compile('[А-Яа-яЁё]')
DOC_EXT = {'.md', '.rst', '.txt', '.adoc'}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def words(text):
    # Conservative review units: include code, table cells and JSON keys too.
    return len(text.split())


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalized(path):
    return re.sub(r'\.(ru|en)(?=\.md$)', '', path)


def batch_group(path):
    name = normalized(path)
    if path.startswith('examples/'):
        return 'examples'
    if path.startswith('src/test/'):
        return 'fixture-readmes'
    if path.startswith('docs/research/'):
        return 'research'
    if path.startswith('docs/standards/'):
        return 'standard-reference-cards'
    if 'verification.md' in path or name in {
            'docs/cpp03-constructor-defaults.md', 'docs/cpp03-copy-outcomes.md',
            'docs/cpp03-exception-qualification.md', 'docs/cpp03-explicit-construction.md',
            'docs/cpp03-member-exceptions.md', 'docs/cpp03-object-initializers.md',
            'docs/cpp03-scalar-initializers.md'}:
        return 'implementation-evidence-reports'
    return name


def spans(text, limit=1200):
    """Partition all lines, preferring headings/blank lines, never losing a line."""
    lines = text.splitlines(keepends=True)
    start, size = 0, 0
    for i, line in enumerate(lines):
        boundary = line.startswith('#') or (i and not lines[i-1].strip())
        if i > start and (size + words(line) > limit or (boundary and size >= 600)):
            yield start + 1, i, ''.join(lines[start:i])
            start, size = i, 0
        size += words(line)
    if lines:
        yield start + 1, len(lines), ''.join(lines[start:])


def string_fields(value, pointer=''):
    if isinstance(value, str):
        yield pointer, value
    elif isinstance(value, dict):
        for key, child in value.items():
            escaped = key.replace('~', '~0').replace('/', '~1')
            yield from string_fields(child, pointer + '/' + escaped)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from string_fields(child, pointer + '/' + str(index))


def summarize_fields(fields):
    result = {}
    for field in fields:
        shape = re.sub(r'/[0-9]+(?=/|$)', '/*', field['pointer'])
        item = result.setdefault(shape, {'pointer_pattern': shape, 'count': 0, 'words': 0})
        item['count'] += 1
        item['words'] += field['words']
    return list(result.values())


def inventory():
    names = git('ls-tree', '-r', '--name-only', BASE).decode().splitlines()
    texts, files, sections, references, exclusions = {}, [], [], [], []
    for name in names:
        data = git('show', BASE + ':' + name)
        try:
            texts[name] = data.decode('utf-8')
        except UnicodeDecodeError:
            exclusions.append({'path': name, 'reason': 'binary; not prose input'})
    candidates = [n for n in texts if Path(n).suffix in DOC_EXT or
                  (n.startswith('docs/') and Path(n).suffix in {'.json', '.jsonl'})]
    migrate_groups = {normalized(n) for n in candidates if
                      '.ru.' in n or CYRILLIC.search(texts[n])}
    for name in sorted(candidates):
        text = texts[name]
        structured = Path(name).suffix in {'.json', '.jsonl'}
        action = 'migrate' if normalized(name) in migrate_groups else 'retain_candidate'
        if name == 'README.md':
            action = 'root_introduction'
        target = normalized(name)
        counterpart = next((n for n in candidates if n != name and
                            normalized(n) == target), None)
        fields = []
        if structured:
            records = ([json.loads(line) for line in text.splitlines() if line.strip()]
                       if name.endswith('.jsonl') else [json.loads(text)])
            for record_index, record in enumerate(records):
                for pointer, value in string_fields(record):
                    # Inventory all string fields, including unknown future prose keys.
                    fields.append({'record': record_index + 1, 'pointer': pointer,
                                   'words': words(value),
                                   'cyrillic': bool(CYRILLIC.search(value))})
        file = {'path': name, 'sha256': digest(text.encode()), 'words': words(text),
                'lines': len(text.splitlines()), 'kind': 'structured' if structured else 'text',
                'action': action, 'target': target, 'counterpart': counterpart,
                'headings': [i for i, line in enumerate(text.splitlines(), 1)
                             if re.match(r'^#{1,6}\s', line)],
                'string_field_shapes': summarize_fields(fields),
                'cyrillic_fields': [f for f in fields if f['cyrillic']]}
        files.append(file)
        for first, last, content in spans(text):
            review_words = words(content)
            section_action = action
            if structured:
                selected = [field for field in fields if field['cyrillic'] and
                            (not name.endswith('.jsonl') or first <= field['record'] <= last)]
                # Budget changed strings and their paired English values. All other
                # fields are mechanically preserved, not semantically audited.
                paired = {(f['record'], re.sub(r'_ru$', '_en', f['pointer'])) for f in selected}
                review_words = sum(f['words'] for f in selected) + sum(
                    f['words'] for f in fields if (f['record'], f['pointer']) in paired
                    and not f['cyrillic'])
                section_action = 'migrate' if selected else 'retain_candidate'
            sections.append({'id': f'{name}:{first}-{last}', 'path': name,
                             'first_line': first, 'last_line': last,
                             'sha256': digest(content.encode()), 'words': review_words,
                             'raw_words': words(content), 'action': section_action, 'target': target, 'batch': None})
    # Candidate references, not claims that a Markdown link resolves. This also
    # finds hard-coded paths in validators, generators, tests and workflow inputs.
    by_basename = {}
    for file in files:
        by_basename.setdefault(Path(file['path']).name, []).append(file['path'])
    pattern = re.compile('|'.join(re.escape(n) for n in sorted(by_basename, key=len, reverse=True)))
    for source, text in sorted(texts.items()):
        for line_number, line in enumerate(text.splitlines(), 1):
            for basename in sorted(set(pattern.findall(line))):
                for target in by_basename[basename]:
                    references.append({'source': source, 'line': line_number,
                                       'target_candidate': target,
                                       'match': 'basename; resolve relative path and anchor during migration'})
    batches = []
    for group in sorted({batch_group(s['path']) for s in sections if s['action'] == 'migrate'}):
        current = None
        for section in [s for s in sections if batch_group(s['path']) == group and s['action'] == 'migrate']:
            if section['words'] > 3000:
                raise ValueError('Single line exceeds migration budget: ' + section['id'])
            if current is None or current['words'] + section['words'] > 2800:
                current = {'id': f'M{len(batches)+1:03}', 'group': group,
                           'words': 0, 'sections': []}
                batches.append(current)
            current['words'] += section['words']
            current['sections'].append(section['id'])
            section['batch'] = current['id']
    included = set(candidates)
    for name in sorted(texts):
        if name not in included:
            exclusions.append({'path': name, 'reason': ('license notice; preserve verbatim' if Path(name).name.upper() in {'LICENSE', 'NOTICE', 'COPYING'} else 'code/config/fixture; scanned for inbound references, not documentation prose')})
    return {'schema_version': 1, 'baseline': BASE, 'files': files,
            'sections': sections, 'batches': batches,
            'reference_candidates': references, 'excluded_files': exclusions}


def attach_owners(data, owners):
    if set(owners) != {b['id'] for b in data['batches']}:
        raise ValueError('Ownership must exactly match all migration batches')
    for batch in data['batches']:
        owner = owners[batch['id']]
        if not isinstance(owner, int) or owner <= 0:
            raise ValueError('Invalid issue owner')
        batch['issue'] = owner
    for section in data['sections']:
        section['issue'] = owners[section['batch']] if section['batch'] else (
            231 if section['action'] == 'root_introduction' else 230)
    # An existing seed issue can own several small batches, but never >3000 units.
    totals = {}
    for batch in data['batches']:
        totals[batch['issue']] = totals.get(batch['issue'], 0) + batch['words']
    if any(count > 3000 for count in totals.values()):
        raise ValueError('An issue exceeds its migration review budget')
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--draft', action='store_true')
    args = parser.parse_args()
    data = inventory()
    if not args.draft:
        data = attach_owners(data, json.loads((OUT / 'owners.json').read_text()))
    if args.draft:
        outputs = {'draft.json': json.dumps(data, separators=(',', ':')) + '\n'}
    else:
        outputs = {}
        index = {'schema_version': 1, 'baseline': BASE, 'batches': data['batches'], 'collections': {}}
        for key in ('files', 'sections', 'reference_candidates', 'excluded_files'):
            chunks, chunk = [], ''
            for record in data[key]:
                line = json.dumps(record, ensure_ascii=True, separators=(',', ':')) + '\n'
                if chunk and len((chunk + line).encode()) > 100000:
                    chunks.append(chunk)
                    chunk = ''
                chunk += line
            if chunk:
                chunks.append(chunk)
            names = []
            for number, content in enumerate(chunks, 1):
                name = f'{key}-{number:02}.jsonl'
                outputs[name] = content
                names.append(name)
            index['collections'][key] = names
        outputs['inventory.json'] = json.dumps(index, indent=2) + '\n'
        rows = ['# Migration batch index', '',
                'Frozen baseline: `' + BASE + '`. Counts are review words, not normative requirements.', '',
                '| Batch | Topic | Words | Owner |', '| --- | --- | ---: | --- |']
        rows += [f"| {b['id']} | {b['group']} | {b['words']} | [#{b['issue']}](https://github.com/kniazkov/code-property-graph/issues/{b['issue']}) |" for b in data['batches']]
        outputs['batches.md'] = '\n'.join(rows) + '\n'
    for name, rendered in outputs.items():
        target = OUT / name
        if args.check:
            if not target.exists() or target.read_text() != rendered:
                raise SystemExit('Inventory drift: ' + name)
        else:
            target.write_text(rendered)
    stale = set(p.name for p in OUT.glob('*.jsonl')) - set(outputs)
    if stale and not args.draft:
        raise SystemExit('Remove stale generated shards: ' + ', '.join(sorted(stale)))
    print(f"{len(data['files'])} documents, {len(data['sections'])} ranges, "
          f"{len(data['batches'])} migration batches, {len(data['reference_candidates'])} reference candidates")


if __name__ == '__main__':
    main()
