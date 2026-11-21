#!/usr/bin/env python3
"""Regression tests for lossless scope partitioning and exclusive ownership."""
import importlib.util
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('inventory_docs', Path(__file__).with_name('inventory-documentation.py'))
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)


class InventoryTests(unittest.TestCase):
    def test_ranges_cover_every_line_once(self):
        text = '# One\n\n' + ('alpha beta gamma\n' * 40) + '## Two\nlast'
        ranges = list(MOD.spans(text, limit=20))
        self.assertEqual(text, ''.join(content for _, _, content in ranges))
        self.assertEqual(list(range(1, len(text.splitlines()) + 1)),
                         [n for first, last, _ in ranges for n in range(first, last + 1)])
        self.assertTrue(all(MOD.words(content) <= 20 for _, _, content in ranges))

    def test_long_line_is_never_silently_cut(self):
        text = 'word ' * 3001 + '\n'
        self.assertEqual([(1, 1, text)], list(MOD.spans(text)))

    def test_json_pointers_escape_keys_and_keep_records_distinct(self):
        fields = list(MOD.string_fields({'a/b': [{'~key': 'example'}]}))
        self.assertEqual([('/a~1b/0/~0key', 'example')], fields)

    def test_field_shapes_do_not_copy_prose(self):
        result = MOD.summarize_fields([
            {'pointer': '/units/0/description', 'words': 2},
            {'pointer': '/units/1/description', 'words': 3}])
        self.assertEqual([{'pointer_pattern': '/units/*/description', 'count': 2, 'words': 5}], result)

    def data(self):
        return {'batches': [{'id': 'M001', 'words': 1600}, {'id': 'M002', 'words': 1600}],
                'sections': [{'batch': 'M001', 'action': 'migrate'},
                             {'batch': None, 'action': 'root_introduction'}]}

    def test_missing_or_extra_owner_rejected(self):
        for owners in ({'M001': 1}, {'M001': 1, 'M002': 2, 'M003': 3}):
            with self.assertRaises(ValueError):
                MOD.attach_owners(self.data(), owners)

    def test_combined_issue_budget_rejected(self):
        with self.assertRaises(ValueError):
            MOD.attach_owners(self.data(), {'M001': 1, 'M002': 1})

    def test_exclusive_ownership_and_readme_exception(self):
        data = MOD.attach_owners(self.data(), {'M001': 233, 'M002': 234})
        self.assertEqual([233, 231], [s['issue'] for s in data['sections']])


if __name__ == '__main__':
    unittest.main()
