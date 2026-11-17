#!/usr/bin/env python3
"""Focused pre-PR checks for C++03 stream interfaces; never a replacement for CI."""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parent.parent
QUICK = ['StreamTypePropertiesTest', 'OverloadResolutionTest', 'StandardLibraryCatalogueTest']
MATRIX = QUICK + ['IosfwdDeclarationTest', 'StreamCopyAccessTest', 'StreamIsOpenTest',
                  'StreamLibraryResolutionTest', 'LibraryForwardClassResolutionTest', 'ProfileTypeSemanticsTest']
INTEGRATION = ['IosfwdDeclarationIT', 'StreamCopyAccessIT', 'StreamIsOpenIT', 'StreamTypePropertiesIT']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=['quick', 'matrix'])
    parser.add_argument('--compiler', choices=['gcc', 'both'], default='both')
    parser.add_argument('--maven-command', default='mvn', help='Executable and optional arguments; no shell is used')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    command = shlex.split(args.maven_command) + ['-B', '-ntp', '-DargLine=-Xmx768m',
        '-Dtest=' + ','.join(QUICK if args.stage == 'quick' else MATRIX)]
    if args.stage == 'matrix':
        suffix = '#gccOriginalAndGenerated' if args.compiler == 'gcc' else ''
        command += ['-Dit.test=' + ','.join(name + suffix for name in INTEGRATION), 'verify']
    else:
        command += ['test']
    print(shlex.join(command), flush=True)
    if args.dry_run:
        return 0
    destination = ROOT / 'target/stream-checks' / args.stage
    destination.mkdir(parents=True, exist_ok=True)
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    patch = subprocess.check_output(['git', 'diff', 'HEAD', '--'], cwd=ROOT)
    names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT)
    source_hash = hashlib.sha256()
    for name in sorted(set(names.split(b'\0')) - {b''}):
        path = ROOT / name.decode('utf-8')
        if path.is_file():
            source_hash.update(name + b'\0' + path.read_bytes() + b'\0')
    started = time.monotonic()
    with (destination / 'maven.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    report = dict(revision=revision, source_sha256=source_hash.hexdigest(), diff_sha256=hashlib.sha256(patch).hexdigest(),
                  command=command, exit_code=result.returncode, seconds=round(time.monotonic()-started, 2),
                  scope='partial; full GitHub CI remains mandatory')
    (destination / 'result.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    print('Log:', destination / 'maven.log')
    return result.returncode

if __name__ == '__main__':
    raise SystemExit(main())
