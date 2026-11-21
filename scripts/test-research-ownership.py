#!/usr/bin/env python3
"""Positive and corruption checks for the metadata-only allocation."""
import copy, importlib.util, json, unittest, tempfile
from unittest.mock import patch
from pathlib import Path
spec=importlib.util.spec_from_file_location('allocation',Path(__file__).with_name('inventory-research-ownership.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class OwnershipTests(unittest.TestCase):
    def setUp(self):
        self.data=m.generate();self.owners=json.loads((m.OUT/'owners.json').read_text())
    def test_complete_partition(self):
        m.validate_owners(self.data,self.owners)
        ids=[x for b in self.data['batches'] for x in b['locators']]
        self.assertEqual(len(ids),len(set(ids)));self.assertEqual(len(ids),5250)
        self.assertTrue(all(0<len(b['locators'])<=50 for b in self.data['batches']))
    def test_missing_owner(self):
        self.owners.pop(next(iter(self.owners)))
        with self.assertRaises(AssertionError):m.validate_owners(self.data,self.owners)
    def test_extra_owner(self):
        self.owners['unknown']=999999
        with self.assertRaises(AssertionError):m.validate_owners(self.data,self.owners)
    def test_duplicate_owner(self):
        keys=list(self.owners);self.owners[keys[1]]=self.owners[keys[0]]
        with self.assertRaises(AssertionError):m.validate_owners(self.data,self.owners)
    def test_initial_owner(self):
        b=next(b for b in self.data['batches'] if b['initial_issue']);self.owners[b['id']]=999999
        with self.assertRaises(AssertionError):m.validate_owners(self.data,self.owners)
    def test_missing_locator(self):
        data=copy.deepcopy(self.data);data['batches'][0]['locators'].pop()
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory)
            (out/'batches.json').write_text(json.dumps(data,indent=2)+'\n')
            (out/'owners.json').write_text(json.dumps(self.owners))
            with patch.object(m,'OUT',out), patch('sys.argv',['allocator','--check']):
                with self.assertRaisesRegex(AssertionError,'Allocation drift'):m.main()
    def test_index(self):
        index=(m.OUT/'index.md').read_text()
        for b in self.data['batches']:
            self.assertIn(f"| {b['id']} | {len(b['locators'])} |",index)
            self.assertIn(f"issues/{self.owners[b['id']]}",index)
if __name__=='__main__':unittest.main()
