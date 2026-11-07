#!/usr/bin/env python3
"""Check the C++03 macro obligation inventory, not full implementation support."""
import collections
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
INTEGER_FACT_NAMES = frozenset('''EDOM ERANGE EILSEQ LC_ALL LC_COLLATE LC_CTYPE
    LC_MONETARY LC_NUMERIC LC_TIME SIGABRT SIGFPE SIGILL SIGINT SIGSEGV SIGTERM
    _IOFBF _IOLBF _IONBF BUFSIZ EOF FOPEN_MAX FILENAME_MAX L_tmpnam
    SEEK_CUR SEEK_END SEEK_SET TMP_MAX EXIT_FAILURE EXIT_SUCCESS RAND_MAX'''.split())


def check(data, root=ROOT):
    errors = []
    def require(condition, message):
        if not condition:
            errors.append(message)
    require(data.get('schema_version') == 1, 'Unsupported inventory schema')
    headers = data.get('headers', [])
    entries = data.get('entries', [])
    require(len(headers) == 18, 'Expected all 18 C++03 C-header pairs')
    header_names = [h['header'] for h in headers]
    require(len(set(header_names)) == len(header_names), 'Duplicate header')
    require(len(set(h['c_header'] for h in headers)) == len(headers), 'Duplicate old C spelling')
    expected = {(h['header'], n) for h in headers for n in h['macros']}
    pairs = [(e['header'], e['name']) for e in entries]
    require(len(pairs) == len(set(pairs)), 'Duplicate header/macro obligation')
    require(set(pairs) == expected, 'Header list and obligation entries differ')
    require(len(pairs) == 102 and len({n for _, n in pairs}) == 95,
            'Expected reviewed 102 obligations and 95 distinct macros')
    require(not set(data.get('excluded_mandatory', [])) & {n for _, n in pairs},
            'A later-standard or nonmandatory macro entered the inventory')
    require(not set(data.get('language_not_header_macros', [])) & {n for _, n in pairs},
            'C++ alternative tokens are not header-defined macros')
    for header in ('cctype', 'ciso646'):
        require(not any(h == header for h, _ in pairs), header + ' must have no mandatory macros')
    require({h for h, n in pairs if n == 'NULL'} ==
            {'cstddef', 'cstdio', 'cstdlib', 'cstring', 'ctime', 'clocale', 'cwchar'},
            'NULL must occur in all seven specified headers')
    require({h for h, n in pairs if n == 'WEOF'} == {'cwchar', 'cwctype'},
            'WEOF must occur in both wide-character headers')
    require(('cerrno', 'EILSEQ') in pairs, 'C90 Amendment 1 requires EILSEQ')
    records = {}
    for path in (root / 'docs/cpp03-paragraphs').glob('*.jsonl'):
        for line in path.read_text().splitlines():
            row = json.loads(line)
            records[row['id']] = row
    for locator in data.get('global_locators', []):
        require(locator in records, 'Missing global locator: ' + locator)
    aliases = {h['header']: h['c_header'] for h in headers}
    for entry in entries:
        label = entry['header'] + '/' + entry['name']
        require(aliases.get(entry['header']) == entry['c_header'], label + ': wrong old spelling')
        require(bool(entry.get('cpp03_locators')), label + ': no C++03 source locator')
        for locator in entry.get('cpp03_locators', []):
            require(locator in records, label + ': missing ledger locator ' + locator)
        require(bool(entry.get('inherited_locator')), label + ': no inherited C locator')
        require(entry.get('macro_kind') in ('object', 'function'), label + ': invalid macro kind')
        require(entry.get('support_status') in ('profile_definition', 'runtime_binding', 'target_fact', 'open'), label + ': invalid support state')
        require(bool(entry.get('support_boundary')), label + ': missing support boundary')
        require(bool(re.fullmatch(r'https://github\.com/kniazkov/code-property-graph/issues/[0-9]+', entry.get('remaining_work',''))),
                label + ': no remaining-work issue')
        if entry.get('support_status') == 'runtime_binding':
            binding = entry.get('runtime_binding', {})
            require(all(binding.get(k) for k in ('profile_setting', 'supported_value', 'unknown_behavior', 'abi_scope')),
                    label + ': runtime binding needs explicit selection and ABI/unknown boundaries')
        if entry.get('support_status') == 'target_fact':
            fact = entry.get('target_fact', {})
            require(entry.get('macro_kind') == 'object' and entry.get('required_in_if') == 'yes',
                    label + ': integer target fact must retain its object macro and #if contract')
            if entry['name'] == 'WEOF':
                require(set(fact) == {'profile_setting', 'entry_name', 'supported_types',
                                      'character_domain', 'unknown_behavior', 'value_origin'},
                        label + ': WEOF target fact needs the exact coupled schema')
                require(entry['header'] in ('cwchar', 'cwctype'),
                        label + ': WEOF fact belongs only to wide headers')
                require(fact.get('profile_setting') == 'wideInteger' and
                        fact.get('entry_name') == 'WEOF' and
                        fact.get('supported_types') == ['int', 'unsigned int', 'long', 'unsigned long'] and
                        fact.get('character_domain') == 'nonnegative-wchar',
                        label + ': WEOF needs exact coupled type/domain selection')
            else:
                require(entry['name'] in INTEGER_FACT_NAMES,
                        label + ': not an implemented integer target fact')
                require(fact.get('profile_setting') == 'runtimeIntegerMacros' and
                        fact.get('entry_name') == entry['name'] and fact.get('supported_type') == 'int',
                        label + ': target fact needs exact profile/name/type selection')
            require(bool(fact.get('unknown_behavior')) and bool(fact.get('value_origin')),
                    label + ': target fact needs missing-value and target-source boundaries')
        for kind in ('implementation', 'tests'):
            paths = entry.get(kind, [])
            if entry.get('support_status') in ('profile_definition', 'runtime_binding', 'target_fact'):
                require(bool(paths), label + ': profile definition needs ' + kind)
            for path in paths:
                resolved = (root / path).resolve()
                require(resolved.is_relative_to(root.resolve()) and resolved.is_file(), label + ': missing local evidence ' + path)
    return errors


if __name__ == '__main__':
    data = json.loads((ROOT / 'docs/cpp03-library-macros.json').read_text())
    errors = check(data)
    if errors:
        raise SystemExit('\n'.join(errors))
    counts = collections.Counter(e['support_status'] for e in data['entries'])
    print('C++03 library macro inventory: 102 obligations, 95 names; ' +
          ', '.join(f'{n} {state}' for state, n in sorted(counts.items())) + '.')
