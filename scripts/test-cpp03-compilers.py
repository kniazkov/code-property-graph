#!/usr/bin/env python3
"""Offline, isolated regressions for the mandatory compiler bootstrap. MIT License."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
HELPER = ROOT / "scripts" / "ensure-cpp03-compilers.py"
# Each fake has an absolute Python shebang. PATH contains only the fixture bin,
# so an absent compiler or sudo can never fall through to the real host tool.
FAKE_TOOL = r'''
import json
import os
from pathlib import Path
import sys

directory = Path(os.environ["FIXTURE"])
config = json.loads((directory / "config.json").read_text())
name = Path(sys.argv[0]).name
with (directory / "calls.jsonl").open("a") as log:
    log.write(json.dumps({"name": name, "path": sys.argv[0], "args": sys.argv[1:]}) + "\n")
if name == "sudo":
    if sys.argv[1:] == ["apt-get", "update"]:
        sys.exit(config.get("update_exit", 0))
    if sys.argv[1:4] != ["apt-get", "install", "-y"]:
        sys.exit(98)
    if config.get("install_exit", 0):
        sys.exit(config["install_exit"])
    for package in sys.argv[4:]:
        compiler = {"g++": "g++", "clang": "clang++"}[package]
        if compiler not in config.get("omit_install", []):
            destination = directory / "bin" / compiler
            destination.write_text((directory / "fake.py").read_text())
            destination.chmod(0o755)
    sys.exit(0)
mode = config.get("modes", {}).get(name, "ok")
if mode == "compile_fail":
    sys.exit(23)
arguments = sys.argv[1:]
source = Path(arguments[arguments.index("-o") - 1])
with (directory / "sources.jsonl").open("a") as log:
    log.write(json.dumps(source.read_text()) + "\n")
binary = Path(arguments[arguments.index("-o") + 1])
if mode != "no_binary":
    program = "print('C++03 compiler smoke: 42')\n"
    if mode == "run_fail":
        program = "import sys\nsys.exit(24)\n"
    if mode == "wrong_output":
        program = "print('incorrect result')\n"
    binary.write_text("#!" + sys.executable + "\n" + program)
    binary.chmod(0o644 if mode == "not_executable" else 0o755)
'''


@unittest.skipIf(os.name == "nt", "Linux bootstrap uses POSIX executable fixtures")
class CompilerBootstrapTests(unittest.TestCase):
    def run_fixture(self, present=("g++", "clang++"), **config):
        with tempfile.TemporaryDirectory(prefix="cpg compiler fixtures ") as temporary:
            directory = Path(temporary)
            binaries = directory / "bin"
            binaries.mkdir()
            script = "#!" + sys.executable + "\n" + FAKE_TOOL
            (directory / "fake.py").write_text(script, encoding="utf-8")
            (directory / "config.json").write_text(json.dumps(config), encoding="utf-8")
            for name in (*present, "sudo"):
                executable = binaries / name
                executable.write_text(script, encoding="utf-8")
                executable.chmod(0o755)
            environment = dict(os.environ, PATH=str(binaries), FIXTURE=str(directory))
            result = subprocess.run([sys.executable, str(HELPER)], env=environment,
                                    capture_output=True, text=True, timeout=30)
            log = directory / "calls.jsonl"
            calls = [json.loads(line) for line in log.read_text().splitlines()]
            sources = directory / "sources.jsonl"
            source_texts = ([json.loads(line) for line in sources.read_text().splitlines()]
                            if sources.exists() else [])
            # Every compiler call must use the exact resolved path and strict flags.
            for call in calls:
                if call["name"] == "sudo":
                    continue
                self.assertEqual(call["path"], str(binaries / call["name"]))
                prefix = ["-std=c++03", "-pedantic-errors"]
                if call["name"] == "clang++":
                    prefix.append("-DCPG_EXPECT_CLANG=1")
                self.assertEqual(call["args"][:-3], prefix)
                self.assertTrue(call["args"][-3].endswith("/smoke.cpp"))
                self.assertEqual(call["args"][-2], "-o")
            for source in source_texts:
                self.assertIn("__cplusplus != 199711L", source)
                self.assertIn("#if !defined(__clang__)", source)
                self.assertIn("!defined(__GNUC__) || defined(__clang__)", source)
                self.assertIn("std::vector<int>", source)
            return result, calls

    def test_both_working_never_invoke_apt(self):
        result, calls = self.run_fixture()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([call["name"] for call in calls], ["g++", "clang++"])
        self.assertIn("Both mandatory C++03 compilers passed", result.stdout)

    def test_missing_compilers_install_only_missing_packages_and_recheck_both(self):
        for present, packages, initial in [
                (("g++",), ["clang"], ["g++"]),
                (("clang++",), ["g++"], ["clang++"]),
                ((), ["g++", "clang"], [])]:
            with self.subTest(present=present):
                result, calls = self.run_fixture(present)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual([call["name"] for call in calls],
                                 initial + ["sudo", "sudo", "g++", "clang++"])
                apt = [call["args"] for call in calls if call["name"] == "sudo"]
                self.assertEqual(apt, [["apt-get", "update"],
                                      ["apt-get", "install", "-y", *packages]])

    def test_apt_failure_is_fatal(self):
        for key, count in [("update_exit", 1), ("install_exit", 2)]:
            with self.subTest(stage=key):
                result, calls = self.run_fixture((), **{key: 100})
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("exit status 100", result.stderr)
                self.assertEqual([call["name"] for call in calls], ["sudo"] * count)

    def test_present_broken_compiler_is_fatal_without_apt_or_replacement(self):
        for compiler in ("g++", "clang++"):
            for mode in ("compile_fail", "no_binary", "not_executable",
                         "run_fail", "wrong_output"):
                for present in [("g++", "clang++"), (compiler,)]:
                    with self.subTest(compiler=compiler, mode=mode, present=present):
                        result, calls = self.run_fixture(present, modes={compiler: mode})
                        self.assertNotEqual(result.returncode, 0)
                        self.assertIn("bootstrap failed", result.stderr)
                        self.assertNotIn("sudo", [call["name"] for call in calls])
                        self.assertNotIn("Both mandatory", result.stdout)

    def test_incomplete_installation_is_fatal(self):
        for compiler in ("g++", "clang++"):
            with self.subTest(compiler=compiler):
                result, _ = self.run_fixture((), omit_install=[compiler])
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(f"unavailable after apt: {compiler}", result.stderr)

    def test_newly_installed_broken_compiler_is_fatal(self):
        for compiler in ("g++", "clang++"):
            for mode in ("compile_fail", "run_fail", "wrong_output"):
                with self.subTest(compiler=compiler, mode=mode):
                    result, calls = self.run_fixture((), modes={compiler: mode})
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual([call["name"] for call in calls].count("sudo"), 2)
                    self.assertNotIn("Both mandatory", result.stdout)

    def test_workflow_keeps_bootstrap_and_regressions_mandatory(self):
        workflow = (ROOT / ".github" / "workflows" / "build.yml").read_text()
        self.assertIn("run: python3 scripts/test-cpp03-compilers.py", workflow)
        self.assertIn("run: python3 scripts/ensure-cpp03-compilers.py", workflow)
        self.assertLess(workflow.index("run: python3 scripts/test-cpp03-compilers.py"),
                        workflow.index("run: python3 scripts/ensure-cpp03-compilers.py"))
        self.assertNotIn("continue-on-error", workflow)
        self.assertNotIn("skipTests", workflow)
        for gate in ("mvn -B -ntp test", "mvn -B verify",
                     "bash scripts/probe-cpp03-library-catalogue.sh",
                     "python3 scripts/check-cpp03-reports.py",
                     "python3 scripts/check-cpp03-paragraphs.py --require-source-complete",
                     "javadoc -classpath", "-Werror -private -Xdoclint:all,-missing",
                     "bash scripts/demo.sh"):
            self.assertIn(gate, workflow)


if __name__ == "__main__":
    unittest.main()
