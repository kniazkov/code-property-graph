#!/usr/bin/env python3
"""Portable filename regressions without creating Windows-invalid files."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('portable_paths', Path(__file__).with_name('check-portable-paths.py'))
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class PortablePathsTest(unittest.TestCase):
    def test_reserved_devices_with_extensions_and_case(self):
        for name in ('CON', 'prn.txt', 'Aux.cpp', 'NUL.tar.gz', 'COM1', 'lpt9.h',
                     'COM¹.txt', 'LPT²', 'com³', 'CONIN$', 'CONOUT$.txt', 'AUX .cpp'):
            with self.subTest(name=name):
                self.assertTrue(CHECKER.path_errors(['src/' + name]))

    def test_reserved_directory(self):
        self.assertTrue(CHECKER.path_errors(['src/aux/file.cpp']))

    def test_invalid_characters(self):
        for char in '<>:"\\|?*' + ''.join(chr(n) for n in range(32)):
            with self.subTest(char=repr(char)):
                self.assertTrue(CHECKER.path_errors(['src/a' + char + 'b.cpp']))

    def test_trailing_dot_or_space(self):
        for name in ('file.', 'file ', 'dir./file', 'dir /file'):
            with self.subTest(name=name):
                self.assertTrue(CHECKER.path_errors([name]))

    def test_file_case_collision(self):
        self.assertTrue(CHECKER.path_errors(['src/File.cpp', 'src/file.cpp']))

    def test_directory_case_collision(self):
        self.assertTrue(CHECKER.path_errors(['Src/a.cpp', 'src/b.cpp']))

    def test_file_directory_case_collision(self):
        self.assertTrue(CHECKER.path_errors(['src', 'SRC/file.cpp']))

    def test_valid_names_and_shared_directories(self):
        self.assertEqual([], CHECKER.path_errors([
            'src/auxiliary.cpp', 'src/console.cpp', 'src/COM0.txt', 'src/LPT10.h',
            '.github/workflows/build.yml', 'two words/файл.txt', 'a.b/c.d']))

    def test_empty_and_relative_components(self):
        for name in ('/file', 'a//b', './a', 'a/../b'):
            with self.subTest(name=name):
                self.assertTrue(CHECKER.path_errors([name]))

    def test_git_index_ignores_untracked_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            (root / 'two words.txt').write_text('tracked', encoding='utf-8')
            (root / 'untracked.txt').write_text('ignored', encoding='utf-8')
            subprocess.run(['git', '-C', str(root), 'add', 'two words.txt'], check=True)
            self.assertEqual(['two words.txt'], CHECKER.tracked_paths(root))


if __name__ == '__main__':
    unittest.main()
