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
    for source in "$root"/src/test/resources/library-catalogue/*.cpp "$root"/src/test/resources/library-catalogue/resolved-remaining/*.cpp; do
        case "$source" in */c-stream-signatures.cpp|*/c-benign-runtime.cpp) continue;; esac
        "$compiler" -std=c++03 -pedantic-errors -fsyntax-only "$source"
    done
    "$compiler" -std=c++03 -pedantic-errors "$root/src/test/resources/library-catalogue/c-stream-signatures.cpp" -o "$work/streams"
    "$work/streams"
    "$compiler" -std=c++03 -pedantic-errors "$root/src/test/resources/library-catalogue/c-benign-runtime.cpp" -o "$work/c-benign"
    "$work/c-benign"
done
