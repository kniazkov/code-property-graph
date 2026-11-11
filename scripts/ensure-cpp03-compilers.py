#!/usr/bin/env python3
"""Require working GCC and Clang C++03 toolchains, installing only missing ones.

MIT License. A compiler already on PATH must pass: never repair or replace a
broken compiler silently, and never substitute one compiler family for another.
"""

from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


COMPILERS = {"g++": "g++", "clang++": "clang"}
EXPECTED_OUTPUT = "C++03 compiler smoke: 42\n"
SOURCE = r"""#include <iostream>
#include <vector>
#if __cplusplus != 199711L
#error A strict C++03 language mode is required
#endif
#if defined(CPG_EXPECT_CLANG)
#if !defined(__clang__)
#error clang++ must be Clang
#endif
#else
#if !defined(__GNUC__) || defined(__clang__)
#error g++ must be GCC
#endif
#endif
template <typename T> T sum(const std::vector<T>& values) {
    T result = 0;
    for (typename std::vector<T>::const_iterator it = values.begin();
         it != values.end(); ++it) result += *it;
    return result;
}
int main() {
    std::vector<int> values(2, 21);
    std::cout << "C++03 compiler smoke: " << sum(values) << '\n';
    return sum(values) == 42 ? 0 : 1;
}
"""


def validate(compiler, executable):
    """Compile, link and run with this exact PATH-resolved executable."""
    print(f"Validating mandatory C++03 compiler {compiler}: {executable}", flush=True)
    with tempfile.TemporaryDirectory(prefix="cpg-compiler-smoke-") as directory:
        source = Path(directory) / "smoke.cpp"
        binary = Path(directory) / "smoke"
        source.write_text(SOURCE, encoding="utf-8")
        command = [executable, "-std=c++03", "-pedantic-errors"]
        if compiler == "clang++":
            command.append("-DCPG_EXPECT_CLANG=1")
        subprocess.run(command + [str(source), "-o", str(binary)],
                       check=True, timeout=60)
        result = subprocess.run([str(binary)], check=True, capture_output=True,
                                text=True, timeout=10)
        if result.stdout != EXPECTED_OUTPUT:
            raise RuntimeError(f"{compiler} smoke returned unexpected output: "
                               f"{result.stdout!r}")


def ensure_compilers():
    missing = []
    # Validate present compilers before apt, including when the other is missing.
    for compiler, package in COMPILERS.items():
        executable = shutil.which(compiler)
        if executable is None:
            missing.append(package)
        else:
            validate(compiler, executable)
    if missing:
        print(f"Installing missing compiler packages: {', '.join(missing)}", flush=True)
        subprocess.run(["sudo", "apt-get", "update"], check=True)
        subprocess.run(["sudo", "apt-get", "install", "-y", *missing], check=True)
        # Installation success alone is insufficient. Recheck both exact names.
        for compiler in COMPILERS:
            executable = shutil.which(compiler)
            if executable is None:
                raise RuntimeError(f"Required compiler unavailable after apt: {compiler}")
            validate(compiler, executable)
    print("Both mandatory C++03 compilers passed compile, link and execution.")


if __name__ == "__main__":
    try:
        ensure_compilers()
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"C++03 compiler bootstrap failed: {error}", file=sys.stderr)
        sys.exit(1)
