#!/usr/bin/env bash
# Compile the safe C++03 buffer lifecycle against compiler-provided STL. MIT License.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
if [ "$#" -eq 0 ]; then set -- g++ clang++; fi
for compiler in "$@"; do
    echo "Checking C++03 strstream lifecycle: $compiler"
    "$compiler" -std=c++03 -pedantic-errors "$root/src/test/resources/library-catalogue/strstream-runtime.cpp" -o "$work/strstream"
    "$work/strstream"
done
