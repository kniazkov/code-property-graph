#!/usr/bin/env python3
"""Check Git's index for paths that cannot coexist in a Windows checkout.

No files are opened: even names invalid on the current OS can be validated.
This is a conservative naming gate, not a replacement for a Windows checkout.
"""
import re
import subprocess
import sys
from pathlib import Path

RESERVED = re.compile(r'^(CON|PRN|AUX|NUL|CONIN\$|CONOUT\$|COM[1-9¹²³]|LPT[1-9¹²³])$', re.I)
INVALID = re.compile(r'[<>:"\\|?*\x00-\x1f]')


def path_errors(paths):
    """Return deterministic diagnostics for components and case collisions."""
    errors = set()
    seen = {}
    for path in paths:
        parts = path.split('/')
        for index, part in enumerate(parts):
            prefix = '/'.join(parts[:index + 1])
            if not part or part in ('.', '..'):
                errors.add(f'{path!r}: empty or relative component')
            if INVALID.search(part):
                errors.add(f'{path!r}: invalid Windows character in {part!r}')
            if part.endswith((' ', '.')):
                errors.add(f'{path!r}: trailing dot or space in {part!r}')
            # Windows reserves device names even with extensions or spaces
            # immediately before an extension (e.g. AUX .cpp).
            if RESERVED.fullmatch(part.split('.')[0].rstrip(' ')):
                errors.add(f'{path!r}: reserved Windows name {part!r}')
            key = prefix.casefold()
            if key in seen and seen[key] != prefix:
                errors.add(f'case collision: {seen[key]!r} and {prefix!r}')
            else:
                seen[key] = prefix
    return sorted(errors)


def tracked_paths(root):
    """Use NUL separators so spaces, newlines and non-ASCII names survive."""
    raw = subprocess.check_output(['git', 'ls-files', '--cached', '-z'], cwd=root)
    return raw.decode('utf-8', errors='surrogateescape').split('\0')[:-1]


def main():
    paths = tracked_paths(Path(__file__).resolve().parent.parent)
    errors = path_errors(paths)
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Portable path check passed: {len(paths)} tracked paths')
    return 0


if __name__ == '__main__':
    sys.exit(main())
