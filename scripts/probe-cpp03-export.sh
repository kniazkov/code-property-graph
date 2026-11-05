#!/usr/bin/env bash
# Reproduce compiler support for C++03 exported templates. MIT License.
# This is an investigation, not a passing language-conformance test.
set -eu
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cat > "$work/api.hpp" <<'CPP'
export template<class T> T identity(T value);
CPP
cat > "$work/definition.cpp" <<'CPP'
#include "api.hpp"
export template<class T> T identity(T value) { return value; }
CPP
cat > "$work/main.cpp" <<'CPP'
#include "api.hpp"
int main() { return identity(0); }
CPP
if [ "$#" -eq 0 ]; then set -- g++ clang++; fi
for compiler in "$@"; do
    if ! command -v "$compiler" >/dev/null 2>&1; then
        printf '\nSKIPPED: compiler unavailable: %s\n' "$compiler"
        continue
    fi
    printf '\n=== %s ===\n' "$compiler"
    "$compiler" --version | head -n 1
    if "$compiler" -std=c++03 -pedantic-errors "$work/definition.cpp" "$work/main.cpp" -o "$work/exported"; then
        printf 'Separate exported template: link succeeded; inspect compiler semantics separately.\n'
    else
        printf 'Separate exported template: compile/link failed (see diagnostics above).\n'
    fi
    # The explicit definition is a control, not evidence of export support.
    cp "$work/definition.cpp" "$work/explicit.cpp"
    printf '\ntemplate int identity<int>(int);\n' >> "$work/explicit.cpp"
    if "$compiler" -std=c++03 -pedantic-errors "$work/explicit.cpp" "$work/main.cpp" -o "$work/explicit"; then
        "$work/explicit"
        printf 'Explicit-instantiation control: compile/link/run succeeded.\n'
    else
        printf 'Explicit-instantiation control: compile/link failed.\n'
    fi
done
