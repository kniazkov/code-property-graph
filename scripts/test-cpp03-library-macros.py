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

    def integer_fact(self):
        return next(e for e in self.data['entries'] if e['name'] == 'EDOM')

    def test_target_fact_without_selection(self):
        self.integer_fact()['target_fact']['profile_setting'] = ''
        self.assertTrue(checker.check(self.data))

    def test_target_fact_without_unknown_boundary(self):
        self.integer_fact()['target_fact']['unknown_behavior'] = ''
        self.assertTrue(checker.check(self.data))

    def test_target_fact_without_value_origin(self):
        self.integer_fact()['target_fact']['value_origin'] = ''
        self.assertTrue(checker.check(self.data))

    def test_target_fact_wrong_entry_or_type(self):
        for field, wrong in (('entry_name', 'ERANGE'), ('supported_type', 'long')):
            with self.subTest(field=field):
                data = copy.deepcopy(self.data)
                entry = next(e for e in data['entries'] if e['name'] == 'EDOM')
                entry['target_fact'][field] = wrong
                self.assertTrue(checker.check(data))

    def test_integer_fact_cannot_claim_live_runtime_macro(self):
        entry = next(e for e in self.data['entries'] if e['name'] == 'errno')
        entry['support_status'] = 'target_fact'
        entry['target_fact'] = copy.deepcopy(self.integer_fact()['target_fact'])
        entry['target_fact']['entry_name'] = 'errno'
        self.assertTrue(checker.check(self.data))


if __name__ == '__main__':
    unittest.main()
