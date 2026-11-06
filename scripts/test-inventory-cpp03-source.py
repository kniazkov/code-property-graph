#!/usr/bin/env python3
"""Synthetic extraction regression tests; no copyrighted source fixture required."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('inventory_cpp03', Path(__file__).with_name('inventory-cpp03-source.py'))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def document(pages):
    result = [''] * 786
    for number, text in pages.items():
        result[number - 1] = text
    return '\f'.join(result)


def page(*lines):
    return '\n' * 6 + '\n'.join(lines)


class InventoryTests(unittest.TestCase):
    def parse(self, pages):
        return MODULE.parse_layout(document(pages))

    def test_alphabetic_paragraphs_are_not_lost_or_relabelled(self):
        result = self.parse({29: page('3.4.2 Sample [sample]', '2  First rule.', '2a  Added rule.', '3  Next rule.')})
        self.assertEqual([u['paragraph'] for u in result['units']], [2, '2a', 3])
        self.assertEqual(result['units'][1]['id'], 'cpp03:3.4.2:p2a')

    def test_unnumbered_introduction_does_not_invent_paragraph_one(self):
        result = self.parse({29: page('1.3 Sample [sample]', '    An unnumbered introductory block.', '2  Numbered rule.')})
        ids = {u['id'] for u in result['units']}
        self.assertIn('cpp03:1.3:u1', ids)
        self.assertIn('cpp03:1.3:p2', ids)
        self.assertNotIn('cpp03:1.3:p1', ids)

    def test_one_of_grammar_is_retained_and_prose_artifact_rejected(self):
        result = self.parse({29: page('5.1 Sample [sample]', '1  Rule.',
                                     '    used:', '        sample text',
                                     '    digit: one of', '        0 1 2')})
        ids = {u['id'] for u in result['units']}
        self.assertIn('cpp03:5.1:grammar:digit', ids)
        self.assertNotIn('cpp03:5.1:grammar:used', ids)
        self.assertEqual(result['rejected_candidates'][0]['id'], 'cpp03:5.1:grammar:used')

    def test_footnote_has_global_id_and_separate_reference(self):
        result = self.parse({29: page('1.1 Sample [sample]', '1  First rule.22)',
                                     '1.2 Next [next]', '1  Other rule.', '', '',
                                     '    __________________', '    22) Explanatory note.')})
        note = next(u for u in result['units'] if u['unit_kind'] == 'footnote')
        self.assertEqual(note['id'], 'cpp03:footnotes:footnote22')
        self.assertEqual(note['section'], 'footnotes')
        self.assertIsNone(note['tag'])
        self.assertEqual(note['call_sites'], ['cpp03:1.1:p1'])

    def test_alphabetic_footnote_starts_footer(self):
        result = self.parse({29: page('20.3 Sample [sample]', '1  First rule.214a)',
                                     '', '', '', '', '    __________________',
                                     '    214a) Explanatory note.', '    215) Another note.')})
        notes = [u['footnote_number'] for u in result['units'] if u['unit_kind'] == 'footnote']
        self.assertEqual(notes, ['214a', '215'])

    def test_known_continued_footer_keeps_later_notes(self):
        result = self.parse({612: page('26.3.2.1 Sample [sample]', '1  First rule.256)',
                                      '', '', '', '', '    __________________',
                                      '    Continuation of a preceding note.',
                                      '    256) Next note.', '    257) Further note.')})
        notes = [u['footnote_number'] for u in result['units'] if u['unit_kind'] == 'footnote']
        self.assertEqual(notes, ['256', '257'])

    def test_standalone_number_remains_an_explicit_anomaly(self):
        result = self.parse({686: page('27.7.1.3 Sample [sample]', '8  Actual rule.', '9')})
        item = next(u for u in result['units'] if u.get('paragraph') == 9)
        self.assertIn('body not inferred', item['extraction_note'])

    def test_output_never_contains_source_body(self):
        result = self.parse({29: page('1.1 Sample [sample]', '1  PRIVATE NORMATIVE TEXT MARKER')})
        self.assertNotIn('PRIVATE NORMATIVE TEXT MARKER', str(result))
        layout = document({749: 'Latin: 00c0–00c1, 00d0'})
        table = 'RANGES = {0x00c0, 0x00c1, 0x00d0, 0x00d0};'
        compared = MODULE.compare_annex_e(layout, table)
        self.assertEqual(compared['source_range_count'], 2)
        self.assertTrue(compared['exact_order_equal'])
        self.assertNotIn('00c0', str(compared))
        for item in result['units']:
            self.assertFalse({'body', 'source_text', 'paragraph_text'} & item.keys())

    def test_wrong_edition_bytes_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            pdf = Path(directory) / 'other.pdf'
            pdf.write_bytes(b'%PDF-incorrect edition')
            with self.assertRaisesRegex(ValueError, 'refuse edition substitution'):
                MODULE.check_original_pdf(pdf)


if __name__ == '__main__':
    unittest.main()
