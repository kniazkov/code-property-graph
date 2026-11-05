#!/usr/bin/env bash
# Check portable declaration signatures against real STL headers. MIT License.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
if [ "$#" -eq 0 ]; then set -- g++ clang++; fi
for compiler in "$@"; do
    command -v "$compiler" >/dev/null 2>&1 || { printf 'Required compiler unavailable: %s\n' "$compiler" >&2; exit 1; }
    printf 'Checking C++03 library signatures: %s\n' "$compiler"
    "$compiler" -std=c++03 -pedantic-errors -fsyntax-only "$root/src/test/resources/library-catalogue/containers.cpp"
    "$compiler" -std=c++03 -pedantic-errors "$root/src/test/resources/library-catalogue/c-stream-signatures.cpp" -o "$work/streams"
    "$work/streams"
done
