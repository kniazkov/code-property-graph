#!/usr/bin/env python3
"""Regression tests for the audit validator; no PDF or network is required."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('audit_checker', Path(__file__).with_name('check-cpp03-paragraphs.py'))
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class AuditChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / 'test.java').write_text('void sample() {}\n')
        self.row = dict(id='cpp03:5.1:p1', section='5.1', paragraph=1,
                        source_hash=CHECKER.SOURCE_HASH, edition='ISO/IEC 14882:2003',
                        printed_page=65, pdf_page=93, source_reviewed=True,
                        evidence_status='gap', gap='No exact test.', validation='source_inspected',
                        parser=[], model=[], resolution=[], analysis=[], generator=[], tests=[])

    def tearDown(self):
        self.temp.cleanup()

    def errors(self, **changes):
        return CHECKER.record_errors(dict(self.row, **changes), 'test', self.root)

    def test_honest_reviewed_gap_is_valid_but_not_verified_support(self):
        self.assertEqual([], self.errors())
        self.assertTrue(self.errors(evidence_status='verified'))

    def test_raw_source_field_is_not_a_summary(self):
        self.assertTrue(self.errors(body='Raw normative paragraph'))

    def test_wrong_edition_hash_and_page_are_rejected(self):
        for changes in (dict(edition='N1638'), dict(source_hash='x'), dict(pdf_page=94)):
            self.assertTrue(self.errors(**changes))

    def test_verified_claim_requires_source_review_and_test(self):
        self.assertTrue(self.errors(evidence_status='verified', source_reviewed=False, tests=['test.java#sample']))
        self.assertEqual([], self.errors(evidence_status='verified', tests=['test.java#sample']))

    def test_missing_methods_and_escaping_paths_are_rejected(self):
        for citation in ('test.java#absent', '../outside.java', 'missing.java', 'test.java#L2'):
            self.assertTrue(self.errors(tests=[citation]))
        self.assertEqual([], self.errors(tests=['test.java#L1']))

    def test_alphanumeric_original_paragraph_label_is_preserved(self):
        self.assertEqual([], self.errors(id='cpp03:5.1:p1a', paragraph='1a'))
        self.assertTrue(self.errors(id='cpp03:5.1:p1', paragraph='1a'))

    def test_inventory_counts_source_review_separately(self):
        unit = dict(id='cpp03:5.1:p1', section='5.1', paragraph=1, tag='expr',
                    printed_page=65, pdf_page=93, unit_kind='numbered_paragraph')
        manifest = dict(CHECKER.SOURCE_IDENTITY, counts={'numbered_paragraph': 1}, units=[unit])
        (self.root / 'inventory.json').write_text(json.dumps(manifest))
        row = dict(self.row, tag='expr', source_reviewed=False)
        coverage, errors = CHECKER.inventory_coverage([row], self.root)
        self.assertEqual([], errors)
        self.assertEqual({'inventory': 1, 'records': 1, 'source_reviewed': 0}, coverage['numbered_paragraph'])
        row['source_reviewed'] = True
        coverage, errors = CHECKER.inventory_coverage([row], self.root)
        self.assertEqual(1, coverage['numbered_paragraph']['source_reviewed'])
        self.assertEqual([], errors)

    def test_inventory_rejects_unknown_ids_and_locator_drift(self):
        unit = dict(id='cpp03:5.1:p1', section='5.1', paragraph=1,
                    printed_page=65, pdf_page=93, unit_kind='numbered_paragraph')
        (self.root / 'inventory.json').write_text(json.dumps(dict(CHECKER.SOURCE_IDENTITY, counts={'numbered_paragraph': 1}, units=[unit])))
        self.assertTrue(CHECKER.inventory_coverage([dict(self.row, id='cpp03:5.1:p2')], self.root)[1])
        self.assertTrue(CHECKER.inventory_coverage([dict(self.row, printed_page=66)], self.root)[1])

    def test_footnote_call_sites_are_known_and_match_record(self):
        unit = dict(id='cpp03:footnotes:footnote1', section='footnotes', paragraph=None,
                    unit_kind='footnote', footnote_number='1', call_sites=['cpp03:missing:p1'])
        manifest = dict(CHECKER.SOURCE_IDENTITY, counts={'footnote': 1}, units=[unit])
        (self.root / 'inventory.json').write_text(json.dumps(manifest))
        row = dict(self.row, **unit)
        self.assertTrue(CHECKER.inventory_coverage([row], self.root)[1])
        unit['call_sites'] = []
        (self.root / 'inventory.json').write_text(json.dumps(manifest))
        self.assertTrue(CHECKER.inventory_coverage([row], self.root)[1])
        row['call_sites'] = []
        self.assertEqual([], CHECKER.inventory_coverage([row], self.root)[1])

    def test_unsupported_and_reversed_evidence_fragments_are_rejected(self):
        (self.root / 'test.java').write_text('void sample() {}\n// second line\n')
        for citation in ('test.java#L999-typo', 'test.java#this-anchor-does-not-exist',
                         'test.java#L2-L1', 'test.java#'):
            self.assertTrue(self.errors(tests=[citation]))
        self.assertEqual([], self.errors(tests=['test.java#L1-L2']))

    def test_inventory_requires_exact_source_identity(self):
        manifest = dict(CHECKER.SOURCE_IDENTITY, counts={}, units=[])
        for field in CHECKER.SOURCE_IDENTITY:
            changed = dict(manifest, **{field: 'wrong'})
            (self.root / 'inventory.json').write_text(json.dumps(changed))
            self.assertTrue(CHECKER.inventory_coverage([], self.root)[1])
        (self.root / 'inventory.json').write_text(json.dumps(manifest))
        self.assertEqual([], CHECKER.inventory_coverage([], self.root)[1])

    def test_schema_distinguishes_boolean_from_integer(self):
        self.assertTrue(CHECKER.schema_errors(True, {'type': 'integer'}, 'value'))
        self.assertEqual([], CHECKER.schema_errors(True, {'type': 'boolean'}, 'value'))

    def test_schema_checks_required_and_enum(self):
        schema = {'type': 'object', 'required': ['status'], 'properties': {'status': {'enum': ['gap']}}}
        self.assertTrue(CHECKER.schema_errors({}, schema, 'row'))
        self.assertTrue(CHECKER.schema_errors({'status': 'complete'}, schema, 'row'))
        self.assertEqual([], CHECKER.schema_errors({'status': 'gap'}, schema, 'row'))


if __name__ == '__main__':
    unittest.main()
