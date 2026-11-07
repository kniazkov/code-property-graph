#!/usr/bin/env python3
"""Mutation tests keep inventory counts and evidence gaps honest."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('macro_inventory', ROOT / 'scripts/check-cpp03-library-macros.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class MacroInventoryTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((ROOT / 'docs/cpp03-library-macros.json').read_text())

    def test_current_inventory(self):
        self.assertEqual([], checker.check(self.data))

    def test_dropped_obligation(self):
        self.data['entries'].pop()
        self.assertTrue(checker.check(self.data))

    def test_duplicate_obligation(self):
        self.data['entries'].append(copy.deepcopy(self.data['entries'][0]))
        self.assertTrue(checker.check(self.data))

    def test_missing_source_coordinate(self):
        self.data['entries'][0]['cpp03_locators'] = ['cpp03:99:p99']
        self.assertTrue(checker.check(self.data))

    def test_unsubstantiated_support(self):
        self.data['entries'][0]['support_status'] = 'profile_definition'
        self.data['entries'][0]['implementation'] = []
        self.assertTrue(checker.check(self.data))

    def test_gap_without_followup(self):
        self.data['entries'][0]['remaining_work'] = ''
        self.assertTrue(checker.check(self.data))

    def test_runtime_binding_without_scope(self):
        entry = self.data['entries'][0]
        entry['support_status'] = 'runtime_binding'
        entry['runtime_binding'] = {}
        self.assertTrue(checker.check(self.data))

    def test_missing_evidence_file(self):
        self.data['entries'][0]['tests'] = ['not-present.java']
        self.assertTrue(checker.check(self.data))


if __name__ == '__main__':
    unittest.main()
